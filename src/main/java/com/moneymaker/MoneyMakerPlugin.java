package com.moneymaker;

import com.google.inject.Provides;
import com.moneymaker.data.BossCheck;
import com.moneymaker.data.BossQuests;
import com.moneymaker.data.BossSetup;
import com.moneymaker.data.FarmRoute;
import com.moneymaker.data.FarmRun;
import com.moneymaker.data.Gear;
import com.moneymaker.data.GoalWatch;
import com.moneymaker.data.Training;
import com.moneymaker.data.GearPricer;
import com.moneymaker.data.ItemPrice;
import com.moneymaker.data.PriceBook;
import com.moneymaker.data.RefreshCooldown;
import com.moneymaker.data.Recipe;
import com.moneymaker.data.WikiPriceClient;
import com.moneymaker.route.Places;
import com.moneymaker.route.Pos;
import com.moneymaker.route.RouteOverlays;
import com.moneymaker.route.RouteService;
import com.moneymaker.route.TrainSpots;
import com.moneymaker.ui.MoneyMakerPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Adventurer's Ledger",
	description = "Money makers, fastest training routes, farm run timers, boss gear at live GE prices and a route finder",
	tags = {"money", "gp", "profit", "xp", "training", "ironman", "farming", "timers", "boss", "gear", "ledger", "route", "path", "map"}
)
public class MoneyMakerPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(MoneyMakerPlugin.class);

	@Inject
	private Client client;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private MoneyMakerConfig config;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private WikiPriceClient wiki;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Notifier notifier;
	@Inject
	private ClientThread clientThread;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private WorldMapOverlay worldMapOverlay;
	@Inject
	private WorldMapPointManager worldMapPointManager;

	/** The route finder: works out and follows the route you've asked for. */
	private RouteService routes;
	private volatile Places places = Places.empty();
	/** Where to go for each training method on the Train tab. */
	private volatile TrainSpots trainSpots = TrainSpots.empty();
	private final List<Overlay> routeOverlays = new java.util.ArrayList<>();
	/** The flag on the world map where the route ends. Only touched under its own lock. */
	private WorldMapPoint routeMarker;
	private final Object routeMarkerLock = new Object();

	private MoneyMakerPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> priceTask;
	private ScheduledFuture<?> farmTask;
	/** Bank save waiting to be written, so a busy bank session writes once, not on every change. */
	private ScheduledFuture<?> bankSaveTask;
	private volatile Runnable pendingBankSave;
	/** Account the farm timers belong to (the last one logged in). 0 until an account has been seen. */
	private volatile long farmAccount;
	private volatile List<FarmRun> farmRuns = Collections.emptyList();
	private volatile FarmRoute farmRoute = FarmRoute.empty();
	private volatile BossQuests bossQuests = BossQuests.empty();
	private volatile BossCheck bossCheck = BossCheck.empty();
	/** Quest name to how far you are with it, for the quests bosses need. Empty when logged out. */
	private volatile Map<String, QuestState> questStates = Collections.emptyMap();
	/** Game ticks until quest progress is checked again. */
	private int questCheckIn;
	private volatile Training training = Training.empty();
	private volatile Gear gear = Gear.empty();
	/** Item id -> quantity in your inventory, worn equipment and bank (unnoted ids). */
	private volatile Map<Integer, Long> invItems = Collections.emptyMap();
	private volatile Map<Integer, Long> wornItems = Collections.emptyMap();
	private volatile Map<Integer, Long> bankItems = Collections.emptyMap();
	private volatile boolean bankSeen;
	private final Map<Integer, String> itemNames = new java.util.concurrent.ConcurrentHashMap<>();
	/** Account type varbit: 0 normal, anything else is an ironman type. -1 when unknown. */
	private volatile int accountType = -1;
	private long loadedBankFor;

	private volatile List<Recipe> recipes = Collections.emptyList();
	private volatile List<BossSetup> bosses = Collections.emptyList();
	private volatile GearPricer gearPricer = new GearPricer(Collections.emptyMap());
	private volatile PriceBook prices = PriceBook.EMPTY;
	private volatile String priceError;
	private volatile List<WikiPriceClient.Mapping> mapping = Collections.emptyList();
	/** Skill name -> {level, xp}. Empty when logged out. */
	private volatile Map<String, long[]> skills = Collections.emptyMap();

	@Provides
	MoneyMakerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MoneyMakerConfig.class);
	}

	@Override
	protected void startUp()
	{
		// each file loads on its own, so a problem in one doesn't take the others down with it
		recipes = load("recipes.txt", Recipe::load, Collections.emptyList());
		bosses = load("bosses.txt", BossSetup::load, Collections.emptyList());
		gearPricer = load("aliases.txt", GearPricer::load, new GearPricer(Collections.emptyMap()));
		farmRuns = load("farmruns.txt", FarmRun::load, Collections.emptyList());
		farmRoute = load("farmroutes.txt", FarmRoute::load, FarmRoute.empty());
		bossQuests = load("bossquests.txt", in ->
		{
			try (InputStream lines = resource("questlines.txt"))
			{
				return BossQuests.load(in, lines);
			}
		}, BossQuests.empty());
		training = load("training.txt", Training::load, Training.empty());
		gear = load("gear.txt", Gear::load, Gear.empty());
		bossCheck = load("bossskills.txt", BossCheck::load, BossCheck.empty());
		farmAccount = savedFarmAccount();
		places = loadPlaces();
		trainSpots = loadTrainSpots(places);
		routes = new RouteService(client, clientThread, new RouteService.Settings()
		{
			@Override
			public com.moneymaker.route.PlayerState.Teleports teleports()
			{
				return config.routeTeleports();
			}

			@Override
			public boolean avoidWilderness()
			{
				return config.routeAvoidWilderness();
			}
		}, this::routeChanged, RouteService.class);
		routes.startUp();
		routeOverlays.add(new RouteOverlays.Scene(client, routes, () -> config.routeGround() ? config.routeColor() : null));
		routeOverlays.add(new RouteOverlays.Minimap(client, routes, () -> config.routeMinimap() ? config.routeColor() : null));
		routeOverlays.add(new RouteOverlays.WorldMap(client, routes, worldMapOverlay, config::routeColor));
		routeOverlays.forEach(overlayManager::add);

		panel = new MoneyMakerPanel(this, itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Adventurer's Ledger")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		schedulePriceRefresh();
		farmTask = executor.scheduleWithFixedDelay(this::checkFarmTimers, 5, 30, TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown()
	{
		if (priceTask != null)
		{
			priceTask.cancel(false);
		}
		if (farmTask != null)
		{
			farmTask.cancel(false);
		}
		if (bankSaveTask != null)
		{
			bankSaveTask.cancel(false);
		}
		Runnable save = pendingBankSave;
		if (save != null)
		{
			save.run();
		}
		routeOverlays.forEach(overlayManager::remove);
		routeOverlays.clear();
		if (routes != null)
		{
			routes.shutDown();
		}
		setRouteMarker(null, null);
		clientToolbar.removeNavigation(navButton);
		if (panel != null)
		{
			panel.shutDown();
		}
		panel = null;
		skills = Collections.emptyMap();
	}

	private interface Loader<T>
	{
		T load(InputStream in) throws IOException;
	}

	/** Load one data file, falling back to an empty value (and logging why) if it can't be read. */
	private <T> T load(String name, Loader<T> loader, T fallback)
	{
		try (InputStream in = resource(name))
		{
			return loader.load(in);
		}
		catch (IOException | RuntimeException e)
		{
			log.error("Adventurer's Ledger couldn't load {}", name, e);
			return fallback;
		}
	}

	private InputStream resource(String name) throws IOException
	{
		InputStream in = MoneyMakerPlugin.class.getResourceAsStream(name);
		if (in == null)
		{
			throw new IOException("Missing resource " + name);
		}
		return in;
	}

	// ---------------------------------------------------------------- events

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		accountType = client.getVarbitValue(VarbitID.IRONMAN);
		routes.onGameTick();
		loadSavedBank();
		claimFarmTimers();
		checkQuests();
		resolveNames();

		Map<String, long[]> now = new HashMap<>();
		for (Skill s : Skill.values())
		{
			if (!"OVERALL".equals(s.name()))
			{
				now.put(s.name(), new long[]{client.getRealSkillLevel(s), client.getSkillExperience(s)});
			}
		}
		if (!sameSkills(now, skills))
		{
			skills = Collections.unmodifiableMap(now);
			checkGoal();
			updatePanel(MoneyMakerPanel::refreshJobs);
		}
	}

	/** Watches for you reaching the level goal set on the Goal tab. */
	private final GoalWatch goalWatch = new GoalWatch();

	/** Saved as "goalDone" once the level goal is reached, so the Goal tab can say so. */
	private void checkGoal()
	{
		String skill = "Reach a level".equals(getSaved("goalMode")) ? getSaved("goalSkill") : null;
		int goal;
		try
		{
			goal = Integer.parseInt(String.valueOf(getSaved("goalLevel")));
		}
		catch (NumberFormatException e)
		{
			return;
		}
		long[] mine = skill == null ? null : getSkill(skill);
		if (!goalWatch.update(skill, goal, mine == null ? -1 : (int) mine[0]))
		{
			return;
		}
		save("goalDone", skill + ":" + goal);
		String name = skill.charAt(0) + skill.substring(1).toLowerCase(java.util.Locale.ROOT);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=00c000>Adventurer's Ledger:</col> goal reached, level " + goal + " " + name + "!", null);
		if (config.goalNotify())
		{
			notifier.notify("Adventurer's Ledger: goal reached, level " + goal + " " + name);
		}
	}

	/** The level goal you've reached ("MINING:62"), as saved when it happened, or null. */
	public String getGoalDone()
	{
		return getSaved("goalDone");
	}

	/** Logging out: forget the levels and items of the account that just left. */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGIN_SCREEN || skills.isEmpty())
		{
			return;
		}
		skills = Collections.emptyMap();
		goalWatch.reset();
		questStates = Collections.emptyMap();
		questCheckIn = 0;
		accountType = -1;
		invItems = Collections.emptyMap();
		wornItems = Collections.emptyMap();
		updatePanel(MoneyMakerPanel::refreshAll);
	}

	/** How often to re-read quest progress, in game ticks (about 30 seconds). */
	private static final int QUEST_CHECK_TICKS = 50;

	/** Read how far you are with the quests bosses need (client thread only). */
	private void checkQuests()
	{
		if (questCheckIn-- > 0)
		{
			return;
		}
		questCheckIn = QUEST_CHECK_TICKS;
		Set<String> wanted = bossQuests.allQuests();
		if (wanted.isEmpty())
		{
			return;
		}
		Map<String, QuestState> now = new HashMap<>();
		for (Quest q : Quest.values())
		{
			if (wanted.contains(q.getName()))
			{
				now.put(q.getName(), q.getState(client));
			}
		}
		if (!now.equals(questStates))
		{
			questStates = Collections.unmodifiableMap(now);
			updatePanel(MoneyMakerPanel::refreshBosses);
		}
	}

	/** Skill levels each boss needs. */
	public BossCheck getBossCheck()
	{
		return bossCheck;
	}

	/** True when you have every level a job needs. Also true when logged out, as there's nothing to check against. */
	public boolean meetsReqs(Recipe r)
	{
		for (Recipe.Req q : r.getReqs())
		{
			long[] have = getSkill(q.getSkill());
			if (have != null && have[0] < q.getLevel())
			{
				return false;
			}
		}
		return true;
	}

	/** Open the Goal tab with this item as the thing to save for. */
	public void planFor(String item)
	{
		updatePanel(p -> p.showGoal(item));
	}

	/** A small value the panel wants remembered between sessions (your goal), or null. */
	public String getSaved(String key)
	{
		return configManager == null ? null : configManager.getConfiguration(MoneyMakerConfig.GROUP, key);
	}

	/** Remember a value between sessions; null forgets it. */
	public void save(String key, String value)
	{
		if (configManager == null)
		{
			return;
		}
		if (value == null)
		{
			configManager.unsetConfiguration(MoneyMakerConfig.GROUP, key);
		}
		else
		{
			configManager.setConfiguration(MoneyMakerConfig.GROUP, key, value);
		}
	}

	public BossQuests getBossQuests()
	{
		return bossQuests;
	}

	/** How far you are with a quest, or null when logged out or the quest isn't known. */
	public QuestState getQuestState(String quest)
	{
		return questStates.get(quest);
	}

	private static boolean sameSkills(Map<String, long[]> a, Map<String, long[]> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (Map.Entry<String, long[]> e : a.entrySet())
		{
			long[] o = b.get(e.getKey());
			if (o == null || o[0] != e.getValue()[0] || o[1] != e.getValue()[1])
			{
				return false;
			}
		}
		return true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!MoneyMakerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		// things the plugin saves for itself (goal, timers, bank) aren't settings: nothing to redraw
		String key = event.getKey();
		if (key.equals("goalMode") || key.equals("goalSkill") || key.equals("goalLevel"))
		{
			// a new goal: note where you stand now, so reaching it later is noticed
			clientThread.invokeLater(this::checkGoal);
		}
		if (key.startsWith("goal") || key.startsWith("route") || key.startsWith("farm_") || key.startsWith("farmdone_") || key.equals("farmAccount")
			|| key.startsWith("bank_"))
		{
			return;
		}
		if ("refreshMinutes".equals(event.getKey()))
		{
			schedulePriceRefresh();
		}
		updatePanel(MoneyMakerPanel::refreshAll);
	}

	// ---------------------------------------------------------------- routes

	private Places loadPlaces()
	{
		Places all = Places.empty();
		for (String file : ROUTE_PLACE_FILES)
		{
			try (InputStream in = RouteService.class.getResourceAsStream(file))
			{
				if (in != null)
				{
					all.add(in);
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.error("Adventurer's Ledger couldn't load {}", file, e);
			}
		}
		return all;
	}

	private TrainSpots loadTrainSpots(Places places)
	{
		try (InputStream in = RouteService.class.getResourceAsStream("trainspots.txt"))
		{
			if (in != null)
			{
				return TrainSpots.load(in, places);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.error("Adventurer's Ledger couldn't load trainspots.txt", e);
		}
		return TrainSpots.empty();
	}

	/** Where to go for each training method, so the Train tab can show the way. */
	public TrainSpots getTrainSpots()
	{
		return trainSpots;
	}

	/** The files of named places, next to the route classes. */
	private static final String[] ROUTE_PLACE_FILES = {"places.txt", "places-bosses-farms.txt"};

	/** Routes to bosses and farm stops are on: their places are in places-bosses-farms.txt. */
	public boolean canRoutePerks()
	{
		return true;
	}

	/** A line to show where those routes would be, or null to show nothing. */
	public String routePerkHint()
	{
		return null;
	}

	/** The route finder. */
	public RouteService getRoutes()
	{
		return routes;
	}

	/** Named places a route can go to. */
	public Places getPlaces()
	{
		return places;
	}

	public void routeTo(Places.Place place, String tag)
	{
		routes.routeTo(place.getName(), tag, place.getTiles());
	}

	/** Start a route to where a quest begins. */
	public void routeToQuest(String quest)
	{
		routes.routeToQuest(quest, places);
	}

	public void clearRoute()
	{
		routes.clear();
	}

	/** The route changed (any thread): move the flag on the world map and redraw the route box. */
	private void routeChanged()
	{
		RouteService r = routes;
		if (r == null)
		{
			return;
		}
		setRouteMarker(r.isActive() ? r.getDestination() : null, r.getName());
		updatePanel(MoneyMakerPanel::refreshRoute);
	}

	private void setRouteMarker(WorldPoint at, String name)
	{
		synchronized (routeMarkerLock)
		{
			if (routeMarker != null)
			{
				if (at != null && at.equals(routeMarker.getWorldPoint()))
				{
					return;
				}
				worldMapPointManager.remove(routeMarker);
				routeMarker = null;
			}
			if (at != null)
			{
				routeMarker = WorldMapPoint.builder()
					.worldPoint(at)
					.image(RouteOverlays.flag(config.routeColor()))
					.imagePoint(new net.runelite.api.Point(3, 17))
					.snapToEdge(true)
					.jumpOnClick(true)
					.name("Route: " + (name == null ? "destination" : name))
					.build();
				worldMapPointManager.add(routeMarker);
			}
		}
	}

	/** Right-click on the world map: "Route here", and "Clear route" while there is one. */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map == null || map.isHidden())
		{
			return;
		}
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		java.awt.Rectangle bounds = map.getBounds();
		if (!bounds.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}
		WorldMap worldMap = client.getWorldMap();
		float zoom = worldMap.getWorldMapZoom();
		net.runelite.api.Point centre = worldMap.getWorldMapPosition();
		int dx = (int) ((mouse.getX() - bounds.getCenterX()) / zoom);
		int dy = (int) ((mouse.getY() - bounds.getCenterY()) / zoom);
		WorldPoint target = new WorldPoint(centre.getX() + dx, centre.getY() - dy, 0);
		if (routes.isActive())
		{
			client.getMenu().createMenuEntry(-1)
				.setOption("Clear route")
				.setTarget("<col=00c8ff>Ledger</col>")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> routes.clear());
		}
		client.getMenu().createMenuEntry(-1)
			.setOption("Route here")
			.setTarget("<col=00c8ff>Ledger</col>")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> routes.routeTo("Spot on the map", "map", target));
	}

	// ---------------------------------------------------------------- prices

	private void schedulePriceRefresh()
	{
		if (priceTask != null)
		{
			priceTask.cancel(false);
		}
		priceTask = executor.scheduleWithFixedDelay(this::refreshPrices, 0, config.refreshMinutes(), TimeUnit.MINUTES);
	}

	private final RefreshCooldown cooldown = new RefreshCooldown();

	/** Starts a manual refresh unless one ran recently. Returns seconds left to wait, or 0 if it started. */
	public long refreshPricesNow()
	{
		long wait = cooldown.secondsLeft(System.currentTimeMillis());
		if (wait == 0)
		{
			cooldown.markFetch(System.currentTimeMillis());
			executor.execute(this::refreshPrices);
		}
		return wait;
	}

	private void refreshPrices()
	{
		cooldown.markFetch(System.currentTimeMillis());
		try
		{
			if (mapping.isEmpty())
			{
				mapping = wiki.fetchMapping();
			}
			Map<Integer, WikiPriceClient.Latest> latest = wiki.fetchLatest();
			Map<Integer, WikiPriceClient.Average> hourly = wiki.fetchHourly();

			Map<String, ItemPrice> byName = new HashMap<>();
			for (WikiPriceClient.Mapping m : mapping)
			{
				if (m.name == null)
				{
					continue;
				}
				WikiPriceClient.Latest l = latest.get(m.id);
				WikiPriceClient.Average h = hourly.get(m.id);
				byName.put(PriceBook.key(m.name), new ItemPrice(m.id, m.name, m.limit == null ? 0 : m.limit,
					l == null ? 0 : nz(l.high), l == null ? 0 : nz(l.low),
					h == null ? 0 : nz(h.avgHighPrice), h == null ? 0 : nz(h.avgLowPrice),
					h == null ? 0 : nz(h.highPriceVolume) + nz(h.lowPriceVolume),
					l == null ? 0 : Math.max(nz(l.highTime), nz(l.lowTime))));
			}
			prices = new PriceBook(Collections.unmodifiableMap(byName), System.currentTimeMillis());
			priceError = null;
		}
		catch (Exception e)
		{
			log.warn("Adventurer's Ledger couldn't fetch prices", e);
			priceError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		}
		updatePanel(MoneyMakerPanel::refreshAll);
	}

	private static long nz(Long v)
	{
		return v == null ? 0 : v;
	}

	// ---------------------------------------------------------------- farm run timers

	// ---------------------------------------------------------------- items you own

	private static final int INVENTORY = InventoryID.INV;
	private static final int EQUIPMENT = InventoryID.WORN;
	private static final int BANK = InventoryID.BANK;
	private static final int BANK_SAVE_DELAY_SECONDS = 3;

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id != INVENTORY && id != EQUIPMENT && id != BANK)
		{
			return;
		}
		ItemContainer c = event.getItemContainer();
		if (c == null)
		{
			return;
		}
		Map<Integer, Long> items = new HashMap<>();
		for (Item it : c.getItems())
		{
			if (it.getId() <= 0 || it.getQuantity() <= 0)
			{
				continue;
			}
			items.merge(itemManager.canonicalize(it.getId()), (long) it.getQuantity(), Long::sum);
		}
		Map<Integer, Long> frozen = Collections.unmodifiableMap(items);
		if (id == INVENTORY)
		{
			invItems = frozen;
		}
		else if (id == EQUIPMENT)
		{
			wornItems = frozen;
		}
		else
		{
			bankItems = frozen;
			bankSeen = true;
			saveBank(frozen);
		}
		resolveNames();
		updatePanel(MoneyMakerPanel::refreshTrain);
	}

	/** Remember the bank between sessions (only item ids and amounts, in your RuneLite profile). */
	private void saveBank(Map<Integer, Long> items)
	{
		long hash = client.getAccountHash();
		if (hash == -1)
		{
			return;
		}
		// the bank changes with every deposit and withdrawal; wait for it to settle, then write once
		Runnable save = () ->
		{
			pendingBankSave = null;
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<Integer, Long> e : items.entrySet())
			{
				sb.append(sb.length() > 0 ? "," : "").append(e.getKey()).append(':').append(e.getValue());
			}
			configManager.setConfiguration(MoneyMakerConfig.GROUP, "bank_" + hash, sb.toString());
		};
		if (bankSaveTask != null)
		{
			bankSaveTask.cancel(false);
		}
		pendingBankSave = save;
		bankSaveTask = executor.schedule(save, BANK_SAVE_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	private void loadSavedBank()
	{
		long hash = client.getAccountHash();
		if (hash == -1 || hash == loadedBankFor)
		{
			return;
		}
		// new account (or first login): forget the old bank, then load this account's saved one
		boolean switched = loadedBankFor != 0;
		loadedBankFor = hash;
		if (switched || !bankSeen)
		{
			bankItems = Collections.emptyMap();
			bankSeen = false;
		}
		String saved = configManager.getConfiguration(MoneyMakerConfig.GROUP, "bank_" + hash);
		if (saved == null || saved.isEmpty())
		{
			return;
		}
		Map<Integer, Long> items = new HashMap<>();
		for (String part : saved.split(","))
		{
			int colon = part.indexOf(':');
			try
			{
				items.put(Integer.parseInt(part.substring(0, colon)), Long.parseLong(part.substring(colon + 1)));
			}
			catch (RuntimeException ignored)
			{
			}
		}
		bankItems = Collections.unmodifiableMap(items);
		bankSeen = true;
		updatePanel(MoneyMakerPanel::refreshTrain);
	}

	/** Look up names for item ids (client thread only). */
	private void resolveNames()
	{
		int budget = 300;
		for (Map<Integer, Long> m : java.util.Arrays.asList(invItems, wornItems, bankItems))
		{
			for (Integer id : m.keySet())
			{
				if (budget > 0 && !itemNames.containsKey(id))
				{
					budget--;
					itemNames.put(id, itemManager.getItemComposition(id).getName());
				}
			}
		}
	}

	/** Everything you own by lower-case name, with where it is. */
	public Map<String, Gear.Owned> getOwned()
	{
		Map<String, Gear.Owned> out = new HashMap<>();
		String[] where = {"worn", "bag", "bank"};
		List<Map<Integer, Long>> maps = java.util.Arrays.asList(wornItems, invItems, bankItems);
		for (int i = 0; i < maps.size(); i++)
		{
			for (Map.Entry<Integer, Long> e : maps.get(i).entrySet())
			{
				String name = itemNames.get(e.getKey());
				if (name == null || name.equalsIgnoreCase("null"))
				{
					continue;
				}
				String key = name.toLowerCase(java.util.Locale.ROOT);
				Gear.Owned prev = out.get(key);
				out.put(key, new Gear.Owned(name, e.getValue() + (prev == null ? 0 : prev.getQty()),
					prev == null ? where[i] : prev.getWhere()));
			}
		}
		return out;
	}

	public boolean isBankSeen()
	{
		return bankSeen;
	}

	public Gear getGear()
	{
		return gear;
	}

	/** Ironman mode from the setting, or from your account type when set to detect. */
	public boolean isIronman()
	{
		switch (config.ironman())
		{
			case ON:
				return true;
			case OFF:
				return false;
			default:
				return accountType > 0;
		}
	}

	public Training getTraining()
	{
		return training;
	}

	/** Where to go on each farm run. */
	public FarmRoute getFarmRoute()
	{
		return farmRoute;
	}

	public List<FarmRun> getFarmRuns()
	{
		return farmRuns;
	}

	/** When this run was planted (epoch ms), or 0 if no timer is running. Saved in your RuneLite profile. */
	public long getPlanted(FarmRun run)
	{
		String v = configManager.getConfiguration(MoneyMakerConfig.GROUP, farmKey("farm_", run));
		try
		{
			return v == null ? 0 : Long.parseLong(v);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** When this run will be ready (epoch ms), or 0 if no timer is running. */
	public long getReadyAt(FarmRun run)
	{
		long planted = getPlanted(run);
		return planted == 0 ? 0 : planted + Math.round(run.getGrowMinutes() * 60_000);
	}

	public void startFarmTimer(FarmRun run)
	{
		configManager.setConfiguration(MoneyMakerConfig.GROUP, farmKey("farm_", run), String.valueOf(System.currentTimeMillis()));
		configManager.unsetConfiguration(MoneyMakerConfig.GROUP, farmKey("farmdone_", run));
		updatePanel(MoneyMakerPanel::refreshFarm);
	}

	public void clearFarmTimer(FarmRun run)
	{
		configManager.unsetConfiguration(MoneyMakerConfig.GROUP, farmKey("farm_", run));
		configManager.unsetConfiguration(MoneyMakerConfig.GROUP, farmKey("farmdone_", run));
		updatePanel(MoneyMakerPanel::refreshFarm);
	}

	/**
	 * Config key for a run's timer. Timers belong to the account that was last logged in, so two
	 * accounts don't share them; they keep counting (and alerting) on the login screen.
	 */
	private String farmKey(String prefix, FarmRun run)
	{
		long account = farmAccount;
		return account == 0 ? prefix + run.getId() : prefix + account + "_" + run.getId();
	}

	private long savedFarmAccount()
	{
		String v = configManager.getConfiguration(MoneyMakerConfig.GROUP, "farmAccount");
		try
		{
			return v == null ? 0 : Long.parseLong(v);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** On login, point the farm timers at this account. Timers set before accounts were tracked move to it. */
	private void claimFarmTimers()
	{
		long hash = client.getAccountHash();
		if (hash == -1 || hash == 0 || hash == farmAccount)
		{
			return;
		}
		for (FarmRun run : farmRuns)
		{
			for (String prefix : new String[]{"farm_", "farmdone_"})
			{
				String oldKey = prefix + run.getId();
				String newKey = prefix + hash + "_" + run.getId();
				String old = configManager.getConfiguration(MoneyMakerConfig.GROUP, oldKey);
				if (old == null)
				{
					continue;
				}
				if (configManager.getConfiguration(MoneyMakerConfig.GROUP, newKey) == null)
				{
					configManager.setConfiguration(MoneyMakerConfig.GROUP, newKey, old);
				}
				configManager.unsetConfiguration(MoneyMakerConfig.GROUP, oldKey);
			}
		}
		farmAccount = hash;
		configManager.setConfiguration(MoneyMakerConfig.GROUP, "farmAccount", String.valueOf(hash));
		updatePanel(MoneyMakerPanel::refreshFarm);
	}

	private void checkFarmTimers()
	{
		long now = System.currentTimeMillis();
		for (FarmRun run : farmRuns)
		{
			long ready = getReadyAt(run);
			if (ready == 0 || now < ready)
			{
				continue;
			}
			String doneKey = farmKey("farmdone_", run);
			if (configManager.getConfiguration(MoneyMakerConfig.GROUP, doneKey) == null)
			{
				configManager.setConfiguration(MoneyMakerConfig.GROUP, doneKey, "1");
				if (config.farmNotify())
				{
					notifier.notify("Adventurer's Ledger: " + run.getName() + " is ready");
				}
			}
		}
		updatePanel(MoneyMakerPanel::refreshFarmTimers);
	}

	// ---------------------------------------------------------------- for the panel

	public MoneyMakerConfig getConfig()
	{
		return config;
	}

	public List<Recipe> getRecipes()
	{
		return recipes;
	}

	public List<BossSetup> getBosses()
	{
		return bosses;
	}

	public GearPricer getGearPricer()
	{
		return gearPricer;
	}

	public PriceBook getPrices()
	{
		return prices;
	}

	public String getPriceError()
	{
		return priceError;
	}

	/** {level, xp} for a skill, or null if not logged in. */
	public boolean isLoggedIn()
	{
		return !skills.isEmpty();
	}

	/** Level and xp in a skill, or null when logged out. "MELEE" means your lowest of Attack, Strength and Defence. */
	public long[] getSkill(String skill)
	{
		return skills.get(resolveSkill(skill));
	}

	/** Which real skill a job trains: "MELEE" becomes the lowest melee skill (the one you'd train next). */
	public String resolveSkill(String skill)
	{
		if (!"MELEE".equals(skill))
		{
			return skill;
		}
		String best = "ATTACK";
		long bestXp = Long.MAX_VALUE;
		for (String s : new String[]{"ATTACK", "STRENGTH", "DEFENCE"})
		{
			long[] v = skills.get(s);
			if (v != null && v[1] < bestXp)
			{
				best = s;
				bestXp = v[1];
			}
		}
		return skills.isEmpty() ? skill : best;
	}

	/** XP needed to reach the level after `level`, or -1. */
	public static long xpForNextLevel(long level)
	{
		if (level < 1 || level >= 99)
		{
			return -1;
		}
		return Experience.getXpForLevel((int) level + 1);
	}

	private void updatePanel(Consumer<MoneyMakerPanel> action)
	{
		SwingUtilities.invokeLater(() ->
		{
			MoneyMakerPanel p = panel;
			if (p != null)
			{
				action.accept(p);
			}
		});
	}

	private static BufferedImage createIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x2E7D32));
		g.fillRoundRect(1, 3, 14, 10, 3, 3);
		g.setColor(new Color(0x1B5E20));
		g.drawRoundRect(1, 3, 14, 10, 3, 3);
		g.setColor(new Color(0xE8F5E9));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		g.drawString("$", 5, 12);
		g.dispose();
		return img;
	}
}

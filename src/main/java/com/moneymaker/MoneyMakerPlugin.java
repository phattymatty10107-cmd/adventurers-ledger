package com.moneymaker;

import com.google.inject.Provides;
import com.moneymaker.data.BossSetup;
import com.moneymaker.data.FarmRun;
import com.moneymaker.data.Gear;
import com.moneymaker.data.Training;
import com.moneymaker.data.GearPricer;
import com.moneymaker.data.ItemPrice;
import com.moneymaker.data.PriceBook;
import com.moneymaker.data.Recipe;
import com.moneymaker.data.WikiPriceClient;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
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
	description = "Money makers, fastest training routes, farm run timers and boss gear at live GE prices",
	tags = {"money", "gp", "profit", "xp", "training", "ironman", "farming", "timers", "boss", "gear", "ledger"}
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

	private MoneyMakerPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> priceTask;
	private ScheduledFuture<?> farmTask;
	private volatile List<FarmRun> farmRuns = Collections.emptyList();
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
		try
		{
			recipes = Recipe.load(resource("recipes.txt"));
			bosses = BossSetup.load(resource("bosses.txt"));
			gearPricer = GearPricer.load(resource("aliases.txt"));
			farmRuns = FarmRun.load(resource("farmruns.txt"));
			training = Training.load(resource("training.txt"));
			gear = Gear.load(resource("gear.txt"));
		}
		catch (IOException | RuntimeException e)
		{
			log.error("Adventurer's Ledger couldn't load its data files", e);
		}

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
		clientToolbar.removeNavigation(navButton);
		panel = null;
		skills = Collections.emptyMap();
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
		accountType = client.getVarbitValue(ACCOUNT_TYPE_VARBIT);
		loadSavedBank();
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
			updatePanel(MoneyMakerPanel::refreshJobs);
		}
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
		if ("refreshMinutes".equals(event.getKey()))
		{
			schedulePriceRefresh();
		}
		updatePanel(MoneyMakerPanel::refreshAll);
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

	public void refreshPricesNow()
	{
		executor.execute(this::refreshPrices);
	}

	private void refreshPrices()
	{
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
					h == null ? 0 : nz(h.highPriceVolume) + nz(h.lowPriceVolume)));
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

	private static final int ACCOUNT_TYPE_VARBIT = 1777;
	private static final int INVENTORY = 93;
	private static final int EQUIPMENT = 94;
	private static final int BANK = 95;

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
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Long> e : items.entrySet())
		{
			sb.append(sb.length() > 0 ? "," : "").append(e.getKey()).append(':').append(e.getValue());
		}
		configManager.setConfiguration(MoneyMakerConfig.GROUP, "bank_" + client.getAccountHash(), sb.toString());
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

	public List<FarmRun> getFarmRuns()
	{
		return farmRuns;
	}

	/** When this run was planted (epoch ms), or 0 if no timer is running. Saved in your RuneLite profile. */
	public long getPlanted(FarmRun run)
	{
		String v = configManager.getConfiguration(MoneyMakerConfig.GROUP, "farm_" + run.getId());
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
		configManager.setConfiguration(MoneyMakerConfig.GROUP, "farm_" + run.getId(), String.valueOf(System.currentTimeMillis()));
		configManager.unsetConfiguration(MoneyMakerConfig.GROUP, "farmdone_" + run.getId());
		updatePanel(MoneyMakerPanel::refreshFarm);
	}

	public void clearFarmTimer(FarmRun run)
	{
		configManager.unsetConfiguration(MoneyMakerConfig.GROUP, "farm_" + run.getId());
		configManager.unsetConfiguration(MoneyMakerConfig.GROUP, "farmdone_" + run.getId());
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
			String doneKey = "farmdone_" + run.getId();
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

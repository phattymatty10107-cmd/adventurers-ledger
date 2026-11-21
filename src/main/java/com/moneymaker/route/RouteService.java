package com.moneymaker.route;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the route you've asked for: works it out on its own thread, follows you along it, and
 * works it out again if you wander off or teleport somewhere else.
 */
public final class RouteService
{
	private static final Logger log = LoggerFactory.getLogger(RouteService.class);

	/** The route settings, read each time a route is worked out. */
	public interface Settings
	{
		PlayerState.Teleports teleports();

		boolean avoidWilderness();
	}

	/** This close to the end counts as there. */
	private static final int ARRIVE_TILES = 2;
	/** Further than this from every nearby tile of the route and it's worked out again. */
	private static final int OFF_ROUTE_TILES = 7;
	/** Game ticks to leave between working the route out again. */
	private static final int RECALC_TICKS = 4;

	private final Client client;
	private final ClientThread clientThread;
	private final Settings settings;
	private final Runnable onChange;
	private final Class<?> resourceBase;
	private ExecutorService worker;

	private volatile Pathfinder pathfinder;
	private volatile TransportData data = TransportData.empty();
	private volatile boolean loadFailed;

	/** Where you're going, or null when there's no route. */
	private volatile String name;
	private volatile String tag;
	private volatile List<Integer> targets = Collections.emptyList();
	private volatile Route route;
	private volatile int progress;
	private volatile String status;
	private volatile boolean computing;
	/** Goes up every time the destination changes, so an old answer is thrown away. */
	private volatile int generation;
	private int ticksSinceCalc;
	private volatile boolean waitingForLogin;

	public RouteService(Client client, ClientThread clientThread, Settings settings, Runnable onChange, Class<?> resourceBase)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.settings = settings;
		this.onChange = onChange;
		this.resourceBase = resourceBase;
	}

	public void startUp()
	{
		worker = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "adventurers-ledger-route");
			t.setDaemon(true);
			return t;
		});
	}

	public void shutDown()
	{
		clear();
		if (worker != null)
		{
			worker.shutdownNow();
			worker = null;
		}
		pathfinder = null;
		data = TransportData.empty();
	}

	// ---------------------------------------------------------------- asking for a route

	/** Start a route to the nearest of these tiles. Safe to call from any thread. */
	public void routeTo(String name, String tag, List<Integer> tiles)
	{
		if (tiles == null || tiles.isEmpty())
		{
			return;
		}
		generation++;
		this.name = name;
		this.tag = tag;
		this.targets = Collections.unmodifiableList(new ArrayList<>(tiles));
		this.route = null;
		this.progress = 0;
		this.status = "Working out the route...";
		onChange.run();
		clientThread.invokeLater(this::calculate);
	}

	public void routeTo(String name, String tag, WorldPoint point)
	{
		routeTo(name, tag, Collections.singletonList(Pos.pack(point.getX(), point.getY(), point.getPlane())));
	}

	/**
	 * Start a route to where a quest begins. The game's own quest list says where that is; the
	 * list that ships with the plugin is only used if the game doesn't.
	 */
	public void routeToQuest(String questName, Places fallback)
	{
		clientThread.invokeLater(() ->
		{
			WorldPoint p = questStart(questName);
			if (p != null)
			{
				routeTo(questName, "quest", p);
				return;
			}
			Places.Place place = fallback == null ? null : fallback.find("quest", questName);
			if (place != null)
			{
				routeTo(questName, "quest", place.getTiles());
				return;
			}
			generation++;
			name = questName;
			tag = "quest";
			targets = Collections.emptyList();
			route = null;
			status = client.getGameState() == GameState.LOGGED_IN
				? "The game doesn't say where this one starts." : "Log in to find where this quest starts.";
			onChange.run();
		});
	}

	/** Where the game says a quest starts, or null. Client thread only. */
	private WorldPoint questStart(String questName)
	{
		try
		{
			for (Quest q : Quest.values())
			{
				if (!q.getName().equals(questName))
				{
					continue;
				}
				Object[] field = client.getDBTableField(q.getId(), DBTableID.Quest.COL_STARTCOORD, 0);
				if (field != null && field.length > 0 && field[0] instanceof Integer)
				{
					WorldPoint p = WorldPoint.fromCoord((Integer) field[0]);
					if (p.getX() > 0 && p.getY() > 0)
					{
						return p;
					}
				}
				return null;
			}
		}
		catch (RuntimeException e)
		{
			log.debug("No start point from the game for {}", questName, e);
		}
		return null;
	}

	/** Forget the route. */
	public void clear()
	{
		generation++;
		name = null;
		tag = null;
		targets = Collections.emptyList();
		route = null;
		progress = 0;
		status = null;
		computing = false;
		onChange.run();
	}

	// ---------------------------------------------------------------- working it out

	/** Client thread: note what your character can use, then hand the search to the worker. */
	private void calculate()
	{
		List<Integer> want = targets;
		if (want.isEmpty() || computing || worker == null)
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			status = "Log in and the route will appear.";
			waitingForLogin = true;
			onChange.run();
			return;
		}
		if (pathfinder == null)
		{
			if (loadFailed)
			{
				status = "The map data couldn't be loaded.";
				onChange.run();
				return;
			}
			// first route of the session: read the map, then come back here
			computing = true;
			submit(() ->
			{
				load();
				computing = false;
				clientThread.invokeLater(this::calculate);
			});
			return;
		}
		int start = here();
		PlayerState state = snapshot();
		int gen = generation;
		computing = true;
		ticksSinceCalc = 0;
		submit(() ->
		{
			Route found = null;
			try
			{
				found = pathfinder.find(start, want, state);
			}
			catch (RuntimeException e)
			{
				log.warn("Adventurer's Ledger couldn't work out a route", e);
			}
			finally
			{
				computing = false;
			}
			if (gen != generation)
			{
				return;
			}
			route = found;
			progress = 0;
			if (found == null)
			{
				status = "No route found.";
			}
			else if (!found.isReached())
			{
				status = "Can't get all the way there with what you have. This is as close as you can walk.";
			}
			else
			{
				status = null;
			}
			onChange.run();
		});
	}

	private void submit(Runnable job)
	{
		try
		{
			worker.execute(job);
		}
		catch (RejectedExecutionException | NullPointerException e)
		{
			// the plugin was turned off while this was queued
			computing = false;
		}
	}

	private void load()
	{
		try
		{
			CollisionMap map;
			try (InputStream in = open("collision-map.zip"))
			{
				if (in == null)
				{
					throw new IOException("Missing collision-map.zip");
				}
				map = CollisionMap.load(in);
			}
			TransportData td = TransportData.load(this::open);
			data = td;
			pathfinder = new Pathfinder(map, td);
		}
		catch (IOException | RuntimeException e)
		{
			log.error("Adventurer's Ledger couldn't load its map data", e);
			loadFailed = true;
		}
	}

	private InputStream open(String file)
	{
		return resourceBase.getResourceAsStream(file);
	}

	/** Your tile, packed. In an instance (a house, a raid) this is the tile the instance was copied from. */
	private int here()
	{
		Player p = client.getLocalPlayer();
		WorldPoint w = client.getTopLevelWorldView().isInstance()
			? WorldPoint.fromLocalInstance(client, p.getLocalLocation()) : p.getWorldLocation();
		if (w == null)
		{
			w = p.getWorldLocation();
		}
		return Pos.pack(w.getX(), w.getY(), w.getPlane());
	}

	/** Client thread: read everything a transport might ask about. */
	private PlayerState snapshot()
	{
		PlayerState s = new PlayerState();
		s.teleports = settings.teleports();
		s.avoidWilderness = settings.avoidWilderness();
		for (Skill skill : Skill.values())
		{
			s.levels.put(skill.getName(), client.getRealSkillLevel(skill));
		}
		s.totalLevel = client.getTotalLevel();
		Player me = client.getLocalPlayer();
		s.combatLevel = me == null ? 3 : me.getCombatLevel();

		TransportData td = data;
		if (!td.questNames().isEmpty())
		{
			for (Quest q : Quest.values())
			{
				if (td.questNames().contains(q.getName()) && q.getState(client) == QuestState.FINISHED)
				{
					s.questsDone.add(q.getName());
				}
			}
		}
		for (int id : td.varbitIds())
		{
			s.varbits.put(id, client.getVarbitValue(id));
		}
		for (int id : td.varpIds())
		{
			s.varps.put(id, client.getVarpValue(id));
		}
		s.fairyRingsWithoutStaff = client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;

		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			for (Item it : worn.getItems())
			{
				if (it.getId() > 0 && it.getQuantity() > 0)
				{
					s.worn.add(it.getId());
					s.carried.merge(it.getId(), it.getQuantity(), Integer::sum);
				}
			}
		}
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			for (Item it : inv.getItems())
			{
				if (it.getId() > 0 && it.getQuantity() > 0)
				{
					s.carried.merge(it.getId(), it.getQuantity(), Integer::sum);
				}
			}
		}
		return s;
	}

	// ---------------------------------------------------------------- following you

	/** Call once a game tick, on the client thread. */
	public void onGameTick()
	{
		if (targets.isEmpty() || client.getLocalPlayer() == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		ticksSinceCalc++;
		Route r = route;
		if (r == null)
		{
			// asked for while logged out: work it out now you're in
			if (waitingForLogin && !computing)
			{
				waitingForLogin = false;
				calculate();
			}
			return;
		}
		int me = here();
		int[] tiles = r.getTiles();
		int end = r.getEnd();
		if (r.isReached() && Pos.plane(me) == Pos.plane(end) && Pos.distance(me, end) <= ARRIVE_TILES)
		{
			String arrived = name;
			clear();
			status = "You've arrived" + (arrived == null ? "." : ": " + arrived + ".");
			onChange.run();
			return;
		}
		// where on the route are you? look a little behind and a good way ahead
		int from = Math.max(0, progress - 5);
		int to = Math.min(tiles.length - 1, progress + 80);
		int nearest = -1;
		int nearestDist = Integer.MAX_VALUE;
		for (int i = from; i <= to; i++)
		{
			if (Pos.plane(tiles[i]) != Pos.plane(me))
			{
				continue;
			}
			int d = Pos.distance(tiles[i], me);
			if (d < nearestDist || (d == nearestDist && i > nearest && d <= 1))
			{
				nearestDist = d;
				nearest = i;
			}
		}
		if (nearest >= 0 && nearestDist <= OFF_ROUTE_TILES)
		{
			if (nearestDist <= 2)
			{
				// the overlays read this each frame; nothing else needs telling
				progress = nearest;
			}
			return;
		}
		if (!computing && ticksSinceCalc >= RECALC_TICKS)
		{
			calculate();
		}
	}

	// ---------------------------------------------------------------- for the overlays and the panel

	/** The route, or null when there isn't one (yet). */
	public Route getRoute()
	{
		return route;
	}

	/** How far along the route's tiles you are. */
	public int getProgress()
	{
		return progress;
	}

	/** Where the route goes, or null when there's no route. */
	public String getName()
	{
		return name;
	}

	/** What asked for the route ("quest", "farm:herbs"), so it can tell the route is its own. */
	public String getTag()
	{
		return tag;
	}

	/** A line to show the player ("Working out the route..."), or null. */
	public String getStatus()
	{
		return status;
	}

	public boolean isActive()
	{
		return name != null;
	}

	/** The tile the route ends at (or will), or null. */
	public WorldPoint getDestination()
	{
		Route r = route;
		int p;
		if (r != null)
		{
			p = r.getEnd();
		}
		else if (!targets.isEmpty())
		{
			p = targets.get(0);
		}
		else
		{
			return null;
		}
		return new WorldPoint(Pos.x(p), Pos.y(p), Pos.plane(p));
	}
}

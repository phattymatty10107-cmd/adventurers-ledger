package com.moneymaker.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Quest;
import org.junit.BeforeClass;
import org.junit.Test;

/** Checks the route finder against the real map data, without needing the game running. */
public class RouteTest
{
	private static final int LUMBRIDGE = Pos.pack(3222, 3218, 0);
	private static final int GRAND_EXCHANGE = Pos.pack(3164, 3487, 0);

	private static CollisionMap map;
	private static TransportData transports;
	private static Pathfinder finder;

	static InputStream file(String name)
	{
		return RouteService.class.getResourceAsStream(name);
	}

	@BeforeClass
	public static void load() throws Exception
	{
		try (InputStream in = file("collision-map.zip"))
		{
			assertNotNull("missing collision-map.zip", in);
			map = CollisionMap.load(in);
		}
		transports = TransportData.load(RouteTest::file);
		finder = new Pathfinder(map, transports);
	}

	/** Someone with no levels, quests or items. */
	private static PlayerState nobody()
	{
		return new PlayerState();
	}

	@Test
	public void tilesPackAndUnpack()
	{
		int p = Pos.pack(3222, 3218, 2);
		assertEquals(3222, Pos.x(p));
		assertEquals(3218, Pos.y(p));
		assertEquals(2, Pos.plane(p));
		assertEquals(p, Pos.parse("3222 3218 2"));
		assertEquals(Pos.NONE, Pos.parse("somewhere"));
		assertEquals(0, Pos.wildernessLevel(LUMBRIDGE));
		assertTrue(Pos.wildernessLevel(Pos.pack(3100, 3800, 0)) > 30);
	}

	@Test
	public void dataLoads()
	{
		assertTrue("transports", transports.size() > 5000);
		assertTrue("teleports", transports.teleports().size() > 300);
		assertFalse("Lumbridge courtyard is walkable", map.isBlocked(LUMBRIDGE));
	}

	@Test
	public void questNamesInTheTransportDataAreRealQuests()
	{
		Set<String> real = new HashSet<>();
		for (Quest q : Quest.values())
		{
			real.add(q.getName());
		}
		for (String q : transports.questNames())
		{
			assertTrue("not a quest RuneLite knows: " + q, real.contains(q));
		}
	}

	@Test
	public void walksFromLumbridgeToTheGrandExchange()
	{
		Route r = finder.find(LUMBRIDGE, Collections.singleton(GRAND_EXCHANGE), nobody());
		assertNotNull(r);
		assertTrue(r.isReached());
		assertEquals(LUMBRIDGE, r.getTiles()[0]);
		assertEquals(GRAND_EXCHANGE, r.getEnd());
		// about 300 tiles on foot, and each step is to a neighbouring tile unless a transport is used
		assertTrue("length " + r.getCost(), r.getCost() > 250 && r.getCost() < 400);
		for (int i = 1; i < r.getTiles().length; i++)
		{
			if (r.hopInto(i) == null)
			{
				assertEquals(1, Pos.distance(r.getTiles()[i - 1], r.getTiles()[i]));
			}
		}
	}

	@Test
	public void aTeleportIsUsedOnlyWhenYouCan()
	{
		// a Varrock teleport tablet in the inventory makes the trip much shorter
		PlayerState s = nobody();
		Route walk = finder.find(LUMBRIDGE, Collections.singleton(GRAND_EXCHANGE), s);
		s.carried.put(8007, 1);
		Route tab = finder.find(LUMBRIDGE, Collections.singleton(GRAND_EXCHANGE), s);
		assertTrue(tab.isReached());
		assertTrue(tab.getCost() < walk.getCost());
		assertTrue("first step is the tablet", tab.getSteps().get(0).isTransport());

		// ...unless teleports are turned off
		s.teleports = PlayerState.Teleports.NONE;
		Route off = finder.find(LUMBRIDGE, Collections.singleton(GRAND_EXCHANGE), s);
		assertEquals(walk.getCost(), off.getCost());
	}

	@Test
	public void staysOutOfTheWildernessUnlessGoingThere()
	{
		PlayerState s = PlayerState.maxed();
		s.avoidWilderness = true;
		// Edgeville to Falador never needs the Wilderness
		Route r = finder.find(Pos.pack(3094, 3491, 0), Collections.singleton(Pos.pack(2965, 3380, 0)), s);
		assertTrue(r.isReached());
		for (int tile : r.getTiles())
		{
			assertEquals("tile in the Wilderness: " + Pos.toString(tile), 0, Pos.wildernessLevel(tile));
		}
		// but a spot in the Wilderness can still be reached
		Route in = finder.find(Pos.pack(3094, 3491, 0), Collections.singleton(Pos.pack(3000, 3900, 0)), s);
		assertTrue(in.isReached());
	}

	@Test
	public void goesToTheNearestOfSeveralPlaces() throws Exception
	{
		Places places = Places.empty();
		try (InputStream in = file("places.txt"))
		{
			assertNotNull("missing places.txt", in);
			places.add(in);
		}
		Places.Place banks = places.find("place", "Nearest bank");
		assertNotNull(banks);
		assertTrue(banks.getTiles().size() > 50);
		Route r = finder.find(LUMBRIDGE, banks.getTiles(), nobody());
		assertTrue(r.isReached());
		// the bank at the top of Lumbridge Castle, not one across the map
		assertTrue("ended at " + Pos.toString(r.getEnd()), Pos.distance(r.getEnd(), LUMBRIDGE) < 30);
	}

	@Test
	public void questPlacesAreRealQuests() throws Exception
	{
		Places places = Places.empty();
		try (InputStream in = file("places.txt"))
		{
			places.add(in);
		}
		Set<String> real = new HashSet<>();
		for (Quest q : Quest.values())
		{
			real.add(q.getName());
		}
		int quests = 0;
		for (Places.Place p : places.all())
		{
			if ("quest".equals(p.getKind()))
			{
				quests++;
				assertTrue("not a quest RuneLite knows: " + p.getName(), real.contains(p.getName()));
			}
		}
		assertTrue(quests > 100);
		assertNotNull(places.find("quest", "Cook's Assistant"));
	}

	@Test
	public void anUnreachablePlaceGetsYouAsCloseAsItCan()
	{
		// the middle of the sea south of Karamja
		Route r = finder.find(LUMBRIDGE, Collections.singleton(Pos.pack(2900, 2850, 0)), nobody());
		assertNotNull(r);
		assertFalse(r.isReached());
		assertTrue(r.getTiles().length > 1);
	}
}

package com.moneymaker.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.moneymaker.data.Training;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/** Every training spot is a real method, a tile you can stand on, and reachable on the map. */
public class SpotsTest
{
	private static final int LUMBRIDGE = Pos.pack(3222, 3218, 0);

	private static Places places;
	private static Spots spots;
	private static CollisionMap map;
	private static Pathfinder finder;

	@BeforeClass
	public static void load() throws Exception
	{
		places = Places.empty();
		try (InputStream in = RouteService.class.getResourceAsStream("places.txt"))
		{
			places.add(in);
		}
		try (InputStream in = RouteService.class.getResourceAsStream("trainspots.txt"))
		{
			assertNotNull("missing trainspots.txt", in);
			spots = Spots.load(in, places);
		}
		try (InputStream in = RouteService.class.getResourceAsStream("collision-map.zip"))
		{
			map = CollisionMap.load(in);
		}
		finder = new Pathfinder(map, TransportData.load(RouteTest::file));
	}

	@Test
	public void everyNameIsATrainingMethod() throws Exception
	{
		Training training;
		try (InputStream in = Training.class.getResourceAsStream("/com/moneymaker/training.txt"))
		{
			training = Training.load(in);
		}
		Set<String> methods = new HashSet<>();
		for (String skill : training.getSkills())
		{
			for (Training.Method m : training.getMethods(skill))
			{
				methods.add(m.getName());
			}
		}
		List<String> unknown = new ArrayList<>();
		for (String name : spots.all().keySet())
		{
			if (!methods.contains(name))
			{
				unknown.add(name);
			}
		}
		assertTrue("not in training.txt: " + unknown, unknown.isEmpty());
		assertTrue(spots.all().size() > 100);
	}

	@Test
	public void bankMethodsGoToTheNearestBank()
	{
		Places.Place p = spots.find("High Level Alchemy");
		assertNotNull(p);
		assertEquals(places.find("place", Spots.BANK_PLACE).getTiles(), p.getTiles());
	}

	@Test
	public void everySpotCanBeStoodOnAndReached()
	{
		PlayerState maxed = PlayerState.maxed();
		List<String> problems = new ArrayList<>();
		Set<Integer> checked = new HashSet<>();
		Places.Place bank = places.find("place", Spots.BANK_PLACE);
		for (Map.Entry<String, Places.Place> e : spots.all().entrySet())
		{
			if (e.getValue().getTiles().equals(bank.getTiles()))
			{
				continue; // the bank list is already checked by RouteTest
			}
			for (int tile : e.getValue().getTiles())
			{
				if (!checked.add(tile))
				{
					continue;
				}
				if (map.isBlocked(tile))
				{
					problems.add(e.getKey() + ": can't stand on " + Pos.toString(tile));
					continue;
				}
				Route r = finder.find(LUMBRIDGE, Collections.singleton(tile), maxed);
				if (r == null || !r.isReached())
				{
					problems.add(e.getKey() + ": can't reach " + Pos.toString(tile));
				}
			}
		}
		assertTrue(String.join("\n", problems), problems.isEmpty());
	}
}

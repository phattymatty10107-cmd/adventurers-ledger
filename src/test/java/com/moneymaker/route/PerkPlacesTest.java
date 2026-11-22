package com.moneymaker.route;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/** The boss and farm patch places: real names, tiles you can stand on, and reachable on the map. */
public class PerkPlacesTest
{
	private static final int LUMBRIDGE = Pos.pack(3222, 3218, 0);

	private static Places places;
	private static CollisionMap map;
	private static Pathfinder finder;

	@BeforeClass
	public static void load() throws Exception
	{
		places = Places.empty();
		try (InputStream in = RouteService.class.getResourceAsStream("places-bosses-farms.txt"))
		{
			assertNotNull("missing places-bosses-farms.txt", in);
			places.add(in);
		}
		try (InputStream in = RouteService.class.getResourceAsStream("collision-map.zip"))
		{
			map = CollisionMap.load(in);
		}
		finder = new Pathfinder(map, TransportData.load(RouteTest::file));
	}

	/** Lines of a data file in the plugin's resources folder. */
	private static List<String> lines(String file) throws Exception
	{
		List<String> out = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
			RouteService.class.getResourceAsStream("/com/moneymaker/" + file), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				out.add(line);
			}
		}
		return out;
	}

	@Test
	public void bossNamesAreBossesOnTheBossTab() throws Exception
	{
		Set<String> bosses = new HashSet<>();
		for (String line : lines("bosses.txt"))
		{
			if (line.startsWith("BOSS|"))
			{
				bosses.add(line.split("\\|", -1)[1]);
			}
		}
		int count = 0;
		for (Places.Place p : places.all())
		{
			if ("boss".equals(p.getKind()))
			{
				count++;
				assertTrue("not a boss in bosses.txt: " + p.getName(), bosses.contains(p.getName()));
			}
		}
		assertTrue(count > 40);
	}

	@Test
	public void everyFarmStopHasAPlace() throws Exception
	{
		List<String> missing = new ArrayList<>();
		for (String line : lines("farmroutes.txt"))
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\\|", -1);
			String name = f[0].trim() + ": " + f[1].trim();
			if (places.find("farm", name) == null)
			{
				missing.add(name);
			}
		}
		assertTrue("farm stops with no place: " + missing, missing.isEmpty());
	}

	@Test
	public void everyPlaceCanBeStoodOnAndReached()
	{
		PlayerState maxed = PlayerState.maxed();
		List<String> problems = new ArrayList<>();
		Set<Integer> checked = new HashSet<>();
		for (Places.Place p : places.all())
		{
			for (int tile : p.getTiles())
			{
				if (!checked.add(tile))
				{
					continue;
				}
				if (map.isBlocked(tile))
				{
					problems.add(p.getName() + ": can't stand on " + Pos.toString(tile));
					continue;
				}
				Route r = finder.find(LUMBRIDGE, Collections.singleton(tile), maxed);
				if (r == null || !r.isReached())
				{
					problems.add(p.getName() + ": can't reach " + Pos.toString(tile));
				}
			}
		}
		assertTrue(String.join("\n", problems), problems.isEmpty());
	}
}

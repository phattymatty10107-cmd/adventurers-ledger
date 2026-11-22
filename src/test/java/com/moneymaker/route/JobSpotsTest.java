package com.moneymaker.route;

import static org.junit.Assert.assertEquals;
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

/** The Jobs tab's places: real job names, references that resolve, tiles you can stand on and reach. */
public class JobSpotsTest
{
	private static final int LUMBRIDGE = Pos.pack(3222, 3218, 0);

	private static Places places;
	private static Spots spots;
	private static List<String[]> lines;
	private static CollisionMap map;
	private static Pathfinder finder;

	@BeforeClass
	public static void load() throws Exception
	{
		places = Places.empty();
		for (String file : new String[]{"places.txt", "places-bosses-farms.txt"})
		{
			try (InputStream in = RouteService.class.getResourceAsStream(file))
			{
				places.add(in);
			}
		}
		try (InputStream in = RouteService.class.getResourceAsStream("jobspots.txt"))
		{
			assertNotNull("missing jobspots.txt", in);
			spots = Spots.load(in, places);
		}
		lines = new ArrayList<>();
		for (String line : read("route/jobspots.txt"))
		{
			if (!line.trim().isEmpty() && !line.startsWith("#"))
			{
				lines.add(line.split("\\|", -1));
			}
		}
		try (InputStream in = RouteService.class.getResourceAsStream("collision-map.zip"))
		{
			map = CollisionMap.load(in);
		}
		finder = new Pathfinder(map, TransportData.load(RouteTest::file));
	}

	private static List<String> read(String file) throws Exception
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
	public void everyNameIsAJob() throws Exception
	{
		Set<String> jobs = new HashSet<>();
		for (String line : read("recipes.txt"))
		{
			if (!line.startsWith("#") && line.contains("|"))
			{
				jobs.add(line.split("\\|", -1)[1]);
			}
		}
		List<String> unknown = new ArrayList<>();
		for (String[] f : lines)
		{
			if (!jobs.contains(f[0]))
			{
				unknown.add(f[0]);
			}
		}
		assertTrue("not in recipes.txt: " + unknown, unknown.isEmpty());
		assertTrue("most jobs have a place", lines.size() > 500);
	}

	@Test
	public void everyLineLoads()
	{
		// a line is skipped when its tiles don't parse or its @place/@boss isn't loaded
		assertEquals(lines.size(), spots.all().size());
	}

	@Test
	public void everyTileCanBeStoodOnAndReached()
	{
		PlayerState maxed = PlayerState.maxed();
		List<String> problems = new ArrayList<>();
		Set<Integer> checked = new HashSet<>();
		for (String[] f : lines)
		{
			if (f[1].equals("BANK") || f[1].startsWith("@"))
			{
				continue; // banks and places are checked by RouteTest and PerkPlacesTest
			}
			for (int tile : spots.find(f[0]).getTiles())
			{
				if (!checked.add(tile))
				{
					continue;
				}
				if (map.isBlocked(tile))
				{
					problems.add(f[0] + ": can't stand on " + Pos.toString(tile));
					continue;
				}
				Route r = finder.find(LUMBRIDGE, Collections.singleton(tile), maxed);
				if (r == null || !r.isReached())
				{
					problems.add(f[0] + ": can't reach " + Pos.toString(tile));
				}
			}
		}
		assertTrue(String.join("\n", problems), problems.isEmpty());
	}
}

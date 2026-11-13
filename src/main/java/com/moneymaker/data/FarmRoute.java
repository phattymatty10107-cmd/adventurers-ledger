package com.moneymaker.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Where to go on each farm run, in order, loaded from farmroutes.txt. */
public class FarmRoute
{
	/** Route key (a run's name or its group, lower case) to its stops in visiting order. */
	private final Map<String, List<Stop>> byKey;

	private FarmRoute(Map<String, List<Stop>> byKey)
	{
		this.byKey = byKey;
	}

	public static FarmRoute empty()
	{
		return new FarmRoute(Collections.emptyMap());
	}

	/** The stops for a run: a route written for that run by name wins, otherwise its group's route. */
	public List<Stop> stopsFor(FarmRun run)
	{
		List<Stop> stops = byKey.get(key(run.getName()));
		if (stops == null)
		{
			stops = byKey.get(key(run.getGroup()));
		}
		return stops == null ? Collections.emptyList() : stops;
	}

	private static String key(String s)
	{
		return s.trim().toLowerCase(java.util.Locale.ROOT);
	}

	/** One place to visit. */
	public static class Stop
	{
		private final String name;
		private final String travel;
		private final String needs;

		Stop(String name, String travel, String needs)
		{
			this.name = name;
			this.travel = travel;
			this.needs = needs;
		}

		public String getName()
		{
			return name;
		}

		/** How to get there. May be empty. */
		public String getTravel()
		{
			return travel;
		}

		/** A level, quest or other thing you need for this stop. Empty if nothing. */
		public String getNeeds()
		{
			return needs;
		}
	}

	/** Parse farmroutes.txt: route|stop|how to get there|what you need */
	public static FarmRoute load(InputStream in) throws IOException
	{
		Map<String, List<Stop>> map = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] f = line.split("\\|", -1);
				if (f.length < 2 || f[0].trim().isEmpty() || f[1].trim().isEmpty())
				{
					continue;
				}
				map.computeIfAbsent(key(f[0]), k -> new ArrayList<>())
					.add(new Stop(f[1].trim(), f.length > 2 ? f[2].trim() : "", f.length > 3 ? f[3].trim() : ""));
			}
		}
		for (Map.Entry<String, List<Stop>> e : map.entrySet())
		{
			e.setValue(Collections.unmodifiableList(e.getValue()));
		}
		return new FarmRoute(map);
	}
}

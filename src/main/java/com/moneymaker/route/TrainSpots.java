package com.moneymaker.route;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where to go for each training method, from trainspots.txt:
 * method name|x y plane;x y plane|where. "BANK" in place of tiles means the nearest bank.
 */
public final class TrainSpots
{
	/** Name of the "Nearest bank" place in places.txt, used for BANK lines. */
	static final String BANK_PLACE = "Nearest bank";

	private final Map<String, Places.Place> byMethod = new HashMap<>();

	public static TrainSpots empty()
	{
		return new TrainSpots();
	}

	/**
	 * Read trainspots.txt. {@code places} supplies the bank tiles for BANK lines; lines that
	 * don't parse are skipped.
	 */
	public static TrainSpots load(InputStream in, Places places) throws IOException
	{
		TrainSpots spots = new TrainSpots();
		Places.Place bank = places == null ? null : places.find("place", BANK_PLACE);
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = r.readLine()) != null)
		{
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\\|", -1);
			if (f.length < 3 || f[0].trim().isEmpty())
			{
				continue;
			}
			String method = f[0].trim();
			String where = f[2].trim();
			List<Integer> tiles = new ArrayList<>();
			if ("BANK".equalsIgnoreCase(f[1].trim()))
			{
				if (bank == null)
				{
					continue;
				}
				tiles.addAll(bank.getTiles());
			}
			else
			{
				for (String p : f[1].split(";"))
				{
					int tile = Pos.parse(p);
					if (tile != Pos.NONE)
					{
						tiles.add(tile);
					}
				}
			}
			if (tiles.isEmpty())
			{
				continue;
			}
			spots.byMethod.put(method, new Places.Place("train", where.isEmpty() ? method : where,
				Collections.unmodifiableList(tiles), ""));
		}
		return spots;
	}

	/** Where to go for this training method, or null if it has no single place. */
	public Places.Place find(String methodName)
	{
		return methodName == null ? null : byMethod.get(methodName);
	}

	/** Every method name with a place, for checks. */
	public Map<String, Places.Place> all()
	{
		return Collections.unmodifiableMap(byMethod);
	}
}

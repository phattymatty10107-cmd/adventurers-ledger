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
import java.util.Locale;
import java.util.Map;

/**
 * Where to go for each training method (trainspots.txt) or money maker (jobspots.txt).
 * Each line is: name|where to go|a short note. "Where to go" is one of:
 * <ul>
 * <li>tiles: {@code x y plane;x y plane} (the route goes to whichever is quickest to reach)</li>
 * <li>{@code BANK}: the nearest bank</li>
 * <li>{@code @place:<name>} or {@code @boss:<name>}: a place already in the places files</li>
 * </ul>
 * Lines that don't parse, or refer to a place that isn't loaded, are skipped.
 */
public final class Spots
{
	/** Name of the "Nearest bank" place in places.txt, used for BANK lines. */
	static final String BANK_PLACE = "Nearest bank";

	private final Map<String, Places.Place> byName = new HashMap<>();

	public static Spots empty()
	{
		return new Spots();
	}

	/** Read a spots file. {@code places} supplies the tiles for BANK, @place and @boss lines. */
	public static Spots load(InputStream in, Places places) throws IOException
	{
		Spots spots = new Spots();
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
			String name = f[0].trim();
			String where = f[2].trim();
			List<Integer> tiles = tiles(f[1].trim(), places);
			if (tiles.isEmpty())
			{
				continue;
			}
			spots.byName.put(name, new Places.Place("spot", where.isEmpty() ? name : where,
				Collections.unmodifiableList(tiles), ""));
		}
		return spots;
	}

	/** The tiles a "where to go" field stands for, or an empty list. */
	static List<Integer> tiles(String spec, Places places)
	{
		Places.Place ref = null;
		if ("BANK".equalsIgnoreCase(spec))
		{
			ref = places == null ? null : places.find("place", BANK_PLACE);
		}
		else if (spec.startsWith("@"))
		{
			int colon = spec.indexOf(':');
			if (colon > 1 && places != null)
			{
				ref = places.find(spec.substring(1, colon).toLowerCase(Locale.ROOT), spec.substring(colon + 1).trim());
			}
		}
		else
		{
			List<Integer> tiles = new ArrayList<>();
			for (String p : spec.split(";"))
			{
				int tile = Pos.parse(p);
				if (tile != Pos.NONE)
				{
					tiles.add(tile);
				}
			}
			return tiles;
		}
		return ref == null ? Collections.emptyList() : new ArrayList<>(ref.getTiles());
	}

	/** Where to go for this method or job, or null if it has no single place. */
	public Places.Place find(String name)
	{
		return name == null ? null : byName.get(name);
	}

	/** Every name with a place, for checks. */
	public Map<String, Places.Place> all()
	{
		return Collections.unmodifiableMap(byName);
	}
}

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

/** Named places you can ask for a route to, loaded from the places files: kind|name|x y plane;x y plane|note */
public final class Places
{
	/** Somewhere to go. With more than one tile (all the banks, say) the route goes to the nearest. */
	public static final class Place
	{
		private final String kind;
		private final String name;
		private final List<Integer> tiles;
		private final String note;

		public Place(String kind, String name, List<Integer> tiles, String note)
		{
			this.kind = kind;
			this.name = name;
			this.tiles = tiles;
			this.note = note;
		}

		/** "quest", "place", "boss" or "farm". */
		public String getKind()
		{
			return kind;
		}

		public String getName()
		{
			return name;
		}

		/** Tiles packed with {@link Pos}. */
		public List<Integer> getTiles()
		{
			return tiles;
		}

		/** Something worth knowing ("the entrance, the boss is inside"). May be empty. */
		public String getNote()
		{
			return note;
		}
	}

	private final List<Place> all = new ArrayList<>();
	private final Map<String, Place> byKey = new HashMap<>();

	public static Places empty()
	{
		return new Places();
	}

	/** Add the places in a file to this list. Lines that don't parse are skipped. */
	public Places add(InputStream in) throws IOException
	{
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
			if (f.length < 3)
			{
				continue;
			}
			List<Integer> tiles = new ArrayList<>();
			for (String p : f[2].split(";"))
			{
				int tile = Pos.parse(p);
				if (tile != Pos.NONE)
				{
					tiles.add(tile);
				}
			}
			if (tiles.isEmpty() || f[1].trim().isEmpty())
			{
				continue;
			}
			Place place = new Place(f[0].trim().toLowerCase(Locale.ROOT), f[1].trim(),
				Collections.unmodifiableList(tiles), f.length > 3 ? f[3].trim() : "");
			all.add(place);
			byKey.put(key(place.kind, place.name), place);
		}
		return this;
	}

	private static String key(String kind, String name)
	{
		return kind + "|" + name.trim().toLowerCase(Locale.ROOT);
	}

	/** The place of this kind with this name, or null. */
	public Place find(String kind, String name)
	{
		return byKey.get(key(kind, name));
	}

	public List<Place> all()
	{
		return Collections.unmodifiableList(all);
	}

	/** Places of a kind whose name contains the text, names that start with it first. */
	public List<Place> search(String kind, String text, int max)
	{
		String q = text.trim().toLowerCase(Locale.ROOT);
		List<Place> starts = new ArrayList<>();
		List<Place> contains = new ArrayList<>();
		for (Place p : all)
		{
			if (kind != null && !kind.equals(p.kind))
			{
				continue;
			}
			String n = p.name.toLowerCase(Locale.ROOT);
			if (n.startsWith(q))
			{
				starts.add(p);
			}
			else if (n.contains(q))
			{
				contains.add(p);
			}
		}
		starts.addAll(contains);
		return starts.size() > max ? new ArrayList<>(starts.subList(0, max)) : starts;
	}
}

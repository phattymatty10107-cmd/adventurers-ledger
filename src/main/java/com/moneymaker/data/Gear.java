package com.moneymaker.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Skilling tools, outfits and useful items per skill (gear.txt), checked against what you own. */
public class Gear
{
	/** An item you own: total quantity and where it is ("worn", "bag", "bank"). */
	public static class Owned
	{
		private final String name;
		private final long qty;
		private final String where;

		public Owned(String name, long qty, String where)
		{
			this.name = name;
			this.qty = qty;
			this.where = where;
		}

		public String getName()
		{
			return name;
		}

		public long getQty()
		{
			return qty;
		}

		public String getWhere()
		{
			return where;
		}
	}

	/** One line of the gear check. */
	public static class Line
	{
		public enum Kind
		{
			GOOD, UPGRADE, MISSING, INFO
		}

		private final Kind kind;
		private final String text;

		Line(Kind kind, String text)
		{
			this.kind = kind;
			this.text = text;
		}

		public Kind getKind()
		{
			return kind;
		}

		public String getText()
		{
			return text;
		}
	}

	private static class Tier
	{
		final int level;
		final String item;
		/** Same strength as the tier before it (e.g. gilded, 3rd age, infernal): counts if owned, never suggested. */
		final boolean equivalent;

		Tier(int level, String item)
		{
			this.level = level;
			this.equivalent = item.startsWith("=");
			this.item = equivalent ? item.substring(1) : item;
		}
	}

	private static class SkillGear
	{
		final Map<String, List<Tier>> tools = new LinkedHashMap<>();
		final List<String[]> outfits = new ArrayList<>();
		final List<String[]> useful = new ArrayList<>();
	}

	private final Map<String, SkillGear> bySkill;

	private Gear(Map<String, SkillGear> bySkill)
	{
		this.bySkill = bySkill;
	}

	public static Gear empty()
	{
		return new Gear(Collections.emptyMap());
	}

	/** Find an owned item by name; variants like "Dragon pickaxe (or)" count. */
	public static Owned find(Map<String, Owned> owned, String item)
	{
		String key = item.toLowerCase(Locale.ROOT);
		Owned exact = owned.get(key);
		if (exact != null)
		{
			return exact;
		}
		for (Map.Entry<String, Owned> e : owned.entrySet())
		{
			if (e.getKey().startsWith(key + " (") || e.getKey().startsWith(key + "("))
			{
				return e.getValue();
			}
		}
		return null;
	}

	/** What you have for a skill, the next upgrade you can use, and what's missing. */
	public List<Line> check(String skill, int level, Map<String, Owned> owned)
	{
		List<Line> out = new ArrayList<>();
		SkillGear g = bySkill.get(skill);
		if (g == null)
		{
			return out;
		}
		for (Map.Entry<String, List<Tier>> e : g.tools.entrySet())
		{
			Tier bestOwned = null;
			Owned bestOwnedItem = null;
			for (Tier t : e.getValue())
			{
				Owned o = find(owned, t.item);
				if (o != null && t.level <= level)
				{
					bestOwned = t;
					bestOwnedItem = o;
				}
			}
			Tier canUse = null;
			Tier next = null;
			for (Tier t : e.getValue())
			{
				int ownedLevel = bestOwned == null ? -1 : e.getValue().indexOf(bestOwned);
				if (e.getValue().indexOf(t) <= ownedLevel || t.equivalent)
				{
					continue;
				}
				if (t.level <= level)
				{
					canUse = t;
				}
				else if (next == null)
				{
					next = t;
				}
			}
			String group = e.getKey();
			if (bestOwned != null)
			{
				out.add(new Line(Line.Kind.GOOD, group + ": " + bestOwnedItem.getName() + " (" + bestOwnedItem.getWhere() + ")"));
			}
			else
			{
				out.add(new Line(Line.Kind.MISSING, group + ": none found"));
			}
			if (canUse != null)
			{
				out.add(new Line(Line.Kind.UPGRADE, "Upgrade you can use now: " + canUse.item + " (" + canUse.level + ")"));
			}
			else if (next != null)
			{
				out.add(new Line(Line.Kind.INFO, "Next: " + next.item + " at " + next.level));
			}
		}
		for (String[] o : g.outfits)
		{
			List<String> pieces = Arrays.asList(o[1].split(";"));
			List<String> missing = new ArrayList<>();
			for (String p : pieces)
			{
				if (find(owned, p) == null)
				{
					missing.add(p);
				}
			}
			int have = pieces.size() - missing.size();
			if (missing.isEmpty())
			{
				out.add(new Line(Line.Kind.GOOD, o[0] + ": full set (" + o[2] + ")"));
			}
			else
			{
				out.add(new Line(have > 0 ? Line.Kind.UPGRADE : Line.Kind.MISSING, o[0] + ": " + have + "/" + pieces.size()
					+ " (" + o[2] + ")" + (have > 0 ? " · missing " + shortList(missing) : "")));
			}
		}
		for (String[] u : g.useful)
		{
			Owned o = find(owned, u[0]);
			out.add(o != null
				? new Line(Line.Kind.GOOD, u[0] + (o.getQty() > 1 ? " ×" + String.format(Locale.ROOT, "%,d", o.getQty()) : "") + " (" + o.getWhere() + ")")
				: new Line(Line.Kind.MISSING, u[0] + ": not found · " + u[1]));
		}
		return out;
	}

	private static String shortList(List<String> items)
	{
		List<String> s = new ArrayList<>();
		for (String i : items)
		{
			String[] w = i.split(" ");
			s.add(w[w.length - 1]);
		}
		return String.join(", ", s);
	}

	/** Parse gear.txt. */
	public static Gear load(InputStream in) throws IOException
	{
		Map<String, SkillGear> map = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				String[] f = line.split("\\|", -1);
				if (f.length < 4)
				{
					continue;
				}
				SkillGear g = map.computeIfAbsent(f[1], k -> new SkillGear());
				if ("T".equals(f[0]) && f.length >= 5)
				{
					g.tools.computeIfAbsent(f[2], k -> new ArrayList<>()).add(new Tier(Integer.parseInt(f[3]), f[4]));
				}
				else if ("O".equals(f[0]) && f.length >= 5)
				{
					g.outfits.add(new String[]{f[2], f[3], f[4]});
				}
				else if ("U".equals(f[0]))
				{
					g.useful.add(new String[]{f[2], f[3]});
				}
			}
		}
		return new Gear(map);
	}
}

package com.moneymaker.data;

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
 * Prices gear. Most items trade on the GE under their own name; the rest are listed in
 * aliases.txt, either as tradeable parts (e.g. a charged weapon's uncharged version) or
 * as untradeable with a note on how to get them.
 */
public class GearPricer
{
	private final Map<String, Alias> aliases;

	public GearPricer(Map<String, Alias> aliases)
	{
		this.aliases = aliases;
	}

	/** Price of one gear option. */
	public static class Price
	{
		private final String name;
		/** 0 when untradeable or no price. */
		private final long cost;
		/** Item to show an icon for, -1 if none. */
		private final int iconId;
		private final String note;
		private final boolean tradeable;

		Price(String name, long cost, int iconId, String note, boolean tradeable)
		{
			this.name = name;
			this.cost = cost;
			this.iconId = iconId;
			this.note = note;
			this.tradeable = tradeable;
		}

		public String getName()
		{
			return name;
		}

		public long getCost()
		{
			return cost;
		}

		public int getIconId()
		{
			return iconId;
		}

		public String getNote()
		{
			return note;
		}

		public boolean isTradeable()
		{
			return tradeable;
		}
	}

	public Price price(String name, PriceBook prices)
	{
		if (name.contains(" + "))
		{
			return combo(name, prices);
		}

		Alias alias = aliases.get(PriceBook.key(name));
		if (alias == null)
		{
			ItemPrice p = prices.get(name);
			if (p != null && p.typical() > 0)
			{
				return new Price(name, p.typical(), p.getId(), null, true);
			}
			return new Price(name, 0, p != null ? p.getId() : -1, p != null ? "No recent price" : "Untradeable", false);
		}

		if (alias.parts.isEmpty())
		{
			return new Price(name, 0, -1, alias.note, false);
		}

		if (alias.anyOf)
		{
			// cheapest of several equivalent items
			Price best = null;
			for (Recipe.Stack s : alias.parts)
			{
				ItemPrice p = prices.get(s.getItem());
				if (p != null && p.typical() > 0 && (best == null || p.typical() < best.getCost()))
				{
					best = new Price(name, p.typical(), p.getId(), alias.note + " (cheapest: " + p.getName() + ")", true);
				}
			}
			return best != null ? best : new Price(name, 0, -1, alias.note, false);
		}

		long total = 0;
		int icon = -1;
		for (Recipe.Stack s : alias.parts)
		{
			ItemPrice p = prices.get(s.getItem());
			if (p == null || p.typical() <= 0)
			{
				return new Price(name, 0, -1, alias.note, false);
			}
			total += Math.round(p.typical() * s.getQty());
			if (icon < 0)
			{
				icon = p.getId();
			}
		}
		return new Price(name, total, icon, alias.note, true);
	}

	/** Several items used together (e.g. weapon switches): price the tradeable ones, list the rest. */
	private Price combo(String name, PriceBook prices)
	{
		long total = 0;
		int icon = -1;
		List<String> unpriced = new ArrayList<>();
		for (String part : name.split(" \\+ "))
		{
			Price p = price(part.trim(), prices);
			if (p.isTradeable())
			{
				total += p.getCost();
				if (icon < 0)
				{
					icon = p.getIconId();
				}
			}
			else
			{
				unpriced.add(part.trim());
			}
		}
		String note = "Used together (switches)" + (unpriced.isEmpty() ? "" : "; not priced: " + String.join(", ", unpriced));
		return new Price(name, total, icon, note, total > 0);
	}

	/** Cheapest priced option in a tier, or an untradeable option if none are priced. */
	public Price cheapestOf(List<String> options, PriceBook prices)
	{
		Price best = null;
		Price fallback = null;
		for (String o : options)
		{
			Price p = price(o, prices);
			if (p.isTradeable())
			{
				if (best == null || p.getCost() < best.getCost())
				{
					best = p;
				}
			}
			else if (fallback == null)
			{
				fallback = p;
			}
		}
		return best != null ? best : fallback;
	}

	static class Alias
	{
		final List<Recipe.Stack> parts;
		final boolean anyOf;
		final String note;

		Alias(List<Recipe.Stack> parts, boolean anyOf, String note)
		{
			this.parts = parts;
			this.anyOf = anyOf;
			this.note = note;
		}
	}

	/** Parse aliases.txt: name|parts|note. Parts are "A*1 + B*2" (all needed) or "A, B, C" (any one). */
	public static GearPricer load(InputStream in) throws IOException
	{
		Map<String, Alias> map = new HashMap<>();
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
				if (f.length < 3)
				{
					continue;
				}
				String partsText = f[1].trim();
				boolean anyOf = partsText.contains(",");
				List<Recipe.Stack> parts = new ArrayList<>();
				if (!partsText.isEmpty())
				{
					for (String p : partsText.split(anyOf ? "," : "\\+"))
					{
						p = p.trim();
						int star = p.lastIndexOf('*');
						if (star > 0)
						{
							parts.add(new Recipe.Stack(p.substring(0, star).trim(), Integer.parseInt(p.substring(star + 1).trim())));
						}
						else
						{
							parts.add(new Recipe.Stack(p, 1));
						}
					}
				}
				map.put(PriceBook.key(f[0]), new Alias(Collections.unmodifiableList(parts), anyOf, f[2].trim()));
			}
		}
		return new GearPricer(map);
	}
}

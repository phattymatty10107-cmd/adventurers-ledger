package com.moneymaker.data;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/** All current prices, looked up by item name (case-insensitive). */
public class PriceBook
{
	public static final PriceBook EMPTY = new PriceBook(Collections.emptyMap(), 0);

	private final Map<String, ItemPrice> byName;
	private final long updatedAt;

	public PriceBook(Map<String, ItemPrice> byName, long updatedAt)
	{
		this.byName = byName;
		this.updatedAt = updatedAt;
	}

	public static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT);
	}

	public ItemPrice get(String name)
	{
		return byName.get(key(name));
	}

	/**
	 * Items whose name contains the text, best matches first (exact, then starts-with, then shorter
	 * names). Only items with a price are returned.
	 */
	public java.util.List<ItemPrice> search(String text, int max)
	{
		String q = key(text);
		java.util.List<ItemPrice> out = new java.util.ArrayList<>();
		if (q.isEmpty())
		{
			return out;
		}
		for (Map.Entry<String, ItemPrice> e : byName.entrySet())
		{
			if (e.getKey().contains(q) && e.getValue().typical() > 0)
			{
				out.add(e.getValue());
			}
		}
		out.sort(java.util.Comparator
			.comparing((ItemPrice p) -> !key(p.getName()).equals(q))
			.thenComparing(p -> !key(p.getName()).startsWith(q))
			.thenComparing(p -> p.getName().length())
			.thenComparing(ItemPrice::getName));
		return out.size() > max ? new java.util.ArrayList<>(out.subList(0, max)) : out;
	}

	public boolean isEmpty()
	{
		return byName.isEmpty();
	}

	public int size()
	{
		return byName.size();
	}

	public long getUpdatedAt()
	{
		return updatedAt;
	}
}

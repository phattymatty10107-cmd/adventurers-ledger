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

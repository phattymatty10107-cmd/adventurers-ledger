package com.moneymaker.data;

/** Live price info for one item, merged from the wiki's latest and 1h endpoints. */
public class ItemPrice
{
	private final int id;
	private final String name;
	private final int limit;
	/** Latest instant-buy price (someone paid this). */
	private final long high;
	/** Latest instant-sell price (someone sold for this). */
	private final long low;
	private final long avgHigh;
	private final long avgLow;
	private final long hourVolume;
	/** When it last traded (epoch seconds), 0 if unknown. */
	private final long lastTrade;

	public ItemPrice(int id, String name, int limit, long high, long low, long avgHigh, long avgLow, long hourVolume)
	{
		this(id, name, limit, high, low, avgHigh, avgLow, hourVolume, 0);
	}

	public ItemPrice(int id, String name, int limit, long high, long low, long avgHigh, long avgLow, long hourVolume,
		long lastTrade)
	{
		this.lastTrade = lastTrade;
		this.id = id;
		this.name = name;
		this.limit = limit;
		this.high = high;
		this.low = low;
		this.avgHigh = avgHigh;
		this.avgLow = avgLow;
		this.hourVolume = hourVolume;
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	/** GE buy limit per 4 hours, 0 if unknown. */
	public int getLimit()
	{
		return limit;
	}

	public long getHourVolume()
	{
		return hourVolume;
	}

	/** How long a price can go without a trade before we stop trusting it. */
	public static final long STALE_AFTER_MS = 24 * 60 * 60 * 1000L;

	/**
	 * True when nothing traded in the last hour and the last known trade is over a day old,
	 * so the price shown may be nowhere near what you'd really get.
	 */
	public boolean isStale(long nowMs)
	{
		return hourVolume <= 0 && lastTrade > 0 && nowMs - lastTrade * 1000 > STALE_AFTER_MS;
	}

	/** What you'd pay to buy one, for the chosen price mode. 0 if unknown. */
	public long buyPrice(PriceMode mode)
	{
		switch (mode)
		{
			case INSTANT:
				return first(high, avgHigh, low);
			case PATIENT:
				return first(low, avgLow, high);
			default:
				return mid(first(avgHigh, high), first(avgLow, low));
		}
	}

	/** What you'd get for selling one (before tax), for the chosen price mode. 0 if unknown. */
	public long sellPrice(PriceMode mode)
	{
		switch (mode)
		{
			case INSTANT:
				return first(low, avgLow, high);
			case PATIENT:
				return first(high, avgHigh, low);
			default:
				return mid(first(avgHigh, high), first(avgLow, low));
		}
	}

	/** A single "typical" price, used for gear costs. */
	public long typical()
	{
		return mid(first(avgHigh, high), first(avgLow, low));
	}

	private static long first(long... values)
	{
		for (long v : values)
		{
			if (v > 0)
			{
				return v;
			}
		}
		return 0;
	}

	private static long mid(long a, long b)
	{
		if (a <= 0)
		{
			return b;
		}
		if (b <= 0)
		{
			return a;
		}
		return (a + b) / 2;
	}
}

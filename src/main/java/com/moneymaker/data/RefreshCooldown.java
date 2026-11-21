package com.moneymaker.data;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Stops the manual Refresh button from hammering the wiki prices API:
 * a fetch is only allowed once {@link #COOLDOWN_MS} has passed since the last one started.
 */
public final class RefreshCooldown
{
	public static final long COOLDOWN_MS = 60_000L;

	private final AtomicLong lastFetch = new AtomicLong(Long.MIN_VALUE / 2);

	/** Note that a fetch is starting now (scheduled or manual). */
	public void markFetch(long now)
	{
		lastFetch.set(now);
	}

	/** Whole seconds still to wait before a manual refresh is allowed; 0 means go ahead. */
	public long secondsLeft(long now)
	{
		long left = COOLDOWN_MS - (now - lastFetch.get());
		return left <= 0 ? 0 : (left + 999) / 1000;
	}
}

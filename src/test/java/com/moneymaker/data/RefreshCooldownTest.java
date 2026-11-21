package com.moneymaker.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RefreshCooldownTest
{
	private static final long T = 1_800_000_000_000L;

	@Test
	public void firstRefreshIsAllowed()
	{
		assertEquals(0, new RefreshCooldown().secondsLeft(T));
	}

	@Test
	public void clicksInsideTheCooldownAreBlocked()
	{
		RefreshCooldown c = new RefreshCooldown();
		c.markFetch(T);
		assertEquals(60, c.secondsLeft(T));
		assertEquals(31, c.secondsLeft(T + 29_500));
		assertEquals(1, c.secondsLeft(T + 59_999));
	}

	@Test
	public void allowedAgainAfterTheCooldown()
	{
		RefreshCooldown c = new RefreshCooldown();
		c.markFetch(T);
		assertEquals(0, c.secondsLeft(T + RefreshCooldown.COOLDOWN_MS));
		assertEquals(0, c.secondsLeft(T + 120_000));
	}
}

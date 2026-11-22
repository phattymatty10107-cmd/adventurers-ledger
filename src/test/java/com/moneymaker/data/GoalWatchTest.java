package com.moneymaker.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoalWatchTest
{
	@Test
	public void announcesOnceWhenYouLevelPastTheGoal()
	{
		GoalWatch w = new GoalWatch();
		assertFalse(w.update("MINING", 62, 60));
		assertFalse(w.update("MINING", 62, 61));
		assertTrue("reached", w.update("MINING", 62, 62));
		assertFalse("only once", w.update("MINING", 62, 62));
		assertFalse(w.update("MINING", 62, 63));
	}

	@Test
	public void aGoalYouAlreadyHaveIsNotAnnounced()
	{
		GoalWatch w = new GoalWatch();
		assertFalse(w.update("MINING", 50, 62));
		assertFalse(w.update("MINING", 50, 63));
	}

	@Test
	public void changingTheGoalStartsAgain()
	{
		GoalWatch w = new GoalWatch();
		assertFalse(w.update("MINING", 62, 61));
		// switched to a Woodcutting goal you already have: nothing
		assertFalse(w.update("WOODCUTTING", 40, 45));
		// back to the Mining goal and you've reached it: that counts
		assertTrue(w.update("MINING", 62, 62));
		// a new, higher goal waits until you're seen below it and then reach it
		assertFalse(w.update("MINING", 70, 70));
		assertFalse(w.update("MINING", 75, 70));
		assertTrue(w.update("MINING", 75, 75));
	}

	@Test
	public void noGoalOrUnknownLevelDoesNothing()
	{
		GoalWatch w = new GoalWatch();
		assertFalse(w.update(null, 62, 70));
		assertFalse(w.update("MINING", 62, -1));
		w.update("MINING", 62, 61);
		w.reset();
		assertFalse("forgotten after logout", w.update("MINING", 62, 62));
	}
}

package com.moneymaker.data;

/**
 * Notices when you reach the level goal set on the Goal tab. It only counts as reached after it
 * has seen you below the goal, so picking a level you already have, or logging in already past
 * it, doesn't announce anything.
 */
public final class GoalWatch
{
	/** The goal ("MINING:62") last seen below its level this session, or null. */
	private String seenBelow;

	/**
	 * Call with the current goal and your level in its skill.
	 *
	 * @param skill      the goal's skill as saved by the Goal tab, or null when there's no level goal
	 * @param goalLevel  the level wanted
	 * @param level      your level in that skill now, or -1 if unknown
	 * @return true once, at the moment the goal is reached
	 */
	public boolean update(String skill, int goalLevel, int level)
	{
		if (skill == null || goalLevel < 2 || level < 1)
		{
			return false;
		}
		String key = skill + ":" + goalLevel;
		if (level < goalLevel)
		{
			seenBelow = key;
			return false;
		}
		if (key.equals(seenBelow))
		{
			seenBelow = null;
			return true;
		}
		return false;
	}

	/** Forget what was seen, e.g. when you log out. */
	public void reset()
	{
		seenBelow = null;
	}
}

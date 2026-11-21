package com.moneymaker.route;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** What your character can use right now: levels, finished quests, what you're carrying. */
public final class PlayerState
{
	/** Which teleports the route may use. */
	public enum Teleports
	{
		NONE("None"),
		FREE("Only free ones"),
		ALL("Any I can use now");

		private final String label;

		Teleports(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** Skill name as the game shows it ("Agility") to your level. */
	public final Map<String, Integer> levels = new HashMap<>();
	public int totalLevel;
	public int combatLevel;
	/** Names of the quests you've finished. */
	public final Set<String> questsDone = new HashSet<>();
	/** Item id to how many you have in your inventory and worn equipment. */
	public final Map<Integer, Integer> carried = new HashMap<>();
	public final Set<Integer> worn = new HashSet<>();
	public final Map<Integer, Integer> varbits = new HashMap<>();
	public final Map<Integer, Integer> varps = new HashMap<>();
	/** True once the elite Lumbridge diary is done, when fairy rings no longer need a staff. */
	public boolean fairyRingsWithoutStaff;
	public long nowMinutes = System.currentTimeMillis() / 60_000L;
	public Teleports teleports = Teleports.ALL;
	public boolean avoidWilderness = true;

	/** Someone who can use everything: for tests and for checking the data. */
	public static PlayerState maxed()
	{
		PlayerState s = new PlayerState();
		s.everything = true;
		s.avoidWilderness = false;
		return s;
	}

	/** When true every requirement counts as met. */
	boolean everything;

	Map<Integer, Integer> carried()
	{
		return Collections.unmodifiableMap(carried);
	}
}

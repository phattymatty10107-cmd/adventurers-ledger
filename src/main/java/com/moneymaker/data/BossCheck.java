package com.moneymaker.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Can I fight this boss?": the skill levels a boss needs (bossskills.txt) and which of its gear
 * you already own.
 */
public class BossCheck
{
	private final Map<String, List<SkillReq>> skills;

	private BossCheck(Map<String, List<SkillReq>> skills)
	{
		this.skills = skills;
	}

	public static BossCheck empty()
	{
		return new BossCheck(Collections.emptyMap());
	}

	/** Skill levels needed for a boss. Empty when it needs none. */
	public List<SkillReq> skillsFor(String boss)
	{
		return skills.getOrDefault(boss, Collections.emptyList());
	}

	public Map<String, List<SkillReq>> all()
	{
		return skills;
	}

	/** A skill level a boss needs. */
	public static class SkillReq
	{
		private final String skill;
		private final int level;
		private final String note;

		SkillReq(String skill, int level, String note)
		{
			this.skill = skill;
			this.level = level;
			this.note = note;
		}

		public String getSkill()
		{
			return skill;
		}

		public int getLevel()
		{
			return level;
		}

		public String getNote()
		{
			return note;
		}
	}

	/** Parse bossskills.txt: boss|SKILL|level|note */
	public static BossCheck load(InputStream in) throws IOException
	{
		Map<String, List<SkillReq>> map = new LinkedHashMap<>();
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
				int level;
				try
				{
					level = Integer.parseInt(f[2].trim());
				}
				catch (NumberFormatException e)
				{
					continue;
				}
				map.computeIfAbsent(f[0].trim(), k -> new ArrayList<>())
					.add(new SkillReq(f[1].trim(), level, f.length > 3 ? f[3].trim() : ""));
			}
		}
		for (Map.Entry<String, List<SkillReq>> e : map.entrySet())
		{
			e.setValue(Collections.unmodifiableList(e.getValue()));
		}
		return new BossCheck(map);
	}

	// ---------------------------------------------------------------- gear you own

	/**
	 * Whether you own a gear option, and where. "A + B" (used together) counts only if you own
	 * every part. Returns null if you don't.
	 */
	public static Gear.Owned owned(String option, Map<String, Gear.Owned> have)
	{
		if (have == null || have.isEmpty())
		{
			return null;
		}
		Gear.Owned first = null;
		for (String part : option.split(" \\+ "))
		{
			Gear.Owned o = Gear.find(have, part.trim());
			if (o == null)
			{
				return null;
			}
			if (first == null)
			{
				first = o;
			}
		}
		return first;
	}

	/** Any option in a tier that you own, or null. */
	public static Gear.Owned ownedInTier(List<String> tier, Map<String, Gear.Owned> have)
	{
		for (String option : tier)
		{
			Gear.Owned o = owned(option, have);
			if (o != null)
			{
				return o;
			}
		}
		return null;
	}

	/** How your gear stacks up for one boss set-up. */
	public static class Kit
	{
		private final int slots;
		private final int slotsOwned;
		private final long costToFill;
		private final List<String> toBuy;
		private final List<String> toEarn;

		Kit(int slots, int slotsOwned, long costToFill, List<String> toBuy, List<String> toEarn)
		{
			this.slots = slots;
			this.slotsOwned = slotsOwned;
			this.costToFill = costToFill;
			this.toBuy = toBuy;
			this.toEarn = toEarn;
		}

		public int getSlots()
		{
			return slots;
		}

		/** Slots where you own at least one of the listed options. */
		public int getSlotsOwned()
		{
			return slotsOwned;
		}

		/** GE cost of the cheapest option for every slot you own nothing for. */
		public long getCostToFill()
		{
			return costToFill;
		}

		/** The cheapest option to buy for each empty slot. */
		public List<String> getToBuy()
		{
			return toBuy;
		}

		/** Empty slots where nothing listed can be bought (untradeable gear to earn). */
		public List<String> getToEarn()
		{
			return toEarn;
		}
	}

	/** Compare a set-up with what you own: how many slots are covered and what the rest would cost. */
	public static Kit kit(BossSetup setup, GearPricer pricer, PriceBook prices, Map<String, Gear.Owned> have)
	{
		int ownedSlots = 0;
		long cost = 0;
		List<String> toBuy = new ArrayList<>();
		List<String> toEarn = new ArrayList<>();
		for (BossSetup.Slot slot : setup.getSlots())
		{
			boolean covered = false;
			GearPricer.Price cheapest = null;
			for (List<String> tier : slot.getTiers())
			{
				if (ownedInTier(tier, have) != null)
				{
					covered = true;
					break;
				}
				GearPricer.Price p = pricer.cheapestOf(tier, prices);
				if (p != null && p.isTradeable() && (cheapest == null || p.getCost() < cheapest.getCost()))
				{
					cheapest = p;
				}
			}
			if (covered)
			{
				ownedSlots++;
			}
			else if (cheapest != null)
			{
				cost += cheapest.getCost();
				toBuy.add(cheapest.getName());
			}
			else
			{
				toEarn.add(slot.getName());
			}
		}
		return new Kit(setup.getSlots().size(), ownedSlots, cost,
			Collections.unmodifiableList(toBuy), Collections.unmodifiableList(toEarn));
	}
}

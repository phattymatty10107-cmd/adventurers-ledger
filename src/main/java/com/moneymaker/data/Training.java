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

/** XP training methods and milestone levels for every skill, loaded from training.txt, plus a route planner. */
public class Training
{
	private static final int[] XP = new int[100];

	static
	{
		// standard OSRS experience table
		double points = 0;
		for (int lvl = 1; lvl < 100; lvl++)
		{
			XP[lvl] = (int) Math.floor(points / 4);
			points += Math.floor(lvl + 300 * Math.pow(2, lvl / 7.0));
		}
	}

	/** XP needed to reach a level (1-99). */
	public static int xpForLevel(int level)
	{
		return XP[Math.max(1, Math.min(99, level))];
	}

	/** Level for an amount of XP. */
	public static int levelForXp(long xp)
	{
		int lvl = 1;
		while (lvl < 99 && XP[lvl + 1] <= xp)
		{
			lvl++;
		}
		return lvl;
	}

	private final Map<String, List<Method>> methods;
	private final Map<String, List<Milestone>> milestones;

	private Training(Map<String, List<Method>> methods, Map<String, List<Milestone>> milestones)
	{
		this.methods = methods;
		this.milestones = milestones;
	}

	public static Training empty()
	{
		return new Training(Collections.emptyMap(), Collections.emptyMap());
	}

	public List<String> getSkills()
	{
		return new ArrayList<>(methods.keySet());
	}

	public List<Method> getMethods(String skill)
	{
		return methods.getOrDefault(skill, Collections.emptyList());
	}

	public List<Milestone> getMilestones(String skill)
	{
		return milestones.getOrDefault(skill, Collections.emptyList());
	}

	/** A level that unlocks something worth aiming for. */
	public static class Milestone
	{
		private final int level;
		private final String text;

		Milestone(int level, String text)
		{
			this.level = level;
			this.text = text;
		}

		public int getLevel()
		{
			return level;
		}

		public String getText()
		{
			return text;
		}
	}

	/** One way to train a skill. */
	public static class Method
	{
		private final String skill;
		private final String name;
		private final int level;
		private final double xpAtMin;
		private final double xpAt99;
		private final double xpEach;
		private final String unit;
		private final boolean ironman;
		private final boolean members;
		private final boolean intense;
		private final int cycleMinutes;
		private final List<Recipe.Stack> needs;
		private final List<Recipe.Stack> gives;
		private final String requirements;
		private final String note;

		Method(String[] f)
		{
			skill = f[1];
			name = f[2];
			level = Integer.parseInt(f[3]);
			xpAtMin = Double.parseDouble(f[4]);
			xpAt99 = Double.parseDouble(f[5]);
			xpEach = Double.parseDouble(f[6]);
			unit = f[7];
			ironman = "1".equals(f[8]);
			members = "1".equals(f[9]);
			intense = "1".equals(f[10]);
			cycleMinutes = Integer.parseInt(f[11]);
			needs = Recipe.stacks(f[12]);
			gives = Recipe.stacks(f[13]);
			requirements = f[14];
			note = f[15];
		}

		public String getSkill()
		{
			return skill;
		}

		public String getName()
		{
			return name;
		}

		public int getLevel()
		{
			return level;
		}

		/** Typical xp per hour at a level (interpolated between the method's level and 99). */
		public double xpPerHour(int atLevel)
		{
			if (level >= 99)
			{
				return xpAt99;
			}
			double t = Math.max(0, Math.min(1, (atLevel - level) / (double) (99 - level)));
			return xpAtMin + (xpAt99 - xpAtMin) * t;
		}

		/** For ranking: xp per real hour (timed runs are limited by how often you can do them). */
		double effectiveRate(int atLevel)
		{
			return cycleMinutes > 0 && xpEach > 0 ? xpEach * 60.0 / cycleMinutes : xpPerHour(atLevel);
		}

		public double getXpEach()
		{
			return xpEach;
		}

		public String getUnit()
		{
			return unit.isEmpty() ? "action" : unit;
		}

		public boolean isIronman()
		{
			return ironman;
		}

		public boolean isMembers()
		{
			return members;
		}

		public boolean isIntense()
		{
			return intense;
		}

		/** For timed methods (farm runs): minutes between runs, else 0. */
		public int getCycleMinutes()
		{
			return cycleMinutes;
		}

		public List<Recipe.Stack> getNeeds()
		{
			return needs;
		}

		public List<Recipe.Stack> getGives()
		{
			return gives;
		}

		public String getRequirements()
		{
			return requirements;
		}

		public String getNote()
		{
			return note;
		}
	}

	/** Which methods the planner may use. */
	public static class Filter
	{
		public boolean ironman;
		public boolean members = true;
		public boolean intense = true;
		/** Only methods that don't use up supplies. */
		public boolean noSupplies;
		/** Only timed methods (farm runs); they're ranked by xp per real hour. */
		public boolean timedOnly;

		boolean allows(Method m)
		{
			return (!ironman || m.ironman) && (members || !m.members) && (intense || !m.intense)
				&& (!noSupplies || m.needs.isEmpty()) && (!timedOnly || m.cycleMinutes > 0);
		}
	}

	/** One stretch of the route: levels from-to with one method. */
	public static class Step
	{
		private final Method method;
		private final int fromLevel;
		private int toLevel;
		private double xp;
		private double hours;

		Step(Method method, int fromLevel)
		{
			this.method = method;
			this.fromLevel = fromLevel;
			this.toLevel = fromLevel;
		}

		public Method getMethod()
		{
			return method;
		}

		public int getFromLevel()
		{
			return fromLevel;
		}

		public int getToLevel()
		{
			return toLevel;
		}

		public double getXp()
		{
			return xp;
		}

		/** Hours of play. */
		public double getHours()
		{
			return hours;
		}

		/** Actions (kills, logs, casts, runs...), or 0 when the method has no per-action xp. */
		public long getActions()
		{
			return method.xpEach > 0 ? (long) Math.ceil(xp / method.xpEach) : 0;
		}

		/** For timed methods: real days if you do one run every cycle. */
		public double getDays()
		{
			return method.cycleMinutes > 0 ? getActions() * method.cycleMinutes / 1440.0 : 0;
		}
	}

	/**
	 * Fastest route from the current xp to a target level: at every level, use the best method you can.
	 * Returns an empty list if nothing is available.
	 */
	public List<Step> plan(String skill, long fromXp, int toLevel, Filter filter)
	{
		List<Step> steps = new ArrayList<>();
		long xp = fromXp;
		long target = xpForLevel(toLevel);
		Step current = null;
		while (xp < target)
		{
			int lvl = levelForXp(xp);
			long next = Math.min(target, xpForLevel(lvl + 1));
			Method best = best(skill, lvl, filter);
			if (best == null)
			{
				break;
			}
			double rate = best.xpPerHour(lvl);
			if (rate <= 0)
			{
				break;
			}
			if (current == null || current.method != best)
			{
				current = new Step(best, lvl);
				steps.add(current);
			}
			current.xp += next - xp;
			current.hours += (next - xp) / rate;
			current.toLevel = levelForXp(next);
			xp = next;
		}
		return steps;
	}

	/** The fastest method available at a level, or null. */
	public Method best(String skill, int level, Filter filter)
	{
		Method best = null;
		for (Method m : getMethods(skill))
		{
			if (m.level <= level && filter.allows(m) && (best == null || m.effectiveRate(level) > best.effectiveRate(level)))
			{
				best = m;
			}
		}
		return best;
	}

	/** Parse training.txt. */
	public static Training load(InputStream in) throws IOException
	{
		Map<String, List<Method>> methods = new LinkedHashMap<>();
		Map<String, List<Milestone>> milestones = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				String[] f = line.split("\\|", -1);
				if ("M".equals(f[0]) && f.length >= 4)
				{
					milestones.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new Milestone(Integer.parseInt(f[2]), f[3]));
				}
				else if ("T".equals(f[0]) && f.length >= 16)
				{
					methods.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new Method(f));
				}
			}
		}
		milestones.values().forEach(l -> l.sort((a, b) -> Integer.compare(a.level, b.level)));
		return new Training(methods, milestones);
	}
}

package com.moneymaker.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One money-making job, loaded from recipes.txt.
 * Quantities can be fractions, e.g. 0.05 of a rare drop per kill.
 */
public class Recipe
{
	private final String category;
	private final String name;
	/** RuneLite Skill enum name, or "NONE". This is the skill used for the next-level planner. */
	private final String skill;
	private final int level;
	private final boolean members;
	private final double perHour;
	/** Coins paid per action (fees). Negative means coins received, e.g. from pickpocketing. */
	private final double coinsPerAction;
	private final List<Stack> inputs;
	private final List<Stack> outputs;
	private final double xp;
	/** Every skill level needed, including the main one. */
	private final List<Req> reqs;
	/** What one action is, e.g. "kill", "fish", "min". */
	private final String unit;
	/** OSRS Wiki guide for this method, or empty. */
	private final String url;

	public Recipe(String category, String name, String skill, int level, boolean members, double perHour,
		double coinsPerAction, List<Stack> inputs, List<Stack> outputs, double xp, List<Req> reqs, String unit, String url)
	{
		this.category = category;
		this.name = name;
		this.skill = skill;
		this.level = level;
		this.members = members;
		this.perHour = perHour;
		this.coinsPerAction = coinsPerAction;
		this.inputs = inputs;
		this.outputs = outputs;
		this.xp = xp;
		this.reqs = reqs;
		this.unit = unit;
		this.url = url;
	}

	public String getCategory()
	{
		return category;
	}

	public String getName()
	{
		return name;
	}

	public String getSkill()
	{
		return skill;
	}

	public boolean hasSkill()
	{
		return !"NONE".equals(skill);
	}

	public int getLevel()
	{
		return level;
	}

	public boolean isMembers()
	{
		return members;
	}

	public double getPerHour()
	{
		return perHour;
	}

	public double getCoinsPerAction()
	{
		return coinsPerAction;
	}

	public List<Stack> getInputs()
	{
		return inputs;
	}

	public List<Stack> getOutputs()
	{
		return outputs;
	}

	public double getXp()
	{
		return xp;
	}

	public List<Req> getReqs()
	{
		return reqs;
	}

	public String getUnit()
	{
		return unit;
	}

	public String getUrl()
	{
		return url;
	}

	/** An item name and how many of it. */
	public static class Stack
	{
		private final String item;
		private final double qty;

		public Stack(String item, double qty)
		{
			this.item = item;
			this.qty = qty;
		}

		public String getItem()
		{
			return item;
		}

		public double getQty()
		{
			return qty;
		}
	}

	/** A skill level requirement. */
	public static class Req
	{
		private final String skill;
		private final int level;

		public Req(String skill, int level)
		{
			this.skill = skill;
			this.level = level;
		}

		public String getSkill()
		{
			return skill;
		}

		public int getLevel()
		{
			return level;
		}
	}

	/**
	 * Parse recipes.txt:
	 * category|name|skill|level|members|perHour|coins|inputs|outputs|xp[|reqs|unit|url]
	 * where reqs is "SKILL:level;SKILL:level".
	 */
	public static List<Recipe> load(InputStream in) throws IOException
	{
		List<Recipe> list = new ArrayList<>();
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
				if (f.length < 10)
				{
					continue;
				}
				String skill = f[2];
				int level = Integer.parseInt(f[3]);
				List<Req> reqs = new ArrayList<>();
				if (!"NONE".equals(skill))
				{
					reqs.add(new Req(skill, level));
				}
				if (f.length > 10)
				{
					for (String part : f[10].split(";"))
					{
						int colon = part.indexOf(':');
						if (colon > 0)
						{
							String s = part.substring(0, colon);
							if (!s.equals(skill))
							{
								reqs.add(new Req(s, Integer.parseInt(part.substring(colon + 1))));
							}
						}
					}
				}
				list.add(new Recipe(f[0], f[1], skill, level, "1".equals(f[4]),
					Double.parseDouble(f[5]), Double.parseDouble(f[6]), stacks(f[7]), stacks(f[8]), Double.parseDouble(f[9]),
					Collections.unmodifiableList(reqs), f.length > 11 ? f[11] : "", f.length > 12 ? f[12] : ""));
			}
		}
		return Collections.unmodifiableList(list);
	}

	static List<Stack> stacks(String s)
	{
		List<Stack> out = new ArrayList<>();
		for (String part : s.split(";"))
		{
			if (part.isEmpty())
			{
				continue;
			}
			int star = part.lastIndexOf('*');
			out.add(new Stack(part.substring(0, star), Double.parseDouble(part.substring(star + 1))));
		}
		return Collections.unmodifiableList(out);
	}
}

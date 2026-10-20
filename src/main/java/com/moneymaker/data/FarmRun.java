package com.moneymaker.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A farm run (or bird house run, or daily) with a growth timer, loaded from farmruns.txt. All amounts are per run. */
public class FarmRun
{
	private final String id;
	private final String group;
	private final String name;
	private final String skill;
	private final int level;
	private final boolean members;
	private final double growMinutes;
	private final double runMinutes;
	private final int patches;
	private final List<Recipe.Stack> inputs;
	private final List<Recipe.Stack> outputs;
	private final double coins;
	private final double xp;
	private final String note;
	private final String url;

	FarmRun(String group, String name, String skill, int level, boolean members, double growMinutes, double runMinutes,
		int patches, List<Recipe.Stack> inputs, List<Recipe.Stack> outputs, double coins, double xp, String note, String url)
	{
		this.id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		this.group = group;
		this.name = name;
		this.skill = skill;
		this.level = level;
		this.members = members;
		this.growMinutes = growMinutes;
		this.runMinutes = runMinutes;
		this.patches = patches;
		this.inputs = inputs;
		this.outputs = outputs;
		this.coins = coins;
		this.xp = xp;
		this.note = note;
		this.url = url;
	}

	public String getId()
	{
		return id;
	}

	public String getGroup()
	{
		return group;
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

	public double getGrowMinutes()
	{
		return growMinutes;
	}

	public double getRunMinutes()
	{
		return runMinutes;
	}

	public int getPatches()
	{
		return patches;
	}

	public List<Recipe.Stack> getInputs()
	{
		return inputs;
	}

	public List<Recipe.Stack> getOutputs()
	{
		return outputs;
	}

	/** Coins paid per run (fees); negative means coins or untradeable loot received. */
	public double getCoins()
	{
		return coins;
	}

	public double getXp()
	{
		return xp;
	}

	public String getNote()
	{
		return note;
	}

	public String getUrl()
	{
		return url;
	}

	/** A run priced at current GE prices. */
	public static class Priced
	{
		private final FarmRun run;
		private final long cost;
		private final long revenue;
		private final List<JobResult.Line> buy;
		private final List<JobResult.Line> sell;

		Priced(FarmRun run, long cost, long revenue, List<JobResult.Line> buy, List<JobResult.Line> sell)
		{
			this.run = run;
			this.cost = cost;
			this.revenue = revenue;
			this.buy = buy;
			this.sell = sell;
		}

		public FarmRun getRun()
		{
			return run;
		}

		/** Cash you need for one run (supplies and fees). */
		public long getCost()
		{
			return cost;
		}

		public long getProfit()
		{
			return revenue - cost;
		}

		/** Profit per real hour, if you do the run every time it's ready. */
		public long getProfitPerHour()
		{
			return Math.round(getProfit() * 60.0 / Math.max(run.growMinutes, run.runMinutes));
		}

		/** Profit per minute spent doing the run. */
		public long getProfitPerMinute()
		{
			return Math.round(getProfit() / Math.max(0.5, run.runMinutes));
		}

		public List<JobResult.Line> getBuy()
		{
			return buy;
		}

		public List<JobResult.Line> getSell()
		{
			return sell;
		}
	}

	/** Price this run, or null if an item has no price. */
	public Priced price(PriceBook prices, PriceMode mode)
	{
		// coins: positive is a fee you pay, negative is coins or untradeable loot you get
		double cost = Math.max(0, coins);
		List<JobResult.Line> buy = new ArrayList<>();
		for (Recipe.Stack s : inputs)
		{
			ItemPrice p = prices.get(s.getItem());
			if (p == null || p.buyPrice(mode) <= 0)
			{
				return null;
			}
			cost += p.buyPrice(mode) * s.getQty();
			buy.add(new JobResult.Line(p.getName(), s.getQty(), p.buyPrice(mode), p.getId()));
		}
		double revenue = Math.max(0, -coins);
		List<JobResult.Line> sell = new ArrayList<>();
		for (Recipe.Stack s : outputs)
		{
			ItemPrice p = prices.get(s.getItem());
			if (p == null || p.sellPrice(mode) <= 0)
			{
				return null;
			}
			long each = p.sellPrice(mode);
			revenue += (each - GeTax.perItem(p.getId(), each)) * s.getQty();
			sell.add(new JobResult.Line(p.getName(), s.getQty(), each, p.getId()));
		}
		return new Priced(this, Math.round(cost), Math.round(revenue), buy, sell);
	}

	/** Parse farmruns.txt: group|name|skill|level|members|grow|run|patches|inputs|outputs|coins|xp|note|url */
	public static List<FarmRun> load(InputStream in) throws IOException
	{
		List<FarmRun> list = new ArrayList<>();
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
				if (f.length < 14)
				{
					continue;
				}
				list.add(new FarmRun(f[0], f[1], f[2], Integer.parseInt(f[3]), "1".equals(f[4]),
					Double.parseDouble(f[5]), Double.parseDouble(f[6]), Integer.parseInt(f[7]),
					Recipe.stacks(f[8]), Recipe.stacks(f[9]), Double.parseDouble(f[10]), Double.parseDouble(f[11]), f[12], f[13]));
			}
		}
		return Collections.unmodifiableList(list);
	}
}

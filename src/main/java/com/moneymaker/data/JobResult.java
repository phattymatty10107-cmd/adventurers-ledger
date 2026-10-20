package com.moneymaker.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A recipe priced at current GE prices, plus what it takes to reach your next level. */
public class JobResult
{
	private final Recipe recipe;
	private final double costEach;
	private final double revenueEach;
	private final double actionsPerHour;
	private final boolean limitCapped;
	private final String cappedBy;
	private final boolean lowVolume;
	private final List<Line> inputLines;
	private final List<Line> outputLines;
	/** Next-level info; null when there's no skill, no XP, or you're 99. */
	private final NextLevel next;

	private JobResult(Recipe recipe, double costEach, double revenueEach, double actionsPerHour, boolean limitCapped,
		String cappedBy, boolean lowVolume, List<Line> inputLines, List<Line> outputLines, NextLevel next)
	{
		this.recipe = recipe;
		this.costEach = costEach;
		this.revenueEach = revenueEach;
		this.actionsPerHour = actionsPerHour;
		this.limitCapped = limitCapped;
		this.cappedBy = cappedBy;
		this.lowVolume = lowVolume;
		this.inputLines = inputLines;
		this.outputLines = outputLines;
		this.next = next;
	}

	public Recipe getRecipe()
	{
		return recipe;
	}

	public long getCostEach()
	{
		return Math.round(costEach);
	}

	public long getRevenueEach()
	{
		return Math.round(revenueEach);
	}

	public long getProfitEach()
	{
		return Math.round(revenueEach - costEach);
	}

	public double getActionsPerHour()
	{
		return actionsPerHour;
	}

	public long getGpPerHour()
	{
		return Math.round((revenueEach - costEach) * actionsPerHour);
	}

	public boolean isLimitCapped()
	{
		return limitCapped;
	}

	public String getCappedBy()
	{
		return cappedBy;
	}

	public boolean isLowVolume()
	{
		return lowVolume;
	}

	public List<Line> getInputLines()
	{
		return inputLines;
	}

	public List<Line> getOutputLines()
	{
		return outputLines;
	}

	public NextLevel getNext()
	{
		return next;
	}

	/** One priced item line, e.g. "2 x Vial of water @ 4". */
	public static class Line
	{
		private final String item;
		private final double qty;
		private final long priceEach;
		private final int itemId;

		Line(String item, double qty, long priceEach, int itemId)
		{
			this.item = item;
			this.qty = qty;
			this.priceEach = priceEach;
			this.itemId = itemId;
		}

		public String getItem()
		{
			return item;
		}

		public double getQty()
		{
			return qty;
		}

		public long getPriceEach()
		{
			return priceEach;
		}

		public int getItemId()
		{
			return itemId;
		}
	}

	/** What reaching the next level with this job looks like. */
	public static class NextLevel
	{
		private final int currentLevel;
		private final int targetLevel;
		private final long xpNeeded;
		private final long actions;
		private final long profit;
		private final double hours;
		/** Shopping list: total qty of each input for all the actions. */
		private final List<Line> shopping;
		private final long shoppingCost;

		NextLevel(int currentLevel, int targetLevel, long xpNeeded, long actions, long profit, double hours,
			List<Line> shopping, long shoppingCost)
		{
			this.currentLevel = currentLevel;
			this.targetLevel = targetLevel;
			this.xpNeeded = xpNeeded;
			this.actions = actions;
			this.profit = profit;
			this.hours = hours;
			this.shopping = shopping;
			this.shoppingCost = shoppingCost;
		}

		public int getCurrentLevel()
		{
			return currentLevel;
		}

		public int getTargetLevel()
		{
			return targetLevel;
		}

		public long getXpNeeded()
		{
			return xpNeeded;
		}

		public long getActions()
		{
			return actions;
		}

		public long getProfit()
		{
			return profit;
		}

		public double getHours()
		{
			return hours;
		}

		public List<Line> getShopping()
		{
			return shopping;
		}

		public long getShoppingCost()
		{
			return shoppingCost;
		}
	}

	/**
	 * Price a recipe. Returns null if any item has no price.
	 *
	 * @param level current level in the recipe's skill (0 if unknown)
	 * @param xp current XP in the recipe's skill (-1 if unknown)
	 * @param xpForNextLevel XP needed for level+1 (from RuneLite's Experience table), or -1
	 */
	public static JobResult evaluate(Recipe r, PriceBook prices, PriceMode mode, int speedPercent,
		int level, long xp, long xpForNextLevel)
	{
		double cost = r.getCoinsPerAction();
		List<Line> ins = new ArrayList<>();
		double rate = r.getPerHour() * Math.max(1, speedPercent) / 100.0;
		boolean capped = false;
		String cappedBy = null;

		for (Recipe.Stack s : r.getInputs())
		{
			ItemPrice p = prices.get(s.getItem());
			if (p == null || p.buyPrice(mode) <= 0)
			{
				return null;
			}
			long each = p.buyPrice(mode);
			cost += each * s.getQty();
			ins.add(new Line(p.getName(), s.getQty(), each, p.getId()));

			// GE buy limit: you can only buy `limit` every 4 hours
			if (p.getLimit() > 0 && s.getQty() > 0)
			{
				double maxPerHour = p.getLimit() / s.getQty() / 4.0;
				if (maxPerHour < rate)
				{
					rate = maxPerHour;
					capped = true;
					cappedBy = p.getName();
				}
			}
		}

		double revenue = 0;
		boolean lowVolume = false;
		List<Line> outs = new ArrayList<>();
		for (Recipe.Stack s : r.getOutputs())
		{
			ItemPrice p = prices.get(s.getItem());
			if (p == null || p.sellPrice(mode) <= 0)
			{
				return null;
			}
			long each = p.sellPrice(mode);
			revenue += (each - GeTax.perItem(p.getId(), each)) * s.getQty();
			outs.add(new Line(p.getName(), s.getQty(), each, p.getId()));
			// only flag the main products, not rare drops
			if (s.getQty() >= 0.2 && p.getHourVolume() < rate * s.getQty())
			{
				lowVolume = true;
			}
		}
		// show the most valuable product first
		outs.sort((a, b) -> Double.compare(b.getPriceEach() * b.getQty(), a.getPriceEach() * a.getQty()));

		NextLevel next = null;
		if (r.hasSkill() && r.getXp() > 0 && xp >= 0 && xpForNextLevel > xp && level > 0 && level < 99)
		{
			long xpNeeded = xpForNextLevel - xp;
			long actions = (long) Math.ceil(xpNeeded / r.getXp());
			List<Line> shopping = new ArrayList<>();
			long shoppingCost = 0;
			for (Line l : ins)
			{
				double qty = Math.ceil(l.getQty() * actions);
				shopping.add(new Line(l.getItem(), qty, l.getPriceEach(), l.getItemId()));
				shoppingCost += Math.round(qty * l.getPriceEach());
			}
			shoppingCost += Math.round(Math.max(0, r.getCoinsPerAction()) * actions);
			long profit = Math.round((revenue - cost) * actions);
			double hours = rate > 0 ? actions / rate : 0;
			next = new NextLevel(level, level + 1, xpNeeded, actions, profit, hours,
				Collections.unmodifiableList(shopping), shoppingCost);
		}

		return new JobResult(r, cost, revenue, rate, capped, cappedBy, lowVolume,
			Collections.unmodifiableList(ins), Collections.unmodifiableList(outs), next);
	}
}

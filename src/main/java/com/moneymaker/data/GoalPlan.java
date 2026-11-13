package com.moneymaker.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Working out how to pay for a goal: what you have, what you still need and the quickest jobs to earn it. */
public final class GoalPlan
{
	private static final long PLATINUM_TOKEN_VALUE = 1000;

	private GoalPlan()
	{
	}

	/** Coins you hold (bag and bank), counting platinum tokens at 1,000 each. */
	public static long coins(Map<String, Gear.Owned> have)
	{
		long total = 0;
		Gear.Owned coins = have.get("coins");
		if (coins != null)
		{
			total += coins.getQty();
		}
		Gear.Owned tokens = have.get("platinum token");
		if (tokens != null)
		{
			total += tokens.getQty() * PLATINUM_TOKEN_VALUE;
		}
		return total;
	}

	/** One job and how long it takes to earn the amount. */
	public static class Earner
	{
		private final JobResult job;
		private final double hours;

		Earner(JobResult job, double hours)
		{
			this.job = job;
			this.hours = hours;
		}

		public JobResult getJob()
		{
			return job;
		}

		public double getHours()
		{
			return hours;
		}
	}

	/**
	 * The quickest jobs for earning an amount: best gp/hr first. Leaves out jobs that lose money
	 * and ones whose prices can't be trusted (old prices, or products that won't sell fast enough).
	 */
	public static List<Earner> earners(List<JobResult> jobs, long gpNeeded, int max)
	{
		List<JobResult> good = new ArrayList<>();
		for (JobResult j : jobs)
		{
			if (j != null && j.getGpPerHour() > 0 && !j.isStale() && !j.isLowVolume())
			{
				good.add(j);
			}
		}
		good.sort((a, b) -> Long.compare(b.getGpPerHour(), a.getGpPerHour()));
		List<Earner> out = new ArrayList<>();
		for (JobResult j : good)
		{
			if (out.size() >= max)
			{
				break;
			}
			out.add(new Earner(j, Math.max(0, gpNeeded) / (double) j.getGpPerHour()));
		}
		return Collections.unmodifiableList(out);
	}

	/** What the supplies for a training route cost to buy. Items with no price are left out. */
	public static long suppliesCost(List<Training.Step> steps, PriceBook prices, PriceMode mode)
	{
		long cost = 0;
		for (Training.Step s : steps)
		{
			long actions = s.getActions();
			for (Recipe.Stack need : s.getMethod().getNeeds())
			{
				ItemPrice p = prices.get(need.getItem());
				if (p != null)
				{
					cost += (long) Math.ceil(need.getQty() * actions) * p.buyPrice(mode);
				}
			}
		}
		return cost;
	}

	/** Total hours of play in a training route. */
	public static double hours(List<Training.Step> steps)
	{
		double h = 0;
		for (Training.Step s : steps)
		{
			h += s.getHours();
		}
		return h;
	}
}

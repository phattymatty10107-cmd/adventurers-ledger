package com.moneymaker.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A worked-out route: every tile from where you are to where you're going. */
public final class Route
{
	/** One instruction for the side panel: "Varrock Teleport", "Walk 120 tiles". */
	public static final class Step
	{
		private final String text;
		private final boolean transport;

		Step(String text, boolean transport)
		{
			this.text = text;
			this.transport = transport;
		}

		public String getText()
		{
			return text;
		}

		/** False for plain walking. */
		public boolean isTransport()
		{
			return transport;
		}
	}

	/** A doorway or dungeon entrance this short isn't worth its own line in the list. */
	private static final int MINOR_HOP = 12;

	private final int[] tiles;
	/** Index into tiles to the transport taken to arrive at that tile. */
	private final Map<Integer, Transport> hops;
	private final int cost;
	private final boolean reached;
	private final List<Step> steps;

	Route(int[] tiles, Map<Integer, Transport> hops, int cost, boolean reached)
	{
		this.tiles = tiles;
		this.hops = hops;
		this.cost = cost;
		this.reached = reached;
		this.steps = Collections.unmodifiableList(buildSteps());
	}

	/** Every tile in order, packed with {@link Pos}. The first is where you started. */
	public int[] getTiles()
	{
		return tiles;
	}

	/** The transport used to get to tiles[index], or null if you walk there. */
	public Transport hopInto(int index)
	{
		return hops.get(index);
	}

	/** Rough length: one per tile walked plus the game ticks each transport takes. */
	public int getCost()
	{
		return cost;
	}

	/** False when the place can't be reached and this only gets you as near as possible. */
	public boolean isReached()
	{
		return reached;
	}

	public int getEnd()
	{
		return tiles[tiles.length - 1];
	}

	public List<Step> getSteps()
	{
		return steps;
	}

	public int walkedTiles()
	{
		return tiles.length - 1 - hops.size();
	}

	private List<Step> buildSteps()
	{
		List<Step> out = new ArrayList<>();
		int walked = 0;
		for (int i = 1; i < tiles.length; i++)
		{
			Transport t = hops.get(i);
			if (t == null)
			{
				walked++;
				continue;
			}
			boolean minor = (t.kind == Transport.Kind.OBJECT || t.kind == Transport.Kind.AGILITY)
				&& Pos.distance(tiles[i - 1], tiles[i]) < MINOR_HOP;
			if (minor)
			{
				// doors, stiles and stairs: part of the walk
				walked++;
				continue;
			}
			if (walked > 0)
			{
				out.add(new Step("Walk " + walked + " tile" + (walked == 1 ? "" : "s"), false));
				walked = 0;
			}
			out.add(new Step(t.label(), true));
		}
		if (walked > 0)
		{
			out.add(new Step("Walk " + walked + " tile" + (walked == 1 ? "" : "s"), false));
		}
		return out;
	}
}

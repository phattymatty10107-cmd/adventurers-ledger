package com.moneymaker.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the quickest way between two tiles: walking over the collision map, plus the doors,
 * boats, fairy rings and teleports this character can use. Plain Dijkstra, where a tile walked
 * costs 1 and a transport costs the game ticks it takes.
 */
public final class Pathfinder
{
	/** Give up after this many tiles; the whole walkable world is under this. */
	private static final int MAX_TILES = 12_000_000;
	private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
	private static final int[] DY = {1, 1, 0, -1, -1, -1, 0, 1};
	/** Extra cost for using any teleport, so a two-tile saving doesn't burn a teleport. */
	private static final int TELEPORT_PENALTY = 10;
	/** How far from the place asked for to look for somewhere to stand, when the tile itself is solid. */
	private static final int SNAP_RADIUS = 6;
	/** Ending this close to a place that can't be stood on counts as getting there. */
	private static final int NEAR_ENOUGH = 6;

	private final CollisionMap map;
	private final TransportData transports;

	public Pathfinder(CollisionMap map, TransportData transports)
	{
		this.map = map;
		this.transports = transports;
	}

	/**
	 * The quickest route from start to the nearest of the targets, or null if start isn't on the map.
	 * When none of the targets can be reached the route ends as close to the first one as it can get.
	 */
	public Route find(int start, Collection<Integer> wanted, PlayerState state)
	{
		if (wanted.isEmpty())
		{
			return null;
		}
		Set<Integer> targets = new HashSet<>();
		int first = Pos.NONE;
		for (int w : wanted)
		{
			if (first == Pos.NONE)
			{
				first = w;
			}
			targets.addAll(standable(w));
		}
		boolean wildOk = !state.avoidWilderness || Pos.wildernessLevel(start) > 0;
		for (int t : wanted)
		{
			wildOk |= Pos.wildernessLevel(t) > 0;
		}

		Grid cost = new Grid(Integer.MAX_VALUE);
		Grid prev = new Grid(Pos.NONE);
		Map<Integer, Transport> via = new HashMap<>();
		Heap heap = new Heap();
		cost.set(start, 0);
		heap.push(0, start);

		// teleports work from where you stand
		int startWild = Pos.wildernessLevel(start);
		for (Transport t : transports.teleports())
		{
			if (startWild > Math.max(0, t.maxWilderness) || !t.available(state))
			{
				continue;
			}
			if (!wildOk && Pos.wildernessLevel(t.destination) > 0)
			{
				continue;
			}
			int c = Math.max(1, t.duration) + TELEPORT_PENALTY;
			if (c < cost.get(t.destination))
			{
				cost.set(t.destination, c);
				prev.set(t.destination, start);
				via.put(t.destination, t);
				heap.push(c, t.destination);
			}
		}

		int best = start;
		long bestScore = score(start, first);
		int end = Pos.NONE;
		int settled = 0;
		while (!heap.isEmpty())
		{
			long top = heap.pop();
			int node = (int) top;
			int c = (int) (top >>> 32);
			if (c > cost.get(node))
			{
				continue;
			}
			if (targets.contains(node))
			{
				end = node;
				break;
			}
			if (++settled > MAX_TILES)
			{
				break;
			}
			long sc = score(node, first);
			if (sc < bestScore)
			{
				bestScore = sc;
				best = node;
			}
			int x = Pos.x(node);
			int y = Pos.y(node);
			int z = Pos.plane(node);
			int moves = map.moves(x, y, z);
			for (int d = 0; d < 8; d++)
			{
				if ((moves & (1 << d)) == 0)
				{
					continue;
				}
				int next = Pos.pack(x + DX[d], y + DY[d], z);
				if (!wildOk && Pos.wildernessLevel(next) > 0)
				{
					continue;
				}
				if (c + 1 < cost.get(next))
				{
					cost.set(next, c + 1);
					prev.set(next, node);
					via.remove(next);
					heap.push(c + 1, next);
				}
			}
			for (Transport t : transports.from(node))
			{
				if (!t.available(state) || (!wildOk && Pos.wildernessLevel(t.destination) > 0))
				{
					continue;
				}
				int nc = c + Math.max(1, t.duration);
				if (nc < cost.get(t.destination))
				{
					cost.set(t.destination, nc);
					prev.set(t.destination, node);
					via.put(t.destination, t);
					heap.push(nc, t.destination);
				}
			}
		}

		boolean reached = end != Pos.NONE;
		if (!reached)
		{
			// a few tiles short on the same floor is as good as there (the spot is inside a rock or a building)
			end = best;
			reached = bestScore <= NEAR_ENOUGH;
		}
		List<Integer> back = new ArrayList<>();
		for (int n = end; n != Pos.NONE; n = prev.get(n))
		{
			back.add(n);
			if (n == start)
			{
				break;
			}
		}
		int[] tiles = new int[back.size()];
		Map<Integer, Transport> hops = new HashMap<>();
		for (int i = 0; i < tiles.length; i++)
		{
			tiles[i] = back.get(tiles.length - 1 - i);
			Transport t = i == 0 ? null : via.get(tiles[i]);
			if (t != null)
			{
				hops.put(i, t);
			}
		}
		return new Route(tiles, hops, cost.get(end), reached);
	}

	/** How far a tile is from where we want to be, counting a different floor as a long way. */
	private static long score(int node, int target)
	{
		return Pos.distance(node, target) + (Pos.plane(node) == Pos.plane(target) ? 0 : 1000L);
	}

	/** The tile itself if you can stand on it, otherwise the nearest tiles round it that you can. */
	List<Integer> standable(int target)
	{
		List<Integer> out = new ArrayList<>();
		if (!map.isBlocked(target))
		{
			out.add(target);
			return out;
		}
		int x = Pos.x(target);
		int y = Pos.y(target);
		int z = Pos.plane(target);
		for (int r = 1; r <= SNAP_RADIUS && out.isEmpty(); r++)
		{
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) == r && !map.isBlocked(x + dx, y + dy, z))
					{
						out.add(Pos.pack(x + dx, y + dy, z));
					}
				}
			}
		}
		if (out.isEmpty())
		{
			out.add(target);
		}
		return out;
	}

	/** A value per tile, kept in 64x64 blocks that are only made when a tile in them is written. */
	private static final class Grid
	{
		private final Map<Integer, int[]> pages = new HashMap<>();
		private final int empty;
		private int lastKey = Integer.MIN_VALUE;
		private int[] lastPage;

		Grid(int empty)
		{
			this.empty = empty;
		}

		private static int key(int p)
		{
			return (Pos.x(p) >> 6) | ((Pos.y(p) >> 6) << 9) | (Pos.plane(p) << 18);
		}

		private static int index(int p)
		{
			return (Pos.x(p) & 63) | ((Pos.y(p) & 63) << 6);
		}

		int get(int p)
		{
			int k = key(p);
			int[] page = k == lastKey ? lastPage : pages.get(k);
			if (page == null)
			{
				return empty;
			}
			lastKey = k;
			lastPage = page;
			return page[index(p)];
		}

		void set(int p, int v)
		{
			int k = key(p);
			int[] page = k == lastKey ? lastPage : pages.get(k);
			if (page == null)
			{
				page = new int[4096];
				if (empty != 0)
				{
					Arrays.fill(page, empty);
				}
				pages.put(k, page);
			}
			lastKey = k;
			lastPage = page;
			page[index(p)] = v;
		}
	}

	/** A min-heap of (cost, tile) pairs packed into longs. */
	private static final class Heap
	{
		private long[] a = new long[1 << 14];
		private int n;

		boolean isEmpty()
		{
			return n == 0;
		}

		void push(int cost, int node)
		{
			if (n == a.length)
			{
				a = Arrays.copyOf(a, n * 2);
			}
			long v = ((long) cost << 32) | (node & 0xFFFFFFFFL);
			int i = n++;
			while (i > 0)
			{
				int parent = (i - 1) >> 1;
				if (a[parent] <= v)
				{
					break;
				}
				a[i] = a[parent];
				i = parent;
			}
			a[i] = v;
		}

		long pop()
		{
			long top = a[0];
			long last = a[--n];
			int i = 0;
			while (true)
			{
				int child = 2 * i + 1;
				if (child >= n)
				{
					break;
				}
				if (child + 1 < n && a[child + 1] < a[child])
				{
					child++;
				}
				if (a[child] >= last)
				{
					break;
				}
				a[i] = a[child];
				i = child;
			}
			a[i] = last;
			return top;
		}
	}
}

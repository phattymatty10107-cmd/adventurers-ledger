package com.moneymaker.route;

/** A world tile packed into one int: x in the low 15 bits, then y, then the plane. */
public final class Pos
{
	public static final int NONE = -1;

	private Pos()
	{
	}

	public static int pack(int x, int y, int plane)
	{
		return (x & 0x7FFF) | ((y & 0x7FFF) << 15) | ((plane & 3) << 30);
	}

	public static int x(int p)
	{
		return p & 0x7FFF;
	}

	public static int y(int p)
	{
		return (p >> 15) & 0x7FFF;
	}

	public static int plane(int p)
	{
		return (p >> 30) & 3;
	}

	/** Tiles apart, ignoring the plane (a diagonal step counts as one). */
	public static int distance(int a, int b)
	{
		return Math.max(Math.abs(x(a) - x(b)), Math.abs(y(a) - y(b)));
	}

	/** "x y plane" as written in the data files, or NONE if it isn't three numbers. */
	public static int parse(String s)
	{
		String[] f = s.trim().split("[ ,]+");
		if (f.length != 3)
		{
			return NONE;
		}
		try
		{
			return pack(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
		}
		catch (NumberFormatException e)
		{
			return NONE;
		}
	}

	/** Wilderness level of a tile, or 0 outside the Wilderness. */
	public static int wildernessLevel(int p)
	{
		int x = x(p);
		int y = y(p);
		if (x >= 2944 && x < 3392 && y >= 3525 && y < 3968)
		{
			return (y - 3520) / 8 + 1;
		}
		// the Wilderness caves sit 6400 tiles north of the surface above them
		if (x >= 2944 && x < 3392 && y >= 9920 && y < 10368)
		{
			return (y - 9920) / 8 + 1;
		}
		return 0;
	}

	public static String toString(int p)
	{
		return x(p) + " " + y(p) + " " + plane(p);
	}
}

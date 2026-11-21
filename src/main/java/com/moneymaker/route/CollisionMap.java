package com.moneymaker.route;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Which tiles you can walk between. Read from collision-map.zip, the map data made for the
 * Shortest Path plugin (github.com/Skretzo/shortest-path, BSD 2-Clause): one entry per 64x64 map
 * region, holding four bits per tile on each plane. Bit 0 is "can step north", bit 1 "can step east".
 */
public final class CollisionMap
{
	private static final int REGION = 64;
	private static final int FLAGS = 4;
	private static final int NORTH = 0;
	private static final int EAST = 1;

	private final int minRx;
	private final int minRy;
	private final int width;
	private final int height;
	/** One array of bits per region, or null where there's no map. */
	private final long[][] regions;

	private CollisionMap(int minRx, int minRy, int width, int height, long[][] regions)
	{
		this.minRx = minRx;
		this.minRy = minRy;
		this.width = width;
		this.height = height;
		this.regions = regions;
	}

	public static CollisionMap load(InputStream in) throws IOException
	{
		Map<Integer, long[]> byRegion = new HashMap<>();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = 0;
		int maxY = 0;
		try (ZipInputStream zip = new ZipInputStream(in))
		{
			ZipEntry entry;
			byte[] buf = new byte[8192];
			while ((entry = zip.getNextEntry()) != null)
			{
				String[] n = entry.getName().split("_");
				if (n.length != 2)
				{
					continue;
				}
				int rx;
				int ry;
				try
				{
					rx = Integer.parseInt(n[0]);
					ry = Integer.parseInt(n[1]);
				}
				catch (NumberFormatException e)
				{
					continue;
				}
				ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
				int read;
				while ((read = zip.read(buf)) > 0)
				{
					out.write(buf, 0, read);
				}
				byte[] bytes = out.toByteArray();
				long[] words = new long[(bytes.length + 7) / 8];
				ByteBuffer bb = ByteBuffer.wrap(java.util.Arrays.copyOf(bytes, words.length * 8)).order(ByteOrder.LITTLE_ENDIAN);
				for (int i = 0; i < words.length; i++)
				{
					words[i] = bb.getLong();
				}
				byRegion.put(rx | (ry << 16), words);
				minX = Math.min(minX, rx);
				minY = Math.min(minY, ry);
				maxX = Math.max(maxX, rx);
				maxY = Math.max(maxY, ry);
			}
		}
		if (byRegion.isEmpty())
		{
			throw new IOException("The collision map is empty");
		}
		int w = maxX - minX + 1;
		int h = maxY - minY + 1;
		long[][] regions = new long[w * h][];
		for (Map.Entry<Integer, long[]> e : byRegion.entrySet())
		{
			int rx = e.getKey() & 0xFFFF;
			int ry = e.getKey() >>> 16;
			regions[(rx - minX) + (ry - minY) * w] = e.getValue();
		}
		return new CollisionMap(minX, minY, w, h, regions);
	}

	private boolean get(int x, int y, int z, int flag)
	{
		int rx = (x >> 6) - minRx;
		int ry = (y >> 6) - minRy;
		if (rx < 0 || ry < 0 || rx >= width || ry >= height)
		{
			return false;
		}
		long[] words = regions[rx + ry * width];
		if (words == null)
		{
			return false;
		}
		int bit = (z * REGION * REGION + (y & 63) * REGION + (x & 63)) * FLAGS + flag;
		int word = bit >> 6;
		return word < words.length && (words[word] >>> (bit & 63) & 1L) != 0;
	}

	public boolean n(int x, int y, int z)
	{
		return get(x, y, z, NORTH);
	}

	public boolean s(int x, int y, int z)
	{
		return get(x, y - 1, z, NORTH);
	}

	public boolean e(int x, int y, int z)
	{
		return get(x, y, z, EAST);
	}

	public boolean w(int x, int y, int z)
	{
		return get(x - 1, y, z, EAST);
	}

	/** True when you can't step off this tile in any direction (a wall, water, or off the map). */
	public boolean isBlocked(int x, int y, int z)
	{
		return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z);
	}

	public boolean isBlocked(int p)
	{
		return isBlocked(Pos.x(p), Pos.y(p), Pos.plane(p));
	}

	/**
	 * The tiles you can step to from here, as a bit per direction: N, NE, E, SE, S, SW, W, NW.
	 * A diagonal is only open when both ways round the corner are.
	 */
	public int moves(int x, int y, int z)
	{
		boolean n = n(x, y, z);
		boolean s = s(x, y, z);
		boolean e = e(x, y, z);
		boolean w = w(x, y, z);
		int mask = 0;
		if (n)
		{
			mask |= 1;
			if (e && e(x, y + 1, z) && n(x + 1, y, z))
			{
				mask |= 2;
			}
			if (w && w(x, y + 1, z) && n(x - 1, y, z))
			{
				mask |= 128;
			}
		}
		if (e)
		{
			mask |= 4;
		}
		if (s)
		{
			mask |= 16;
			if (e && e(x, y - 1, z) && s(x + 1, y, z))
			{
				mask |= 8;
			}
			if (w && w(x, y - 1, z) && s(x - 1, y, z))
			{
				mask |= 32;
			}
		}
		if (w)
		{
			mask |= 64;
		}
		return mask;
	}
}

package com.moneymaker.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A gear setup for one boss, from the OSRS Wiki strategy page. Each slot holds tiers
 * ordered best to worst; each tier can have several equally-good alternatives.
 */
public class BossSetup
{
	private final String boss;
	private final String setup;
	private final String url;
	private final List<Slot> slots;

	public BossSetup(String boss, String setup, String url, List<Slot> slots)
	{
		this.boss = boss;
		this.setup = setup;
		this.url = url;
		this.slots = slots;
	}

	public String getBoss()
	{
		return boss;
	}

	public String getSetup()
	{
		return setup;
	}

	public String getUrl()
	{
		return url;
	}

	public List<Slot> getSlots()
	{
		return slots;
	}

	@Override
	public String toString()
	{
		return boss + " - " + setup;
	}

	public static class Slot
	{
		private final String name;
		/** Best first. */
		private final List<List<String>> tiers;

		public Slot(String name, List<List<String>> tiers)
		{
			this.name = name;
			this.tiers = tiers;
		}

		public String getName()
		{
			return name;
		}

		public List<List<String>> getTiers()
		{
			return tiers;
		}
	}

	/** Parse bosses.txt (BOSS| and SLOT| lines). */
	public static List<BossSetup> load(InputStream in) throws IOException
	{
		List<BossSetup> out = new ArrayList<>();
		String boss = null;
		String setup = null;
		String url = null;
		List<Slot> slots = null;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				String[] f = line.trim().split("\\|", -1);
				if (f[0].equals("BOSS") && f.length >= 4)
				{
					if (boss != null)
					{
						out.add(new BossSetup(boss, setup, url, Collections.unmodifiableList(slots)));
					}
					boss = f[1];
					setup = f[2];
					url = f[3];
					slots = new ArrayList<>();
				}
				else if (f[0].equals("SLOT") && f.length >= 3 && slots != null)
				{
					List<List<String>> tiers = new ArrayList<>();
					for (String group : f[2].split(";"))
					{
						List<String> alts = new ArrayList<>();
						for (String item : Arrays.asList(group.split(" / ")))
						{
							if (!item.trim().isEmpty())
							{
								alts.add(item.trim());
							}
						}
						tiers.add(Collections.unmodifiableList(alts));
					}
					slots.add(new Slot(f[1], Collections.unmodifiableList(tiers)));
				}
			}
		}
		if (boss != null)
		{
			out.add(new BossSetup(boss, setup, url, Collections.unmodifiableList(slots)));
		}
		return Collections.unmodifiableList(out);
	}
}

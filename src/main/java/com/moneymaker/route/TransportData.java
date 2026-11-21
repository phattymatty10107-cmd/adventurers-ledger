package com.moneymaker.route;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every door, ladder, boat, fairy ring and teleport the route finder knows about, read from the
 * tab-separated files in route/transports. The files come from the Shortest Path plugin
 * (github.com/Skretzo/shortest-path, BSD 2-Clause).
 */
public final class TransportData
{
	/** Transports you use at a spot, by the tile you stand on. */
	private final Map<Integer, List<Transport>> byOrigin;
	/** Teleports: usable from anywhere. */
	private final List<Transport> teleports;
	private final Set<Integer> varbitIds = new HashSet<>();
	private final Set<Integer> varpIds = new HashSet<>();
	private final Set<String> questNames = new HashSet<>();

	/** Opens a data file by name, or returns null if it isn't there. */
	public interface Source
	{
		InputStream open(String name) throws IOException;
	}

	private TransportData(Map<Integer, List<Transport>> byOrigin, List<Transport> teleports)
	{
		this.byOrigin = byOrigin;
		this.teleports = teleports;
		for (List<Transport> list : byOrigin.values())
		{
			list.forEach(this::note);
		}
		teleports.forEach(this::note);
	}

	private void note(Transport t)
	{
		for (Transport.VarCheck v : t.vars)
		{
			(v.varbit ? varbitIds : varpIds).add(v.id);
		}
		questNames.addAll(t.quests);
	}

	public static TransportData empty()
	{
		return new TransportData(Collections.emptyMap(), Collections.emptyList());
	}

	public List<Transport> from(int tile)
	{
		List<Transport> list = byOrigin.get(tile);
		return list == null ? Collections.emptyList() : list;
	}

	public List<Transport> teleports()
	{
		return teleports;
	}

	/** The game settings that have to be read before a route can be worked out. */
	public Set<Integer> varbitIds()
	{
		return varbitIds;
	}

	public Set<Integer> varpIds()
	{
		return varpIds;
	}

	public Set<String> questNames()
	{
		return questNames;
	}

	public int size()
	{
		int n = teleports.size();
		for (List<Transport> list : byOrigin.values())
		{
			n += list.size();
		}
		return n;
	}

	public static TransportData load(Source source) throws IOException
	{
		Map<String, int[][]> variations = new HashMap<>();
		try (InputStream in = source.open("item-variations.tsv"))
		{
			if (in != null)
			{
				loadVariations(in, variations);
			}
		}
		Map<Integer, List<Transport>> byOrigin = new HashMap<>();
		List<Transport> teleports = new ArrayList<>();
		for (Transport.Kind kind : Transport.Kind.values())
		{
			try (InputStream in = source.open("transports/" + kind.file + ".tsv"))
			{
				if (in == null)
				{
					continue;
				}
				loadFile(kind, in, variations, byOrigin, teleports);
			}
		}
		for (Map.Entry<Integer, List<Transport>> e : byOrigin.entrySet())
		{
			e.setValue(Collections.unmodifiableList(e.getValue()));
		}
		return new TransportData(byOrigin, Collections.unmodifiableList(teleports));
	}

	private static void loadVariations(InputStream in, Map<String, int[][]> out) throws IOException
	{
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = r.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			out.put(f[0].trim().toUpperCase(Locale.ROOT), new int[][]{
				ints(f.length > 1 ? f[1] : ""), ints(f.length > 2 ? f[2] : ""), ints(f.length > 3 ? f[3] : "")});
		}
	}

	private static int[] ints(String csv)
	{
		List<Integer> out = new ArrayList<>();
		for (String s : csv.split(","))
		{
			s = s.trim();
			if (!s.isEmpty())
			{
				try
				{
					out.add(Integer.parseInt(s));
				}
				catch (NumberFormatException ignored)
				{
					// skip a bad id rather than lose the whole line
				}
			}
		}
		int[] a = new int[out.size()];
		for (int i = 0; i < a.length; i++)
		{
			a[i] = out.get(i);
		}
		return a;
	}

	private static void loadFile(Transport.Kind kind, InputStream in, Map<String, int[][]> variations,
		Map<Integer, List<Transport>> byOrigin, List<Transport> teleports) throws IOException
	{
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line = r.readLine();
		if (line == null || !line.startsWith("#"))
		{
			throw new IOException(kind.file + ".tsv has no header line");
		}
		// the first line names the columns
		Map<String, Integer> col = new HashMap<>();
		String[] head = line.substring(1).split("\t", -1);
		for (int i = 0; i < head.length; i++)
		{
			col.put(head[i].trim().toLowerCase(Locale.ROOT), i);
		}
		boolean hasOrigin = col.containsKey("origin");
		List<Transport> ins = new ArrayList<>();
		List<Transport> outs = new ArrayList<>();
		while ((line = r.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			String originText = field(f, col, "origin");
			String destText = field(f, col, "destination");
			int origin = originText.isEmpty() ? Pos.NONE : Pos.parse(originText);
			int dest = destText.isEmpty() ? Pos.NONE : Pos.parse(destText);
			if ((!originText.isEmpty() && origin == Pos.NONE) || (!destText.isEmpty() && dest == Pos.NONE))
			{
				continue;
			}
			boolean[] usable = {true};
			List<String> skills = split(field(f, col, "skills"));
			List<String> quests = split(field(f, col, "quests"));
			List<List<Transport.ItemChoice>> items = items(field(f, col, "items"), variations, usable);
			List<Transport.VarCheck> vars = new ArrayList<>();
			vars(field(f, col, "varbits"), true, vars, usable);
			vars(field(f, col, "varplayers"), false, vars, usable);
			String display = field(f, col, "display info");
			String object = field(f, col, "menuoption menutarget objectid");
			Transport t = new Transport(kind, origin, dest, number(field(f, col, "duration"), 0),
				display.isEmpty() ? null : display, object.isEmpty() ? null : object,
				"T".equalsIgnoreCase(field(f, col, "consumable")), number(field(f, col, "wilderness level"), -1),
				skills, quests, items, vars.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(vars), usable[0]);
			if (origin != Pos.NONE && dest != Pos.NONE)
			{
				if (origin != dest)
				{
					byOrigin.computeIfAbsent(origin, k -> new ArrayList<>(2)).add(t);
				}
			}
			else if (origin != Pos.NONE)
			{
				ins.add(t);
			}
			else if (dest != Pos.NONE)
			{
				if (hasOrigin)
				{
					outs.add(t);
				}
				else
				{
					teleports.add(t);
				}
			}
		}
		// a network: every way in connects to every way out
		for (Transport from : ins)
		{
			for (Transport to : outs)
			{
				if (Pos.distance(from.origin, to.destination) > kind.minDistance || Pos.plane(from.origin) != Pos.plane(to.destination))
				{
					byOrigin.computeIfAbsent(from.origin, k -> new ArrayList<>(2)).add(Transport.join(from, to));
				}
			}
		}
	}

	private static String field(String[] f, Map<String, Integer> col, String name)
	{
		Integer i = col.get(name);
		return i == null || i >= f.length ? "" : f[i].trim();
	}

	private static int number(String s, int fallback)
	{
		try
		{
			return s.isEmpty() ? fallback : Integer.parseInt(s);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	private static List<String> split(String s)
	{
		if (s.isEmpty())
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (String part : s.split(";"))
		{
			if (!part.trim().isEmpty())
			{
				out.add(part.trim());
			}
		}
		return Collections.unmodifiableList(out);
	}

	/** "AIR_RUNE=3&&LAW_RUNE=1" or "13121=1||13122=1": every "&" part is needed, "|" parts are alternatives. */
	private static List<List<Transport.ItemChoice>> items(String s, Map<String, int[][]> variations, boolean[] usable)
	{
		if (s.isEmpty())
		{
			return Collections.emptyList();
		}
		String norm = s.replace(" ", "").replace("&&", "&").replace("||", "|").toUpperCase(Locale.ROOT);
		List<List<Transport.ItemChoice>> all = new ArrayList<>();
		for (String and : norm.split("&"))
		{
			List<Transport.ItemChoice> any = new ArrayList<>();
			for (String or : and.split("\\|"))
			{
				String[] kv = or.split("=");
				if (kv.length != 2)
				{
					usable[0] = false;
					continue;
				}
				int qty = number(kv[1], -1);
				if (qty < 0)
				{
					usable[0] = false;
					continue;
				}
				int[] none = new int[0];
				if (kv[0].startsWith("UNLOCK_"))
				{
					// something the game doesn't tell the client (an axe left at a canoe station):
					// count it as not there, the other alternatives still work
					continue;
				}
				int[][] v = variations.get(kv[0]);
				if (v != null)
				{
					any.add(new Transport.ItemChoice(v[0], v[1], v[2], qty));
				}
				else
				{
					int id = number(kv[0], -1);
					if (id < 0)
					{
						usable[0] = false;
						continue;
					}
					any.add(new Transport.ItemChoice(new int[]{id}, none, none, qty));
				}
			}
			all.add(Collections.unmodifiableList(any));
		}
		return Collections.unmodifiableList(all);
	}

	private static void vars(String s, boolean varbit, List<Transport.VarCheck> out, boolean[] usable)
	{
		if (s.isEmpty())
		{
			return;
		}
		for (String part : s.split(";"))
		{
			part = part.trim();
			if (part.isEmpty())
			{
				continue;
			}
			boolean ok = false;
			for (char op : new char[]{'&', '@', '=', '>', '<'})
			{
				int at = part.indexOf(op);
				if (at > 0)
				{
					int id = number(part.substring(0, at), -1);
					String v = part.substring(at + 1);
					if (id >= 0 && v.matches("-?\\d+"))
					{
						out.add(new Transport.VarCheck(varbit, id, op, Integer.parseInt(v)));
						ok = true;
					}
					break;
				}
			}
			if (!ok)
			{
				usable[0] = false;
			}
		}
	}
}

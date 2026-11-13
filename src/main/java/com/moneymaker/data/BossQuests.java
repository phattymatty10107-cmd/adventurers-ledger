package com.moneymaker.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The quests each boss needs (bossquests.txt) and what each of those quests needs first
 * (questlines.txt).
 */
public class BossQuests
{
	private final Map<String, List<Req>> byBoss;
	private final Map<String, List<String>> lines;

	private BossQuests(Map<String, List<Req>> byBoss, Map<String, List<String>> lines)
	{
		this.byBoss = byBoss;
		this.lines = lines;
	}

	public static BossQuests empty()
	{
		return new BossQuests(Collections.emptyMap(), Collections.emptyMap());
	}

	/** Quests needed to fight a boss. Empty when it needs none. */
	public List<Req> forBoss(String boss)
	{
		return byBoss.getOrDefault(boss, Collections.emptyList());
	}

	/** Everything to do before a quest, in order. Doesn't include the quest itself. */
	public List<String> lineFor(String quest)
	{
		return lines.getOrDefault(quest, Collections.emptyList());
	}

	public Set<String> getBosses()
	{
		return byBoss.keySet();
	}

	/** Every quest named anywhere, so the plugin knows which ones to check your progress on. */
	public Set<String> allQuests()
	{
		Set<String> out = new LinkedHashSet<>();
		for (List<Req> reqs : byBoss.values())
		{
			for (Req r : reqs)
			{
				out.add(r.getQuest());
			}
		}
		for (Map.Entry<String, List<String>> e : lines.entrySet())
		{
			out.add(e.getKey());
			out.addAll(e.getValue());
		}
		return out;
	}

	/** OSRS Wiki page for a quest. */
	public static String wikiUrl(String quest)
	{
		StringBuilder sb = new StringBuilder("https://oldschool.runescape.wiki/w/");
		for (char c : quest.toCharArray())
		{
			if (c == ' ')
			{
				sb.append('_');
			}
			else if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == '(' || c == ')')
			{
				sb.append(c);
			}
			else
			{
				sb.append('%').append(String.format("%02X", (int) c));
			}
		}
		return sb.toString();
	}

	/** One quest a boss needs. */
	public static class Req
	{
		private final String quest;
		private final String note;

		Req(String quest, String note)
		{
			this.quest = quest;
			this.note = note;
		}

		public String getQuest()
		{
			return quest;
		}

		/** Extra detail, e.g. that the quest only has to be started. Empty if none. */
		public String getNote()
		{
			return note;
		}
	}

	/** Parse bossquests.txt (boss|quest|note) and questlines.txt (quest|needed first;needed first). */
	public static BossQuests load(InputStream bossFile, InputStream lineFile) throws IOException
	{
		Map<String, List<Req>> byBoss = new LinkedHashMap<>();
		for (String[] f : rows(bossFile))
		{
			if (f.length >= 2 && !f[0].isEmpty() && !f[1].isEmpty())
			{
				byBoss.computeIfAbsent(f[0], k -> new ArrayList<>()).add(new Req(f[1], f.length > 2 ? f[2] : ""));
			}
		}
		Map<String, List<String>> lines = new LinkedHashMap<>();
		for (String[] f : rows(lineFile))
		{
			if (f.length < 2 || f[0].isEmpty())
			{
				continue;
			}
			List<String> first = new ArrayList<>();
			for (String q : f[1].split(";"))
			{
				if (!q.trim().isEmpty() && !first.contains(q.trim()))
				{
					first.add(q.trim());
				}
			}
			lines.put(f[0], Collections.unmodifiableList(first));
		}
		for (Map.Entry<String, List<Req>> e : byBoss.entrySet())
		{
			e.setValue(Collections.unmodifiableList(e.getValue()));
		}
		return new BossQuests(byBoss, lines);
	}

	private static List<String[]> rows(InputStream in) throws IOException
	{
		List<String[]> out = new ArrayList<>();
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
				for (int i = 0; i < f.length; i++)
				{
					f[i] = f[i].trim();
				}
				out.add(f);
			}
		}
		return out;
	}
}

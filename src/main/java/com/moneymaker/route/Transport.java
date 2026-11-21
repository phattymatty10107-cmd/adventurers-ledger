package com.moneymaker.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One way of getting from a tile to another that isn't walking: a door, a boat, a teleport. */
public final class Transport
{
	/** The kinds of transport, one per data file. */
	public enum Kind
	{
		OBJECT("transports", null, false, 0),
		AGILITY("agility_shortcuts", null, false, 0),
		BOAT("boats", "Boat", false, 0),
		CANOE("canoes", "Canoe", false, 0),
		CHARTER_SHIP("charter_ships", "Charter ship", false, 0),
		SHIP("ships", "Ship", false, 0),
		FAIRY_RING("fairy_rings", "Fairy ring", false, 6),
		GNOME_GLIDER("gnome_gliders", "Gnome glider", false, 6),
		BALLOON("hot_air_balloons", "Hot air balloon", false, 7),
		MAGIC_CARPET("magic_carpets", "Magic carpet", false, 0),
		MUSHTREE("magic_mushtrees", "Magic Mushtree", false, 5),
		MINECART("minecarts", "Minecart", false, 0),
		QUETZAL("quetzals", "Quetzal", false, 5),
		SPIRIT_TREE("spirit_trees", "Spirit tree", false, 5),
		LEVER("teleportation_levers", "Lever", false, 0),
		PORTAL("teleportation_portals", "Portal", false, 0),
		OBELISK("wilderness_obelisks", "Obelisk", false, 0),
		QUETZAL_WHISTLE("quetzal_whistle", null, true, 0),
		ITEM("teleportation_items", null, true, 0),
		MINIGAME("teleportation_minigames", "Minigame teleport", true, 0),
		SPELL("teleportation_spells", null, true, 0),
		HOME("teleportation_spells_home", null, true, 0);

		final String file;
		final String label;
		/** True for things you can use from anywhere rather than at a spot on the map. */
		final boolean teleport;
		/** For networks (fairy rings and the like): don't link two ends closer together than this. */
		final int minDistance;

		Kind(String file, String label, boolean teleport, int minDistance)
		{
			this.file = file;
			this.label = label;
			this.teleport = teleport;
			this.minDistance = minDistance;
		}
	}

	/** At least `qty` of any of `ids`, or one of the staves or off-hands worn (they give endless runes). */
	static final class ItemChoice
	{
		final int[] ids;
		final int[] staves;
		final int[] offhands;
		final int qty;

		ItemChoice(int[] ids, int[] staves, int[] offhands, int qty)
		{
			this.ids = ids;
			this.staves = staves;
			this.offhands = offhands;
			this.qty = qty;
		}

		boolean met(PlayerState s)
		{
			for (int id : staves)
			{
				if (s.worn.contains(id))
				{
					return true;
				}
			}
			for (int id : offhands)
			{
				if (s.worn.contains(id))
				{
					return true;
				}
			}
			long have = 0;
			for (int id : ids)
			{
				Integer n = s.carried.get(id);
				if (n != null)
				{
					have += n;
				}
			}
			return ids.length > 0 && have >= qty;
		}
	}

	/** A game setting (varbit or varplayer) that has to hold a value. */
	static final class VarCheck
	{
		final boolean varbit;
		final int id;
		final char op;
		final int value;

		VarCheck(boolean varbit, int id, char op, int value)
		{
			this.varbit = varbit;
			this.id = id;
			this.op = op;
			this.value = value;
		}

		boolean met(PlayerState s)
		{
			Integer v = (varbit ? s.varbits : s.varps).get(id);
			if (v == null)
			{
				return false;
			}
			switch (op)
			{
				case '=':
					return v == value;
				case '>':
					return v > value;
				case '<':
					return v < value;
				case '&':
					return (v & value) > 0;
				case '@':
					// a cooldown: the setting holds the minute it was last used
					return s.nowMinutes - (long) v > value;
				default:
					return false;
			}
		}
	}

	/** Dramen staff and the Lunar staves: one has to be carried to use fairy rings before the elite Lumbridge diary. */
	private static final int[] FAIRY_STAVES = {772, 9091, 9092, 9093, 9084};

	private static boolean carries(PlayerState s, int[] ids)
	{
		for (int id : ids)
		{
			if (s.carried.containsKey(id))
			{
				return true;
			}
		}
		return false;
	}

	final Kind kind;
	final int origin;
	final int destination;
	/** Game ticks it takes. */
	final int duration;
	final String display;
	final String object;
	final boolean consumable;
	/** Deepest Wilderness level a teleport still works at. */
	final int maxWilderness;
	final List<String> skills;
	final List<String> quests;
	/** Every entry must be met; each entry is a list of alternatives. */
	final List<List<ItemChoice>> items;
	final List<VarCheck> vars;
	/** False when the data asks for something this plugin can't check, so the transport is never used. */
	final boolean usable;

	Transport(Kind kind, int origin, int destination, int duration, String display, String object,
		boolean consumable, int maxWilderness, List<String> skills, List<String> quests,
		List<List<ItemChoice>> items, List<VarCheck> vars, boolean usable)
	{
		this.kind = kind;
		this.origin = origin;
		this.destination = destination;
		this.duration = duration;
		this.display = display;
		this.object = object;
		this.consumable = consumable;
		this.maxWilderness = maxWilderness;
		this.skills = skills;
		this.quests = quests;
		this.items = items;
		this.vars = vars;
		this.usable = usable;
	}

	/** Join the way in to a network (a fairy ring you stand at) with a way out (a ring you can dial). */
	static Transport join(Transport from, Transport to)
	{
		return new Transport(from.kind, from.origin, to.destination, Math.max(from.duration, to.duration),
			to.display != null ? to.display : from.display, from.object, from.consumable || to.consumable,
			Math.max(from.maxWilderness, to.maxWilderness), concat(from.skills, to.skills), concat(from.quests, to.quests),
			concat(from.items, to.items), concat(from.vars, to.vars), from.usable && to.usable);
	}

	private static <T> List<T> concat(List<T> a, List<T> b)
	{
		if (b.isEmpty())
		{
			return a;
		}
		if (a.isEmpty())
		{
			return b;
		}
		List<T> out = new ArrayList<>(a);
		out.addAll(b);
		return Collections.unmodifiableList(out);
	}

	public int getOrigin()
	{
		return origin;
	}

	public int getDestination()
	{
		return destination;
	}

	public Kind getKind()
	{
		return kind;
	}

	public boolean isTeleport()
	{
		return kind.teleport;
	}

	/** Can this character use it right now? */
	boolean available(PlayerState s)
	{
		if (!usable)
		{
			return false;
		}
		if (s.everything)
		{
			return true;
		}
		if (kind.teleport)
		{
			if (s.teleports == PlayerState.Teleports.NONE)
			{
				return false;
			}
			if (s.teleports == PlayerState.Teleports.FREE && (consumable || kind == Kind.SPELL))
			{
				return false;
			}
		}
		if (kind == Kind.FAIRY_RING && !s.fairyRingsWithoutStaff && !carries(s, FAIRY_STAVES))
		{
			return false;
		}
		for (String req : skills)
		{
			int space = req.indexOf(' ');
			if (space < 0)
			{
				return false;
			}
			String name = req.substring(space + 1);
			int need;
			try
			{
				need = "Max".equals(req.substring(0, space)) ? 99 : Integer.parseInt(req.substring(0, space));
			}
			catch (NumberFormatException e)
			{
				return false;
			}
			String lower = name.toLowerCase(java.util.Locale.ROOT);
			int have;
			if (lower.startsWith("total"))
			{
				have = s.totalLevel;
			}
			else if (lower.startsWith("combat"))
			{
				have = s.combatLevel;
			}
			else
			{
				Integer lvl = s.levels.get(name);
				if (lvl == null)
				{
					return false;
				}
				have = lvl;
			}
			if (have < need)
			{
				return false;
			}
		}
		for (String q : quests)
		{
			if (!s.questsDone.contains(q))
			{
				return false;
			}
		}
		for (VarCheck v : vars)
		{
			if (!v.met(s))
			{
				return false;
			}
		}
		for (List<ItemChoice> any : items)
		{
			boolean ok = false;
			for (ItemChoice c : any)
			{
				if (c.met(s))
				{
					ok = true;
					break;
				}
			}
			if (!ok)
			{
				return false;
			}
		}
		return true;
	}

	/** What to tell the player: "Varrock Teleport", "Fairy ring: A I Q", "Climb-down Ladder". */
	public String label()
	{
		String what = object == null ? null : object.replaceAll("\\s+\\d+$", "").trim();
		if (kind.label != null)
		{
			return display != null && !display.isEmpty() ? kind.label + ": " + display.replaceFirst("^\\d+: ", "") : kind.label;
		}
		if (display != null && !display.isEmpty())
		{
			return display;
		}
		return what == null || what.isEmpty() ? "Use the shortcut" : what;
	}
}

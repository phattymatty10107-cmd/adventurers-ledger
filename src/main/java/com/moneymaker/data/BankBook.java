package com.moneymaker.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Your bank sorted into types (runes, teleports, potions, gear...), with ideas for clearing it out
 * and tag tabs you can paste into RuneLite's Bank Tags plugin.
 */
public final class BankBook
{
	/** What the client knows about an item: looked up once on the client thread. */
	public static final class Info
	{
		private final String name;
		private final int haPrice;
		private final boolean tradeable;
		/** Equipment slot index, or -1 if it can't be worn. */
		private final int slot;

		public Info(String name, int haPrice, boolean tradeable, int slot)
		{
			this.name = name;
			this.haPrice = haPrice;
			this.tradeable = tradeable;
			this.slot = slot;
		}

		public String getName()
		{
			return name;
		}

		public int getHaPrice()
		{
			return haPrice;
		}

		public boolean isTradeable()
		{
			return tradeable;
		}

		public int getSlot()
		{
			return slot;
		}
	}

	/** Bank types, in the order the tidy layout puts them. */
	public enum Category
	{
		RUNES("Coins & runes", "runes", 561),
		TELEPORTS("Teleports", "teleports", 8013),
		WEAPONS("Weapons", "weapons", 4151),
		ARMOUR("Armour", "armour", 1127),
		JEWELLERY("Jewellery", "jewellery", 1712),
		AMMO("Ammo", "ammo", 892),
		POTIONS("Potions", "potions", 2434),
		FOOD("Food", "food", 385),
		TOOLS("Tools", "tools", 1275),
		HERBS("Herbs", "herbs", 257),
		FARMING("Seeds & farming", "farming", 5295),
		ORES("Ores & bars", "ores", 2363),
		LOGS("Logs & planks", "logs", 1513),
		CRAFTING("Crafting & fletching", "crafting", 1615),
		FISH("Raw fish & meat", "fish", 383),
		BONES("Bones & ashes", "bones", 536),
		CLUES("Clues", "clues", 2677),
		OTHER("Everything else", "other", 1925);

		private final String label;
		private final String tag;
		private final int icon;

		Category(String label, String tag, int icon)
		{
			this.label = label;
			this.tag = tag;
			this.icon = icon;
		}

		public String getLabel()
		{
			return label;
		}

		/** The bank tag name this type is exported as ("ledger potions"). */
		public String getTag()
		{
			return TAG_PREFIX + " " + tag;
		}

		public int getIcon()
		{
			return icon;
		}
	}

	/** One stack in your bank, priced. */
	public static final class Entry
	{
		private final int id;
		private final String name;
		private final long qty;
		private final Category category;
		/** What one sells for on the GE after tax (0 if it can't be sold). */
		private final long geEach;
		private final long alchEach;
		private final boolean tradeable;

		public Entry(int id, String name, long qty, Category category, long geEach, long alchEach, boolean tradeable)
		{
			this.id = id;
			this.name = name;
			this.qty = qty;
			this.category = category;
			this.geEach = geEach;
			this.alchEach = alchEach;
			this.tradeable = tradeable;
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public long getQty()
		{
			return qty;
		}

		public Category getCategory()
		{
			return category;
		}

		public long getGeEach()
		{
			return geEach;
		}

		public long getAlchEach()
		{
			return alchEach;
		}

		public boolean isTradeable()
		{
			return tradeable;
		}

		/** The whole stack at GE prices (after tax), or alch value if it doesn't sell. */
		public long getValue()
		{
			return qty * Math.max(geEach, alchEach);
		}
	}

	/** A clear-out idea for one stack, with why and what it frees up. */
	public static final class Idea
	{
		private final Entry entry;
		private final String reason;
		/** Extra gp you'd get doing this rather than leaving it (or the whole sale value). */
		private final long gain;

		Idea(Entry entry, String reason, long gain)
		{
			this.entry = entry;
			this.reason = reason;
			this.gain = gain;
		}

		public Entry getEntry()
		{
			return entry;
		}

		public String getReason()
		{
			return reason;
		}

		public long getGain()
		{
			return gain;
		}
	}

	public static final String TAG_PREFIX = "ledger";
	public static final String LAYOUT_TAG = "ledger";
	public static final String CLEAR_OUT_TAG = "ledger clear out";
	/** Coins, shown on the tidy tab. */
	public static final int COINS = 995;
	public static final int PLATINUM_TOKEN = 13204;
	/** Spade, the icon on the clear-out tab. */
	private static final int CLEAR_OUT_ICON = 952;
	/** Items in one row of the bank. */
	public static final int ROW = 8;
	/** Ignore alching when it beats selling by less than this for the whole stack. */
	static final long MIN_ALCH_GAIN = 1_000;

	private final List<Entry> entries;

	public BankBook(List<Entry> entries)
	{
		List<Entry> copy = new ArrayList<>(entries);
		copy.sort(Comparator.comparingLong(Entry::getValue).reversed().thenComparing(Entry::getName));
		this.entries = Collections.unmodifiableList(copy);
	}

	/** Everything, most valuable first. */
	public List<Entry> getEntries()
	{
		return entries;
	}

	public long getTotalValue()
	{
		long sum = 0;
		for (Entry e : entries)
		{
			sum += e.getValue();
		}
		return sum;
	}

	/** Each type with its stacks, most valuable first. Types you have nothing in are left out. */
	public Map<Category, List<Entry>> byCategory()
	{
		Map<Category, List<Entry>> out = new EnumMap<>(Category.class);
		for (Entry e : entries)
		{
			out.computeIfAbsent(e.getCategory(), k -> new ArrayList<>()).add(e);
		}
		return out;
	}

	// ---------------------------------------------------------------- clear-out ideas

	/**
	 * Stacks that are worth more as high alchs than on the GE, by at least {@link #MIN_ALCH_GAIN}
	 * for the whole stack. Most gained first.
	 */
	public List<Idea> betterAlched(long natureRune)
	{
		List<Idea> out = new ArrayList<>();
		for (Entry e : entries)
		{
			if (!e.isTradeable() || e.getAlchEach() <= 0 || e.getCategory() == Category.RUNES)
			{
				continue;
			}
			long perAlch = e.getAlchEach() - natureRune;
			long gain = (perAlch - e.getGeEach()) * e.getQty();
			if (perAlch > e.getGeEach() && gain >= MIN_ALCH_GAIN)
			{
				out.add(new Idea(e, "Alch " + gp(perAlch) + ", GE " + gp(e.getGeEach()) + " each", gain));
			}
		}
		out.sort(Comparator.comparingLong(Idea::getGain).reversed());
		return out;
	}

	/** Types that are kept even when cheap: you need them, and they don't clutter. */
	private static final Set<Category> KEEP = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		Category.RUNES, Category.TELEPORTS, Category.TOOLS, Category.CLUES)));

	/**
	 * Tradeable stacks worth less than {@code under} in total (runes, teleports, tools and clues
	 * are kept, and so is anything without a price). Cheapest first.
	 */
	public List<Idea> clutter(long under)
	{
		List<Idea> out = new ArrayList<>();
		for (Entry e : entries)
		{
			if (!e.isTradeable() || KEEP.contains(e.getCategory()) || e.getId() == COINS
				|| e.getId() == PLATINUM_TOKEN || e.getValue() >= under || e.getValue() <= 0)
			{
				continue;
			}
			out.add(new Idea(e, "Stack worth " + gp(e.getValue()) + " gp", e.getValue()));
		}
		out.sort(Comparator.comparingLong(Idea::getGain).thenComparing(i -> i.getEntry().getName()));
		return out;
	}

	/** Stacks named in {@code outgrown} (lower-case name -> why), e.g. tools you've replaced. */
	public List<Idea> outgrown(Map<String, String> outgrown)
	{
		List<Idea> out = new ArrayList<>();
		for (Entry e : entries)
		{
			String why = outgrown.get(e.getName().toLowerCase(Locale.ROOT));
			if (why != null)
			{
				out.add(new Idea(e, why, e.getValue()));
			}
		}
		out.sort(Comparator.comparingLong(Idea::getGain).reversed());
		return out;
	}

	// ---------------------------------------------------------------- bank tags export

	/**
	 * One tag tab holding the whole bank, laid out by type: every type starts on a new row and
	 * items of a kind sit together (sorted by name, so "Rune platebody" sits by "Rune platelegs").
	 * Paste it into the Bank Tags plugin with "Import tag tab".
	 */
	public String tidyLayout()
	{
		List<List<Integer>> groups = new ArrayList<>();
		for (List<Entry> list : byCategory().values())
		{
			groups.add(ids(byName(list)));
		}
		return layoutTab(LAYOUT_TAG, COINS, groups);
	}

	/** One type as its own tag tab, laid out by name. */
	public static String categoryTab(Category category, List<Entry> list)
	{
		return layoutTab(category.getTag(), category.getIcon(), Collections.singletonList(ids(byName(list))));
	}

	/** The clear-out ideas as one tag tab, so you can open them in the bank and sell or alch. */
	public static String clearOutTab(Collection<Idea> ideas)
	{
		List<Integer> ids = new ArrayList<>();
		for (Idea i : ideas)
		{
			if (!ids.contains(i.getEntry().getId()))
			{
				ids.add(i.getEntry().getId());
			}
		}
		return layoutTab(CLEAR_OUT_TAG, CLEAR_OUT_ICON, Collections.singletonList(ids));
	}

	/**
	 * Bank Tags' import format: {@code banktags,1,<tag>,<icon>,<item ids...>,layout,<pos>,<id>,...}.
	 * Each group starts on a fresh row of the bank.
	 */
	static String layoutTab(String tag, int icon, List<List<Integer>> groups)
	{
		StringBuilder items = new StringBuilder();
		StringBuilder layout = new StringBuilder();
		int pos = 0;
		for (List<Integer> group : groups)
		{
			if (group.isEmpty())
			{
				continue;
			}
			for (int id : group)
			{
				items.append(',').append(id);
				layout.append(',').append(pos++).append(',').append(id);
			}
			pos = (pos + ROW - 1) / ROW * ROW;
		}
		return "banktags,1," + tag + "," + icon + items + ",layout" + layout;
	}

	private static List<Entry> byName(List<Entry> list)
	{
		List<Entry> sorted = new ArrayList<>(list);
		sorted.sort(Comparator.comparing((Entry e) -> e.getName().toLowerCase(Locale.ROOT)));
		return sorted;
	}

	private static List<Integer> ids(List<Entry> list)
	{
		List<Integer> ids = new ArrayList<>();
		for (Entry e : list)
		{
			ids.add(e.getId());
		}
		return ids;
	}

	// ---------------------------------------------------------------- sorting into types

	// equipment slots (EquipmentInventorySlot indexes)
	private static final int WEAPON = 3;
	private static final int AMULET = 2;
	private static final int RING = 12;
	private static final int AMMO = 13;

	private static final Set<String> TELEPORT_WORDS = set("teleport", "ring of dueling", "games necklace",
		"amulet of glory", "amulet of eternal glory", "skills necklace", "combat bracelet", "ring of wealth",
		"digsite pendant", "necklace of passage", "burning amulet", "slayer ring", "xeric's talisman",
		"ectophial", "royal seed pod", "drakan's medallion", "enchanted lyre", "ring of the elements",
		"pharaoh's sceptre", "kharedst's memoirs", "book of the dead", "camulet", "ring of returning",
		"giantsoul amulet", "chronicle", "ring of shadows", "quetzal whistle", "construct. cape",
		"max cape", "achievement diary cape", "ardougne cloak", "desert amulet", "fremennik sea boots",
		"karamja gloves", "kandarin headgear", "morytania legs", "explorer's ring", "rada's blessing",
		"varrock armour", "western banner", "wilderness sword", "falador shield");

	private static final Set<String> TOOLS = set("hammer", "chisel", "tinderbox", "knife", "saw", "needle",
		"rake", "seed dibber", "spade", "secateurs", "magic secateurs", "trowel", "glassblowing pipe",
		"pestle and mortar", "machete", "butterfly net", "magic butterfly net", "bird snare", "box trap",
		"noose wand", "rope", "bucket", "small fishing net", "big fishing net", "lobster pot", "harpoon",
		"coal bag", "open coal bag", "gem bag", "open gem bag", "log basket", "open log basket",
		"fish barrel", "open fish barrel", "herb sack", "open herb sack", "seed box", "open seed box",
		"looting bag", "small pouch", "medium pouch", "large pouch", "giant pouch", "colossal pouch",
		"bolt pouch", "plank sack", "tackle box", "huntsman's kit", "imcando hammer", "crystal saw",
		"karambwan vessel", "dramen staff", "lunar staff", "slayer's staff", "light source", "lantern",
		"candle lantern", "oil lamp", "bullseye lantern", "torch", "lit torch", "mining helmet", "enchanted gem",
		"anti-dragon shield", "nets", "ogre bellows", "fishbowl", "insect repellent", "ice gloves");

	private static final Set<String> TOOL_ENDS = set(" pickaxe", " axe", " harpoon", " mould", "fishing rod",
		" rod", " felling axe", " saw", " watering can");

	private static final Set<String> HERB_NAMES = set("guam leaf", "marrentill", "tarromin", "harralander",
		"ranarr weed", "toadflax", "irit leaf", "avantoe", "kwuarm", "huasca", "snapdragon", "cadantine",
		"lantadyme", "dwarf weed", "torstol", "vial of water", "vial", "eye of newt", "snape grass",
		"limpwurt root", "red spiders' eggs", "white berries", "wine of zamorak", "crushed nest",
		"bird nest", "potato cactus", "jangerberries", "chocolate dust", "unicorn horn dust",
		"dragon scale dust", "goat horn dust", "crushed superior dragon bones", "amylase crystal",
		"zulrah's scales", "crystal dust", "lava scale shard", "mort myre fungus", "nail beast nails",
		"cactus spine", "toad's legs", "volcanic ash", "magic roots", "yew roots");

	private static final Set<String> FOOD = set("shark", "manta ray", "anglerfish", "dark crab", "cooked karambwan",
		"tuna potato", "sea turtle", "monkfish", "swordfish", "lobster", "bass", "tuna", "salmon", "trout",
		"pike", "cod", "herring", "sardine", "shrimps", "anchovies", "mackerel", "bread", "cake",
		"chocolate cake", "jug of wine", "stew", "curry", "potato with cheese", "egg potato", "chilli potato",
		"mushroom potato", "cabbage", "banana", "purple sweets", "blighted manta ray", "blighted anglerfish",
		"blighted karambwan", "cooked chicken", "cooked meat", "rainbow fish", "cave eel", "lava eel",
		"slimy eel", "baked potato", "kebab", "ugthanki kebab", "watermelon slice", "pineapple chunks",
		"pineapple ring", "orange", "strawberry", "cooked jubbly", "cooked chompy", "cooked bream",
		"cooked moss lizard", "giant carp", "karambwanji", "pyre fox", "moonlight mead", "summer pie",
		"wild pie", "admiral pie", "botanical pie", "mushroom pie", "dragonfruit pie");

	private static final Set<String> GEMS = set("sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx",
		"zenyte", "opal", "jade", "red topaz", "molten glass", "bucket of sand", "soda ash", "seaweed",
		"giant seaweed", "flax", "bow string", "thread", "leather", "hard leather", "cowhide", "snakeskin",
		"feather", "arrow shaft", "headless arrow", "gold bar", "silver bar", "ball of wool", "wool", "clay", "soft clay", "pot", "unfired pot",
		"broad arrowheads", "broad bolts", "amethyst", "zenyte shard");

	private static String gp(long v)
	{
		return String.format(Locale.ROOT, "%,d", v);
	}

	private static Set<String> set(String... words)
	{
		return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(words)));
	}

	/** Which type an item goes in, from its name and the slot it's worn in (-1 if none). */
	public static Category categorise(String name, int slot)
	{
		String n = name.toLowerCase(Locale.ROOT).trim();

		if (n.equals("coins") || n.equals("platinum token") || n.endsWith(" rune") || n.endsWith("rune pouch")
			|| n.endsWith(" essence") || n.equals("rune essence"))
		{
			return Category.RUNES;
		}
		if (n.contains("clue scroll") || n.contains("casket") || n.contains("scroll box") || n.contains("clue geode")
			|| n.contains("clue bottle") || n.contains("clue nest") || n.equals("challenge scroll"))
		{
			return Category.CLUES;
		}
		if (containsAny(n, TELEPORT_WORDS))
		{
			return Category.TELEPORTS;
		}
		if (slot == AMMO || n.endsWith(" dart") || n.contains(" dart(") || n.contains("javelin")
			|| n.contains("throwing axe") || n.contains("thrownaxe") || n.contains("chinchompa")
			|| (slot == WEAPON && n.contains(" knife")))
		{
			return Category.AMMO;
		}
		if (TOOLS.contains(n) || endsWithAny(n, TOOL_ENDS) || n.startsWith("watering can"))
		{
			return Category.TOOLS;
		}
		if (slot == WEAPON)
		{
			return Category.WEAPONS;
		}
		if (slot == AMULET || slot == RING)
		{
			return Category.JEWELLERY;
		}
		if (slot >= 0)
		{
			return Category.ARMOUR;
		}
		if (n.startsWith("grimy ") || n.contains("(unf)") || HERB_NAMES.contains(n))
		{
			return Category.HERBS;
		}
		if (n.matches(".*\\(\\d\\)$") || n.contains("potion") || n.contains(" brew") || n.contains(" mix(")
			|| n.startsWith("antidote") || n.startsWith("antipoison") || n.startsWith("anti-venom")
			|| n.contains("antifire"))
		{
			return Category.POTIONS;
		}
		if (FOOD.contains(n) || n.endsWith(" pie") || n.contains("pizza") || n.startsWith("cooked ")
			|| n.endsWith(" cake") || n.startsWith("1/2 ") || n.endsWith(" potato"))
		{
			return Category.FOOD;
		}
		if (n.startsWith("raw ") || n.startsWith("leaping "))
		{
			return Category.FISH;
		}
		if (n.endsWith("bones") || n.endsWith(" ashes") || n.startsWith("ensouled ") || n.endsWith(" remains"))
		{
			return Category.BONES;
		}
		if (n.endsWith(" seed") || n.endsWith(" seeds") || n.endsWith("seedling") || n.endsWith(" sapling")
			|| n.endsWith("compost") || n.equals("plant cure") || n.endsWith(" spore") || n.equals("saltpetre"))
		{
			return Category.FARMING;
		}
		if (n.endsWith(" ore") || n.equals("coal") || (n.endsWith(" bar") && !n.startsWith("chocolate")
			&& !n.equals("gold bar") && !n.equals("silver bar")))
		{
			return Category.ORES;
		}
		if (n.endsWith("logs") || n.endsWith(" plank") || n.equals("plank"))
		{
			return Category.LOGS;
		}
		if (GEMS.contains(n) || n.startsWith("uncut ") || n.endsWith(" leather") || n.contains("dragonhide")
			|| n.endsWith("arrowtips") || n.endsWith(" dart tip") || n.contains("(u)") || n.contains("(unf)")
			|| n.endsWith(" limbs") || n.endsWith(" stock") || n.endsWith(" bolt tips") || n.endsWith(" hide"))
		{
			return Category.CRAFTING;
		}
		return Category.OTHER;
	}

	private static boolean containsAny(String n, Set<String> words)
	{
		for (String w : words)
		{
			if (n.contains(w))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean endsWithAny(String n, Set<String> ends)
	{
		for (String e : ends)
		{
			if (n.endsWith(e))
			{
				return true;
			}
		}
		return false;
	}
}

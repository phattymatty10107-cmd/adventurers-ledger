package com.moneymaker.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.moneymaker.data.BankBook.Category;
import com.moneymaker.data.BankBook.Entry;
import com.moneymaker.data.BankBook.Idea;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The Bank tab: sorting into types, clear-out ideas and the Bank Tags export. */
public class BankBookTest
{
	private static final int NONE = -1;
	private static final int HEAD = 0;
	private static final int AMULET = 2;
	private static final int WEAPON = 3;
	private static final int BODY = 4;
	private static final int RING = 12;
	private static final int AMMO = 13;

	@Test
	public void sortsItemsIntoTypes()
	{
		assertEquals(Category.RUNES, BankBook.categorise("Coins", NONE));
		assertEquals(Category.RUNES, BankBook.categorise("Nature rune", NONE));
		assertEquals(Category.RUNES, BankBook.categorise("Pure essence", NONE));
		assertEquals(Category.RUNES, BankBook.categorise("Rune pouch", NONE));
		assertEquals(Category.TELEPORTS, BankBook.categorise("Varrock teleport", NONE));
		assertEquals(Category.TELEPORTS, BankBook.categorise("Amulet of glory(4)", AMULET));
		assertEquals(Category.TELEPORTS, BankBook.categorise("Ring of dueling(8)", RING));
		assertEquals(Category.TOOLS, BankBook.categorise("Dragon pickaxe", WEAPON));
		assertEquals(Category.TOOLS, BankBook.categorise("Rune axe", WEAPON));
		assertEquals(Category.TOOLS, BankBook.categorise("Hammer", NONE));
		assertEquals(Category.TOOLS, BankBook.categorise("Knife", NONE));
		assertEquals(Category.TOOLS, BankBook.categorise("Small pouch", NONE));
		assertEquals(Category.AMMO, BankBook.categorise("Rune throwing axe", WEAPON));
		assertEquals(Category.AMMO, BankBook.categorise("Rune knife", WEAPON));
		assertEquals(Category.AMMO, BankBook.categorise("Dragon dart(p++)", WEAPON));
		assertEquals(Category.AMMO, BankBook.categorise("Rune arrow", AMMO));
		assertEquals(Category.WEAPONS, BankBook.categorise("Abyssal whip", WEAPON));
		assertEquals(Category.WEAPONS, BankBook.categorise("Rune battleaxe", WEAPON));
		assertEquals(Category.ARMOUR, BankBook.categorise("Rune platebody", BODY));
		assertEquals(Category.ARMOUR, BankBook.categorise("Black mask (10)", HEAD));
		assertEquals(Category.JEWELLERY, BankBook.categorise("Berserker ring", RING));
		assertEquals(Category.POTIONS, BankBook.categorise("Prayer potion(4)", NONE));
		assertEquals(Category.POTIONS, BankBook.categorise("Saradomin brew(3)", NONE));
		assertEquals(Category.POTIONS, BankBook.categorise("Anti-venom+(4)", NONE));
		assertEquals(Category.FOOD, BankBook.categorise("Shark", NONE));
		assertEquals(Category.FOOD, BankBook.categorise("Cooked karambwan", NONE));
		assertEquals(Category.FOOD, BankBook.categorise("Summer pie", NONE));
		assertEquals(Category.FISH, BankBook.categorise("Raw shark", NONE));
		assertEquals(Category.HERBS, BankBook.categorise("Grimy ranarr weed", NONE));
		assertEquals(Category.HERBS, BankBook.categorise("Ranarr potion (unf)", NONE));
		assertEquals(Category.HERBS, BankBook.categorise("Snape grass", NONE));
		assertEquals(Category.FARMING, BankBook.categorise("Ranarr seed", NONE));
		assertEquals(Category.FARMING, BankBook.categorise("Ultracompost", NONE));
		assertEquals(Category.FARMING, BankBook.categorise("Magic sapling", NONE));
		assertEquals(Category.ORES, BankBook.categorise("Runite ore", NONE));
		assertEquals(Category.ORES, BankBook.categorise("Coal", NONE));
		assertEquals(Category.ORES, BankBook.categorise("Adamantite bar", NONE));
		assertEquals(Category.LOGS, BankBook.categorise("Magic logs", NONE));
		assertEquals(Category.LOGS, BankBook.categorise("Logs", NONE));
		assertEquals(Category.LOGS, BankBook.categorise("Mahogany plank", NONE));
		assertEquals(Category.CRAFTING, BankBook.categorise("Uncut diamond", NONE));
		assertEquals(Category.CRAFTING, BankBook.categorise("Green dragonhide", NONE));
		assertEquals(Category.CRAFTING, BankBook.categorise("Gold bar", NONE));
		assertEquals(Category.CRAFTING, BankBook.categorise("Yew longbow (u)", NONE));
		assertEquals(Category.BONES, BankBook.categorise("Dragon bones", NONE));
		assertEquals(Category.BONES, BankBook.categorise("Infernal ashes", NONE));
		assertEquals(Category.CLUES, BankBook.categorise("Clue scroll (elite)", NONE));
		assertEquals(Category.CLUES, BankBook.categorise("Reward casket (hard)", NONE));
		assertEquals(Category.OTHER, BankBook.categorise("Ghostly robe", NONE));
	}

	private static Entry entry(int id, String name, long qty, long ge, long alch)
	{
		return new Entry(id, name, qty, BankBook.categorise(name, NONE), ge, alch, true);
	}

	@Test
	public void totalsAndTypes()
	{
		BankBook bank = new BankBook(Arrays.asList(
			entry(385, "Shark", 100, 900, 120),
			entry(995, "Coins", 5_000, 1, 0),
			entry(2434, "Prayer potion(4)", 10, 8_000, 0)));
		assertEquals(90_000 + 5_000 + 80_000, bank.getTotalValue());
		assertEquals("most valuable first", "Shark", bank.getEntries().get(0).getName());
		Map<Category, List<Entry>> byType = bank.byCategory();
		assertEquals("in layout order", Arrays.asList(Category.RUNES, Category.POTIONS, Category.FOOD),
			Arrays.asList(byType.keySet().toArray()));
	}

	@Test
	public void alchIdeasNeedToBeatTheGeByEnough()
	{
		BankBook bank = new BankBook(Arrays.asList(
			// alchs for 600 - 100 nature = 500 vs 300 on the GE, 200 x 50 = 10,000 more
			entry(1, "Rune med helm", 50, 300, 600),
			// only 1 more each: not worth the bother
			entry(2, "Steel platebody", 3, 499, 600),
			// sells for more than it alchs
			entry(3, "Shark", 100, 900, 120),
			// runes are never alched
			entry(4, "Death rune", 100, 0, 200)));
		List<Idea> alch = bank.betterAlched(100);
		assertEquals(1, alch.size());
		assertEquals("Rune med helm", alch.get(0).getEntry().getName());
		assertEquals(10_000, alch.get(0).getGain());
	}

	@Test
	public void clutterKeepsRunesToolsAndUnpricedItems()
	{
		BankBook bank = new BankBook(Arrays.asList(
			entry(1, "Trout", 3, 50, 10),
			entry(2, "Air rune", 10, 4, 0),
			entry(3, "Hammer", 1, 20, 0),
			entry(4, "Mystery thing", 1, 0, 0),
			entry(5, "Shark", 100, 900, 120)));
		List<Idea> clutter = bank.clutter(10_000);
		assertEquals(1, clutter.size());
		assertEquals("Trout", clutter.get(0).getEntry().getName());
	}

	@Test
	public void exportsATidyLayoutWithEachTypeOnNewRows()
	{
		BankBook bank = new BankBook(Arrays.asList(
			entry(561, "Nature rune", 100, 100, 0),
			entry(556, "Air rune", 100, 4, 0),
			entry(385, "Shark", 1, 900, 0)));
		// runes (sorted by name: Air, Nature) in slots 0 and 1, then food starts the next row at slot 8
		assertEquals("banktags,1,ledger,995,556,561,385,layout,0,556,1,561,8,385", bank.tidyLayout());
	}

	@Test
	public void layoutRowsWrapAfterEight()
	{
		String tab = BankBook.layoutTab("t", 1, Arrays.asList(
			Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9), Arrays.asList(10)));
		assertTrue(tab, tab.endsWith(",8,9,16,10"));
		assertTrue(tab, tab.startsWith("banktags,1,t,1,1,2,3,4,5,6,7,8,9,10,layout,0,1,"));
	}

	@Test
	public void tagNamesHaveNoCharactersBankTagsDrops()
	{
		for (Category c : Category.values())
		{
			assertTrue(c.getTag(), c.getTag().matches("[a-z ]+"));
		}
		assertTrue(BankBook.CLEAR_OUT_TAG.matches("[a-z ]+"));
	}

	@Test
	public void outgrownToolsAreOnlyOnesYouHaveBetterFor() throws Exception
	{
		Gear gear;
		try (InputStream in = Gear.class.getResourceAsStream("../gear.txt"))
		{
			gear = Gear.load(in);
		}
		Map<String, Gear.Owned> owned = new HashMap<>();
		for (String n : new String[]{"Bronze pickaxe", "Rune pickaxe", "Dragon pickaxe", "Iron axe",
			"Small pouch", "Medium pouch", "Large pouch"})
		{
			owned.put(n.toLowerCase(), new Gear.Owned(n, 1, "bank"));
		}

		// Mining 70: the dragon pickaxe is usable, so bronze and rune can go
		Map<String, String> out = gear.outgrown(owned, skill -> "MINING".equals(skill) ? 70 : 1);
		assertTrue(out.containsKey("bronze pickaxe"));
		assertTrue(out.containsKey("rune pickaxe"));
		assertFalse("your best one stays", out.containsKey("dragon pickaxe"));
		assertFalse("your only axe stays", out.containsKey("iron axe"));
		assertFalse("pouches are used together", out.containsKey("small pouch"));

		// Mining 50: you can't use the dragon pickaxe yet, so the rune one stays
		out = gear.outgrown(owned, skill -> "MINING".equals(skill) ? 50 : 1);
		assertTrue(out.containsKey("bronze pickaxe"));
		assertFalse(out.containsKey("rune pickaxe"));
	}
}

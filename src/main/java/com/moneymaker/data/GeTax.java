package com.moneymaker.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Grand Exchange sales tax: 2% of the sell price per item, rounded down,
 * capped at 5m per item. Items selling for under 50gp are exempt, as are bonds
 * and a short list of everyday items.
 */
public final class GeTax
{
	public static final int BOND_ID = 13190;
	private static final long CAP = 5_000_000L;

	/**
	 * Items the GE never taxes, by lower-case name. From the OSRS Wiki's Grand Exchange tax
	 * page (checked November 2026); update this if Jagex changes the list.
	 */
	private static final Set<String> EXEMPT = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"old school bond",
		"energy potion(1)", "energy potion(2)", "energy potion(3)", "energy potion(4)",
		"bronze arrow", "iron arrow", "steel arrow", "bronze dart", "iron dart", "steel dart", "mind rune",
		"bass", "bread", "cake", "cooked chicken", "cooked meat", "herring", "lobster", "mackerel", "meat pie",
		"pike", "salmon", "shrimps", "tuna",
		"ardougne teleport (tablet)", "camelot teleport (tablet)", "civitas illa fortis teleport (tablet)",
		"falador teleport (tablet)", "kourend castle teleport (tablet)", "lumbridge teleport (tablet)",
		"varrock teleport (tablet)", "teleport to house (tablet)",
		"games necklace(8)", "ring of dueling(8)",
		"chisel", "gardening trowel", "glassblowing pipe", "hammer", "needle", "pestle and mortar", "rake", "saw",
		"secateurs", "seed dibber", "shears", "spade", "watering can")));

	private GeTax()
	{
	}

	public static boolean isExempt(int itemId, String name)
	{
		return itemId == BOND_ID || (name != null && EXEMPT.contains(name.trim().toLowerCase(Locale.ROOT)));
	}

	/** Tax on one item, taking the exempt list into account. */
	public static long perItem(int itemId, String name, long price)
	{
		return isExempt(itemId, name) ? 0 : perItem(itemId, price);
	}

	public static long perItem(int itemId, long price)
	{
		if (itemId == BOND_ID || price < 50)
		{
			return 0;
		}
		return Math.min(price * 2 / 100, CAP);
	}

	/** Profit per item when buying at {@code buy} and selling at {@code sell}. */
	public static long margin(int itemId, long buy, long sell)
	{
		return sell - perItem(itemId, sell) - buy;
	}
}

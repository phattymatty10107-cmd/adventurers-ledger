package com.moneymaker;

import com.moneymaker.data.PriceMode;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(MoneyMakerConfig.GROUP)
public interface MoneyMakerConfig extends Config
{
	String GROUP = "moneymaker";

	@ConfigItem(
		keyName = "priceMode",
		name = "Prices",
		description = "Instant: buy and sell straight away (safest). Average: last hour's average. "
			+ "Patient: place offers and wait (most profit, slowest).",
		position = 0
	)
	default PriceMode priceMode()
	{
		return PriceMode.INSTANT;
	}

	@Range(min = 10, max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "speed",
		name = "Your speed",
		description = "Scale the actions-per-hour estimates to how fast you play",
		position = 1
	)
	default int speed()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "onlyDoable",
		name = "Only jobs I can do",
		description = "Hide jobs above your current level (needs you to be logged in)",
		position = 2
	)
	default boolean onlyDoable()
	{
		return false;
	}

	@ConfigItem(
		keyName = "includeMembers",
		name = "Include members jobs",
		description = "Show members-only jobs",
		position = 3
	)
	default boolean includeMembers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideLowVolume",
		name = "Hide low-volume jobs",
		description = "Hide jobs whose product trades less per hour than you'd make",
		position = 4
	)
	default boolean hideLowVolume()
	{
		return false;
	}

	@Range(min = 1, max = 60)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "refreshMinutes",
		name = "Price refresh",
		description = "How often to pull prices from the OSRS Wiki",
		position = 5
	)
	default int refreshMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "farmNotify",
		name = "Farm run alerts",
		description = "Send a notification when a farm run, bird house or daily timer is ready",
		position = 6
	)
	default boolean farmNotify()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ironman",
		name = "Ironman mode",
		description = "Detect from account: on for ironman, hardcore, ultimate and group ironman accounts. "
			+ "Train tab shows what to gather instead of what to buy, and opens first",
		position = 7
	)
	default IronmanMode ironman()
	{
		return IronmanMode.AUTO;
	}
}

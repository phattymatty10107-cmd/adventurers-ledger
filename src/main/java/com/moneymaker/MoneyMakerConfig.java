package com.moneymaker;

import com.moneymaker.data.PriceMode;
import com.moneymaker.route.PlayerState;
import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
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
		description = "Hide jobs whose product trades less per hour than you'd make, or whose price is over a day old",
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
		description = "How often to download prices from the OSRS Wiki price API (prices.runescape.wiki). "
			+ "Only price requests are sent, nothing about your account",
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

	@ConfigSection(
		name = "Routes",
		description = "The route finder: the path drawn to a quest, a bank or a spot you pick on the world map",
		position = 20
	)
	String ROUTES = "routes";

	@ConfigItem(
		keyName = "routeTeleports",
		name = "Teleports to use",
		description = "None: walking, boats and the like only. Only free ones: teleports that cost nothing "
			+ "(no runes, tablets or charges). Any I can use now: every teleport you have the level, quest and "
			+ "items for in your inventory or worn. Your bank isn't checked",
		position = 21,
		section = ROUTES
	)
	default PlayerState.Teleports routeTeleports()
	{
		return PlayerState.Teleports.ALL;
	}

	@ConfigItem(
		keyName = "routeAvoidWilderness",
		name = "Stay out of the Wilderness",
		description = "Never route through the Wilderness unless you start in it or are going into it",
		position = 22,
		section = ROUTES
	)
	default boolean routeAvoidWilderness()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routeGround",
		name = "Draw on the ground",
		description = "Draw the route in the game view, with a marked tile wherever you use a door, boat or teleport",
		position = 23,
		section = ROUTES
	)
	default boolean routeGround()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routeMinimap",
		name = "Draw on the minimap",
		description = "Draw the route on the minimap",
		position = 24,
		section = ROUTES
	)
	default boolean routeMinimap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routeColor",
		name = "Route colour",
		description = "The colour of the route line",
		position = 25,
		section = ROUTES
	)
	default Color routeColor()
	{
		return new Color(0, 200, 255);
	}
}

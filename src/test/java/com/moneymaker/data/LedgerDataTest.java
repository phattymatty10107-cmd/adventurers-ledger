package com.moneymaker.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Checks the maths and the data files without needing the game running. */
public class LedgerDataTest
{
	private static final long NOW = 1_800_000_000_000L;

	private static InputStream text(String s)
	{
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	private static InputStream file(String name)
	{
		InputStream in = Recipe.class.getResourceAsStream("../" + name);
		assertNotNull("missing " + name, in);
		return in;
	}

	private static PriceBook book(ItemPrice... items)
	{
		Map<String, ItemPrice> m = new HashMap<>();
		for (ItemPrice p : items)
		{
			m.put(PriceBook.key(p.getName()), p);
		}
		return new PriceBook(m, NOW);
	}

	/** An item that traded a moment ago at one price. */
	private static ItemPrice item(int id, String name, long price)
	{
		return new ItemPrice(id, name, 0, price, price, price, price, 100_000, NOW / 1000);
	}

	@Test
	public void taxIsTwoPercentRoundedDownAndCapped()
	{
		assertEquals(0, GeTax.perItem(1, "Thing", 49));
		assertEquals(1, GeTax.perItem(1, "Thing", 50));
		assertEquals(1, GeTax.perItem(1, "Thing", 99));
		assertEquals(2, GeTax.perItem(1, "Thing", 100));
		assertEquals(5_000_000, GeTax.perItem(1, "Thing", 2_000_000_000L));
	}

	@Test
	public void exemptItemsPayNoTax()
	{
		assertEquals(0, GeTax.perItem(GeTax.BOND_ID, "Old school bond", 15_000_000));
		assertEquals(0, GeTax.perItem(558, "Mind rune", 1_000));
		assertEquals(0, GeTax.perItem(1, " LOBSTER ", 1_000));
		assertEquals(0, GeTax.perItem(1, "Teleport to house (tablet)", 1_000));
		assertEquals(20, GeTax.perItem(1, "Shark", 1_000));
		assertEquals(20, GeTax.perItem(1, null, 1_000));
	}

	@Test
	public void recipeLineIsParsed() throws Exception
	{
		List<Recipe> list = Recipe.load(text(
			"# comment\n"
				+ "Herblore|Clean grimy guam leaf|HERBLORE|3|1|5000|0|Grimy guam leaf*1|Guam leaf*1|2.5\n"
				+ "Combat|Kill thing|MELEE|70|1|30|-100|Shark*2|Bones*1;Rare*0.01|300|SLAYER:75;MELEE:70|kill|https://example.org\n"));
		assertEquals(2, list.size());
		Recipe kill = list.get(1);
		assertEquals(2, kill.getReqs().size());
		assertEquals("SLAYER", kill.getReqs().get(1).getSkill());
		assertEquals(0.01, kill.getOutputs().get(1).getQty(), 1e-9);
		assertEquals("kill", kill.getUnit());
	}

	@Test
	public void aBadLineIsSkippedNotFatal() throws Exception
	{
		List<Recipe> list = Recipe.load(text(
			"Herblore|Broken|HERBLORE|three|1|5000|0|A*1|B*1|2.5\n"
				+ "Herblore|No star|HERBLORE|3|1|5000|0|A|B*1|2.5\n"
				+ "Herblore|Good|HERBLORE|3|1|5000|0|A*1|B*1|2.5\n"));
		assertEquals(1, list.size());
		assertEquals("Good", list.get(0).getName());
	}

	@Test
	public void jobProfitIsAfterTaxAndBuyLimit() throws Exception
	{
		Recipe r = Recipe.load(text("Test|Make B|NONE|1|0|1000|0|A*1|B*1|0\n")).get(0);
		ItemPrice a = new ItemPrice(1, "A", 400, 100, 100, 100, 100, 100_000, NOW / 1000);
		JobResult res = JobResult.evaluate(r, book(a, item(2, "B", 1_000)), PriceMode.INSTANT, 100, 0, -1, -1);
		assertNotNull(res);
		// sell 1000, less 2% tax (20), less 100 to buy
		assertEquals(880, res.getProfitEach());
		// a limit of 400 per 4 hours means 100 an hour, not the 1000 the recipe allows
		assertTrue(res.isLimitCapped());
		assertEquals(88_000, res.getGpPerHour());
		assertFalse(res.isStale());
	}

	@Test
	public void exemptProductIsNotTaxed() throws Exception
	{
		Recipe r = Recipe.load(text("Test|Cook lobster|NONE|1|0|1000|0|Raw lobster*1|Lobster*1|0\n")).get(0);
		JobResult res = JobResult.evaluate(r, book(item(1, "Raw lobster", 100), item(2, "Lobster", 1_000)),
			PriceMode.INSTANT, 100, 0, -1, -1);
		assertEquals(900, res.getProfitEach());
	}

	@Test
	public void priceWithNoTradeForADayIsFlagged() throws Exception
	{
		Recipe r = Recipe.load(text("Test|Make B|NONE|1|0|1000|0|A*1|B*1|0\n")).get(0);
		long twoDaysAgo = NOW / 1000 - 2 * 24 * 3600;
		ItemPrice old = new ItemPrice(2, "B", 0, 1_000, 1_000, 0, 0, 0, twoDaysAgo);
		JobResult res = JobResult.evaluate(r, book(item(1, "A", 100), old), PriceMode.INSTANT, 100, 0, -1, -1);
		assertTrue(res.isStale());
		assertEquals("B", res.getStaleItem());

		// an hour-old trade with nothing in the last hour is quiet, not stale
		ItemPrice quiet = new ItemPrice(2, "B", 0, 1_000, 1_000, 0, 0, 0, NOW / 1000 - 3600);
		assertFalse(JobResult.evaluate(r, book(item(1, "A", 100), quiet), PriceMode.INSTANT, 100, 0, -1, -1).isStale());
	}

	@Test
	public void missingPriceMeansNoResult() throws Exception
	{
		Recipe r = Recipe.load(text("Test|Make B|NONE|1|0|1000|0|A*1|B*1|0\n")).get(0);
		assertNull(JobResult.evaluate(r, book(item(1, "A", 100)), PriceMode.INSTANT, 100, 0, -1, -1));
	}

	@Test
	public void shippedDataFilesAllLoad() throws Exception
	{
		assertTrue(Recipe.load(file("recipes.txt")).size() > 500);
		assertTrue(BossSetup.load(file("bosses.txt")).size() > 40);
		assertTrue(FarmRun.load(file("farmruns.txt")).size() > 30);
		assertFalse(Training.load(file("training.txt")).getSkills().isEmpty());
		assertNotNull(Gear.load(file("gear.txt")));
		assertNotNull(GearPricer.load(file("aliases.txt")));
	}

	@Test
	public void farmRoutesBelongToRealRuns() throws Exception
	{
		List<FarmRun> runs = FarmRun.load(file("farmruns.txt"));
		FarmRoute routes = FarmRoute.load(file("farmroutes.txt"));
		int withRoute = 0;
		for (FarmRun run : runs)
		{
			if (!routes.stopsFor(run).isEmpty())
			{
				withRoute++;
			}
		}
		assertTrue("expected most farm runs to have a route, got " + withRoute, withRoute >= 30);

		// every route in the file must be named after a run or a group that exists (catches typos)
		java.util.Set<String> known = new java.util.HashSet<>();
		for (FarmRun run : runs)
		{
			known.add(run.getName().toLowerCase(java.util.Locale.ROOT));
			known.add(run.getGroup().toLowerCase(java.util.Locale.ROOT));
		}
		try (java.io.BufferedReader r = new java.io.BufferedReader(
			new java.io.InputStreamReader(file("farmroutes.txt"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String key = line.split("\\|", -1)[0].trim().toLowerCase(java.util.Locale.ROOT);
				assertTrue("farmroutes.txt names a run that doesn't exist: " + key, known.contains(key));
			}
		}
	}

	@Test
	public void routeForARunBeatsItsGroup() throws Exception
	{
		FarmRun run = FarmRun.load(text(
			"Special trees|Redwood tree run|FARMING|90|1|6400|3|1|Redwood sapling*1||0|100|note|url\n")).get(0);
		FarmRoute routes = FarmRoute.load(text(
			"Special trees|Somewhere|walk|\nRedwood tree run|Farming Guild|Skills necklace|85 Farming\n"));
		assertEquals(1, routes.stopsFor(run).size());
		assertEquals("Farming Guild", routes.stopsFor(run).get(0).getName());
		assertEquals("85 Farming", routes.stopsFor(run).get(0).getNeeds());
	}

	@Test
	public void bossQuestsNameRealBossesAndRealQuests() throws Exception
	{
		BossQuests data = BossQuests.load(file("bossquests.txt"), file("questlines.txt"));
		assertTrue(data.getBosses().size() > 20);

		java.util.Set<String> bosses = new java.util.HashSet<>();
		for (BossSetup b : BossSetup.load(file("bosses.txt")))
		{
			bosses.add(b.getBoss());
		}
		for (String boss : data.getBosses())
		{
			assertTrue("bossquests.txt names a boss that isn't in bosses.txt: " + boss, bosses.contains(boss));
		}

		// progress is looked up by RuneLite's quest names, so a typo would silently show as "not done"
		java.util.Set<String> real = new java.util.HashSet<>();
		for (net.runelite.api.Quest q : net.runelite.api.Quest.values())
		{
			real.add(q.getName());
		}
		for (String quest : data.allQuests())
		{
			assertTrue("not a quest RuneLite knows: " + quest, real.contains(quest));
			assertFalse(quest + " is listed as needed before itself", data.lineFor(quest).contains(quest));
		}
	}

	@Test
	public void questLineComesBeforeTheQuest() throws Exception
	{
		BossQuests data = BossQuests.load(
			text("Zulrah|Regicide|Only as far as Port Tyras\nNex|Troll Stronghold|\nNex|The Frozen Door|\n"),
			text("Regicide|Plague City;Biohazard;Underground Pass\n"));
		assertEquals(1, data.forBoss("Zulrah").size());
		assertEquals("Only as far as Port Tyras", data.forBoss("Zulrah").get(0).getNote());
		assertEquals(java.util.Arrays.asList("Plague City", "Biohazard", "Underground Pass"), data.lineFor("Regicide"));
		assertEquals(2, data.forBoss("Nex").size());
		assertTrue(data.forBoss("Giant Mole").isEmpty());
		assertTrue(data.lineFor("Cook's Assistant").isEmpty());
	}

	@Test
	public void questWikiLinksAreEncoded()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Dragon_Slayer_II", BossQuests.wikiUrl("Dragon Slayer II"));
		assertEquals("https://oldschool.runescape.wiki/w/Legends%27_Quest", BossQuests.wikiUrl("Legends' Quest"));
		assertEquals("https://oldschool.runescape.wiki/w/Romeo_%26_Juliet", BossQuests.wikiUrl("Romeo & Juliet"));
		assertEquals("https://oldschool.runescape.wiki/w/Wanted%21", BossQuests.wikiUrl("Wanted!"));
	}

	@Test
	public void bossSkillsNameRealBossesAndSkills() throws Exception
	{
		BossCheck check = BossCheck.load(file("bossskills.txt"));
		assertTrue(check.all().size() >= 10);
		java.util.Set<String> bosses = new java.util.HashSet<>();
		for (BossSetup b : BossSetup.load(file("bosses.txt")))
		{
			bosses.add(b.getBoss());
		}
		for (Map.Entry<String, List<BossCheck.SkillReq>> e : check.all().entrySet())
		{
			assertTrue("bossskills.txt names a boss that isn't in bosses.txt: " + e.getKey(), bosses.contains(e.getKey()));
			for (BossCheck.SkillReq r : e.getValue())
			{
				net.runelite.api.Skill.valueOf(r.getSkill()); // throws if it isn't a real skill
				assertTrue(r.getLevel() >= 2 && r.getLevel() <= 99);
			}
		}
		assertEquals(91, check.skillsFor("Cerberus").get(0).getLevel());
		assertTrue(check.skillsFor("Giant Mole").isEmpty());
	}

	private static Map<String, Gear.Owned> have(Object... nameQtyWhere)
	{
		Map<String, Gear.Owned> m = new HashMap<>();
		for (int i = 0; i < nameQtyWhere.length; i += 3)
		{
			String name = (String) nameQtyWhere[i];
			m.put(name.toLowerCase(java.util.Locale.ROOT),
				new Gear.Owned(name, ((Number) nameQtyWhere[i + 1]).longValue(), (String) nameQtyWhere[i + 2]));
		}
		return m;
	}

	@Test
	public void ownedGearIsRecognised()
	{
		Map<String, Gear.Owned> mine = have("Abyssal whip", 1, "bank", "Dragon defender", 1, "worn");
		assertEquals("bank", BossCheck.owned("Abyssal whip", mine).getWhere());
		assertNull(BossCheck.owned("Ghrazi rapier", mine));
		// things used together only count when you own all of them
		assertNotNull(BossCheck.owned("Abyssal whip + Dragon defender", mine));
		assertNull(BossCheck.owned("Abyssal whip + Avernic defender", mine));
		assertNotNull(BossCheck.ownedInTier(java.util.Arrays.asList("Ghrazi rapier", "Abyssal whip"), mine));
		assertNull(BossCheck.owned("Abyssal whip", new HashMap<>()));
	}

	@Test
	public void kitCountsOwnedSlotsAndPricesTheRest() throws Exception
	{
		BossSetup setup = BossSetup.load(text(
			"BOSS|Test boss|Melee|https://example.org\n"
				+ "SLOT|Weapon|Ghrazi rapier;Abyssal whip\n"
				+ "SLOT|Body|Torva platebody;Rune platebody\n"
				+ "SLOT|Cape|Fire cape\n")).get(0);
		PriceBook prices = book(item(1, "Ghrazi rapier", 50_000_000), item(2, "Abyssal whip", 1_500_000),
			item(3, "Torva platebody", 300_000_000), item(4, "Rune platebody", 38_000));
		GearPricer pricer = GearPricer.load(text(""));

		BossCheck.Kit kit = BossCheck.kit(setup, pricer, prices, have("Abyssal whip", 1, "bank"));
		assertEquals(3, kit.getSlots());
		assertEquals(1, kit.getSlotsOwned());
		// the body slot is filled with the cheapest option; the cape can't be bought at all
		assertEquals(38_000, kit.getCostToFill());
		assertEquals(java.util.Arrays.asList("Rune platebody"), kit.getToBuy());
		assertEquals(java.util.Arrays.asList("Cape"), kit.getToEarn());
	}

	@Test
	public void coinsIncludePlatinumTokens()
	{
		assertEquals(0, GoalPlan.coins(new HashMap<>()));
		assertEquals(2_500_000, GoalPlan.coins(have("Coins", 500_000, "bag", "Platinum token", 2_000, "bank")));
	}

	@Test
	public void earnersAreBestFirstAndSkipBadPrices() throws Exception
	{
		List<Recipe> recipes = Recipe.load(text(
			"Test|Slow|NONE|1|0|100|0|A*1|B*1|0\n"
				+ "Test|Fast|NONE|1|0|1000|0|A*1|B*1|0\n"
				+ "Test|Loses money|NONE|1|0|1000|0|B*1|A*1|0\n"
				+ "Test|Old price|NONE|1|0|5000|0|A*1|C*1|0\n"));
		ItemPrice stale = new ItemPrice(3, "C", 0, 9_000, 9_000, 0, 0, 0, NOW / 1000 - 3 * 24 * 3600);
		PriceBook prices = book(item(1, "A", 100), item(2, "B", 1_000), stale);
		List<JobResult> jobs = new java.util.ArrayList<>();
		for (Recipe r : recipes)
		{
			jobs.add(JobResult.evaluate(r, prices, PriceMode.INSTANT, 100, 0, -1, -1));
		}
		jobs.add(null);

		List<GoalPlan.Earner> earners = GoalPlan.earners(jobs, 8_800_000, 5);
		assertEquals(2, earners.size());
		assertEquals("Fast", earners.get(0).getJob().getRecipe().getName());
		// Fast makes 880 a go, 1000 an hour = 880k/h, so 8.8m takes 10 hours
		assertEquals(10.0, earners.get(0).getHours(), 1e-6);
		assertEquals(100.0, earners.get(1).getHours(), 1e-6);
		assertEquals(1, GoalPlan.earners(jobs, 1, 1).size());
	}

	@Test
	public void itemSearchPutsBestMatchFirst()
	{
		PriceBook prices = book(item(1, "Bandos chestplate", 18_000_000), item(2, "Bandos tassets", 25_000_000),
			item(3, "Bandos boots", 150_000), item(4, "Ancient ceremonial top", 1), item(5, "Rune platebody", 38_000),
			new ItemPrice(6, "Bandos nothing", 0, 0, 0, 0, 0, 0));
		List<ItemPrice> found = prices.search("bandos", 8);
		assertEquals(3, found.size());
		assertEquals("Bandos boots", found.get(0).getName());
		assertEquals("Bandos chestplate", prices.search(" BANDOS CHESTPLATE ", 8).get(0).getName());
		assertEquals(2, prices.search("bandos", 2).size());
		assertTrue(prices.search("", 8).isEmpty());
	}

	@Test
	public void recipeNamesAreUnique() throws Exception
	{
		Map<String, Integer> seen = new HashMap<>();
		for (Recipe r : Recipe.load(file("recipes.txt")))
		{
			assertNull("duplicate job name: " + r.getName(), seen.put(r.getName(), 1));
		}
	}
}

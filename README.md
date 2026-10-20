# Adventurer's Ledger: a RuneLite plugin

A side panel with four tabs (Jobs, Train, Bosses and Farm), priced live from the OSRS Wiki real-time prices API.

## Jobs

700+ money makers covering **every skill**, ranked by **gp per hour** at live Grand Exchange prices:

- **All 500+ OSRS Wiki money making guides**: bossing, raids, Slayer, combat, Sailing, Runecraft, Thieving, Hunter, Mining, Smithing, Fishing, Cooking, Woodcutting, Farming runs, Agility, Magic and more. Drops, supplies and kill rates come from each guide; prices are live.
- **Processing jobs**: herbs and potions, gems, bows, tanning, planks, decanting and item sets.
- **Training costs** for Prayer (bones), Construction and Firemaking, so the planner can tell you what the next level costs.

Each job shows profit per action (per kill, per catch, per run...) after the 2% GE tax, and gp per hour. Jobs that need you to buy supplies are capped by the GE buy limit when that's the bottleneck. Farm runs and daily buys are spread over how often you can do them, so their gp/hr is per real hour.

Combat jobs that use melee train whichever of Attack, Strength or Defence is lowest.

When you're logged in, each job also shows **what it takes to reach your next level**:

- how many kills or actions you need
- the shopping list of supplies and the cash you'll need
- how much you'll make (or lose) by the time you level
- roughly how long it takes

Jobs you don't have the levels for are marked in red (or hidden with "only jobs you can do"). Hover a job for the full breakdown, or right-click it to open its wiki guide. In the settings you can choose:

- **Price mode:** Instant (default and safest), Average (last hour), or Patient (place offers and wait)
- **Your speed:** scale the rates to how fast you play
- **Filters:** only jobs you can do, include members jobs, and hide jobs whose product trades too slowly

## Farm

The best farm runs at live prices: herbs, hops, trees, fruit trees, hardwood, calquat and redwood, plus bird houses, coral, Miscellania and daily shop buys. Each run shows profit per run, XP, cash needed and growth time; sort by profit per run, profit per real hour, XP or what's ready soonest.

Click a run when you've planted it to start its **timer**. You get a notification when it's ready (turn it off with "Farm run alerts" in the settings), and timers are saved so they survive closing RuneLite.

## Train (and Ironman mode)

Pick a skill and a milestone (for example Mining 15 → 85 for runite ore) and the Train tab plans the **fastest route**: the best method at every level, how many hours each stretch takes, how many actions, and what supplies you need. Every skill is covered, including Sailing, with milestones like new ores, trees, potions, prayers, spells and gear.

- **Ironman mode** (plugin settings) is detected from your account by default (ironman, hardcore, ultimate and group ironman), or set it On/Off. Supplies show as what to **gather** instead of what to buy, and the plugin opens on the Train tab.
- **Your gear**: when you're logged in, it checks your bag, worn gear and bank (open the bank once; it's remembered per account) and shows your best tool for the skill (pickaxe, axe, harpoon, weapon...), the upgrade you can use now, your skilling outfit pieces and useful items like the coal bag or bonecrusher. Supplies in the route show how many you already have.
- "Only methods with no supplies" plans a route that doesn't use up items (e.g. Guardians of the Rift, Tithe Farm, Wintertodt).
- Farming also shows a route using tree runs, with how many runs and days it takes.
- Logged in, it starts from your current level and xp.

Rates are typical estimates from the OSRS Wiki training guides (`tools/gen_training.py`).

## Bosses

Gear set-ups from the OSRS Wiki strategy pages. Each slot lists gear from starter to best in slot, with live GE prices and totals for budget, mid and best kits. Untradeables (Fire cape, Barrows gloves, Void and so on) show how to get them. Charged or crafted items are priced from their tradeable parts, for example the blowpipe from the empty blowpipe, crystal armour from crystal armour seeds, and imbued rings from the plain ring.

Set-ups included: 61 set-ups for 58 bosses, including all three raids, Fight Caves, Inferno and Colosseum (from each boss's OSRS Wiki strategy page).

## Run it

Open this folder in IntelliJ, then open `src/test/java/com/moneymaker/MoneyMakerPluginTest.java` and click ▶ next to `main`.

## Notes

- Rates are estimates (from the OSRS Wiki money making guides where available). Adjust "Your speed" to match how you play.
- Money making data comes from the [OSRS Wiki](https://oldschool.runescape.wiki/w/Money_making_guide) (CC BY-NC-SA 3.0). `tools/gen_wiki_jobs.py` rebuilds it from a snapshot of the guides.
- The data lives in `src/main/resources/com/moneymaker/`:
  - `recipes.txt`: the jobs
  - `bosses.txt`: the gear set-ups
  - `aliases.txt`: untradeables and their tradeable parts

  Add lines there to add jobs or bosses.

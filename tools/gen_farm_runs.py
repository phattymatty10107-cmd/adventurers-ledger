# Builds src/main/resources/com/moneymaker/farmruns.txt: farm runs, bird houses and other timed runs.
#   python3 tools/gen_farm_runs.py <wiki_mmg.json> <prices.json>
#
# Format (all amounts are per whole run):
#   group|name|skill|level|members|growMinutes|runMinutes|patches|inputs|outputs|coins|xp|note|url
# Herb, hop, bird house and daily runs come from the OSRS Wiki recurring money making guides.
# Tree, fruit tree, hardwood, calquat and redwood runs are added here from wiki Farming data.
import json, re, sys
from urllib.parse import quote

SKILLS = {"Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
          "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving",
          "Slayer", "Farming", "Runecraft", "Hunter", "Construction", "Sailing"}

def num(v):
    s = "%.6g" % v
    return ("%.10f" % v).rstrip("0").rstrip(".") if "e" in s else s

def clean(s):
    return s.replace("|", "/").replace("*", "x").replace(";", ",").strip()

def stacks(d):
    return ";".join(f"{clean(k)}*{num(v)}" for k, v in d.items())

WIKI = "https://oldschool.runescape.wiki/w/"
lines = []

def add(group, name, skill, level, members, grow, run, patches, ins, outs, coins, xp, note, url):
    lines.append("|".join([group, clean(name), skill, str(level), "1" if members else "0", num(grow), num(run), str(patches),
                           stacks(ins), stacks(outs), num(coins), num(xp), clean(note), url]))

# ---------------------------------------------------------------- hand-made runs (saplings bought on the GE)
TREE_PATCHES = 6      # Lumbridge, Varrock, Falador, Taverley, Gnome Stronghold, Farming Guild
FRUIT_PATCHES = 6     # Gnome Stronghold, Tree Gnome Village, Brimhaven, Catherby, Lletya, Farming Guild
HARDWOOD_PATCHES = 3  # Fossil Island
# name, level, sapling, plant xp, check xp, payment, payment qty, grow minutes
trees = [("Oak", 15, "Oak sapling", 14, 467.3, "Tomatoes(5)", 1, 200),
         ("Willow", 30, "Willow sapling", 25, 1456.5, "Apples(5)", 1, 280),
         ("Maple", 45, "Maple sapling", 45, 3403.4, "Oranges(5)", 1, 320),
         ("Yew", 60, "Yew sapling", 81, 7069.9, "Cactus spine", 10, 400),
         ("Magic", 75, "Magic sapling", 145.5, 13768.3, "Coconut", 25, 480)]
for n, lvl, sap, px, cx, pay, pq, grow in trees:
    p = TREE_PATCHES
    add("Tree runs", f"{n} tree run", "FARMING", lvl, True, grow, 8, p,
        {sap: p, pay: pq * p}, {}, 200 * p, (px + cx) * p,
        f"{p} tree patches. Pays the gardener to protect each tree and 200 coins to clear it. Chop and dig the stump yourself for roots instead.",
        WIKI + quote(f"{n}_sapling"))

fruit = [("Apple", 27, "Apple sapling", 22, 1199.5, 8.5, "Cooking apple", "Sweetcorn", 9),
         ("Banana", 33, "Banana sapling", 28, 1750.5, 10.5, "Banana", "Apples(5)", 4),
         ("Orange", 39, "Orange sapling", 35.5, 2470.2, 13.5, "Orange", "Strawberries(5)", 3),
         ("Curry", 42, "Curry sapling", 40, 2906.9, 15, "Curry leaf", "Bananas(5)", 5),
         ("Pineapple", 51, "Pineapple sapling", 57, 4605.7, 21.5, "Pineapple", "Watermelon", 10),
         ("Papaya", 57, "Papaya sapling", 72, 6146.4, 27, "Papaya fruit", "Pineapple", 10),
         ("Palm", 68, "Palm sapling", 110.5, 10150.1, 41.5, "Coconut", "Papaya fruit", 15),
         ("Dragonfruit", 81, "Dragonfruit sapling", 140, 17335, 70, "Dragonfruit", "Coconut", 15)]
for n, lvl, sap, px, cx, hx, fr, pay, pq in fruit:
    p = FRUIT_PATCHES
    add("Fruit tree runs", f"{n} tree run", "FARMING", lvl, True, 960, 8, p,
        {sap: p, pay: pq * p}, {fr: 6 * p}, 200 * p, (px + cx + 6 * hx) * p,
        f"{p} fruit tree patches, 6 fruit each. Pays the gardener to protect each tree and 200 coins to clear it.",
        WIKI + quote(f"{n}_sapling"))

for n, lvl, sap, px, cx, pay, pq, grow in [("Teak", 35, "Teak sapling", 35, 7290, "Limpwurt root", 15, 4480),
                                           ("Mahogany", 55, "Mahogany sapling", 63, 15720, "Yanillian hops", 25, 5120)]:
    p = HARDWOOD_PATCHES
    add("Hardwood runs", f"{n} tree run", "FARMING", lvl, True, grow, 4, p,
        {sap: p, pay: pq * p}, {}, 0, (px + cx) * p,
        f"{p} hardwood patches on Fossil Island. Pays the gardener to protect each tree.", WIKI + quote(f"{n}_seed"))

add("Special trees", "Calquat tree run", "FARMING", 72, True, 1280, 4, 2,
    {"Calquat sapling": 2, "Poison ivy berries": 16}, {"Calquat fruit": 12}, 0, (129.5 + 12096 + 291) * 2,
    "Tai Bwo Wannai and Kastori patches, 6 fruit each. Pays the gardener 8 poison ivy berries.", WIKI + "Calquat_tree_seed")
add("Special trees", "Redwood tree run", "FARMING", 90, True, 6400, 3, 1,
    {"Redwood sapling": 1, "Dragonfruit": 6}, {}, 0, 230 + 22450,
    "Farming Guild redwood patch. Pays the gardener 6 dragonfruit.", WIKI + "Redwood_tree_seed")

# ---------------------------------------------------------------- recurring wiki guides
mmg = json.load(open(sys.argv[1]))
prices = json.load(open(sys.argv[2]))
tradeable = {r[1].lower(): r[1] for r in prices["map"]}
id_of = {r[1].lower(): str(r[0]) for r in prices["map"]}
latest = prices["latest"]

KEEP_OTHER = ("Bird house trapping", "Planting Crystal acorn", "Growing coral", "Managing Miscellania",
              "Buying battlestaves from Zaff", "Claiming runes from Lundail", "Claiming Dynamite from Thirus",
              "Claiming pure essence from Wizard Cromperty")
for title, o in sorted(mmg.items()):
    if not o.get("recurring"):
        continue
    short = title.split("/", 1)[1]
    cat = (o.get("category") or "").split("(")[0].strip()
    if cat != "Farming" and short not in KEEP_OTHER:
        continue
    m = re.match(r"\s*([0-9.]+)\s*(minute|hour|day)", o["recurring"])
    if not m:
        continue
    grow = float(m.group(1)) * {"minute": 1, "hour": 60, "day": 1440}[m.group(2)]
    run = 60.0 / o["kph"]
    ins, outs, coins = {}, {}, 0.0
    value_names = {v.lower() for v in o.get("valueNames", [])}
    for it in o["items"]:
        key = it["name"].lower()
        ge = tradeable.get(key)
        priced = ge is not None and key not in value_names and id_of.get(key) in latest
        if priced and it["qty"] > 0:
            hi, lo = latest[id_of[key]]
            expect = ((hi or lo) + (lo or hi)) / 2 * it["qty"]
            if expect > 0 and abs(abs(it["cost"]) - expect) > 0.35 * max(abs(it["cost"]), expect) and abs(abs(it["cost"]) - expect) > 50:
                priced = False
        if priced and it["qty"] > 0:
            d = ins if it["io"] == "in" else outs
            d[ge] = d.get(ge, 0) + it["qty"]
        else:
            coins += it["cost"] if it["io"] == "in" else -it["cost"]
    if short == "Bird house trapping":
        group, skill = "Bird houses", "Hunter"
    elif cat == "Farming":
        sub = o.get("category", "")
        group = "Herb runs" if "Herb" in sub else "Hops" if "Hop" in sub else "Fruit picking" if "Picking" in sub else "Other farming"
        skill = "Farming"
    elif short == "Planting Crystal acorn" or short == "Growing coral":
        group, skill = "Other farming", "Farming"
    else:
        group, skill = "Dailies", None
    xp = sum(float(x["pk"]) for x in o["xp"] if x.get("skill") == skill and x.get("pk"))
    level = 1
    for line in o["skills"]:
        for mm in re.finditer(r"§([A-Za-z]+):([0-9]+)\+?§", line):
            if skill and mm.group(1) == skill:
                level = max(level, int(mm.group(2)))
                break
    patches = 4 if group == "Bird houses" else 10 if group == "Herb runs" else 0
    note = "Bird houses last 50 minutes; 4 houses on Fossil Island." if group == "Bird houses" else \
        "Based on the OSRS Wiki guide (10 herb patches with ultracompost and magic secateurs)." if group == "Herb runs" else \
        "Based on the OSRS Wiki guide."
    name = o["activity"].replace("Bird house trapping", "Bird house run")
    if group == "Herb runs":
        name = "Herb run: " + re.sub(r"^Farming ", "", name)
    elif group == "Hops":
        name = "Hops run: " + re.sub(r"^Farming ", "", name)
    add(group, name, skill.upper() if skill else "NONE", level, not o.get("members", "").lower().startswith("no"),
        grow, run, patches, ins, outs, coins, xp, note, WIKI + quote(title.replace(" ", "_"), safe="/()',_-"))

out = "src/main/resources/com/moneymaker/farmruns.txt"
open(out, "w").write("\n".join(lines) + "\n")
print(len(lines), "runs ->", out)

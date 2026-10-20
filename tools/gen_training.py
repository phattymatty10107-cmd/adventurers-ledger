# Builds src/main/resources/com/moneymaker/training.txt: XP methods and milestone levels for every skill.
#   python3 tools/gen_training.py
#
#   M|SKILL|level|what it unlocks
#   T|SKILL|method|min level|xp/hr at min|xp/hr at 99|xp each|unit|ironman ok|members|intense|cycle minutes|needs|gives|requirements|note
#
# xp/hr is interpolated between the min level and 99. "needs"/"gives" are per action (unit), so the plugin can
# say how many items you need to gather (ironman) or buy. Rates are typical, non-tick-manipulation figures from
# the OSRS Wiki training guides; treat them as estimates.

L = []

def M(skill, *pairs):
    for lvl, text in pairs:
        L.append(f"M|{skill}|{lvl}|{text}")

def T(skill, name, lvl, lo, hi, each=0, unit="", iron=1, mem=1, intense=0, cycle=0, needs=(), gives=(), req="", note=""):
    f = lambda xs: ";".join(f"{n}*{q:g}" for n, q in xs)
    L.append("|".join(["T", skill, name, str(lvl), str(lo), str(hi), f"{each:g}", unit, str(iron), str(mem),
                       str(intense), str(cycle), f(needs), f(gives), req, note]))

# ---------------------------------------------------------------- combat
MELEE_MS = [(5, "Black weapons"), (10, "Mithril weapons"), (20, "Adamant weapons"), (30, "Rune weapons/armour (Def)"),
            (40, "Rune weapons"), (50, "Granite & ancient staff tier"), (60, "Dragon weapons"), (65, "Barrows/Bandos (Def)"),
            (70, "Abyssal whip, Barrows gear"), (75, "Godswords, Abyssal tentacle"), (80, "Blade of saeldor, Ghrazi rapier"),
            (85, "Inquisitor's, Noxious halberd"), (90, "Scythe of vitur"), (99, "Skill cape")]
for sk, ms in [("ATTACK", MELEE_MS), ("STRENGTH", [(50, "Granite maul"), (60, "Warriors' Guild (Att+Str 130)"),
                                                   (70, "Obsidian/Dharok's set bonus"), (99, "Skill cape")]),
               ("DEFENCE", [(1, "Iron armour"), (20, "Mithril armour"), (30, "Adamant armour"), (40, "Rune armour"),
                            (45, "Initiate/ Proselyte"), (60, "Dragon armour"), (65, "Bandos armour"), (70, "Barrows armour, Dragon defender"),
                            (75, "Torva (80), Justiciar"), (80, "Torva, Crystal armour (70)"), (99, "Skill cape")])]:
    M(sk, *ms)
    T(sk, "Cows, chickens & goblins (F2P)", 1, 3000, 15000, mem=0, note="Good for the first few levels")
    T(sk, "Hill & moss giants (F2P)", 20, 12000, 40000, mem=0, note="Big bones for Prayer too")
    T(sk, "Sand & rock crabs (AFK)", 1, 8000, 45000, note="Hosidius / Rellekka crabs, very low effort")
    T(sk, "Ammonite crabs (Fossil Island)", 1, 12000, 55000, req="Bone Voyage", note="Best AFK crabs")
    T(sk, "Slayer tasks", 1, 8000, 60000, note="Slower, but trains Slayer and gets loot")
    T(sk, "Nightmare Zone (Dharok's / obsidian, absorptions)", 60, 60000, 110000,
      req="Quests for the NMZ bosses", note="AFK with absorption potions; NMZ points buy herb boxes")
    T(sk, "Gemstone crab (Cam Torum)", 1, 10000, 50000, req="Perilous Moons access", note="AFK boss crab, gives gems")

M("HITPOINTS", (10, "Default level"), (50, "Fast-healing safety"), (70, "Useful for NMZ/bossing"), (99, "Skill cape"))
T("HITPOINTS", "Melee or ranged training (crabs/NMZ/slayer)", 10, 4000, 40000,
  note="Hitpoints rises by 1.33 xp for every 4 combat xp; train Attack/Strength/Defence/Ranged")
T("HITPOINTS", "Nightmare Zone (Dharok's)", 60, 25000, 40000, req="NMZ quests", note="Comes with your melee xp")

M("RANGED", (20, "Mithril/adamant arrows"), (30, "Green d'hide"), (40, "Blue d'hide, rune arrows, magic shortbow (50)"),
  (50, "Magic shortbow, toxic blowpipe (75)"), (60, "Black d'hide, dragon arrows (60)"), (61, "Rune crossbow"),
  (65, "Black chinchompas"), (70, "Armadyl, karil's"), (75, "Toxic blowpipe, Armadyl crossbow"), (80, "Masori, bowfa (80)"),
  (85, "Zaryte crossbow"), (99, "Skill cape"))
T("RANGED", "Ranging hill/moss giants (F2P)", 1, 6000, 35000, mem=0)
T("RANGED", "Sand & ammonite crabs with arrows/darts", 1, 10000, 45000)
T("RANGED", "Cannon + ranged on slayer tasks", 40, 30000, 60000, req="Dwarf Cannon")
T("RANGED", "Nightmare Zone (toxic blowpipe)", 75, 90000, 130000, req="NMZ quests, blowpipe from Zulrah")
T("RANGED", "Chinning grey chinchompas (MM2 monkeys)", 45, 120000, 200000, each=0, needs=(("Chinchompa", 1),),
  req="Monkey Madness II (or MM1 tunnels)", note="Hunt chinchompas yourself as an ironman (53 Hunter)")
T("RANGED", "Chinning red chinchompas", 55, 180000, 320000, needs=(("Red chinchompa", 1),),
  req="Monkey Madness II", note="Ironmen: catch them at 63 Hunter")
T("RANGED", "Chinning black chinchompas", 65, 250000, 450000, needs=(("Black chinchompa", 1),),
  req="Monkey Madness II", note="Ironmen: catch them in the Wilderness at 73 Hunter")

M("MAGIC", (13, "Fire Strike"), (25, "Varrock Teleport"), (33, "Telekinetic Grab"), (37, "Falador Teleport"),
  (45, "Camelot Teleport"), (50, "Ancient Magicks (with Desert Treasure)"), (55, "High Level Alchemy"),
  (60, "God spells (Mage Arena)"), (66, "Vengeance (with Lunars)"), (70, "Ice Burst"), (75, "Trident of the seas"),
  (77, "Superglass Make, Augury req"), (82, "Ice Blitz, Stun"), (86, "Plank Make"), (94, "Ice Barrage"), (99, "Skill cape"))
T("MAGIC", "Splashing (Fire Strike, AFK)", 13, 13000, 14000, each=11.5, unit="cast", mem=0,
  needs=(("Mind rune", 1), ("Air rune", 2)), note="Use a fire staff; needs -65 magic attack bonus")
T("MAGIC", "Camelot Teleport", 45, 90000, 95000, each=55.5, unit="cast", needs=(("Law rune", 1), ("Air rune", 5)),
  note="Use a staff of air")
T("MAGIC", "High Level Alchemy", 55, 78000, 78000, each=65, unit="cast", mem=0, needs=(("Nature rune", 1),),
  note="Plus one item to alch each cast; use a fire staff")
T("MAGIC", "Superglass Make", 77, 75000, 80000, each=78, unit="cast",
  needs=(("Astral rune", 2), ("Bucket of sand", 18), ("Giant seaweed", 3)), gives=(("Molten glass", 26),),
  req="Lunar Diplomacy", note="Smoke battlestaff covers air/fire runes; great for ironman Crafting")
T("MAGIC", "Tan Leather (Lunar)", 78, 70000, 75000, each=81, unit="cast",
  needs=(("Astral rune", 2), ("Nature rune", 1), ("Green dragonhide", 5)), gives=(("Green dragon leather", 5),),
  req="Lunar Diplomacy, 78 Magic", note="Staff of fire covers fire runes")
T("MAGIC", "Plank Make (Lunar)", 86, 95000, 100000, each=90, unit="cast",
  needs=(("Astral rune", 2), ("Nature rune", 1), ("Mahogany logs", 1)), gives=(("Mahogany plank", 1),),
  req="Dream Mentor", note="Plus 1,050 coins per mahogany plank")
T("MAGIC", "Ice Burst at Maniacal monkeys", 70, 150000, 220000, intense=1,
  req="Desert Treasure I, Monkey Madness II", note="Burst/barrage groups of monkeys; costs runes (chaos, death)")
T("MAGIC", "Ice Barrage at Maniacal monkeys", 94, 300000, 330000, intense=1,
  req="Desert Treasure I, Monkey Madness II", note="Costs blood, death and water runes")

M("PRAYER", (25, "Protect Item"), (31, "Ultimate Strength/Improved Reflexes"), (37, "Protect from Magic"),
  (40, "Protect from Missiles"), (43, "Protect from Melee"), (44, "Eagle Eye/Mystic Might"), (52, "Smite"),
  (60, "Chivalry"), (70, "Piety, Rigour/Augury with scrolls (74/77)"), (74, "Rigour"), (77, "Augury"), (99, "Skill cape"))
T("PRAYER", "Bury bones (F2P)", 1, 8000, 8000, each=4.5, unit="bone", mem=0, needs=(("Bones", 1),))
T("PRAYER", "Bury big bones (F2P)", 1, 27000, 27000, each=15, unit="bone", mem=0, needs=(("Big bones", 1),))
T("PRAYER", "Bury dragon bones", 1, 130000, 130000, each=72, unit="bone", needs=(("Dragon bones", 1),))
T("PRAYER", "Gilded altar: big bones", 1, 60000, 60000, each=52.5, unit="bone", needs=(("Big bones", 1),),
  req="House with a gilded altar (or a friend's)")
T("PRAYER", "Gilded altar: dragon bones", 1, 280000, 280000, each=252, unit="bone", needs=(("Dragon bones", 1),),
  req="House with a gilded altar (or a friend's)")
T("PRAYER", "Chaos altar (Wilderness): dragon bones", 1, 350000, 350000, each=504, unit="bone", needs=(("Dragon bones", 1),),
  note="7x xp with a 50% chance to keep the bone (counted here); dangerous")
T("PRAYER", "Gilded altar: superior dragon bones", 70, 550000, 550000, each=525, unit="bone",
  needs=(("Superior dragon bones", 1),), req="70 Prayer")

# ---------------------------------------------------------------- skilling
M("RUNECRAFT", (5, "Earth runes"), (14, "Fire runes"), (23, "Lava runes"),
  (27, "Guardians of the Rift, Cosmic runes"), (35, "Chaos runes"), (40, "Astral runes"), (44, "Nature runes"),
  (54, "Law runes"), (65, "Death runes"), (77, "Blood runes"), (82, "Double astrals"), (90, "Soul runes"),
  (91, "Double natures"), (95, "Wrath runes"), (99, "Skill cape"))
T("RUNECRAFT", "Air runes (F2P)", 1, 8000, 15000, each=5, unit="essence", mem=0, needs=(("Rune essence", 1),),
  gives=(("Air rune", 1),))
T("RUNECRAFT", "Lava runes (binding necklace + Magic Imbue)", 23, 45000, 85000, each=10.5, unit="essence", intense=1,
  needs=(("Pure essence", 1), ("Earth rune", 1)), gives=(("Lava rune", 1),), note="Very click-intensive")
T("RUNECRAFT", "Guardians of the Rift", 27, 35000, 70000, req="Temple of the Eye",
  note="Best ironman method: no essence needed, gives pouches, runes and outfit")
T("RUNECRAFT", "Ourania Altar (ZMI)", 1, 20000, 60000, each=0, needs=(("Pure essence", 1),),
  note="Random runes; Ourania Teleport needs Lunar Diplomacy")
T("RUNECRAFT", "Astral runes", 40, 30000, 50000, each=8.7, unit="essence", needs=(("Pure essence", 1),),
  gives=(("Astral rune", 1),), req="Lunar Diplomacy")
T("RUNECRAFT", "Blood runes (True Blood altar)", 77, 40000, 55000, each=10.5, unit="essence",
  needs=(("Pure essence", 1),), gives=(("Blood rune", 1),), req="Sins of the Father")
T("RUNECRAFT", "Soul runes (Arceuus, dense essence)", 90, 45000, 55000, note="Mine your own dense essence; AFK-ish")

M("CONSTRUCTION", (16, "Portal to house in Rimmington/Taverley"), (33, "Oak larder"), (40, "Lectern tablets"), (52, "Mahogany table"), (65, "Restoration pool"), (75, "Gilded altar"), (80, "Rejuvenation pool"), (82, "Spirit tree & fairy ring"),
  (90, "Ornate rejuvenation pool, Occult altar"), (91, "Ornate jewellery box"), (99, "Skill cape"))
T("CONSTRUCTION", "Crude wooden chairs → bookshelves", 1, 20000, 30000, each=58, unit="chair",
  needs=(("Plank", 2), ("Steel nails", 2)))
T("CONSTRUCTION", "Oak chairs", 19, 60000, 80000, each=120, unit="chair", needs=(("Oak plank", 2),))
T("CONSTRUCTION", "Oak larders", 33, 200000, 300000, each=480, unit="larder", needs=(("Oak plank", 8),),
  note="Use a demon butler (50) for the fastest rates")
T("CONSTRUCTION", "Mythical capes (teak)", 47, 250000, 300000, each=370, unit="mount", needs=(("Teak plank", 3),),
  req="Dragon Slayer II (mythical cape)")
T("CONSTRUCTION", "Mahogany tables", 52, 350000, 450000, each=840, unit="table", needs=(("Mahogany plank", 6),))
T("CONSTRUCTION", "Oak dungeon doors", 74, 280000, 320000, each=600, unit="door", needs=(("Oak plank", 10),))
T("CONSTRUCTION", "Mahogany Homes contracts", 1, 20000, 120000,
  note="Ironman favourite: planks and steel bars only; gives Carpenter's outfit")

M("AGILITY", (10, "Draynor rooftop"), (20, "Al Kharid rooftop"), (30, "Varrock rooftop"), (40, "Canifis rooftop"),
  (50, "Falador rooftop"), (52, "Wilderness course, Hallowed Sepulchre"), (60, "Seers' rooftop"),
  (62, "Advanced Colossal Wyrm course"), (70, "Pollnivneach rooftop"), (75, "Prifddinas course"),
  (80, "Rellekka rooftop"), (90, "Ardougne rooftop"), (99, "Skill cape"))
for name, lvl, lo, hi, req in [("Gnome Stronghold course", 1, 8000, 8000, ""), ("Draynor rooftop", 10, 9000, 10000, ""),
                                ("Al Kharid rooftop", 20, 12000, 13000, ""), ("Varrock rooftop", 30, 13000, 14000, ""),
                                ("Canifis rooftop", 40, 18000, 19000, ""), ("Falador rooftop", 50, 27000, 28000, ""),
                                ("Colossal Wyrm course", 50, 30000, 45000, "Children of the Sun"),
                                ("Wilderness Agility Course", 52, 45000, 55000, ""),
                                ("Hallowed Sepulchre", 52, 30000, 110000, "Sins of the Father"),
                                ("Seers' Village rooftop", 60, 45000, 55000, ""), ("Pollnivneach rooftop", 70, 52000, 54000, ""),
                                ("Prifddinas course", 75, 62000, 64000, "Song of the Elves"), ("Rellekka rooftop", 80, 55000, 56000, ""),
                                ("Ardougne rooftop", 90, 62000, 63000, "")]:
    T("AGILITY", name, lvl, lo, hi, req=req, note="Marks of grace buy the Graceful outfit" if "rooftop" in name else "")

POT = [("Attack potion(3)", 3, 25, "Grimy guam leaf", "Eye of newt", 0), ("Antipoison(3)", 5, 37.5, "Grimy marrentill", "Unicorn horn dust", 0),
       ("Strength potion(3)", 12, 50, "Grimy tarromin", "Limpwurt root", 0), ("Restore potion(3)", 22, 62.5, "Grimy harralander", "Red spiders' eggs", 0),
       ("Energy potion(3)", 26, 67.5, "Grimy harralander", "Chocolate dust", 0), ("Prayer potion(3)", 38, 87.5, "Grimy ranarr weed", "Snape grass", 0),
       ("Super attack(3)", 45, 100, "Grimy irit leaf", "Eye of newt", 0), ("Superantipoison(3)", 48, 106.3, "Grimy irit leaf", "Unicorn horn dust", 0),
       ("Super energy(3)", 52, 117.5, "Grimy avantoe", "Mort myre fungus", 0), ("Super strength(3)", 55, 125, "Grimy kwuarm", "Limpwurt root", 0),
       ("Super restore(3)", 63, 142.5, "Grimy snapdragon", "Red spiders' eggs", 0), ("Super defence(3)", 66, 150, "Grimy cadantine", "White berries", 0),
       ("Antifire potion(3)", 69, 157.5, "Grimy lantadyme", "Dragon scale dust", 0), ("Ranging potion(3)", 72, 162.5, "Grimy dwarf weed", "Wine of zamorak", 0),
       ("Magic potion(3)", 76, 172.5, "Grimy lantadyme", "Potato cactus", 0), ("Saradomin brew(3)", 81, 180, "Grimy toadflax", "Crushed nest", 0)]
M("HERBLORE", (3, "Attack potions (Druidic Ritual)"), (22, "Restore potions"), (38, "Prayer potions"), (45, "Super attack"),
  (55, "Super strength"), (60, "Weapon poison+ / Ranarr"), (63, "Super restore"), (66, "Super defence"),
  (69, "Antifire"), (72, "Ranging potion"), (78, "Zamorak brew"), (81, "Saradomin brew"), (87, "Anti-venom"),
  (90, "Super combat potion"), (94, "Anti-venom+"), (99, "Skill cape"))
for pot, lvl, xp, herb, sec, _ in POT:
    T("HERBLORE", f"Make {pot}", lvl, round(xp * 2200), round(xp * 2400), each=xp, unit="potion",
      needs=((herb, 1), ("Vial of water", 1), (sec, 1)), gives=((pot, 1),), req="Druidic Ritual",
      note="Clean the herb, make the unfinished potion, add the secondary")

M("THIEVING", (5, "Cake stalls"), (21, "Pyramid Plunder"), (25, "Fruit stalls"), (38, "Master farmers"),
  (45, "Blackjacking"), (55, "Knights of Ardougne"), (70, "Paladins"), (80, "Heroes"), (82, "Vyres"),
  (85, "Elves"), (99, "Skill cape"))
T("THIEVING", "Pickpocketing men & women", 1, 5000, 8000)
T("THIEVING", "Fruit stalls (Hosidius)", 25, 35000, 45000, note="Food for ironmen; easy")
T("THIEVING", "Pyramid Plunder", 21, 20000, 250000, req="Icthlarin's Little Helper", note="Gets much better at 71/81/91")
T("THIEVING", "Master farmers (seeds)", 38, 40000, 70000, note="Great ironman seed source")
T("THIEVING", "Blackjacking (Pollnivneach)", 45, 90000, 200000, intense=1, req="The Feud")
T("THIEVING", "Knights of Ardougne", 55, 60000, 150000, note="Ardougne diary + Rogue outfit boost loot")
T("THIEVING", "Pickpocketing vyres", 82, 200000, 260000, req="Sins of the Father")
T("THIEVING", "Pickpocketing elves", 85, 200000, 270000, req="Song of the Elves", note="Enhanced crystal teleport seeds")

M("CRAFTING", (7, "Gold rings/necklaces"), (20, "Cut sapphires"), (27, "Cut emeralds"), (34, "Cut rubies"),
  (43, "Cut diamonds"), (46, "Unpowered orbs"), (55, "Cut dragonstones"), (63, "Green d'hide bodies"), (66, "Air battlestaves"), (71, "Blue d'hide bodies"), (77, "Red d'hide bodies"),
  (84, "Black d'hide bodies"), (87, "Light orbs"), (89, "Cut zenytes"), (99, "Skill cape"))
T("CRAFTING", "Leather items (gloves → chaps)", 1, 20000, 30000, each=13.8, unit="leather", mem=0, needs=(("Leather", 1),))
T("CRAFTING", "Gold amulets (u) at a furnace", 8, 35000, 40000, each=30, unit="amulet", mem=0, needs=(("Gold bar", 1),))
for g, lvl, xp in [("sapphire", 20, 50), ("emerald", 27, 67.5), ("ruby", 34, 85), ("diamond", 43, 107.5), ("dragonstone", 55, 137.5)]:
    T("CRAFTING", f"Cut {g}s", lvl, round(xp * 2500), round(xp * 2600), each=xp, unit="gem", mem=0 if g != "dragonstone" else 1,
      needs=((f"Uncut {g}", 1),), gives=((g.capitalize(), 1),))
T("CRAFTING", "Glassblowing unpowered orbs", 46, 80000, 85000, each=52.5, unit="orb", needs=(("Molten glass", 1),),
  gives=(("Unpowered orb", 1),), note="Ironmen: make glass with Superglass Make")
T("CRAFTING", "Glassblowing light orbs", 87, 105000, 110000, each=70, unit="orb", needs=(("Molten glass", 1),))
for c, lvl, xp in [("Green", 63, 186), ("Blue", 71, 210), ("Red", 77, 234), ("Black", 84, 258)]:
    T("CRAFTING", f"{c} d'hide bodies", lvl, round(xp * 1700), round(xp * 1750), each=xp, unit="body",
      needs=((f"{c} dragon leather", 3),), gives=((f"{c} d'hide body", 1),))
T("CRAFTING", "Air battlestaves", 66, 330000, 345000, each=137.5, unit="staff", needs=(("Battlestaff", 1), ("Air orb", 1)),
  gives=(("Air battlestaff", 1),), note="Ironmen: battlestaves from Zaff, orbs from Charge Air Orb")

M("FLETCHING", (10, "Bronze darts"), (20, "Oak shortbows"), (22, "Iron darts"),
  (25, "Oak longbows"), (37, "Steel darts"), (40, "Willow longbows"), (52, "Mithril darts"), (55, "Maple longbows"),
  (67, "Adamant darts"), (70, "Yew longbows"), (81, "Rune darts"), (85, "Magic longbows"), (95, "Dragon darts"), (99, "Skill cape"))
T("FLETCHING", "Arrow shafts", 1, 8000, 10000, each=5, unit="log", mem=0, needs=(("Logs", 1),), gives=(("Arrow shaft", 15),))
T("FLETCHING", "Headless arrows", 1, 25000, 30000, each=15, unit="set of 15", mem=0, needs=(("Arrow shaft", 15), ("Feather", 15)),
  gives=(("Headless arrow", 15),))
for w, lvl, xp in [("Oak", 25, 25), ("Willow", 40, 41.5), ("Maple", 55, 58.3), ("Yew", 70, 75), ("Magic", 85, 91.5)]:
    T("FLETCHING", f"Cut {w.lower()} longbows (u)", lvl, round(xp * 1700), round(xp * 1750), each=xp, unit="bow", mem=0 if w in ("Oak", "Willow", "Maple", "Yew") else 1,
      needs=((f"{w} logs", 1),), gives=((f"{w} longbow (u)", 1),))
    T("FLETCHING", f"String {w.lower()} longbows", lvl, round(xp * 2400), round(xp * 2500), each=xp, unit="bow",
      needs=((f"{w} longbow (u)", 1), ("Bow string", 1)), gives=((f"{w} longbow", 1),))
for m, lvl, xp in [("Steel", 37, 75), ("Mithril", 52, 112), ("Adamant", 67, 150), ("Rune", 81, 188), ("Dragon", 95, 250)]:
    T("FLETCHING", f"{m} darts", lvl, round(xp * 2000), round(xp * 2100), each=xp, unit="10 darts",
      needs=((f"{m} dart tip", 10), ("Feather", 10)), gives=((f"{m} dart", 10),), req="The Tourist Trap",
      note="Very fast; just click. Ironmen smith their own dart tips")

M("SLAYER", (5, "Cave crawlers"), (15, "Banshees (Morytania Legs/earmuffs)"), (55, "Turoth"), (58, "Cave horrors"),
  (62, "Wyrms"), (65, "Dust devils"), (70, "Kurask"), (72, "Skeletal wyverns"), (75, "Gargoyles"), (80, "Nechryael"),
  (84, "Drakes"), (85, "Abyssal demons"), (87, "Kraken"), (91, "Cerberus"), (93, "Smoke devil boss"), (95, "Alchemical Hydra"), (99, "Skill cape"))
T("SLAYER", "Turael / Spria tasks", 1, 3000, 6000, note="Only for the first levels")
T("SLAYER", "Vannaka / Chaeldar tasks", 20, 12000, 30000, note="Needs combat level 40 / 70")
T("SLAYER", "Nieve / Duradel / Konar tasks", 50, 30000, 60000, note="Needs combat level 85+ for Nieve, 100 for Duradel")
T("SLAYER", "Bursting & barraging tasks (Nieve/Duradel)", 70, 60000, 110000, intense=1,
  req="Desert Treasure I, 70/94 Magic", note="Burst dust devils, nechryael, smoke devils...")

M("FARMING", (15, "Oak trees"), (27, "Apple trees"), (30, "Willow trees"), (32, "Ranarr weed"), (34, "Tithe Farm"),
  (45, "Maple trees"), (51, "Pineapple trees"), (55, "Mahogany trees"), (57, "Papaya trees"), (60, "Yew trees"),
  (65, "Farming Guild"), (68, "Palm trees"), (72, "Calquat trees"), (75, "Magic trees"), (81, "Dragonfruit trees"),
  (83, "Spirit trees"), (85, "Celastrus, Torstol (85)"), (90, "Redwood trees"), (99, "Skill cape"))
T("FARMING", "Tithe Farm", 34, 30000, 100000, note="Gets faster at 54 and 74; gives the Farmer's outfit and seed box")

M("FIREMAKING", (15, "Oak logs"), (30, "Willow logs"), (45, "Maple logs"), (50, "Wintertodt"), (60, "Yew logs"),
  (75, "Magic logs"), (90, "Redwood logs"), (99, "Skill cape"))
FM = [("Logs", 1, 40, 0), ("Oak logs", 15, 60, 0), ("Willow logs", 30, 90, 0), ("Teak logs", 35, 105, 1), ("Maple logs", 45, 135, 0),
      ("Yew logs", 60, 202.5, 0), ("Magic logs", 75, 303.8, 1), ("Redwood logs", 90, 350, 1)]
for logs, lvl, xp, mem in FM:
    T("FIREMAKING", f"Burn {logs.lower()}", lvl, round(xp * 1100), round(xp * 1150), each=xp, unit="log", mem=mem,
      needs=((logs, 1),))
T("FIREMAKING", "Wintertodt", 50, 120000, 300000, note="Best ironman method: supply crates with seeds, ores, logs and herbs")

M("WOODCUTTING", (15, "Oak trees"), (30, "Willow trees"), (35, "Teak trees"), (41, "Rune axe"), (45, "Maple trees"),
  (50, "Mahogany trees"), (60, "Yew trees, Woodcutting Guild, Dragon axe (61)"), (65, "Sulliuscep"), (75, "Magic trees"),
  (90, "Redwood trees"), (99, "Skill cape"))
for tree, logs, lvl, xp, lo, hi, mem in [("Normal trees", "Logs", 1, 25, 6000, 12000, 0), ("Oak trees", "Oak logs", 15, 37.5, 15000, 40000, 0),
                                          ("Willow trees", "Willow logs", 30, 67.5, 30000, 60000, 0),
                                          ("Teak trees", "Teak logs", 35, 85, 40000, 90000, 1), ("Maple trees", "Maple logs", 45, 100, 30000, 50000, 0),
                                          ("Mahogany trees", "Mahogany logs", 50, 125, 25000, 45000, 1), ("Yew trees", "Yew logs", 60, 175, 30000, 45000, 0),
                                          ("Magic trees", "Magic logs", 75, 250, 28000, 40000, 1), ("Redwood trees", "Redwood logs", 90, 380, 65000, 75000, 1)]:
    T("WOODCUTTING", tree, lvl, lo, hi, each=xp, unit="log", mem=mem, gives=((logs, 1),),
      note="Powerchop (drop logs) for faster xp" if lvl < 45 else "")

M("MINING", (15, "Iron ore"), (20, "Silver ore"), (30, "Coal, Motherlode Mine"), (40, "Gold ore"),
  (41, "Rune pickaxe"), (45, "Granite"), (50, "Volcanic Mine"), (55, "Mithril ore"), (60, "Mining Guild"),
  (61, "Dragon pickaxe"), (70, "Adamantite ore"), (72, "Motherlode upper level"), (85, "Runite ore"),
  (92, "Amethyst"), (99, "Skill cape"))
for name, ore, lvl, xp, lo, hi, mem, note in [
        ("Copper & tin ore", "Copper ore", 1, 17.5, 8000, 20000, 0, "Mine at Lumbridge Swamp or Varrock"),
        ("Iron ore (powermine)", "Iron ore", 15, 35, 25000, 70000, 0, "Drop the ore for the best xp; Al Kharid or Mining Guild"),
        ("Coal", "Coal", 30, 50, 15000, 40000, 0, ""), ("Gold ore", "Gold ore", 40, 65, 20000, 35000, 0, "Crafting Guild has gold rocks"),
        ("Mithril ore", "Mithril ore", 55, 80, 15000, 30000, 0, ""), ("Adamantite ore", "Adamantite ore", 70, 95, 15000, 25000, 0, ""),
        ("Runite ore", "Runite ore", 85, 125, 8000, 12000, 0, "Few rocks and slow respawns"),
        ("Amethyst", "Amethyst", 92, 240, 20000, 25000, 1, "Mining Guild; AFK")]:
    T("MINING", name, lvl, lo, hi, each=xp, unit="ore", mem=mem, gives=((ore, 1),), note=note)
T("MINING", "Motherlode Mine", 30, 20000, 50000, note="AFK; gives coal, gold, mithril, adamantite, runite and the Prospector kit")
T("MINING", "Shooting stars", 10, 15000, 50000, mem=0, note="AFK, works in F2P; stardust buys useful rewards")
T("MINING", "Granite (desert quarry)", 45, 50000, 100000, note="Bring waterskins; 3-tick for more (intense)")
T("MINING", "Volcanic Mine", 50, 40000, 110000, req="Bone Voyage", note="Team activity on Fossil Island")

M("SMITHING", (15, "Iron bars"), (30, "Steel bars"), (35, "Cannonballs"), (40, "Gold bars (Blast Furnace)"),
  (50, "Mithril bars"), (60, "Blast Furnace without fee"), (68, "Mithril platebodies"), (70, "Adamant bars"),
  (85, "Runite bars"), (88, "Adamant platebodies"), (99, "Rune platebodies, skill cape"))
T("SMITHING", "Bronze items (F2P)", 1, 8000, 12000, mem=0, note="Smelt bronze bars and smith daggers → platebodies")
for metal, lvl, mem in [("Iron", 33, 0), ("Steel", 48, 0), ("Mithril", 68, 0), ("Adamant", 88, 0), ("Rune", 99, 0)]:
    xp = {"Iron": 125, "Steel": 187.5, "Mithril": 250, "Adamant": 312.5, "Rune": 375}[metal]
    T("SMITHING", f"{metal} platebodies", lvl, round(xp * 800), round(xp * 820), each=xp, unit="platebody", mem=mem,
      needs=(((f"{metal} bar" if metal != "Rune" else "Runite bar") if metal != "Adamant" else "Adamantite bar", 5),),
      gives=((f"{metal} platebody", 1),))
T("SMITHING", "Gold bars at Blast Furnace (goldsmith gauntlets)", 40, 280000, 320000, each=56.2, unit="bar",
  needs=(("Gold ore", 1),), gives=(("Gold bar", 1),), req="Family Crest (gauntlets)", note="Pay 72k/hour below 60 Smithing")
T("SMITHING", "Giants' Foundry", 15, 30000, 100000, req="Sleeping Giants", note="Ironman favourite; uses mithril/adamant bars")
T("SMITHING", "Cannonballs (AFK)", 35, 15000, 18000, each=25.6, unit="bar", needs=(("Steel bar", 1),),
  gives=(("Steel cannonball", 4),), req="Dwarf Cannon")

M("FISHING", (20, "Fly fishing trout"), (35, "Tempoross, tuna"), (40, "Lobsters"), (43, "Aerial fishing"),
  (48, "Barbarian fishing"), (50, "Swordfish"), (62, "Monkfish"), (65, "Karambwans"), (68, "Fishing Guild"),
  (76, "Sharks"), (80, "Infernal eels"), (82, "Anglerfish, minnows"), (85, "Dark crabs"), (99, "Skill cape"))
T("FISHING", "Shrimps & anchovies", 1, 5000, 8000, each=10, unit="fish", mem=0, gives=(("Raw shrimps", 1),))
T("FISHING", "Fly fishing trout & salmon (powerfish)", 20, 25000, 50000, each=50, unit="fish", mem=0,
  needs=(("Feather", 1),), gives=(("Raw trout", 1),))
T("FISHING", "Lobsters", 40, 15000, 25000, each=90, unit="fish", mem=0, gives=(("Raw lobster", 1),))
T("FISHING", "Tempoross", 35, 30000, 70000, note="Fishing outfit, fish barrel and tackle box")
T("FISHING", "Aerial fishing", 43, 30000, 45000, req="35 Hunter", note="Also trains Hunter; gives angler outfit")
T("FISHING", "Barbarian fishing", 48, 40000, 80000, req="15 Agility & Strength, Barbarian Training",
  note="Also trains Agility and Strength")
T("FISHING", "Monkfish", 62, 25000, 35000, each=120, unit="fish", gives=(("Raw monkfish", 1),), req="Swan Song")
T("FISHING", "Karambwans", 65, 30000, 50000, each=50, unit="fish", needs=(("Raw karambwanji", 1),),
  gives=(("Raw karambwan", 1),), req="Tai Bwo Wannai Trio")
T("FISHING", "Sharks", 76, 20000, 25000, each=110, unit="fish", gives=(("Raw shark", 1),))
T("FISHING", "Minnows", 82, 40000, 50000, req="Angler outfit")
T("FISHING", "Anglerfish", 82, 15000, 20000, each=120, unit="fish", needs=(("Sandworms", 1),), gives=(("Raw anglerfish", 1),),
  req="Partial Kourend favour")

M("COOKING", (15, "Trout"), (30, "Karambwans"), (35, "Wine"), (40, "Lobsters"), (45, "Swordfish"), (62, "Monkfish"),
  (68, "Never burn wine"), (80, "Sharks"), (84, "Anglerfish"), (90, "Dark crabs"), (99, "Skill cape"))
for raw, ck, lvl, xp, mem in [("Raw shrimps", "Shrimps", 1, 30, 0), ("Raw trout", "Trout", 15, 70, 0), ("Raw salmon", "Salmon", 25, 90, 0),
                               ("Raw tuna", "Tuna", 30, 100, 0), ("Raw lobster", "Lobster", 40, 120, 0), ("Raw swordfish", "Swordfish", 45, 140, 0),
                               ("Raw monkfish", "Monkfish", 62, 150, 1), ("Raw shark", "Shark", 80, 210, 1), ("Raw anglerfish", "Anglerfish", 84, 230, 1)]:
    T("COOKING", f"Cook {ck.lower()}", lvl, round(xp * 1300), round(xp * 1350), each=xp, unit="fish", mem=mem,
      needs=((raw, 1),), gives=((ck, 1),), note="Burns less as you level; use the Hosidius range")
T("COOKING", "Cook karambwans", 30, 190 * 1300, 190 * 1350, each=190, unit="fish", needs=(("Raw karambwan", 1),),
  gives=(("Cooked karambwan", 1),), note="1-tick cooking is ~4x faster but intense")
T("COOKING", "Make jugs of wine", 35, 450000, 480000, each=200, unit="wine", needs=(("Grapes", 1), ("Jug of water", 1)),
  gives=(("Jug of wine", 1),), note="Ironmen: buy grapes at the Culinaromancer's chest or farm them")

M("HUNTER", (9, "Birdhouses (wood)"), (19, "Tropical wagtails"), (29, "Swamp lizards"), (43, "Falconry"),
  (46, "Hunters' Rumours"), (47, "Orange salamanders"), (53, "Chinchompas"), (59, "Red salamanders"),
  (63, "Red chinchompas"), (67, "Black salamanders"), (73, "Black chinchompas"), (80, "Herbiboar"), (99, "Skill cape"))
T("HUNTER", "Bird snaring", 1, 5000, 15000)
T("HUNTER", "Swamp lizards", 29, 25000, 35000, each=152, unit="lizard", gives=(("Swamp lizard", 1),))
T("HUNTER", "Falconry (spotted/dark kebbits)", 43, 50000, 70000)
T("HUNTER", "Hunters' Rumours (Varlamore)", 46, 40000, 110000, req="Children of the Sun", note="Ironman favourite")
T("HUNTER", "Orange salamanders", 47, 45000, 55000, each=224, unit="salamander", gives=(("Orange salamander", 1),))
T("HUNTER", "Red salamanders", 59, 55000, 65000, each=272, unit="salamander", gives=(("Red salamander", 1),))
T("HUNTER", "Red chinchompas", 63, 100000, 150000, each=265, unit="chinchompa", gives=(("Red chinchompa", 1),),
  req="Eagles' Peak", note="Feldip Hills; catch your own Ranged ammo")
T("HUNTER", "Black salamanders", 67, 65000, 75000, each=319.5, unit="salamander", gives=(("Black salamander", 1),))
T("HUNTER", "Black chinchompas (Wilderness)", 73, 120000, 200000, each=315, unit="chinchompa", gives=(("Black chinchompa", 1),),
  note="Dangerous")
T("HUNTER", "Herbiboar", 80, 100000, 130000, req="Bone Voyage", note="Gives herbs - great for ironmen")

M("SAILING", (12, "Prying Times"), (15, "Shipwreck salvaging"), (22, "Current Affairs"), (30, "The Tempor Tantrum"),
  (42, "Salvaging station"), (45, "Troubled Tortugans"), (55, "The Jubbly Jive"), (62, "Rellekka courier route"),
  (70, "Prifddinas route"), (72, "The Gwenith Glide, bluefin trawling"), (76, "Lunar Isle route"),
  (78, "All sea charting, marlin trawling"), (80, "All bounty tasks"), (93, "Rosewood hull"), (99, "Skill cape"))
T("SAILING", "Sea charting + courier tasks", 1, 5000, 12000, req="Pandemonium", note="Do the Sailing quests as you unlock them")
T("SAILING", "The Tempor Tantrum (Barracuda Trials)", 30, 19000, 25000, req="Pandemonium")
T("SAILING", "Shipwreck salvaging (large)", 53, 18000, 20000, note="Low effort with crewmates")
T("SAILING", "The Jubbly Jive (Barracuda Trials)", 55, 65000, 89000)
T("SAILING", "Courier tasks: Rellekka route", 62, 66000, 99000, note="~99k once Etceteria is unlocked (65)")
T("SAILING", "Courier tasks: Prifddinas route", 70, 71000, 77000, req="Song of the Elves")
T("SAILING", "Deep sea trawling (bluefin)", 72, 49000, 50000, req="87 Fishing", note="Also trains Fishing")
T("SAILING", "The Gwenith Glide (Barracuda Trials)", 72, 114000, 198000, note="Fastest; Marlin rank with a crystal extractor")
T("SAILING", "Shipwreck salvaging (mercenary)", 73, 49000, 50000, note="Low effort")
T("SAILING", "Courier tasks: Lunar Isle route", 76, 130000, 160000, req="Lunar Diplomacy")
T("SAILING", "Shipwreck salvaging (merchant)", 87, 60000, 60000, note="Low effort")

# ---------------------------------------------------------------- farm runs from farmruns.txt
for line in open("src/main/resources/com/moneymaker/farmruns.txt"):
    f = line.rstrip("\n").split("|")
    group, name, skill, lvl, mem, grow, run, patches, ins, outs, coins, xp = f[:12]
    if skill != "FARMING" or float(xp) <= 0 or group not in ("Tree runs", "Fruit tree runs", "Hardwood runs", "Special trees", "Herb runs", "Hops"):
        continue
    runs_per_hour = 60.0 / max(float(run), 1)
    rate = round(float(xp) * runs_per_hour)
    needs = [(x.rsplit("*", 1)[0], float(x.rsplit("*", 1)[1])) for x in ins.split(";") if x]
    gives = [(x.rsplit("*", 1)[0], float(x.rsplit("*", 1)[1])) for x in outs.split(";") if x]
    T("FARMING", name, int(lvl), rate, rate, each=float(xp), unit="run", mem=1, cycle=int(float(grow)),
      needs=needs, gives=gives, note=f"XP per hour is while doing the run; one run every {float(grow)/60:g}h")

open("src/main/resources/com/moneymaker/training.txt", "w").write("\n".join(L) + "\n")
print(len(L), "lines")

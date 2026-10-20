# Builds src/main/resources/com/moneymaker/gear.txt: skilling tools, outfits and useful items per skill.
#   python3 tools/gen_gear.py
#
#   T|SKILL|group|level|item       tool tiers, worst first (item names match by prefix, so "Dragon pickaxe (or)" counts)
#   O|SKILL|outfit|piece;piece|what the full set does
#   U|SKILL|item|why it helps
L = []

def tiers(skill, group, items):
    for lvl, item in items:
        L.append(f"T|{skill}|{group}|{lvl}|{item}")

def outfit(skill, name, pieces, bonus):
    L.append(f"O|{skill}|{name}|{';'.join(pieces)}|{bonus}")

def useful(skill, item, why):
    L.append(f"U|{skill}|{item}|{why}")

METALS = [(1, "Bronze"), (1, "Iron"), (6, "Steel"), (11, "Black"), (21, "Mithril"), (31, "Adamant"), (41, "Rune")]
tiers("MINING", "Pickaxe", [(l, f"{m} pickaxe") for l, m in METALS] +
      [(41, "=Gilded pickaxe"), (61, "Dragon pickaxe"), (61, "=3rd age pickaxe"), (61, "=Infernal pickaxe"), (71, "Crystal pickaxe")])
outfit("MINING", "Prospector kit", ["Prospector helmet", "Prospector jacket", "Prospector legs", "Prospector boots"], "+2.5% Mining xp")
useful("MINING", "Coal bag", "Carries 27+ coal")
useful("MINING", "Gem bag", "Stores gems while mining")
useful("MINING", "Varrock armour", "Chance of extra ore")

tiers("WOODCUTTING", "Axe", [(l, f"{m} axe") for l, m in METALS] +
      [(41, "=Gilded axe"), (61, "Dragon axe"), (61, "=3rd age axe"), (61, "=Infernal axe"), (71, "Crystal axe")])
outfit("WOODCUTTING", "Lumberjack outfit", ["Lumberjack hat", "Lumberjack top", "Lumberjack legs", "Lumberjack boots"], "+2.5% Woodcutting xp")
useful("WOODCUTTING", "Log basket", "Holds 28 extra logs")
useful("WOODCUTTING", "Forestry kit", "Stores Forestry rewards")

tiers("FISHING", "Harpoon", [(35, "Harpoon"), (35, "=Barb-tail harpoon"), (61, "Dragon harpoon"), (75, "=Infernal harpoon"), (71, "Crystal harpoon")])
useful("FISHING", "Fly fishing rod", "Trout and salmon (20)")
useful("FISHING", "Barbarian rod", "Barbarian fishing (48)")
outfit("FISHING", "Angler outfit", ["Angler hat", "Angler top", "Angler waders", "Angler boots"], "+2.5% Fishing xp")
useful("FISHING", "Fish barrel", "Holds 28 extra fish")
useful("FISHING", "Tackle box", "Stores fishing tools")
useful("FISHING", "Spirit flakes", "Chance of extra fish at Tempoross")

outfit("FIREMAKING", "Pyromancer outfit", ["Pyromancer hood", "Pyromancer garb", "Pyromancer robe", "Pyromancer boots"], "+2.5% Firemaking xp")
useful("FIREMAKING", "Bruma torch", "Lights fires without a tinderbox")
useful("FIREMAKING", "Tinderbox", "Needed to light fires")
useful("FIREMAKING", "Warm gloves", "Wintertodt cold protection")

outfit("FARMING", "Farmer's outfit", ["Farmer's strawhat", "Farmer's jacket", "Farmer's boro trousers", "Farmer's boots"], "+2.5% Farming xp")
useful("FARMING", "Magic secateurs", "+10% herb and crop yield")
useful("FARMING", "Bottomless compost bucket", "Stores compost")
useful("FARMING", "Seed box", "Stores seeds")
useful("FARMING", "Ultracompost", "Best compost")

outfit("CONSTRUCTION", "Carpenter's outfit", ["Carpenter's helmet", "Carpenter's shirt", "Carpenter's trousers", "Carpenter's boots"], "+2.5% Construction xp")
useful("CONSTRUCTION", "Hammer", "Needed to build")
useful("CONSTRUCTION", "Saw", "Needed to build (or Crystal saw)")
useful("CONSTRUCTION", "Plank sack", "Holds extra planks")

outfit("THIEVING", "Rogue outfit", ["Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"], "Double loot from pickpocketing")
useful("THIEVING", "Dodgy necklace", "Prevents stuns")
useful("THIEVING", "Ardougne cloak", "Higher pickpocket success")

outfit("AGILITY", "Graceful outfit", ["Graceful hood", "Graceful top", "Graceful legs", "Graceful gloves", "Graceful boots", "Graceful cape"], "Faster run energy regeneration, lighter")
useful("AGILITY", "Stamina potion", "Keeps you running")

outfit("RUNECRAFT", "Raiments of the Eye", ["Hat of the eye", "Robe top of the eye", "Robe bottoms of the eye", "Boots of the eye"], "Up to +60% runes crafted")
tiers("RUNECRAFT", "Essence pouch", [(1, "Small pouch"), (25, "Medium pouch"), (50, "Large pouch"), (75, "Giant pouch"), (85, "Colossal pouch")])
useful("RUNECRAFT", "Binding necklace", "100% combination rune success")
useful("RUNECRAFT", "Rune pouch", "Carries your spell runes")

outfit("SMITHING", "Smiths' uniform", ["Smiths tunic", "Smiths trousers", "Smiths boots", "Smiths gloves"], "Faster smithing")
useful("SMITHING", "Goldsmith gauntlets", "2.5x xp for gold bars")
useful("SMITHING", "Ice gloves", "Take bars from the Blast Furnace")
useful("SMITHING", "Hammer", "Needed to smith")
useful("SMITHING", "Coal bag", "Carries extra coal for the Blast Furnace")

outfit("HUNTER", "Guild hunter outfit", ["Guild hunter headwear", "Guild hunter top", "Guild hunter legs", "Guild hunter boots"], "Better Hunter catch rates")
useful("HUNTER", "Box trap", "Chinchompas, ferrets")
useful("HUNTER", "Small fishing net", "Salamanders and lizards")
useful("HUNTER", "Rope", "Salamander traps")
useful("HUNTER", "Bird snare", "Birds")
useful("HUNTER", "Noose wand", "Kebbits, Hunters' Rumours")

useful("PRAYER", "Bonecrusher", "Buries bones for you while killing")
useful("PRAYER", "Ectoplasmator", "Extra Prayer xp from kills")
useful("PRAYER", "Dragonbone necklace", "Restores prayer when burying")
useful("PRAYER", "Dragon bones", "Best common bones")

useful("COOKING", "Cooking gauntlets", "Burn less lobster, swordfish, monkfish, shark, anglerfish")
useful("HERBLORE", "Herb sack", "Holds grimy herbs")
useful("HERBLORE", "Amulet of chemistry", "Chance of a 4-dose potion")
useful("HERBLORE", "Vial of water", "Needed for every potion")
useful("CRAFTING", "Needle", "Leather and d'hide")
useful("CRAFTING", "Chisel", "Cutting gems")
useful("CRAFTING", "Glassblowing pipe", "Glassblowing")
useful("FLETCHING", "Knife", "Cutting logs")
useful("FLETCHING", "Feather", "Arrows and darts")

for sk in ("ATTACK", "STRENGTH"):
    tiers(sk, "Weapon", [(1, "Iron scimitar"), (5, "Steel scimitar"), (20, "Mithril scimitar"), (30, "Adamant scimitar"),
                         (40, "Rune scimitar"), (60, "Dragon scimitar"), (70, "Abyssal whip"), (75, "Abyssal tentacle"),
                         (75, "=Blade of saeldor"), (80, "Ghrazi rapier"), (82, "Osmumten's fang")])
tiers("DEFENCE", "Body", [(1, "Iron platebody"), (5, "Steel platebody"), (20, "Mithril platebody"), (30, "Adamant platebody"),
                          (40, "Rune platebody"), (60, "Dragon platebody"), (65, "Bandos chestplate"), (75, "Justiciar chestguard"),
                          (80, "Torva platebody")])
tiers("DEFENCE", "Defender", [(1, "Bronze defender"), (5, "Iron defender"), (10, "Steel defender"), (20, "Mithril defender"),
                              (30, "Adamant defender"), (40, "Rune defender"), (60, "Dragon defender"), (70, "Avernic defender")])
tiers("RANGED", "Weapon", [(1, "Shortbow"), (5, "Oak shortbow"), (20, "Willow shortbow"), (30, "Maple shortbow"),
                           (40, "Yew shortbow"), (50, "Magic shortbow"), (61, "Rune crossbow"), (70, "Armadyl crossbow"),
                           (75, "Toxic blowpipe"), (75, "=Twisted bow"), (80, "Bow of faerdhinen")])
tiers("MAGIC", "Staff", [(1, "Staff of air"), (1, "=Staff of fire"), (30, "Smoke battlestaff"), (50, "Ancient staff"),
                         (75, "Trident of the seas"), (75, "Trident of the swamp"), (75, "=Kodai wand"), (82, "Sanguinesti staff"),
                         (85, "Tumeken's shadow")])
useful("MAGIC", "Rune pouch", "Carries your spell runes")
useful("MAGIC", "Tome of fire", "Infinite fire runes")
tiers("SLAYER", "Helmet", [(1, "Black mask"), (55, "Slayer helmet")])
useful("SLAYER", "Nose peg", "Aberrant spectres")
useful("SLAYER", "Earmuffs", "Banshees")
useful("SLAYER", "Rock hammer", "Finishes gargoyles")
useful("SLAYER", "Leaf-bladed sword", "Kurask and turoth")
useful("SLAYER", "Mirror shield", "Basilisks and cockatrices")

open("src/main/resources/com/moneymaker/gear.txt", "w").write("\n".join(L) + "\n")
print(len(L), "lines")

# Generates src/main/resources/com/moneymaker/recipes.txt
# Format: category|name|skill|level|members|perHour|coinsPerAction|inputs|outputs
# inputs/outputs: Item name*qty;Item name*qty
L = []
def add(cat, name, skill, lvl, mem, rate, coins, ins, outs, xp=0):
    f = lambda xs: ";".join(f"{n}*{q}" for n, q in xs)
    L.append(f"{cat}|{name}|{skill}|{lvl}|{1 if mem else 0}|{rate}|{coins}|{f(ins)}|{f(outs)}|{xp}")

CLEANXP={"Guam leaf":2.5,"Marrentill":3.8,"Tarromin":5,"Harralander":6.3,"Ranarr weed":7.5,"Toadflax":8,"Irit leaf":8.8,"Avantoe":10,"Kwuarm":11.3,"Snapdragon":11.8,"Cadantine":12.5,"Lantadyme":13.1,"Dwarf weed":13.8,"Torstol":15}
herbs = [  # herb, clean lvl, unf lvl
 ("Guam leaf",3,3),("Marrentill",5,5),("Tarromin",11,12),("Harralander",20,22),("Ranarr weed",25,30),
 ("Toadflax",30,34),("Irit leaf",40,45),("Avantoe",48,50),("Kwuarm",54,55),("Snapdragon",59,63),
 ("Cadantine",65,66),("Lantadyme",67,69),("Dwarf weed",70,72),("Torstol",75,78)]
unf = {"Guam leaf":"Guam potion (unf)","Marrentill":"Marrentill potion (unf)","Tarromin":"Tarromin potion (unf)",
 "Harralander":"Harralander potion (unf)","Ranarr weed":"Ranarr potion (unf)","Toadflax":"Toadflax potion (unf)",
 "Irit leaf":"Irit potion (unf)","Avantoe":"Avantoe potion (unf)","Kwuarm":"Kwuarm potion (unf)",
 "Snapdragon":"Snapdragon potion (unf)","Cadantine":"Cadantine potion (unf)","Lantadyme":"Lantadyme potion (unf)",
 "Dwarf weed":"Dwarf weed potion (unf)","Torstol":"Torstol potion (unf)"}
for h, cl, ul in herbs:
    add("Herblore", f"Clean grimy {h.lower()}", "HERBLORE", cl, True, 5000, 0, [(f"Grimy {h.lower()}",1)], [(h,1)], CLEANXP[h])
    add("Herblore", f"Make {unf[h]}", "HERBLORE", ul, True, 2700, 0, [(h,1),("Vial of water",1)], [(unf[h],1)])
POTXP={"Attack potion(3)":25,"Antipoison(3)":37.5,"Strength potion(3)":50,"Restore potion(3)":62.5,"Energy potion(3)":67.5,"Prayer potion(3)":87.5,"Agility potion(3)":80,"Super attack(3)":100,"Superantipoison(3)":106.3,"Fishing potion(3)":112.5,"Super energy(3)":117.5,"Super strength(3)":125,"Super restore(3)":142.5,"Super defence(3)":150,"Antifire potion(3)":157.5,"Ranging potion(3)":162.5,"Magic potion(3)":172.5,"Zamorak brew(3)":175,"Saradomin brew(3)":180}
pots = [ # unf, secondary, output(3), level
 ("Guam potion (unf)","Eye of newt","Attack potion(3)",3),
 ("Marrentill potion (unf)","Unicorn horn dust","Antipoison(3)",5),
 ("Tarromin potion (unf)","Limpwurt root","Strength potion(3)",12),
 ("Harralander potion (unf)","Red spiders' eggs","Restore potion(3)",22),
 ("Harralander potion (unf)","Chocolate dust","Energy potion(3)",26),
 ("Ranarr potion (unf)","Snape grass","Prayer potion(3)",38),
 ("Toadflax potion (unf)","Toad's legs","Agility potion(3)",34),
 ("Irit potion (unf)","Eye of newt","Super attack(3)",45),
 ("Irit potion (unf)","Unicorn horn dust","Superantipoison(3)",48),
 ("Avantoe potion (unf)","Snape grass","Fishing potion(3)",50),
 ("Avantoe potion (unf)","Mort myre fungus","Super energy(3)",52),
 ("Kwuarm potion (unf)","Limpwurt root","Super strength(3)",55),
 ("Snapdragon potion (unf)","Red spiders' eggs","Super restore(3)",63),
 ("Cadantine potion (unf)","White berries","Super defence(3)",66),
 ("Lantadyme potion (unf)","Dragon scale dust","Antifire potion(3)",69),
 ("Dwarf weed potion (unf)","Wine of Zamorak","Ranging potion(3)",72),
 ("Lantadyme potion (unf)","Potato cactus","Magic potion(3)",76),
 ("Torstol potion (unf)","Jangerberries","Zamorak brew(3)",78),
 ("Toadflax potion (unf)","Crushed nest","Saradomin brew(3)",81),
]
for u, sec, out, lvl in pots:
    add("Herblore", f"Make {out}", "HERBLORE", lvl, True, 2500, 0, [(u,1),(sec,1)], [(out,1)], POTXP[out])

GEMXP={"Opal":15,"Jade":20,"Red topaz":25,"Sapphire":50,"Emerald":67.5,"Ruby":85,"Diamond":107.5,"Dragonstone":137.5,"Onyx":167.5,"Zenyte":200}
gems = [("Opal",1,False),("Jade",13,False),("Red topaz",16,False),("Sapphire",20,False),("Emerald",27,False),
        ("Ruby",34,False),("Diamond",43,False),("Dragonstone",55,True),("Onyx",67,True),("Zenyte",89,True)]
for g, lvl, mem in gems:
    add("Crafting", f"Cut {g.lower()}", "CRAFTING", lvl, mem, 2700, 0, [(f"Uncut {g.lower()}",1)], [(g,1)], GEMXP[g])

SHORTXP={"Oak":16.5,"Willow":33.3,"Maple":50,"Yew":67.5,"Magic":83.3}
LONGXP={"Oak":25,"Willow":41.5,"Maple":58.3,"Yew":75,"Magic":91.5}
bows = [("Oak",20,25),("Willow",35,40),("Maple",50,55),("Yew",65,70),("Magic",80,85)]
for w, sl, ll in bows:
    add("Fletching", f"String {w.lower()} shortbow", "FLETCHING", sl, True, 2500, 0, [(f"{w} shortbow (u)",1),("Bow string",1)], [(f"{w} shortbow",1)], SHORTXP[w])
    add("Fletching", f"String {w.lower()} longbow", "FLETCHING", ll, True, 2500, 0, [(f"{w} longbow (u)",1),("Bow string",1)], [(f"{w} longbow",1)], LONGXP[w])
    add("Fletching", f"Cut {w.lower()} longbow (u)", "FLETCHING", ll, True, 1700, 0, [(f"{w} logs",1)], [(f"{w} longbow (u)",1)], LONGXP[w])

for hide, out, fee, mem in [("Cowhide","Leather",1,False),("Cowhide","Hard leather",3,False),
        ("Green dragonhide","Green dragon leather",20,True),("Blue dragonhide","Blue dragon leather",20,True),
        ("Red dragonhide","Red dragon leather",20,True),("Black dragonhide","Black dragon leather",20,True)]:
    add("Tanning", f"Tan {hide.lower()} into {out.lower()}", "NONE", 1, mem, 2500, fee, [(hide,1)], [(out,1)])

for logs, plank, fee in [("Logs","Plank",100),("Oak logs","Oak plank",250),("Teak logs","Teak plank",500),("Mahogany logs","Mahogany plank",1500)]:
    add("Planks", f"Sawmill {logs.lower()}", "NONE", 1, True, 4000, fee, [(logs,1)], [(plank,1)])

decant = ["Attack potion","Strength potion","Restore potion","Energy potion","Prayer potion","Super attack",
 "Super strength","Super defence","Super energy","Super restore","Ranging potion","Magic potion","Antifire potion",
 "Extended antifire","Saradomin brew","Super combat potion","Stamina potion","Divine super combat potion",
 "Anti-venom+","Superantipoison"]
for p in decant:
    add("Decanting", f"Decant {p} (3) to (4)", "NONE", 1, True, 20000, 0, [(f"{p}(3)",4)], [(f"{p}(4)",3)])
    add("Decanting", f"Decant {p} (4) to (3)", "NONE", 1, True, 20000, 0, [(f"{p}(4)",3)], [(f"{p}(3)",4)])
    add("Decanting", f"Decant {p} (2) to (4)", "NONE", 1, True, 20000, 0, [(f"{p}(2)",2)], [(f"{p}(4)",1)])

sets = {
 "Rune armour set (lg)": ["Rune full helm","Rune platebody","Rune platelegs","Rune kiteshield"],
 "Rune armour set (sk)": ["Rune full helm","Rune platebody","Rune plateskirt","Rune kiteshield"],
 "Adamant set (lg)": ["Adamant full helm","Adamant platebody","Adamant platelegs","Adamant kiteshield"],
 "Dragon armour set (lg)": ["Dragon full helm","Dragon platebody","Dragon platelegs","Dragon kiteshield"],
 "Dharok's armour set": ["Dharok's helm","Dharok's greataxe","Dharok's platebody","Dharok's platelegs"],
 "Guthan's armour set": ["Guthan's helm","Guthan's warspear","Guthan's platebody","Guthan's chainskirt"],
 "Karil's armour set": ["Karil's coif","Karil's crossbow","Karil's leathertop","Karil's leatherskirt"],
 "Ahrim's armour set": ["Ahrim's hood","Ahrim's staff","Ahrim's robetop","Ahrim's robeskirt"],
 "Torag's armour set": ["Torag's helm","Torag's hammers","Torag's platebody","Torag's platelegs"],
 "Verac's armour set": ["Verac's helm","Verac's flail","Verac's brassard","Verac's plateskirt"],
 "Justiciar armour set": ["Justiciar faceguard","Justiciar chestguard","Justiciar legguards"],
 "Ancestral robes set": ["Ancestral hat","Ancestral robe top","Ancestral robe bottom"],
 "Inquisitor's armour set": ["Inquisitor's great helm","Inquisitor's hauberk","Inquisitor's plateskirt"],
 "Obsidian armour set": ["Obsidian helmet","Obsidian platebody","Obsidian platelegs"],
 "Dagon'hai robes set": ["Dagon'hai hat","Dagon'hai robe top","Dagon'hai robe bottom"],
 "Masori armour set (f)": ["Masori mask (f)","Masori body (f)","Masori chaps (f)"],
 "Torva armour set": ["Torva full helm","Torva platebody","Torva platelegs"],
 "Virtus armour set": ["Virtus mask","Virtus robe top","Virtus robe bottom"],
}
for s, parts in sets.items():
    add("Item sets", f"Combine {s}", "NONE", 1, True, 300, 0, [(p,1) for p in parts], [(s,1)])
    add("Item sets", f"Split {s}", "NONE", 1, True, 300, 0, [(s,1)], [(p,1) for p in parts])

# --- Prayer, Construction and Firemaking training (these cost money; useful for the next-level planner) ---
BONES = [("Bones",4.5,1,False),("Big bones",15,1,False),("Babydragon bones",30,1,True),("Dragon bones",72,1,False),
         ("Wyvern bones",72,1,True),("Lava dragon bones",85,1,True),("Dagannoth bones",125,1,True),("Superior dragon bones",150,70,True)]
for b, xp, lvl, mem in BONES:
    add("Prayer", f"Bury {b.lower()}", "PRAYER", lvl, mem, 1500, 0, [(b,1)], [], xp)
    add("Prayer", f"Offer {b.lower()} at a gilded altar", "PRAYER", lvl, True, 1200, 0, [(b,1)], [], round(xp*3.5,2))
for name, lvl, planks, n, xp, rate in [("Crude wooden chair",1,"Plank",2,58,600),("Oak chair",19,"Oak plank",2,120,600),
        ("Oak larder",33,"Oak plank",8,480,450),("Mahogany table",52,"Mahogany plank",6,840,400),
        ("Teak garden bench",66,"Teak plank",6,540,450),("Oak dungeon door",74,"Oak plank",10,600,450)]:
    ins = [(planks,n)] + ([("Steel nails",2)] if planks == "Plank" else [])
    add("Construction", f"Build {name.lower()}s", "CONSTRUCTION", lvl, True, rate, 0, ins, [], xp)
for logs, lvl, xp, mem in [("Logs",1,40,False),("Oak logs",15,60,False),("Willow logs",30,90,False),("Teak logs",35,105,True),
        ("Maple logs",45,135,False),("Mahogany logs",50,157.5,True),("Yew logs",60,202.5,False),("Magic logs",75,303.8,True),("Redwood logs",90,350,True)]:
    add("Firemaking", f"Burn {logs.lower()}", "FIREMAKING", lvl, mem, 1200, 0, [(logs,1)], [], xp)

# --- Every OSRS Wiki money making guide (tools/gen_wiki_jobs.py), minus ones that repeat a job above ---
def key(line):
    f = line.split("|")
    names = lambda t: frozenset(x.split("*")[0].lower() for x in t.split(";") if x)
    return (names(f[7]), names(f[8]))
have = {key(l) for l in L}
wiki = [l for l in open("tools/wiki_jobs.txt").read().splitlines() if l.strip()]
added = [l for l in wiki if key(l) not in have]
print(len(wiki) - len(added), "wiki jobs skipped as duplicates")
L += added

open("src/main/resources/com/moneymaker/recipes.txt","w").write("\n".join(L)+"\n")
names = sorted({x.split("*")[0] for line in L for part in (line.split("|")[7], line.split("|")[8]) for x in part.split(";")})
open("/tmp/claude-0/-home-claude/d4a54bcb-b291-5f32-b051-ae51c3389378/scratchpad/names.json","w").write(__import__("json").dumps(names))
print(len(L), "recipes,", len(names), "distinct items")

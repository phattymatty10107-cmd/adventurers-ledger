# Turns the OSRS Wiki money making guides into recipes.txt lines.
#
# Input: a JSON snapshot of every guide's rendered table (made in a browser on the wiki,
# see README) plus the price API mapping. Usage:
#   python3 tools/gen_wiki_jobs.py <wiki_mmg.json> <prices.json>  > tools/wiki_jobs.txt
#
# Each guide becomes one job. Items that trade on the GE are kept as items so the plugin
# prices them live; anything else (untradeable loot, spell costs, values the wiki works out
# by hand) is folded into a coin amount per action using the wiki's own valuation.
import json, re, sys
from urllib.parse import quote

SKILLS = {"Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
          "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving",
          "Slayer", "Farming", "Runecraft", "Hunter", "Construction", "Sailing"}

def num(v):
    s = ("%.6g" % v)
    if "e" in s:
        s = ("%.10f" % v).rstrip("0").rstrip(".")
    return s

def clean(s):
    return s.replace("|", "/").replace("*", "x").replace(";", ",").strip()

def unit_from(kph_name, perkill):
    if not perkill:
        return "min"
    words = re.sub(r"(?i)\s*per hour.*$", "", kph_name).strip().split()
    if not words:
        return "kill"
    w = words[-1].lower()
    if w.endswith("ies"):
        w = w[:-3] + "y"
    elif w.endswith("es") and w[:-2].endswith(("sh", "ch", "x")):
        w = w[:-2]
    elif w.endswith("s") and not w.endswith("ss"):
        w = w[:-1]
    return clean(w) or "kill"

def reqs_from(lines):
    """Hard skill requirements. Lines that say recommended/optional/ironmen, or that only unlock
    an extra (e.g. "50 Agility for Rogue equipment"), are skipped. Slayer levels always count."""
    out = {}
    split = []
    for line in lines:
        # lines separated by <br> arrive glued together: "...)§Skill:..." or "§§Skill:..."
        split += [x for x in re.split(r"(?<=[§)])(?=§[A-Z])", line) if x.strip()]
    for line in split:
        outside = re.sub(r"\([^)]*\)", "", line)
        lowered = " " + outside.lower() + " "
        soft = any(w in lowered for w in ("recommend", "optional", "ironmen", "suggest", " for ", " to ", " if "))
        hard = any(w in lowered for w in ("required", "access", " enter", "unlock")) and "not required" not in lowered
        if hard and not any(w in lowered for w in ("recommend", "optional", "ironmen", "suggest")):
            soft = False
        for m in re.finditer(r"§([A-Za-z]+):([0-9,]*)\+?§", outside):
            skill, lvl = m.group(1), m.group(2).replace(",", "")
            if skill not in SKILLS or not lvl.isdigit() or not 1 <= int(lvl) <= 99:
                continue
            if soft and skill != "Slayer":
                continue
            out[skill] = max(out.get(skill, 0), int(lvl))
    return out

def main():
    mmg = json.load(open(sys.argv[1]))
    prices = json.load(open(sys.argv[2]))
    tradeable = {row[1].lower(): row[1] for row in prices["map"]}
    latest = prices["latest"]
    id_of = {row[1].lower(): str(row[0]) for row in prices["map"]}

    lines, names = [], set()
    for title, o in sorted(mmg.items()):
        perkill = o["perkill"] and o["kph"] > 0
        kph = o["kph"] if perkill else 60.0  # non per-kill guides: one action = one minute
        every = ""
        if o.get("recurring"):
            # timed activities (farm runs, daily shops): spread one run over how often you can do it,
            # so gp/hr is per real hour rather than per minute spent doing it
            m = re.match(r"\s*([0-9.]+)\s*(minute|hour|day)", o["recurring"])
            if m:
                mins = float(m.group(1)) * {"minute": 1, "hour": 60, "day": 1440}[m.group(2)]
                kph = 60.0 / max(mins, 60.0 / kph)
                every = " (every " + (f"{mins:g} min" if mins < 120 else f"{mins/60:g}h" if mins < 2880 else f"{mins/1440:g} days") + ")"
        cat_raw = o.get("category") or ""
        top = cat_raw.split("/")[0].split("(")[0].strip()
        sub = [x.split("(")[0].strip() for x in (o.get("skillCategory") or "").split(",") if x.strip()]
        if top == "Ironman":
            continue
        if top in SKILLS and not sub:
            sub = [top]
        if top == "Combat":
            category = "Combat"
        elif sub and sub[0] in SKILLS:
            category = sub[0]
        elif top in ("Processing", "Collecting", "Minigame", "Recurring"):
            category = top
        else:
            category = "Skilling" if top == "Skilling" else (top or "Other")

        # xp per action, by skill
        xp = {}
        for x in o["xp"]:
            s = x.get("skill")
            if not s:
                continue
            if s == "Combat level":
                s = "Combat"
            if x.get("pk") not in (None, ""):
                v = float(x["pk"]) if perkill else float(x["pk"]) / kph
            elif x.get("ph") not in (None, ""):
                v = float(x["ph"]) / kph
            else:
                continue
            xp[s] = xp.get(s, 0) + v

        reqs = reqs_from(o["skills"])
        # main skill (used for the next-level planner)
        main = None
        melee = [xp[k] for k in ("Attack", "Strength", "Defence") if xp.get(k)]
        if len(melee) > 1:
            # "Attack/Strength/Defence" listed as alternatives: whichever style you train
            xp["Combat"] = max(melee)
            for k in ("Attack", "Strength", "Defence"):
                xp.pop(k, None)
        real = {k: v for k, v in xp.items() if k in SKILLS}
        if sub and sub[0] in SKILLS:
            main = sub[0]
        elif top == "Combat":
            if "Slayer" in reqs and xp.get("Slayer"):
                main = "Slayer"
            elif xp.get("Combat"):
                # "Combat" xp: work out the style from the skills the guide lists
                text = " ".join(o["skills"])
                if any("§" + k + ":" in text for k in ("Attack", "Strength", "Defence")):
                    main = "Melee"  # the plugin trains whichever of Attack/Strength/Defence is lowest
                elif "§Magic:" in text:
                    main, xp["Magic"] = "Magic", xp["Combat"]
                elif "§Ranged:" in text:
                    main, xp["Ranged"] = "Ranged", xp["Combat"]
                else:
                    main = "Melee"
            else:
                styles = {k: v for k, v in real.items() if k != "Hitpoints"}
                main = max(styles, key=styles.get) if styles else ("Hitpoints" if "Hitpoints" in real else None)
        elif real:
            main = max(real, key=real.get)
        elif reqs:
            main = max(reqs, key=reqs.get)
        if main == "Slayer" or (top == "Combat" and "Slayer" in reqs):
            category = "Slayer"
        level = reqs.get(main, 1) if main else 1
        main_xp = xp.get("Combat" if main == "Melee" else main, 0.0) if main else 0.0

        value_names = {v.lower() for v in o.get("valueNames", [])}
        coins = 0.0
        ins, outs = {}, {}
        for it in o["items"]:
            name = it["name"]
            qty = it["qty"] if it["pk"] else it["qty"] / kph
            cost = it["cost"] if it["costpk"] else it["cost"] / kph
            key = name.lower()
            ge = tradeable.get(key)
            priced = ge is not None and key not in value_names and id_of.get(key) in latest
            if priced and qty > 0:
                # The wiki sometimes shows one item but values it as something else (charges, extra runes,
                # "worth X of item Y"). If its value is far from the GE price, keep the wiki's value as coins.
                hi, lo = latest[id_of[key]]
                ge_each = ((hi or lo) + (lo or hi)) / 2
                expect = ge_each * qty
                wiki_val = abs(cost)
                if expect > 0 and abs(wiki_val - expect) > 0.35 * max(wiki_val, expect) and abs(wiki_val - expect) > 50:
                    priced = False
            if priced and qty > 0:
                bucket = ins if it["io"] == "in" else outs
                bucket[ge] = bucket.get(ge, 0) + qty
            else:
                coins += cost if it["io"] == "in" else -cost
        if not ins and not outs and abs(coins) < 1e-9:
            continue

        name = (clean(o["activity"]) or clean(title.split("/", 1)[1])) + every
        if name in names:
            name = clean(title.split("/", 1)[1])
        names.add(name)
        members = 0 if o.get("members", "").lower().startswith("no") else 1
        skill = main.upper() if main else "NONE"
        req_txt = ";".join(f"{k.upper()}:{v}" for k, v in sorted(reqs.items()))
        f = lambda d: ";".join(f"{clean(k)}*{num(v)}" for k, v in d.items())
        url = "https://oldschool.runescape.wiki/w/" + quote(title.replace(" ", "_"), safe="/()',_-")
        lines.append("|".join([category, name, skill, str(level), str(members), num(kph), num(coins),
                               f(ins), f(outs), num(main_xp), req_txt, unit_from(o.get("kphName", ""), perkill), url]))
    print("\n".join(lines))

main()

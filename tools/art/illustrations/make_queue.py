"""Builds the illustration queue (tools/art/illustrations/queue.tsv) from the game's content: one row per image still to
make, with the subject written from the content (tier names, district names, event text...). Luxury items never use
their parody names in prompts (names pull generators toward real brands); each gets an invented design with a varied
style instead. Rows already in generated.tsv are skipped.

Usage: python -I tools/art/illustrations/make_queue.py <repo-root>
"""
import csv
import json
import sys
from pathlib import Path

CAR = ["sleek two-door coupe", "open-top roadster", "long-bonnet grand tourer", "low wedge-shaped hypercar", "elegant four-door saloon",
       "shooting-brake estate", "compact sports car", "luxury off-road wagon", "curvy 1950s-style racer", "boxy 1980s-style supercar"]
WATCH = ["slim gold dress watch", "steel dive watch with a rotating bezel", "chronograph with three sub-dials", "skeleton watch showing its gears",
         "rose-gold moon-phase watch", "square art-deco watch", "pilot's watch with a large crown"]
WINE = ["a single old bottle on a straw bed", "a wooden case of bottles", "a magnum bottle beside a crystal glass", "bottles resting in a cellar rack",
        "a decanter and a bottle on a barrel top"]
YACHT = ["sleek motor superyacht", "classic wooden sailing yacht", "explorer yacht with a helipad", "catamaran with white sails", "long silver megayacht"]
MANSION = ["Palladian country house", "modernist glass villa on a cliff", "Mediterranean palazzo with terraces", "Tudor manor with a long lawn",
           "Hollywood-hills mansion with a pool", "chalet in snowy mountains", "colonial plantation house with a veranda"]
JET = ["slim business jet", "long-range private jet", "small light jet on a grass airfield", "jet with a sweeping tail at dusk"]
JEWEL = ["diamond tiara", "emerald necklace", "ruby ring", "sapphire brooch", "pearl choker", "gold bracelet set with stones"]
ISLAND = ["palm-ringed atoll", "rocky Mediterranean islet with a villa", "jungle island with a waterfall", "sandbar island with a single house"]
HELI = ["sleek private helicopter", "twin-engine executive helicopter", "small two-seat helicopter", "helicopter over a mountain lake"]
STYLE = {"car": CAR, "watch": WATCH, "wine": WINE, "yacht": YACHT, "mansion": MANSION, "jet": JET, "jewelry": JEWEL, "island": ISLAND, "helicopter": HELI}
RARITY = {"common": "", "rare": "a rare, finely made ", "epic": "a remarkable ", "legendary": "a legendary, one-of-a-kind "}


def main(repo: Path) -> None:
    done = {(r[0], r[1]) for r in csv.reader((repo / "tools/art/illustrations/generated.tsv").open(encoding="utf-8"), delimiter="\t")}
    rows: list[tuple[str, str, str, str]] = []  # set, id, subject, aspect

    raw = json.loads((repo / "content/data/businesses.json").read_text(encoding="utf-8"))
    biz = raw["businesses"] if isinstance(raw, dict) else raw
    for b in biz:
        for i, t in enumerate(b["tiers"], start=1):
            scale = ["small and new", "busy and growing", "well established", "large and impressive", "very grand", "vast and famous", "monumental, the biggest of its kind"][min(i - 1, 6)]
            rows.append(("tier", f"{b['id']}_{i}", f"a {b['name'].lower()} business grown into a '{t['name']}': {scale}, clearly bigger than a humble start", "wide"))
        for x in b["expansions"]:
            rows.append(("expansion", f"{b['id']}_{x['id']}", f"{x['name']} for a {b['name'].lower()} business: {x.get('description', '')}", "wide"))

    est = json.loads((repo / "content/data/estates.json").read_text(encoding="utf-8"))
    cities = {c["id"]: c["name"] for c in est["cities"]}
    for d in est["districts"]:
        feel = "upscale" if d.get("tier", 1) >= 1.4 else "lively mid-market" if d.get("tier", 1) >= 1.0 else "up-and-coming"
        cbd = ", the business district with office towers" if d.get("cbd") else ""
        rows.append(("district", d["id"], f"a street view of the {d['name']} neighbourhood in {cities[d['city']]}, {feel}{cbd}, characteristic local architecture", "wide"))
    for t in est.get("towers", []):
        rows.append(("tower", t["id"], f"a striking invented skyscraper called '{t['name']}' rising over a waterfront city, {t.get('description', '')}", "wide"))

    bal = json.loads((repo / "content/balance/balance.json").read_text(encoding="utf-8"))
    for h in bal["hqLevels"]:
        rows.append(("hq", str(h["level"]), f"a company headquarters at the '{h['name']}' stage, showing how grand the office has become", "wide"))

    world = json.loads((repo / "content/data/world.json").read_text(encoding="utf-8"))
    for ev in world["events"]:
        rows.append(("event", ev["id"], f"a news illustration of: {ev['title']}. {ev['text']}", "wide"))

    lux = json.loads((repo / "content/data/luxury.json").read_text(encoding="utf-8"))
    counts: dict[str, int] = {}
    for it in lux["items"]:
        if it.get("image"):
            continue
        cat = it["category"]
        n = counts.get(cat, 0)
        counts[cat] = n + 1
        styles = STYLE.get(cat, [cat])
        era = "a classic vintage " if it.get("classic") else ""
        rows.append(("luxury", it["id"], f"{RARITY.get(it['rarity'], '')}{era}{styles[n % len(styles)]}, an invented design shown as a showroom piece", "square"))

    out = [r for r in rows if (r[0], r[1]) not in done]
    with (repo / "tools/art/illustrations/queue.tsv").open("w", encoding="utf-8", newline="\n") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        for r in out:
            w.writerow(r)
    by = {}
    for r in out:
        by[r[0]] = by.get(r[0], 0) + 1
    print(f"queue: {len(out)} images", by)


if __name__ == "__main__":
    main(Path(sys.argv[1]))

"""Third illustration wave (owner request 2026-11-09: "vast number of AI images for everything like stocks").

Builds tools/art/illustrations/queue3.tsv (set, id, subject, aspect) for every stock, sector, fund, bond, coin,
commodity, store product, Desk decision card and tenant event, then splits it into batches/w3_batchN.tsv.
Company and coin names are parodies of real brands, so prompts never use names: they describe the business, its
sector, its character and a scene, which keeps generators away from real logos and products.

Usage: python -I tools/art/illustrations/make_queue3.py <repo-root> [batches]
"""
import csv
import json
import re
import sys
from pathlib import Path

SECTOR = {
    "consumer": "a shops-and-brands company", "food": "a food and drink company", "industrials": "an engineering and machinery company",
    "energy": "an energy company", "materials": "a mining and materials company", "financials": "a bank and finance company",
    "technology": "a technology company", "media": "a media and entertainment company", "healthcare": "a medicine and healthcare company",
    "property": "a property and construction company", "transport": "a transport and travel company", "frontier": "a frontier-science company",
}
SCENES = [
    "its headquarters building seen from the street", "its busiest workplace in full swing", "its flagship product being made",
    "its people at work, seen from a distance", "its offices with a sweeping city view", "its warehouse and loading bays at dawn",
    "its newest site under construction", "its showroom with customers browsing",
]
BAND = {"defensive": "steady and long-established", "core": "solid and well known", "growth": "fast-growing and ambitious", "speculative": "young, daring and unproven"}
COIN_SCENE = ["a futuristic digital mint glowing at night", "a vast hall of humming server racks", "a crowd watching a giant price screen (no numbers)",
              "a lone miner's rig in a mountain cabin", "a sleek vault with glowing lattice patterns", "a busy street-market of digital traders"]


def clean(t: str) -> str:
    t = re.sub(r"\{[a-z_]+\}", "a business", t)
    return re.sub(r"\s+", " ", t).strip().rstrip(".")


def main(repo: Path, nbatches: int) -> None:
    rows: list[tuple[str, str, str, str]] = []
    m = json.loads((repo / "content/data/markets.json").read_text(encoding="utf-8"))
    for s in m["sectors"]:
        rows.append(("sector", s["id"], f"a panorama of the {s['name'].lower()} industry: {s['description'].rstrip('.').lower()}", "wide"))
    for i, st in enumerate(m["stocks"]):
        rows.append(("stock", st["id"], f"{SECTOR.get(st['sector'], 'a company')} that is {BAND.get(st['band'], 'well known')} and known as a {st['trait'].lower()}: {SCENES[i % len(SCENES)]}", "wide"))
    for f in m["funds"]:
        rows.append(("fund", f["id"], f"an index fund that owns a slice of many companies: {clean(f['description'])}, shown as a calm panorama of many industries side by side", "wide"))
    for b in m["bonds"]:
        kind = {"government": "a government treasury building with columns", "corporate": "a corporate tower lending to the market", "junk": "a risky, run-down industrial company borrowing money"}.get(b["kind"], "a lender's office")
        rows.append(("bond", b["id"], f"a {b['kind']} bond: {kind}, steady and paying interest over time", "wide"))
    for i, c in enumerate(m["coins"]):
        rows.append(("coin", c["id"], f"an invented cryptocurrency ({clean(c.get('risk', 'a digital coin'))}): {COIN_SCENE[i % len(COIN_SCENE)]}, no symbols or letters on any coin", "wide"))
    for c in m["commodities"]:
        rows.append(("commodity", c["id"], f"{c['name'].lower()} as a traded commodity: where it comes from and how it is shipped, sold by the {c['unit']}", "wide"))
    st = json.loads((repo / "content/data/store.json").read_text(encoding="utf-8"))
    for p in st["products"]:
        rows.append(("product", p["id"], f"a store item for a business game called '{p['name']}': {clean(p['description'])}, shown as a luxurious object or scene, no prices", "square"))
    w = json.loads((repo / "content/data/world.json").read_text(encoding="utf-8"))
    for c in w["cards"]:
        rows.append(("card", c["id"], f"an editorial illustration of a business decision, '{c['title']}': {clean(c['text'])}", "wide"))
    e = json.loads((repo / "content/data/estates.json").read_text(encoding="utf-8"))
    for ev in e["events"]:
        rows.append(("tenant", ev["id"], f"a landlord's moment: {clean(ev['text']).replace('a business', 'a property')}", "wide"))

    base = repo / "tools/art/illustrations"
    done = set()
    for f in [base / "generated.tsv", *sorted((base / "results").glob("*.tsv"))]:
        done |= {(r[0], r[1]) for r in csv.reader(f.open(encoding="utf-8"), delimiter="\t") if len(r) > 1}
    rows = [r for r in rows if (r[0], r[1]) not in done]
    with (base / "queue3.tsv").open("w", encoding="utf-8", newline="\n") as f:
        csv.writer(f, delimiter="\t", lineterminator="\n").writerows(rows)
    size = -(-len(rows) // nbatches)
    for i in range(nbatches):
        with (base / "batches" / f"w3_batch{i + 1}.tsv").open("w", encoding="utf-8", newline="\n") as f:
            csv.writer(f, delimiter="\t", lineterminator="\n").writerows(rows[i * size:(i + 1) * size])
    by: dict[str, int] = {}
    for r in rows:
        by[r[0]] = by.get(r[0], 0) + 1
    print(f"queue3: {len(rows)} images in {nbatches} batches of up to {size}", by)


if __name__ == "__main__":
    main(Path(sys.argv[1]), int(sys.argv[2]) if len(sys.argv) > 2 else 8)

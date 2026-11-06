"""Generates content/data/store.json: the demo store catalogue (docs/GDD.md §15, docs/ARCHITECTURE.md §8).

Every purchase is simulated: "demo" prices are labels only and no money is ever charged or requested. Products can
also be bought with Bullion, which is earned free from achievements, missions, the daily ladder and seasons.
Banned by design: paid random rewards, countdown timers on offers, "only N left" pressure and interrupting offers.

Usage: python -I tools/content/gen_store.py <repo-root>
"""
import json
import sys
from pathlib import Path

# id, name, description, section, demo price label (None = Bullion only), Bullion price (None = demo only), grants, limit
P = [
    ("bullion_100", "Pouch of Bullion", "100 Bullion.", "bullion", "$0.99", None, {"bullion": 100}, 0),
    ("bullion_550", "Bag of Bullion", "550 Bullion (500 + 10% extra).", "bullion", "$4.99", None, {"bullion": 550}, 0),
    ("bullion_1200", "Chest of Bullion", "1,200 Bullion (1,000 + 20% extra).", "bullion", "$9.99", None, {"bullion": 1200}, 0),
    ("bullion_2600", "Vault of Bullion", "2,600 Bullion (2,000 + 30% extra).", "bullion", "$19.99", None, {"bullion": 2600}, 0),
    ("starter_pack", "Starter pack", "300 Bullion, 50 Favor and a 2-hour ×2 income boost. Once per account.", "bullion", "$2.99", None,
     {"bullion": 300, "favor": 50, "boostHours": 2}, 1),
    ("boost_2h", "Double income · 2 h", "Every business earns ×2 for 2 hours. Stacks by extending the time.", "boosts", "$0.99", 60, {"boostHours": 2}, 0),
    ("boost_8h", "Double income · 8 h", "Every business earns ×2 for 8 hours.", "boosts", None, 180, {"boostHours": 8}, 0),
    ("boost_24h", "Double income · 24 h", "Every business earns ×2 for a full day.", "boosts", None, 450, {"boostHours": 24}, 0),
    ("favor_100", "Favor bundle", "100 Favor to rush timers.", "boosts", None, 100, {"favor": 100}, 0),
    ("favor_500", "Favor crate", "500 Favor to rush timers.", "boosts", "$2.99", 450, {"favor": 500}, 0),
    ("permanent_10", "Investor's edge", "+10% income from every business, permanently (up to 3).", "upgrades", "$4.99", 1500, {"permanentPct": 0.10}, 3),
    ("slot_1", "Extra business slot", "One more business slot, permanently. Every slot can also be earned in play (up to 3 here).", "upgrades", None, 800, {"slots": 1}, 3),
    ("offline_4h", "Night shift", "+4 hours of offline earnings, permanently (up to 2).", "upgrades", None, 900, {"offlineHours": 4}, 2),
    ("vip_30", "VIP · 30 days", "For 30 game days: +10% income, +2 h offline earnings and 20 extra Bullion with every daily reward.", "passes", "$9.99", 2500, {"vipDays": 30}, 0),
    ("season_pass", "Season Pass", "Unlocks the premium track of the current season.", "passes", "$4.99", 1000, {"seasonPass": 1}, 0),
    ("title_magnate", "Title: The Magnate", "A title for your profile.", "cosmetics", None, 200, {"cosmetic": "title_magnate"}, 1),
    ("title_old_money", "Title: Old Money", "A title for your profile.", "cosmetics", None, 200, {"cosmetic": "title_old_money"}, 1),
    ("title_disruptor", "Title: The Disruptor", "A title for your profile.", "cosmetics", None, 200, {"cosmetic": "title_disruptor"}, 1),
    ("title_patron", "Title: Patron of the Arts", "A title for your profile.", "cosmetics", None, 300, {"cosmetic": "title_patron"}, 1),
    ("title_tycoon", "Title: Tycoon", "A title for your profile.", "cosmetics", None, 400, {"cosmetic": "title_tycoon"}, 1),
    ("frame_gold", "Gold frame", "A gold frame for your profile picture.", "cosmetics", None, 250, {"cosmetic": "frame_gold"}, 1),
    ("frame_emerald", "Emerald frame", "An emerald frame for your profile picture.", "cosmetics", None, 350, {"cosmetic": "frame_emerald"}, 1),
    ("frame_sapphire", "Sapphire frame", "A sapphire frame for your profile picture.", "cosmetics", None, 350, {"cosmetic": "frame_sapphire"}, 1),
    ("frame_obsidian", "Obsidian frame", "An obsidian frame for your profile picture.", "cosmetics", None, 500, {"cosmetic": "frame_obsidian"}, 1),
]

COSMETICS = {
    "title_magnate": ("title", "The Magnate"), "title_old_money": ("title", "Old Money"), "title_disruptor": ("title", "The Disruptor"),
    "title_patron": ("title", "Patron of the Arts"), "title_tycoon": ("title", "Tycoon"),
    "frame_gold": ("frame", "Gold"), "frame_emerald": ("frame", "Emerald"), "frame_sapphire": ("frame", "Sapphire"), "frame_obsidian": ("frame", "Obsidian"),
}

SECTIONS = [("bullion", "Bullion"), ("boosts", "Boosts"), ("upgrades", "Upgrades"), ("passes", "VIP and Season Pass"), ("cosmetics", "Cosmetics")]


def main(repo: Path) -> None:
    products = []
    for pid, name, desc, section, demo, bullion, grants, limit in P:
        products.append({"id": pid, "name": name, "description": desc, "section": section, "demoPrice": demo, "bullionPrice": bullion,
                         "grants": grants, "limit": limit})
    out = {
        "sections": [{"id": i, "name": n} for i, n in SECTIONS],
        "products": products,
        "cosmetics": [{"id": k, "kind": v[0], "name": v[1]} for k, v in COSMETICS.items()],
        "config": {"maxPermanentPct": 0.30, "maxSlots": 3, "maxOfflineHours": 8, "vipIncomePct": 0.10, "vipOfflineHours": 2, "vipLadderBullion": 20},
    }
    path = repo / "content/data/store.json"
    path.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {path}: {len(products)} products, {len(COSMETICS)} cosmetics")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

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
    ("starter_pack", "Starter pack", "300 Bullion, 50 Favor and a 2-hour income boost. Once per account.", "bullion", "$2.99", None,
     {"bullion": 300, "favor": 50, "boostHours": 2}, 1),
    ("boost_2h", "Income boost · 2 h", "Every business earns 25% more for 2 hours. Buying again extends the time.", "boosts", "$0.99", 60, {"boostHours": 2}, 0),
    ("boost_8h", "Income boost · 8 h", "Every business earns 25% more for 8 hours.", "boosts", None, 180, {"boostHours": 8}, 0),
    ("boost_24h", "Income boost · 24 h", "Every business earns 25% more for a full day.", "boosts", None, 450, {"boostHours": 24}, 0),
    ("favor_100", "Favor bundle", "100 Favor to rush timers.", "boosts", None, 100, {"favor": 100}, 0),
    ("favor_500", "Favor crate", "500 Favor to rush timers.", "boosts", "$2.99", 450, {"favor": 500}, 0),
    ("permanent_5", "Investor's edge", "+5% income from every business, permanently (up to 3).", "upgrades", "$4.99", 1500, {"permanentPct": 0.05}, 3),
    ("slot_1", "Extra business slot", "One more business slot, permanently. Every slot can also be earned in play (up to 2 here).", "upgrades", None, 800, {"slots": 1}, 2),
    ("offline_2h", "Night shift", "+2 hours of offline earnings, permanently (up to 2).", "upgrades", None, 900, {"offlineHours": 2}, 2),
    ("vip_30", "VIP · 30 days", "For 30 game days: +5% income, +1 h offline earnings and 20 extra Bullion with every daily reward.", "passes", "$9.99", 2500, {"vipDays": 30}, 0),
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
    ("title_visionary", "Title: The Visionary", "A title for your profile.", "cosmetics", None, 250, {"cosmetic": "title_visionary"}, 1),
    ("title_self_made", "Title: Self-Made", "A title for your profile.", "cosmetics", None, 250, {"cosmetic": "title_self_made"}, 1),
    ("title_closer", "Title: The Closer", "A title for your profile.", "cosmetics", None, 250, {"cosmetic": "title_closer"}, 1),
    ("title_market_maker", "Title: Market Maker", "A title for your profile.", "cosmetics", None, 300, {"cosmetic": "title_market_maker"}, 1),
    ("title_iron_will", "Title: Iron Will", "A title for your profile.", "cosmetics", None, 300, {"cosmetic": "title_iron_will"}, 1),
    ("title_silver_spoon", "Title: Silver Spoon", "A title for your profile (worn ironically).", "cosmetics", None, 350, {"cosmetic": "title_silver_spoon"}, 1),
    ("frame_ruby", "Ruby frame", "A ruby frame for your profile picture.", "cosmetics", None, 350, {"cosmetic": "frame_ruby"}, 1),
    ("frame_amethyst", "Amethyst frame", "An amethyst frame for your profile picture.", "cosmetics", None, 350, {"cosmetic": "frame_amethyst"}, 1),
    ("frame_rose_gold", "Rose gold frame", "A rose gold frame for your profile picture.", "cosmetics", None, 400, {"cosmetic": "frame_rose_gold"}, 1),
    ("frame_platinum", "Platinum frame", "A platinum frame for your profile picture.", "cosmetics", None, 600, {"cosmetic": "frame_platinum"}, 1),
    ("avatar_yacht", "Avatar: Superyacht", "A superyacht emblem for your profile picture.", "cosmetics", None, 300, {"cosmetic": "avatar_yacht"}, 1),
    ("avatar_heli", "Avatar: Helicopter", "A helicopter emblem for your profile picture.", "cosmetics", None, 300, {"cosmetic": "avatar_heli"}, 1),
    ("avatar_island", "Avatar: Private island", "A private island emblem for your profile picture.", "cosmetics", None, 400, {"cosmetic": "avatar_island"}, 1),
    ("avatar_mansion", "Avatar: Mansion", "A mansion emblem for your profile picture.", "cosmetics", None, 400, {"cosmetic": "avatar_mansion"}, 1),
    ("avatar_watch", "Avatar: Grand complication", "A watch emblem for your profile picture.", "cosmetics", None, 250, {"cosmetic": "avatar_watch"}, 1),
    ("avatar_wine", "Avatar: Grand cru", "A wine emblem for your profile picture.", "cosmetics", None, 250, {"cosmetic": "avatar_wine"}, 1),
]

# Avatars anyone can pick (the app draws each from the game's own icon set); premium ones are the avatar_* cosmetics.
FREE_AVATARS = ["crown", "store", "chef", "fleet", "wheat", "factory", "hanger", "pickaxe", "flame", "bed", "bank", "crane",
                "film", "chip", "antenna", "plane", "flask", "rocket"]

COSMETICS = {
    "title_magnate": ("title", "The Magnate"), "title_old_money": ("title", "Old Money"), "title_disruptor": ("title", "The Disruptor"),
    "title_patron": ("title", "Patron of the Arts"), "title_tycoon": ("title", "Tycoon"),
    "frame_gold": ("frame", "Gold"), "frame_emerald": ("frame", "Emerald"), "frame_sapphire": ("frame", "Sapphire"), "frame_obsidian": ("frame", "Obsidian"),
    "title_visionary": ("title", "The Visionary"), "title_self_made": ("title", "Self-Made"), "title_closer": ("title", "The Closer"),
    "title_market_maker": ("title", "Market Maker"), "title_iron_will": ("title", "Iron Will"), "title_silver_spoon": ("title", "Silver Spoon"),
    "frame_ruby": ("frame", "Ruby"), "frame_amethyst": ("frame", "Amethyst"), "frame_rose_gold": ("frame", "Rose gold"), "frame_platinum": ("frame", "Platinum"),
    "avatar_yacht": ("avatar", "Superyacht"), "avatar_heli": ("avatar", "Helicopter"), "avatar_island": ("avatar", "Private island"),
    "avatar_mansion": ("avatar", "Mansion"), "avatar_watch": ("avatar", "Grand complication"), "avatar_wine": ("avatar", "Grand cru"),
}

# What you can buy with Bullion earned in play comes first; demo Bullion packs come last (GDD §2 "Respect the player").
SECTIONS = [("boosts", "Boosts"), ("upgrades", "Upgrades"), ("cosmetics", "Cosmetics"), ("passes", "VIP and Season Pass"), ("bullion", "Demo Bullion packs")]


def main(repo: Path) -> None:
    products = []
    for pid, name, desc, section, demo, bullion, grants, limit in P:
        products.append({"id": pid, "name": name, "description": desc, "section": section, "demoPrice": demo, "bullionPrice": bullion,
                         "grants": grants, "limit": limit})
    out = {
        "sections": [{"id": i, "name": n} for i, n in SECTIONS],
        "products": products,
        "cosmetics": [{"id": k, "kind": v[0], "name": v[1]} for k, v in COSMETICS.items()],
        # Tuned on the spender-gap check (Phase 14): paying should be 25–40 % faster, never more than 2×.
        "freeAvatars": FREE_AVATARS,
        "config": {"maxPermanentPct": 0.15, "maxSlots": 2, "maxOfflineHours": 4, "vipIncomePct": 0.05, "vipOfflineHours": 1, "vipLadderBullion": 20, "boostPct": 0.25},
    }
    path = repo / "content/data/store.json"
    path.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {path}: {len(products)} products, {len(COSMETICS)} cosmetics")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

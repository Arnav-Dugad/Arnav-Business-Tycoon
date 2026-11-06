"""Adds chapters 3–8 (and Chapter 2's unlocks) to content/data/chapters.json. Chapters 1–2 keep their tuned values.

Usage: python -I tools/content/gen_chapters.py <repo-root>
"""
import json
import sys
from pathlib import Path


def m(mid, title, desc, goal, cash="0.00", bullion=0, favor=0):
    out = {"id": mid, "title": title, "description": desc, "goal": goal, "rewardCash": cash}
    if bullion:
        out["rewardBullion"] = bullion
    if favor:
        out["rewardFavor"] = favor
    return out


def own(b):
    return {"type": "ownBusiness", "businessId": b}


def lvl(b, n):
    return {"type": "businessLevel", "businessId": b, "level": n}


def mgr(b):
    return {"type": "hireManager", "businessId": b}


def nw(amount):
    return {"type": "netWorth", "amount": amount}


LATER = [
    {"number": 3, "title": "Raw Power", "summary": "Land, factories and fashion. You start making the things other people sell.",
     "unlocksBusinesses": ["mining", "energy", "hotels"],
     "missions": [
         m("c3_farmland", "Breaking ground", "Open Farmland and plant your first season.", own("farmland"), "2000000.00", favor=15),
         m("c3_factory", "The first line", "Open a Factory.", own("factory"), "15000000.00"),
         m("c3_factory_25", "Balanced line", "Grow Factories to level 25.", lvl("factory", 25), "60000000.00", bullion=10),
         m("c3_fashion", "Opening night", "Open a Fashion House.", own("fashion"), "250000000.00", favor=20),
         m("c3_board_seat", "A seat at the table", "Own 5% of any listed company to earn a board seat.", {"type": "boardSeats", "count": 1}, "100000000.00", bullion=15),
         m("c3_networth", "Billionaire", "Reach a net worth of $1 billion.", nw("1000000000.00"), "500000000.00", bullion=25),
     ]},
    {"number": 4, "title": "Old Money", "summary": "Ore, power and hospitality. The establishment starts returning your calls.",
     "unlocksBusinesses": ["bank", "construction", "media"],
     "missions": [
         m("c4_mining", "Below the surface", "Open Mining.", own("mining"), "3000000000.00", favor=20),
         m("c4_energy", "Power play", "Open Energy.", own("energy"), "30000000000.00"),
         m("c4_hotels", "Check-in", "Open Hotels & Resorts.", own("hotels"), "400000000000.00", favor=25),
         m("c4_first_conglomerate", "Synergy", "Form your first conglomerate.", {"type": "conglomerates", "count": 1}, "200000000000.00", bullion=30),
         m("c4_networth", "Trillionaire", "Reach a net worth of $1 trillion.", nw("1000000000000.00"), "200000000000.00", bullion=30),
     ]},
    {"number": 5, "title": "Disruption", "summary": "Banks, builders and broadcasters. Then the technologists arrive.",
     "unlocksBusinesses": ["tech", "telecom"],
     "missions": [
         m("c5_bank", "Lender of first resort", "Open Banking.", own("bank"), "5000000000000.00", favor=25),
         m("c5_construction", "Skyline", "Open Construction.", own("construction"), "60000000000000.00"),
         m("c5_media", "On air", "Open Media.", own("media"), "700000000000000.00", favor=25),
         m("c5_ten_businesses", "Diversified", "Own ten businesses.", {"type": "businessesOwned", "count": 10}, "1000000000000000.00", bullion=35),
     ]},
    {"number": 6, "title": "Altitude", "summary": "Silicon and signal. Your empire learns to think and to talk.",
     "unlocksBusinesses": ["airline", "pharma"],
     "missions": [
         m("c6_tech", "Ship it", "Open Technology.", own("tech"), "9000000000000000.00", favor=30),
         m("c6_telecom", "Signal", "Open Telecom & Data.", own("telecom"), "100000000000000000.00"),
         m("c6_two_conglomerates", "Group structure", "Form two conglomerates.", {"type": "conglomerates", "count": 2}, "300000000000000000.00", bullion=40),
     ]},
    {"number": 7, "title": "Escape Velocity", "summary": "Airlines and cures. Then the stadium, and the stars.",
     "unlocksBusinesses": ["sports", "space"],
     "missions": [
         m("c7_airline", "Wheels up", "Open an Airline.", own("airline"), "1000000000000000000.00", favor=35),
         m("c7_pharma", "Breakthrough", "Open Pharmaceuticals.", own("pharma"), "15000000000000000000.00"),
         m("c7_three_conglomerates", "Three pillars", "Form three conglomerates.", {"type": "conglomerates", "count": 3}, "20000000000000000000.00", bullion=50),
     ]},
    {"number": 8, "title": "The Crown", "summary": "Every pillar of the economy bears your name. Build the holding company that crowns it all.",
     "unlocksBusinesses": [],
     "missions": [
         m("c8_sports", "Home ground", "Open a Sports Franchise.", own("sports"), "200000000000000000000.00", favor=40),
         m("c8_space", "Liftoff", "Open Space.", own("space"), "2000000000000000000000.00"),
         m("c8_networth", "Sovereign wealth", "Reach a net worth of $1 sextillion.", nw("1000000000000000000000.00"), "0.00", bullion=100),
     ]},
]


def scale_rewards(repo: Path, chapters: list) -> None:
    """Opening a business pays back half its unlock cost; other rewards stay as written (they scale by chapter)."""
    from decimal import Decimal, getcontext
    getcontext().prec = 80
    unlocks = {b["id"]: Decimal(b["unlockCost"]) for b in json.loads((repo / "content/data/businesses.json").read_text(encoding="utf-8"))}
    for c in chapters:
        if c["number"] <= 2:
            continue
        # Other goals pay the opening price of the chapter's cheapest business. Fixed rewards used to dwarf the
        # economy (e.g. $1 quadrillion for owning ten businesses when the chapter's business cost $2.6 trillion) and
        # one reward funded the next chapter outright: the Phase 5 "cascade" (see TUNING_LOG p6c).
        owned = [unlocks[mi["goal"]["businessId"]] for mi in c["missions"] if mi["goal"]["type"] == "ownBusiness"]
        base = min(owned) if owned else None
        for mi in c["missions"]:
            if base is not None and mi["goal"]["type"] != "ownBusiness" and mi["id"] != "c8_networth":
                mi["rewardCash"] = f"{base.quantize(Decimal('0.01'))}"
            if mi["id"] == "c8_networth":
                # The final goal tracks the ladder: five times the Space unlock cost.
                mi["goal"]["amount"] = f"{(unlocks['space'] * 5).quantize(Decimal('0.01'))}"
                mi["description"] = "Reach a net worth worthy of a dynasty."
            if mi["goal"]["type"] == "ownBusiness":
                mi["rewardCash"] = f"{(unlocks[mi['goal']['businessId']] / 2).quantize(Decimal('0.01'))}"


def main(repo: Path) -> None:
    path = repo / "content/data/chapters.json"
    chapters = json.loads(path.read_text(encoding="utf-8"))
    kept = [c for c in chapters if c["number"] <= 2]
    for c in kept:
        if c["number"] == 2:
            c["unlocksBusinesses"] = ["farmland", "factory", "fashion"]
    allc = kept + LATER
    scale_rewards(repo, allc)
    path.write_text(json.dumps(allc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {len(kept) + len(LATER)} chapters")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

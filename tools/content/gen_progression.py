"""Generates content/data/progression.json: daily and weekly mission templates, achievements, the daily reward ladder,
weekly events and seasons, the Family Charter, heir traits, Dynasty challenges and the IPO (docs/GDD.md §12,
docs/ECONOMY.md §9).

Targets that would go stale as the economy grows are expressed in hours of income (decided when a mission is
assigned) or scaled by chapter. Rewards are in hours of income plus Bullion and season XP.

Usage: python -I tools/content/gen_progression.py <repo-root>
"""
import json
import sys
from pathlib import Path

# ----------------------------------------------------------------------------------------------------------------
# Metrics: counters the engine tracks (docs: core/progression). "earn_hours" targets are converted to dollars when
# a mission is assigned.
# ----------------------------------------------------------------------------------------------------------------
METRICS = {
    "levels": "business levels bought", "taps": "Hustle taps", "earn_hours": "income earned", "cards": "Desk decisions",
    "trades": "market trades", "frenzies": "Frenzies started", "managers": "managers hired", "expansions": "expansions finished",
    "properties": "properties bought", "luxury": "luxury pieces bought", "renovations": "renovations finished",
    "businesses": "businesses opened", "auctions": "auctions won", "returns": "tax returns self-filed",
    "payouts": "market payouts received", "loans_repaid": "loans repaid", "events": "world events lived through",
}

# (metric, min chapter, targets for easy/normal/hard by chapter band [ch1-2, ch3-4, ch5-8], titles)
DAILY = [
    ("levels", 1, [(15, 30, 60), (40, 80, 150), (80, 160, 300)], ["Shopfitter", "Busy builder", "Expansion drive", "Level rush"]),
    ("taps", 1, [(60, 150, 300), (60, 150, 300), (60, 150, 300)], ["Front of house", "Rush hour", "Counter champion", "Elbow grease"]),
    ("earn_hours", 1, [(2, 4, 8), (2, 4, 8), (2, 4, 8)], ["Good day's work", "Steady earner", "Money machine", "Cash flow"]),
    ("cards", 1, [(1, 2, 3), (1, 2, 3), (1, 2, 3)], ["Decision maker", "Desk duty", "Chief executive", "The buck stops here"]),
    ("frenzies", 1, [(1, 2, 3), (1, 2, 3), (1, 2, 3)], ["Frenzy", "Double rush", "Triple threat", "All-out"]),
    ("managers", 1, [(1, 1, 2), (1, 2, 3), (1, 2, 3)], ["New hire", "Talent scout", "Head-hunter", "Dream team"]),
    ("expansions", 1, [(1, 1, 2), (1, 2, 3), (1, 2, 3)], ["Ribbon cutting", "Grand opening", "Expansion spree", "Bigger and better"]),
    ("businesses", 1, [(1, 1, 1), (1, 1, 2), (1, 1, 2)], ["New venture", "Diversify", "Empire builder", "Fresh start"]),
    ("trades", 2, [(1, 3, 6), (2, 5, 10), (3, 8, 15)], ["Market debut", "Day trader", "Floor regular", "Bulls and bears"]),
    ("properties", 2, [(1, 1, 2), (1, 2, 3), (1, 2, 3)], ["House hunter", "Landlord", "Property tycoon", "Bricks and mortar"]),
    ("luxury", 2, [(1, 1, 2), (1, 2, 3), (1, 2, 3)], ["Treat yourself", "Collector", "Connoisseur", "Showroom stocker"]),
    ("renovations", 2, [(1, 1, 2), (1, 1, 2), (1, 2, 3)], ["Fixer-upper", "Renovator", "Makeover", "Builder's dust"]),
    ("payouts", 3, [(1, 2, 3), (1, 2, 4), (1, 2, 4)], ["Dividend day", "Coupon clipper", "Passive income", "Yield hunter"]),
    ("auctions", 3, [(1, 1, 1), (1, 1, 2), (1, 1, 2)], ["Going once", "Hammer time", "Sold!", "Paddle up"]),
    ("events", 2, [(1, 1, 2), (1, 1, 2), (1, 2, 2)], ["Weather the storm", "Read the news", "Ride the cycle", "Headline act"]),
]
DIFF = [("easy", 1.0, 6, 15), ("normal", 2.0, 10, 25), ("hard", 3.5, 16, 40)]  # (name, reward hours, bullion, season xp)

WEEKLY = [
    ("levels", 1, (300, 800, 2000), ["Week of growth", "Construction boom"]),
    ("taps", 1, (1500, 1500, 1500), ["Counter marathon", "Rush week"]),
    ("earn_hours", 1, (40, 40, 40), ["Week's wages", "Profit week"]),
    ("cards", 1, (8, 10, 12), ["Executive week", "Desk marathon"]),
    ("frenzies", 1, (10, 12, 14), ["Frenzy week", "Non-stop"]),
    ("managers", 1, (3, 5, 6), ["Hiring week", "Talent drive"]),
    ("expansions", 1, (3, 5, 6), ["Ribbon week", "Expansion week"]),
    ("trades", 2, (10, 20, 30), ["Trading week", "Market mover"]),
    ("properties", 2, (3, 5, 6), ["Property week", "Portfolio builder"]),
    ("luxury", 2, (3, 5, 8), ["Shopping week", "Collector's week"]),
    ("payouts", 3, (8, 12, 16), ["Income week", "Yield week"]),
    ("auctions", 3, (1, 2, 3), ["Auction week", "Hammer week"]),
    ("returns", 4, (1, 1, 1), ["Tax season", "Filing week"]),
    ("renovations", 2, (3, 4, 6), ["Makeover week", "Renovation week"]),
    ("events", 2, (4, 5, 6), ["Turbulent week", "Newsmaker"]),
]


def missions():
    daily, weekly = [], []
    for metric, ch, targets, titles in DAILY:
        for di, (dname, hours, bullion, xp) in enumerate(DIFF):
            daily.append({
                "id": f"d_{metric}_{dname}", "metric": metric, "minChapter": ch, "title": titles[di],
                "targets": [t[di] for t in targets], "rewardHours": hours, "bullion": bullion, "seasonXp": xp,
            })
        # A fourth, "focus" variant: normal target, double season XP.
        daily.append({
            "id": f"d_{metric}_focus", "metric": metric, "minChapter": ch, "title": titles[3],
            "targets": [t[1] for t in targets], "rewardHours": 1.5, "bullion": 8, "seasonXp": 50,
        })
    for metric, ch, targets, titles in WEEKLY:
        for i, title in enumerate(titles):
            mult = 1.0 if i == 0 else 1.5
            weekly.append({
                "id": f"w_{metric}_{i}", "metric": metric, "minChapter": ch, "title": title,
                "targets": [max(1, int(round(t * mult))) for t in targets], "rewardHours": 8.0 * mult, "bullion": int(40 * mult), "seasonXp": int(150 * mult),
            })
    return daily, weekly


# ----------------------------------------------------------------------------------------------------------------
# Achievements (10 categories). goal: {metric | netWorth | chapter | business | hq | rating | fortuneRank | ...}
# ----------------------------------------------------------------------------------------------------------------
BUSINESSES = ["retail", "restaurants", "logistics", "farmland", "factory", "fashion", "mining", "energy", "hotels", "bank",
              "construction", "media", "tech", "telecom", "airline", "pharma", "sports", "space"]
BUSINESS_NAMES = ["Retail Stores", "Restaurants", "Logistics", "Farmland", "Factories", "Fashion House", "Mining", "Energy",
                  "Hotels & Resorts", "Banking", "Construction", "Media", "Technology", "Telecom & Data", "Airline",
                  "Pharmaceuticals", "Sports Franchise", "Space"]
SUFFIX = ["", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc"]


def money_label(exp):
    return f"${10 ** (exp % 3)}{SUFFIX[exp // 3]}"


def achievements():
    a = []

    def add(aid, cat, title, desc, goal, bullion):
        a.append({"id": aid, "category": cat, "title": title, "description": desc, "goal": goal, "bullion": bullion})

    for i, exp in enumerate(range(3, 25, 2)):  # $1K .. $100Sx in ×100 steps (11)
        add(f"nw_{exp}", "Wealth", ["First thousand", "Six figures", "Millionaire", "Eight figures", "Billionaire", "Ten billion",
                                     "Trillionaire", "Hundred trillion", "Quadrillionaire", "Hundred quadrillion", "Quintillionaire"][i],
            f"Reach a net worth of {money_label(exp)}.", {"type": "netWorth", "log10": exp}, 5 + 5 * i)
    for i, n in enumerate([100, 500, 1000, 5000, 10000, 50000, 100000]):
        add(f"levels_{n}", "Builder", f"{n:,} levels", f"Buy {n:,} business levels.", {"type": "metric", "metric": "levels", "value": n}, 5 + 5 * i)
    for i, n in enumerate([100, 1000, 10000, 50000, 100000]):
        add(f"taps_{n}", "Hustle", f"{n:,} customers", f"Tap the counter {n:,} times.", {"type": "metric", "metric": "taps", "value": n}, 5 + 5 * i)
    for i, (b, name) in enumerate(zip(BUSINESSES, BUSINESS_NAMES)):
        add(f"open_{b}", "Empire", name, f"Open {name}.", {"type": "business", "id": b}, 5 + 3 * i)
    for ch in range(1, 9):
        add(f"chapter_{ch}", "Story", f"Chapter {ch} complete", f"Finish chapter {ch} of the story.", {"type": "chapter", "value": ch}, 10 * ch)
    for i, n in enumerate([1, 3, 5]):
        add(f"congl_{n}", "Empire", f"{n} conglomerate" + ("s" if n > 1 else ""), f"Form {n} conglomerate" + ("s." if n > 1 else "."), {"type": "conglomerates", "value": n}, 20 + 20 * i)
    add("holding", "Empire", "Holding company", "Form your holding company.", {"type": "holding"}, 60)
    for i, n in enumerate([1, 10, 25, 50]):
        add(f"managers_{n}", "People", f"{n} manager" + ("s" if n > 1 else ""), f"Hire {n} manager" + ("s." if n > 1 else "."), {"type": "metric", "metric": "managers", "value": n}, 5 + 10 * i)
    for i, n in enumerate([1, 10, 25, 50]):
        add(f"exp_{n}", "People", f"{n} expansion" + ("s" if n > 1 else ""), f"Finish {n} expansion" + ("s." if n > 1 else "."), {"type": "metric", "metric": "expansions", "value": n}, 5 + 10 * i)
    for i, n in enumerate([1, 10, 100, 500, 1000]):
        add(f"trades_{n}", "Markets", f"{n:,} trade" + ("s" if n > 1 else ""), f"Make {n:,} market trade" + ("s." if n > 1 else "."), {"type": "metric", "metric": "trades", "value": n}, 5 + 8 * i)
    for i, n in enumerate([1, 3, 5]):
        add(f"seats_{n}", "Markets", f"{n} board seat" + ("s" if n > 1 else ""), f"Hold {n} board seat" + ("s" if n > 1 else "") + " at once.", {"type": "boardSeats", "value": n}, 15 + 15 * i)
    for i, n in enumerate([1, 5, 10, 20]):
        add(f"props_{n}", "Estates", f"{n} propert" + ("ies" if n > 1 else "y"), f"Own {n} propert" + ("ies" if n > 1 else "y") + " at once.", {"type": "properties", "value": n}, 5 + 10 * i)
    for i, n in enumerate([1, 10, 25]):
        add(f"reno_{n}", "Estates", f"{n} renovation" + ("s" if n > 1 else ""), f"Finish {n} renovation" + ("s." if n > 1 else "."), {"type": "metric", "metric": "renovations", "value": n}, 5 + 10 * i)
    for i, n in enumerate([1, 3]):
        add(f"tower_{n}", "Estates", "Skyline" if n == 1 else "Skyline king", f"Complete {n} skyscraper" + ("s." if n > 1 else "."), {"type": "metric", "metric": "towers", "value": n}, 40 + 40 * i)
    for i, n in enumerate([1, 10, 25, 50, 100]):
        add(f"lux_{n}", "Collection", f"{n} piece" + ("s" if n > 1 else ""), f"Own {n} luxury piece" + ("s" if n > 1 else "") + " at once.", {"type": "luxuryOwned", "value": n}, 5 + 8 * i)
    for i, n in enumerate([1, 5, 10]):
        add(f"sets_{n}", "Collection", f"{n} set" + ("s" if n > 1 else ""), f"Complete {n} collection set" + ("s." if n > 1 else "."), {"type": "sets", "value": n}, 20 + 20 * i)
    for i, n in enumerate([1, 5, 10, 25]):
        add(f"auction_{n}", "Collection", f"{n} hammer" + ("s" if n > 1 else ""), f"Win {n} auction" + ("s." if n > 1 else "."), {"type": "metric", "metric": "auctions", "value": n}, 10 + 10 * i)
    for i, tier in enumerate(["Bronze", "Silver", "Gold", "Platinum", "Diamond", "Royal", "Imperial"]):
        add(f"status_{tier.lower()}", "Collection", f"{tier} status", f"Reach {tier} Status.", {"type": "statusTier", "value": i + 1}, 10 + 10 * i)
    for i, n in enumerate([1, 10, 50, 100, 250]):
        add(f"cards_{n}", "Rivals", f"{n} decision" + ("s" if n > 1 else ""), f"Decide {n} matter" + ("s" if n > 1 else "") + " on the Desk.", {"type": "metric", "metric": "cards", "value": n}, 5 + 8 * i)
    for i, n in enumerate([220, 100, 50, 10, 1]):
        add(f"fortune_{n}", "Rivals", "On the list" if n == 220 else ("Richest in the world" if n == 1 else f"Top {n}"),
            "Enter the Fortune List." if n == 220 else ("Reach #1 on the Fortune List." if n == 1 else f"Reach the top {n} of the Fortune List."),
            {"type": "fortuneRank", "value": n}, 10 + 20 * i)
    for ch in range(2, 9):
        add(f"nemesis_{ch}", "Rivals", f"Nemesis {ch - 1}", f"Defeat chapter {ch}'s nemesis.", {"type": "chapter", "value": ch}, 15)
    for i, n in enumerate([1, 10]):
        add(f"repaid_{n}", "Finance", f"{n} loan" + ("s" if n > 1 else "") + " repaid", f"Repay {n} loan" + ("s." if n > 1 else "."), {"type": "metric", "metric": "loans_repaid", "value": n}, 10 + 15 * i)
    for i, r in enumerate(["A", "AA", "AAA"]):
        add(f"rating_{r.lower()}", "Finance", f"Rated {r}", f"Earn a credit rating of {r}.", {"type": "rating", "value": r}, 15 + 15 * i)
    for i, n in enumerate([1, 5, 10]):
        add(f"returns_{n}", "Finance", f"{n} return" + ("s" if n > 1 else ""), f"Self-file {n} tax return" + ("s." if n > 1 else "."), {"type": "metric", "metric": "returns", "value": n}, 10 + 10 * i)
    add("sold_business", "Rivals", "Cashing out", "Sell a business to a rival.", {"type": "metric", "metric": "sold", "value": 1}, 15)
    add("phoenix", "Finance", "Phoenix", "Rise again with the Phoenix protocol.", {"type": "metric", "metric": "phoenix", "value": 1}, 25)
    add("ipo", "Finance", "Ring the bell", "Take your holding company public.", {"type": "ipo"}, 80)
    for i, g in enumerate([2, 3, 5, 10]):
        add(f"gen_{g}", "Dynasty", f"Generation {g}", f"Reach generation {g}.", {"type": "generation", "value": g}, 50 + 50 * i)
    for i, n in enumerate([100, 1000, 10000]):
        add(f"legacy_{n}", "Dynasty", f"{n:,} Legacy", f"Bank {n:,} Legacy.", {"type": "legacy", "value": n}, 40 + 40 * i)
    for i, n in enumerate([1, 3]):
        add(f"heirloom_{n}", "Dynasty", f"{n} heirloom" + ("s" if n > 1 else ""), f"Pass {n} heirloom" + ("s" if n > 1 else "") + " to an heir.", {"type": "heirlooms", "value": n}, 20 + 20 * i)
    for i, n in enumerate([1, 5]):
        add(f"challenge_{n}", "Dynasty", f"{n} challenge" + ("s" if n > 1 else ""), f"Complete {n} Dynasty challenge" + ("s." if n > 1 else "."), {"type": "challenges", "value": n}, 40 + 60 * i)
    for i, n in enumerate([10, 50, 100, 250]):
        add(f"daily_{n}", "Habits", f"{n} dailies", f"Complete {n} daily missions.", {"type": "metric", "metric": "dailies", "value": n}, 10 + 10 * i)
    for i, n in enumerate([5, 25, 50]):
        add(f"weekly_{n}", "Habits", f"{n} weeklies", f"Complete {n} weekly missions.", {"type": "metric", "metric": "weeklies", "value": n}, 20 + 20 * i)
    for i, n in enumerate([7, 28]):
        add(f"ladder_{n}", "Habits", "Week in" if n == 7 else "Month in", f"Claim {n} daily rewards.", {"type": "metric", "metric": "ladder", "value": n}, 15 + 25 * i)
    for i, n in enumerate([3, 5, 8, 10, 12]):
        add(f"hq_{n}", "Builder", f"HQ level {n}", f"Upgrade your headquarters to level {n}.", {"type": "hq", "value": n}, 10 + 10 * i)
    return a


# ----------------------------------------------------------------------------------------------------------------
# Dynasty
# ----------------------------------------------------------------------------------------------------------------
LEGACY_THRESHOLDS = [10, 25, 60, 150, 400, 1000, 2500, 6000, 15000, 40000]
GEN_REQ = [2, 2, 2, 3, 3, 4, 4, 5, 6, 7]

# Each tier: two choices (effect type, value, name, text). Effects are implemented in core/progression.
CHARTER = {
    "industry": ("Industry", [
        (("incomeAll", 0.10, "Family firm", "+10% income from every business"), ("hustle", 1.0, "Shopkeeper's touch", "Hustle taps earn ×2")),
        (("offlineHours", 2, "Night managers", "+2 h offline earnings cap"), ("incomeSector:consumer", 0.25, "High-street empire", "+25% consumer income")),
        (("slots", 1, "Extra site", "+1 business slot"), ("incomeSector:food", 0.25, "Farm to table", "+25% food income")),
        (("incomeAll", 0.15, "Industrial might", "+15% income from every business"), ("incomeSector:industrials", 0.30, "Assembly lines", "+30% industrial income")),
        (("offlineHours", 3, "Round the clock", "+3 h offline earnings cap"), ("incomeSector:energy", 0.30, "Power grid", "+30% energy income")),
        (("slots", 1, "Second campus", "+1 business slot"), ("incomeSector:materials", 0.30, "Deep mines", "+30% materials income")),
        (("incomeAll", 0.20, "Conglomerate DNA", "+20% income from every business"), ("incomeSector:technology", 0.35, "Silicon heritage", "+35% technology income")),
        (("hustle", 2.0, "Born salesman", "Hustle taps earn ×3"), ("incomeSector:transport", 0.35, "Logistics dynasty", "+35% transport income")),
        (("incomeAll", 0.25, "Titans of industry", "+25% income from every business"), ("incomeSector:media", 0.40, "Media barons", "+40% media income")),
        (("slots", 1, "Third campus", "+1 business slot"), ("incomeSector:frontier", 0.50, "Star-bound", "+50% space income")),
    ]),
    "finance": ("Finance", [
        (("startCash", 6, "Trust fund", "Heirs start with $1M"), ("creditScore", 30, "Good name", "+30 credit score")),
        (("dividends", 0.20, "Dividend habit", "+20% dividends and coupons"), ("rent", 0.15, "Landed family", "+15% rent")),
        (("taxCut", 0.02, "Family office", "−2 points on every tax bracket"), ("startCash", 9, "Bigger trust", "Heirs start with $1B")),
        (("creditScore", 40, "Banker's friend", "+40 credit score"), ("dividends", 0.30, "Patient capital", "+30% dividends and coupons")),
        (("rent", 0.25, "Prime addresses", "+25% rent"), ("taxCut", 0.03, "Clever structures", "−3 points on every tax bracket")),
        (("startCash", 12, "Inheritance", "Heirs start with $1T"), ("incomeSector:financials", 0.35, "Merchant bank", "+35% financial income")),
        (("dividends", 0.40, "Compounders", "+40% dividends and coupons"), ("creditScore", 50, "AAA family", "+50 credit score")),
        (("taxCut", 0.04, "Offshore wisdom", "−4 points on every tax bracket"), ("rent", 0.35, "Real-estate dynasty", "+35% rent")),
        (("legacyBonus", 0.10, "Long view", "+10% Legacy at every prestige"), ("startCash", 15, "Old fortune", "Heirs start with $1Qa")),
        (("legacyBonus", 0.20, "Generational wealth", "+20% Legacy at every prestige"), ("incomeAll", 0.30, "Money makes money", "+30% income from every business")),
    ]),
    "influence": ("Influence", [
        (("luxuryDiscount", 0.03, "Dealer's friend", "3% off at dealers"), ("status", 0.10, "Good taste", "+10% Status")),
        (("takeoverShield", 0.5, "Loyal shareholders", "Takeover pressure halved"), ("luxuryDiscount", 0.05, "VIP client", "5% off at dealers")),
        (("status", 0.15, "Society pages", "+15% Status"), ("incomeSector:property", 0.30, "City builders", "+30% property income")),
        (("luxuryDiscount", 0.05, "Private sales", "5% off at dealers"), ("takeoverShield", 0.5, "Poison-pill charter", "Takeover pressure halved")),
        (("status", 0.20, "Old-world glamour", "+20% Status"), ("incomeSector:healthcare", 0.35, "Hospital wings", "+35% healthcare income")),
        (("legacyBonus", 0.05, "Family legend", "+5% Legacy at every prestige"), ("luxuryDiscount", 0.05, "Collector's eye", "5% off at dealers")),
        (("status", 0.25, "Royal patronage", "+25% Status"), ("takeoverShield", 1.0, "Untouchable", "No takeover pressure")),
        (("incomeAll", 0.15, "Kingmakers", "+15% income from every business"), ("legacyBonus", 0.10, "Statesmen", "+10% Legacy at every prestige")),
        (("status", 0.30, "Icons", "+30% Status"), ("luxuryDiscount", 0.08, "First call", "8% off at dealers")),
        (("legacyBonus", 0.15, "Immortal name", "+15% Legacy at every prestige"), ("incomeAll", 0.25, "Soft power", "+25% income from every business")),
    ]),
}

TRAITS = [
    ("frugal", "Frugal", "Watches every penny. Starts with $1M.", "startCash", 6), ("charming", "Charming", "Dealers love them.", "luxuryDiscount", 0.03),
    ("workaholic", "Workaholic", "Never stops.", "offlineHours", 2), ("salesman", "Natural salesman", "Can sell anything.", "hustle", 1.0),
    ("visionary", "Visionary", "Sees what others miss.", "incomeAll", 0.08), ("cautious", "Cautious", "Pays every bill on time.", "creditScore", 25),
    ("landlord", "Born landlord", "Loves property.", "rent", 0.15), ("investor", "Investor", "Lives on dividends.", "dividends", 0.20),
    ("glamorous", "Glamorous", "Always in the society pages.", "status", 0.15), ("tough", "Tough", "Cannot be pushed around.", "takeoverShield", 0.5),
    ("accountant", "Accountant's child", "Knows the tax code.", "taxCut", 0.02), ("builder", "Builder", "Opens an extra site.", "slots", 1),
    ("techie", "Techie", "Grew up coding.", "incomeSector:technology", 0.25), ("foodie", "Foodie", "Lives for great food.", "incomeSector:food", 0.25),
    ("fashionista", "Fashionista", "Sets trends.", "incomeSector:consumer", 0.25), ("engineer", "Engineer", "Loves machines.", "incomeSector:industrials", 0.25),
    ("rocket", "Rocket kid", "Dreams of space.", "incomeSector:frontier", 0.30), ("banker", "Banker", "Thinks in interest rates.", "incomeSector:financials", 0.25),
    ("ambitious", "Ambitious", "Wants a bigger legacy.", "legacyBonus", 0.08), ("traveller", "Traveller", "Knows every route.", "incomeSector:transport", 0.25),
]

CHALLENGES = [
    ("self_made", "Self-made", "No loans or credit line this generation.", "noLoans", 0.15),
    ("purist", "Purist", "No crypto this generation.", "noCrypto", 0.05),
    ("no_markets", "Main Street only", "No stocks, funds, bonds, commodities or crypto.", "noMarkets", 0.20),
    ("renter", "Forever renter", "No property this generation.", "noProperty", 0.20),
    ("spartan", "Spartan", "No luxury purchases or auctions.", "noLuxury", 0.10),
    ("no_frenzy", "Steady hands", "No Frenzies this generation.", "noFrenzy", 0.08),
    ("tax_hawk", "Tax hawk", "Every tax bracket is 5 points higher.", "taxPlus", 0.10),
    ("short_nights", "Short nights", "Offline earnings cap halved.", "halfOffline", 0.15),
    ("lean_hq", "Lean HQ", "No headquarters upgrades.", "noHq", 0.25),
    ("hands_off", "Hands off the Desk", "No decision cards this generation.", "noCards", 0.05),
    ("from_zero", "From zero", "Start with no inheritance (trust funds ignored).", "noStartCash", 0.05),
    ("iron_will", "Iron will", "No Phoenix protocol and no restructuring.", "noRescue", 0.12),
]

LADDER = [
    {"day": 1, "cashHours": 1.0, "bullion": 0, "favor": 0}, {"day": 2, "cashHours": 0.0, "bullion": 10, "favor": 0},
    {"day": 3, "cashHours": 0.0, "bullion": 0, "favor": 5}, {"day": 4, "cashHours": 2.0, "bullion": 0, "favor": 0},
    {"day": 5, "cashHours": 0.0, "bullion": 20, "favor": 0}, {"day": 6, "cashHours": 0.0, "bullion": 0, "favor": 10},
    {"day": 7, "cashHours": 4.0, "bullion": 50, "favor": 0},
]

WEEKLY_EVENTS = [
    ("oil_shock_week", "Oil Shock", "Energy prices spike all week.", "sector:energy", 0.20, ["frenzies", "levels", "events"]),
    ("art_fair", "Art Fair", "Collectors flood the city.", "status", 0.15, ["luxury", "auctions", "cards"]),
    ("startup_week", "Startup Week", "Founders everywhere.", "sector:technology", 0.20, ["businesses", "managers", "levels"]),
    ("harvest_festival", "Harvest Festival", "Farms and restaurants boom.", "sector:food", 0.20, ["taps", "levels", "expansions"]),
    ("fashion_month", "Fashion Month", "The runways are full.", "sector:consumer", 0.20, ["luxury", "levels", "cards"]),
    ("property_expo", "Property Expo", "Everyone is buying.", "rent", 0.20, ["properties", "renovations", "levels"]),
    ("bull_run", "Bull Run", "Markets only go up.", "dividends", 0.25, ["trades", "payouts", "levels"]),
    ("travel_season", "Travel Season", "Planes and hotels are full.", "sector:transport", 0.20, ["levels", "expansions", "cards"]),
    ("media_week", "Awards Season", "Every screen is on.", "sector:media", 0.20, ["levels", "cards", "managers"]),
    ("health_week", "Health Week", "Wellness is in.", "sector:healthcare", 0.20, ["levels", "cards", "expansions"]),
    ("build_week", "Build Week", "Cranes on every corner.", "sector:property", 0.20, ["properties", "levels", "renovations"]),
    ("mining_boom", "Metals Boom", "Copper hits records.", "sector:materials", 0.20, ["levels", "frenzies", "events"]),
    ("factory_week", "Factory Week", "Order books overflow.", "sector:industrials", 0.20, ["levels", "managers", "expansions"]),
    ("bank_week", "Banking Week", "Rates are up.", "sector:financials", 0.20, ["trades", "levels", "cards"]),
    ("space_week", "Launch Week", "Rockets every day.", "sector:frontier", 0.25, ["levels", "expansions", "cards"]),
    ("hustle_week", "Hustle Week", "Every tap counts double.", "hustle", 1.0, ["taps", "frenzies", "levels"]),
]

SEASONS = [
    ("golden_age", "Golden Age"), ("silk_road", "Silk Road"), ("iron_and_steam", "Iron and Steam"), ("boom_town", "Boom Town"),
    ("neon_nights", "Neon Nights"), ("high_society", "High Society"), ("blue_chip", "Blue Chip"), ("frontier", "Frontier"),
    ("riviera", "Riviera"), ("skyline", "Skyline"), ("tycoon_cup", "Tycoon Cup"), ("starfall", "Starfall"),
]


def season_track():
    tiers = []
    for t in range(1, 31):
        free = {"bullion": 10 + t * 2} if t % 3 else {"favor": 5 + t // 3}
        if t % 5 == 0:
            free = {"cashHours": 2.0 + t / 10, "bullion": 20 + t}
        premium = {"cosmetic": f"season_title_{t}"} if t % 5 == 0 else {"bullion": 15 + t * 2}
        tiers.append({"tier": t, "xp": 100 * t, "free": free, "premium": premium})
    return tiers


def main(repo: Path) -> None:
    daily, weekly = missions()
    ach = achievements()
    charter = []
    for branch, (bname, tiers) in CHARTER.items():
        for ti, choices in enumerate(tiers):
            for ci, (etype, value, name, text) in enumerate(choices):
                charter.append({"id": f"{branch}_{ti + 1}_{'ab'[ci]}", "branch": branch, "branchName": bname, "tier": ti + 1,
                                "legacy": LEGACY_THRESHOLDS[ti], "generation": GEN_REQ[ti], "name": name, "description": text,
                                "effect": etype, "value": value})
    out = {
        "daily": daily, "weekly": weekly, "achievements": ach, "ladder": LADDER,
        "weeklyEvents": [{"id": i, "name": n, "description": d, "effect": e, "value": v, "goals": g} for i, n, d, e, v, g in WEEKLY_EVENTS],
        "seasons": [{"id": i, "name": n} for i, n in SEASONS], "seasonTrack": season_track(),
        "charter": charter,
        "traits": [{"id": i, "name": n, "description": d, "effect": e, "value": v} for i, n, d, e, v in TRAITS],
        "challenges": [{"id": i, "name": n, "description": d, "rule": r, "legacyBonus": b} for i, n, d, r, b in CHALLENGES],
        "config": {
            "dailyCount": 3, "weeklyCount": 5, "seasonDays": 28, "weekDays": 7,
            "legacyScale": 4.0, "legacyExponent": 0.42, "legacyNormalizerLog10": 13.4, "legacyIncomeFactor": 0.02, "legacyIncomeExponent": 0.9,
            "heirlooms": [1, 1, 2, 2, 3], "traitChoices": 3,
            "ipo": {"floats": [0.10, 0.20, 0.30], "valuationMultiple": 2.0, "minChapter": 7},
        },
    }
    path = repo / "content/data/progression.json"
    path.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    cats = {}
    for x in ach:
        cats[x["category"]] = cats.get(x["category"], 0) + 1
    print(f"wrote {path}: {len(daily)} daily, {len(weekly)} weekly, {len(ach)} achievements {cats}, {len(charter)} charter nodes, "
          f"{len(TRAITS)} traits, {len(CHALLENGES)} challenges, {len(WEEKLY_EVENTS)} weekly events, {len(SEASONS)} seasons")
    assert len({x['id'] for x in ach}) == len(ach)


if __name__ == "__main__":
    main(Path(sys.argv[1]))

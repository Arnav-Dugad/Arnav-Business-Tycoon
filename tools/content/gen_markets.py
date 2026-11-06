"""Generates content/data/markets.json: sectors, 120 stocks, 4 funds, 8 bonds, 20 coins, 12 commodities,
news templates and market tunables (docs/GDD.md §6, docs/ECONOMY.md §5).

Company and coin names are coined from syllables with a fixed seed, so they are original and stable. They are
checked against a blocklist of real brands and well-known real tickers (the Kotlin validator checks again).

Usage: python -I tools/content/gen_markets.py <repo-root>
"""
import json
import random
import sys
from pathlib import Path

SEED = 20261106

# --- Sectors: id, name, icon, businesses that get the board-seat perk, (band weights) -------------------------
SECTORS = [
    ("consumer", "Consumer & Retail", "Store", ["retail", "fashion"], "Shops, brands and the people who buy them."),
    ("food", "Food & Agriculture", "Wheat", ["restaurants", "farmland"], "From field to fork."),
    ("industrials", "Industrials", "Factory", ["factory"], "Machines, parts and the plants that make them."),
    ("energy", "Energy", "Flame", ["energy"], "Oil, gas, power and the grid."),
    ("materials", "Materials & Mining", "Pickaxe", ["mining"], "Metals, minerals and everything dug from the ground."),
    ("financials", "Financials", "Bank", ["bank"], "Banks, insurers and asset managers."),
    ("technology", "Technology", "Chip", ["tech"], "Software, chips and devices."),
    ("media", "Media & Telecom", "Antenna", ["media", "telecom", "sports"], "Networks, studios and the screens in between."),
    ("healthcare", "Healthcare", "Flask", ["pharma"], "Medicines, devices and care."),
    ("property", "Property & Construction", "Crane", ["construction"], "Builders, developers and landlords."),
    ("transport", "Transport & Travel", "Plane", ["logistics", "airline", "hotels"], "Freight, flights and places to stay."),
    ("frontier", "Frontier", "Rocket", ["space"], "Rockets, robots and long bets."),
]

SECTOR_NOUNS = {
    "consumer": ["Goods", "Brands", "Outfitters", "Stores", "Home", "Living", "Mercantile", "Supply", "Retail Group", "Market"],
    "food": ["Foods", "Harvest", "Farms", "Provisions", "Dairy", "Grains", "Kitchens", "Orchards", "Beverages", "Agri"],
    "industrials": ["Industries", "Machinery", "Works", "Engineering", "Dynamics", "Systems", "Fabrication", "Tooling", "Controls", "Motors"],
    "energy": ["Energy", "Power", "Petroleum", "Utilities", "Grid", "Renewables", "Oil & Gas", "Fuels", "Solar", "Hydro"],
    "materials": ["Mining", "Metals", "Minerals", "Resources", "Materials", "Steel", "Chemicals", "Alloys", "Quarries", "Ores"],
    "financials": ["Financial", "Bancorp", "Capital", "Assurance", "Trust", "Holdings", "Securities", "Lending", "Partners", "Insurance"],
    "technology": ["Labs", "Systems", "Software", "Devices", "Micro", "Networks", "Logic", "Cloud", "Robotics", "Semiconductor"],
    "media": ["Media", "Broadcasting", "Telecom", "Studios", "Pictures", "Wireless", "Signal", "Entertainment", "Cable", "Publishing"],
    "healthcare": ["Therapeutics", "Health", "Biosciences", "Medical", "Pharma", "Diagnostics", "Genomics", "Care", "Biologics", "Clinics"],
    "property": ["Properties", "Realty", "Builders", "Construction", "Estates", "Developments", "Homes", "Towers", "Land", "Infrastructure"],
    "transport": ["Logistics", "Airways", "Freight", "Rail", "Shipping", "Hotels", "Transit", "Cargo", "Voyages", "Lines"],
    "frontier": ["Aerospace", "Orbital", "Fusion", "Quantum", "Autonomy", "Launch", "Deep Sea", "Hyperloop", "Biotech Frontier", "Space Systems"],
}

TRAITS = ["Steady compounder", "Serial acquirer", "Meme darling", "Dividend aristocrat", "Turnaround story",
          "Founder-led", "Cyclical", "Value pick", "High flyer", "Takeover target", "Quiet giant", "Innovator"]

# Risk bands (docs/ECONOMY.md §5.1): drift/day, vol/day range, dividend/day range, beta range, base cap range ($B)
BANDS = {
    "defensive": (0.008, (0.010, 0.018), (0.0015, 0.0030), (0.4, 0.8), (40, 400)),
    "core": (0.015, (0.018, 0.030), (0.0005, 0.0020), (0.8, 1.2), (15, 250)),
    "growth": (0.025, (0.030, 0.050), (0.0, 0.0005), (1.1, 1.6), (5, 150)),
    "speculative": (0.030, (0.050, 0.090), (0.0, 0.0), (1.4, 2.2), (0.5, 20)),
}
SECTOR_BANDS = {  # band mix per sector (10 companies each)
    "consumer": ["defensive"] * 3 + ["core"] * 4 + ["growth"] * 2 + ["speculative"],
    "food": ["defensive"] * 5 + ["core"] * 4 + ["growth"],
    "industrials": ["defensive"] * 2 + ["core"] * 5 + ["growth"] * 2 + ["speculative"],
    "energy": ["defensive"] * 3 + ["core"] * 4 + ["growth"] * 2 + ["speculative"],
    "materials": ["defensive"] + ["core"] * 4 + ["growth"] * 3 + ["speculative"] * 2,
    "financials": ["defensive"] * 3 + ["core"] * 5 + ["growth"] + ["speculative"],
    "technology": ["core"] * 3 + ["growth"] * 5 + ["speculative"] * 2,
    "media": ["defensive"] * 2 + ["core"] * 4 + ["growth"] * 3 + ["speculative"],
    "healthcare": ["defensive"] * 3 + ["core"] * 3 + ["growth"] * 2 + ["speculative"] * 2,
    "property": ["defensive"] * 3 + ["core"] * 5 + ["growth"] + ["speculative"],
    "transport": ["defensive"] * 2 + ["core"] * 5 + ["growth"] * 2 + ["speculative"],
    "frontier": ["core"] + ["growth"] * 4 + ["speculative"] * 5,
}

ONSETS = ["b", "c", "d", "f", "g", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "z", "br", "cr", "dr", "tr", "st", "gr", "pr", "cl", "fl", "sk"]
VOWELS = ["a", "e", "i", "o", "u", "a", "o", "e"]
MIDDLES = ["l", "r", "n", "m", "v", "s", "t", "d", "k", "rn", "lv", "nd", "st", "rd", "nt", "lt"]
ENDINGS = ["a", "o", "en", "ar", "ex", "is", "on", "ia", "ora", "ane", "el", "ix", "ent", "ura", "", ""]

# Real brands and well-known real tickers that generated names/tickers must avoid (case-insensitive).
BLOCKED_WORDS = {
    "apple", "amazon", "google", "tesla", "nike", "meta", "intel", "oracle", "nvidia", "visa", "chase", "shell", "exxon",
    "boeing", "ford", "honda", "sony", "dell", "ibm", "uber", "lyft", "zara", "gap", "ikea", "lego", "bayer", "pfizer",
    "merck", "roche", "sanofi", "novartis", "nestle", "danone", "unilever", "kraft", "heinz", "mars", "pepsi", "cola",
    "bitcoin", "ether", "ethereum", "solana", "cardano", "ripple", "doge", "dogecoin", "tether", "binance", "litecoin",
    "polka", "chainlink", "stellar", "tron", "avalanche", "monero", "zcash", "dash", "neo", "nano", "iota", "vechain",
    "orion", "atlas", "titan", "zenith", "apex", "vertex", "summit", "pinnacle", "nova", "aurora", "nexus", "quantum",
    "vista", "acme", "omni", "delta", "alpha", "beta", "gamma", "sigma", "omega", "trane", "xerox", "kodak", "canon",
    "nikon", "lexus", "volvo", "saab", "audi", "fiat", "opel", "seat", "kia", "mini", "jeep", "dodge", "ram",
    "corona", "stella", "rolex", "omega", "cartier", "tiffany", "chanel", "dior", "hermes", "prada", "gucci",
    "allianz", "axa", "aviva", "zurich", "lloyds", "barclays", "hsbc", "citi", "wells", "fargo", "schwab", "fidelity",
    "vanguard", "blackrock", "carlyle", "kkr", "verizon", "sprint", "vodafone", "orange", "telus", "rogers", "bell",
    "comcast", "viacom", "paramount", "warner", "fox", "hulu", "roku", "spotify", "zoom", "slack", "adobe", "cisco",
    "qualcomm", "broadcom", "micron", "amd", "arm", "asus", "acer", "lenovo", "xiaomi", "huawei", "oppo", "vivo",
    "alibaba", "tencent", "baidu", "rakuten", "sega", "nintendo", "atari", "valve", "epic", "unity", "roblox",
    "airbnb", "expedia", "booking", "hyatt", "hilton", "marriott", "accor", "ryanair", "easyjet", "qantas", "lufthansa",
    "united", "american", "southwest", "jetblue", "spirit", "frontier", "alaska", "hawaiian", "emirates", "etihad",
    "maersk", "fedex", "ups", "dhl", "tnt", "moller", "cargill", "deere", "caterpillar", "komatsu", "siemens", "bosch",
    "philips", "abb", "hitachi", "toshiba", "panasonic", "sharp", "lg", "samsung", "hyundai", "toyota", "nissan",
    "mazda", "subaru", "suzuki", "yamaha", "kawasaki", "bmw", "mercedes", "porsche", "ferrari", "rio", "vale",
    "glencore", "anglo", "bhp", "newmont", "barrick", "alcoa", "nucor", "arcelor", "tata", "reliance", "adani",
    "mahindra", "infosys", "wipro", "zeta", "theta", "kappa", "lambda", "epsilon", "aria", "luna", "terra", "sol",
    "voltex", "last", "landin", "stero", "nata", "dolar", "skod", "bondix", "bestix", "tosten", "kakix", "priv",
    "brar", "gond", "dandex", "lomane", "mavorn", "gestix", "podia", "citura", "cranex", "luvax", "funix",
    "zidane", "gratin", "tunis", "denis", "landon", "tonto", "brandis", "marel", "dives", "panto", "masal", "celora",
    "devora", "zilton", "hendel", "prentex", "lendis", "talis", "gumon", "vandon", "kalvax", "rotel", "dornir",
    "traton", "kanton", "kotor", "baden", "fadel", "bentos", "sterix", "velen", "nuvant", "lordis", "bomes",
    "sandia", "fivesix", "dentex", "tesora", "posen", "sitar", "zesta", "draken", "pasto", "gastex", "hindindo",
    "getax", "mentas", "five", "seven", "nine", "eight", "lavex", "canix", "vunix", "sunor",
    "honora", "premedia", "contax", "raton", "valval", "clade", "viron", "sordido", "nisto", "satastas", "vestones", "herdel",
    "hex", "avax", "flow", "near", "algo", "atom", "dot", "link", "uni", "aave", "comp", "maker", "curve", "sushi",
}
BLOCKED_TICKERS = {
    "AAPL", "MSFT", "GOOG", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "BRK", "JPM", "JNJ", "V", "PG", "UNH", "HD",
    "MA", "DIS", "BAC", "XOM", "CVX", "KO", "PEP", "WMT", "MRK", "PFE", "ABBV", "AVGO", "COST", "ORCL", "CSCO",
    "ADBE", "CRM", "NFLX", "INTC", "AMD", "QCOM", "TXN", "IBM", "GE", "F", "GM", "BA", "CAT", "DE", "MMM", "HON",
    "UPS", "FDX", "LMT", "RTX", "NOC", "GS", "MS", "C", "WFC", "AXP", "BLK", "SCHW", "T", "VZ", "TMUS", "CMCSA",
    "NKE", "SBUX", "MCD", "LOW", "TGT", "AMGN", "GILD", "BMY", "LLY", "ABT", "TMO", "DHR", "MDT", "ISRG", "SPY",
    "QQQ", "DIA", "IWM", "VOO", "VTI", "BTC", "ETH", "SOL", "ADA", "XRP", "DOGE", "USDT", "USDC", "BNB", "DOT",
    "LTC", "LINK", "AVAX", "MATIC", "SHIB", "TRX", "XLM", "ATOM", "UNI", "UBER", "LYFT", "ABNB", "SNAP", "PINS",
    "SPOT", "ZM", "SHOP", "SQ", "PYPL", "COIN", "HOOD", "PLTR", "SNOW", "NET", "DDOG", "MU", "ARM", "SONY", "TM",
    "HMC", "BABA", "TCEHY", "NIO", "LI", "BIDU", "JD", "PDD", "SAP", "ASML", "TSM", "NVO", "AZN", "GSK", "BP",
    "SHEL", "RIO", "BHP", "VALE", "TTE", "EQNR", "HSBC", "UBS", "ING", "BCS", "LYG", "DB", "SAN", "BBVA", "ACN",
    "NIST", "SATA", "NASA", "NATO", "FIFA", "UEFA", "FB", "TWTR", "X", "AA", "CL", "KHC", "MDLZ", "HSY", "GIS", "K", "CPB", "SJM", "MO", "PM", "BUD", "STZ", "TAP",
}


def coin_word(rng: random.Random, syllables: int) -> str:
    """Pronounceable coined word: onset-vowel-(middle-vowel)*-ending, e.g. "Kolvara", "Tremix"."""
    w = rng.choice(ONSETS) + rng.choice(VOWELS)
    for _ in range(syllables - 1):
        w += rng.choice(MIDDLES) + rng.choice(VOWELS)
    if rng.random() < 0.7:
        end = rng.choice(ENDINGS)
        w = w[:-1] + end if end else w
    else:
        w += rng.choice(["n", "r", "x", "s", "l"])
    return w.capitalize()


# Substrings that must never appear (offensive or unfortunate in English).
BAD_SUBSTRINGS = ("porn", "sex", "nazi", "anal", "cum", "tit", "fag", "rape", "kill", "dead", "shit", "piss", "slut",
                  "nig", "cock", "dick", "puss", "twat", "wank", "fuk", "fuc", "cunt", "bitch", "crap", "turd", "poo")


def ok_word(w: str, used: set) -> bool:
    lw = w.lower()
    if any(b in lw for b in BAD_SUBSTRINGS):
        return False
    if lw in used or len(lw) < 5 or len(lw) > 8:
        return False
    if any(b in lw for b in BLOCKED_WORDS if len(b) >= 4) or lw in BLOCKED_WORDS:
        return False
    # Avoid awkward clusters
    if any(a == b and a not in "lst" for a, b in zip(lw, lw[1:])):
        return False
    if any(x in lw for x in ("aa", "ii", "uu", "ee", "oo", "ae", "uo", "ui", "ue", "ao", "eo")):
        return False
    return True


def make_ticker(name: str, used: set, length: int) -> str:
    letters = [c for c in name.upper() if c.isalpha()]
    consonants = [c for c in letters[1:] if c not in "AEIOU"]
    candidates = []
    first = letters[0]
    for i in range(len(consonants)):
        for j in range(i + 1, len(consonants)):
            for k in range(j + 1, len(consonants) + 1):
                t = first + consonants[i] + consonants[j] + (consonants[k] if k < len(consonants) and length == 4 else "")
                if len(t) == length:
                    candidates.append(t)
    candidates += [first + "".join(letters[1:length])]
    for t in candidates:
        if any(a == b for a, b in zip(t, t[1:])):
            continue
        if t not in used and t not in BLOCKED_TICKERS:
            return t
    raise ValueError(f"no ticker for {name}")


def r6(x: float) -> float:
    return round(x, 6)


def main(repo: Path) -> None:
    rng = random.Random(SEED)
    used_words: set = set()
    used_tickers: set = set()

    def fresh_word(syl):
        while True:
            w = coin_word(rng, syl)
            if ok_word(w, used_words):
                used_words.add(w.lower())
                return w

    sectors = [{"id": sid, "name": name, "icon": icon, "businesses": biz, "description": desc}
               for sid, name, icon, biz, desc in SECTORS]

    stocks = []
    for sid, _, _, _, _ in SECTORS:
        nouns = SECTOR_NOUNS[sid][:]
        rng.shuffle(nouns)
        bands = SECTOR_BANDS[sid][:]
        for i in range(10):
            band = bands[i]
            drift, vol_r, div_r, beta_r, cap_r = BANDS[band]
            base = fresh_word(rng.choice([2, 2, 3]))
            name = f"{base} {nouns[i]}"
            ticker = make_ticker(base + nouns[i], used_tickers, 4)
            used_tickers.add(ticker)
            vol = rng.uniform(*vol_r)
            cap_b = round(rng.uniform(*cap_r), 1)
            price = round(rng.uniform(8, 120) if band in ("speculative", "growth") else rng.uniform(20, 480), 2)
            stocks.append({
                "id": ticker.lower(),
                "ticker": ticker,
                "name": name,
                "sector": sid,
                "band": band,
                "trait": rng.choice(TRAITS),
                "driftPerDay": r6(drift * rng.uniform(0.85, 1.15)),
                "volPerDay": r6(vol),
                "dividendPerDay": r6(rng.uniform(*div_r)),
                "beta": r6(rng.uniform(*beta_r)),
                "baseCapBillions": cap_b,
                "startPrice": f"{price:.2f}",
            })

    funds = [
        {"id": "fund_total", "ticker": "ATMF", "name": "Aurum Total Market Fund", "style": "total", "feePerDay": 0.0001,
         "description": "Every company on the exchange, weighted by size. The calm way in."},
        {"id": "fund_growth", "ticker": "AGRF", "name": "Aurum Growth Fund", "style": "growth", "feePerDay": 0.00015,
         "description": "Growth and speculative names. Bigger swings, bigger upside."},
        {"id": "fund_defensive", "ticker": "ADFF", "name": "Aurum Defensive Fund", "style": "defensive", "feePerDay": 0.0001,
         "description": "Defensive companies that hold up in a downturn and pay steady dividends."},
        {"id": "fund_global", "ticker": "AGLF", "name": "Aurum Global Fund", "style": "global", "feePerDay": 0.00012,
         "description": "An equal slice of every sector, so no single industry decides your returns."},
    ]
    for f in funds:
        assert f["ticker"] not in BLOCKED_TICKERS
        used_tickers.add(f["ticker"])

    bonds = [
        {"id": "bond_gov_2", "ticker": "GOV2", "name": "Treasury Note, 2-season", "kind": "government", "duration": 2.0, "couponSpread": 0.0, "defaultChance": 0.0},
        {"id": "bond_gov_5", "ticker": "GOV5", "name": "Treasury Note, 5-season", "kind": "government", "duration": 5.0, "couponSpread": 0.0005, "defaultChance": 0.0},
        {"id": "bond_gov_10", "ticker": "GOVX", "name": "Treasury Bond, 10-season", "kind": "government", "duration": 9.0, "couponSpread": 0.001, "defaultChance": 0.0},
        {"id": "bond_gov_30", "ticker": "GOVL", "name": "Treasury Bond, long", "kind": "government", "duration": 18.0, "couponSpread": 0.0015, "defaultChance": 0.0},
        {"id": "bond_ig_5", "ticker": "IGC5", "name": "Investment-Grade Corporate, 5-season", "kind": "investment_grade", "duration": 4.5, "couponSpread": 0.002, "defaultChance": 0.0},
        {"id": "bond_ig_10", "ticker": "IGCX", "name": "Investment-Grade Corporate, 10-season", "kind": "investment_grade", "duration": 8.0, "couponSpread": 0.0028, "defaultChance": 0.0},
        {"id": "bond_hy_5", "ticker": "HYC5", "name": "High-Yield Corporate, 5-season", "kind": "high_yield", "duration": 4.0, "couponSpread": 0.008, "defaultChance": 0.04},
        {"id": "bond_hy_10", "ticker": "HYCX", "name": "High-Yield Corporate, 10-season", "kind": "high_yield", "duration": 7.0, "couponSpread": 0.011, "defaultChance": 0.06},
    ]
    for b in bonds:
        used_tickers.add(b["ticker"])

    # Crypto: 4 tiers × 5 coins. drift/day, vol/day, crash chance/day, crash depth range, staking/day range, cap $B, price range
    tiers = {
        1: (0.020, 0.06, 0.0, (0.0, 0.0), (0.0, 0.0008), (60, 600), (800, 40000), "Established coin. Volatile, but it has survived every crash so far."),
        2: (0.026, 0.09, 0.004, (0.30, 0.45), (0.0005, 0.0015), (8, 60), (20, 900), "Large project. Swings hard and can crash in a bad week."),
        3: (0.032, 0.13, 0.010, (0.40, 0.70), (0.001, 0.003), (0.5, 8), (0.5, 40), "Small project. Big hype cycles and real crash risk."),
        4: (0.040, 0.18, 0.020, (0.60, 0.90), (0.0, 0.004), (0.02, 0.5), (0.0005, 0.5), "Speculative token. It can multiply or go to nearly zero. Only bet what you can lose."),
    }
    coins = []
    for tier in (1, 2, 3, 4):
        drift, vol, crash, depth, stake, cap_r, price_r, words = tiers[tier]
        for _ in range(5):
            base = fresh_word(rng.choice([2, 2, 3]))
            name = base + rng.choice(["", " Coin", " Chain", " Token", "", " Network"])
            t = make_ticker(base, used_tickers, 4)
            used_tickers.add(t)
            price = rng.uniform(*price_r)
            coins.append({
                "id": "c_" + t.lower(),
                "ticker": t,
                "name": name.strip(),
                "tier": tier,
                "driftPerDay": r6(drift * rng.uniform(0.85, 1.15)),
                "volPerDay": r6(vol * rng.uniform(0.85, 1.15)),
                "crashChancePerDay": r6(crash),
                "crashDepthMin": depth[0],
                "crashDepthMax": depth[1],
                "stakingPerDay": r6(rng.uniform(*stake)),
                "baseCapBillions": round(rng.uniform(*cap_r), 3),
                "startPrice": f"{price:.6f}",
                "risk": words,
            })

    commodities = [
        {"id": "crude", "ticker": "CRUD", "name": "Crude oil", "unit": "barrel", "anchorPrice": "78.00", "volPerDay": 0.025, "reversionPerDay": 0.5,
         "effects": [{"business": "energy", "mode": "revenue", "elasticity": 0.25}, {"business": "airline", "mode": "upkeep", "elasticity": 0.30}, {"business": "logistics", "mode": "upkeep", "elasticity": 0.20}]},
        {"id": "natgas", "ticker": "NGAS", "name": "Natural gas", "unit": "MMBtu", "anchorPrice": "3.20", "volPerDay": 0.040, "reversionPerDay": 0.6,
         "effects": [{"business": "energy", "mode": "revenue", "elasticity": 0.15}, {"business": "factory", "mode": "upkeep", "elasticity": 0.10}]},
        {"id": "gold", "ticker": "GOLD", "name": "Gold", "unit": "ounce", "anchorPrice": "2100.00", "volPerDay": 0.012, "reversionPerDay": 0.2,
         "effects": [{"business": "mining", "mode": "revenue", "elasticity": 0.15}]},
        {"id": "silver", "ticker": "SLVR", "name": "Silver", "unit": "ounce", "anchorPrice": "26.00", "volPerDay": 0.020, "reversionPerDay": 0.3,
         "effects": [{"business": "mining", "mode": "revenue", "elasticity": 0.05}]},
        {"id": "copper", "ticker": "COPR", "name": "Copper", "unit": "pound", "anchorPrice": "4.10", "volPerDay": 0.018, "reversionPerDay": 0.4,
         "effects": [{"business": "mining", "mode": "revenue", "elasticity": 0.10}, {"business": "construction", "mode": "upkeep", "elasticity": 0.10}, {"business": "telecom", "mode": "upkeep", "elasticity": 0.08}]},
        {"id": "ironore", "ticker": "IORE", "name": "Iron ore", "unit": "tonne", "anchorPrice": "115.00", "volPerDay": 0.022, "reversionPerDay": 0.4,
         "effects": [{"business": "mining", "mode": "revenue", "elasticity": 0.10}, {"business": "factory", "mode": "upkeep", "elasticity": 0.15}, {"business": "construction", "mode": "upkeep", "elasticity": 0.10}]},
        {"id": "lithium", "ticker": "LTHM", "name": "Lithium", "unit": "tonne", "anchorPrice": "14500.00", "volPerDay": 0.035, "reversionPerDay": 0.3,
         "effects": [{"business": "tech", "mode": "upkeep", "elasticity": 0.10}]},
        {"id": "rareearth", "ticker": "REEM", "name": "Rare earths", "unit": "kilogram", "anchorPrice": "62.00", "volPerDay": 0.040, "reversionPerDay": 0.3,
         "effects": [{"business": "tech", "mode": "upkeep", "elasticity": 0.08}, {"business": "space", "mode": "upkeep", "elasticity": 0.10}]},
        {"id": "wheat", "ticker": "WHET", "name": "Wheat", "unit": "bushel", "anchorPrice": "6.10", "volPerDay": 0.022, "reversionPerDay": 0.5,
         "effects": [{"business": "farmland", "mode": "revenue", "elasticity": 0.20}, {"business": "restaurants", "mode": "upkeep", "elasticity": 0.10}]},
        {"id": "coffee", "ticker": "COFE", "name": "Coffee", "unit": "pound", "anchorPrice": "2.30", "volPerDay": 0.028, "reversionPerDay": 0.5,
         "effects": [{"business": "farmland", "mode": "revenue", "elasticity": 0.08}, {"business": "retail", "mode": "upkeep", "elasticity": 0.05}]},
        {"id": "cocoa", "ticker": "COCA", "name": "Cocoa", "unit": "tonne", "anchorPrice": "4200.00", "volPerDay": 0.030, "reversionPerDay": 0.5,
         "effects": [{"business": "farmland", "mode": "revenue", "elasticity": 0.07}, {"business": "restaurants", "mode": "upkeep", "elasticity": 0.05}]},
        {"id": "timber", "ticker": "TMBR", "name": "Timber", "unit": "board foot", "anchorPrice": "0.55", "volPerDay": 0.020, "reversionPerDay": 0.4,
         "effects": [{"business": "construction", "mode": "upkeep", "elasticity": 0.12}, {"business": "hotels", "mode": "upkeep", "elasticity": 0.05}]},
    ]
    for c in commodities:
        assert c["ticker"] not in BLOCKED_TICKERS, c["ticker"]
        assert c["ticker"] not in used_tickers, c["ticker"]
        used_tickers.add(c["ticker"])

    # News templates. kind decides the effect; {company} {sector} {coin} {commodity} are filled from content.
    news = [
        # Company jumps (immediate)
        {"id": "co_contract", "kind": "company", "text": "{company} wins a multi-year supply contract", "min": 0.03, "max": 0.10, "weight": 3},
        {"id": "co_product", "kind": "company", "text": "{company} unveils a product line that analysts call a hit", "min": 0.04, "max": 0.12, "weight": 2},
        {"id": "co_buyback", "kind": "company", "text": "{company} announces a large share buyback", "min": 0.02, "max": 0.06, "weight": 2},
        {"id": "co_upgrade", "kind": "company", "text": "Analysts upgrade {company} to Buy", "min": 0.02, "max": 0.07, "weight": 3},
        {"id": "co_takeover", "kind": "company", "text": "{company} receives a takeover approach at a premium", "min": 0.10, "max": 0.25, "weight": 1},
        {"id": "co_recall", "kind": "company", "text": "{company} recalls a flagship product", "min": -0.12, "max": -0.04, "weight": 2},
        {"id": "co_ceo", "kind": "company", "text": "{company}'s chief executive steps down without warning", "min": -0.10, "max": -0.03, "weight": 2},
        {"id": "co_downgrade", "kind": "company", "text": "Analysts downgrade {company} to Sell", "min": -0.07, "max": -0.02, "weight": 3},
        {"id": "co_probe", "kind": "company", "text": "Regulators open an inquiry into {company}", "min": -0.15, "max": -0.05, "weight": 1},
        {"id": "co_outage", "kind": "company", "text": "{company} hit by a day-long systems outage", "min": -0.06, "max": -0.02, "weight": 2},
        # Sector drift (6 hours)
        {"id": "sec_rules", "kind": "sector", "text": "Lawmakers ease rules for {sector}", "min": 0.004, "max": 0.010, "weight": 2},
        {"id": "sec_demand", "kind": "sector", "text": "Demand surges across {sector}", "min": 0.004, "max": 0.012, "weight": 2},
        {"id": "sec_tariff", "kind": "sector", "text": "New tariffs weigh on {sector}", "min": -0.012, "max": -0.004, "weight": 2},
        {"id": "sec_glut", "kind": "sector", "text": "Oversupply worries hang over {sector}", "min": -0.010, "max": -0.003, "weight": 2},
        # Predictive earnings hints (applied at the next earnings season)
        {"id": "hint_beat", "kind": "hint", "text": "{company} expected to beat earnings estimates", "min": 0.04, "max": 0.09, "weight": 3},
        {"id": "hint_miss", "kind": "hint", "text": "{company} may miss earnings estimates", "min": -0.09, "max": -0.04, "weight": 3},
        # Commodities (immediate)
        {"id": "cm_supply", "kind": "commodity", "text": "Supply disruption sends {commodity} higher", "min": 0.06, "max": 0.18, "weight": 2},
        {"id": "cm_weather", "kind": "commodity", "text": "Bad weather threatens the {commodity} harvest", "min": 0.05, "max": 0.15, "weight": 1, "only": ["wheat", "coffee", "cocoa", "timber"]},
        {"id": "cm_glut", "kind": "commodity", "text": "{commodity} stockpiles swell; prices slide", "min": -0.14, "max": -0.05, "weight": 2},
        {"id": "cm_discovery", "kind": "commodity", "text": "A major new {commodity} deposit is discovered", "min": -0.12, "max": -0.04, "weight": 1, "only": ["gold", "silver", "copper", "ironore", "lithium", "rareearth"]},
        # Crypto
        {"id": "cr_hype", "kind": "crypto", "text": "{coin} trends everywhere as a new hype wave builds", "min": 0.08, "max": 0.30, "weight": 2},
        {"id": "cr_listing", "kind": "crypto", "text": "{coin} gets listed on a major exchange", "min": 0.10, "max": 0.25, "weight": 1},
        {"id": "cr_hack", "kind": "crypto", "text": "A bridge exploit drains funds from {coin}", "min": -0.35, "max": -0.12, "weight": 1},
        {"id": "cr_fud", "kind": "crypto", "text": "Rumors swirl about {coin}'s reserves", "min": -0.20, "max": -0.06, "weight": 2},
        # More company stories
        {"id": "co_partner", "kind": "company", "text": "{company} signs a partnership with a national retailer", "min": 0.02, "max": 0.07, "weight": 2},
        {"id": "co_expansion", "kind": "company", "text": "{company} opens a flagship site abroad", "min": 0.02, "max": 0.06, "weight": 2},
        {"id": "co_patent", "kind": "company", "text": "{company} wins a key patent ruling", "min": 0.04, "max": 0.11, "weight": 1},
        {"id": "co_dividend", "kind": "company", "text": "{company} raises its dividend", "min": 0.02, "max": 0.05, "weight": 2},
        {"id": "co_index", "kind": "company", "text": "{company} joins the exchange's headline index", "min": 0.03, "max": 0.08, "weight": 1},
        {"id": "co_record", "kind": "company", "text": "{company} reports record quarterly orders", "min": 0.03, "max": 0.09, "weight": 2},
        {"id": "co_award", "kind": "company", "text": "{company} named employer of the year", "min": 0.01, "max": 0.03, "weight": 1},
        {"id": "co_activist", "kind": "company", "text": "An activist investor takes a stake in {company}", "min": 0.04, "max": 0.10, "weight": 1},
        {"id": "co_spinoff", "kind": "company", "text": "{company} plans to spin off its fastest-growing unit", "min": 0.03, "max": 0.09, "weight": 1},
        {"id": "co_costcut", "kind": "company", "text": "{company} unveils a cost-cutting plan that impresses analysts", "min": 0.02, "max": 0.06, "weight": 2},
        {"id": "co_strike", "kind": "company", "text": "Workers at {company} go on strike", "min": -0.08, "max": -0.02, "weight": 2},
        {"id": "co_lawsuit", "kind": "company", "text": "{company} hit with a class-action lawsuit", "min": -0.10, "max": -0.03, "weight": 2},
        {"id": "co_fire", "kind": "company", "text": "A fire halts production at a {company} plant", "min": -0.09, "max": -0.03, "weight": 1},
        {"id": "co_guidance", "kind": "company", "text": "{company} cuts its outlook for the year", "min": -0.12, "max": -0.04, "weight": 2},
        {"id": "co_delay", "kind": "company", "text": "{company} delays its next big launch", "min": -0.08, "max": -0.02, "weight": 2},
        {"id": "co_accounting", "kind": "company", "text": "Auditors flag accounting questions at {company}", "min": -0.18, "max": -0.06, "weight": 1},
        {"id": "co_dilution", "kind": "company", "text": "{company} sells new shares to raise cash", "min": -0.06, "max": -0.02, "weight": 2},
        {"id": "co_cyber", "kind": "company", "text": "{company} discloses a data breach", "min": -0.09, "max": -0.03, "weight": 1},
        {"id": "co_shortreport", "kind": "company", "text": "A short seller publishes a damning report on {company}", "min": -0.15, "max": -0.05, "weight": 1},
        {"id": "co_supplier", "kind": "company", "text": "{company} loses its biggest customer", "min": -0.12, "max": -0.04, "weight": 1},
        # More sector stories
        {"id": "sec_subsidy", "kind": "sector", "text": "New subsidies announced for {sector}", "min": 0.005, "max": 0.012, "weight": 2},
        {"id": "sec_mergers", "kind": "sector", "text": "Merger talk lifts {sector}", "min": 0.004, "max": 0.010, "weight": 2},
        {"id": "sec_upgrade", "kind": "sector", "text": "Strategists turn bullish on {sector}", "min": 0.003, "max": 0.008, "weight": 2},
        {"id": "sec_rates", "kind": "sector", "text": "Higher borrowing costs bite into {sector}", "min": -0.010, "max": -0.003, "weight": 2},
        {"id": "sec_regulation", "kind": "sector", "text": "Lawmakers propose tougher rules for {sector}", "min": -0.012, "max": -0.004, "weight": 2},
        {"id": "sec_shortage", "kind": "sector", "text": "A parts shortage slows {sector}", "min": -0.010, "max": -0.003, "weight": 1},
        # More hints
        {"id": "hint_beat2", "kind": "hint", "text": "Channel checks point to strong sales at {company}", "min": 0.03, "max": 0.08, "weight": 2},
        {"id": "hint_miss2", "kind": "hint", "text": "Suppliers say orders from {company} are slowing", "min": -0.08, "max": -0.03, "weight": 2},
        {"id": "hint_beat3", "kind": "hint", "text": "{company} hires aggressively ahead of earnings", "min": 0.02, "max": 0.06, "weight": 1},
        {"id": "hint_miss3", "kind": "hint", "text": "Insiders sell shares in {company} before earnings", "min": -0.07, "max": -0.02, "weight": 1},
        # More commodities
        {"id": "cm_demand", "kind": "commodity", "text": "Factories race to buy {commodity}", "min": 0.04, "max": 0.12, "weight": 2},
        {"id": "cm_strike", "kind": "commodity", "text": "A strike at major producers tightens {commodity} supply", "min": 0.05, "max": 0.14, "weight": 1, "only": ["copper", "ironore", "gold", "silver", "lithium", "crude", "natgas"]},
        {"id": "cm_harvest", "kind": "commodity", "text": "A bumper harvest sends {commodity} lower", "min": -0.12, "max": -0.04, "weight": 1, "only": ["wheat", "coffee", "cocoa"]},
        {"id": "cm_slowdown", "kind": "commodity", "text": "Weak demand weighs on {commodity}", "min": -0.10, "max": -0.03, "weight": 2},
        {"id": "cm_cartel", "kind": "commodity", "text": "Producers agree to cut {commodity} output", "min": 0.06, "max": 0.16, "weight": 1, "only": ["crude", "natgas"]},
        # More crypto
        {"id": "cr_upgrade", "kind": "crypto", "text": "{coin} ships a long-awaited network upgrade", "min": 0.06, "max": 0.20, "weight": 2},
        {"id": "cr_whale", "kind": "crypto", "text": "A large holder moves a huge amount of {coin} to an exchange", "min": -0.15, "max": -0.04, "weight": 2},
        {"id": "cr_outage", "kind": "crypto", "text": "The {coin} network halts for several hours", "min": -0.18, "max": -0.05, "weight": 1},
        {"id": "cr_adoption", "kind": "crypto", "text": "A payments company starts accepting {coin}", "min": 0.08, "max": 0.22, "weight": 1},
        {"id": "cr_regulator", "kind": "crypto", "text": "Regulators warn investors about {coin}", "min": -0.20, "max": -0.06, "weight": 1},
    ]

    # Macro cycle (docs/ECONOMY.md §2: phases last 1.5–4 days). drift is added to every stock (× beta) per day;
    # rate is the central-bank rate per day (government bond yield).
    macro = [
        {"id": "expansion", "name": "Expansion", "driftPerDay": 0.002, "ratePerDay": 0.007, "minDays": 1.5, "maxDays": 4.0, "next": {"boom": 0.6, "slowdown": 0.4}},
        {"id": "boom", "name": "Boom", "driftPerDay": 0.005, "ratePerDay": 0.011, "minDays": 1.5, "maxDays": 3.0, "next": {"slowdown": 1.0}},
        {"id": "slowdown", "name": "Slowdown", "driftPerDay": -0.003, "ratePerDay": 0.009, "minDays": 1.5, "maxDays": 3.5, "next": {"recession": 0.5, "expansion": 0.5}},
        {"id": "recession", "name": "Recession", "driftPerDay": -0.010, "ratePerDay": 0.004, "minDays": 1.5, "maxDays": 3.0, "next": {"expansion": 1.0}},
    ]

    config = {
        "newsChancePerMinute": 0.05,
        "hintReliability": {"Rumor": 0.55, "Report": 0.72, "Confirmed": 0.9},
        "earningsEveryDays": 3,
        "earningsSurpriseSd": 0.035,
        "payoutEveryHours": 6,
        "noiseReversionPerDay": 2.0,
        "fundamentalVolShare": 0.7,
        "slippageK": 0.5,
        "slippageExponent": 0.6,
        "slippageMax": 0.5,
        "feeRate": 0.001,
        "capitalGainsTax": 0.10,
        "shortInitialMargin": 1.5,
        "shortMaintenanceMargin": 1.25,
        "borrowFeePerDay": 0.002,
        "boardSeatStake": 0.05,
        "boardSeatIncomeMultiplier": "1.05",
        "divisionStake": 0.20,
        "divisionDiscount": "0.75",
        "fundDepthMultiplier": 25.0,
        "bondDepthBillions": 20000.0,
        "commodityDepthBillions": 500.0,
        "unlocks": {"stocks": 2, "funds": 2, "bonds": 2, "crypto": 3, "commodities": 3, "shorts": 5},
        # Market depth grows with the story so investing stays meaningful (docs/ECONOMY.md §5.1).
        "depthScaleByChapter": [1, 1, 1, 10, 1000, 100000, 10000000, 1000000000],
        "historyFineMinutes": 30,
        "historyFinePoints": 48,
        "historyCoarseMinutes": 360,
        "historyCoarsePoints": 28,
        "newsKept": 40,
        "maxStake": 0.49,
    }

    out = {"config": config, "macro": macro, "sectors": sectors, "stocks": stocks, "funds": funds, "bonds": bonds,
           "coins": coins, "commodities": commodities, "news": news}
    path = repo / "content/data/markets.json"
    path.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {path}: {len(stocks)} stocks, {len(funds)} funds, {len(bonds)} bonds, {len(coins)} coins, {len(commodities)} commodities, {len(news)} news templates")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

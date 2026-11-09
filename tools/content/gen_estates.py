"""Generates content/data/estates.json: 22 real cities and their neighbourhoods, 10 property types, 8 skyscraper
designs, 40 tenant events and real-estate tunables (docs/GDD.md §7, docs/ECONOMY.md §6).

Real-world money (owner decision 2026-11-09): city prices come from researched prime prices per square metre
(tools/content/data/cities_real.json, sources inside), relative to Lisbon = 1. Property types are priced as real
property is; neighbourhood yields follow the real gross rental yields (scaled to game days).

Usage: python -I tools/content/gen_estates.py <repo-root>
"""
import json
import re
import math
import random
import sys
import unicodedata
from decimal import Decimal, getcontext
from pathlib import Path

getcontext().prec = 60
SEED = 7070707

# id, name, chapter, longitude, latitude, description, chapter's reference business for the price scale (None = fixed)
CITIES = [
    ("lisbon", "Lisbon", 2, -9.14, 38.72, "Hills, trams and river light. Old neighbourhoods are being restored one building at a time: where most property fortunes start.", None),
    ("london", "London", 2, -0.13, 51.51, "A global capital of finance, Georgian squares and warehouse conversions. Expensive, but it never really goes out of fashion.", None),
    ("miami", "Miami", 3, -80.19, 25.76, "Beaches, art deco and a skyline that keeps climbing. Hurricane season makes insurance worth having.", "farmland"),
    ("mumbai", "Mumbai", 4, 72.88, 19.08, "India's commercial capital: old mill land turning into offices, sea-facing towers and some of the world's priciest square feet.", "mining"),
    ("new_york", "New York", 5, -74.01, 40.71, "Towers, brownstones and the world's busiest property market. Fortunes are made a block at a time.", "bank"),
    ("dubai", "Dubai", 6, 55.27, 25.20, "Artificial islands, record-breaking towers and marinas full of yachts. The final frontier of luxury property.", "tech"),
]
FIXED_SCALE = {"lisbon": Decimal(1), "london": Decimal(12)}

# District templates: name, tier (price multiplier), yield/day, cbd, risk, allowed property types
DISTRICT_SETS = {
    "lisbon": [
        ("Alfama", 1.0, 0.034, False, 1.0, ["apartment", "townhouse", "retail_unit", "mixed_use"]),
        ("Baixa", 1.4, 0.032, False, 0.9, ["retail_unit", "apartment", "hotel_plot", "mixed_use"]),
        ("Chiado", 2.0, 0.028, False, 0.9, ["apartment", "retail_unit", "penthouse"]),
        ("Belém", 1.6, 0.030, False, 1.1, ["villa", "townhouse", "hotel_plot"]),
        ("Cais do Sodré", 1.2, 0.035, False, 1.1, ["apartment", "retail_unit", "warehouse", "mixed_use"]),
        ("Parque das Nações", 1.8, 0.029, True, 1.0, ["office_floor", "land", "apartment", "penthouse"]),
    ],
    "london": [
        ("City of London", 2.8, 0.026, True, 0.8, ["office_floor", "land", "penthouse", "mixed_use"]),
        ("Mayfair", 3.0, 0.022, False, 0.8, ["penthouse", "townhouse", "hotel_plot"]),
        ("Kensington", 2.6, 0.024, False, 0.8, ["townhouse", "apartment", "villa"]),
        ("Shoreditch", 1.4, 0.033, False, 0.9, ["office_floor", "apartment", "retail_unit", "mixed_use"]),
        ("Camden", 1.2, 0.034, False, 0.9, ["apartment", "retail_unit", "townhouse"]),
        ("Canary Wharf", 2.2, 0.029, False, 1.0, ["office_floor", "penthouse", "apartment"]),
        ("Southwark", 1.1, 0.035, False, 1.0, ["warehouse", "apartment", "mixed_use", "retail_unit"]),
    ],
    "miami": [
        ("South Beach", 2.2, 0.029, False, 1.6, ["hotel_plot", "apartment", "retail_unit", "penthouse"]),
        ("Brickell", 2.0, 0.028, True, 1.3, ["office_floor", "land", "penthouse", "mixed_use"]),
        ("Wynwood", 1.1, 0.035, False, 1.2, ["warehouse", "retail_unit", "mixed_use"]),
        ("Coconut Grove", 1.8, 0.027, False, 1.4, ["villa", "townhouse", "apartment"]),
        ("Little Havana", 0.8, 0.039, False, 1.2, ["apartment", "retail_unit", "townhouse"]),
        ("Coral Gables", 1.6, 0.029, False, 1.2, ["villa", "townhouse", "office_floor"]),
        ("Key Biscayne", 2.6, 0.025, False, 1.7, ["villa", "hotel_plot", "land"]),
    ],
    "mumbai": [
        ("Nariman Point", 2.6, 0.027, True, 1.1, ["office_floor", "land", "penthouse", "hotel_plot"]),
        ("Bandra", 2.0, 0.030, False, 1.1, ["apartment", "penthouse", "retail_unit"]),
        ("Colaba", 1.8, 0.031, False, 1.2, ["hotel_plot", "apartment", "retail_unit"]),
        ("Juhu", 2.2, 0.028, False, 1.3, ["villa", "apartment", "hotel_plot"]),
        ("Lower Parel", 1.6, 0.032, False, 1.0, ["office_floor", "mixed_use", "apartment", "warehouse"]),
        ("Andheri", 1.0, 0.037, False, 1.0, ["apartment", "office_floor", "warehouse", "land"]),
    ],
    "new_york": [
        ("Financial District", 3.0, 0.025, True, 0.9, ["office_floor", "land", "penthouse", "mixed_use"]),
        ("Midtown", 2.8, 0.026, False, 0.8, ["office_floor", "hotel_plot", "penthouse", "retail_unit"]),
        ("SoHo", 2.4, 0.027, False, 0.9, ["retail_unit", "apartment", "mixed_use"]),
        ("Upper East Side", 2.6, 0.024, False, 0.8, ["townhouse", "penthouse", "apartment"]),
        ("Brooklyn Heights", 1.8, 0.029, False, 1.0, ["townhouse", "apartment"]),
        ("Harlem", 1.0, 0.036, False, 1.0, ["apartment", "townhouse", "retail_unit"]),
        ("Hudson Yards", 2.2, 0.028, False, 1.0, ["office_floor", "land", "mixed_use", "penthouse"]),
    ],
    "dubai": [
        ("Downtown Dubai", 3.0, 0.025, False, 0.9, ["penthouse", "hotel_plot", "apartment", "mixed_use"]),
        ("Palm Jumeirah", 3.2, 0.024, False, 1.3, ["villa", "hotel_plot", "penthouse"]),
        ("Dubai Marina", 2.5, 0.027, False, 1.1, ["apartment", "penthouse", "retail_unit"]),
        ("DIFC", 3.0, 0.025, True, 0.8, ["office_floor", "land", "mixed_use", "penthouse"]),
        ("Jumeirah", 2.6, 0.026, False, 1.0, ["villa", "townhouse", "hotel_plot"]),
        ("Business Bay", 1.8, 0.030, False, 1.0, ["office_floor", "apartment", "mixed_use", "land"]),
        ("Deira", 0.9, 0.037, False, 1.0, ["warehouse", "retail_unit", "apartment"]),
    ],
}

# Map projection used for x/y (the app draws a stylised world map over the same window).
MAP_LON = (-130.0, 160.0)
MAP_LAT = (-45.0, 65.0)
# Real gross yield (% a year) → rent a game day. 5% a year → 3% a game day (the economy runs on game days).
YIELD_PER_DAY = 0.006
REAL = json.loads((Path(__file__).parent / "data" / "cities_real.json").read_text(encoding="utf-8"))
LISBON_PRIME = next(c["primePriceUsdPerSqm"] for c in REAL["cities"] if c["id"] == "lisbon")


def project(lon, lat):
    return round((lon - MAP_LON[0]) / (MAP_LON[1] - MAP_LON[0]), 4), round((MAP_LAT[1] - lat) / (MAP_LAT[1] - MAP_LAT[0]), 4)


# id, name, icon, base price ($ at scale 1), yield multiplier, renovation (cost share, hours, value +, rent +),
# sale hours, disaster risk, businesses it can host
TYPES = [
    ("apartment", "Apartment", "Buildings", 450_000, 1.00, (0.10, 2, 0.15, 0.12), 2, 1.0, []),
    ("townhouse", "Townhouse", "House", 1_200_000, 0.95, (0.12, 3, 0.18, 0.14), 3, 1.0, []),
    ("villa", "Villa", "Villa", 3_500_000, 0.80, (0.14, 5, 0.22, 0.16), 5, 1.2, []),
    ("office_floor", "Office floor", "Briefcase", 5_000_000, 1.05, (0.10, 3, 0.14, 0.15), 3, 0.8, ["bank", "tech", "media"]),
    ("retail_unit", "Retail unit", "Store", 900_000, 1.10, (0.09, 2, 0.13, 0.15), 2, 1.0, ["retail", "restaurants", "fashion"]),
    ("warehouse", "Warehouse", "Warehouse", 4_000_000, 1.15, (0.08, 3, 0.12, 0.12), 4, 1.1, ["logistics", "factory"]),
    ("hotel_plot", "Hotel plot", "Bed", 25_000_000, 0.90, (0.16, 6, 0.25, 0.20), 6, 1.2, ["hotels"]),
    ("land", "Land", "Plot", 1_500_000, 0.50, (0.20, 4, 0.30, 0.25), 6, 0.6, []),
    ("penthouse", "Penthouse", "Tower", 6_000_000, 0.85, (0.15, 5, 0.22, 0.18), 4, 1.0, []),
    ("mixed_use", "Mixed-use block", "Buildings", 15_000_000, 1.00, (0.12, 6, 0.18, 0.16), 5, 1.0, ["retail", "restaurants", "fashion"]),
]

# Skyscraper designs: id, name, floors, stage cost multiples of the city scale, stage hours, value multiple, floor rent share, landmark boost
TOWERS = [
    ("aurelia", "The Aurelia", 40, [400_000, 1_200_000, 2_000_000, 900_000], [3, 6, 8, 4], 5_500_000, 0.032, 0.04),
    ("needle", "Harbour Needle", 55, [600_000, 1_800_000, 3_000_000, 1_400_000], [4, 7, 10, 5], 8_000_000, 0.031, 0.05),
    ("prism", "The Prism", 48, [500_000, 1_500_000, 2_600_000, 1_100_000], [3, 6, 9, 5], 6_800_000, 0.032, 0.045),
    ("helix", "Helix Tower", 64, [800_000, 2_400_000, 3_800_000, 1_800_000], [4, 8, 12, 6], 10_500_000, 0.030, 0.06),
    ("monarch", "Monarch Plaza", 36, [350_000, 1_000_000, 1_700_000, 800_000], [2, 5, 7, 4], 4_600_000, 0.033, 0.035),
    ("spire", "Summit Spire", 80, [1_100_000, 3_200_000, 5_200_000, 2_400_000], [5, 9, 14, 7], 14_500_000, 0.029, 0.07),
    ("lumen", "Lumen Works", 44, [450_000, 1_300_000, 2_300_000, 1_000_000], [3, 6, 8, 4], 6_100_000, 0.032, 0.04),
    ("tidewater", "Tidewater Tower", 52, [550_000, 1_700_000, 2_800_000, 1_300_000], [4, 7, 9, 5], 7_400_000, 0.031, 0.05),
]


def ev(eid, text, *choices):
    return {"id": eid, "text": text, "choices": [dict(label=c[0], **c[1]) for c in choices]}


# Effects: costRent / gainRent = multiples of one 6-hour rent; condition = permille change; vacancyHours = tenant leaves;
# boostDays = rent +10 % for that many days; costValue = share of the property value.
EVENTS = [
    ev("late_rent", "The tenant of your {type} in {district} is a week late with the rent.",
       ("Give them more time", {"gainRent": 0.0, "boostDays": 1.0}), ("Charge a late fee", {"gainRent": 0.4}), ("Start eviction", {"vacancyHours": 8})),
    ev("leak", "A pipe burst in your {type} in {district}.",
       ("Call a plumber now", {"costRent": 0.8}), ("Patch it yourself", {"costRent": 0.2, "condition": -60})),
    ev("long_lease", "A reliable tenant offers a long lease on your {type} in {district} if you take 5 % off.",
       ("Accept", {"boostDays": -2.0, "condition": 0, "gainRent": 1.5}), ("Decline", {})),
    ev("noise", "Neighbours complain about noise from your {type} in {district}.",
       ("Soundproof the walls", {"costRent": 1.0, "boostDays": 1.0}), ("Warn the tenant", {}), ("Ignore it", {"condition": -40})),
    ev("pet", "Your tenant in {district} asks to keep a large dog.",
       ("Allow it, with a deposit", {"gainRent": 0.3, "condition": -30}), ("Refuse", {})),
    ev("sublet", "You discover your {type} in {district} is being sublet without permission.",
       ("Take a cut", {"gainRent": 0.6, "condition": -40}), ("End the sublet", {"vacancyHours": 4})),
    ev("roof", "Storm damage to the roof of your {type} in {district}.",
       ("Full repair", {"costRent": 1.5}), ("Temporary fix", {"costRent": 0.4, "condition": -80})),
    ev("upgrade_request", "Your tenant in {district} asks for a new kitchen and offers to pay more rent.",
       ("Install it", {"costRent": 2.0, "boostDays": 3.0, "condition": 40}), ("Not now", {})),
    ev("film_crew", "A film crew wants to shoot at your {type} in {district} for a day.",
       ("Say yes", {"gainRent": 1.2, "condition": -20}), ("Say no", {})),
    ev("inspection", "The city schedules a safety inspection of your {type} in {district}.",
       ("Prepare properly", {"costRent": 0.6}), ("Wing it", {"costRent": 0.0, "condition": -50})),
    ev("rent_strike", "Tenants in {district} organise a rent strike over rising prices.",
       ("Freeze rents", {"boostDays": -1.0}), ("Negotiate", {"costRent": 0.5}), ("Hold firm", {"vacancyHours": 6})),
    ev("break_in", "A break-in at your {type} in {district}.",
       ("Install new locks and cameras", {"costRent": 0.7, "boostDays": 1.0}), ("Just replace the door", {"costRent": 0.2})),
    ev("mould", "Mould is spreading in your {type} in {district}.",
       ("Treat it properly", {"costRent": 1.0}), ("Paint over it", {"costRent": 0.1, "condition": -90})),
    ev("heating", "The heating fails at your {type} in {district} in a cold snap.",
       ("Emergency repair", {"costRent": 0.9}), ("Lend heaters", {"costRent": 0.2, "condition": -30})),
    ev("referral", "Your tenant in {district} refers a friend who wants a similar place.",
       ("Thank them with a discount", {"costRent": 0.2, "boostDays": 1.0}), ("Just say thanks", {})),
    ev("art", "A local artist offers to paint a mural on your {type} in {district}.",
       ("Commission it", {"costRent": 0.5, "boostDays": 2.0}), ("Decline politely", {})),
    ev("early_exit", "Your tenant in {district} wants to leave early and offers two weeks' rent.",
       ("Accept", {"gainRent": 1.0, "vacancyHours": 4}), ("Hold them to the lease", {})),
    ev("parking", "Tenants in {district} ask for parking spaces.",
       ("Build them", {"costRent": 1.4, "boostDays": 2.5}), ("Refuse", {})),
    ev("short_stay", "A short-stay platform offers to rent your {type} in {district} by the night.",
       ("Try short stays", {"boostDays": 2.0, "condition": -60}), ("Keep long-term tenants", {})),
    ev("elevator", "The lift in your {type} in {district} breaks down.",
       ("Replace it", {"costRent": 1.6, "condition": 50}), ("Repair it", {"costRent": 0.5})),
    ev("party", "Your tenant in {district} threw a party that damaged the hallway.",
       ("Bill the tenant", {"gainRent": 0.2}), ("Fix it quietly", {"costRent": 0.3})),
    ev("solar", "An installer offers solar panels for your {type} in {district}.",
       ("Install them", {"costRent": 1.8, "boostDays": 4.0}), ("Not interested", {})),
    ev("smart_locks", "Tenants in {district} ask for smart locks.",
       ("Fit them", {"costRent": 0.4, "boostDays": 1.0}), ("Keep the old keys", {})),
    ev("charity", "A charity asks to use your vacant storage in {district} rent-free for a month.",
       ("Say yes", {"costRent": 0.0, "boostDays": 0.5}), ("Say no", {})),
    ev("tree", "A neighbour's tree fell onto your {type} in {district}.",
       ("Repair and claim from the neighbour", {"costRent": 0.4}), ("Repair it all yourself", {"costRent": 1.0, "condition": 30})),
    ev("lawsuit_threat", "A former tenant in {district} threatens to sue over their deposit.",
       ("Settle", {"costRent": 0.6}), ("Fight it", {"costRent": 0.2, "boostDays": -0.5})),
    ev("renovation_neighbor", "Construction next door is disturbing your tenants in {district}.",
       ("Offer a rent discount", {"boostDays": -1.0}), ("Do nothing", {"vacancyHours": 3})),
    ev("energy_rating", "New rules require a better energy rating for your {type} in {district}.",
       ("Upgrade insulation", {"costRent": 1.2, "condition": 60}), ("Apply for an exemption", {"costRent": 0.2})),
    ev("rodents", "Tenants in {district} report mice.",
       ("Call pest control", {"costRent": 0.3}), ("Set traps yourself", {"condition": -30})),
    ev("cleaning", "Tenants in {district} ask for a cleaning service in the shared areas.",
       ("Hire cleaners", {"costRent": 0.5, "boostDays": 1.5}), ("Leave it to the tenants", {"condition": -20})),
    ev("wifi", "Tenants in {district} complain about slow internet.",
       ("Install fibre", {"costRent": 0.6, "boostDays": 2.0}), ("Shrug", {})),
    ev("viewing", "A buyer asks to view your {type} in {district}: they love it.",
       ("Invite them for a viewing", {"boostDays": 0.0, "gainRent": 0.2}), ("Not selling", {})),
    ev("historic", "Your {type} in {district} might be listed as a historic building.",
       ("Support the listing", {"boostDays": 2.0, "costRent": 0.3}), ("Object", {"costRent": 0.5})),
    ev("window", "A window shattered at your {type} in {district}.",
       ("Replace it today", {"costRent": 0.3}), ("Board it up", {"condition": -40})),
    ev("flood_risk", "Forecasters warn of flooding near {district}.",
       ("Sandbags and pumps", {"costRent": 0.6}), ("Hope for the best", {"condition": -70})),
    ev("community", "Residents in {district} ask you to sponsor a street festival.",
       ("Sponsor it", {"costRent": 0.5, "boostDays": 2.0}), ("Decline", {})),
    ev("rent_review", "It is time for the annual rent review on your {type} in {district}.",
       ("Small rise", {"gainRent": 0.5}), ("Keep it flat", {"boostDays": 1.0})),
    ev("furnish", "A tenant in {district} would pay more for a furnished place.",
       ("Furnish it", {"costRent": 1.0, "boostDays": 2.5}), ("Leave it unfurnished", {})),
    ev("balcony", "Your tenant in {district} wants permission to enclose the balcony.",
       ("Allow it", {"condition": 30}), ("Refuse", {})),
    ev("insurance_audit", "An insurer offers a free risk audit of your {type} in {district}.",
       ("Accept the audit", {"costRent": 0.1, "condition": 20}), ("Skip it", {})),
]


def main(repo: Path) -> None:
    rng = random.Random(SEED)
    cities, districts = [], []
    for rc in REAL["cities"]:
        cid, name, chapter, lon, lat, desc = rc["id"], rc["name"], rc["chapter"], rc["longitude"], rc["latitude"], rc["description"]
        x, y = project(lon, lat)
        scale = (Decimal(rc["primePriceUsdPerSqm"]) / Decimal(LISBON_PRIME)).quantize(Decimal("0.01"))
        cities.append({
            "id": cid, "name": name, "chapter": chapter, "x": x, "y": y, "longitude": lon, "latitude": lat, "description": desc, "priceScale": f"{scale}",
            "periodDays": round(rng.uniform(6.0, 12.0), 2), "amplitude": round(rng.uniform(0.02, 0.05), 3), "phase": round(rng.uniform(0, 2 * math.pi), 3),
        })
        for n in rc["neighbourhoods"]:
            dname, tier, cbd, risk, types = n["name"], n["tier"], n["cbd"], n["risk"], n["types"]
            yld = round(n["grossYieldPct"] * YIELD_PER_DAY, 4)
            ascii_name = unicodedata.normalize("NFKD", dname).encode("ascii", "ignore").decode()
            did = cid[:3] + "_" + re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", ascii_name.lower().replace("'", ""))).strip("_")
            districts.append({
                "id": did, "city": cid, "name": dname, "tier": tier, "yieldPerDay": yld, "cbd": cbd, "risk": risk, "types": types,
                "periodDays": round(rng.uniform(4.0, 9.0), 2), "amplitude": round(rng.uniform(0.04, 0.12), 3),
                "phase": round(rng.uniform(0, 2 * math.pi), 3), "trendPerDay": round(rng.uniform(-0.001, 0.006), 4),
                "x": round(min(0.98, max(0.02, x + rng.uniform(-0.01, 0.01))), 3), "y": round(min(0.98, max(0.02, y + rng.uniform(-0.01, 0.01))), 3),
            })
    types = [{
        "id": tid, "name": name, "icon": icon, "basePrice": f"{price}.00", "yieldMultiplier": ym,
        "renovationCost": r[0], "renovationHours": r[1], "renovationValue": r[2], "renovationRent": r[3],
        "saleHours": sh, "risk": risk, "hosts": hosts,
    } for tid, name, icon, price, ym, r, sh, risk, hosts in TYPES]
    towers = [{
        "id": tid, "name": name, "floors": floors, "stageCosts": [f"{c * 150}.00" for c in costs], "stageHours": hours,
        "stageNames": ["Foundation", "Structure", "Floors", "Fit-out"], "value": f"{value * 150}.00",
        "floorRentPerDay": rent, "landmarkBoost": boost,
    } for tid, name, floors, costs, hours, value, rent, boost in TOWERS]
    config = {
        "transactionCost": 0.03,
        "propertyTaxPerDay": 0.002,
        "insurancePerDay": 0.0005,
        "mortgageSpreadPerDay": 0.003,
        "mortgageLtvOptions": [0.0, 0.25, 0.5, 0.6, 0.75],
        "foreclosureLtv": 0.95,
        "listingRefreshHours": 6,
        "listingsMin": 3,
        "listingsMax": 5,
        "distressedChance": 0.25,
        "distressedDiscountMin": 0.70,
        "distressedDiscountMax": 0.85,
        "disasterChancePer6h": 0.004,
        "eventChancePer6h": 0.03,
        "maxQueuedEvents": 3,
        "unresolvedRentFactor": 0.8,
        "gentrificationChancePerDay": 0.03,
        "gentrificationBoost": 0.08,
        "repairCost": 0.06,
        "repairHours": 2,
        "maxRenovations": 3,
        "maxProperties": 150,
        "hostedUpkeepFactor": 0.9,
        "towerConstructionDiscount": 0.8,
        "unlocks": {"estates": 2, "mortgages": 3, "skyscrapers": 5},
        "macroAmplitude": {"boom": 0.06, "expansion": 0.02, "slowdown": -0.03, "recession": -0.08},
    }
    out = {"config": config, "cities": cities, "districts": districts, "types": types, "towers": towers, "events": EVENTS}
    path = repo / "content/data/estates.json"
    path.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {path}: {len(cities)} cities, {len(districts)} districts, {len(types)} types, {len(towers)} towers, {len(EVENTS)} events")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

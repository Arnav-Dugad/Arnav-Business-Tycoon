"""Generates the Chapter 2–7 businesses (15 module mechanics) and appends them after the hand-tuned first three.

Prices follow the ladder established by the Phase 4 simulator (docs/balance/TUNING_LOG.md): each business costs
~11× the previous to level and earns ~8× more, with growth creeping up from 1.152 to 1.18. The simulator retunes
these numbers afterwards through knob files.

Usage: python -I tools/content/gen_businesses.py <repo-root>
"""
import json
import os
import sys
from decimal import Decimal, getcontext

getcontext().prec = 80  # late-game prices exceed 1e29
from pathlib import Path

# id, name, tagline, starterName, chapter, sector, icon, kind, tier names (7), managers [(name, title, policy, trait)×4],
# expansions [(id, name, description)×3], params
B = [
    ("farmland", "Farmland", "Plant smart, sell smarter.", "Market Garden", 2, "agriculture", "Wheat", "farmland",
     ["Family Farm", "Estate Orchards", "Valley Holdings", "Agri Cooperative", "Harvest Group", "Breadbasket Co.", "Farmland Empire"],
     [("Hollis Ferraro", "Farm Manager", "Safe", "Never bets the harvest."), ("Priya Nandakumar", "Agronomist", "Balanced", "Reads soil like a ledger."),
      ("Joaquín Ellery", "Commodities Lead", "Aggressive", "Plants for the price spike."), ("Annika Thorsby", "Head of Agriculture", "Balanced", "Turns weather into profit.")],
     [("irrigation", "Irrigation Network", "Steady water, steadier yields."), ("grain_silos", "Grain Silos", "Store the harvest, sell at the peak."), ("agri_lab", "Agri-Science Lab", "Seeds bred for your fields.")], {}),
    ("factory", "Factories", "Turn raw materials into everything.", "Workshop", 2, "industrial", "Factory", "factory",
     ["Machine Shop", "Assembly Plant", "Industrial Park", "Manufacturing Group", "Advanced Fabrication", "Industrial Giant", "Manufacturing Empire"],
     [("Dmitri Halvorsen", "Shift Supervisor", "Safe", "Keeps the line moving."), ("Grace Achterberg", "Plant Manager", "Balanced", "Finds the bottleneck first."),
      ("Rohan Castellanos", "Operations Director", "Aggressive", "Runs every line flat out."), ("Mei-Lin Ostrova", "Chief Manufacturing Officer", "Balanced", "Lean to the last bolt.")],
     [("robotics_cell", "Robotics Cell", "Arms that never tire."), ("supplier_park", "Supplier Park", "Your suppliers move in next door."), ("smart_foundry", "Smart Foundry", "Self-tuning production.")], {}),
    ("fashion", "Fashion House", "Call the trend before it calls you.", "Atelier", 2, "consumer", "Hanger", "fashion",
     ["Boutique Label", "Runway Label", "Design House", "Fashion Group", "Luxury Maison", "Style Conglomerate", "Fashion Empire"],
     [("Céleste Moreau-Vance", "Creative Lead", "Safe", "Classics that always sell."), ("Tariq Bellamy", "Merchandiser", "Balanced", "Follows the hints, hedges the rest."),
      ("Ines Varga", "Trend Director", "Aggressive", "All in on the next big thing."), ("Lucian Abara", "Creative Director", "Balanced", "Sees seasons coming.")],
     [("flagship_salon", "Flagship Salon", "A showroom on the grand boulevard."), ("runway_week", "Runway Week Show", "Your collection, the city's attention."), ("couture_atelier", "Couture Atelier", "Made-to-measure for the few.")], {}),
    ("mining", "Mining", "Fortunes lie below.", "Prospecting Claim", 3, "materials", "Pickaxe", "mining",
     ["Open Pit", "Shaft Mine", "Mining District", "Ore Consortium", "Deep Works", "Minerals Group", "Mining Empire"],
     [("Bram Oyelaran", "Pit Boss", "Safe", "Safety first, ore second."), ("Saskia Lindqvist-Rao", "Geologist", "Balanced", "Surveys before every claim."),
      ("Hector Nakamura", "Mine Director", "Aggressive", "Digs deeper than anyone."), ("Olufemi Carrow", "Chief Mining Officer", "Balanced", "Grade over gamble.")],
     [("ore_crusher", "Ore Crusher", "More ore from every tonne."), ("rail_spur", "Rail Spur", "Straight from pit to port."), ("autonomous_haul", "Autonomous Haul Fleet", "Trucks that drive themselves.")], {}),
    ("energy", "Energy", "Power the city, hedge the price.", "Single Well", 3, "energy", "Flame", "energy",
     ["Well Field", "Power Station", "Regional Utility", "Energy Group", "Grid Operator", "Energy Major", "Energy Empire"],
     [("Wendell Asante", "Field Operator", "Safe", "Locks in the price."), ("Freya Kowalczyk", "Trading Desk Lead", "Balanced", "Half hedged, half nerve."),
      ("Nikolai Brennan", "Spot Trader", "Aggressive", "Lives on the spot price."), ("Amaya Sorensen", "Chief Energy Officer", "Balanced", "Keeps the lights and margins on.")],
     [("solar_farm", "Solar Farm", "Free fuel, every sunny day."), ("storage_banks", "Grid Storage Banks", "Sell power when it is scarce."), ("fusion_pilot", "Fusion Pilot Plant", "The future, plugged in.")], {}),
    ("hotels", "Hotels & Resorts", "Every room a little kingdom.", "Guest House", 3, "hospitality", "Bed", "hotels",
     ["Boutique Hotel", "City Hotel", "Resort", "Hotel Group", "Luxury Collection", "Resorts Worldwide", "Hospitality Empire"],
     [("Marguerite Osei", "Front Office Manager", "Safe", "A full house beats a high rate."), ("Callum Ferreira", "Revenue Manager", "Balanced", "Prices to the season."),
      ("Yasmin Delacroix", "Director of Sales", "Aggressive", "Premium or nothing."), ("Takeshi Abernathy", "Chief Hospitality Officer", "Balanced", "Every guest, every night.")],
     [("rooftop_pool", "Rooftop Pool", "The best view in town."), ("convention_centre", "Convention Centre", "Fill rooms with every conference."), ("private_island_resort", "Private Island Resort", "Where the wealthy go to vanish.")], {}),
    ("bank", "Banking", "Money that makes money.", "Credit Union", 4, "finance", "Bank", "bank",
     ["Savings Bank", "Commercial Bank", "Regional Bank", "Banking Group", "Investment Bank", "Global Bank", "Banking Empire"],
     [("Harriet Vandermeer", "Branch Manager", "Safe", "Lends to the reliable."), ("Kwame Lindberg", "Credit Officer", "Balanced", "Risk priced right."),
      ("Sebastian Okoro", "Head of Lending", "Aggressive", "Every applicant is an opportunity."), ("Ingrid Mbeki-Shaw", "Chief Risk Officer", "Balanced", "Calm in every cycle.")],
     [("private_banking", "Private Banking Desk", "Serve the very wealthy."), ("trading_floor", "Trading Floor", "Markets, all day long."), ("clearing_house", "Clearing House", "Every payment passes through you.")], {}),
    ("construction", "Construction", "Build the skyline.", "Builders' Yard", 4, "industrial", "Crane", "construction",
     ["General Contractor", "Civil Engineering", "Developer", "Construction Group", "Infrastructure Builder", "Megaproject Group", "Construction Empire"],
     [("Doyle Mancini", "Site Foreman", "Safe", "Wins the safe bids."), ("Leilani Hart", "Estimator", "Balanced", "Bids what it is worth."),
      ("Viktor Asamoah", "Bid Director", "Aggressive", "Holds out for the rich contracts."), ("Paloma Strand", "Chief Construction Officer", "Balanced", "On time, on budget.")],
     [("tower_cranes", "Tower Crane Fleet", "Build taller, faster."), ("precast_plant", "Precast Plant", "Buildings made in sections."), ("design_studio", "Architecture Studio", "Landmarks people pay to see.")], {}),
    ("media", "Media", "Own the conversation.", "Local Radio", 4, "media", "Film", "media",
     ["Regional Network", "Broadcast Network", "Streaming Service", "Media Group", "Studio Empire", "Global Network", "Media Empire"],
     [("Corinne Adeyemi", "Programme Manager", "Safe", "Reliable hits, every season."), ("Felix Marchetti-Wu", "Commissioning Editor", "Balanced", "Backs quality."),
      ("Rana Holloway", "Head of Content", "Aggressive", "Swings for the blockbuster."), ("Anders Okafor", "Chief Content Officer", "Balanced", "Knows what audiences want.")],
     [("sound_stages", "Sound Stages", "Film everything in-house."), ("streaming_app", "Streaming App", "Your channels in every pocket."), ("film_studio_lot", "Studio Lot", "Blockbusters on your back lot.")], {}),
    ("tech", "Technology", "Ship the future.", "Garage Start-up", 5, "technology", "Chip", "tech",
     ["Software House", "Product Company", "Platform Company", "Tech Group", "Innovation Leader", "Tech Giant", "Technology Empire"],
     [("Zoe Kallinikos", "Engineering Manager", "Safe", "Ships when it is ready."), ("Arjun Whitlock", "Product Lead", "Balanced", "Launches on the hype curve."),
      ("Lena Szabo-Grant", "VP of Product", "Aggressive", "Ship early, ship often."), ("Mateus Obi", "Chief Technology Officer", "Balanced", "Builds what lasts.")],
     [("research_campus", "Research Campus", "Bright minds, one address."), ("chip_fab", "Chip Fabrication Plant", "Make your own silicon."), ("ai_lab", "Advanced Computing Lab", "Problems solved before they exist.")], {}),
    ("telecom", "Telecom & Data", "Every signal runs through you.", "Relay Tower", 5, "technology", "Antenna", "telecom",
     ["Cell Network", "Carrier", "Data Centre Group", "National Network", "Cloud Provider", "Global Carrier", "Telecom Empire"],
     [("Reuben Castellane", "Network Engineer", "Safe", "Builds before it is needed."), ("Aiko Brannigan", "Capacity Planner", "Balanced", "Utilization just right."),
      ("Emeka Vossberg", "Growth Director", "Aggressive", "Runs the network hot."), ("Helena Marsh-Idowu", "Chief Network Officer", "Balanced", "Five nines, always.")],
     [("fibre_backbone", "Fibre Backbone", "Light-speed between every city."), ("hyperscale_dc", "Hyperscale Data Centre", "Rent compute to the world."), ("subsea_cable", "Subsea Cable", "Connect continents.")], {}),
    ("airline", "Airline", "The world, one route at a time.", "Charter Service", 6, "travel", "Plane", "airline",
     ["Regional Airline", "National Carrier", "International Airline", "Airline Group", "Flag Carrier", "Global Alliance", "Aviation Empire"],
     [("Rosalind Achebe", "Duty Manager", "Safe", "Maintenance on the dot."), ("Gustavo Lindgren", "Network Planner", "Balanced", "Fares for every season."),
      ("Simone Okwuosa", "Commercial Director", "Aggressive", "Pushes the schedule."), ("Henrik Adebayo", "Chief Airline Officer", "Balanced", "Safe skies, full cabins.")],
     [("lounge_network", "Premium Lounges", "Comfort worth paying for."), ("maintenance_hangar", "Maintenance Hangar", "Fix planes on your own schedule."), ("long_haul_fleet", "Long-Haul Fleet", "Non-stop to everywhere.")], {}),
    ("pharma", "Pharmaceuticals", "Cures, patents and patience.", "Research Lab", 6, "health", "Flask", "pharma",
     ["Biotech", "Drug Developer", "Pharma Company", "Life Sciences Group", "Global Pharma", "Health Giant", "Pharma Empire"],
     [("Dr. Imogen Fairweather", "Lab Director", "Safe", "One careful trial at a time."), ("Dr. Kofi Lindqvist", "Head of Trials", "Balanced", "A steady pipeline."),
      ("Dr. Ayasha Moreno", "R&D Director", "Aggressive", "Funds everything promising."), ("Dr. Viktor Hallam", "Chief Scientific Officer", "Balanced", "Science first, sales follow.")],
     [("trial_network", "Clinical Trial Network", "Recruit patients worldwide."), ("api_plant", "Active Ingredient Plant", "Make your own compounds."), ("genomics_centre", "Genomics Centre", "Medicine tailored to the patient.")], {}),
    ("sports", "Sports Franchise", "Win on the field, win at the box office.", "Amateur Club", 7, "entertainment", "Ball", "sports",
     ["Semi-Pro Club", "Professional Club", "League Contender", "Champions", "Dynasty Club", "Global Franchise", "Sporting Empire"],
     [("Brady Okonkwo", "Team Manager", "Safe", "Keeps the squad steady."), ("Lucia Ferrante", "Director of Football", "Balanced", "Signs smart."),
      ("Kenji Albrecht", "Head Coach", "Aggressive", "Trophies at any cost."), ("Aurelia Mensah", "Club President", "Balanced", "Builds dynasties.")],
     [("training_ground", "Training Ground", "Champions are built here."), ("new_stadium", "New Stadium", "Sixty thousand seats, sold out."), ("global_academy", "Global Academy", "Tomorrow's stars, today.")], {}),
    ("space", "Space", "The last frontier pays best.", "Rocket Lab", 7, "aerospace", "Rocket", "space",
     ["Launch Provider", "Satellite Operator", "Orbital Company", "Space Group", "Lunar Ventures", "Interplanetary Co.", "Space Empire"],
     [("Commander Ilse Varga", "Flight Director", "Safe", "Launch only when certain."), ("Dr. Tobias Achterberg", "Mission Planner", "Balanced", "Satellites first."),
      ("Rhiannon Okeke", "Head of Missions", "Aggressive", "Shoots for the asteroid."), ("Dr. Malik Fontaine", "Chief Space Officer", "Balanced", "Reaches for the stars, carefully.")],
     [("launch_complex", "Launch Complex", "Your own pad on the coast."), ("reusable_boosters", "Reusable Boosters", "Rockets that land and fly again."), ("orbital_station", "Orbital Station", "A home above the clouds.")], {}),
]

TIER_LEVELS = [10, 25, 50, 100, 150, 250, 400]
# Index in B of the first compressed business (Banking) and its unlock step (TUNING_LOG p11b).
TOP_FROM = int(os.environ.get("ABT_TOP_FROM", "6"))
TOP_UNLOCK = Decimal(os.environ.get("ABT_TOP_UNLOCK", "3.6"))
TIER_MULTS = ["1.5", "2", "2", "2.5", "2.5", "3", "3"]


def money(d: Decimal) -> str:
    return f"{d.quantize(Decimal('0.01'))}"


def main(repo: Path) -> None:
    path = repo / "content/data/businesses.json"
    existing = json.loads(path.read_text(encoding="utf-8"))
    keep = [b for b in existing if b["id"] in ("retail", "restaurants", "logistics")]
    # Ladder anchored on Logistics (tuned in Phase 4).
    base_cost, revenue, unlock, growth = Decimal("18000"), Decimal("90"), Decimal("150000"), Decimal("1.150")
    out = list(keep)
    for i, (bid, name, tagline, starter, chapter, sector, icon, kind, tiers, managers, expansions, params) in enumerate(B):
        # Gentle steps through Chapter 2; steeper from Mining onward so the late chapters don't cascade (TUNING_LOG p5).
        late = i >= 3
        # From Banking on the ladder is compressed (TUNING_LOG p11): once every business is open, growth slows to
        # ~0.4 decades a day, so ×11.5 steps put Space and the final goal days out of reach. Unlock, level cost and
        # revenue shrink by the same ratio, so each business still pays back in the same time (no cascade).
        top = i >= TOP_FROM
        squeeze = TOP_UNLOCK / Decimal("11.5") if top else Decimal(1)
        base_cost *= (Decimal("10.5") if late else Decimal("9.5")) * squeeze
        revenue *= Decimal("8.5") * squeeze
        unlock *= (Decimal("11.5") if late else 10) * squeeze
        growth = min(Decimal("1.180"), growth + Decimal("0.002"))
        mgr_mult = [Decimal(13), Decimal(2000), Decimal(700000), Decimal(270000000)]
        exp = [(5, Decimal(10), 600, "1.25"), (20, Decimal(1300), 2400, "1.5"), (45, Decimal(160000), 9000, "2")]
        out.append({
            "id": bid, "starterName": starter, "name": name, "tagline": tagline, "chapter": chapter, "sector": sector, "icon": icon,
            "unlockCost": money(unlock), "baseCost": money(base_cost), "costGrowth": f"{growth}", "baseRevenue": money(revenue),
            "upkeep": "0.20", "storageSeconds": 60,
            "tiers": [{"level": lv, "multiplier": m, "name": tn, "description": f"{tn}. Output ×{m}."} for lv, m, tn in zip(TIER_LEVELS, TIER_MULTS, tiers)],
            "managers": [
                {"id": f"{bid}_m{k + 1}", "name": mn, "title": mt, "stars": k + 2, "cost": money(unlock * mgr_mult[k]), "policy": pol, "trait": tr}
                for k, (mn, mt, pol, tr) in enumerate(managers)
            ],
            "expansions": [
                {"id": eid, "name": en, "description": ed, "requiresLevel": req, "cost": money(unlock * mult), "durationSeconds": dur, "multiplier": em}
                for (eid, en, ed), (req, mult, dur, em) in zip(expansions, exp)
            ],
            "mechanic": {"type": "module", "kind": kind, "params": params},
        })
    path.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {len(out)} businesses")
    # Conglomerate fees scale with their most expensive member (×25 its unlock cost).
    unlocks = {b["id"]: Decimal(b["unlockCost"]) for b in out}
    cpath = repo / "content/data/conglomerates.json"
    congs = json.loads(cpath.read_text(encoding="utf-8"))
    for c in congs:
        c["fee"] = money(max(unlocks[m] for m in c["members"]) * 25)
    cpath.write_text(json.dumps(congs, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"updated {len(congs)} conglomerate fees")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

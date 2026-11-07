"""Generates content/data/luxury.json: ~300 luxury items, showrooms, collection sets, Status tiers, auction bidders
(docs/GDD.md §8, docs/ECONOMY.md §7).

Names follow GDD §1.1: parody makers and models (puns, never exact real names); islands and mansions sit in real
places; paintings and sculptures are real public-domain museum works (title and artist as credited), read from
tools/art/aic_works.json written by tools/art/fetch_aic.py.

Usage: python -I tools/content/gen_luxury.py <repo-root>
"""
import json
import math
import random
import sys
from pathlib import Path

SEED = 8080808

# category id, name, showroom, icon, drift/day, upkeep/day, chapter, art cycle
CATEGORIES = [
    ("car", "Cars", "garage", "Car", -0.005, 0.0005, 2, False),
    ("watch", "Watches", "vault", "Watch", 0.0, 0.0, 2, False),
    ("wine", "Wine", "vault", "Wine", 0.002, 0.0, 2, False),
    ("jewelry", "Jewellery", "vault", "Gem", 0.001, 0.0, 2, False),
    ("jet", "Private jets", "hangar", "Plane", -0.004, 0.003, 3, False),
    ("helicopter", "Helicopters", "hangar", "Heli", -0.004, 0.003, 3, False),
    ("yacht", "Yachts", "marina", "Yacht", -0.003, 0.003, 3, False),
    ("painting", "Paintings", "gallery", "Frame", 0.004, 0.0, 3, True),
    ("sculpture", "Sculptures & antiques", "gallery", "Bust", 0.003, 0.0, 3, True),
    ("island", "Private islands", "residences", "Island", 0.003, 0.001, 4, False),
    ("mansion", "Mansions", "residences", "Mansion", 0.002, 0.001, 4, False),
]

SHOWROOMS = [
    ("garage", "Garage", "Car", 6), ("vault", "Vault", "Gem", 12), ("hangar", "Hangar", "Plane", 3),
    ("marina", "Marina", "Yacht", 3), ("gallery", "Gallery", "Frame", 10), ("residences", "Residences", "Mansion", 3),
]

# Display names for models (owner rule, docs/GDD.md §1.1: brands AND models are parodies, never the exact real name).
# Item ids are built from the original strings above and must not change (saves, sets and auctions use them), so the
# parody name is applied only to the displayed name, after the id is made.
PARODY_MODELS = {
    ("Ferraro", "Roma GT"): "Romana GT", ("Ferraro", "Dino 246 GTS"): "Dinamo 264 GTS",
    ("Lamborgotti", "Countach '74"): "Countash '74",
    ("Porch", "911 Carrera S"): "919 Carrero S", ("Porch", "Taycan Turbo"): "Tycan Turbo", ("Porch", "918 Spyder"): "919 Spyda",
    ("Porch", "356 Speedster"): "365 Speedstar",
    ("Bugotti", "Chiron Sport"): "Chirone Sport", ("Bugotti", "Veyron Grand"): "Vayron Grand", ("Bugotti", "Type 35 '27"): "Type 53 '27",
    ("Rolls-Royts", "Ghost"): "Ghostly", ("Rolls-Royts", "Cullinan"): "Cullinon", ("Rolls-Royts", "Phantom Drophead"): "Phantasm Drophead",
    ("Rolls-Royts", "Silver Ghost '09"): "Silver Wraithe '09",
    ("Bentlee", "Continental GT"): "Continentale GT", ("Bentlee", "Bentayga"): "Bentaygo", ("Bentlee", "Blower '29"): "Blowr '29",
    ("Aston Martini", "DB12"): "DM12", ("Aston Martini", "Vantage"): "Vantaggio", ("Aston Martini", "DB5 '64"): "DM5 '64",
    ("McLaron", "Artura"): "Arturo", ("McLaron", "750S"): "705S", ("McLaron", "Elva"): "Elvah", ("McLaron", "F1 '94"): "F-Uno '94",
    ("Mercedeez", "G 63"): "G 36", ("Mercedeez", "SL 63"): "SL 36", ("Mercedeez", "AMG One"): "AMZ One", ("Mercedeez", "300 SL Gullwing"): "330 SL Gullwing",
    ("BMV", "M5 Touring"): "MV5 Touring", ("BMV", "i7 Executive"): "iSeven Executive", ("BMV", "507 Roadster"): "570 Roadster",
    ("Maserotti", "MC20"): "MC21", ("Maserotti", "GranTurismo"): "GrandeTurismo", ("Maserotti", "A6GCS '54"): "A6GCX '54",
    ("Kingsegg", "Jesko Absolut"): "Jesper Absolut", ("Kingsegg", "Gemera"): "Gemerra",
    ("Pagano", "Huayra R"): "Huayro R", ("Pagano", "Zonda Cinque"): "Zondo Cinque",
    ("Lotos", "Emira"): "Emiro", ("Lotos", "Evija"): "Evyja", ("Lotos", "Elan '66"): "Elann '66",
    ("Tesler", "Model S Plaid"): "Model Z Plaid", ("Tesler", "Roadster Next"): "Roadstar Next",
    ("Jagwar", "F-Type R"): "Eff-Type R", ("Jagwar", "E-Type '61"): "Ee-Type '61", ("Jagwar", "XJ220"): "XJ202",
    ("Alpina Romeo", "Giulia GTAm"): "Giula GTAm", ("Alpina Romeo", "33 Stradale"): "34 Stradale",
    ("Fjord", "GT '05"): "GTX '05", ("Fjord", "Mustang Dark Horse"): "Mustank Dark Horse", ("Fjord", "GT40 '66"): "GT41 '66",
    ("Shevvy", "Corvette Z06"): "Corvetta Z60", ("Shevvy", "Bel Air '57"): "Belle Air '57",
    # Jets and helicopters (real type designations are swapped for invented ones).
    ("Gulfstreem", "G700"): "GS-770", ("Gulfstreem", "G280"): "GS-282", ("Gulfstreem", "G800"): "GS-880",
    ("Bombardeer", "Global 8000"): "Globale 8800", ("Bombardeer", "Challenger 3500"): "Challenjer 3550", ("Bombardeer", "Global 6500"): "Globale 6600",
    ("Dassoult", "Falcon 10X"): "Faulcon 11X", ("Dassoult", "Falcon 2000LXS"): "Faulcon 2200LX",
    ("Embrayer", "Praetor 600"): "Praetorian 660", ("Embrayer", "Phenom 300E"): "Phenomen 330",
    ("Cessno", "Citation Longitude"): "Citatio Latitude", ("Cessno", "Citation CJ4 Gen2"): "Citatio CJ5",
    ("Pilatis", "PC-24"): "PX-42", ("Hondajet-ish", "Elite II"): "Elitto II", ("Boing", "BBJ 787"): "BBJ Dreamline 788",
    ("Airbuzz", "ACJ TwoTwenty"): "ACJ TwoTwentyTwo",
    ("Airbuzz", "ACH160"): "AZ-616", ("Airbuzz", "H145"): "AZ-541", ("Belle", "525 Relentless"): "552 Restless", ("Belle", "505"): "550",
    ("Leonardi", "AW169"): "LV-196", ("Leonardi", "AW139 VIP"): "LV-193 VIP", ("Sikorski", "S-76D"): "SK-67", ("Robinsun", "R66"): "RS-88",
    # Watches, wine and yachts that kept a real model or vessel name.
    ("Audemars Pigeot", "Code 11.95"): "Kode 11.95", ("Cartyer", "Tank Francaise"): "Tankette Francaise", ("Cartyer", "Crash Londres"): "Krash Londres",
    ("Richard Mill", "RM 27-04"): "RX 72-04", ("Richard Mill", "RM 11"): "RX 111", ("Tag Hoyer", "Carrera Glassbox"): "Carrero Glassbox",
    ("Breguette", "Classique Tourbillon"): "Classico Tourbillon",
    ("Heessen", "Galactica 50m"): "Galactico 50m",
}

# Makers that kept a real name (displayed names only; ids are made before these apply).
PARODY_MAKERS = {"Masseto": "Massetto", "Vega Sicilia": "Vega Sicillia"}

# Parody car makers: (maker, [(model, price, classic)])
CARS = [
    ("Ferraro", [("Roma GT", 240_000, False), ("SF-91 Spyder", 520_000, False), ("Testarossi '86", 180_000, True), ("Dino 246 GTS", 420_000, True)]),
    ("Lamborgotti", [("Urus Cub", 230_000, False), ("Huracano EVO", 280_000, False), ("Aventadoro SVJ", 520_000, False), ("Countach '74", 900_000, True)]),
    ("Porch", [("911 Carrera S", 140_000, False), ("Taycan Turbo", 190_000, False), ("918 Spyder", 1_600_000, False), ("356 Speedster", 380_000, True)]),
    ("Bugotti", [("Chiron Sport", 3_200_000, False), ("Veyron Grand", 1_900_000, False), ("Type 35 '27", 2_400_000, True)]),
    ("Rolls-Royts", [("Ghost", 340_000, False), ("Cullinan", 380_000, False), ("Phantom Drophead", 600_000, False), ("Silver Ghost '09", 2_100_000, True)]),
    ("Bentlee", [("Continental GT", 240_000, False), ("Bentayga", 210_000, False), ("Blower '29", 3_000_000, True)]),
    ("Aston Martini", [("DB12", 250_000, False), ("Vantage", 190_000, False), ("Valkyrion", 3_500_000, False), ("DB5 '64", 1_100_000, True)]),
    ("McLaron", [("Artura", 240_000, False), ("750S", 330_000, False), ("Elva", 1_700_000, False), ("F1 '94", 18_000_000, True)]),
    ("Mercedeez", [("G 63", 180_000, False), ("SL 63", 190_000, False), ("AMG One", 2_700_000, False), ("300 SL Gullwing", 1_500_000, True)]),
    ("BMV", [("M5 Touring", 120_000, False), ("i7 Executive", 135_000, False), ("507 Roadster", 2_300_000, True)]),
    ("Maserotti", [("MC20", 220_000, False), ("GranTurismo", 175_000, False), ("A6GCS '54", 1_800_000, True)]),
    ("Kingsegg", [("Jesko Absolut", 3_400_000, False), ("Gemera", 1_900_000, False)]),
    ("Pagano", [("Huayra R", 3_100_000, False), ("Zonda Cinque", 6_000_000, False)]),
    ("Lotos", [("Emira", 100_000, False), ("Evija", 2_200_000, False), ("Elan '66", 80_000, True)]),
    ("Tesler", [("Model S Plaid", 95_000, False), ("Roadster Next", 250_000, False), ("Cybertrike", 110_000, False)]),
    ("Jagwar", [("F-Type R", 110_000, False), ("E-Type '61", 450_000, True), ("XJ220", 600_000, True)]),
    ("Alpina Romeo", [("Giulia GTAm", 180_000, False), ("33 Stradale", 2_000_000, False)]),
    ("Fjord", [("GT '05", 400_000, True), ("Mustang Dark Horse", 70_000, False), ("GT40 '66", 9_000_000, True)]),
    ("Shevvy", [("Corvette Z06", 120_000, False), ("Bel Air '57", 120_000, True)]),
]
WATCH_MAKERS = [("Rolecks", ["Daytonna", "Submarina", "GMT-Mastr", "Day-Datum"], (12_000, 450_000)),
                ("Pateck Filippe", ["Nautilius", "Calatravva", "Grand Complicashun"], (35_000, 2_500_000)),
                ("Audemars Pigeot", ["Royal Oaky", "Code 11.95"], (30_000, 600_000)),
                ("Omegah", ["Speedmaster Moonish", "Seamastr"], (6_000, 40_000)),
                ("Cartyer", ["Tank Francaise", "Santoss", "Crash Londres"], (8_000, 250_000)),
                ("Vacheron Konstantin", ["Overseaz", "Patrimonie"], (25_000, 300_000)),
                ("Richard Mill", ["RM 27-04", "RM 11"], (180_000, 1_400_000)),
                ("Breitlinng", ["Navitymer", "Chronomatt"], (6_000, 30_000)),
                ("Hublow", ["Big Bang Unica"], (20_000, 90_000)),
                ("Jaeger-LeCoultrr", ["Reversso", "Master Grandee"], (9_000, 120_000)),
                ("A. Langer & Sohn", ["Lange Eins", "Datographh"], (35_000, 250_000)),
                ("Tag Hoyer", ["Monacko", "Carrera Glassbox"], (5_000, 15_000)),
                ("Breguette", ["Classique Tourbillon"], (60_000, 200_000))]
WINES = [("Château Margot", "Premier Grand Cru"), ("Château Lafeet", "Pauillac"), ("Domaine de la Romané", "Grand Cru"),
         ("Château Petruss", "Pomerol"), ("Penfoldz", "Grange"), ("Opus Uno", "Napa Valley"), ("Sassicaya", "Bolgheri"),
         ("Screaming Eagel", "Cabernet"), ("Dom Peryon", "Champagne"), ("Krugg", "Clos d'Ambonnay"), ("Vega Sicilia", "Único"),
         ("Château d'Yquim", "Sauternes"), ("Harlan Estait", "Napa Valley"), ("Masseto", "Toscana"), ("Biondi-Santy", "Brunello"),
         ("Château Latoor", "Pauillac"), ("Henri Jayer-ish", "Cros Parantoux"), ("Taylor's Vintij", "Port 1945"),
         ("Château Haut-Brioche", "Pessac"), ("Salon Le Mesnil-ish", "Blanc de Blancs")]
JEWELRY = [("Tiffanny & Co", "Yellow Diamond Necklace", 4_000_000), ("Harry Winstone", "Sapphire Riviera", 2_800_000),
           ("Cartyer", "Panthère Bracelet", 450_000), ("Bulgaro", "Serpenti Emerald Collar", 1_600_000),
           ("Van Cleef & Arpells", "Zip Necklace", 900_000), ("Graff-ish", "Pink Diamond Ring", 12_000_000),
           ("Chopardo", "Ruby Chandelier Earrings", 700_000), ("Boucheronne", "Peacock Brooch", 350_000),
           ("De Beerz", "Millennium-ish Star Pendant", 18_000_000), ("Mikimotto", "South Sea Pearl Strand", 250_000),
           ("Piagett", "Limelight Cuff", 180_000), ("Buccellatti", "Gold Lace Tiara", 1_100_000),
           ("Fabergé-ish", "Imperial Egg", 25_000_000), ("Graff-ish", "Blue Heart Diamond", 30_000_000),
           ("Tiffanny & Co", "Blue Book Brooch", 600_000)]
JETS = [("Gulfstreem", "G700", 75_000_000), ("Gulfstreem", "G280", 25_000_000), ("Bombardeer", "Global 8000", 78_000_000),
        ("Bombardeer", "Challenger 3500", 27_000_000), ("Dassoult", "Falcon 10X", 75_000_000), ("Dassoult", "Falcon 2000LXS", 35_000_000),
        ("Embrayer", "Praetor 600", 21_000_000), ("Embrayer", "Phenom 300E", 11_000_000), ("Cessno", "Citation Longitude", 30_000_000),
        ("Cessno", "Citation CJ4 Gen2", 11_000_000), ("Pilatis", "PC-24", 11_000_000), ("Hondajet-ish", "Elite II", 7_000_000),
        ("Boing", "BBJ 787", 250_000_000), ("Airbuzz", "ACJ TwoTwenty", 80_000_000), ("Gulfstreem", "G800", 72_000_000),
        ("Bombardeer", "Global 6500", 56_000_000)]
HELIS = [("Airbuzz", "ACH160", 20_000_000), ("Airbuzz", "H145", 11_000_000), ("Belle", "525 Relentless", 15_000_000),
         ("Belle", "505", 1_500_000), ("Leonardi", "AW169", 12_000_000), ("Leonardi", "AW139 VIP", 16_000_000),
         ("Sikorski", "S-76D", 13_000_000), ("Robinsun", "R66", 1_300_000)]
YACHTS = [("Feedship", "Lady Aurelia 94m", 250_000_000), ("Lursen", "Azura 115m", 480_000_000), ("Benotti", "Oasis 40m", 22_000_000),
          ("Oceaneco", "Black Pearl-ish 106m", 220_000_000), ("Sunseekr", "Manhattan 68", 4_000_000), ("Azimutt", "Grande 32m", 18_000_000),
          ("Heessen", "Galactica 50m", 45_000_000), ("Amels", "Limited 60", 70_000_000), ("Perini Navy", "Sailing Ketch 56m", 50_000_000),
          ("Rivaa", "Aquarama Classic", 1_200_000), ("Princess Yachtz", "X95", 15_000_000), ("Wallie", "WHY200", 30_000_000),
          ("Codecasa-ish", "Vintage 50m", 35_000_000), ("Sanlorenzo-ish", "SX112", 20_000_000), ("Lursen", "Dilbar-ish 156m", 600_000_000),
          ("Feedship", "Savannah-ish 83m", 160_000_000), ("Royal Huisman-ish", "Sea Eagle 81m", 120_000_000), ("Benotti", "Iron Lady 69m", 95_000_000),
          ("Pershing-ish", "GTX116", 16_000_000), ("Baglietto-ish", "T52", 48_000_000)]
ISLANDS = [("Coral Cay", "Exuma, Bahamas", 85_000_000), ("Isla Serena", "Belize Barrier Reef", 25_000_000),
           ("Motu Hina", "Bora Bora, French Polynesia", 60_000_000), ("Kalua Atoll", "Maldives", 120_000_000),
           ("Isola Bianca", "Lake Como, Italy", 45_000_000), ("Petit Ancre", "Grenadines", 70_000_000),
           ("Fjordholm", "Stockholm Archipelago, Sweden", 12_000_000), ("Kai Island", "Fiji", 40_000_000),
           ("Dune Key", "Florida Keys, USA", 18_000_000), ("Seychelle Rouge", "Seychelles", 150_000_000)]
MANSIONS = [("Bel Air Estate", "Los Angeles", 120_000_000), ("Kensington Palace Gardens House", "London", 90_000_000),
            ("Villa on Cap Ferrat", "Côte d'Azur", 150_000_000), ("Malibu Beach House", "Malibu", 75_000_000),
            ("Palm Jumeirah Signature Villa", "Dubai", 60_000_000), ("Billionaires' Row Penthouse", "New York", 190_000_000),
            ("Altamount Road Tower Home", "Mumbai", 200_000_000), ("Aspen Mountain Lodge", "Aspen", 45_000_000),
            ("Lake Geneva Château", "Switzerland", 110_000_000), ("Sintra Palácio", "Lisbon", 30_000_000),
            ("Hamptons Oceanfront", "Southampton", 85_000_000), ("Mayfair Townhouse", "London", 65_000_000),
            ("Star Island Compound", "Miami Beach", 95_000_000), ("Tuscan Castello", "Chianti, Italy", 35_000_000),
            ("Ibiza Clifftop Villa", "Ibiza", 28_000_000), ("Kyoto Machiya Estate", "Kyoto", 22_000_000),
            ("Peak Road Mansion", "Hong Kong", 280_000_000), ("Gstaad Chalet", "Gstaad", 70_000_000),
            ("Mustique Hilltop Villa", "Mustique", 40_000_000), ("Napa Vineyard Estate", "Napa Valley", 55_000_000)]

# Parody billionaires who bid at auctions (satire; they only bid, nothing else is said about them). GDD §1.1.
BIDDERS = [("Jeff Bozos", "steady"), ("Melon Tusk", "aggressive"), ("Mark Zuckerbird", "sniper"), ("Bill Gatesworth", "steady"),
           ("Warren Buffit", "collector"), ("Larry Ellisun", "aggressive"), ("Bernard Arnot", "collector"), ("Mukesh Ambanee", "steady"),
           ("Gautam Adanni", "aggressive"), ("Larry Pagely", "sniper"), ("Sergey Brinn", "sniper"), ("Steve Ballmore", "aggressive"),
           ("Michael Dellvin", "steady"), ("Françoise Bettencorte", "collector"), ("Carlos Slimm", "steady"), ("Amancio Orteguez", "collector"),
           ("Jensen Huangg", "aggressive"), ("Pony Mah", "sniper"), ("Alice Waltz", "collector"), ("Phil Nightly", "steady"),
           ("Ken Griffon", "sniper"), ("Opra Windfrey", "collector"), ("Rob Waltonne", "steady"), ("Zhong Shanshine", "sniper")]


def status_points(price: float) -> float:
    return round(10 * max(0.0, math.log10(price / 1000)) ** 2, 1)


def main(repo: Path) -> None:
    rng = random.Random(SEED)
    items = []

    def add(iid, name, maker, cat, price, rarity, image=None, description="", classic=False, location=""):
        name = PARODY_MODELS.get((maker, name), name)
        maker = PARODY_MAKERS.get(maker, maker)
        items.append({"id": iid, "name": name, "maker": maker, "category": cat, "price": f"{int(price)}.00", "rarity": rarity,
                      "classic": classic, "image": image, "description": description, "location": location})

    for maker, models in CARS:
        for model, price, classic in models:
            rarity = "legendary" if price >= 3_000_000 else "rare" if price >= 500_000 else "common"
            add(f"car_{maker[:4].lower()}_{model.lower().replace(' ', '_').replace(chr(39), '').replace('-', '_')}"[:40], model, maker, "car", price, rarity,
                classic=classic, description="A classic that gains value over time." if classic else "Loses a little value every day you drive it.")
    for maker, models, (lo, hi) in WATCH_MAKERS:
        for i, model in enumerate(models):
            price = round(lo * (hi / lo) ** (i / max(1, len(models) - 1)), -2)
            add(f"watch_{''.join(ch for ch in maker.lower() if ch.isalnum())[:6]}_{i}", model, maker, "watch", price, "legendary" if price >= 1_000_000 else "rare" if price >= 100_000 else "common")
    for i, (estate, label) in enumerate(WINES):
        price = round(rng.uniform(800, 60_000), -2)
        add(f"wine_{i}", f"{label}, case of 12", estate, "wine", price, "rare" if price > 20_000 else "common", description="Wine gains a little value each day in your cellar.")
    for i, (maker, name, price) in enumerate(JEWELRY):
        add(f"jewel_{i}", name, maker, "jewelry", price, "legendary" if price >= 10_000_000 else "rare" if price >= 1_000_000 else "common")
    for i, (maker, model, price) in enumerate(JETS):
        add(f"jet_{i}", model, maker, "jet", price, "legendary" if price >= 70_000_000 else "rare", description="Upkeep 0.3% of its value a day: crew, fuel and hangar fees.")
    for i, (maker, model, price) in enumerate(HELIS):
        add(f"heli_{i}", model, maker, "helicopter", price, "rare" if price >= 10_000_000 else "common", description="Upkeep 0.3% of its value a day.")
    for i, (maker, model, price) in enumerate(YACHTS):
        add(f"yacht_{i}", model, maker, "yacht", price, "legendary" if price >= 150_000_000 else "rare" if price >= 20_000_000 else "common",
            description="Upkeep 0.3% of its value a day: crew, berth and fuel.")
    for i, (name, place, price) in enumerate(ISLANDS):
        add(f"island_{i}", name, "Private island", "island", price, "legendary" if price >= 100_000_000 else "rare", location=place)
    for i, (name, place, price) in enumerate(MANSIONS):
        add(f"mansion_{i}", name, "Residence", "mansion", price, "legendary" if price >= 150_000_000 else "rare", location=place)
    works = json.loads((repo / "tools/art/aic_works.json").read_text(encoding="utf-8"))
    ranked = sorted(works, key=lambda w: -w.get("_score", 0))
    for rank, w in enumerate(ranked):
        cat = "painting" if w["kind"] == "painting" else "sculpture"
        # Most celebrated works are priced highest (log scale from ~$450M down to ~$1M).
        price = round(450_000_000 * (0.93 ** rank), -3) if cat == "painting" else round(60_000_000 * (0.85 ** rank), -3)
        price = max(price, 400_000)
        rarity = "legendary" if price >= 100_000_000 else "rare" if price >= 10_000_000 else "common"
        add(f"aic_{w['id']}", w["title"], w["artist_title"], cat, price, rarity, image=f"img/art/aic_{w['id']}.webp",
            description=f"{w.get('date_display', '')}. Public-domain work from the Art Institute of Chicago.")

    by_cat = {}
    for it in items:
        by_cat.setdefault(it["category"], []).append(it)
    # Collection sets: themed groups with cross-system bonuses.
    sets = []

    def s(sid, name, members, bonus_type, value, target="", desc=""):
        sets.append({"id": sid, "name": name, "items": members, "bonusType": bonus_type, "bonusValue": value, "bonusTarget": target, "description": desc})

    car = {it["maker"]: [] for it in by_cat["car"]}
    for it in by_cat["car"]:
        car[it["maker"]].append(it["id"])
    s("grand_tourers", "Grand Tourers", car["Aston Martini"][:2] + car["Bentlee"][:1] + car["Ferraro"][:1] + car["Maserotti"][:1], "status", 0.05, desc="+5% Status from every item")
    s("hypercars", "Hypercar Club", [i["id"] for i in by_cat["car"] if i["rarity"] == "legendary" and not i["classic"]][:5], "income", 0.04, "retail", "+4% Retail income")
    s("concours", "Concours d'Elegance", [i["id"] for i in by_cat["car"] if i["classic"]][:6], "status", 0.06, desc="+6% Status from every item")
    s("italian_job", "The Italian Job", car["Ferraro"][:2] + car["Lamborgotti"][:2] + car["Pagano"][:1], "income", 0.04, "fashion", "+4% Fashion House income")
    s("british_racing", "British Racing Green", car["McLaron"][:2] + car["Jagwar"][:2] + car["Lotos"][:1], "income", 0.03, "logistics", "+3% Logistics income")
    s("electric", "Electric Avenue", car["Tesler"] + car["Porch"][1:2] + car["Lotos"][1:2], "income", 0.04, "energy", "+4% Energy income")
    watches = [i["id"] for i in by_cat["watch"]]
    s("watch_trinity", "Holy Trinity", [i["id"] for i in by_cat["watch"] if i["maker"] in ("Pateck Filippe", "Audemars Pigeot", "Vacheron Konstantin")][:3], "dividends", 0.03, desc="+3% dividend yield")
    s("tool_watches", "Tool Watches", [i["id"] for i in by_cat["watch"] if i["maker"] in ("Rolecks", "Omegah", "Breitlinng")][:5], "income", 0.03, "factory", "+3% Factories income")
    s("dress_watches", "Black Tie", [i["id"] for i in by_cat["watch"] if i["maker"] in ("Cartyer", "Jaeger-LeCoultrr", "Breguette", "A. Langer & Sohn")][:5], "status", 0.04, desc="+4% Status")
    s("haute_horlogerie", "Haute Horlogerie", sorted(watches, key=lambda x: -float(next(i["price"] for i in items if i["id"] == x)))[:4], "luxuryDiscount", 0.03, desc="Dealers give you 3% more off")
    s("bordeaux", "Left Bank Bordeaux", [i["id"] for i in by_cat["wine"] if "Château" in i["maker"]][:5], "income", 0.04, "restaurants", "+4% Restaurants income")
    s("cellar", "The Grand Cellar", [i["id"] for i in by_cat["wine"]][5:12], "income", 0.03, "hotels", "+3% Hotels income")
    s("bubbles", "Bubbles", [i["id"] for i in by_cat["wine"] if i["maker"] in ("Dom Peryon", "Krugg", "Salon Le Mesnil-ish")], "status", 0.03, desc="+3% Status")
    s("crown_jewels", "Crown Jewels", [i["id"] for i in by_cat["jewelry"] if i["rarity"] == "legendary"], "status", 0.08, desc="+8% Status")
    s("red_carpet", "Red Carpet", [i["id"] for i in by_cat["jewelry"] if i["rarity"] != "legendary"][:6], "income", 0.04, "media", "+4% Media income")
    s("long_range", "Long Range", [i["id"] for i in by_cat["jet"] if i["rarity"] == "legendary"][:4], "income", 0.05, "airline", "+5% Airline income")
    s("light_jets", "Light Jets", [i["id"] for i in by_cat["jet"] if i["rarity"] != "legendary"][:5], "income", 0.03, "logistics", "+3% Logistics income")
    s("rotor_club", "Rotor Club", [i["id"] for i in by_cat["helicopter"]][:4], "income", 0.03, "construction", "+3% Construction income")
    s("harbor_lights", "Harbour Lights", [i["id"] for i in by_cat["yacht"] if i["rarity"] == "rare"][:5], "income", 0.05, "hotels", "+5% Hotels income")
    s("gigayachts", "Gigayachts", [i["id"] for i in by_cat["yacht"] if i["rarity"] == "legendary"][:4], "status", 0.08, desc="+8% Status")
    s("day_boats", "Day Boats", [i["id"] for i in by_cat["yacht"] if i["rarity"] == "common"][:4], "rent", 0.03, desc="+3% rent from your properties")
    paintings = [i for i in by_cat["painting"]]
    s("old_masters", "Old Masters", [i["id"] for i in paintings if any(k in i["description"] for k in ("15", "16", "17"))][:5] or [i["id"] for i in paintings][:5], "dividends", 0.03, desc="+3% dividend yield")
    s("impressionists", "Impressionists", [i["id"] for i in paintings if i["maker"] in ("Claude Monet", "Pierre-Auguste Renoir", "Edgar Degas", "Gustave Caillebotte", "Berthe Morisot", "Camille Pissarro", "Mary Cassatt")][:6], "income", 0.05, "tech", "+5% Technology income")
    s("post_impressionists", "Post-Impressionists", [i["id"] for i in paintings if i["maker"] in ("Vincent van Gogh", "Paul Cézanne", "Georges Seurat", "Paul Gauguin", "Henri de Toulouse-Lautrec")][:5], "income", 0.05, "pharma", "+5% Pharmaceuticals income")
    s("modern_wing", "The Modern Wing", [i["id"] for i in paintings][-8:-3], "income", 0.04, "telecom", "+4% Telecom & Data income")
    s("museum_quality", "Museum Quality", [i["id"] for i in paintings if i["rarity"] == "legendary"][:5], "status", 0.10, desc="+10% Status")
    s("sculpture_garden", "Sculpture Garden", [i["id"] for i in by_cat["sculpture"]][:6], "rent", 0.04, desc="+4% rent from your properties")
    s("island_hopper", "Island Hopper", [i["id"] for i in by_cat["island"]][:4], "income", 0.05, "sports", "+5% Sports Franchise income")
    s("riviera", "The Riviera", [i["id"] for i in by_cat["mansion"] if any(k in i["location"] for k in ("Côte", "Ibiza", "Como", "Italy", "Monaco"))][:3] + [by_cat["island"][4]["id"]], "income", 0.04, "bank", "+4% Banking income")
    s("global_citizen", "Global Citizen", [i["id"] for i in by_cat["mansion"] if i["location"] in ("London", "New York", "Dubai", "Mumbai", "Lisbon", "Miami Beach")][:6], "income", 0.05, "space", "+5% Space income")
    sets = [x for x in sets if len(x["items"]) >= 3]

    tiers = [("Newcomer", 0, 0.0), ("Bronze", 50, 0.03), ("Silver", 250, 0.05), ("Gold", 800, 0.07), ("Platinum", 2000, 0.09),
             ("Diamond", 4500, 0.11), ("Royal", 9000, 0.13), ("Imperial", 16000, 0.15)]
    config = {
        "dealerFee": 0.02, "dealerBuyback": 0.85, "showroomUpgradeBase": "250000.00", "showroomUpgradeGrowth": 6.0, "showroomCapacityStep": 0.5,
        "artCyclePeriodDays": 6.0, "artCycleAmplitude": 0.15,
        "auctionEveryHours": 2, "auctionMinSeconds": 2700, "auctionExtendSeconds": 30, "auctionBidIncrement": 0.05,
        "auctionOpeningShare": 0.6, "auctionBiddersMin": 3, "auctionBiddersMax": 5, "auctionValuationSd": 0.15,
        "unlocks": {"collection": 2, "auctions": 3},
    }
    out = {
        "config": config,
        "categories": [{"id": c[0], "name": c[1], "showroom": c[2], "icon": c[3], "driftPerDay": c[4], "upkeepPerDay": c[5], "chapter": c[6], "artCycle": c[7]} for c in CATEGORIES],
        "showrooms": [{"id": s_[0], "name": s_[1], "icon": s_[2], "baseCapacity": s_[3]} for s_ in SHOWROOMS],
        "items": [dict(it, statusPoints=status_points(float(it["price"]))) for it in items],
        "sets": sets,
        "tiers": [{"name": n, "points": p, "dealerDiscount": d} for n, p, d in tiers],
        "bidders": [{"id": n.lower().replace(" ", "_").replace("ç", "c").replace(".", ""), "name": n, "style": st} for n, st in BIDDERS],
    }
    path = repo / "content/data/luxury.json"
    path.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {path}: {len(items)} items, {len(sets)} sets, {len(BIDDERS)} bidders; " + ", ".join(f"{k} {len(v)}" for k, v in by_cat.items()))


if __name__ == "__main__":
    main(Path(sys.argv[1]))

"""Generates content/data/world.json: 220 rivals (parody and invented names), 8 archetypes, chapter nemeses with story
beats, 60 world events, 160+ decision cards and the finance tunables (docs/GDD.md §9–11, docs/ECONOMY.md §8).

Rival fortunes are a pure function of game time t (days since the generation began):
log10(net worth) = base + growth · G(t) + wobble · sin(...), with G(t) = early · min(t, knee) + late · max(0, t − knee).
Game time trails real time (offline earnings are capped), so the curve is calibrated on the simulator's game-time
trajectory (trajectory.csv): an active player enters the list around real day 1.5 and reaches #1 around real day 6–7
(Chapter 7); afterwards rivals slow down so a strong player stays on top.

Parody names follow GDD §1.1: clearly punning, never a real person's exact name; portraits are caricatures of invented
people that hint at a type, never a real face. Negative story beats attach to companies, never to a person.

Usage: python -I tools/content/gen_world.py <repo-root>
"""
import json
import math
import random
import sys
import unicodedata
from pathlib import Path

SEED = 9090909
CURVE = {"earlyPerDay": 1.4, "kneeDays": 4.5, "latePerDay": 0.4}

ARCHETYPES = [
    # id, name, description, growth/day, wobble (log10), aggression range, risk range
    ("shark", "Shark", "Buys rivals cheaply and strikes when a company looks weak.", 0.68, 0.10, (0.7, 0.95), (0.5, 0.8)),
    ("old_money", "Old Money", "Generations of wealth. Slow, steady and very well connected.", 0.55, 0.04, (0.2, 0.5), (0.1, 0.3)),
    ("disruptor", "Disruptor", "Builds the new thing and lets the old thing burn.", 0.74, 0.16, (0.5, 0.85), (0.6, 0.95)),
    ("collector", "Collector", "Spends on art, cars and auctions; a fierce bidder.", 0.60, 0.07, (0.3, 0.6), (0.3, 0.6)),
    ("philanthropist", "Philanthropist", "Gives much of it away and prefers partnerships to fights.", 0.56, 0.05, (0.05, 0.3), (0.2, 0.4)),
    ("gambler", "Gambler", "Bets big. Fortunes swing wildly with every deal.", 0.66, 0.28, (0.5, 0.8), (0.85, 1.0)),
    ("recluse", "Recluse", "Never gives interviews. Quietly owns more than you think.", 0.62, 0.06, (0.2, 0.5), (0.2, 0.5)),
    ("heir", "Heir", "Inherited an empire and is determined to prove they deserved it.", 0.58, 0.09, (0.3, 0.7), (0.4, 0.7)),
]

SECTORS = ["consumer", "food", "industrials", "energy", "materials", "financials", "technology", "media", "healthcare", "property", "transport", "frontier"]

# The 24 auction bidders (same names and ids as luxury.json) top the list.
# name, archetype, home city, sectors, fortune source, log10 net worth at day 0
BIDDERS = [
    ("Melon Tusk", "disruptor", "Austin", ["frontier", "technology"], "Electric cars and reusable rockets", 11.60),
    ("Jeff Bozos", "shark", "Seattle", ["consumer", "technology"], "Online everything store", 11.42),
    ("Larry Ellisun", "shark", "Lanai", ["technology"], "Database software", 11.36),
    ("Mark Zuckerbird", "disruptor", "Palo Alto", ["technology", "media"], "Social networks", 11.32),
    ("Bernard Arnot", "collector", "Paris", ["consumer"], "Luxury fashion houses", 11.26),
    ("Larry Pagely", "recluse", "Palo Alto", ["technology"], "Search engine", 11.12),
    ("Sergey Brinn", "recluse", "Los Altos", ["technology"], "Search engine", 11.09),
    ("Steve Ballmore", "gambler", "Hunts Point", ["technology", "media"], "Software and a basketball team", 11.06),
    ("Warren Buffit", "old_money", "Omaha", ["financials", "food"], "Patient value investing", 11.05),
    ("Jensen Huangg", "disruptor", "Los Altos", ["technology"], "Graphics chips", 11.02),
    ("Mukesh Ambanee", "old_money", "Mumbai", ["energy", "consumer"], "Refineries and telecom", 10.98),
    ("Bill Gatesworth", "philanthropist", "Medina", ["technology", "healthcare"], "Desktop software", 10.97),
    ("Michael Dellvin", "shark", "Austin", ["technology"], "Build-to-order computers", 10.95),
    ("Rob Waltonne", "old_money", "Bentonville", ["consumer"], "Discount supermarkets", 10.92),
    ("Amancio Orteguez", "recluse", "A Coruña", ["consumer"], "Fast fashion", 10.91),
    ("Françoise Bettencorte", "heir", "Paris", ["consumer", "healthcare"], "Cosmetics", 10.90),
    ("Alice Waltz", "collector", "Fort Worth", ["consumer"], "Discount supermarkets", 10.88),
    ("Gautam Adanni", "shark", "Ahmedabad", ["transport", "energy"], "Ports and power", 10.86),
    ("Carlos Slimm", "old_money", "Mexico City", ["media", "financials"], "Telecoms", 10.85),
    ("Zhong Shanshine", "recluse", "Hangzhou", ["food", "healthcare"], "Bottled water and vaccines", 10.76),
    ("Pony Mah", "disruptor", "Shenzhen", ["technology", "media"], "Messaging and games", 10.71),
    ("Ken Griffon", "gambler", "Miami", ["financials"], "Hedge funds", 10.62),
    ("Phil Nightly", "philanthropist", "Portland", ["consumer"], "Running shoes", 10.56),
    ("Opra Windfrey", "collector", "Montecito", ["media"], "Talk shows", 9.45),
]

# More parodies of well-known tycoons (never an exact real name).
PARODIES = [
    ("Rupert Murdock", "shark", "New York", ["media"], "Newspapers and television", 10.30),
    ("Richard Bransun", "gambler", "London", ["transport", "frontier"], "Airlines, music and space tourism", 9.60),
    ("Jack Mah", "disruptor", "Hangzhou", ["consumer", "technology"], "Online marketplaces", 10.40),
    ("Masayoshi Sunn", "gambler", "Tokyo", ["technology", "financials"], "Tech investing", 10.35),
    ("Lee Ka-shine", "old_money", "Hong Kong", ["property", "transport"], "Ports and property", 10.55),
    ("Lakshmi Mittle", "shark", "London", ["materials"], "Steel", 10.25),
    ("Ratan Tatta", "philanthropist", "Mumbai", ["industrials", "transport"], "A century-old conglomerate", 9.70),
    ("Azim Premjee", "philanthropist", "Bengaluru", ["technology"], "IT services", 10.05),
    ("Shiv Nadaar", "philanthropist", "Delhi", ["technology"], "IT services", 10.50),
    ("Savitri Jindall", "old_money", "Delhi", ["materials"], "Steel and power", 10.48),
    ("Cyrus Poonawala", "recluse", "Pune", ["healthcare"], "Vaccines", 10.38),
    ("Dilip Shangvee", "recluse", "Mumbai", ["healthcare"], "Generic medicines", 10.42),
    ("Kumar Birlah", "heir", "Mumbai", ["materials", "industrials"], "Cement and metals", 10.30),
    ("Uday Kotack", "old_money", "Mumbai", ["financials"], "Banking", 10.18),
    ("Radhakishan Damanee", "recluse", "Mumbai", ["consumer"], "Value supermarkets", 10.28),
    ("Mark Cubano", "gambler", "Dallas", ["media"], "Streaming and a basketball team", 9.80),
    ("Steve Koan", "gambler", "New York", ["financials", "media"], "Hedge funds and baseball", 10.32),
    ("Ray Dahlio", "recluse", "Greenwich", ["financials"], "Macro hedge funds", 10.25),
    ("MacKenzie Scotch", "philanthropist", "Seattle", ["consumer"], "An online retail fortune", 10.45),
    ("Jack Dorsee", "disruptor", "San Francisco", ["technology", "financials"], "Payments and microblogs", 9.65),
    ("Brian Cheski", "disruptor", "San Francisco", ["transport"], "Home-sharing app", 10.00),
    ("Travis Kalanik", "shark", "Los Angeles", ["transport"], "Ride hailing", 9.75),
    ("Reed Hastinks", "disruptor", "Los Gatos", ["media"], "Video streaming", 9.70),
    ("Daniel Eck", "disruptor", "Stockholm", ["media"], "Music streaming", 9.68),
    ("Dieter Schwartz", "recluse", "Neckarsulm", ["consumer"], "Discount supermarkets", 10.62),
    ("Klaus-Michael Kuehnly", "old_money", "Schindellegi", ["transport"], "Freight forwarding", 10.60),
    ("Gianluigi Aponti", "old_money", "Geneva", ["transport"], "Container shipping", 10.55),
    ("Giovanni Ferrera", "heir", "Brussels", ["food"], "Chocolate spread", 10.58),
    ("Alain Wertheimar", "heir", "New York", ["consumer"], "A Paris fashion house", 10.66),
    ("François Pinot", "collector", "Paris", ["consumer"], "Luxury brands and art", 10.40),
    ("Emmanuel Besnière", "recluse", "Laval", ["food"], "Dairy", 10.32),
    ("Stefan Pearsson", "heir", "Stockholm", ["consumer"], "Fashion retail", 10.30),
    ("Susanne Klatter", "heir", "Munich", ["industrials"], "Cars and chemicals", 10.42),
    ("Tadashi Yanaii", "shark", "Tokyo", ["consumer"], "Casual clothing", 10.60),
    ("Zhang Yiminng", "disruptor", "Beijing", ["technology", "media"], "Short-video app", 10.68),
    ("Colin Hwang", "disruptor", "Shanghai", ["consumer", "technology"], "Group-buying shopping app", 10.55),
    ("Robin Tseng", "disruptor", "Ningde", ["energy"], "Car batteries", 10.52),
    ("Lei Junn", "disruptor", "Beijing", ["technology"], "Smartphones", 10.40),
    ("He Xiangjiann", "old_money", "Foshan", ["industrials"], "Home appliances", 10.38),
    ("Jacqueline Marz", "heir", "McLean", ["food"], "Candy bars and pet food", 10.58),
    ("Abigail Johnsun", "heir", "Boston", ["financials"], "Mutual funds", 10.48),
    ("Thomas Peterfee", "gambler", "Palm Beach", ["financials"], "Electronic brokerage", 10.70),
    ("Stephen Schwartzmann", "shark", "New York", ["financials", "property"], "Private equity", 10.62),
    ("Len Blavatnick", "collector", "London", ["industrials", "media"], "Chemicals and music labels", 10.50),
    ("Dan Gilbertson", "old_money", "Detroit", ["financials"], "Online mortgages", 10.35),
    ("Aliko Dangotay", "shark", "Lagos", ["materials"], "Cement and refining", 10.10),
    ("Johann Ruperts", "collector", "Stellenbosch", ["consumer"], "Luxury watches and jewellery", 10.15),
    ("Nassef Sawiriss", "shark", "Cairo", ["property", "industrials"], "Construction and fertiliser", 9.90),
    ("Strive Masiyiva", "philanthropist", "London", ["media"], "Mobile networks", 9.40),
    ("Iris Fontbonna", "old_money", "Santiago", ["materials"], "Copper mines", 10.40),
    ("Jorge Paulo Lemon", "shark", "Zurich", ["food"], "Beer and burgers", 10.20),
    ("Germán Larreah", "recluse", "Mexico City", ["materials"], "Copper and railways", 10.45),
    ("Gina Rhinehart", "shark", "Perth", ["materials"], "Iron ore", 10.45),
    ("Andrew Forrester", "gambler", "Perth", ["materials", "energy"], "Iron ore and green hydrogen", 10.20),
    ("Anthony Pratte", "heir", "Melbourne", ["industrials"], "Cardboard boxes", 10.00),
    ("Lee Jae-yongg", "heir", "Seoul", ["technology"], "Electronics", 10.05),
    ("Robert Kwok", "old_money", "Hong Kong", ["food", "transport"], "Sugar and hotels", 10.05),
    ("Hiroshi Mikitanni", "disruptor", "Tokyo", ["consumer", "technology"], "Online marketplace", 9.70),
    ("David Geffern", "collector", "Los Angeles", ["media"], "Records and films", 9.95),
    ("Harold Hamme", "gambler", "Oklahoma City", ["energy"], "Shale oil", 10.25),
    ("Kelcy Warrene", "shark", "Dallas", ["energy"], "Pipelines", 9.85),
    ("Jerry Joanes", "gambler", "Dallas", ["media"], "A football franchise", 10.15),
    ("Stan Kronke", "old_money", "Denver", ["media", "property"], "Sports teams and land", 10.20),
    ("Diane Hendrix", "old_money", "Beloit", ["property"], "Building supplies", 10.30),
]

# Story rivals invented for chapter nemeses (no real counterpart).
STORY = [
    ("Vivienne Strand", "shark", "London", ["consumer", "food"], "High-street chains", 9.30),
]

# Chapter nemeses: chapter -> rival name, and three beats (intro, midpoint, finale). Beats are about companies and
# competition, never personal scandal.
NEMESES = {
    2: ("Vivienne Strand", [
        "Vivienne Strand's high-street chains have noticed your stores. \"Main Street has room for one name,\" her company says.",
        "Strand's group cuts prices on every street you trade on. Your customers are staying loyal.",
        "Strand's group sells three of its high-street sites to you. Main Street is yours.",
    ]),
    3: ("Gina Rhinehart", [
        "Gina Rhinehart's mining group is bidding for the same mineral rights you want. Raw power is a crowded room.",
        "Rhinehart's group locks up a rail line to the coast. You find another way to the port.",
        "Rhinehart's group concedes the eastern fields. Your energy empire is now too big to ignore.",
    ]),
    4: ("Abigail Johnsun", [
        "Abigail Johnsun's fund family controls the old banks you need. Old money does not open doors for newcomers.",
        "Johnsun's funds vote against your bank's licence application. The regulator approves it anyway.",
        "Johnsun's funds ask to invest in your holding. Old money now wants in.",
    ]),
    5: ("Mark Zuckerbird", [
        "Mark Zuckerbird's social network announces a copy of your newest product the same day you launch.",
        "Zuckerbird's company poaches two of your engineers. Your team ships faster without them.",
        "Zuckerbird's company quietly shuts its copycat app. Disruption has a new name: yours.",
    ]),
    6: ("Richard Bransun", [
        "Richard Bransun's airline is flying your routes at half price, with a party on every plane.",
        "Bransun's airline grounds a fleet for repairs. Your planes are full all week.",
        "Bransun's airline offers you its premium routes. Altitude reached.",
    ]),
    7: ("Melon Tusk", [
        "Melon Tusk posts that your rockets are \"cute\". His company has booked every launch window this year.",
        "Tusk's company slips a launch. You fly a satellite through the gap.",
        "Tusk's company asks for a ride on your next rocket. Escape velocity.",
    ]),
    8: ("Jeff Bozos", [
        "Jeff Bozos's company sits at the top of the Fortune List and intends to stay there.",
        "Bozos's company tries to buy a stake in your holding. You decline.",
        "Your holding passes Bozos's company in value. The crown is within reach.",
    ]),
}

FIRST = {
    "en": ["James", "Olivia", "Henry", "Charlotte", "Edward", "Amelia", "Theodore", "Harriet", "Arthur", "Eleanor", "Frederick", "Victoria", "Hugo", "Beatrice", "Oscar", "Florence", "Rupert", "Imogen", "Graham", "Penelope"],
    "us": ["Bradley", "Madison", "Tucker", "Savannah", "Chase", "Brooke", "Grant", "Whitney", "Preston", "Kendall", "Wyatt", "Paige", "Harrison", "Blair", "Carter", "Sloane"],
    "fr": ["Antoine", "Camille", "Mathieu", "Élodie", "Bastien", "Margaux", "Lucien", "Solène", "Thibault", "Clémence"],
    "de": ["Matthias", "Annika", "Konrad", "Leonie", "Friedrich", "Henrike", "Jonas", "Theresa", "Lukas", "Margarethe"],
    "it": ["Lorenzo", "Giulia", "Federico", "Chiara", "Massimo", "Francesca", "Vittorio", "Ludovica"],
    "es": ["Alejandro", "Lucía", "Santiago", "Valentina", "Mateo", "Ximena", "Rodrigo", "Catalina"],
    "pt": ["João", "Beatriz", "Duarte", "Inês", "Rafael", "Mariana", "Gonçalo", "Leonor"],
    "nl": ["Daan", "Sanne", "Joris", "Femke", "Pieter", "Lotte"],
    "nord": ["Erik", "Astrid", "Magnus", "Ingrid", "Henrik", "Sigrid", "Nils", "Freja"],
    "in": ["Arjun", "Priya", "Vikram", "Ananya", "Rohan", "Kavya", "Siddharth", "Meera", "Aditya", "Isha", "Karan", "Nandini"],
    "cn": ["Wei", "Mei", "Jun", "Xiu", "Hao", "Lin", "Bo", "Yan"],
    "jp": ["Haruto", "Yui", "Ren", "Sakura", "Daichi", "Aoi", "Kaito", "Hina"],
    "kr": ["Min-jun", "Ji-woo", "Seo-yeon", "Do-hyun", "Ha-eun", "Joon-ho"],
    "ar": ["Omar", "Layla", "Khalid", "Noor", "Tariq", "Yasmin", "Faisal", "Amira"],
    "af": ["Chidi", "Amara", "Kwame", "Zainab", "Tunde", "Adaeze", "Kofi", "Nia"],
    "br": ["Thiago", "Larissa", "Caio", "Fernanda", "Rafaela", "Bruno"],
    "au": ["Lachlan", "Matilda", "Angus", "Kirra", "Hamish", "Tahlia"],
}
LAST = {
    "en": ["Ashworth", "Pemberton", "Whitcombe", "Fairfax", "Holloway", "Kensley", "Thornbury", "Blackwood", "Harrowgate", "Montague", "Wexford", "Calloway", "Ravensworth", "Sterling", "Loxley"],
    "us": ["Vandermeer", "Holcomb", "Whitaker", "Brannigan", "Castellan", "Delacroix", "McAllister", "Stanhope", "Colfax", "Rutledge", "Beaumont", "Kingsley"],
    "fr": ["Delaunay", "Marchetti", "Vasseur", "Beaulieu", "Rochefort", "Lambertin", "Duvernois", "Montclair"],
    "de": ["Brandauer", "Kessler", "Lindqvist", "Hartmann", "Vogelsang", "Steinberg", "Albrecht", "Falkenrath"],
    "it": ["Bellandi", "Moretti", "Caruso", "Vianello", "Santangelo", "Lombardo"],
    "es": ["Echeverría", "Montalbán", "Valcárcel", "Iturbide", "Cifuentes", "Arrieta"],
    "pt": ["Albuquerque", "Vasconcelos", "Magalhães", "Sequeira", "Brandão"],
    "nl": ["van Leeuwen", "de Graaf", "Hoekstra", "Verhoeven"],
    "nord": ["Lindgren", "Halvorsen", "Ekdahl", "Sundqvist", "Bergström"],
    "in": ["Malhotra", "Raghunathan", "Khanna", "Venkataraman", "Chaudhary", "Iyengar", "Saxena", "Banerjee", "Kapadia", "Mehra"],
    "cn": ["Zhao", "Huang", "Lu", "Song", "Tang", "Xu"],
    "jp": ["Takahara", "Morishita", "Kurosawa", "Ishikawa", "Nakamori"],
    "kr": ["Park", "Choi", "Han", "Seo"],
    "ar": ["Al-Rashidi", "Haddad", "Mansour", "Al-Qasimi", "Nassar"],
    "af": ["Okonkwo", "Mensah", "Adeyemi", "Diallo", "Nwosu"],
    "br": ["Carvalho", "Teixeira", "Barbosa", "Figueiredo"],
    "au": ["Cartwright", "Ellery", "Hargreaves", "Whitlam"],
}
REGION_CITIES = {
    "en": ["London", "Edinburgh", "Monaco"], "us": ["New York", "San Francisco", "Los Angeles", "Chicago", "Houston", "Miami", "Boston"],
    "fr": ["Paris", "Lyon", "Geneva"], "de": ["Munich", "Hamburg", "Zurich"], "it": ["Milan", "Rome", "Turin"],
    "es": ["Madrid", "Barcelona", "Mexico City", "Buenos Aires", "Santiago"], "pt": ["Lisbon", "Porto"],
    "nl": ["Amsterdam", "Rotterdam"], "nord": ["Stockholm", "Oslo", "Copenhagen"],
    "in": ["Mumbai", "Delhi", "Bengaluru", "Hyderabad", "Chennai"], "cn": ["Shanghai", "Shenzhen", "Hong Kong", "Singapore"],
    "jp": ["Tokyo", "Osaka"], "kr": ["Seoul", "Busan"], "ar": ["Dubai", "Riyadh", "Doha", "Abu Dhabi"],
    "af": ["Lagos", "Nairobi", "Johannesburg", "Accra"], "br": ["São Paulo", "Rio de Janeiro"], "au": ["Sydney", "Melbourne", "Perth"],
}
REGION_WEIGHTS = {"en": 12, "us": 22, "fr": 7, "de": 7, "it": 5, "es": 7, "pt": 3, "nl": 3, "nord": 4, "in": 16, "cn": 10, "jp": 6, "kr": 4, "ar": 7, "af": 6, "br": 4, "au": 4}

SOURCES = {
    "consumer": ["Discount department stores", "Sneakers and streetwear", "Cosmetics", "Furniture flat-packs", "Convenience stores", "Eyewear"],
    "food": ["Frozen foods", "Coffee shops", "Fast-food franchises", "Soft drinks", "Snack brands", "Farmland and grain"],
    "industrials": ["Elevators", "Industrial robots", "Shipbuilding", "Packaging", "Trucks and engines"],
    "energy": ["Offshore oil", "Solar farms", "Natural gas", "Wind turbines", "Refineries"],
    "materials": ["Copper mines", "Steel mills", "Cement", "Lithium", "Gold mines"],
    "financials": ["Private banking", "Insurance", "Hedge funds", "Payments", "Asset management", "Private equity"],
    "technology": ["Cloud software", "Chips", "Video games", "Cybersecurity", "Online ads", "Smartphones"],
    "media": ["Television networks", "Film studios", "Music labels", "Sports franchises", "Newspapers"],
    "healthcare": ["Generic medicines", "Hospitals", "Medical devices", "Vaccines", "Biotech"],
    "property": ["Office towers", "Shopping malls", "Luxury hotels", "Homebuilding", "Warehouses"],
    "transport": ["Container shipping", "Airlines", "Ports", "Railways", "Delivery fleets"],
    "frontier": ["Satellites", "Rockets", "Fusion research"],
}

ARCH_WEIGHTS = {"shark": 14, "old_money": 18, "disruptor": 12, "collector": 10, "philanthropist": 10, "gambler": 10, "recluse": 14, "heir": 12}


def ascii_id(name: str) -> str:
    s = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    return "".join(ch if ch.isalnum() else "_" for ch in s.lower()).strip("_").replace("__", "_")


def bidder_id(name: str) -> str:
    # Must match gen_luxury.py's bidder ids.
    return name.lower().replace(" ", "_").replace("ç", "c").replace(".", "")


# --------------------------------------------------------------------------------------------------------------------
# World events: sector/global/disaster. pct = business income change while active.
# --------------------------------------------------------------------------------------------------------------------
# (id, title, news text, scope, target, pct, hours, phase weights {expansion, boom, slowdown, recession})
EV = []


def ev(i, title, text, scope, target, pct, hours, w=(1, 1, 1, 1)):
    EV.append({"id": i, "title": title, "text": text, "scope": scope, "target": target, "pct": pct, "hours": hours,
               "weights": {"expansion": w[0], "boom": w[1], "slowdown": w[2], "recession": w[3]}})


ev("tech_bubble", "Tech bubble", "Investors pile into anything with an app. Technology revenues surge.", "sector", "technology", 0.25, 24, (1, 3, 1, 0))
ev("chip_shortage", "Chip shortage", "A global chip shortage slows every gadget maker.", "sector", "technology", -0.15, 24, (1, 1, 2, 1))
ev("ai_frenzy", "AI frenzy", "Every company wants software that thinks. Tech orders double.", "sector", "technology", 0.20, 18, (2, 2, 1, 0))
ev("data_breach_wave", "Data-breach wave", "A wave of data breaches makes customers wary of tech firms.", "sector", "technology", -0.12, 18, (1, 1, 1, 1))
ev("oil_shock", "Oil shock", "Supply cuts send oil prices soaring. Energy companies print money.", "sector", "energy", 0.25, 24, (1, 2, 1, 1))
ev("oil_glut", "Oil glut", "Tankers queue offshore with nowhere to sell. Energy margins collapse.", "sector", "energy", -0.18, 24, (1, 0, 2, 2))
ev("heatwave_power", "Heatwave", "A record heatwave pushes electricity demand to new highs.", "sector", "energy", 0.15, 12, (1, 1, 1, 1))
ev("green_subsidies", "Green subsidies", "Governments announce clean-energy subsidies.", "sector", "energy", 0.12, 36, (2, 1, 1, 1))
ev("housing_boom", "Housing boom", "Cheap credit sparks a building frenzy.", "sector", "property", 0.22, 24, (2, 3, 0, 0))
ev("construction_strike", "Builders' strike", "A nationwide builders' strike stops work on hundreds of sites.", "sector", "property", -0.15, 18, (1, 1, 1, 1))
ev("credit_crunch_build", "Credit crunch", "Banks stop lending to developers.", "sector", "property", -0.20, 24, (0, 0, 2, 3))
ev("supply_crunch", "Supply-chain crunch", "Ports jam and freight rates spike. Shippers are busy; everyone else waits.", "sector", "transport", 0.15, 24, (1, 2, 1, 0))
ev("fever_season", "Fever season", "A nasty flu season keeps travellers at home.", "sector", "transport", -0.20, 24, (1, 1, 1, 2))
ev("travel_rush", "Travel rush", "Everyone wants a holiday at once. Planes and hotels are full.", "sector", "transport", 0.20, 18, (2, 2, 1, 0))
ev("fuel_spike", "Jet-fuel spike", "Fuel costs bite into every airline and haulier.", "sector", "transport", -0.12, 18, (1, 1, 1, 1))
ev("crop_blight", "Crop blight", "A blight hits wheat across three continents. Food prices jump.", "sector", "food", -0.15, 24, (1, 1, 1, 1))
ev("bumper_harvest", "Bumper harvest", "Perfect weather brings a record harvest.", "sector", "food", 0.15, 24, (1, 1, 1, 1))
ev("foodie_craze", "Foodie craze", "A viral food trend packs restaurants every night.", "sector", "food", 0.18, 12, (2, 2, 1, 0))
ev("health_scare", "Food-safety scare", "A food-safety scare empties dining rooms for a day.", "sector", "food", -0.12, 12, (1, 1, 1, 1))
ev("retail_holiday", "Holiday shopping", "The holiday season starts early. Tills are ringing.", "sector", "consumer", 0.20, 24, (2, 2, 1, 1))
ev("consumer_slump", "Consumer slump", "Shoppers tighten their belts.", "sector", "consumer", -0.15, 24, (0, 0, 2, 3))
ev("fashion_week", "Fashion week", "A breakout fashion week makes luxury labels the talk of the town.", "sector", "consumer", 0.15, 12, (1, 2, 1, 0))
ev("counterfeit_wave", "Counterfeit wave", "A flood of fakes undercuts branded goods.", "sector", "consumer", -0.10, 18, (1, 1, 1, 1))
ev("factory_boom", "Factory boom", "Order books fill up across heavy industry.", "sector", "industrials", 0.18, 24, (2, 2, 1, 0))
ev("steel_tariffs", "Steel tariffs", "New tariffs raise the cost of every machine.", "sector", "industrials", -0.12, 24, (1, 1, 1, 1))
ev("automation_wave", "Automation wave", "Robots arrive on factory floors everywhere. Output jumps.", "sector", "industrials", 0.12, 36, (1, 1, 1, 1))
ev("metal_rally", "Metals rally", "Copper and lithium hit record prices.", "sector", "materials", 0.22, 24, (1, 3, 1, 0))
ev("mine_collapse_news", "Mine safety crackdown", "Regulators inspect every mine after an accident abroad. Output slows.", "sector", "materials", -0.15, 18, (1, 1, 1, 1))
ev("gold_rush", "Gold rush", "Nervous investors buy gold. Miners cheer.", "sector", "materials", 0.15, 24, (0, 0, 2, 3))
ev("rate_rise", "Rate rise", "The central bank surprises with a rate rise. Banks earn more on every loan.", "sector", "financials", 0.15, 24, (1, 2, 1, 0))
ev("bank_run", "Bank-run jitters", "Rumours of a bank run spook depositors.", "sector", "financials", -0.18, 18, (0, 0, 2, 2))
ev("ipo_wave", "IPO wave", "A wave of listings keeps the investment bankers busy.", "sector", "financials", 0.18, 18, (1, 3, 0, 0))
ev("fintech_squeeze", "Fintech squeeze", "Payment apps undercut bank fees.", "sector", "financials", -0.10, 24, (1, 1, 1, 1))
ev("streaming_war", "Streaming war", "Every studio launches a streaming service. Content is king.", "sector", "media", 0.15, 24, (1, 2, 1, 0))
ev("ad_slump", "Advertising slump", "Brands slash their advertising budgets.", "sector", "media", -0.15, 24, (0, 0, 2, 2))
ev("world_cup_fever", "Cup fever", "A global football tournament glues the world to its screens.", "sector", "media", 0.20, 18, (1, 1, 1, 1))
ev("pandemic_drill", "Pandemic preparedness", "Governments stockpile medicines.", "sector", "healthcare", 0.18, 24, (1, 1, 1, 1))
ev("drug_price_cap", "Drug-price caps", "New rules cap what drug makers can charge.", "sector", "healthcare", -0.12, 36, (1, 1, 1, 1))
ev("breakthrough_cure", "Medical breakthrough", "A breakthrough therapy lifts the whole sector.", "sector", "healthcare", 0.15, 24, (1, 1, 1, 1))
ev("space_race", "New space race", "Governments order satellites by the hundred.", "sector", "frontier", 0.25, 24, (1, 2, 1, 0))
ev("launch_failure_news", "Launch failures", "Two rocket failures abroad make insurers nervous about space.", "sector", "frontier", -0.15, 18, (1, 1, 1, 1))
ev("asteroid_hype", "Asteroid-mining hype", "A probe finds a metal-rich asteroid. Space stocks soar.", "sector", "frontier", 0.20, 12, (1, 2, 1, 0))
ev("global_boom", "Global boom", "Every economy is growing at once. Business is good everywhere.", "all", "", 0.10, 24, (1, 3, 0, 0))
ev("global_jitters", "Market jitters", "A wobble on the world's stock markets makes customers cautious.", "all", "", -0.06, 12, (0, 0, 2, 2))
ev("tax_holiday", "Business tax holiday", "A one-off business-rates holiday puts money back in every till.", "all", "", 0.08, 12, (1, 1, 1, 1))
ev("power_cuts", "Rolling power cuts", "Grid failures cause rolling blackouts.", "all", "", -0.08, 6, (1, 1, 1, 1))
ev("consumer_confidence", "Confidence surge", "Consumer confidence hits a ten-year high.", "all", "", 0.07, 24, (2, 2, 0, 0))
ev("trade_war", "Trade war", "Tariffs fly in both directions. Global trade slows.", "all", "", -0.07, 24, (0, 0, 2, 2))
ev("recovery_signs", "Green shoots", "Hiring picks up. The worst seems to be over.", "all", "", 0.06, 24, (0, 0, 1, 3))
ev("cyber_attack", "Global cyber-attack", "A worm knocks out payment systems for a day.", "all", "", -0.05, 6, (1, 1, 1, 1))
ev("innovation_week", "Innovation week", "Start-up week fills every conference hall.", "all", "", 0.05, 12, (1, 1, 1, 1))
ev("holiday_season", "Long weekend", "A long weekend lifts leisure spending.", "all", "", 0.05, 6, (1, 1, 1, 1))
# Disasters strike a real city: uninsured properties there lose condition (GDD §9).
for i, (city, title, text) in enumerate([
    ("miami", "Hurricane", "A hurricane makes landfall near Miami."),
    ("mumbai", "Monsoon floods", "Monsoon floods swamp parts of Mumbai."),
    ("lisbon", "Earthquake", "A strong earthquake shakes Lisbon."),
    ("london", "Thames flood", "A storm surge overtops defences along the Thames."),
    ("new_york", "Superstorm", "A superstorm floods lower Manhattan."),
    ("dubai", "Sandstorm", "A week-long sandstorm batters Dubai."),
    ("miami", "King tide", "A king tide floods Miami's waterfront streets."),
    ("mumbai", "Cyclone", "A cyclone brushes the Mumbai coast."),
]):
    ev(f"disaster_{city}_{i}", title, text, "city", city, 0.0, 0, (1, 1, 1, 1))

# --------------------------------------------------------------------------------------------------------------------
# Decision cards. Effects shorthand (hours = hours of your total business income):
#   "cash -2" cost/gain · "inc biz|all|sector +15 12" income % for hours · "credit +10" · "rel -5" (bound rival)
#   "donate 1" (cash + tax deduction) · "rd 2" (cash + R&D credit) · "sell 2.5" (sell the bound business at ×valuation)
# --------------------------------------------------------------------------------------------------------------------
CARDS = []


def parse_effect(s: str) -> dict:
    p = s.split()
    t = p[0]
    if t == "cash":
        return {"type": "cash", "hours": float(p[1])}
    if t == "inc":
        return {"type": "income", "target": p[1].replace("biz", "business"), "pct": float(p[2]) / 100.0, "hours": int(p[3])}
    if t == "credit":
        return {"type": "credit", "points": int(p[1])}
    if t == "rel":
        return {"type": "relation", "points": int(p[1])}
    if t == "donate":
        return {"type": "donation", "hours": float(p[1])}
    if t == "rd":
        return {"type": "rd", "hours": float(p[1])}
    if t == "sell":
        return {"type": "sell", "multiple": float(p[1])}
    raise ValueError(s)


def O(label, detail, *outcomes, requires=None):
    """An option. Each outcome is (probability, result text, [effects]); a bare list of effects means a sure outcome."""
    outs = []
    for o in outcomes:
        if isinstance(o, tuple):
            p, text, effs = o
        else:
            p, text, effs = 1.0, "", o
        outs.append({"p": p, "text": text, "effects": [parse_effect(e) for e in effs]})
    if not outs:
        outs = [{"p": 1.0, "text": "", "effects": []}]
    assert abs(sum(x["p"] for x in outs) - 1.0) < 1e-9, label
    d = {"label": label, "detail": detail, "outcomes": outs}
    if requires:
        d["requires"] = requires
    return d


def card(cid, kind, chapter, req, title, text, *options, weight=1.0):
    assert 2 <= len(options) <= 5 or kind == "nemesis", cid
    CARDS.append({"id": cid, "kind": kind, "chapter": chapter, "requires": req, "title": title, "text": text,
                  "options": list(options), "weight": weight})


# ----- Business-specific cards (3 per business) -----
card("retail_shoplifting", "business", 1, "biz:retail", "Shoplifting spree", "Stock keeps vanishing from {business}. Security firms are circling.",
     O("Hire guards", "Costs 1 h of income. Losses stop.", ["cash -1", "inc biz +5 12"]),
     O("Install cameras", "Costs 0.5 h. Works most of the time.", (0.7, "The cameras catch the ring.", ["cash -0.5", "inc biz +5 12"]), (0.3, "The thieves find the blind spots.", ["cash -0.5", "inc biz -5 12"])),
     O("Ignore it", "Free, but losses continue for a while.", ["inc biz -8 12"]))
card("retail_viral_product", "business", 1, "biz:retail", "A product goes viral", "A gadget you stock is all over social media. Suppliers want a bulk order today.",
     O("Order big", "Costs 2 h. Sales jump if the craze lasts.", (0.65, "The craze lasts all week.", ["cash -2", "inc biz +30 18"]), (0.35, "The craze fizzles.", ["cash -2", "inc biz +5 18"])),
     O("Order a little", "Costs 0.5 h. A safe bump.", ["cash -0.5", "inc biz +10 12"]),
     O("Pass", "Nothing happens.", []))
card("retail_landlord", "business", 1, "biz:retail", "Landlord wants more", "The landlord of your flagship {business} site wants a rent rise.",
     O("Pay up", "Costs 1 h now.", ["cash -1"]),
     O("Negotiate", "60%: a fair deal; 40%: they dig in.", (0.6, "You agree a small rise.", ["cash -0.3"]), (0.4, "They dig in and you lose a week of momentum.", ["inc biz -10 12"])),
     O("Threaten to move", "Risky. A move would hurt sales for a while.", (0.5, "The landlord backs down.", []), (0.5, "You move. Customers take time to find you.", ["inc biz -15 18"])))
card("restaurant_critic", "business", 1, "biz:restaurants", "The critic is coming", "A famous food critic has booked a table at {business} tonight.",
     O("Bring in a star chef", "Costs 1.5 h. Usually wows.", (0.75, "Rave review!", ["cash -1.5", "inc biz +25 24"]), (0.25, "A lukewarm review.", ["cash -1.5"])),
     O("Business as usual", "Free. Could go either way.", (0.5, "A kind review.", ["inc biz +10 24"]), (0.5, "\"Forgettable.\"", ["inc biz -10 24"])))
card("restaurant_supplier", "business", 1, "biz:restaurants", "Supplier price hike", "Your produce supplier raises prices 20%.",
     O("Switch suppliers", "Costs 0.5 h to set up. Quality might dip.", (0.7, "The new supplier is great.", ["cash -0.5"]), (0.3, "Diners notice the difference.", ["cash -0.5", "inc biz -8 12"])),
     O("Accept the rise", "Margins shrink for a day.", ["inc biz -6 24"]),
     O("Raise menu prices", "Diners may grumble.", (0.6, "Nobody notices.", ["inc biz +5 24"]), (0.4, "Bookings drop.", ["inc biz -10 12"])))
card("restaurant_delivery_app", "business", 1, "biz:restaurants", "Delivery app deal", "A delivery app offers to list {business} for a 25% commission.",
     O("Sign up", "More orders, thinner margins.", ["inc biz +12 24"]),
     O("Build your own app", "Costs 2 h. Keeps the margin.", ["cash -2", "inc biz +18 24"]),
     O("Decline", "Nothing changes.", []))
card("logistics_driver_shortage", "business", 1, "biz:logistics", "Driver shortage", "Half your drivers have been poached by a rival depot.",
     O("Raise pay", "Costs 1 h. Drivers stay.", ["cash -1"]),
     O("Recruit abroad", "Costs 0.5 h. Takes a while.", ["cash -0.5", "inc biz -8 12"]),
     O("Run short-staffed", "Deliveries slip.", ["inc biz -15 12"]))
card("logistics_big_contract", "business", 1, "biz:logistics", "A big contract", "A supermarket chain wants {business} to run its deliveries. They want a discount.",
     O("Accept the discount", "Steady volume, lower margin.", ["inc biz +15 24"]),
     O("Hold out for full price", "50%: they agree; 50%: they walk.", (0.5, "They agree.", ["inc biz +25 24"]), (0.5, "They walk.", []))
     )
card("logistics_fuel_hedge", "business", 1, "biz:logistics", "Fuel hedge offer", "A broker offers to lock in today's fuel price for {business}.",
     O("Lock it in", "Costs 1 h. Protects margins.", ["cash -1", "inc biz +10 24"]),
     O("Gamble on prices", "50/50.", (0.5, "Fuel gets cheaper.", ["inc biz +12 24"]), (0.5, "Fuel spikes.", ["inc biz -12 24"])))
card("farm_drought", "business", 2, "biz:farmland", "Drought warning", "Forecasters warn of a dry month across your {business}.",
     O("Install irrigation", "Costs 2 h. Crops are safe.", ["cash -2", "inc biz +5 24"]),
     O("Buy crop insurance", "Costs 0.5 h. Pays out if it fails.", (0.6, "Rain arrives after all.", ["cash -0.5"]), (0.4, "The insurer pays out.", ["cash +0.5"])),
     O("Pray for rain", "40% chance of a bad harvest.", (0.6, "The rain comes.", []), (0.4, "The harvest fails.", ["inc biz -25 24"])))
card("farm_organic", "business", 2, "biz:farmland", "Go organic?", "A premium grocer will pay more for organic produce from {business}.",
     O("Convert fields", "Costs 2 h. Higher prices for a day.", ["cash -2", "inc biz +25 24"]),
     O("Convert one field", "Costs 0.5 h.", ["cash -0.5", "inc biz +8 24"]),
     O("Stay conventional", "Nothing changes.", []))
card("farm_locusts", "business", 2, "biz:farmland", "Locust swarm", "A locust swarm is heading for your {business}.",
     O("Spray now", "Costs 1 h. Crops saved.", ["cash -1"]),
     O("Use natural predators", "Cheaper, slower.", (0.6, "The birds feast.", ["cash -0.3"]), (0.4, "Half a field is lost.", ["cash -0.3", "inc biz -12 18"])))
card("factory_union", "business", 2, "biz:factory", "The union wants raises", "Workers at {business} demand a 10% raise.",
     O("Accept", "Costs 1 h. Productivity rises.", ["cash -1", "inc biz +10 24"]),
     O("Negotiate", "70%: deal; 30%: a short strike.", (0.7, "A fair deal.", ["cash -0.5", "inc biz +5 24"]), (0.3, "A one-day strike.", ["inc biz -30 12"])),
     O("Refuse", "40% strike risk.", (0.6, "They grumble and get back to work.", []), (0.4, "Strike!", ["inc biz -40 12", "credit -5"])))
card("factory_robots", "business", 2, "biz:factory", "Robot salesman", "A robotics firm offers to automate a line at {business}.",
     O("Automate", "Costs 3 h. Output rises for a day.", ["cash -3", "inc biz +30 24"]),
     O("Pilot one robot", "Costs 1 h.", ["cash -1", "inc biz +10 24"]),
     O("Decline", "Nothing changes.", []))
card("factory_recall", "business", 2, "biz:factory", "Product recall", "A batch from {business} has a defect.",
     O("Recall everything", "Costs 2 h. Reputation intact.", ["cash -2", "credit +5"]),
     O("Quiet fix", "Cheaper, risky.", (0.6, "Nobody notices.", ["cash -0.5"]), (0.4, "The press finds out.", ["cash -0.5", "inc biz -20 24", "credit -10"])))
card("fashion_muse", "business", 2, "biz:fashion", "A new muse", "A breakout model wants to front {business}'s new campaign.",
     O("Sign them", "Costs 2 h.", (0.7, "The campaign is a hit.", ["cash -2", "inc biz +30 24"]), (0.3, "The campaign is ignored.", ["cash -2"])),
     O("Use the house model", "Free and safe.", ["inc biz +5 12"]))
card("fashion_copycat", "business", 2, "biz:fashion", "Copycats", "Fast-fashion chains copied {business}'s collection within a week.",
     O("Sue", "Costs 1.5 h. 60% win.", (0.6, "You win damages.", ["cash -1.5", "cash +3"]), (0.4, "You lose.", ["cash -1.5"])),
     O("Release the next collection early", "Costs 1 h.", ["cash -1", "inc biz +12 18"]),
     O("Let it go", "Sales dip a little.", ["inc biz -8 18"]))
card("fashion_show", "business", 2, "biz:fashion", "Show in Paris?", "Fashion week in Paris has a late slot for {business}.",
     O("Take the slot", "Costs 2 h.", ["cash -2", "inc biz +25 24"]),
     O("Livestream instead", "Costs 0.5 h.", ["cash -0.5", "inc biz +8 24"]),
     O("Skip it", "Nothing changes.", []))
card("mining_rich_seam", "business", 3, "biz:mining", "A rich seam", "Geologists at {business} have found a rich seam, deep down.",
     O("Dig deep", "Costs 3 h. 70% pays off.", (0.7, "The seam is real.", ["cash -3", "inc biz +40 24"]), (0.3, "It peters out.", ["cash -3", "inc biz +5 24"])),
     O("Sell the rights", "Gain 1.5 h now.", ["cash +1.5"]),
     O("Leave it", "Nothing changes.", []))
card("mining_safety", "business", 3, "biz:mining", "Safety inspection", "Inspectors are coming to {business} next week.",
     O("Upgrade safety gear", "Costs 1.5 h. Passes for sure.", ["cash -1.5", "credit +5"]),
     O("Hope for the best", "30% fail.", (0.7, "You pass.", []), (0.3, "The mine is closed for a day.", ["inc biz -50 12", "credit -10"])))
card("mining_community", "business", 3, "biz:mining", "The town wants a school", "The town next to {business} asks you to fund a new school.",
     O("Fund it", "Donation of 1 h (tax-deductible).", ["donate 1", "inc biz +8 24"]),
     O("Fund half", "Donation of 0.5 h.", ["donate 0.5"]),
     O("Decline", "Locals are unhappy.", ["inc biz -5 24"]))
card("energy_grid_deal", "business", 3, "biz:energy", "Grid contract", "The national grid wants a 10-year supply contract from {business}.",
     O("Sign at a fixed price", "Steady income.", ["inc biz +15 36"]),
     O("Sign at market price", "Riskier.", (0.5, "Prices rise.", ["inc biz +30 24"]), (0.5, "Prices fall.", ["inc biz -5 24"])))
card("energy_solar", "business", 3, "biz:energy", "Solar expansion", "You could cover {business}'s spare land with solar panels.",
     O("Build it", "Costs 3 h. Counts toward green credits.", ["cash -3", "inc biz +25 36"]),
     O("Lease the land", "Gain 1 h now.", ["cash +1"]),
     O("Not now", "Nothing changes.", []))
card("energy_pipeline_leak", "business", 3, "biz:energy", "Pipeline leak", "A small leak at {business}. Cleaning it fast is expensive.",
     O("Clean it now", "Costs 2 h.", ["cash -2", "credit +5"]),
     O("Patch it", "Costs 0.5 h. 30% it gets worse.", (0.7, "The patch holds.", ["cash -0.5"]), (0.3, "It gets worse.", ["cash -0.5", "inc biz -25 24", "credit -10"])))
card("hotel_celebrity_wedding", "business", 3, "biz:hotels", "Celebrity wedding", "A pop star wants to rent all of {business} for a wedding.",
     O("Say yes", "Costs 1 h in preparations; huge buzz.", ["cash -1", "inc biz +30 24"]),
     O("Say no", "Regular guests stay happy.", []))
card("hotel_bad_reviews", "business", 3, "biz:hotels", "Bad reviews", "A string of one-star reviews hits {business}.",
     O("Renovate rooms", "Costs 2 h.", ["cash -2", "inc biz +12 24"]),
     O("Reply to every review", "Free.", (0.5, "Guests appreciate it.", []), (0.5, "Bookings dip.", ["inc biz -10 18"])))
card("hotel_overbooking", "business", 3, "biz:hotels", "Overbooked", "{business} has sold 120 rooms for 100 beds this weekend.",
     O("Upgrade guests elsewhere", "Costs 1 h. Guests love it.", ["cash -1", "inc biz +8 24"]),
     O("Turn them away", "Free. Reviews suffer.", ["inc biz -12 24"]))
card("bank_regulator", "business", 4, "biz:bank", "Regulator review", "The regulator asks {business} to hold more capital.",
     O("Raise capital", "Costs 2 h. Rating improves.", ["cash -2", "credit +15"]),
     O("Appeal", "50/50.", (0.5, "The appeal succeeds.", []), (0.5, "The appeal fails and you pay anyway.", ["cash -2.5"])))
card("bank_fintech_partner", "business", 4, "biz:bank", "Fintech partnership", "A payments start-up wants to plug into {business}.",
     O("Partner", "Costs 1 h. More customers.", ["cash -1", "inc biz +18 24"]),
     O("Buy them", "Costs 3 h.", ["cash -3", "inc biz +30 36"]),
     O("Decline", "Nothing changes.", []))
card("bank_bad_loans", "business", 4, "biz:bank", "Bad loans", "A borrower of {business} has defaulted.",
     O("Write it off", "Costs 1.5 h now.", ["cash -1.5"]),
     O("Chase it", "60% recover.", (0.6, "You recover most of it.", ["cash -0.3"]), (0.4, "Nothing to recover.", ["cash -2"])))
card("construction_tender", "business", 4, "biz:construction", "City tender", "The city is tendering a new stadium. {business} could bid.",
     O("Bid low", "Likely win, thin margin.", (0.75, "You win the tender.", ["inc biz +18 36"]), (0.25, "Undercut anyway.", [])),
     O("Bid high", "Big margin if you win.", (0.35, "You win at a great price.", ["inc biz +40 36"]), (0.65, "You lose the tender.", [])),
     O("Skip it", "Nothing changes.", []))
card("construction_crane", "business", 4, "biz:construction", "Crane collapse", "A crane at a {business} site has toppled. Nobody was hurt.",
     O("Full safety review", "Costs 2 h.", ["cash -2", "credit +5"]),
     O("Back to work", "Faster; reputation risk.", (0.6, "No more trouble.", []), (0.4, "Inspectors halt the site.", ["inc biz -30 18", "credit -10"])))
card("construction_green", "business", 4, "biz:construction", "Green building", "Clients want low-carbon buildings from {business}.",
     O("Retool for timber", "Costs 2 h.", ["cash -2", "inc biz +20 36"]),
     O("Greenwash", "Free; risky.", (0.6, "Clients buy it.", ["inc biz +5 24"]), (0.4, "Journalists expose it.", ["inc biz -15 24", "credit -5"])))
card("media_scoop", "business", 4, "biz:media", "The scoop", "Reporters at {business} have a huge exclusive, but it's legally risky.",
     O("Publish", "60%: a scoop; 40%: a lawsuit.", (0.6, "Record audiences.", ["inc biz +30 24"]), (0.4, "A costly lawsuit.", ["cash -2"])),
     O("Lawyer it first", "Costs 0.5 h; safer.", ["cash -0.5", "inc biz +15 24"]),
     O("Spike it", "Nothing happens.", []))
card("media_star_host", "business", 4, "biz:media", "Star host wants more", "{business}'s star host wants double pay.",
     O("Pay", "Costs 2 h; ratings safe.", ["cash -2", "inc biz +10 24"]),
     O("Let them go", "Ratings dip.", ["inc biz -15 24"]))
card("media_streaming", "business", 4, "biz:media", "Launch a streamer?", "Should {business} launch its own streaming service?",
     O("Launch", "Costs 3 h. 60% success.", (0.6, "Subscribers pour in.", ["cash -3", "inc biz +40 36"]), (0.4, "Nobody subscribes.", ["cash -3"])),
     O("License to others", "Gain 1 h now.", ["cash +1"]))
card("tech_hackers", "business", 5, "biz:tech", "Ransomware", "Hackers have locked {business}'s servers and want a ransom.",
     O("Restore from backup", "Costs 1.5 h; two hours of chaos.", ["cash -1.5", "inc biz -10 6"]),
     O("Pay the ransom", "Costs 1 h. 70% they unlock it.", (0.7, "They unlock it.", ["cash -1"]), (0.3, "They take the money and run.", ["cash -1", "inc biz -25 12"])))
card("tech_acquihire", "business", 5, "biz:tech", "Acqui-hire", "A brilliant ten-person start-up could join {business}.",
     O("Buy them", "Costs 3 h. R&D credit.", ["rd 3", "inc biz +30 36"]),
     O("Hire just the founder", "Costs 1 h.", ["rd 1", "inc biz +10 24"]),
     O("Pass", "Nothing changes.", []))
card("tech_open_source", "business", 5, "biz:tech", "Open-source it?", "Engineers at {business} want to open-source your core library.",
     O("Open it", "Goodwill and talent.", ["inc biz +10 36", "credit +5"]),
     O("Keep it closed", "Nothing changes.", []))
card("telecom_spectrum", "business", 5, "biz:telecom", "Spectrum auction", "Regulators are auctioning 6G spectrum.",
     O("Bid aggressively", "Costs 3 h.", ["cash -3", "inc biz +35 36"]),
     O("Bid modestly", "50% win.", (0.5, "You win a slice.", ["cash -1", "inc biz +15 36"]), (0.5, "Outbid.", [])),
     O("Sit out", "Nothing changes.", []))
card("telecom_outage", "business", 5, "biz:telecom", "Network outage", "A nationwide outage hits {business}.",
     O("Compensate customers", "Costs 1 h; loyalty kept.", ["cash -1"]),
     O("Apologise", "Free; some customers leave.", ["inc biz -12 24"]))
card("telecom_towers", "business", 5, "biz:telecom", "Sell the towers?", "An infrastructure fund wants to buy {business}'s towers.",
     O("Sell and lease back", "Gain 2 h now; costs a little later.", ["cash +2", "inc biz -5 36"]),
     O("Keep them", "Nothing changes.", []))
card("airline_strike", "business", 6, "biz:airline", "Pilots' strike", "Pilots at {business} vote to strike.",
     O("Meet their demands", "Costs 2 h.", ["cash -2"]),
     O("Negotiate", "65% deal.", (0.65, "A deal is struck.", ["cash -1"]), (0.35, "Planes are grounded for a day.", ["inc biz -40 12"])))
card("airline_new_route", "business", 6, "biz:airline", "New route", "{business} could launch a nonstop route to Sydney.",
     O("Launch it", "Costs 2 h. 70% success.", (0.7, "Flights sell out.", ["cash -2", "inc biz +25 36"]), (0.3, "Half-empty planes.", ["cash -2"])),
     O("Codeshare instead", "Free; small gain.", ["inc biz +6 24"]))
card("airline_ash_cloud", "business", 6, "biz:airline", "Ash cloud", "A volcano's ash cloud closes airspace over your hub.",
     O("Reroute flights", "Costs 1.5 h.", ["cash -1.5", "inc biz -10 12"]),
     O("Ground the fleet", "Free; lost revenue.", ["inc biz -30 12"]))
card("pharma_trial", "business", 6, "biz:pharma", "Phase III trial", "{business}'s new drug is entering its final trial.",
     O("Fund it fully", "Costs 3 h (R&D). 60% success.", (0.6, "Approved!", ["rd 3", "inc biz +45 36"]), (0.4, "The trial fails.", ["rd 3"])),
     O("Partner with a rival", "Costs 1 h. Shared upside.", (0.6, "Approved; profits shared.", ["rd 1", "inc biz +20 36"]), (0.4, "The trial fails.", ["rd 1"]))
     )
card("pharma_patent", "business", 6, "biz:pharma", "Patent cliff", "{business}'s best-selling patent expires soon.",
     O("Develop a successor", "Costs 2 h (R&D).", ["rd 2", "inc biz +15 36"]),
     O("License a generic", "Gain 1 h now.", ["cash +1", "inc biz -8 24"]))
card("pharma_price", "business", 6, "biz:pharma", "Price backlash", "Politicians attack {business}'s prices.",
     O("Cut prices", "Margins dip, goodwill rises.", ["inc biz -10 24", "credit +5"]),
     O("Defend prices", "50% backlash.", (0.5, "It blows over.", []), (0.5, "A boycott.", ["inc biz -20 24"])))
card("sports_star", "business", 7, "biz:sports", "A superstar is available", "The world's best striker could join {business}.",
     O("Sign them", "Costs 3 h.", (0.7, "Trophies and shirt sales.", ["cash -3", "inc biz +40 36"]), (0.3, "Injured in the first game.", ["cash -3"])),
     O("Promote from the academy", "Free.", (0.4, "A new star is born.", ["inc biz +20 24"]), (0.6, "Steady season.", [])))
card("sports_stadium", "business", 7, "biz:sports", "New stadium", "{business} has outgrown its stadium.",
     O("Build a new one", "Costs 4 h.", ["cash -4", "inc biz +35 48"]),
     O("Expand the old one", "Costs 1.5 h.", ["cash -1.5", "inc biz +12 36"]),
     O("Stay put", "Nothing changes.", []))
card("sports_derby", "business", 7, "biz:sports", "Derby day", "{business} plays its biggest rival this weekend.",
     O("Big marketing push", "Costs 1 h.", ["cash -1", "inc biz +18 18"]),
     O("Let the game sell itself", "50/50 on the result.", (0.5, "A famous win!", ["inc biz +15 18"]), (0.5, "A painful loss.", ["inc biz -5 18"])))
card("space_launch_window", "business", 7, "biz:space", "Launch window", "A perfect launch window opens for {business}. The weather is marginal.",
     O("Launch", "75% success.", (0.75, "Perfect orbit.", ["inc biz +35 36"]), (0.25, "Scrubbed after liftoff. Costly.", ["cash -3"])),
     O("Wait for the next window", "Safe; slower.", ["inc biz -5 24"]))
card("space_tourism", "business", 7, "biz:space", "Space tourists", "Billionaires want seats on {business}'s next flight.",
     O("Sell seats", "Gain 2 h now.", ["cash +2"]),
     O("Keep the seats for science", "R&D credit.", ["rd 1", "inc biz +15 36"]))
card("space_debris", "business", 7, "biz:space", "Space debris", "Debris threatens a {business} satellite.",
     O("Manoeuvre", "Costs 1 h of fuel.", ["cash -1"]),
     O("Hope it misses", "80% miss.", (0.8, "It misses.", []), (0.2, "The satellite is lost.", ["inc biz -30 24"])))

# ----- Generic business cards (bound to any business you own) -----
GENERIC = [
    ("gen_consultant", "Management consultants", "A consulting firm promises to make {business} 20% more efficient.",
     [O("Hire them", "Costs 1.5 h. 60% it works.", (0.6, "They find real savings.", ["cash -1.5", "inc biz +25 24"]), (0.4, "Slides, no savings.", ["cash -1.5"])),
      O("Do it in-house", "Free; smaller gain.", ["inc biz +6 24"])]),
    ("gen_burnout", "Staff burnout", "Overtime at {business} is burning out your best people.",
     [O("Hire more staff", "Costs 1 h.", ["cash -1", "inc biz +5 24"]), O("Give a bonus", "Costs 0.5 h.", ["cash -0.5"]), O("Push through", "Productivity drops.", ["inc biz -12 24"])]),
    ("gen_award", "Industry award", "{business} is shortlisted for an industry award. The gala ticket isn't cheap.",
     [O("Attend", "Costs 0.5 h. 50% win.", (0.5, "You win!", ["cash -0.5", "inc biz +15 24"]), (0.5, "Runner-up.", ["cash -0.5"])), O("Skip", "Nothing changes.", [])]),
    ("gen_charity_run", "Charity run", "Staff at {business} want to sponsor a charity run.",
     [O("Sponsor", "Donation of 0.5 h (deductible).", ["donate 0.5", "inc biz +4 24"]), O("Match staff donations", "Donation of 1 h.", ["donate 1", "inc biz +6 24"]), O("Decline", "Nothing changes.", [])]),
    ("gen_software_upgrade", "Software upgrade", "{business}'s systems are ten years old.",
     [O("Upgrade now", "Costs 2 h. Two hours of disruption, then faster.", ["cash -2", "inc biz +15 36"]), O("Patch", "Costs 0.3 h.", ["cash -0.3"]), O("Wait", "Small risk of a crash.", (0.8, "It keeps running.", []), (0.2, "It crashes.", ["inc biz -25 12"]))]),
    ("gen_poach_exec", "Poach an executive", "A star executive from a competitor would join {business}.",
     [O("Make an offer", "Costs 1.5 h.", (0.7, "They join and shake things up.", ["cash -1.5", "inc biz +20 36"]), (0.3, "They stay put.", ["cash -0.3"])), O("Pass", "Nothing changes.", [])]),
    ("gen_lawsuit", "A lawsuit", "A former customer sues {business}.",
     [O("Settle", "Costs 1 h.", ["cash -1"]), O("Fight", "60% win.", (0.6, "Case dismissed.", ["cash -0.3"]), (0.4, "You lose.", ["cash -2.5"]))]),
    ("gen_expansion_loan", "Bank offer", "A bank offers {business} a cheap loan to open a new branch.",
     [O("Take it", "More income, more risk.", (0.7, "The branch is a hit.", ["inc biz +20 36"]), (0.3, "The branch struggles.", ["inc biz -5 24"])), O("Decline", "Nothing changes.", [])]),
    ("gen_viral_video", "Viral video", "A video filmed at {business} is going viral. For good reasons.",
     [O("Ride the wave", "Costs 0.5 h in ads.", ["cash -0.5", "inc biz +20 18"]), O("Let it run", "Free.", ["inc biz +8 18"])]),
    ("gen_bad_press", "Bad press", "A newspaper runs an unflattering story about {business}.",
     [O("PR campaign", "Costs 1 h.", ["cash -1"]), O("Ignore it", "Sales dip.", ["inc biz -10 18"]), O("Sue the paper", "50/50.", (0.5, "A retraction.", ["cash -0.5", "inc biz +5 18"]), (0.5, "The story spreads.", ["cash -0.5", "inc biz -15 18"]))]),
    ("gen_supplier_bankrupt", "Supplier goes bust", "A key supplier of {business} has gone bankrupt.",
     [O("Buy the supplier", "Costs 2 h.", ["cash -2", "inc biz +10 36"]), O("Find a new one", "A short disruption.", ["inc biz -10 12"])]),
    ("gen_four_day_week", "Four-day week", "Staff at {business} propose a four-day week.",
     [O("Trial it", "50% productivity rises.", (0.5, "Productivity rises!", ["inc biz +12 36"]), (0.5, "Output dips.", ["inc biz -8 24"])), O("Say no", "Nothing changes.", [])]),
    ("gen_rebrand", "Rebrand", "Marketing wants to rebrand {business}.",
     [O("Go for it", "Costs 1.5 h.", (0.6, "Customers love it.", ["cash -1.5", "inc biz +20 36"]), (0.4, "Customers hate it.", ["cash -1.5", "inc biz -5 24"])), O("Keep the brand", "Nothing changes.", [])]),
    ("gen_insurance_review", "Insurance review", "Your broker says {business} is underinsured.",
     [O("Top up cover", "Costs 0.5 h.", ["cash -0.5", "credit +3"]), O("Take the risk", "Nothing changes for now.", [])]),
    ("gen_intern_idea", "The intern's idea", "An intern at {business} has a bold idea.",
     [O("Fund it", "Costs 0.5 h. 30% big win.", (0.3, "It's brilliant.", ["cash -0.5", "inc biz +30 24"]), (0.7, "It doesn't work.", ["cash -0.5"])), O("Pat them on the head", "Nothing changes.", [])]),
    ("gen_competitor_bust", "Competitor in trouble", "A competitor of {business} is close to bankruptcy.",
     [O("Buy their customers", "Costs 1.5 h.", ["cash -1.5", "inc biz +20 36"]), O("Wait for them to fail", "50/50.", (0.5, "They fail; you gain customers.", ["inc biz +12 24"]), (0.5, "They are rescued.", []))]),
    ("gen_tax_advisor", "Tax advisor", "An advisor says {business} could restructure to save tax.",
     [O("Do it properly", "Costs 0.5 h. R&D credit.", ["rd 0.5"]), O("Not interested", "Nothing changes.", [])]),
    ("gen_cyber_drill", "Cyber drill", "IT wants to run a cyber-attack drill at {business}.",
     [O("Run it", "Costs 0.3 h.", ["cash -0.3", "credit +2"]), O("Skip it", "Small risk later.", (0.85, "Nothing happens.", []), (0.15, "A real attack hits.", ["inc biz -20 12"]))]),
    ("gen_flood", "Burst pipe", "A burst pipe floods part of {business}.",
     [O("Fix it properly", "Costs 1 h.", ["cash -1"]), O("Quick patch", "30% it bursts again.", (0.7, "It holds.", ["cash -0.2"]), (0.3, "It bursts again.", ["cash -0.2", "inc biz -15 12"]))]),
    ("gen_loyalty", "Loyalty programme", "Marketing pitches a loyalty programme for {business}.",
     [O("Launch it", "Costs 1 h.", ["cash -1", "inc biz +12 36"]), O("Not now", "Nothing changes.", [])]),
    ("gen_price_war", "Price war", "A rival slashes prices against {business}.",
     [O("Match their prices", "Margins dip.", ["inc biz -8 24"]), O("Compete on quality", "Costs 1 h.", ["cash -1", "inc biz +5 24"]), O("Do nothing", "50% customers stay.", (0.5, "Customers stay loyal.", []), (0.5, "Customers drift away.", ["inc biz -15 24"]))]),
    ("gen_mentor", "The Chairman calls", "The Chairman suggests {business} needs a sharper strategy.",
     [O("Take the advice", "Costs 0.5 h.", ["cash -0.5", "inc biz +10 24"]), O("Politely ignore", "Nothing changes.", [])]),
    ("gen_eco_audit", "Eco audit", "Investors ask for an environmental audit of {business}.",
     [O("Commission it", "Costs 0.5 h.", ["cash -0.5", "credit +5"]), O("Refuse", "Some investors grumble.", ["credit -3"])]),
    ("gen_city_fair", "Trade fair", "A trade fair could showcase {business}.",
     [O("Big stand", "Costs 1 h.", ["cash -1", "inc biz +12 24"]), O("Small stand", "Costs 0.3 h.", ["cash -0.3", "inc biz +4 24"]), O("Skip", "Nothing changes.", [])]),
    ("gen_patent_troll", "Patent troll", "A patent troll claims {business} infringes a vague patent.",
     [O("Pay them off", "Costs 0.7 h.", ["cash -0.7"]), O("Fight", "70% win.", (0.7, "The claim is thrown out.", ["cash -0.3"]), (0.3, "You lose.", ["cash -2"]))]),
    ("gen_wellness", "Wellness programme", "HR wants a wellness programme at {business}.",
     [O("Fund it", "Costs 0.5 h.", ["cash -0.5", "inc biz +6 36"]), O("Not now", "Nothing changes.", [])]),
    ("gen_data_sale", "Sell customer data?", "A broker wants to buy {business}'s customer data.",
     [O("Sell", "Gain 1.5 h; 40% backlash.", (0.6, "Nobody notices.", ["cash +1.5"]), (0.4, "A privacy scandal.", ["cash +1.5", "inc biz -20 24", "credit -10"])), O("Refuse", "Nothing changes.", [])]),
    ("gen_mergers_bankers", "Bankers' pitch", "Investment bankers pitch a bolt-on acquisition for {business}.",
     [O("Do the deal", "Costs 3 h.", (0.65, "A perfect fit.", ["cash -3", "inc biz +35 36"]), (0.35, "A messy integration.", ["cash -3", "inc biz +5 24"])), O("Pass", "Nothing changes.", [])]),
    ("gen_heat", "Air-conditioning fails", "The air conditioning at {business} fails during a heatwave.",
     [O("Emergency repair", "Costs 0.5 h.", ["cash -0.5"]), O("Open the windows", "Productivity drops.", ["inc biz -10 12"])]),
    ("gen_volunteer", "Volunteer day", "Staff at {business} want a paid volunteer day.",
     [O("Approve", "Donation of 0.3 h.", ["donate 0.3", "inc biz +5 24"]), O("Decline", "Nothing changes.", [])]),
    ("gen_influencer", "Influencer deal", "An influencer offers to promote {business}.",
     [O("Pay them", "Costs 0.7 h.", (0.6, "Followers flock in.", ["cash -0.7", "inc biz +18 18"]), (0.4, "A flop.", ["cash -0.7"])), O("Decline", "Nothing changes.", [])]),
    ("gen_quality_award", "Quality certification", "{business} could earn a quality certification.",
     [O("Go for it", "Costs 1 h.", ["cash -1", "inc biz +10 36", "credit +3"]), O("Not worth it", "Nothing changes.", [])]),
    ("gen_night_shift", "Night shift", "Adding a night shift would raise {business}'s output.",
     [O("Add it", "Costs 1 h to set up.", ["cash -1", "inc biz +15 24"]), O("No", "Nothing changes.", [])]),
    ("gen_old_equipment", "Old equipment", "Equipment at {business} is wearing out.",
     [O("Replace it", "Costs 2 h.", ["cash -2", "inc biz +12 36"]), O("Keep it running", "25% breakdown.", (0.75, "It keeps going.", []), (0.25, "It breaks down.", ["inc biz -20 12"]))]),
    ("gen_union_drive", "Union drive", "Workers at {business} want to unionise.",
     [O("Welcome it", "Costs 0.5 h. Morale up.", ["cash -0.5", "inc biz +6 36"]), O("Stay neutral", "Nothing changes.", [])]),
    ("gen_holiday_party", "Holiday party", "It's time for {business}'s holiday party.",
     [O("Go big", "Costs 0.5 h.", ["cash -0.5", "inc biz +8 24"]), O("Keep it small", "Free.", [])]),
    ("gen_supply_deal", "Bulk supply deal", "A supplier offers {business} a bulk discount for paying upfront.",
     [O("Pay upfront", "Costs 1 h now; cheaper later.", ["cash -1", "inc biz +12 36"]), O("Pay as you go", "Nothing changes.", [])]),
    ("gen_relocate_hq", "Relocate the office", "A cheaper office for {business} has come up across town.",
     [O("Move", "A short disruption, then savings.", ["inc biz -10 6", "inc biz +8 36"]), O("Stay", "Nothing changes.", [])]),
    ("gen_competitor_merger", "Rivals merge", "Two competitors of {business} are merging.",
     [O("Poach their staff", "Costs 1 h.", ["cash -1", "inc biz +12 24"]), O("Wait and see", "Nothing changes.", [])]),
    ("gen_mystery_shopper", "Mystery shopper", "A mystery-shopper report on {business} is grim.",
     [O("Retrain staff", "Costs 0.5 h.", ["cash -0.5", "inc biz +10 24"]), O("Shrug", "Sales slip.", ["inc biz -6 24"])]),
]
for gid, title, text, opts in GENERIC:
    card(gid, "business", 1, "biz:any", title, text, *opts)

# ----- World and economy cards -----
WORLD_CARDS = [
    ("w_bond_offer", "Government bonds", "The treasury offers you a special bond at a good rate.",
     [O("Buy", "Costs 2 h now; pays 2.6 h later.", ["cash -2", "inc all +3 24"]), O("Decline", "Nothing changes.", [])]),
    ("w_recession_plan", "Recession plan", "Economists warn of a slowdown.",
     [O("Cut costs", "Income dips, but you are leaner.", ["inc all -3 12", "credit +5"]), O("Invest through it", "Costs 2 h.", ["cash -2", "inc all +6 24"]), O("Do nothing", "Nothing changes.", [])]),
    ("w_minimum_wage", "Minimum wage rise", "The government raises the minimum wage.",
     [O("Absorb it", "Income dips a little.", ["inc all -3 24"]), O("Automate", "Costs 2 h.", ["cash -2", "inc all +2 36"])]),
    ("w_currency_swing", "Currency swing", "The dollar swings wildly against other currencies.",
     [O("Hedge", "Costs 1 h. Protected.", ["cash -1"]), O("Ride it", "50/50.", (0.5, "It swings your way.", ["inc all +5 24"]), (0.5, "It swings against you.", ["inc all -5 24"]))]),
    ("w_infrastructure", "Infrastructure bill", "A huge infrastructure bill passes.",
     [O("Lobby for contracts", "Costs 1 h.", (0.6, "You win contracts.", ["cash -1", "inc all +6 36"]), (0.4, "Nothing comes of it.", ["cash -1"])), O("Stay out", "Nothing changes.", [])]),
    ("w_election", "Election year", "A tight election makes markets nervous.",
     [O("Stay neutral", "Nothing changes.", []), O("Fund voter-registration drives", "Donation of 1 h (deductible).", ["donate 1", "credit +3"])]),
    ("w_carbon_tax", "Carbon tax", "A carbon tax is coming next year.",
     [O("Go green early", "Costs 2 h.", ["cash -2", "inc all +3 48"]), O("Wait", "Small cost later.", ["inc all -2 24"])]),
    ("w_talent_visa", "Talent visas", "A new visa scheme makes hiring engineers abroad easier.",
     [O("Recruit abroad", "Costs 1 h.", ["cash -1", "inc all +4 36"]), O("Hire locally", "Nothing changes.", [])]),
    ("w_shipping_canal", "Canal blocked", "A container ship is stuck in a major canal.",
     [O("Air-freight critical parts", "Costs 1 h.", ["cash -1"]), O("Wait it out", "Delays for a day.", ["inc all -4 18"])]),
    ("w_heatwave", "Heatwave", "A brutal heatwave slows everything down.",
     [O("Air-condition everything", "Costs 0.5 h.", ["cash -0.5"]), O("Shorter hours", "Income dips.", ["inc all -3 12"])]),
    ("w_market_rally", "Market rally", "Stock markets hit record highs. Your bankers suggest raising capital.",
     [O("Raise capital", "Gain 1.5 h.", ["cash +1.5", "credit -3"]), O("No thanks", "Nothing changes.", [])]),
    ("w_bank_offer", "Rating agency visit", "A rating agency wants to review your books.",
     [O("Open the books", "Costs 0.3 h.", (0.7, "They are impressed.", ["cash -0.3", "credit +15"]), (0.3, "They find loose ends.", ["cash -0.3", "credit -5"])), O("Decline", "Nothing changes.", [])]),
    ("w_lottery_town", "Town lottery", "A town names a lottery after your company.",
     [O("Match the jackpot", "Donation of 0.5 h.", ["donate 0.5", "inc all +2 24"]), O("Politely decline", "Nothing changes.", [])]),
    ("w_university", "University chair", "A university offers to name a chair after you.",
     [O("Endow it", "Donation of 2 h (deductible).", ["donate 2", "credit +5"]), O("Fund a scholarship", "Donation of 0.5 h.", ["donate 0.5"]), O("Decline", "Nothing changes.", [])]),
    ("w_museum_wing", "Museum wing", "The city museum wants a new wing with your name on it.",
     [O("Fund the wing", "Donation of 3 h.", ["donate 3", "credit +8"]), O("Fund an exhibition", "Donation of 0.5 h.", ["donate 0.5"]), O("Decline", "Nothing changes.", [])]),
    ("w_disaster_relief", "Disaster relief", "An earthquake abroad leaves thousands homeless.",
     [O("Donate generously", "Donation of 2 h.", ["donate 2", "credit +5"]), O("Send supplies", "Donation of 0.5 h.", ["donate 0.5"]), O("Do nothing", "Nothing changes.", [])]),
    ("w_green_bond", "Green bond", "Investors want to buy a green bond from your holding.",
     [O("Issue it", "Gain 2 h; small cost later.", ["cash +2", "inc all -1 48"]), O("No", "Nothing changes.", [])]),
    ("w_global_summit", "Global summit", "You are invited to speak at a global economic summit.",
     [O("Go", "Costs 0.5 h. Connections pay off.", ["cash -0.5", "inc all +3 24", "credit +3"]), O("Send a deputy", "Nothing changes.", [])]),
    ("w_research_grant", "Research grant", "The government matches private R&D spending this year.",
     [O("Spend on R&D", "R&D credit of 2 h.", ["rd 2", "inc all +3 36"]), O("Not this year", "Nothing changes.", [])]),
    ("w_strike_wave", "Strike wave", "Strikes spread across the country.",
     [O("Settle early everywhere", "Costs 1.5 h.", ["cash -1.5"]), O("Hold firm", "60% it passes.", (0.6, "The wave passes.", []), (0.4, "Your workers join in.", ["inc all -6 12"]))]),
]
for wid, title, text, opts in WORLD_CARDS:
    card(wid, "world", 2, "none", title, text, *opts)

# ----- Personal, PR and finance-flavoured cards -----
PERSONAL = [
    ("p_documentary", "Documentary", "A streaming service wants to film a documentary about your rise.",
     [O("Agree", "50%: flattering; 50%: awkward.", (0.5, "It's flattering.", ["inc all +4 36"]), (0.5, "It's awkward.", ["credit -3"])), O("Decline", "Nothing changes.", [])], 2),
    ("p_magazine_cover", "Magazine cover", "A business magazine wants you on its cover.",
     [O("Do the shoot", "Free publicity.", ["inc all +2 24"]), O("Decline", "Nothing changes.", [])], 2),
    ("p_book_deal", "Book deal", "A publisher wants your memoir.",
     [O("Write it", "Gain 0.5 h; a little time away.", ["cash +0.5", "inc all -1 12"]), O("Decline", "Nothing changes.", [])], 3),
    ("p_yacht_party", "Yacht party", "A rival billionaire invites you to a yacht party in Monaco.",
     [O("Go", "Costs 0.5 h; deals happen on yachts.", (0.6, "You close a great deal.", ["cash -0.5", "inc all +4 24"]), (0.4, "Just a party.", ["cash -0.5"])), O("Stay home", "Nothing changes.", [])], 3),
    ("p_ted_talk", "Big talk", "You are invited to give a famous talk.",
     [O("Give the talk", "Talent wants to work for you.", ["inc all +3 36"]), O("Decline", "Nothing changes.", [])], 3),
    ("p_health_scare", "Doctor's orders", "Your doctor says you need a week off.",
     [O("Take the week", "Income dips a little.", ["inc all -3 24"]), O("Work through it", "30% you burn out.", (0.7, "You're fine.", []), (0.3, "You burn out.", ["inc all -8 24"]))], 2),
    ("p_auditor_offer", "Accounting firm", "A top accounting firm offers to tidy up your books before tax time.",
     [O("Hire them", "Costs 0.5 h.", ["cash -0.5", "credit +5"]), O("Decline", "Nothing changes.", [])], 4),
    ("p_private_bank", "Private bank", "A private bank offers you a line of credit.",
     [O("Open an account", "Rating improves a little.", ["credit +5"]), O("Decline", "Nothing changes.", [])], 3),
    ("p_late_payment", "Late payment", "A big customer pays three months late.",
     [O("Factor the invoice", "Costs 0.3 h.", ["cash -0.3"]), O("Chase them", "70% they pay.", (0.7, "They pay.", []), (0.3, "They never pay.", ["cash -1"]))], 2),
    ("p_charity_gala", "Charity gala", "A charity gala wants you as its guest of honour.",
     [O("Attend and donate", "Donation of 1 h.", ["donate 1", "credit +3"]), O("Send a cheque", "Donation of 0.3 h.", ["donate 0.3"]), O("Decline", "Nothing changes.", [])], 2),
    ("p_foundation", "Start a foundation", "Your advisors suggest starting a family foundation.",
     [O("Start it", "Donation of 3 h.", ["donate 3", "credit +8"]), O("Not yet", "Nothing changes.", [])], 4),
    ("p_art_advisor", "Art advisor", "An art advisor says the next auction has a sleeper hit.",
     [O("Pay for the tip", "Costs 0.2 h.", (0.5, "A useful tip.", ["cash -0.2", "inc all +2 12"]), (0.5, "Nothing useful.", ["cash -0.2"])), O("Decline", "Nothing changes.", [])], 3),
    ("p_crypto_pitch", "Crypto pitch", "A founder pitches you a new coin.",
     [O("Invest a little", "Costs 1 h. 25% moonshot.", (0.25, "It moons!", ["cash -1", "cash +4"]), (0.75, "It rugs.", ["cash -1"])), O("Pass", "Nothing changes.", [])], 3),
    ("p_old_friend", "An old friend", "A friend from your kiosk days needs a loan.",
     [O("Lend it", "Costs 0.5 h. 70% repaid.", (0.7, "Repaid with thanks.", []), (0.3, "Never repaid.", ["cash -0.5"])), O("Give it as a gift", "Donation of 0.5 h.", ["donate 0.5"]), O("Decline", "Nothing changes.", [])], 1),
    ("p_sabbatical", "Sabbatical", "Your top manager asks for a sabbatical.",
     [O("Grant it", "Income dips a little.", ["inc all -2 24", "inc all +4 48"]), O("Refuse", "Nothing changes.", [])], 2),
    ("p_family_office", "Family office", "Your advisors suggest a family office to manage your wealth.",
     [O("Set one up", "Costs 1 h.", ["cash -1", "credit +10"]), O("Not now", "Nothing changes.", [])], 4),
]
for pid, title, text, opts, ch in PERSONAL:
    card(pid, "personal", ch, "none", title, text, *opts)

# ----- Rival interaction cards ({rival} is bound when the card is drawn) -----
card("r_acquisition_offer", "acquisition", 3, "biz:any", "Acquisition offer", "{rival} offers to buy {business} at a premium.",
     O("Sell", "You receive 2.5× its valuation and free a slot.", ["sell 2.5", "rel +10"]),
     O("Counter at 3×", "50%: they agree; 50%: they walk away.", (0.5, "They agree.", ["sell 3.0", "rel +5"]), (0.5, "They walk away.", ["rel -5"])),
     O("Decline", "You keep the business.", ["rel -5"]), weight=0.8)
card("r_acquisition_lowball", "acquisition", 3, "biz:any", "A cheeky offer", "{rival} sends a lowball offer for {business}.",
     O("Laugh it off", "Nothing changes.", ["rel -3"]),
     O("Accept 2×", "You sell for 2× valuation.", ["sell 2.0", "rel +8"]), weight=0.6)
card("r_joint_venture", "jv", 3, "biz:any", "Joint venture", "{rival} proposes a joint venture with {business}.",
     O("Invest", "Costs 1.5 h. Income rises for a day and a half.", ["cash -1.5", "inc biz +30 36", "rel +10"]),
     O("Small pilot", "Costs 0.5 h.", ["cash -0.5", "inc biz +10 24", "rel +5"]),
     O("Decline", "Nothing changes.", ["rel -3"]), weight=1.0)
card("r_joint_venture_tech", "jv", 4, "biz:any", "Shared research", "{rival} wants to share research costs with {business}.",
     O("Agree", "R&D credit of 1 h; income rises.", ["rd 1", "inc biz +15 36", "rel +8"]),
     O("Decline", "Nothing changes.", ["rel -2"]), weight=0.8)
card("r_takeover", "takeover", 4, "biz:any", "Hostile takeover!", "{rival} is buying up shares to seize {business}. Until you respond, its income is 10% lower.",
     O("Buy back shares", "Costs 4 h. Defends it for sure.", ["cash -4", "rel -10"]),
     O("Poison pill", "Costs 1.5 h and hurts your rating. Defends it for sure.", ["cash -1.5", "credit -15", "rel -15"]),
     O("Call a white knight", "An ally (relationship 40+) steps in. Free.", ["rel -10"], requires="ally"),
     O("Negotiate", "55%: they back off; 45%: you sell at 1.2×.", (0.55, "They back off.", ["rel -5"]), (0.45, "They win; you sell at 1.2× valuation.", ["sell 1.2"])),
     O("Accept the premium", "Sell at 2.5× valuation.", ["sell 2.5", "rel +10"]), weight=0.6)
card("r_takeover_raid", "takeover", 5, "biz:any", "Corporate raid", "{rival}'s fund has built a large stake in {business} overnight. Its income is 10% lower until you act.",
     O("Buy back shares", "Costs 4 h. Defends it for sure.", ["cash -4", "rel -10"]),
     O("Poison pill", "Costs 1.5 h and hurts your rating.", ["cash -1.5", "credit -15", "rel -15"]),
     O("Call a white knight", "An ally (relationship 40+) steps in. Free.", ["rel -10"], requires="ally"),
     O("Accept the premium", "Sell at 2.8× valuation.", ["sell 2.8", "rel +10"]), weight=0.5)
card("r_poach", "rival", 3, "biz:any", "Poaching war", "{rival} is poaching managers from {business}.",
     O("Counter-offer", "Costs 1 h.", ["cash -1", "rel -5"]),
     O("Let them go", "Income dips.", ["inc biz -10 24"]),
     O("Call {rival}", "50%: a truce; 50%: they laugh.", (0.5, "A gentlemen's agreement.", ["rel +10"]), (0.5, "They keep poaching.", ["inc biz -10 24", "rel -5"])))
card("r_charity_challenge", "rival", 3, "none", "Charity challenge", "{rival} challenges you to match a charity donation.",
     O("Match it", "Donation of 1 h.", ["donate 1", "rel +10", "credit +3"]),
     O("Double it", "Donation of 2 h.", ["donate 2", "rel +15", "credit +5"]),
     O("Decline", "Nothing changes.", ["rel -5"]))
card("r_dinner", "rival", 2, "none", "Dinner invitation", "{rival} invites you to dinner.",
     O("Accept", "Costs nothing but time.", ["rel +10"]),
     O("Decline", "Nothing changes.", ["rel -3"]))
card("r_public_feud", "rival", 3, "none", "Public feud", "{rival} mocks your company in an interview.",
     O("Fire back", "50%: the public loves it.", (0.5, "The public is on your side.", ["inc all +3 24", "rel -10"]), (0.5, "You look petty.", ["credit -3", "rel -10"])),
     O("Stay classy", "Nothing changes.", ["rel +3"]))
card("r_tip", "rival", 3, "none", "A tip from a friend", "{rival} tips you off about a coming boom.",
     O("Act on it", "Costs 1 h. 60% right.", (0.6, "They were right.", ["cash -1", "inc all +6 24"]), (0.4, "They were wrong.", ["cash -1"])),
     O("Thank them", "Relationship improves.", ["rel +5"]))
card("r_partnership_city", "jv", 4, "none", "City partnership", "{rival} wants to co-develop a district together.",
     O("Partner", "Costs 2 h.", ["cash -2", "inc all +5 36", "rel +10"]),
     O("Decline", "Nothing changes.", ["rel -3"]))
card("r_auction_truce", "rival", 3, "none", "Auction truce", "{rival} proposes not to bid against each other at the next auction.",
     O("Agree", "Relationship improves.", ["rel +10"]),
     O("Refuse", "Relationship worsens.", ["rel -5"]))
card("r_lawsuit_threat", "rival", 4, "biz:any", "Legal threat", "{rival}'s lawyers claim {business} copied their design.",
     O("Settle", "Costs 1 h.", ["cash -1", "rel +3"]),
     O("Fight", "65% win.", (0.65, "You win.", ["cash -0.3", "rel -10"]), (0.35, "You lose.", ["cash -2", "rel -5"])))
card("r_board_seat", "rival", 4, "none", "Board seat", "{rival} offers you a seat on their company's board.",
     O("Accept", "Relationship and rating improve.", ["rel +15", "credit +5"]),
     O("Decline", "Nothing changes.", []))
card("r_mentor_rival", "rival", 2, "none", "Unexpected advice", "{rival} sends you a note with surprisingly good advice.",
     O("Take it", "Income rises a little.", ["inc all +3 24", "rel +5"]),
     O("Ignore it", "Nothing changes.", []))

card("r_bidding_war", "rival", 3, "none", "Bidding war", "{rival} is bidding against you for a prime office tower.",
     O("Outbid them", "Costs 2 h.", ["cash -2", "inc all +4 36", "rel -8"]),
     O("Let them have it", "Relationship improves.", ["rel +5"]))
card("r_sector_alliance", "jv", 3, "none", "Industry alliance", "{rival} proposes an industry alliance to lobby for lower tariffs.",
     O("Join", "Costs 1 h.", (0.6, "The lobbying works.", ["cash -1", "inc all +4 36", "rel +8"]), (0.4, "The lobbying fails.", ["cash -1", "rel +8"])),
     O("Stay out", "Nothing changes.", ["rel -2"]))
card("r_spy", "rival", 4, "biz:any", "Industrial espionage", "Security catches someone from {rival}'s company photographing {business}'s plans.",
     O("Go to the press", "Your rating rises; they are furious.", ["credit +3", "rel -15"]),
     O("Handle it quietly", "Relationship survives.", ["rel -3"]),
     O("Feed them fake plans", "60%: they waste a fortune.", (0.6, "They chase a dead end.", ["inc biz +10 24", "rel -5"]), (0.4, "They spot the trick.", ["rel -10"])))
card("r_rescue", "rival", 4, "none", "A rival in trouble", "{rival}'s company is in trouble and asks you for a bridge loan.",
     O("Lend generously", "Costs 2 h; they will remember.", (0.8, "They repay with interest.", ["cash -2", "cash +2.4", "rel +20"]), (0.2, "They default.", ["cash -2", "rel +10"])),
     O("Decline", "Nothing changes.", ["rel -5"]))
card("r_golf", "rival", 2, "none", "Golf weekend", "{rival} invites you to a golf weekend in Scotland.",
     O("Go", "Costs 0.3 h; good for relations.", ["cash -0.3", "rel +12"]),
     O("Decline", "Nothing changes.", []))
WORLD2 = [
    ("w_port_strike", "Port strike", "Dock workers strike at the world's busiest port.",
     [O("Charter planes", "Costs 1 h.", ["cash -1"]), O("Wait it out", "A slow day.", ["inc all -4 12"])]),
    ("w_tourism_boom", "Tourism boom", "A weak currency brings a flood of tourists.",
     [O("Advertise abroad", "Costs 0.5 h.", ["cash -0.5", "inc all +4 24"]), O("Enjoy it", "A small lift.", ["inc all +1 24"])]),
    ("w_regulation_wave", "New regulations", "A wave of new regulations lands on business.",
     [O("Hire compliance staff", "Costs 1 h.", ["cash -1", "credit +5"]), O("Do the minimum", "25% a fine.", (0.75, "No trouble.", []), (0.25, "A fine.", ["cash -1.5", "credit -5"]))]),
    ("w_tech_conference", "Tech conference", "The world's biggest tech conference is in town.",
     [O("Sponsor it", "Costs 0.7 h.", ["cash -0.7", "inc all +3 24"]), O("Just attend", "Nothing changes.", [])]),
    ("w_food_inflation", "Food inflation", "Food prices are up 12% this year.",
     [O("Raise staff wages", "Costs 1 h; loyalty up.", ["cash -1", "inc all +2 36"]), O("Hold wages", "Morale dips.", ["inc all -2 24"])]),
]
for wid, title, text, opts in WORLD2:
    card(wid, "world", 2, "none", title, text, *opts)

# ----- Nemesis intro cards (one option, story only) -----
for chn, (name, beats) in NEMESES.items():
    card(f"nemesis_{chn}", "nemesis", chn, "none", "Your nemesis", beats[0],
         O("Bring it on", "", []), O("Study their playbook", "", []), weight=0.0)


def main(repo: Path) -> None:
    rng = random.Random(SEED)
    arch = {a[0]: a for a in ARCHETYPES}
    rivals = []
    used_names = set()

    def add(name, archetype, city, sectors, source, base, rid=None):
        assert name not in used_names, name
        used_names.add(name)
        a = arch[archetype]
        aggr = round(rng.uniform(*a[5]), 2)
        risk = round(rng.uniform(*a[6]), 2)
        rivals.append({
            "id": rid or ascii_id(name), "name": name, "archetype": archetype, "city": city, "sectors": sectors,
            "source": source, "age": rng.randint(34, 92) if archetype != "heir" else rng.randint(28, 55),
            "aggression": aggr, "risk": risk, "base": round(base, 3),
            # Relative growth (1.0 = the reference curve); archetypes differ so ranks reshuffle over time.
            "growth": round(a[3] / 0.62 * rng.uniform(0.94, 1.06), 4),
            "wobble": round(a[4] * rng.uniform(0.7, 1.3), 3),
            "periodDays": round(rng.uniform(2.0, 6.0), 2),
        })

    for n, a, city, sec, src, base in BIDDERS:
        add(n, a, city, sec, src, base, rid=bidder_id(n))
    for n, a, city, sec, src, base in PARODIES + STORY:
        add(n, a, city, sec, src, base)
    # Invented rivals fill the list to 220, log-uniformly between $1B and $30B.
    regions = list(REGION_WEIGHTS)
    weights = [REGION_WEIGHTS[r] for r in regions]
    archs = list(ARCH_WEIGHTS)
    aw = [ARCH_WEIGHTS[a] for a in archs]
    famous_last = {n.split()[-1] for n, *_ in BIDDERS + PARODIES}
    while len(rivals) < 220:
        reg = rng.choices(regions, weights)[0]
        name = f"{rng.choice(FIRST[reg])} {rng.choice(LAST[reg])}"
        if name in used_names or name.split()[-1] in famous_last:
            continue
        a = rng.choices(archs, aw)[0]
        sec = rng.sample(SECTORS[:-1], 2 if rng.random() < 0.35 else 1)
        src = rng.choice(SOURCES[sec[0]])
        add(name, a, rng.choice(REGION_CITIES[reg]), sec, src, rng.uniform(9.0, 10.45))

    # Calibration on game time (Phase 9 trajectory, active bot): at game day 4.3 the player has about $10^17.6, so
    # the richest rival is placed there; the curve's early slope is solved for that.
    def g(d):
        return CURVE["earlyPerDay"] * min(d, CURVE["kneeDays"]) + CURVE["latePerDay"] * max(0.0, d - CURVE["kneeDays"])

    def nw(r, d):
        return r["base"] + r["growth"] * g(d)

    top = max(rivals, key=lambda r: r["base"] + r["growth"] * 4.3)
    CURVE["earlyPerDay"] = round((17.6 - top["base"]) / (top["growth"] * 4.3), 4)
    ids = [r["id"] for r in rivals]
    assert len(set(ids)) == len(ids), "duplicate rival ids"

    by_name = {r["name"]: r for r in rivals}
    nemeses = [{"chapter": ch, "rival": by_name[name]["id"], "beats": beats} for ch, (name, beats) in sorted(NEMESES.items())]

    # Portraits (owner decision 2026-11-06): AI caricatures for the 40 rivals players see most: the 24 auction bidders,
    # the chapter nemeses, then the richest of the rest at day 0. Everyone else gets a monogram avatar.
    portrait_ids = [bidder_id(n) for n, *_ in BIDDERS]
    for nm in nemeses:
        if nm["rival"] not in portrait_ids:
            portrait_ids.append(nm["rival"])
    for r in sorted(rivals, key=lambda r: -r["base"]):
        if len(portrait_ids) >= 40:
            break
        if r["id"] not in portrait_ids:
            portrait_ids.append(r["id"])
    for r in rivals:
        r["portrait"] = r["id"] in portrait_ids

    out = {
        "archetypes": [{"id": a[0], "name": a[1], "description": a[2]} for a in ARCHETYPES],
        "rivals": rivals,
        "nemeses": nemeses,
        "events": EV,
        "cards": CARDS,
        "config": {
            "unlocks": {"cards": 1, "fortune": 2, "events": 2, "loans": 3, "acquisitions": 3, "taxes": 4, "takeovers": 4},
            "cards": {"firstAtMinutes": 30, "everyHours": 4.0, "jitterMin": 0.75, "jitterMax": 1.5, "maxQueued": 3, "recentWindow": 24,
                       "takeoverPenalty": 0.10, "allyRelation": 40},
            "rivalCurve": CURVE,
            "events": {"firstAfterHours": 6.0, "everyHoursMin": 16.0, "everyHoursMax": 30.0, "maxActive": 3,
                        "disasterDamageMin": 250, "disasterDamageMax": 500},
            "finance": {
                "scoreStart": 700, "scoreMin": 300, "scoreMax": 850, "onTimePoints": 3, "missedPoints": -25, "foreclosurePoints": -60,
                "auditPoints": -40, "restructurePoints": -40, "recoverTo": 680, "recoverPer6h": 1, "phoenixScore": 600,
                "dti": [{"days": 1.0, "points": 0}, {"days": 3.0, "points": -20}, {"days": 7.0, "points": -50}, {"days": 1.0e9, "points": -80}],
                "ratings": [
                    {"name": "AAA", "min": 800, "spreadPerDay": 0.002, "borrowFactor": 2.0, "maxLtv": 0.75},
                    {"name": "AA", "min": 760, "spreadPerDay": 0.003, "borrowFactor": 1.7, "maxLtv": 0.75},
                    {"name": "A", "min": 720, "spreadPerDay": 0.005, "borrowFactor": 1.4, "maxLtv": 0.6},
                    {"name": "BBB", "min": 680, "spreadPerDay": 0.008, "borrowFactor": 1.0, "maxLtv": 0.6},
                    {"name": "BB", "min": 640, "spreadPerDay": 0.013, "borrowFactor": 0.8, "maxLtv": 0.5},
                    {"name": "B", "min": 600, "spreadPerDay": 0.02, "borrowFactor": 0.6, "maxLtv": 0.5},
                    {"name": "CCC", "min": 550, "spreadPerDay": 0.04, "borrowFactor": 0.5, "maxLtv": 0.25},
                    {"name": "D", "min": 0, "spreadPerDay": 0.06, "borrowFactor": 0.0, "maxLtv": 0.0},
                ],
                "loanTermsDays": [1, 3, 7], "borrowIncomeHours": 24.0, "borrowNetWorthShare": 0.4, "creditLineIncomeHours": 12.0,
                "missedPenalty": 0.02,
                "taxBrackets": [
                    {"upToPerHour": "1000000000.00", "rate": 0.15},
                    {"upToPerHour": "1000000000000.00", "rate": 0.22},
                    {"upToPerHour": "1000000000000000.00", "rate": 0.28},
                    {"upToPerHour": None, "rate": 0.35},
                ],
                "quarterHours": 72, "filingWindowHours": 24,
                "deductions": {"rd": 0.03, "rdPoolCap": 0.03, "depreciationPerProperty": 0.01, "depreciationMax": 0.04, "green": 0.03, "donationCap": 0.05, "max": 0.12},
                "rdBusinesses": ["tech", "pharma", "space"], "greenBusinesses": ["energy"],
                "aggressiveExtraMin": 0.05, "aggressiveExtraMax": 0.10, "auditChanceMin": 0.15, "auditChanceMax": 0.35, "auditPenaltyMultiple": 2.0,
                "phoenixNegativeMinutes": 30, "phoenixKeepLevels": 0.1, "phoenixBoost": 3.0, "phoenixBoostHours": 2,
            },
        },
    }
    path = repo / "content/data/world.json"
    path.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    kinds = {}
    for c in CARDS:
        kinds[c["kind"]] = kinds.get(c["kind"], 0) + 1
    print(f"wrote {path}: {len(rivals)} rivals ({sum(r['portrait'] for r in rivals)} with portraits), {len(EV)} events, "
          f"{len(CARDS)} cards {kinds}, nemeses {len(nemeses)}")
    lo0 = min(r["base"] for r in rivals)
    for d in (0, 0.7, 1, 2, 3, 4.3, 6, 7.2, 10):
        vals = sorted((nw(r, d) for r in rivals), reverse=True)
        print(f"  day {d:>2}: #1 10^{vals[0]:.2f}  #50 10^{vals[49]:.2f}  #220 10^{vals[-1]:.2f}")
    assert lo0 >= 8.99


if __name__ == "__main__":
    main(Path(sys.argv[1]))

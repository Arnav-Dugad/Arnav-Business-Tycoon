"""Writes tools/art/portraits/rivals.json: image prompts for the rivals marked "portrait" in content/data/world.json.

Rivals with parody names get caricatures of *invented* people that hint at a type (tech founder, oil baron, heir) and
never describe or copy a real person's face (GDD §1.1, ART_PIPELINE §3a). Existing generation results (Figma node ids,
source URLs) in rivals.json are kept, so re-running only adds new entries.

Usage: python -I tools/art/portraits/gen_rival_prompts.py <repo-root>
"""
import json
import sys
from pathlib import Path

FEMALE = {"Françoise Bettencorte", "Alice Waltz", "Opra Windfrey", "Jacqueline Marz", "Abigail Johnsun", "Gina Rhinehart", "Vivienne Strand"}

STYLE = {
    "shark": "a sharp tailored suit, an intense, confident grin and slicked-back hair",
    "old_money": "a tweed jacket with a silk pocket square, silver hair and a calm, amused expression",
    "disruptor": "a plain black turtleneck, restless energy and a mischievous half-smile",
    "collector": "a velvet dinner jacket, a jeweller's loupe in one hand and a gilded picture frame behind",
    "philanthropist": "a soft cardigan, kind eyes and a warm, open smile",
    "gambler": "a loud patterned blazer, a playful wink and a poker chip between the fingers",
    "recluse": "a dark overcoat, tinted glasses and half of the face in soft shadow",
    "heir": "designer clothes, statement cufflinks or pearls and a determined, slightly defiant look",
}

PROP = {
    "Electric cars and reusable rockets": "a small model rocket", "Online everything store": "a stack of delivery boxes",
    "Database software": "a glowing server rack in the background", "Social networks": "a phone covered in like-hearts",
    "Luxury fashion houses": "a fabric swatch book", "Search engine": "a magnifying glass",
    "Software and a basketball team": "a basketball", "Patient value investing": "a newspaper folded to the stock pages",
    "Graphics chips": "a shiny computer chip", "Refineries and telecom": "a tiny oil derrick and a phone mast",
    "Desktop software": "an old floppy disk", "Build-to-order computers": "a laptop", "Discount supermarkets": "a shopping basket",
    "Fast fashion": "a clothes rail", "Cosmetics": "a lipstick", "Ports and power": "a toy container ship",
    "Telecoms": "a retro phone handset", "Bottled water and vaccines": "a water bottle", "Messaging and games": "a game controller",
    "Hedge funds": "a tablet showing a rising chart", "Running shoes": "a running shoe", "Talk shows": "a vintage microphone",
    "Airlines, music and space tourism": "a model aeroplane", "Ports and property": "a miniature skyline",
    "Freight forwarding": "a parcel", "Container shipping": "a ship's wheel", "Chocolate spread": "a jar of chocolate spread with no label",
    "A Paris fashion house": "a perfume bottle with no label", "Casual clothing": "a folded jumper", "Short-video app": "a phone on a selfie stick",
    "Group-buying shopping app": "a shopping-cart icon badge", "Candy bars and pet food": "a candy bar with no label",
    "Mutual funds": "a leather portfolio", "Electronic brokerage": "a trading screen", "Private equity": "a fountain pen and contract",
    "Iron ore": "a lump of iron ore", "High-street chains": "a shop-window display",
}


# Appearance notes (added after the first batch came out with near-identical, mostly European faces). Region cues
# follow the rival's home city; they describe an invented person, never a real one.
REGION_BY_CITY = {
    "Mumbai": "South Asian", "Ahmedabad": "South Asian", "Hangzhou": "Chinese", "Shenzhen": "Chinese",
    "Hong Kong": "Chinese", "Beijing": "Chinese", "Shanghai": "Chinese", "Tokyo": "Japanese", "Mexico City": "Mexican",
}
FEATURES = {
    "sergey_brinn": "a neat grey beard, round wire glasses and a bright patterned scarf",
    "mukesh_ambanee": "a neatly trimmed moustache and a cream bandhgala jacket",
    "gautam_adanni": "a full black beard flecked with grey and a crisp white shirt",
    "pony_mah": "short side-parted hair and rimless glasses",
    "zhong_shanshine": "very short silver hair and a gentle smile",
    "lee_ka_shine": "thick black-framed glasses and a dark suit",
    "tadashi_yanaii": "short grey hair and a minimalist navy jacket",
    "zhang_yiminng": "a boyish face, short hair and a grey hoodie",
    "colin_hwang": "a round face, a fringe of black hair and a casual blazer",
    "carlos_slimm": "a thick grey moustache and a relaxed open-collared shirt",
}


# Second wave (owner request 2026-11-10: portraits for every rival). Only rivals without the "portrait" flag use these,
# so the first 40 prompts (and their images) are unchanged.
FEMALE_2 = {
    "Savitri Jindall", "MacKenzie Scotch", "Susanne Klatter", "Iris Fontbonna", "Diane Hendrix", "Mariana Brandão",
    "Sakura Morishita", "Chiara Vianello", "Meera Chaudhary", "Amira Mansour", "Giulia Santangelo", "Fernanda Teixeira",
    "Rafaela Figueiredo", "Layla Al-Qasimi", "Ji-woo Seo", "Valentina Arrieta", "Henrike Steinberg", "Nandini Khanna",
    "Yui Takahara", "Harriet Wexford", "Mariana Sequeira", "Clémence Beaulieu", "Madison Holcomb", "Rafaela Carvalho",
    "Brooke Rutledge", "Paige Rutledge", "Olivia Harrowgate", "Inês Sequeira", "Harriet Ashworth", "Kendall Whitaker",
    "Layla Nassar", "Chiara Moretti", "Charlotte Holloway", "Madison McAllister", "Mei Huang", "Noor Mansour",
    "Eleanor Ashworth", "Xiu Huang", "Blair Delacroix", "Amira Al-Qasimi", "Paige McAllister", "Eleanor Blackwood",
    "Annika Lindqvist", "Lucía Cifuentes", "Priya Khanna", "Meera Malhotra", "Isha Venkataraman", "Margaux Vasseur",
    "Eleanor Montague", "Imogen Sterling", "Madison Rutledge", "Chiara Bellandi", "Adaeze Diallo", "Leonie Albrecht",
    "Madison Castellan", "Olivia Montague",
}
REGION_2 = {
    "Bengaluru": "South Asian", "Delhi": "South Asian", "Pune": "South Asian", "Hyderabad": "South Asian", "Chennai": "South Asian",
    "Ningde": "Chinese", "Foshan": "Chinese", "Singapore": "Singaporean Chinese", "Seoul": "Korean", "Busan": "Korean",
    "Osaka": "Japanese", "Doha": "Arab", "Riyadh": "Arab", "Cairo": "Egyptian", "Lagos": "West African", "Accra": "West African",
    "Johannesburg": "Black South African", "São Paulo": "Brazilian", "Rio de Janeiro": "Brazilian", "Buenos Aires": "Argentine",
    "Santiago": "Chilean",
}
HAIR_M = ["a shaved head", "curly grey hair", "a silver buzz cut", "a bald crown with a white fringe", "spiky dark hair",
          "a swept-back mane of grey hair", "short tight curls", "a neat side parting", "wavy shoulder-length hair", "a receding hairline"]
HAIR_F = ["a sleek silver bob", "long wavy dark hair", "a high ponytail", "a short pixie cut", "a tidy chignon",
          "voluminous curls", "a sharp asymmetric bob", "long straight grey hair", "braids pinned up", "a soft shoulder-length cut"]
EXTRA_M = ["round tortoiseshell glasses", "a bushy moustache", "a neat goatee", "heavy dark eyebrows", "a bold red scarf",
           "a bow tie", "a gold signet ring", "rimless glasses", "a short white beard", "a dimpled chin"]
EXTRA_F = ["cat-eye glasses", "statement earrings", "a silk scarf", "a string of pearls", "bold red lipstick",
           "round gold glasses", "a sharp blazer with wide lapels", "a jade pendant", "freckles", "a gentle laugh line"]


# Overrides after review (2026-11-10): these prompts drifted toward real people or real brands.
FEATURES_2_FIX = {
    "jack_mah": "a wide round face, a thick black pompadour and a bright orange bow tie",
    "jack_dorsee": "a round face, short curly red hair, freckles and a bright yellow raincoat",
}
PROP_2_FIX = {"layla_al_qasimi": "a plain white canvas shoe with no logo, stripes or marks"}


def features_2(r: dict, woman: bool) -> str:
    if r["id"] in FEATURES_2_FIX:
        return FEATURES_2_FIX[r["id"]]
    import hashlib
    h = int(hashlib.sha256(r["id"].encode()).hexdigest(), 16)
    hair, extra = (HAIR_F, EXTRA_F) if woman else (HAIR_M, EXTRA_M)
    return f"{hair[h % len(hair)]} and {extra[(h // 97) % len(extra)]}"


def prompt(r: dict) -> str:
    second = not r.get("portrait")
    woman = r["name"] in FEMALE or (second and r["name"] in FEMALE_2)
    who = "woman" if woman else "man"
    age = r["age"]
    decade = "in her" if who == "woman" else "in his"
    prop = (PROP_2_FIX.get(r["id"]) if second else None) or PROP.get(r["source"]) or (f"a small object that hints at {r['source'].lower()} (no text, no logo)" if second else "a symbol of their business")
    region = REGION_BY_CITY.get(r["city"]) or (REGION_2.get(r["city"]) if second else None)
    extra = FEATURES.get(r["id"]) or (features_2(r, woman) if second else None)
    if region or extra:
        who = f"{region + ' ' if region else ''}{who}{' with ' + extra if extra else ''}"
    return (
        f"Premium editorial caricature portrait, head and shoulders, of an invented billionaire tycoon character: {'an' if who[0] in 'AEIOU' else 'a'} {who} {decade} "
        f"{(age // 10) * 10}s who made a fortune from {r['source'].lower()}, wearing {STYLE[r['archetype']]}, holding {prop}. "
        "Playful exaggerated proportions (slightly larger head, expressive eyes), painterly digital illustration with soft "
        "lighting, smooth deep navy and muted gold background gradient, centered, looking at the viewer. "
        "Entirely invented person, not resembling any real or famous person. No text, no logos, no watermark."
    )


def main(repo: Path) -> None:
    world = json.loads((repo / "content/data/world.json").read_text(encoding="utf-8"))
    path = repo / "tools/art/portraits/rivals.json"
    existing = {e["id"]: e for e in json.loads(path.read_text(encoding="utf-8"))} if path.exists() else {}
    out = []
    for r in world["rivals"]:
        e = existing.get(r["id"], {})
        new_prompt = prompt(r)
        if e and e.get("prompt") != new_prompt:
            e = {}  # the prompt changed: this portrait must be generated again
        out.append({
            "id": r["id"], "name": r["name"], "archetype": r["archetype"], "prompt": new_prompt,
            "model": e.get("model", ""), "figmaFile": e.get("figmaFile", ""), "figmaNodeId": e.get("figmaNodeId", ""),
            "sourceUrl": e.get("sourceUrl", ""), "generatedAt": e.get("generatedAt", ""),
        })
    path.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {path}: {len(out)} prompts ({sum(1 for e in out if e['sourceUrl'])} already generated)")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

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


def prompt(r: dict) -> str:
    who = "woman" if r["name"] in FEMALE else "man"
    age = r["age"]
    decade = "in her" if who == "woman" else "in his"
    prop = PROP.get(r["source"], "a symbol of their business")
    region = REGION_BY_CITY.get(r["city"])
    extra = FEATURES.get(r["id"])
    if region or extra:
        who = f"{region + ' ' if region else ''}{who}{' with ' + extra if extra else ''}"
    return (
        f"Premium editorial caricature portrait, head and shoulders, of an invented billionaire tycoon character: a {who} {decade} "
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
        if not r.get("portrait"):
            continue
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

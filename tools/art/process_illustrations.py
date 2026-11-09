"""Processes the AI-generated illustrations for the app (docs/ART_PIPELINE.md §3b).

Reads tools/art/illustrations/generated.tsv (the first 54 heroes) and results/*.tsv (the second wave: business tiers,
expansions, districts, towers, HQ levels, world events and collection pieces). Each row is set, id, Figma node,
asset id, subject. The cached originals live in tools/art/cache/illustrations/<set>/<id>.png (git-ignored). Writes WebP
files to app/src/main/assets/img/<folder>/<id>.webp and a credits.json in each folder (license AI-GENERATED).

Hero art: landscape 960x640, portrait "scene" 720x1080. Second wave (mostly thumbnails and banners, and there are
hundreds): landscape 768x512, square 480x480, slightly lower quality, to keep the APK small. Deterministic with the
pinned Pillow settings.

Usage: python -I tools/art/process_illustrations.py <repo-root>
"""
import csv
import json
import sys
from pathlib import Path

from PIL import Image

# In generated.tsv "luxury" meant a collection category; in the second wave it means a single piece.
FOLDERS = {"business": "business", "city": "city", "property": "property", "chapter": "chapter", "luxury": "luxurycat", "scene": "scene"}
WAVE2 = {"tier": "tier", "expansion": "expansion", "district": "district", "tower": "tower", "hq": "hq", "event": "event", "luxury": "luxury",
         # Third wave: markets, the store, Desk cards and tenant events (smaller still: mostly row thumbnails).
         "stock": "stock", "sector": "sector", "fund": "fund", "bond": "bond", "coin": "coin", "commodity": "commodity",
         "product": "product", "card": "card", "tenant": "tenant"}
WAVE3 = {"stock", "sector", "fund", "bond", "coin", "commodity", "product", "card", "tenant"}
NOTE = ("Hand-painted-style illustrations generated with Figma AI (gpt-image-2.5-flare) from prompts written for this game. "
        "Invented scenes: no real logos, brands or identifiable people.")


def rows_of(path: Path) -> list[list[str]]:
    return [r for r in csv.reader(path.open(encoding="utf-8"), delimiter="\t") if len(r) >= 2]


def main(repo: Path) -> None:
    base = repo / "tools/art/illustrations"
    cache = repo / "tools/art/cache/illustrations"
    jobs: list[tuple[str, str, str, str, bool]] = []  # folder, set, id, subject, second wave
    for r in rows_of(base / "generated.tsv"):
        jobs.append((FOLDERS[r[0]], r[0], r[1], r[4], False))

    queue = {(r[0], r[1]): r[2] for r in rows_of(base / "queue.tsv")} if (base / "queue.tsv").exists() else {}
    wave2: dict[tuple[str, str], str] = {}
    for f in sorted((base / "results").glob("*.tsv")):
        for r in rows_of(f):
            if r[0] in WAVE2:
                wave2[(r[0], r[1])] = (r[4] if len(r) > 4 else "") or wave2.get((r[0], r[1]), "")
    missing = []
    for (set_, id_), subject in sorted(wave2.items()):
        if not (cache / set_ / f"{id_}.png").exists():
            missing.append(f"{set_}/{id_}")
            continue
        jobs.append((WAVE2[set_], set_, id_, subject or queue.get((set_, id_), id_), True))

    credits: dict[str, list] = {}
    total = 0
    for folder, set_, id_, subject, second in jobs:
        out_dir = repo / "app/src/main/assets/img" / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        with Image.open(cache / set_ / f"{id_}.png") as im:
            im = im.convert("RGB")
            if second and set_ in WAVE3:
                ratio = im.width / im.height
                size = (384, 384) if 0.8 < ratio < 1.25 else (640, 427) if ratio >= 1.25 else (427, 640)
                quality = 70
            elif second:
                ratio = im.width / im.height
                size = (480, 480) if 0.8 < ratio < 1.25 else (768, 512) if ratio >= 1.25 else (512, 768)
                quality = 72
            else:
                size = (720, 1080) if im.height > im.width else (960, 640)
                quality = 78
            im = im.resize(size, Image.LANCZOS)
            dst = out_dir / f"{id_}.webp"
            im.save(dst, "WEBP", quality=quality, method=6)
            total += dst.stat().st_size
        credits.setdefault(folder, []).append({
            "id": id_,
            "title": f"{subject[:1].upper()}{subject[1:]}",
            "author": "AI-generated with Figma AI (gpt-image-2.5-flare)",
            "source": "Figma AI image generation",
            "sourceUrl": "",
            "license": "AI-GENERATED",
            "licenseUrl": "",
            "modifications": f"Resized to {size[0]}x{size[1]}, WebP",
        })
    for folder, items in credits.items():
        doc = {"note": NOTE, "items": items}
        (repo / "app/src/main/assets/img" / folder / "credits.json").write_text(
            json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"processed {len(jobs)} illustrations into {len(credits)} folders, {total / 1024 / 1024:.1f} MB")
    if missing:
        print(f"{len(missing)} listed but not cached (re-download or regenerate):", ", ".join(missing[:20]))


if __name__ == "__main__":
    main(Path(sys.argv[1]))

"""Processes the AI-generated illustrations for the app (docs/ART_PIPELINE.md §3b).

Reads tools/art/illustrations/generated.tsv (set, id, Figma node, asset id, subject) and the cached originals in
tools/art/cache/illustrations/<set>/<id>.png (git-ignored). Writes WebP files to app/src/main/assets/img/<folder>/<id>.webp
and a credits.json in each folder (license AI-GENERATED). Landscape art becomes 960x640; the portrait "scene" art
becomes 720x1080. Deterministic with the pinned Pillow settings.

Usage: python -I tools/art/process_illustrations.py <repo-root>
"""
import csv
import json
import sys
from pathlib import Path

from PIL import Image

FOLDERS = {"business": "business", "city": "city", "property": "property", "chapter": "chapter", "luxury": "luxurycat", "scene": "scene"}
NOTE = ("Hand-painted-style illustrations generated with Figma AI (gpt-image-2.5-flare) from prompts written for this game. "
        "Invented scenes: no real logos, brands or identifiable people.")


def main(repo: Path) -> None:
    rows = list(csv.reader((repo / "tools/art/illustrations/generated.tsv").open(encoding="utf-8"), delimiter="\t"))
    credits: dict[str, list] = {}
    total = 0
    for set_, id_, node, asset, subject in rows:
        src = repo / "tools/art/cache/illustrations" / set_ / f"{id_}.png"
        out_dir = repo / "app/src/main/assets/img" / FOLDERS[set_]
        out_dir.mkdir(parents=True, exist_ok=True)
        with Image.open(src) as im:
            im = im.convert("RGB")
            size = (720, 1080) if im.height > im.width else (960, 640)
            im = im.resize(size, Image.LANCZOS)
            dst = out_dir / f"{id_}.webp"
            im.save(dst, "WEBP", quality=78, method=6)
            total += dst.stat().st_size
        credits.setdefault(FOLDERS[set_], []).append({
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
    print(f"processed {len(rows)} illustrations into {len(credits)} folders, {total / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

"""Processes AI-generated manager portraits for the app (docs/ART_PIPELINE.md).

Reads tools/art/portraits/managers.json and the cached originals in tools/art/cache/portraits/<id>.png (git-ignored),
writes 320x320 WebP files to app/src/main/assets/img/portraits/<id>.webp and a credits.json next to them.
Deterministic: same inputs give byte-identical outputs with the pinned Pillow settings.

Usage: python -I tools/art/process_portraits.py <repo-root>
"""
import json
import sys
from pathlib import Path

from PIL import Image

SIZE = 320


def main(repo: Path) -> None:
    manifest = json.loads((repo / "tools/art/portraits/managers.json").read_text(encoding="utf-8"))
    cache = repo / "tools/art/cache/portraits"
    out_dir = repo / "app/src/main/assets/img/portraits"
    out_dir.mkdir(parents=True, exist_ok=True)
    credits, missing, total = [], [], 0
    for e in manifest:
        src = cache / f"{e['id']}.png"
        if not src.exists():
            missing.append(e["id"])
            continue
        with Image.open(src) as im:
            im = im.convert("RGB")
            w, h = im.size
            side = min(w, h)
            left, top = (w - side) // 2, (h - side) // 2
            im = im.crop((left, top, left + side, top + side)).resize((SIZE, SIZE), Image.LANCZOS)
            dst = out_dir / f"{e['id']}.webp"
            im.save(dst, "WEBP", quality=80, method=6)
            total += dst.stat().st_size
        credits.append({
            "id": e["id"],
            "title": f"{e['name']} (portrait)",
            "author": f"AI-generated with Figma AI ({e['model']})",
            "source": "Figma AI image generation",
            "sourceUrl": "",
            "license": "AI-GENERATED",
            "licenseUrl": "",
            "modifications": "Resized to 320×320, WebP",
        })
    doc = {
        "note": "Caricature portraits of invented people, generated with Figma AI. They do not depict, and are not meant "
                "to resemble, any real person.",
        "items": credits,
    }
    (out_dir / "credits.json").write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"processed {len(credits)} portraits, {total / 1024:.0f} KB total; missing: {missing or 'none'}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

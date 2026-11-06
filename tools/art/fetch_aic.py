"""Art pipeline, museum step (docs/ART_PIPELINE.md): public-domain paintings and objects from the Art Institute of
Chicago open-access API (CC0 images for works marked is_public_domain).

1. Searches highlighted ("boosted") public-domain paintings and sculptures, writes tools/art/aic_works.json (metadata).
2. Downloads each image from the museum's IIIF service (600 px wide) into tools/art/cache/aic/ (git-ignored).
3. Writes 480-px-wide WebP files to app/src/main/assets/img/art/<id>.webp and app/src/main/assets/img/art/credits.json.

Every request sends a descriptive User-Agent with the repository URL (no email). Re-running skips cached downloads.
Usage: python -I tools/art/fetch_aic.py <repo-root> [--paintings 80] [--objects 20]
"""
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image

UA = "ArnavBusinessTycoon/0.8 (+https://github.com/Arnav-Dugad/Arnav-Business-Tycoon)"
API = "https://api.artic.edu/api/v1/artworks/search"
IIIF = "https://www.artic.edu/iiif/2/{}/full/600,/0/default.jpg"
FIELDS = "id,title,artist_title,artist_display,date_display,image_id,is_public_domain,artwork_type_title,credit_line,thumbnail"


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "AIC-User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def search(kind: str, limit: int) -> list:
    params = {
        "query[bool][must][0][term][is_public_domain]": "true",
        "query[bool][must][1][term][is_boosted]": "true",
        "query[bool][must][2][term][artwork_type_title.keyword]": kind,
        "fields": FIELDS,
        "limit": str(min(limit * 2, 100)),
    }
    data = json.loads(get(API + "?" + urllib.parse.urlencode(params)))["data"]
    return [d for d in data if d.get("is_public_domain") and d.get("image_id") and d.get("artist_title")][:limit]


def main(repo: Path, n_paint: int, n_obj: int) -> None:
    works = [dict(w, kind="painting") for w in search("Painting", n_paint)] + [dict(w, kind="sculpture") for w in search("Sculpture", n_obj)]
    (repo / "tools/art/aic_works.json").write_text(json.dumps(works, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    cache = repo / "tools/art/cache/aic"
    cache.mkdir(parents=True, exist_ok=True)
    out = repo / "app/src/main/assets/img/art"
    out.mkdir(parents=True, exist_ok=True)
    credits = []
    for w in works:
        src = cache / f"{w['id']}.jpg"
        if not src.exists():
            src.write_bytes(get(IIIF.format(w["image_id"])))
            time.sleep(0.5)  # polite pacing
        img = Image.open(src).convert("RGB")
        if img.width > 480:
            img = img.resize((480, round(img.height * 480 / img.width)), Image.LANCZOS)
        dst = out / f"aic_{w['id']}.webp"
        img.save(dst, "WEBP", quality=78, method=6)
        credits.append({
            "id": f"aic_{w['id']}", "title": w["title"], "author": w["artist_title"], "date": w.get("date_display", ""),
            "source": "Art Institute of Chicago", "sourceUrl": f"https://www.artic.edu/artworks/{w['id']}",
            "license": "CC0-1.0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/",
            "creditLine": w.get("credit_line", ""), "modifications": "Resized to 480 px wide, WebP",
        })
    (out / "credits.json").write_text(json.dumps(credits, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    size = sum(f.stat().st_size for f in out.glob("*.webp"))
    print(f"{len(works)} works ({sum(1 for w in works if w['kind'] == 'painting')} paintings), {size / 1e6:.1f} MB of WebP")


if __name__ == "__main__":
    args = sys.argv[2:]
    p = int(args[args.index("--paintings") + 1]) if "--paintings" in args else 80
    o = int(args[args.index("--objects") + 1]) if "--objects" in args else 20
    main(Path(sys.argv[1]), p, o)

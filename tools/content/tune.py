"""Applies balance knobs to content JSON so every tuning step is a small, reviewable, reproducible diff.

Usage: python -I tools/content/tune.py <repo-root> <knobs.json>
knobs.json: {"businesses": {"retail": {"baseCost": "5.00", "managers": {"retail_mira": "2000.00"}, "expansions": {"market_stall": "400.00"}}},
             "missions": {"c1_income": {"rewardCash": "8000.00", "amount": "120.00"}}}
"""
import json
import sys
from pathlib import Path


def main(repo: Path, knobs_path: Path) -> None:
    knobs = json.loads(knobs_path.read_text(encoding="utf-8"))
    bpath = repo / "content/data/businesses.json"
    businesses = json.loads(bpath.read_text(encoding="utf-8"))
    for b in businesses:
        k = knobs.get("businesses", {}).get(b["id"])
        if not k:
            continue
        for field in ("unlockCost", "baseCost", "costGrowth", "baseRevenue", "upkeep", "storageSeconds"):
            if field in k:
                b[field] = k[field]
        for t in b["tiers"]:
            if str(t["level"]) in k.get("tiers", {}):
                t["multiplier"] = k["tiers"][str(t["level"])]
        for m in b["managers"]:
            if m["id"] in k.get("managers", {}):
                m["cost"] = k["managers"][m["id"]]
        for e in b["expansions"]:
            ek = k.get("expansions", {}).get(e["id"])
            if ek:
                if isinstance(ek, str):
                    e["cost"] = ek
                else:
                    e.update(ek)
    bpath.write_text(json.dumps(businesses, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")

    cpath = repo / "content/data/chapters.json"
    chapters = json.loads(cpath.read_text(encoding="utf-8"))
    for ch in chapters:
        for m in ch["missions"]:
            mk = knobs.get("missions", {}).get(m["id"])
            if not mk:
                continue
            for field in ("rewardCash", "rewardBullion", "rewardFavor"):
                if field in mk:
                    m[field] = mk[field]
            for field in ("amount", "level", "count"):
                if field in mk:
                    m["goal"][field] = mk[field]
    cpath.write_text(json.dumps(chapters, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print("applied", knobs_path.name)


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))

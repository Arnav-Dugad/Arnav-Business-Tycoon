"""Generates content/balance/balance.json from formulas so the numbers are reproducible and tunable.

Usage: python -I tools/content/gen_balance.py <repo-root>
Formulas (docs/ECONOMY.md): HQ cost = 500 * 6^(L-2) for L >= 2; +1 slot at levels 5/10/15/20/25;
+30 min offline cap at every even level (8 h total at L24); Hustle x1.6 per level.
"""
import json
import sys
from decimal import Decimal
from pathlib import Path

HQ_NAMES = [
    "Garage Office", "Shared Desk", "Corner Office", "Loft Studio", "Downtown Suite", "Glass Floor",
    "Riverside Offices", "Executive Wing", "Boardroom Level", "Tower Floor", "Twin Floors", "Atrium HQ",
    "Penthouse Offices", "Skyline Campus", "Harbour Tower", "Meridian Plaza", "Gilded Spire", "Summit Tower",
    "Crown Exchange", "Grand Atrium", "Sovereign Tower", "Empire Plaza", "Dynasty House", "Legacy Spire", "Crown Tower",
]


def money(d: Decimal) -> str:
    return f"{d.quantize(Decimal('0.01'))}"


def main(repo: Path) -> None:
    levels = []
    for lvl in range(1, 26):
        cost = Decimal(0) if lvl == 1 else Decimal(500) * (Decimal(6) ** (lvl - 2))
        levels.append({
            "level": lvl,
            "cost": money(cost),
            "extraSlots": 1 if lvl % 5 == 0 else 0,
            "offlineCapBonusSeconds": 1800 if lvl % 2 == 0 else 0,
            "hustleMultiplier": "1" if lvl == 1 else "1.6",
            "name": HQ_NAMES[lvl - 1],
        })
    balance = {
        "startingCash": "0.00",
        "baseSlots": 3,
        "offlineCapBaseSeconds": 7200,
        "offlineCapMaxSeconds": 72000,
        "hustle": {
            "basePerTap": "1.00",
            "incomeSecondsPerTap": 0.5,
            "comboWindowMs": 1200,
            "maxCombo": 25,
            "comboStep": 0.04,
            "frenzyChargesPerDay": 3,
            "frenzySeconds": 30,
            "frenzyMultiplier": 3.0,
        },
        "hqLevels": levels,
        "managerSkillFloor": 0.70,
        "managerSkillPerStar": 0.06,
        "favorPerRushMinute": 1.0,
    }
    out = repo / "content" / "balance" / "balance.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(balance, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {out}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

"""Checks WCAG contrast of Aurum text/icon tokens against background surfaces, for both themes.

Parses designsystem/.../AurumColors.kt so the check always runs on the real values.
Usage: python -I tools/scripts/check_contrast.py <repo-root>
"""
import re
import sys
from pathlib import Path

BODY = 4.5   # normal text
LARGE = 3.0  # large text, icons, signals that are always paired with arrows/signs

RULES = {
    "textPrimary": BODY, "textSecondary": BODY, "textTertiary": BODY, "goldText": BODY,
    "gain": BODY, "loss": BODY, "warning": BODY, "info": BODY,
    "gold": LARGE, "bullion": LARGE,
}
SURFACES = ["background", "surface", "surfaceRaised", "surfaceHigh"]


def lum(hex_rgb: str) -> float:
    r, g, b = (int(hex_rgb[i:i + 2], 16) / 255 for i in (0, 2, 4))
    lin = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in (r, g, b)]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def ratio(a: str, b: str) -> float:
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def parse(text: str, name: str) -> dict:
    block = re.search(rf"val {name} = AurumColors\((.*?)\n\)", text, re.S).group(1)
    return {k: v[2:] for k, v in re.findall(r"(\w+) = Color\(0x([0-9A-Fa-f]{8})\)", block)}


def main(repo: Path) -> int:
    src = (repo / "designsystem/src/main/kotlin/com/arnavgames/tycoon/designsystem/theme/AurumColors.kt").read_text()
    failures = 0
    for theme in ("AurumDark", "AurumLight"):
        tokens = parse(src, theme)
        for token, minimum in RULES.items():
            worst = min((ratio(tokens[token], tokens[s]), s) for s in SURFACES)
            ok = worst[0] >= minimum
            failures += not ok
            print(f"{theme:10s} {token:14s} min {worst[0]:5.2f} on {worst[1]:13s} need {minimum}  {'OK' if ok else 'FAIL'}")
        on_gold = ratio(tokens["textOnGold"], tokens["gold"])
        failures += on_gold < BODY
        print(f"{theme:10s} textOnGold on gold {on_gold:5.2f}  {'OK' if on_gold >= BODY else 'FAIL'}")
    print("RESULT:", "PASS" if failures == 0 else f"{failures} FAIL")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1])))

"""Builds the bundled font files for the Aurum design system.

Input: the variable Google Fonts sources (SIL OFL 1.1, no Reserved Font Name):
  Inter[opsz,wght].ttf and Fraunces[SOFT,WONK,opsz,wght].ttf
Output: small static, Latin-only instances in designsystem/src/main/res/font/.

Usage: python -I tools/fonts/build_fonts.py <dir-with-downloaded-ttfs> <repo-root>
"""
import sys
from pathlib import Path

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

# Basic Latin, Latin-1, general punctuation, currency, arrows used in UI copy, math minus/multiply.
UNICODES = (
    list(range(0x20, 0x7F))
    + list(range(0xA0, 0x100))
    + list(range(0x2010, 0x2030))
    + [0x2032, 0x2033, 0x2039, 0x203A, 0x2044, 0x20AC, 0x20B9, 0x2122, 0x2190, 0x2191, 0x2192, 0x2193,
       0x2212, 0x00D7, 0x2248, 0x2264, 0x2265, 0x221E, 0x2026, 0x00B7]
)
FEATURES = ["kern", "liga", "calt", "tnum", "lnum", "pnum", "case", "ss01", "cv11", "zero"]

INSTANCES = [
    # (source, output name, axis locations)
    ("Inter.ttf", "inter_regular.ttf", {"wght": 400, "opsz": 14}),
    ("Inter.ttf", "inter_medium.ttf", {"wght": 500, "opsz": 14}),
    ("Inter.ttf", "inter_semibold.ttf", {"wght": 600, "opsz": 14}),
    ("Inter.ttf", "inter_bold.ttf", {"wght": 700, "opsz": 24}),
    ("Fraunces.ttf", "fraunces_medium.ttf", {"wght": 500, "opsz": 48, "SOFT": 50, "WONK": 0}),
    ("Fraunces.ttf", "fraunces_semibold.ttf", {"wght": 600, "opsz": 72, "SOFT": 30, "WONK": 0}),
]


def build(src_dir: Path, repo: Path) -> None:
    out_dir = repo / "designsystem" / "src" / "main" / "res" / "font"
    out_dir.mkdir(parents=True, exist_ok=True)
    total = 0
    for src, out, axes in INSTANCES:
        font = TTFont(src_dir / src)
        static = instancer.instantiateVariableFont(font, axes, updateFontNames=False)
        opts = Options()
        opts.layout_features = FEATURES
        opts.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]  # keep copyright + license fields
        opts.notdef_outline = True
        opts.hinting = False
        opts.desubroutinize = True
        sub = Subsetter(opts)
        sub.populate(unicodes=UNICODES)
        sub.subset(static)
        dest = out_dir / out
        static.save(dest)
        size = dest.stat().st_size
        total += size
        print(f"{out:28s} {size/1024:7.1f} KB")
    print(f"{'TOTAL':28s} {total/1024:7.1f} KB")
    if total > 600 * 1024:
        sys.exit("Font budget (600 KB) exceeded")


if __name__ == "__main__":
    build(Path(sys.argv[1]), Path(sys.argv[2]))

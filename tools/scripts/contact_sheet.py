"""Combines screenshots side by side into one image for quick visual review.

Usage: python -I tools/scripts/contact_sheet.py <out.png> <img1> <img2> ... [--width 400]
"""
import sys
from PIL import Image, ImageDraw


def main(argv: list[str]) -> None:
    width = 400
    if "--width" in argv:
        i = argv.index("--width")
        width = int(argv[i + 1])
        argv = argv[:i] + argv[i + 2:]
    out, paths = argv[0], argv[1:]
    imgs = []
    for p in paths:
        im = Image.open(p).convert("RGB")
        h = int(im.height * width / im.width)
        imgs.append((p, im.resize((width, h), Image.LANCZOS)))
    gap = 12
    sheet_h = max(im.height for _, im in imgs) + 28
    sheet = Image.new("RGB", (len(imgs) * (width + gap) + gap, sheet_h + gap), (128, 128, 128))
    draw = ImageDraw.Draw(sheet)
    for i, (p, im) in enumerate(imgs):
        x = gap + i * (width + gap)
        sheet.paste(im, (x, 28))
        draw.text((x, 8), p.replace("\\", "/").split("/")[-1], fill=(0, 0, 0))
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main(sys.argv[1:])

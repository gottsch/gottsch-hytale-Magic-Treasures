#!/usr/bin/env python3
"""Draws the Treasures Case page's one accent (Nov 12): an iron hasp
with a brass lock, hanging over the bottom edge of vanilla's container
frame like the clasp on the case model.

It is drawn flat, in the slate tones of vanilla's container lines
(Common/ContainerDecorationBottom), so it sits in the game's own UI
style; only the lock is warm.

Output: Common/UI/Custom/Pages/MagicTreasures/CaseHasp@2x.png
(WIDTH x HEIGHT UI units, drawn at 2x). Keep the size in step with
MagicTreasures_JewelryCasePage.ui.

Usage (from the repo root): python tools/make_case_hasp_art.py [--preview P]
"""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / \
    "resources" / "Common" / "UI" / "Custom" / "Pages" / "MagicTreasures"

WIDTH, HEIGHT = 40, 44       # UI units
SS = 8                       # drawn at 8 px per unit, saved at 2
OUTLINE = (11, 15, 19, 255)
IRON = (65, 71, 90, 255)
IRON_LIGHT = (92, 100, 126, 255)
IRON_DARK = (43, 52, 74, 255)
BRASS = (190, 152, 86, 255)
BRASS_LIGHT = (226, 196, 128, 255)
BRASS_DARK = (128, 98, 54, 255)


def u(*v):
    return [x * SS for x in v]


def draw():
    im = Image.new("RGBA", (WIDTH * SS, HEIGHT * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    o = 1.0  # outline width, in units
    # the hinge knuckle across the top
    d.rounded_rectangle(u(4, 1, 36, 9), radius=2 * SS, fill=OUTLINE)
    d.rounded_rectangle(u(4 + o, 1 + o, 36 - o, 9 - o), radius=1.5 * SS,
                        fill=IRON)
    d.rectangle(u(5.5, 2.5, 34.5, 4), fill=IRON_LIGHT)
    for x in (16, 24):
        d.line(u(x, 2, x, 8), fill=OUTLINE, width=int(0.8 * SS))
    # the strap, narrowing to a rounded tongue
    strap = [(9, 8), (31, 8), (27, 36), (20, 42), (13, 36)]
    d.polygon([(x * SS, y * SS) for x, y in strap], fill=OUTLINE)
    inner = [(10.6, 9), (29.4, 9), (25.9, 35.4), (20, 40.6), (14.1, 35.4)]
    d.polygon([(x * SS, y * SS) for x, y in inner], fill=IRON)
    d.polygon([(x * SS, y * SS) for x, y in
               [(10.6, 9), (13.2, 9), (16, 35.6), (14.1, 35.4)]],
              fill=IRON_LIGHT)
    d.polygon([(x * SS, y * SS) for x, y in
               [(26.8, 9), (29.4, 9), (25.9, 35.4), (24, 35.6)]],
              fill=IRON_DARK)
    # rivets on the strap
    for x, y in ((14, 13), (26, 13)):
        d.ellipse(u(x - 1.6, y - 1.6, x + 1.6, y + 1.6), fill=OUTLINE)
        d.ellipse(u(x - 1, y - 1, x + 1, y + 1), fill=IRON_LIGHT)
    # the brass lock
    d.rounded_rectangle(u(13, 18, 27, 32), radius=2.5 * SS, fill=OUTLINE)
    d.rounded_rectangle(u(14, 19, 26, 31), radius=1.8 * SS, fill=BRASS)
    d.rectangle(u(15.5, 20, 24.5, 21.4), fill=BRASS_LIGHT)
    d.rectangle(u(15.5, 29, 24.5, 30), fill=BRASS_DARK)
    # the keyhole
    d.ellipse(u(18.4, 22.4, 21.6, 25.6), fill=OUTLINE)
    d.polygon([(x * SS, y * SS) for x, y in
               [(19.2, 24.6), (20.8, 24.6), (21.4, 28.6), (18.6, 28.6)]],
              fill=OUTLINE)
    return im.resize((WIDTH * 2, HEIGHT * 2), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    a = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    im = draw()
    im.save(OUT / "CaseHasp@2x.png")
    print("wrote CaseHasp@2x.png", im.size)
    if a.preview:
        im.resize((im.width * 4, im.height * 4), Image.NEAREST).save(a.preview)


if __name__ == "__main__":
    main()

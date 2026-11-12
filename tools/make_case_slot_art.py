"""Treasures Case slot art: a slot background per jewelry type.

Each image is an empty inventory slot with a faint silhouette of what
goes in it (ring, necklace or bracelet), drawn in the style of vanilla's
armor slot icons: a dark navy fill with a lighter outline. They are for
the Treasures Case page (MagicTreasures_JewelryCasePage.ui).

Output, in Common/UI/Custom/Pages/MagicTreasures/ (148 x 148, @2x like
vanilla's slots):
- CaseSlot_<Type>@2x.png: the slot with the silhouette;
- CaseSlot_Plain@2x.png: an empty slot.

Usage (from the repo root):
    python tools/make_case_slot_art.py [--preview P]
"""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / \
    "resources" / "Common" / "UI" / "Custom" / "Pages" / "MagicTreasures"
SS = 4                      # supersampling
SLOT = 148
ICON = 128
SLOT_FILL = (30, 41, 57, 255)
SLOT_EDGE = (47, 61, 80, 255)
FILL = (40, 52, 71, 255)
LINE = (82, 98, 122, 255)
TYPES = ["Ring", "Necklace", "Bracelet"]


def draw_ring(d, s):
    c = s / 2
    w = 6 * SS
    # the band, seen a little from above: an ellipse ring
    rx, ry = s * 0.30, s * 0.24
    cy = c + s * 0.07
    d.ellipse((c - rx, cy - ry, c + rx, cy + ry), fill=FILL, outline=LINE,
              width=w)
    irx, iry = rx - s * 0.09, ry - s * 0.08
    d.ellipse((c - irx, cy - iry, c + irx, cy + iry), fill=(0, 0, 0, 0),
              outline=LINE, width=w)
    # the gem on top: a diamond in a setting
    gy = cy - ry - s * 0.02
    gw, gh = s * 0.13, s * 0.15
    pts = [(c, gy - gh), (c + gw, gy - gh * 0.25), (c, gy + gh * 0.55),
           (c - gw, gy - gh * 0.25)]
    d.polygon(pts, fill=FILL, outline=LINE, width=w)
    d.line((pts[3], pts[1]), fill=LINE, width=w)


def draw_necklace(d, s):
    c = s / 2
    w = 6 * SS
    # the chain: a deep U from the upper corners
    box = (s * 0.18, s * -0.12, s * 0.82, s * 0.66)
    d.arc(box, 15, 165, fill=LINE, width=w * 3)
    d.arc(box, 15, 165, fill=FILL, width=w)
    # beads along the chain
    import math
    cx, cy = c, (box[1] + box[3]) / 2
    rx, ry = (box[2] - box[0]) / 2, (box[3] - box[1]) / 2
    for a in range(25, 160, 20):
        t = math.radians(a)
        x, y = cx + rx * math.cos(t), cy + ry * math.sin(t)
        r = s * 0.028
        d.ellipse((x - r, y - r, x + r, y + r), fill=FILL, outline=LINE,
                  width=w // 2)
    # the pendant: a drop below the bottom of the U
    py = box[3] + s * 0.02
    pw, ph = s * 0.10, s * 0.20
    d.polygon([(c, py - s * 0.02), (c + pw, py + ph * 0.45), (c, py + ph),
               (c - pw, py + ph * 0.45)], fill=FILL, outline=LINE, width=w)


def draw_bracelet(d, s):
    c = s / 2
    w = 6 * SS
    # a wide band in perspective: a short cylinder
    rx, ry = s * 0.32, s * 0.13
    top, bottom = c - s * 0.10, c + s * 0.12
    # body: the bottom ellipse and the sides
    d.ellipse((c - rx, bottom - ry, c + rx, bottom + ry), fill=FILL,
              outline=LINE, width=w)
    d.rectangle((c - rx, top, c + rx, bottom), fill=FILL)
    d.line((c - rx, top, c - rx, bottom), fill=LINE, width=w)
    d.line((c + rx, top, c + rx, bottom), fill=LINE, width=w)
    # the top rim and the opening
    d.ellipse((c - rx, top - ry, c + rx, top + ry), fill=FILL, outline=LINE,
              width=w)
    irx, iry = rx - s * 0.06, ry - s * 0.04
    d.ellipse((c - irx, top - iry, c + irx, top + iry),
              fill=(22, 30, 42, 255), outline=LINE, width=w // 2)
    # a band of studs around the middle
    for i in range(-2, 3):
        x = c + i * rx * 0.38
        y = (top + bottom) / 2 + ry * 0.55
        r = s * 0.025
        d.ellipse((x - r, y - r, x + r, y + r), fill=LINE)


DRAW = {"Ring": draw_ring, "Necklace": draw_necklace,
        "Bracelet": draw_bracelet}


def icon(kind):
    s = ICON * SS
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    DRAW[kind](ImageDraw.Draw(im), s)
    return im.resize((ICON, ICON), Image.LANCZOS)


def slot(kind):
    s = SLOT * SS
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((2 * SS, 2 * SS, s - 2 * SS, s - 2 * SS),
                        radius=8 * SS, fill=SLOT_FILL, outline=SLOT_EDGE,
                        width=3 * SS)
    im = im.resize((SLOT, SLOT), Image.LANCZOS)
    if kind is None:
        return im
    ic = icon(kind).resize((104, 104), Image.LANCZOS)
    # faint, like vanilla's empty armor slots
    ic.putalpha(ic.split()[3].point(lambda a: a * 0.8))
    im.alpha_composite(ic, ((SLOT - 104) // 2, (SLOT - 104) // 2))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    a = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    slots = []
    for kind in TYPES:
        sl = slot(kind)
        sl.save(OUT / f"CaseSlot_{kind}@2x.png")
        slots.append(sl)
    # an empty slot, for the page's inventory grids
    slot(None).save(OUT / "CaseSlot_Plain@2x.png")
    if a.preview:
        order = [slots[0], slots[0], slots[1], slots[2]]
        sheet = Image.new("RGBA", (SLOT * 4 + 30, SLOT), (20, 26, 36, 255))
        for i, sl in enumerate(order):
            sheet.alpha_composite(sl, (i * (SLOT + 10), 0))
        sheet.save(a.preview)
    print(f"Wrote {len(TYPES) + 1} case slot images to {OUT}")


if __name__ == "__main__":
    main()

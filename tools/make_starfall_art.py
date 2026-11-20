#!/usr/bin/env python3
"""Make the art for Starfall, the named star ring (v1.2, Nov 18).

A gold Great ring whose band is blue enamel between gold rims, dotted
with small gold stars, and whose setting is a round gold bezel holding
a sapphire with a raised gold star on its face. After a picture Mark
liked: a night-sky ring.

It reuses make_ring_art's Great band and rims (item_model, at FINE
density) and its gold, painted by paint_metal_fine; the band's outside
is then painted over with the enamel. The bezel is an octagon (two
boxes turned 45 degrees apart) in the collar's regions, the stone a
smaller octagon in its own region, and the star five tapered arms of
flat boxes in the prong region, one arm pointing away from the icon
camera so it reads upright.

It writes:

- Rings/Ring_Starfall.png: its texture (128 square, as a Great ring's).
  It also holds the 1x band region (make_ring_art.METAL["outer"]) in
  enamel, which the worn ring uses (JewelryWear.NAMED_WORN_TEXTURE);
- Rings/Ring_Starfall.blockymodel: the held and dropped model;
- Icons/Items/MagicTreasures/MT_Ring_Starfall.png: its icon.

Usage (from the repo root):
    python tools/make_starfall_art.py --assets <Assets.zip> [--preview P]
"""
import argparse
import json
import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import blocky_render  # noqa: E402
import make_brilliant_gem as brilliant  # noqa: E402
import make_ring_art as ring  # noqa: E402

ICON_ID = "MT_Ring_Starfall"
MODEL = ring.RINGS_OUT / "Ring_Starfall.blockymodel"
TEXTURE = ring.RINGS_OUT / "Ring_Starfall.png"

# blue enamel, dark to bright
ENAMEL = [(10, 16, 52), (20, 34, 96), (32, 58, 150), (56, 94, 196),
          (116, 156, 232)]
# the sapphire stone, dark to bright
STONE = [(12, 24, 80), (24, 52, 140), (44, 90, 200), (96, 150, 240),
         (210, 230, 255)]
# the stone's regions, in the free top right of the texture (the gem
# area at the top left is unused: Starfall has no brilliant)
STONE_TOP = (64, 0, 16, 16)
STONE_SIDE = (80, 0, 16, 4)

# the setting, in units (the band's top is at r + t)
BEZEL = (5.4, 1.8)        # across, tall
STONE_SIZE = (4.4, 0.9)
STAR_R = 1.9              # centre to tip
STAR_H = 0.3
# each arm's steps from the centre out: (width, length)
ARM = [(0.9, 0.7), (0.5, 0.6), (0.22, 0.5)]
# the icon looks down more steeply than the other rings' (24), so the
# star on the stone's face shows (Nov 18: at 24 it was edge-on)
ICON_PITCH = 40


def ramp(pal, t):
    t = max(0.0, min(1.0, t)) * 4
    i = min(int(t), 3)
    f = t - i
    return tuple(int(pal[i][c] + (pal[i + 1][c] - pal[i][c]) * f)
                 for c in range(3))


def put(im, x, y, c):
    im.putpixel((x, y), tuple(c) + (255,))


def paint_enamel(im, region, gold, stars=True):
    """A band's outside: gold edges, blue enamel between, a bright
    middle, and small gold stars (one sparkle and one dot a segment,
    so they scatter in two rows round the band)."""
    x0, y0, w, h = region
    edge = max(1, h // 8)
    for v in range(h):
        d = abs(v + 0.5 - h / 2) / (h / 2)
        for u in range(w):
            if v < edge or v >= h - edge:
                c = ramp(gold, 0.95 if v < edge else 0.5)
            else:
                c = ramp(ENAMEL, 0.62 - 0.45 * d ** 1.5)
            put(im, x0 + u, y0 + v, c)
    if not stars or h < 12:
        return
    # a four-point sparkle: a bright centre, dimmer arms
    cx, cy = x0 + w // 2 - 1, y0 + int(h * 0.36)
    put(im, cx, cy, ramp(gold, 1.0))
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(im, cx + dx, cy + dy, ramp(gold, 0.7))
    # a single dot, lower and to the side
    put(im, x0 + (w // 2 + 3) % w, y0 + int(h * 0.68), ramp(gold, 0.9))


def paint_stone(im):
    x0, y0, w, h = STONE_TOP
    for v in range(h):
        for u in range(w):
            # domed: bright toward the upper left, dark at the rim
            dx, dy = (u + 0.5) / w - 0.5, (v + 0.5) / h - 0.5
            r = math.hypot(dx, dy) / 0.5
            light = 0.75 - 0.5 * r - 0.35 * (dx + dy)
            c = ramp(STONE, light)
            if math.hypot(dx + 0.2, dy + 0.2) < 0.09:
                c = STONE[4]
            put(im, x0 + u, y0 + v, c)
    x0, y0, w, h = STONE_SIDE
    for v in range(h):
        for u in range(w):
            put(im, x0 + u, y0 + v, ramp(STONE, 0.4 - 0.3 * v / h))


def texture(assets):
    im = Image.new("RGBA", (ring.FINE_TEX, ring.FINE_TEX), (0, 0, 0, 0))
    gold = ring.metal_palette(assets, "Gold")
    # the gold, as a Great gold ring's (its seed: gold, no gem)
    ring.paint_metal_fine(im, gold, ring.MATERIALS.index("Gold") * 31)
    paint_enamel(im, ring.regions("Great")["outer"], gold)
    # the 1x band the worn ring uses: its faces show only the top 2 or
    # 4 rows, so gold edges took half the band, and a gold star texel
    # stretched into a block (Nov 18). Plain enamel.
    x0, y0, w, h = ring.METAL["outer"]
    for v in range(h):
        for u in range(w):
            put(im, x0 + u, y0 + v, ramp(ENAMEL, 0.55 if v % 2 else 0.45))
    paint_stone(im)
    return im


# how far apart overlapping boxes' top faces stand: in one plane they
# z-fought in first person (Mark, Nov 18)
LIFT = 0.06


def octagon(nid, name, y, across, tall, tex, faces):
    """Two square boxes turned 45 degrees apart, on the ring's top. The
    second stands LIFT taller (same bottom), so their tops don't share
    a plane."""
    side = across * 0.92
    return [ring.box(nid + i, f"{name}_{i}", (0, y + i * LIFT / 2, 0),
                     brilliant.quat_axis((0, 1, 0), a), tex,
                     (side, tall + i * LIFT, side), faces)
            for i, a in enumerate((0, math.pi / 4))]


def star_nodes(y, nid):
    """Five tapered arms of flat boxes and a centre, lying on the stone,
    one arm pointing to -z (away from the icon camera). The arms'
    bases overlap each other and the centre, so each arm stands a
    little taller than the last (half a LIFT, same bottom) and the
    centre tallest, or their tops z-fight."""
    reg = ring.regions("Great")
    gold = {f: ring.layout(reg["prong"]) for f in
            ("front", "back", "left", "right", "top", "bottom")}
    tex = ring.texels("Great", (2, 1, 2))
    step = LIFT / 2
    top = 5 * step
    nodes = [ring.box(nid, "Star_Centre", (0, y + top / 2, 0),
                      brilliant.quat_axis((0, 1, 0), math.pi / 4), tex,
                      (0.8, STAR_H + top, 0.8), gold)]
    scale = STAR_R / sum(length for _, length in ARM)
    for k in range(5):
        # angle in the xz plane from -z, turning to +x
        a = 2 * math.pi * k / 5
        dx, dz = math.sin(a), -math.cos(a)
        # a box's length runs along its local z, turned to the arm
        rot = brilliant.quat_axis((0, 1, 0), -a)
        start = 0.2
        for j, (w, length) in enumerate(ARM):
            length *= scale
            mid = start + length / 2
            lift = k * step
            nodes.append(ring.box(nid + 1 + 3 * k + j, f"Star_{k}_{j}",
                                  (dx * mid, y + lift / 2, dz * mid), rot, tex,
                                  (w, STAR_H + lift, length), gold))
            start += length
    return nodes


def model():
    st = ring.STYLES["Great"]
    m = ring.item_model("Great", None)
    nodes = m["nodes"][0]["children"]
    reg = ring.regions("Great")
    top = st["r"] + st["t"]
    collar = {f: ring.layout(reg["collar_side"])
              for f in ("front", "back", "left", "right")}
    collar["top"] = collar["bottom"] = ring.layout(reg["collar_top"])
    bw, bh = BEZEL
    nodes += octagon(2000, "Bezel", top + bh / 2 - 0.2, bw, bh,
                     ring.texels("Great", (6, 3, 6)), collar)
    stone = {f: ring.layout(STONE_SIDE) for f in
             ("front", "back", "left", "right")}
    stone["top"] = stone["bottom"] = ring.layout(STONE_TOP)
    sw, sh = STONE_SIZE
    # sunk a little into the bezel, so a gold lip shows round it
    stone_y = top - 0.2 + bh + sh / 2 - 0.35
    nodes += octagon(2010, "Stone", stone_y, sw, sh, (16, 4, 16), stone)
    nodes += star_nodes(stone_y + sh / 2 + STAR_H / 2 - 0.05, 2100)
    return m


def icon_span():
    """make_ring_art's ring icon scale (the largest standard ring)."""
    return max(blocky_render.fit_span(
        str(ring.RINGS_OUT / (f"Ring_{s}" + ("_Set" if g else "")
                              + ".blockymodel")),
        str(ring.RINGS_OUT / "Ring_Gold_Ruby.png"), yaw=35, pitch=24)
        for s in ring.STYLES for g in (None, "x"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    texture(a.assets).save(TEXTURE)
    MODEL.write_text(json.dumps(model(), indent=2) + "\n",
                     encoding="utf-8", newline="\n")
    span = max(icon_span(), blocky_render.fit_span(str(MODEL), str(TEXTURE),
                                                   yaw=35, pitch=ICON_PITCH))
    icon = blocky_render.render(str(MODEL), str(TEXTURE),
                                str(ring.ICON_OUT / f"{ICON_ID}.png"),
                                margin=4, pitch=ICON_PITCH, yaw=35, span=span)
    print(f"Wrote {TEXTURE.name}, {MODEL.name} and {ICON_ID}.png")
    if a.preview:
        tiles = [Image.open(ring.ICON_OUT / "MT_Ring_Gold_Sapphire_Great.png"),
                 Image.open(ring.ICON_OUT / "MT_Ring_Vampire_Lich.png"), icon]
        sheet = Image.new("RGBA", (3 * 260, 260), (55, 57, 66, 255))
        for i, t in enumerate(tiles):
            sheet.alpha_composite(t.convert("RGBA").resize((256, 256), Image.NEAREST),
                                  (i * 260 + 2, 2))
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

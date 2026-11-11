#!/usr/bin/env python3
"""Make a princess-cut gem: model, a texture per gem, and icons.

A princess cut is square from above with sharp corners: a brilliant
style crown sloping up to a square table, and a deep pavilion of
chevron steps meeting at a point below. Built with the emerald cut's
facet code (make_emerald_gem.py): every facet is a tilted quad cut to
its trapezoid or triangle by transparent texels.

- the outline: a square, so each ring has 4 edges;
- the table: a square with a star of glints;
- the crown: 2 steps of 4 facets;
- the girdle: 4 upright quads;
- the pavilion: 3 chevron steps of 4 facets, the last meeting at the
  culet (a point).

Writes:
- Common/Items/MagicTreasures/Gems/Princess_Gem.blockymodel
- Common/Items/MagicTreasures/Gems/<Gem>_Princess.png (128 x 128)
- Common/Icons/Items/MagicTreasures/Gem_<Gem>_Princess.png

Usage (from the repo root):
    python tools/make_princess_gem.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import contextlib
import json

from PIL import Image

import blocky_render
import make_brilliant_gem as brilliant
import make_emerald_gem as emerald
from make_gem_art import COMMON, GEMS, ICON_OUT, TEX_OUT, palette

MODEL_OUT = (COMMON / "Items" / "MagicTreasures" / "Gems"
             / "Princess_Gem.blockymodel")

HALF = 6.5                  # the girdle's half side
GIRDLE_H = 1.0
CROWN = [(1.4, 1.2), (2.6, 2.2)]          # (inset, height); table last
PAVILION = [(2.2, 2.4), (4.4, 4.6), (HALF, 6.6)]


# 32 texels per facet (the brilliant and emerald cut use 16): the
# princess's long sloped corner edges showed a staircase at 16
Q = 32
TEX_W = TEX_H = 128


@contextlib.contextmanager
def fine_texels():
    """Builds with Q-texel facet regions, then puts the shared
    modules' settings back."""
    saved = (brilliant.Q, emerald.Q, emerald.SLOTS, emerald.TEX_W,
             emerald.TEX_H)
    brilliant.Q = emerald.Q = Q
    emerald.SLOTS, emerald.TEX_W, emerald.TEX_H = TEX_W // Q, TEX_W, TEX_H
    try:
        yield
    finally:
        (brilliant.Q, emerald.Q, emerald.SLOTS, emerald.TEX_W,
         emerald.TEX_H) = saved


def ring(inset):
    h = HALF - inset
    return [(h, -h), (h, h), (-h, h), (-h, -h)]


def gem_nodes():
    with fine_texels():
        return _gem_nodes()


def _gem_nodes():
    regions = emerald.Regions()
    nodes = []
    nid = 0
    top, bottom = GIRDLE_H / 2, -GIRDLE_H / 2

    def lift(points, y):
        return [(x, y, z) for x, z in points]

    girdle = ring(0.0)
    up, down = lift(girdle, top), lift(girdle, bottom)
    for k in range(4):
        n = emerald.facet(nid, f"Girdle_{k}", (down[k], down[(k + 1) % 4]),
                          (up[k], up[(k + 1) % 4]), regions, "p_girdle",
                          "girdle")
        if n:
            nodes.append(n)
        nid += 1
    for part, steps, y0, sign in (("Crown", CROWN, top, 1),
                                  ("Pavilion", PAVILION, bottom, -1)):
        outer = lift(girdle, y0)
        for s, (inset, height) in enumerate(steps):
            inner = lift(ring(inset), y0 + sign * height)
            for k in range(4):
                tone = part.lower() + ("_light" if (s + k) % 2 == 0
                                       else "_dark")
                key = f"p_{part}_{s}_{tone}"
                n = emerald.facet(nid, f"{part}_{s}_{k}",
                                  (outer[k], outer[(k + 1) % 4]),
                                  (inner[k], inner[(k + 1) % 4]),
                                  regions, key, tone)
                if n:
                    nodes.append(n)
                nid += 1
            outer = inner
    inset, height = CROWN[-1]
    side = 2 * (HALF - inset)
    regions.get("p_table", 1.0, "table")
    brilliant.REGIONS["p_table"] = regions.shapes["p_table"][:2]
    nodes.append(brilliant.quad_node(nid, "Table", (0, top + height, 0),
                                     (0, 0, 0, 1), side, side, "p_table",
                                     normal="+Y"))
    return nodes, regions


def model():
    nodes, _ = gem_nodes()
    return {"nodes": [{"id": "1", "name": "R-Attachment",
                       "children": nodes,
                       "position": {"x": 0, "y": 6, "z": 0},
                       "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                       "shape": brilliant.ROOT_SHAPE}],
            "lod": "auto"}


def paint_table(im, ox, oy, pal):
    """A square table with a star of glints along its diagonals."""
    dark, low, mid, high, glint = pal
    q = Q
    for v in range(q):
        for u in range(q):
            du, dv = min(u, q - 1 - u), min(v, q - 1 - v)
            col = high
            if u == v or u == q - 1 - v:
                col = glint
            if du == 0 or dv == 0:
                col = mid
            im.putpixel((ox + u, oy + v), col + (255,))


def texture(pal, regions):
    with fine_texels():
        im = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
        for key, (x, y, top_frac, tone) in regions.shapes.items():
            if tone == "table":
                paint_table(im, x, y, pal)
            else:
                emerald.paint_step(im, x, y, top_frac, pal, tone)
        return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    _, regions = gem_nodes()
    MODEL_OUT.write_text(json.dumps(model(), indent=2) + "\n",
                         encoding="utf-8", newline="\n")
    icons = []
    for gem in GEMS:
        tex = TEX_OUT / f"{gem}_Princess.png"
        texture(palette(a.assets, gem), regions).save(tex)
        icons.append(blocky_render.render(
            str(MODEL_OUT), str(tex),
            str(ICON_OUT / f"Gem_{gem}_Princess.png"),
            margin=5, pitch=24, yaw=30))
    print(f"Wrote the princess-cut model ({len(regions.shapes)} facet "
          f"shapes), {len(GEMS)} textures and icons.")
    if a.preview:
        cell = 150
        sheet = Image.new("RGBA", (cell * len(GEMS), cell),
                          (55, 57, 66, 255))
        for i, icon in enumerate(icons):
            sheet.alpha_composite(icon.resize((128, 128), Image.NEAREST),
                                  (i * cell + 11, 11))
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

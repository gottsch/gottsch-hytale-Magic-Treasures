#!/usr/bin/env python3
"""Make a princess-cut gem: model, a texture per gem, and icons.

A princess cut is square from above with sharp corners: a brilliant
style crown sloping up to a square table, and a deep pavilion of
chevron steps meeting at a point below. Built with the emerald cut's
facet code (make_emerald_gem.py): every facet is a tilted quad cut to
its trapezoid or triangle by transparent texels.

- the outline: a square, so each ring has 4 edges;
- the table: a plain square (its diagonal glints went, Nov 12);
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
import math

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
# Facets are 1% larger than exact, as in the other cuts (PAD None).
# A fixed 0.3 overlap (Nov 12) left spikes at the corners and the GPU
# still opened hairline cracks between facets in the held gem, so the
# gem has a solid core instead (core_nodes).
PAD = None
# How far the gem is lifted off the hand, along its own up axis (the
# table's side). The game pins the root node to the hand and ignores
# the root's own position (vanilla's Dough: root y -4, child +4), so
# the lift goes on a child node that holds every part (Gem_Offset).
# A test (Mark, Nov 12): does the tan square line, maybe the hand
# showing through the wide square pavilion, go away when lifted?
LIFT_Y = 3
# the core's boxes sit this far inside the facets
CORE_INSET = 0.08
# and this far below a flat face (the table, a step's top), so the two
# can't flicker where they lie close
CORE_DROP = 0.3
# transparent texels around each facet's region (see emerald.GUTTER)
GUTTER = 8
# Each sloped facet is drawn as this many horizontal strips, each only
# as wide as its own slice of the trapezoid (strip_nodes). A held gem
# is drawn from a blurred, smaller copy of its texture; the blur spread
# each trapezoid into the see-through corners of its quad, and on the
# princess's square corners those stick out into the air: spikes
# (Mark, Nov 12). A strip's see-through corner is 1/STRIPS the size,
# and nothing is drawn outside a strip. Q must divide by it.
STRIPS = 8
# an opaque texel of the girdle's tile, for the core's colour
CORE_TEXEL = (GUTTER + 4, GUTTER + 4)


# 32 texels per facet (the brilliant and emerald cut use 16): the
# princess's long sloped corner edges showed a staircase at 16
# 112 since Nov 12 (Mark: fix the jagged edges): the facets' slanted
# sides are cut along texels; at 32 each step was about 0.4 units, at
# 112 about 0.12. Each region also has a GUTTER on every side.
Q = 112
TEX_W = TEX_H = 512


@contextlib.contextmanager
def fine_texels():
    """Builds with Q-texel facet regions, then puts the shared
    modules' settings back."""
    saved = (brilliant.Q, emerald.Q, emerald.SLOTS, emerald.TEX_W,
             emerald.TEX_H, emerald.GUTTER)
    brilliant.Q = emerald.Q = Q
    emerald.SLOTS = TEX_W // (Q + 2 * GUTTER)
    emerald.TEX_W, emerald.TEX_H, emerald.GUTTER = TEX_W, TEX_H, GUTTER
    try:
        yield
    finally:
        (brilliant.Q, emerald.Q, emerald.SLOTS, emerald.TEX_W,
         emerald.TEX_H, emerald.GUTTER) = saved


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
                          "girdle", pad=PAD, widen=False)
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
                nodes += strip_nodes(nid, f"{part}_{s}_{k}",
                                     (outer[k], outer[(k + 1) % 4]),
                                     (inner[k], inner[(k + 1) % 4]),
                                     regions, key, tone)
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


def strip_nodes(nid, name, outer, inner, regions, key, tone):
    """A trapezoid facet between a long outer edge and a parallel inner
    edge (3D points) as STRIPS quads. They share one texture region
    (painted by emerald.paint_step, narrow inner edge at the top); each
    reads its own band of rows, only as many columns wide as the band's
    widest row."""
    (o0, o1), (i0, i1) = outer, inner
    mo = tuple((a + b) / 2 for a, b in zip(o0, o1))
    mi = tuple((a + b) / 2 for a, b in zip(i0, i1))
    lo, li = math.dist(o0, o1), math.dist(i0, i1)
    t = emerald.norm(emerald.sub(o1, o0))
    up = emerald.sub(mi, mo)
    h = math.sqrt(sum(c * c for c in up))
    u = emerald.norm(up)
    z = emerald.cross(t, u)
    if sum(a * b for a, b in zip(z, (mo[0], 0.0, mo[2]))) < 0:
        t = tuple(-c for c in t)
        z = emerald.cross(t, u)
    rot = emerald.quat_from_basis(t, u, z)
    top_frac = min(1.0, li / lo)
    x, y = regions.get(key, top_frac, tone)
    rows = Q // STRIPS
    hk = h / STRIPS
    nodes = []
    for k in range(STRIPS):
        # the band's widest (bottom) row, plus the half texel that
        # paint_step paints past the true edge
        half = (top_frac + (1 - top_frac) * (k + 1) / STRIPS) * Q / 2
        cols = min(Q, 2 * math.ceil(half + 0.5))
        w = cols * lo / Q
        # down from the inner edge; 4% taller, so strips overlap a little
        c = tuple(m - d * (k + 0.5) * hk for m, d in zip(mi, u))
        nodes.append({
            "id": str(2000 + nid * STRIPS + k), "name": f"{name}_{k}",
            "children": [],
            "position": {"x": c[0], "y": c[1], "z": c[2]},
            "orientation": {"x": rot[0], "y": rot[1], "z": rot[2],
                            "w": rot[3]},
            "shape": {
                "type": "quad", "offset": {"x": 0, "y": 0, "z": 0},
                "stretch": {"x": w / cols, "y": hk * 1.04 / rows, "z": 1},
                "settings": {"size": {"x": cols, "y": rows},
                             "normal": "+Z"},
                "textureLayout": {"front": {
                    "offset": {"x": x + (Q - cols) // 2, "y": y + k * rows},
                    "mirror": {"x": False, "y": False}, "angle": 0}},
                "unwrapMode": "custom", "visible": True,
                "doubleSided": False, "shadingMode": "reflective",
            },
        })
    return nodes


def core_box(nid, name, half, y0, y1):
    """A solid box (half its width, from y0 to y1), every face the
    girdle's colour."""
    sx = max(1, round(2 * half))
    sy = max(1, round(y1 - y0))
    face = {"offset": {"x": CORE_TEXEL[0], "y": CORE_TEXEL[1]},
            "mirror": {"x": False, "y": False}, "angle": 0}
    return {
        "id": str(nid), "name": name, "children": [],
        "position": {"x": 0, "y": (y0 + y1) / 2, "z": 0},
        "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
        "shape": {
            "type": "box", "offset": {"x": 0, "y": 0, "z": 0},
            # one texel per unit or so, read from inside the girdle tile
            "stretch": {"x": 2 * half / sx, "y": (y1 - y0) / sy,
                        "z": 2 * half / sx},
            "settings": {"size": {"x": sx, "y": sy, "z": sx}},
            "textureLayout": {f: dict(face) for f in
                              ("front", "back", "left", "right", "top",
                               "bottom")},
            "unwrapMode": "custom", "visible": True,
            "doubleSided": False, "shadingMode": "flat",
        },
    }


def core_nodes(nid=900):
    """A solid core of stacked boxes just inside the facets, so a crack
    between two facet quads shows the gem's own dark colour, not what is
    behind the gem (the hand, when held; Mark, Nov 12). Each step's box
    is as wide as the step's top, so it stays under its sloped facets."""
    k = CORE_INSET
    top, bottom = GIRDLE_H / 2, -GIRDLE_H / 2
    d = CORE_DROP
    nodes = [core_box(nid, "Core_Girdle", HALF - k, bottom + d, top - d)]
    y = top
    for s, (inset, height) in enumerate(CROWN):
        y_top = top + height
        nodes.append(core_box(nid + 1 + s, f"Core_Crown_{s}",
                              HALF - inset - k, y - d, y_top - d))
        y = y_top
    y = bottom
    for s, (inset, depth) in enumerate(PAVILION[:-1]):
        y_low = bottom - depth
        nodes.append(core_box(nid + 10 + s, f"Core_Pavilion_{s}",
                              HALF - inset - k, y_low + d, y + d))
        y = y_low
    return nodes


def model():
    nodes, _ = gem_nodes()
    return {"nodes": [{"id": "1", "name": "R-Attachment",
                       "children": [{
                           "id": "999", "name": "Gem_Offset",
                           "children": core_nodes() + nodes,
                           "position": {"x": 0, "y": LIFT_Y, "z": 0},
                           "orientation": {"x": 0, "y": 0, "z": 0,
                                           "w": 1},
                           "shape": brilliant.ROOT_SHAPE}],
                       "position": {"x": 0, "y": 6, "z": 0},
                       "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                       "shape": brilliant.ROOT_SHAPE}],
            "lod": "auto"}


def paint_table(im, ox, oy, pal):
    """A plain square table with a darker border. It had a star of
    glints along its diagonals, but the cut art's edge light lit them
    as facet edges, and on the dark voidstone the X read as a gold seam
    or a gap between pieces (Mark, Nov 12)."""
    dark, low, mid, high, glint = pal
    q = Q
    for v in range(q):
        for u in range(q):
            du, dv = min(u, q - 1 - u), min(v, q - 1 - v)
            col = high
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

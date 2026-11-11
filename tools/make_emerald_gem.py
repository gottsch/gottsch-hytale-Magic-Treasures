#!/usr/bin/env python3
"""Make an emerald-cut gem: model, a texture per gem, and icons.

An emerald cut is a step cut: a rectangle with its corners cut off,
seen from above as rings of long flat facets stepping down from the
table, like a picture frame. Built as for the brilliant
(make_brilliant_gem.py): each facet is a tilted quad, cut to its
trapezoid shape by transparent texels.

- the outline: a rectangle (long along x) with 45 degree corners, so
  each ring has 8 edges: 2 long sides, 2 short sides and 4 corners;
- the table: a flat octagon, painted with faint step lines;
- the crown: 2 steps of 8 facets from the girdle up to the table;
- the girdle: 8 upright quads;
- the pavilion: 3 steps of 8 facets down to a keel, a short ridge
  along the long axis (the deepest ring has no width).

Each ring is the girdle outline moved inward by an inset, so every
facet is a symmetric trapezoid between two parallel edges.

Writes:
- Common/Items/MagicTreasures/Gems/Emerald_Cut_Gem.blockymodel
- Common/Items/MagicTreasures/Gems/<Gem>_Emerald_Cut.png (128 x 64)
- Common/Icons/Items/MagicTreasures/Gem_<Gem>_Emerald_Cut.png

Usage (from the repo root):
    python tools/make_emerald_gem.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import json
import math

from PIL import Image

import blocky_render
import make_brilliant_gem as brilliant
from make_gem_art import COMMON, GEMS, ICON_OUT, TEX_OUT, palette

MODEL_OUT = (COMMON / "Items" / "MagicTreasures" / "Gems"
             / "Emerald_Cut_Gem.blockymodel")

HALF_L, HALF_W = 8.0, 5.5   # the girdle's half length (x) and width (z)
CHAMFER = 2.2               # the corner cut, along each side
GIRDLE_H = 1.0
# (inset, height) of each ring, from the girdle; the table last
CROWN = [(1.3, 1.3), (2.4, 2.4)]
# down to the keel, where the inset equals HALF_W
PAVILION = [(1.8, 1.8), (3.6, 3.7), (HALF_W, 5.5)]
Q = 16
TEX_W, TEX_H = 128, 64
SLOTS = TEX_W // Q          # regions per row


def ring(inset):
    """The 8 corners of the outline moved inward by inset, going
    round from the +x side: [(x, z)]."""
    a, b = HALF_L - inset, HALF_W - inset
    c = max(0.0, CHAMFER - inset * (2 - math.sqrt(2)))
    c = min(c, a, b)
    return [(a, -(b - c)), (a, b - c), (a - c, b), (-(a - c), b),
            (-a, b - c), (-a, -(b - c)), (-(a - c), -b), (a - c, -b)]


EDGE_KIND = ["side_s", "corner", "side_l", "corner",
             "side_s", "corner", "side_l", "corner"]


def quat_from_basis(x, y, z):
    """The rotation taking the unit axes to x, y, z (orthonormal)."""
    m00, m01, m02 = x[0], y[0], z[0]
    m10, m11, m12 = x[1], y[1], z[1]
    m20, m21, m22 = x[2], y[2], z[2]
    tr = m00 + m11 + m22
    if tr > 0:
        s = math.sqrt(tr + 1.0) * 2
        return ((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, s / 4)
    if m00 > m11 and m00 > m22:
        s = math.sqrt(1.0 + m00 - m11 - m22) * 2
        return (s / 4, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
    if m11 > m22:
        s = math.sqrt(1.0 + m11 - m00 - m22) * 2
        return ((m01 + m10) / s, s / 4, (m12 + m21) / s, (m02 - m20) / s)
    s = math.sqrt(1.0 + m22 - m00 - m11) * 2
    return ((m02 + m20) / s, (m12 + m21) / s, s / 4, (m10 - m01) / s)


def sub(p, q):
    return tuple(a - b for a, b in zip(p, q))


def norm(v):
    n = math.sqrt(sum(c * c for c in v))
    return tuple(c / n for c in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


class Regions:
    """Hands out Q x Q texture regions, one per facet shape, and
    remembers how to paint each."""

    def __init__(self):
        self.shapes = {}

    def get(self, key, top_frac, tone):
        if key not in self.shapes:
            n = len(self.shapes)
            self.shapes[key] = ((n % SLOTS) * Q, (n // SLOTS) * Q,
                                top_frac, tone)
        return self.shapes[key][:2]


def facet(nid, name, outer, inner, regions, key, tone):
    """A trapezoid quad between a long outer edge and a parallel inner
    edge (3D points); the texture's narrow top is the inner edge."""
    # key goes into make_brilliant_gem.REGIONS, which the brilliant's
    # own texture() reads too: never reuse one of its names ("girdle",
    # "table", ...), or the brilliant paints that region in the wrong
    # place (a see-through girdle in game, Nov 10)
    assert key not in ("crown_a", "crown_b", "pavilion_a", "pavilion_b",
                       "table", "girdle"), key
    (o0, o1), (i0, i1) = outer, inner
    mo = tuple((a + b) / 2 for a, b in zip(o0, o1))
    mi = tuple((a + b) / 2 for a, b in zip(i0, i1))
    lo = math.dist(o0, o1)
    li = math.dist(i0, i1)
    if lo < 1e-3:
        return None
    t = norm(sub(o1, o0))
    up = sub(mi, mo)
    h = math.sqrt(sum(c * c for c in up))
    u = norm(up)
    z = cross(t, u)
    # the quad must face out of the gem
    out = norm((mo[0], 0.0, mo[2]))
    if sum(a * b for a, b in zip(z, out)) < 0:
        t = tuple(-c for c in t)
        z = cross(t, u)
    rot = quat_from_basis(t, u, z)
    region = regions.get(key, min(1.0, li / lo), tone)
    centre = tuple((a + b) / 2 for a, b in zip(mo, mi))
    brilliant.REGIONS[key] = region
    # a little wider and taller than exact, so neighbours overlap and
    # no seam shows between the narrow corner facets
    return brilliant.quad_node(nid, name, centre, rot, lo * 1.01, h * 1.01,
                               key)


def gem_nodes():
    regions = Regions()
    nodes = []
    nid = 0
    top = GIRDLE_H / 2
    bottom = -GIRDLE_H / 2

    def lift(points, y):
        return [(x, y, z) for x, z in points]

    girdle = ring(0.0)
    # the girdle: upright quads
    up = lift(girdle, top)
    down = lift(girdle, bottom)
    for k in range(8):
        n = facet(nid, f"Girdle_{k}", (down[k], down[(k + 1) % 8]),
                  (up[k], up[(k + 1) % 8]), regions, "e_girdle", "girdle")
        if n:
            nodes.append(n)
        nid += 1
    # the crown and the pavilion: steps between rings
    for part, steps, y0, sign in (("Crown", CROWN, top, 1),
                                  ("Pavilion", PAVILION, bottom, -1)):
        outer = lift(girdle, y0)
        for s, (inset, height) in enumerate(steps):
            inner = lift(ring(inset), y0 + sign * height)
            for k in range(8):
                kind = EDGE_KIND[k]
                o = (outer[k], outer[(k + 1) % 8])
                i = (inner[k], inner[(k + 1) % 8])
                lo, li = math.dist(*o), math.dist(*i)
                key = f"{part}_{s}_{kind}"
                tone = part.lower() + ("_light" if s % 2 == 0 else "_dark")
                n = facet(nid, f"{part}_{s}_{k}", o, i, regions, key, tone)
                if n:
                    nodes.append(n)
                nid += 1
            outer = inner
    # the table: a flat octagon
    inset, height = CROWN[-1]
    a, b = HALF_L - inset, HALF_W - inset
    regions.get("table", 1.0, "table")
    brilliant.REGIONS["table_e"] = regions.shapes["table"][:2]
    nodes.append(brilliant.quad_node(nid, "Table", (0, top + height, 0),
                                     (0, 0, 0, 1), 2 * a, 2 * b, "table_e",
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


def lerp(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def paint_step(im, ox, oy, top_frac, pal, tone):
    """A trapezoid facet: narrow edge at the top. Step facets are flat
    bands of colour with a bright edge, the look of a step cut."""
    dark, low, mid, high, glint = pal
    base = {"crown_light": (high, mid), "crown_dark": (mid, low),
            "pavilion_light": (mid, low), "pavilion_dark": (low, dark),
            "girdle": (low, low)}[tone]
    for v in range(Q):
        t = (v + 0.5) / Q
        half = (top_frac + (1 - top_frac) * t) * Q / 2
        for u in range(Q):
            d = abs(u + 0.5 - Q / 2)
            # any texel the facet touches (see make_brilliant_gem)
            if d > half + 0.5:
                continue
            col = lerp(base[0], base[1], t)
            if v == 0 and tone != "girdle":
                col = glint if tone.startswith("crown") else high
            if d > half - 1:
                col = lerp(col, dark, 0.45)
            im.putpixel((ox + u, oy + v), col + (255,))


def paint_table(im, ox, oy, pal):
    """The table: an octagon with faint concentric step lines (the
    "hall of mirrors" an emerald cut shows through its table)."""
    dark, low, mid, high, glint = pal
    inset, _ = CROWN[-1]
    a, b = HALF_L - inset, HALF_W - inset
    c = max(0.0, CHAMFER - inset * (2 - math.sqrt(2)))
    cu, cv = c / (2 * a) * Q, c / (2 * b) * Q
    for v in range(Q):
        for u in range(Q):
            du = min(u + 0.5, Q - u - 0.5)
            dv = min(v + 0.5, Q - v - 0.5)
            if du / cu + dv / cv < 1:
                continue
            ring_no = int(min(du / (Q / 2), dv / (Q / 2)) * 4)
            col = high if ring_no % 2 == 0 else lerp(high, mid, 0.5)
            if du < 1.2 or dv < 1.2 or du / cu + dv / cv < 1.25:
                col = mid
            if ring_no == 3:
                col = glint
            im.putpixel((ox + u, oy + v), col + (255,))


def texture(pal, regions):
    im = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    for key, (x, y, top_frac, tone) in regions.shapes.items():
        if tone == "table":
            paint_table(im, x, y, pal)
        else:
            paint_step(im, x, y, top_frac, pal, tone)
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
        tex = TEX_OUT / f"{gem}_Emerald_Cut.png"
        texture(palette(a.assets, gem), regions).save(tex)
        icons.append(blocky_render.render(
            str(MODEL_OUT), str(tex),
            str(ICON_OUT / f"Gem_{gem}_Emerald_Cut.png"),
            margin=5, pitch=28, yaw=30))
    print(f"Wrote the emerald-cut model ({len(regions.shapes)} facet "
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

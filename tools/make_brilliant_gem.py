#!/usr/bin/env python3
"""Make a brilliant-cut gem: model, a texture per gem, and icons.

Hytale models have only boxes and flat quads, so the facets are quads
tilted into place, with transparent texels cutting each rectangle to
the facet's real shape:

- the table: an octagon (a flat square quad with its corners cut);
- the crown: 8 trapezoids sloping from the table out to the girdle;
- the girdle: a thin band of 8 upright quads;
- the pavilion: 8 triangles meeting at the culet, below.

Each quad's texture region is 16 x 16 texels; its stretch sets its
real size, so every facet keeps the same texel density.

Writes:
- Common/Items/MagicTreasures/Gems/Brilliant_Gem.blockymodel
- Common/Items/MagicTreasures/Gems/<Gem>_Brilliant.png
- Common/Icons/Items/MagicTreasures/Gem_<Gem>_Brilliant.png

Usage (from the repo root):
    python tools/make_brilliant_gem.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import json
import math

from PIL import Image

import blocky_render
from make_gem_art import COMMON, GEMS, ICON_OUT, TEX_OUT, palette

MODEL_OUT = (COMMON / "Items" / "MagicTreasures" / "Gems"
             / "Brilliant_Gem.blockymodel")

SIDES = 8
R_GIRDLE = 8.0     # girdle circumradius, in model units
R_TABLE = 4.6      # table circumradius
CROWN_H = 3.2      # girdle top to table
GIRDLE_H = 1.0
PAVILION_H = 7.0   # girdle bottom to culet
Q = 16             # texels per quad side
TEX_SIZE = 64

# texture regions (x, y), each Q x Q
REGIONS = {
    "crown_a": (0, 0), "crown_b": (16, 0),
    "pavilion_a": (32, 0), "pavilion_b": (48, 0),
    "table": (0, 16), "girdle": (16, 16),
}


def side_len(r):
    return 2 * r * math.sin(math.pi / SIDES)


def apothem(r):
    return r * math.cos(math.pi / SIDES)


def quat_axis(axis, angle):
    s = math.sin(angle / 2)
    return (axis[0] * s, axis[1] * s, axis[2] * s, math.cos(angle / 2))


def facet_rotation(theta, tilt):
    """A +Z quad turned to face outward at angle theta (0 = +x) and
    tipped by tilt (positive tips its normal up)."""
    pitch = quat_axis((1, 0, 0), -tilt)
    yaw = quat_axis((0, 1, 0), math.pi / 2 - theta)
    return blocky_render.quat_mul(yaw, pitch)


def quad_node(i, name, pos, rot, width, height, region, normal="+Z"):
    x, y = REGIONS[region]
    q = {"x": rot[0], "y": rot[1], "z": rot[2], "w": rot[3]}
    return {
        "id": str(100 + i), "name": name, "children": [],
        "position": {"x": pos[0], "y": pos[1], "z": pos[2]},
        "orientation": q,
        "shape": {
            "type": "quad", "offset": {"x": 0, "y": 0, "z": 0},
            "stretch": {"x": width / Q, "y": 1, "z": height / Q}
            if normal == "+Y" else
            {"x": width / Q, "y": height / Q, "z": 1},
            "settings": {"size": {"x": Q, "y": Q}, "normal": normal},
            "textureLayout": {"front": {
                "offset": {"x": x, "y": y},
                "mirror": {"x": False, "y": False}, "angle": 0}},
            "unwrapMode": "custom", "visible": True,
            "doubleSided": False, "shadingMode": "reflective",
        },
    }


def brilliant_nodes(scale=1.0, center=(0.0, 0.0, 0.0), id_base=0):
    """The gem's facet quads, scaled and centred on the girdle; the
    table faces +y. Other models (rings, pendants) set gems with it,
    using the same texture regions."""
    nodes = []
    cx, cy, cz = center
    a_g, a_t = apothem(R_GIRDLE) * scale, apothem(R_TABLE) * scale
    s_g = side_len(R_GIRDLE) * scale
    crown_h, girdle_h, pav_h = (CROWN_H * scale, GIRDLE_H * scale,
                                PAVILION_H * scale)
    top, bottom = girdle_h / 2, -girdle_h / 2
    crown_tilt = math.atan2(crown_h, a_g - a_t)       # from horizontal
    crown_len = math.hypot(a_g - a_t, crown_h)
    pav_tilt = math.atan2(pav_h, a_g)
    pav_len = math.hypot(a_g, pav_h)
    n = id_base
    for k in range(SIDES):
        theta = k * 2 * math.pi / SIDES
        c, s = math.cos(theta), math.sin(theta)
        ab = "a" if k % 2 == 0 else "b"
        r = (a_g + a_t) / 2
        nodes.append(quad_node(
            n, f"Crown_{k}", (cx + r * c, cy + top + crown_h / 2, cz + r * s),
            facet_rotation(theta, math.pi / 2 - crown_tilt),
            s_g, crown_len, f"crown_{ab}"))
        n += 1
        nodes.append(quad_node(
            n, f"Girdle_{k}", (cx + a_g * c, cy, cz + a_g * s),
            facet_rotation(theta, 0), s_g, girdle_h, "girdle"))
        n += 1
        nodes.append(quad_node(
            n, f"Pavilion_{k}",
            (cx + a_g / 2 * c, cy + bottom - pav_h / 2, cz + a_g / 2 * s),
            facet_rotation(theta, -(math.pi / 2 - pav_tilt)),
            s_g, pav_len, f"pavilion_{ab}"))
        n += 1
    # the table: a flat octagon, as a square with its corners cut
    nodes.append(quad_node(n, "Table", (cx, cy + top + crown_h, cz),
                           (0, 0, 0, 1), 2 * a_t, 2 * a_t, "table",
                           normal="+Y"))
    return nodes


ROOT_SHAPE = {"type": "none", "offset": {"x": 0, "y": 0, "z": 0},
              "stretch": {"x": 1, "y": 1, "z": 1}, "settings": {},
              "textureLayout": {}, "unwrapMode": "custom",
              "visible": True, "doubleSided": False,
              "shadingMode": "flat"}


def model():
    # named and lifted like vanilla held items (salt +4, popberry +8):
    # the root is the hand's grip point, so the gem sits above it
    return {"nodes": [{"id": "1", "name": "R-Attachment",
                       "children": brilliant_nodes(),
                       "position": {"x": 0, "y": 6, "z": 0},
                       "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                       "shape": ROOT_SHAPE}],
            "lod": "auto"}


def lerp(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def paint_trapezoid(im, ox, oy, top_frac, pal, light):
    """Rows from the top (narrow, top_frac of the width) to the bottom
    (full width); outside is transparent."""
    dark, low, mid, high, glint = pal
    for v in range(Q):
        t = (v + 0.5) / Q
        half = (top_frac + (1 - top_frac) * t) * Q / 2
        for u in range(Q):
            if abs(u + 0.5 - Q / 2) > half:
                continue
            col = (lerp(high, mid, t) if light
                   else lerp(mid, dark, t))
            if v == 0:
                col = glint
            if abs(u + 0.5 - Q / 2) > half - 1:
                col = lerp(col, dark, 0.5)
            im.putpixel((ox + u, oy + v), col + (255,))


def paint_triangle(im, ox, oy, pal, light):
    """Full width at the top, a point at the bottom."""
    dark, low, mid, high, glint = pal
    for v in range(Q):
        t = (v + 0.5) / Q
        half = (1 - t) * Q / 2
        for u in range(Q):
            d = abs(u + 0.5 - Q / 2)
            if d > half:
                continue
            col = lerp(mid if light else low, dark, t)
            # a bright keel line down the middle
            if d < 1:
                col = lerp(col, high, 0.6 * (1 - t))
            if d > half - 1:
                col = lerp(col, dark, 0.5)
            im.putpixel((ox + u, oy + v), col + (255,))


def paint_table(im, ox, oy, pal):
    dark, low, mid, high, glint = pal
    cut = Q * (1 - math.tan(math.pi / SIDES)) / 2   # octagon corners
    for v in range(Q):
        for u in range(Q):
            cu, cv = min(u + 0.5, Q - u - 0.5), min(v + 0.5, Q - v - 0.5)
            if cu + cv < cut:
                continue
            col = high
            if (u + v) % 9 in (3, 4):
                col = glint
            if cu < 1.2 or cv < 1.2 or cu + cv < cut + 1.2:
                col = mid
            im.putpixel((ox + u, oy + v), col + (255,))


def texture(pal):
    im = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
    top_frac = side_len(R_TABLE) / side_len(R_GIRDLE)
    paint_trapezoid(im, *REGIONS["crown_a"], top_frac, pal, True)
    paint_trapezoid(im, *REGIONS["crown_b"], top_frac, pal, False)
    paint_triangle(im, *REGIONS["pavilion_a"], pal, True)
    paint_triangle(im, *REGIONS["pavilion_b"], pal, False)
    paint_table(im, *REGIONS["table"], pal)
    gx, gy = REGIONS["girdle"]
    for v in range(Q):
        for u in range(Q):
            im.putpixel((gx + u, gy + v), pal[1] + (255,))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    TEX_OUT.mkdir(parents=True, exist_ok=True)
    ICON_OUT.mkdir(parents=True, exist_ok=True)
    MODEL_OUT.write_text(json.dumps(model(), indent=2) + "\n",
                         encoding="utf-8", newline="\n")
    icons = []
    for gem in GEMS:
        tex = TEX_OUT / f"{gem}_Brilliant.png"
        texture(palette(a.assets, gem)).save(tex)
        icons.append(blocky_render.render(
            str(MODEL_OUT), str(tex),
            str(ICON_OUT / f"Gem_{gem}_Brilliant.png"),
            margin=5, pitch=18, yaw=22.5))
    print(f"Wrote the brilliant model, {len(GEMS)} textures and icons.")
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

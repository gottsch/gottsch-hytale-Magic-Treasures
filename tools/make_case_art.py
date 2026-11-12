#!/usr/bin/env python3
"""Draws the Treasures Case: its model, its texture and its inventory icon.

A rugged little strongbox: weathered oak planks bound with dark iron
straps, iron corner guards and a riveted iron plinth, an overhanging lid
with a raised top, and an iron hasp with a brass lock across the seam.
The node chain above the box (L-Attachment > Origin_Projectile >
Origin_Item) is kept from the old bag model, so the case is held the
same way. The item id stays MT_Jewelry_Case, so cases already in worlds
keep working.

Every face gets its own rectangle of the texture, packed in rows, and is
painted by its part's material, so the texture never has to be drawn by
hand.

Usage: python tools/make_case_art.py
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import blocky_render  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
COMMON = ROOT / "src" / "main" / "resources" / "Common"
MODEL_OUT = COMMON / "Items" / "MagicTreasures" / "Jewelry_Case.blockymodel"
TEX_OUT = COMMON / "Items" / "MagicTreasures" / "Jewelry_Case.png"
ICON_OUT = COMMON / "Icons" / "Items" / "MagicTreasures" / "MT_Jewelry_Case.png"

TEX_W, TEX_H = 128, 64

WOOD = (118, 78, 46)
WOOD_DARK = (84, 54, 32)
IRON = (74, 76, 84)
RUST = (122, 74, 44)
BRASS = (200, 156, 70)

FACES = ("front", "back", "left", "right", "top", "bottom")

# name, size (x, y, z), position relative to its parent, material,
# parent name; the base is the root of the case
PARTS = [
    ("Base", (20, 10, 14), (0, -11, 0), "wood", None),
    ("Plinth", (21, 2, 15), (0, -4, 0), "iron", "Base"),
    ("Lid", (21, 5, 15), (0, 7.5, 0), "wood", "Base"),
    ("Lid_Top", (17, 2, 11), (0, 3.5, 0), "wood", "Lid"),
    ("Corner_FL", (2.5, 10, 2.5), (-9.5, 0, 6.5), "iron", "Base"),
    ("Corner_FR", (2.5, 10, 2.5), (9.5, 0, 6.5), "iron", "Base"),
    ("Corner_BL", (2.5, 10, 2.5), (-9.5, 0, -6.5), "iron", "Base"),
    ("Corner_BR", (2.5, 10, 2.5), (9.5, 0, -6.5), "iron", "Base"),
    ("Hasp", (4, 6, 1), (0, 4.5, 7.5), "iron", "Base"),
    ("Lock", (2, 2, 1), (0, -1, 1), "brass", "Hasp"),
]


def dims(face, size):
    return blocky_render.face_dims(face, size)


def tex_size(size):
    """Texels for a size: half units round up, so a 2.5 part gets 3."""
    return tuple(max(1, int(round(v + 0.01))) for v in size)


def pack():
    """Places every face of every part in the texture; returns
    {(part, face): (x, y, w, h)}."""
    rects = []
    for name, size, _, _, _ in PARTS:
        for face in FACES:
            w, h = dims(face, tex_size(size))
            rects.append(((name, face), w, h))
    rects.sort(key=lambda r: -r[2])
    out, x, y, row = {}, 0, 0, 0
    for key, w, h in rects:
        if x + w > TEX_W:
            x, y, row = 0, y + row + 1, 0
        if y + h > TEX_H:
            raise SystemExit("texture too small")
        out[key] = (x, y, w, h)
        x += w + 1
        row = max(row, h)
    return out


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def iron_texel(u, v, w, h, rng, rivets=True):
    c = shade(IRON, rng.randint(-8, 8))
    if rng.random() < 0.08:
        c = shade(RUST, rng.randint(-10, 10))
    if v == 0:
        c = shade(IRON, 40)
    elif v == h - 1 or u == w - 1:
        c = shade(IRON, -30)
    # rivets one texel in from the corners of larger faces
    if rivets and w >= 4 and h >= 4 and u in (1, w - 2) and v in (1, h - 2):
        c = shade(IRON, 70)
    return c


def paint(im, rect, name, material, face, rng):
    x0, y0, w, h = rect
    px = im.load()
    vertical = face in ("front", "back", "left", "right")
    # where the iron straps cross the wood: two bands across the long faces
    straps = set()
    if material == "wood" and name != "Lid_Top" and w >= 14:
        for s in (round(w * 0.22), round(w * 0.78) - 1):
            straps.update((s, s + 1))
    for v in range(h):
        for u in range(w):
            if material == "wood":
                # planks run along the face; a dark seam every 4 texels
                along, across = (u, v) if vertical or face == "top" else (u, v)
                grain = rng.randint(-10, 10) + (6 if (along * 7 + across * 3) % 11 == 0 else 0)
                c = shade(WOOD, grain)
                if across % 4 == 3:
                    c = shade(WOOD_DARK, rng.randint(-6, 4))
                if rng.random() < 0.04:  # scuffs and knots
                    c = shade(WOOD_DARK, -18)
                if u in (0, w - 1) or v in (0, h - 1):
                    c = shade(WOOD_DARK, -10)
                if u in straps:
                    c = iron_texel(0, 1, 3, 3, rng, rivets=False)
                    if v % 3 == 1:
                        c = shade(IRON, 60)
            elif material == "iron":
                c = iron_texel(u, v, w, h, rng)
            else:  # brass lock with a keyhole
                c = shade(BRASS, 0)
                if u == 0 and v == 0:
                    c = shade(BRASS, 50)
                elif u == w - 1 or v == h - 1:
                    c = shade(BRASS, -60)
                if face == "front" and u == w // 2 and v == h - 1:
                    c = (30, 22, 14, 255)
            px[x0 + u, y0 + v] = c


def node(nid, name, size, pos, layout, children):
    t = tex_size(size)
    return {
        "id": str(nid), "name": name, "children": children,
        "position": {"x": pos[0], "y": pos[1], "z": pos[2]},
        "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
        "shape": {
            "type": "box", "offset": {"x": 0, "y": 0, "z": 0},
            "stretch": {"x": size[0] / t[0], "y": size[1] / t[1], "z": size[2] / t[2]},
            "settings": {"size": {"x": t[0], "y": t[1], "z": t[2]}},
            "textureLayout": layout,
            "unwrapMode": "custom", "visible": True,
            "doubleSided": False, "shadingMode": "flat",
        },
    }


def empty(nid, name, pos, children):
    return {
        "id": str(nid), "name": name, "children": children,
        "position": {"x": pos[0], "y": pos[1], "z": pos[2]},
        "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
        "shape": {
            "type": "none", "offset": {"x": 0, "y": 0, "z": 0},
            "stretch": {"x": 1, "y": 1, "z": 1}, "settings": {},
            "textureLayout": {}, "unwrapMode": "custom", "visible": True,
            "doubleSided": False, "shadingMode": "flat",
        },
    }


def build_model(rects):
    made = {}
    for i, (name, size, pos, _, _) in enumerate(PARTS):
        layout = {f: {"offset": {"x": rects[(name, f)][0], "y": rects[(name, f)][1]},
                      "mirror": {"x": False, "y": False}, "angle": 0}
                  for f in FACES}
        made[name] = node(100 + i, name, size, pos, layout, [])
    root = None
    for name, _, _, _, parent in PARTS:
        if parent is None:
            root = made[name]
        else:
            made[parent]["children"].append(made[name])
    chain = empty(0, "L-Attachment", (0, 0, 0), [
        empty(12, "Origin_Projectile", (0, 31, 0), [
            empty(11, "Origin_Item", (0, -15, 0), [root])])])
    return {"nodes": [chain], "lod": "auto"}


def main():
    rng = random.Random(7)
    rects = pack()
    tex = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    for name, _, _, material, _ in PARTS:
        for face in FACES:
            paint(tex, rects[(name, face)], name, material, face, rng)
    TEX_OUT.parent.mkdir(parents=True, exist_ok=True)
    ICON_OUT.parent.mkdir(parents=True, exist_ok=True)
    tex.save(TEX_OUT)
    MODEL_OUT.write_text(json.dumps(build_model(rects), indent=2) + "\n",
                         encoding="utf-8", newline="\n")
    blocky_render.render(str(MODEL_OUT), str(TEX_OUT), str(ICON_OUT),
                         size=64, yaw=35, pitch=24, margin=4)
    print("wrote", MODEL_OUT.name, TEX_OUT.name, ICON_OUT.name)


if __name__ == "__main__":
    main()

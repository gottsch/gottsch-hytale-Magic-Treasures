#!/usr/bin/env python3
"""Make the cut-gem art: one model, a texture and an icon per gem.

- Model: an emerald (step) cut: a rectangular stone of stacked
  boxes, written to
  Common/Items/MagicTreasures/Gems/Cut_Gem.blockymodel
- Textures: Common/Items/MagicTreasures/Gems/<Gem>.png, 32x32, pixel
  art with bevelled facets like the vanilla gems. The colours are
  sampled from the vanilla gem texture, so a ruby matches the
  vanilla ruby.
- Icons: Common/Icons/Items/MagicTreasures/Gem_<Gem>.png, rendered
  with tools/blocky_render.py.

Each box has a top region, a front region (shared with the back) and
a side region (shared by left and right).

Usage (from the repo root):
    python tools/make_gem_art.py --assets <Assets.zip> [--preview P]
"""
import argparse
import io
import json
import zipfile
from pathlib import Path

from PIL import Image

import blocky_render

ROOT = Path(__file__).resolve().parent.parent
COMMON = ROOT / "src" / "main" / "resources" / "Common"
MODEL_OUT = COMMON / "Items" / "MagicTreasures" / "Gems" / "Cut_Gem.blockymodel"
TEX_OUT = COMMON / "Items" / "MagicTreasures" / "Gems"
ICON_OUT = COMMON / "Icons" / "Items" / "MagicTreasures"

GEMS = ["Topaz", "Ruby", "Sapphire", "Emerald", "Diamond", "Voidstone",
        "Zephyr"]

# An emerald (step) cut: a rectangular stone whose crown rises in
# steps to the table, over a pavilion that steps down to a keel.
# (name, width x, depth z, height, y of the box centre, top shade,
# side shade), bottom to top. Shades index the palette: 0 dark .. 4
# glint.
BOXES = [
    ("Keel", 6, 2, 1, -4.5, 1, 0),
    ("Pavilion_2", 10, 6, 2, -3, 1, 0),
    ("Pavilion_1", 14, 10, 2, -1, 1, 1),
    ("Girdle", 16, 12, 1, 0.5, 1, 2),
    ("Step_1", 14, 10, 1, 1.5, 2, 1),
    ("Step_2", 12, 8, 1, 2.5, 3, 2),
    ("Table", 10, 6, 1, 3.5, 3, 3),
]
TEX_SIZE = 64
ICON_PITCH = 30
ICON_YAW = 30


def palette(assets, gem):
    """Five colours, dark to bright, from the vanilla gem texture."""
    with zipfile.ZipFile(assets) as z:
        data = z.read(f"Common/Resources/Ores/Gem_Textures/{gem}.png")
    im = Image.open(io.BytesIO(data)).convert("RGBA")
    px = [im.getpixel((x, y)) for y in range(im.height)
          for x in range(im.width) if im.getpixel((x, y))[3] > 200]
    px.sort(key=lambda p: 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2])
    pick = [px[int(len(px) * f)] for f in (0.06, 0.3, 0.55, 0.8, 0.97)]
    return [tuple(c[:3]) for c in pick]


def layout():
    """Packs each box's top, front and side regions into the texture.

    Returns {box name: {"top": (x, y), "front": (x, y), "side": (x, y)}}.
    """
    out = {}
    x, y, row_h = 0, 0, 0
    for name, wx, wz, h, *_ in BOXES:
        regions = {}
        for kind, (rw, rh) in (("top", (wx, wz)), ("front", (wx, h)),
                               ("side", (wz, h))):
            if x + rw > TEX_SIZE:
                x, y, row_h = 0, y + row_h, 0
            regions[kind] = (x, y)
            x += rw
            row_h = max(row_h, rh)
        out[name] = regions
    if y + row_h > TEX_SIZE:
        raise SystemExit("texture too small for the boxes")
    return out


def fill(im, ox, oy, w, h, col):
    for v in range(h):
        for u in range(w):
            im.putpixel((ox + u, oy + v), col + (255,))


def table(im, ox, oy, w, h, pal):
    """The table: bright, with a diagonal sheen and a glint."""
    dark, low, mid, high, glint = pal
    for v in range(h):
        for u in range(w):
            col = high
            if (u + v) % 7 in (2, 3):
                col = glint
            if u == 0 or v == 0:
                col = glint
            im.putpixel((ox + u, oy + v), col + (255,))


def texture(pal, regions):
    im = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
    for name, wx, wz, h, _, top, side in BOXES:
        r = regions[name]
        if name == "Table":
            table(im, *r["top"], wx, wz, pal)
        else:
            fill(im, *r["top"], wx, wz, pal[top])
            # a lighter rim on the step's back edges catches the light
            tx, ty = r["top"]
            for u in range(wx):
                im.putpixel((tx + u, ty), pal[min(4, top + 1)] + (255,))
            for v in range(wz):
                im.putpixel((tx, ty + v), pal[min(4, top + 1)] + (255,))
        fill(im, *r["front"], wx, h, pal[side])
        fill(im, *r["side"], wz, h, pal[max(0, side - 1)])
    return im


def model(regions):
    def lay(x, y):
        return {"offset": {"x": x, "y": y},
                "mirror": {"x": False, "y": False}, "angle": 0}

    children = []
    for i, (name, wx, wz, h, cy, *_) in enumerate(BOXES):
        top = lay(*regions[name]["top"])
        front = lay(*regions[name]["front"])
        side = lay(*regions[name]["side"])
        children.append({
            "id": str(10 + i), "name": name, "children": [],
            "position": {"x": 0, "y": cy, "z": 0},
            "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
            "shape": {
                "type": "box", "offset": {"x": 0, "y": 0, "z": 0},
                "stretch": {"x": 1, "y": 1, "z": 1},
                "settings": {"size": {"x": wx, "y": h, "z": wz}},
                "visible": True, "doubleSided": False,
                "shadingMode": "reflective", "unwrapMode": "custom",
                "textureLayout": {"front": front, "back": front,
                                  "left": side, "right": side,
                                  "top": top, "bottom": top},
            },
        })
    # named and lifted like vanilla held items (salt +4, popberry +8):
    # the root is the hand's grip point, so the gem sits above it
    return {"nodes": [{"id": "1", "name": "R-Attachment", "children": children,
                       "position": {"x": 0, "y": 4, "z": 0},
                       "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                       # an empty grouping node, shaped like vanilla's
                       "shape": {"type": "none",
                                 "offset": {"x": 0, "y": 0, "z": 0},
                                 "stretch": {"x": 1, "y": 1, "z": 1},
                                 "settings": {}, "textureLayout": {},
                                 "unwrapMode": "custom", "visible": True,
                                 "doubleSided": False,
                                 "shadingMode": "flat"}}],
            "lod": "auto"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview", help="also write a contact sheet here")
    a = ap.parse_args()

    regions = layout()
    for folder in (TEX_OUT, ICON_OUT):
        folder.mkdir(parents=True, exist_ok=True)
    MODEL_OUT.write_text(json.dumps(model(regions), indent=2) + "\n",
                         encoding="utf-8", newline="\n")
    icons = []
    for gem in GEMS:
        tex_path = TEX_OUT / f"{gem}.png"
        texture(palette(a.assets, gem), regions).save(tex_path)
        icon_path = ICON_OUT / f"Gem_{gem}.png"
        icons.append(blocky_render.render(str(MODEL_OUT), str(tex_path),
                                          str(icon_path), margin=6,
                                          pitch=ICON_PITCH, yaw=ICON_YAW))
    print(f"Wrote the model, {len(GEMS)} textures and {len(GEMS)} icons.")

    if a.preview:
        cell = 160
        sheet = Image.new("RGBA", (cell * len(GEMS), cell * 2),
                          (55, 57, 66, 255))
        for i, gem in enumerate(GEMS):
            sheet.alpha_composite(
                icons[i].resize((128, 128), Image.NEAREST),
                (i * cell + 16, 16))
            tex = Image.open(TEX_OUT / f"{gem}.png")
            sheet.alpha_composite(tex.resize((128, 128), Image.NEAREST),
                                  (i * cell + 16, cell + 16))
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

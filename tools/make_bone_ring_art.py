#!/usr/bin/env python3
"""Make the Bone ring art for the Vampire Lich's Ring (Nov 13): a bone
band topped with a blocky skull whose eyes are small rubies. Not a
full Bone set.

It reuses make_ring_art's band, rims and painting, with a hand-picked
bone palette in place of a metal bar's (sampling vanilla's
Ingredient_Bone_Fragment icon gave a khaki tan with green shadows that
didn't read as bone, Mark, Nov 13). The skull replaces the collar and
gem of a set ring. Its eye sockets and nose are painted dark on the
cranium's front; a small ruby box sits in each socket, a little
smaller than it, so a dark rim shows round each eye. The skull faces
+z, the side the icon camera sees.

It writes:

- Rings/Ring_Bone.png: the plain band, which the worn ring uses;
- Rings/Ring_Lich.blockymodel and Rings/Ring_Lich.png: the skull ring,
  held and dropped;
- Icons/Items/MagicTreasures/MT_Ring_Vampire_Lich.png: its icon, a
  render at the same scale as the other ring icons.

Usage (from the repo root):
    python tools/make_bone_ring_art.py --assets <Assets.zip>
        [--palette ivory|aged|pale] [--preview P]

--preview also writes the icon beside the gold ruby ring's, enlarged.
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import blocky_render  # noqa: E402
import make_ring_art as ring  # noqa: E402
from make_gem_art import palette  # noqa: E402

ICON_ID = "MT_Ring_Vampire_Lich"
MODEL = ring.RINGS_OUT / "Ring_Lich.blockymodel"
TEXTURE = ring.RINGS_OUT / "Ring_Lich.png"

# five shades, dark to bright, as make_ring_art.metal_palette gives
PALETTES = {
    # warm ivory: cream with warm grey-brown shadows
    "ivory": [(112, 96, 78), (172, 156, 132), (212, 200, 178),
              (234, 226, 206), (250, 246, 234)],
    # aged bone: yellowed, brown in the shadows (Mark's pick, Nov 13)
    "aged": [(104, 84, 58), (164, 140, 104), (204, 184, 146),
             (226, 212, 178), (244, 236, 212)],
    # pale bone: grey-white, cool shadows, like a bleached skull
    "pale": [(104, 100, 94), (164, 160, 152), (206, 202, 194),
             (230, 228, 222), (248, 247, 243)],
}
DEFAULT = "aged"
# the eye sockets, nose and the gaps between teeth
HOLLOW = (46, 34, 26)

# skull texture regions (x, y, w, h) in the top half, which a set ring
# gives its gem; this texture has no gem
HEAD_TEX = (10, 9, 10)     # texels: x, y, z
CAP_TEX = (8, 2, 8)
CHEEK_TEX = (8, 3, 9)
TEETH_TEX = (6, 2, 1)
EYE_TEX = (2, 2, 1)
SKULL = {
    "head_front": (0, 0, 10, 9),
    "head_back": (10, 0, 10, 9),
    "head_side": (20, 0, 10, 9),
    "head_top": (40, 0, 10, 10),
    "head_bottom": (50, 0, 10, 10),
    "cap_top": (0, 10, 8, 8),
    "cap_side": (8, 10, 8, 2),
    "cheek_front": (16, 10, 8, 3),
    "cheek_back": (16, 13, 8, 3),
    "cheek_side": (24, 10, 9, 3),
    "cheek_top": (34, 10, 8, 9),
    "teeth_front": (16, 16, 6, 2),
    "teeth_rest": (16, 18, 6, 2),
    "eye_front": (0, 20, 2, 2),
    "eye_rest": (2, 20, 2, 2),
}

# the skull, in model units: (centre, size); the Great band's top is at
# y = r + t = 6, its width 3.6 along z
HEAD = ((0, 9.5, -0.1), (5.2, 4.4, 5.0))
CAP = ((0, 12.1, -0.2), (4.2, 0.8, 4.0))
CHEEK = ((0, 6.6, 0.2), (4.4, 1.5, 4.6))
TEETH = ((0, 6.5, 2.55), (3.0, 1.0, 0.6))
EYE_SIZE = (1.0, 1.0, 0.4)
# sockets on the head's front: texel columns and rows (of 10 x 9)
SOCKET_COLS = ((1, 2, 3), (6, 7, 8))
SOCKET_ROWS = (3, 4, 5)
NOSE = ((4, 6), (5, 6), (4, 7), (5, 7))


def faces(front, back=None, side=None, top=None, bottom=None):
    lay = ring.layout
    return {"front": lay(front), "back": lay(back or front),
            "left": lay(side or front), "right": lay(side or front),
            "top": lay(top or front), "bottom": lay(bottom or top or front)}


def texel_centre(cols, rows, centre, size, tex):
    """The model position of a group of texels on a box's front face."""
    (cx, cy, _), (sx, sy, _) = centre, size
    u = sum(cols) / len(cols) + 0.5
    v = sum(rows) / len(rows) + 0.5
    return cx - sx / 2 + u * sx / tex[0], cy + sy / 2 - v * sy / tex[1]


def lich_model():
    """The Great band and rims, with the skull in place of a collar and
    gem."""
    model = ring.item_model("Great", None)
    nodes = model["nodes"][0]["children"]
    S = SKULL
    parts = [
        ("Skull_Head", HEAD, HEAD_TEX,
         faces(S["head_front"], S["head_back"], S["head_side"],
               S["head_top"], S["head_bottom"])),
        ("Skull_Cap", CAP, CAP_TEX,
         faces(S["cap_side"], side=S["cap_side"], top=S["cap_top"])),
        ("Skull_Cheeks", CHEEK, CHEEK_TEX,
         faces(S["cheek_front"], S["cheek_back"], S["cheek_side"],
               S["cheek_top"])),
        ("Skull_Teeth", TEETH, TEETH_TEX,
         faces(S["teeth_front"], S["teeth_rest"], S["teeth_rest"],
               S["teeth_rest"])),
    ]
    nid = 2000
    for name, (centre, size), tex, f in parts:
        nodes.append(ring.box(nid, name, centre, (0, 0, 0, 1), tex, size, f))
        nid += 1
    # a ruby in each socket, just proud of the head's front
    front_z = HEAD[0][2] + HEAD[1][2] / 2
    for side, cols in zip(("L", "R"), SOCKET_COLS):
        x, y = texel_centre(cols, SOCKET_ROWS, *HEAD, HEAD_TEX)
        nodes.append(ring.box(nid, f"Skull_Eye_{side}",
                              (x, y, front_z + 0.05), (0, 0, 0, 1), EYE_TEX,
                              EYE_SIZE,
                              faces(S["eye_front"], S["eye_rest"],
                                    S["eye_rest"], S["eye_rest"])))
        nid += 1
    return model


def fill(im, region, colour):
    x0, y0, w, h = region
    for v in range(h):
        for u in range(w):
            im.putpixel((x0 + u, y0 + v), colour + (255,))


def paint_skull(im, pal, ruby):
    """The skull's regions: bone lit from above, dark hollows, and ruby
    eyes with a glint."""
    dark, low, mid, high, shine = pal
    S = SKULL

    def shade(region, top_rows=1, bottom_rows=1, edges=True):
        x0, y0, w, h = region
        for v in range(h):
            for u in range(w):
                c = mid
                if v < top_rows:
                    c = high
                elif v >= h - bottom_rows:
                    c = low
                if edges and (u == 0 or u == w - 1) and c != low:
                    c = low if c == mid else mid
                im.putpixel((x0 + u, y0 + v), c + (255,))

    # the cranium's front: brow lit, then sockets and nose hollow
    x0, y0, _, _ = S["head_front"]
    shade(S["head_front"], top_rows=3, bottom_rows=1)
    for cols in SOCKET_COLS:
        for u in cols:
            # a brighter brow ridge right above each socket
            im.putpixel((x0 + u, y0 + SOCKET_ROWS[0] - 1), shine + (255,))
            for v in SOCKET_ROWS:
                im.putpixel((x0 + u, y0 + v), HOLLOW + (255,))
    for u, v in NOSE:
        im.putpixel((x0 + u, y0 + v), HOLLOW + (255,))
    shade(S["head_back"], top_rows=2, bottom_rows=2)
    shade(S["head_side"], top_rows=2, bottom_rows=2)
    # temples: a shallow hollow on each side
    sx, sy, _, _ = S["head_side"]
    for u in (6, 7):
        for v in (4, 5):
            im.putpixel((sx + u, sy + v), low + (255,))
    # the crown: brightest in the middle
    x0, y0, w, h = S["head_top"]
    for v in range(h):
        for u in range(w):
            d = max(abs(u + 0.5 - w / 2), abs(v + 0.5 - h / 2)) / (w / 2)
            c = shine if d < 0.4 else high if d < 0.8 else mid
            im.putpixel((x0 + u, y0 + v), c + (255,))
    fill(im, S["head_bottom"], low)
    fill(im, S["cap_top"], shine)
    fill(im, S["cap_side"], high)
    # cheekbones: lit under each socket, shadowed below
    shade(S["cheek_front"], top_rows=1, bottom_rows=1)
    shade(S["cheek_back"], top_rows=1, bottom_rows=1)
    shade(S["cheek_side"], top_rows=1, bottom_rows=1)
    fill(im, S["cheek_top"], mid)
    # teeth: bright with dark gaps, the bottom row in shadow
    x0, y0, w, h = S["teeth_front"]
    for v in range(h):
        for u in range(w):
            c = HOLLOW if u % 2 == 1 and v == h - 1 else high if v == 0 else mid
            im.putpixel((x0 + u, y0 + v), c + (255,))
    fill(im, S["teeth_rest"], low)
    # ruby eyes: a glint top left on the front, deep red elsewhere
    r_dark, r_low, r_mid, r_high, r_shine = ruby
    x0, y0, _, _ = S["eye_front"]
    for (u, v), c in {(0, 0): r_shine, (1, 0): r_high,
                      (0, 1): r_mid, (1, 1): r_low}.items():
        im.putpixel((x0 + u, y0 + v), c + (255,))
    fill(im, S["eye_rest"], r_dark)


def band_texture(pal):
    im = Image.new("RGBA", (ring.TEX, ring.TEX), (0, 0, 0, 0))
    ring.paint_metal(im, pal)
    return im


def icon_span():
    """The same icon scale as make_ring_art: fitted to the largest
    standard ring. The skull ring is taller, so it may be fitted on its
    own instead (see main)."""
    def model_path(style, gem):
        return ring.RINGS_OUT / (f"Ring_{style}" + ("_Set" if gem else "")
                                 + ".blockymodel")
    return max(blocky_render.fit_span(str(model_path(style, gem)),
                                      str(ring.RINGS_OUT / "Ring_Gold_Ruby.png"),
                                      yaw=35, pitch=24)
               for style in ring.STYLES for gem in (None, "x"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--palette", choices=list(PALETTES), default=DEFAULT)
    ap.add_argument("--preview", help="also write the icon beside the gold ring's")
    a = ap.parse_args()

    pal = PALETTES[a.palette]
    band_texture(pal).save(ring.RINGS_OUT / "Ring_Bone.png")
    tex = band_texture(pal)
    paint_skull(tex, pal, palette(a.assets, "Ruby"))
    tex.save(TEXTURE)
    MODEL.write_text(json.dumps(lich_model(), indent=2), encoding="utf-8", newline="\n")

    # the standard ring scale, unless the skull ring wouldn't fit in it
    span = max(icon_span(), blocky_render.fit_span(str(MODEL), str(TEXTURE), yaw=35, pitch=24))
    icon = ring.ICON_OUT / f"{ICON_ID}.png"
    blocky_render.render(str(MODEL), str(TEXTURE), str(icon), margin=4, pitch=24, yaw=35, span=span)
    print(f"Wrote Ring_Bone.png, {MODEL.name}, {TEXTURE.name} and {icon.name} ({a.palette})")

    if a.preview:
        gold = Image.open(ring.ICON_OUT / "MT_Ring_Gold_Ruby_Great.png").convert("RGBA")
        lich = Image.open(icon).convert("RGBA")
        size, gap = 256, 8
        sheet = Image.new("RGBA", (size * 2 + gap, size), (40, 36, 34, 255))
        for i, tile in enumerate((gold, lich)):
            sheet.alpha_composite(tile.resize((size, size), Image.NEAREST), (i * (size + gap), 0))
        sheet.save(a.preview)
        print("preview:", a.preview)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Make the ring art: item models, textures, icons and worn models.

Item models (held, dropped, and rendered for icons), in
Common/Items/MagicTreasures/Rings/:
- Ring_<Style>.blockymodel: a round band of box segments standing
  upright (its hole along z);
- Ring_<Style>_Set.blockymodel: the same with a collar on top holding
  a small brilliant gem (make_brilliant_gem.brilliant_nodes), table up.
  Great rings add four prongs.

Textures, Ring_<Material>[_<Gem>].png (64 x 64): the gem's facet
regions sit in the top half at the same places as in the loose
brilliant's texture, so the gem nodes need no changes; the metal is in
the bottom half, coloured from the vanilla bar of that metal.

Icons, Common/Icons/Items/MagicTreasures/MT_Ring_*.png: one per item,
rendered with blocky_render.

Worn models, Common/Cosmetics/MagicTreasures/:
- Ring_Worn_R.blockymodel and Ring_Worn_L: a thin band around a
  finger-wide slice at the front of the hand, hung from the player's
  bone chain Belly > ... > R-Hand (or L-Hand), each bone node marked
  "isPiece" like vanilla cosmetics (without it a worn ring hid the
  first-person arms, Nov 11). The band is 0.4 units
  off the skin, so it shows on a bare hand and
  mostly stays inside a glove (vanilla gloves are
  the hand box stretched about 1.05). Mark, Nov 9: a glove covers the
  ring, and that's fine.
- They use the ring item texture of the metal (Ring_<Material>.png),
  not a greyscale texture tinted by the material's gradient: the
  client refuses an attachment texture it hasn't loaded ("Cannot use
  ... as an entity attachment texture"), and it loads only textures
  that assets reference. Nothing references a worn-only texture.

Usage (from the repo root):
    python tools/make_ring_art.py --assets <Assets.zip> [--preview P]
    # a variant to compare, e.g. 8 segments, without touching the art:
    python tools/make_ring_art.py --assets <Assets.zip> --segments 8
        --variant Oct --icons <folder> [--preview P]
"""
import argparse
import io
import json
import math
import zipfile

from PIL import Image

import blocky_render
import make_brilliant_gem as brilliant
from make_gem_art import COMMON, palette

RINGS_OUT = COMMON / "Items" / "MagicTreasures" / "Rings"
ICON_OUT = COMMON / "Icons" / "Items" / "MagicTreasures"
WORN_OUT = COMMON / "Cosmetics" / "MagicTreasures"

MATERIALS = ["Copper", "Silver", "Gold"]
GEMS = [None, "Topaz", "Ruby", "Sapphire", "Diamond", "Emerald", "Zephyr", "Voidstone"]
TEX = 64
METAL_Y = 32
# --segments: band segments for every style, instead of the style's own
SEGMENTS = None          # the metal regions start here

# band: inner radius, thickness, width (along the hole), segments
STYLES = {
    "Regular": {"r": 4.0, "t": 1.2, "w": 2.5, "n": 12,
                "collar": (3.0, 1.2, 3.0), "gem": 0.32, "prongs": False,
                "rims": False},
    "Great": {"r": 4.4, "t": 1.6, "w": 3.6, "n": 12,
              "collar": (4.0, 1.6, 4.0), "gem": 0.40, "prongs": True,
              "rims": True},
}

# metal texture regions (x, y, w, h), all in the bottom half
SEG = (4, 8)                       # texels: chord x width
METAL = {
    "outer": (0, METAL_Y, 4, 8),   # a segment's outside (box top)
    "inner": (4, METAL_Y, 4, 8),   # inside (box bottom)
    "edge": (8, METAL_Y, 4, 2),    # the band's flat edges (front/back)
    "end": (12, METAL_Y, 8, 2),    # segment ends (hidden)
    "collar_top": (0, METAL_Y + 8, 6, 6),
    "collar_side": (6, METAL_Y + 8, 6, 3),
    "prong": (12, METAL_Y + 8, 2, 4),
    "rim_outer": (16, METAL_Y + 8, 4, 2),
}

# the worn band (in the hand's frame: the hand box is 10 x 12 x 14)
HAND = (10.0, 12.0, 14.0)
WORN_GAP = 0.4       # how far the band stands off the skin
WORN_HEIGHT = 1.5  # along the hand
FINGER = 3.5       # a finger's width (the hand is 14 across four)
# from the hand's centre toward the fingertips (the hand box is 12 tall,
# so -2.5 is the lower middle, where fingers would be)
WORN_Y = -2.5
WORN_TEX = 32


def layout(region, mirror=False):
    x, y = region[0], region[1]
    return {"offset": {"x": x, "y": y},
            "mirror": {"x": mirror, "y": False}, "angle": 0}


def quat_z(angle):
    return brilliant.quat_axis((0, 0, 1), angle)


def box(nid, name, pos, rot, size_tex, size, faces):
    """A box whose texture size is size_tex texels and real size is
    size units (stretch makes up the difference)."""
    return {
        "id": str(nid), "name": name, "children": [],
        "position": {"x": pos[0], "y": pos[1], "z": pos[2]},
        "orientation": {"x": rot[0], "y": rot[1], "z": rot[2],
                        "w": rot[3]},
        "shape": {
            "type": "box", "offset": {"x": 0, "y": 0, "z": 0},
            "stretch": {"x": size[0] / size_tex[0],
                        "y": size[1] / size_tex[1],
                        "z": size[2] / size_tex[2]},
            "settings": {"size": {"x": size_tex[0], "y": size_tex[1],
                                  "z": size_tex[2]}},
            "textureLayout": faces,
            "unwrapMode": "custom", "visible": True,
            "doubleSided": False, "shadingMode": "reflective",
        },
    }


def band_nodes(style, nid=1000, radius_add=0.0, width=None, prefix="Band",
               outer_region="outer"):
    st = STYLES[style]
    r, t = st["r"] + radius_add, st["t"]
    n = SEGMENTS or st["n"]
    w = width if width is not None else st["w"]
    mid = r + t / 2
    # chord long enough to close the gaps on the outside
    chord = 2 * (r + t) * math.tan(math.pi / n) * 1.02
    nodes = []
    for k in range(n):
        phi = 2 * math.pi * k / n
        pos = (mid * math.cos(phi), mid * math.sin(phi), 0.0)
        # local +y points outward
        rot = quat_z(phi - math.pi / 2)
        faces = {"top": layout(METAL[outer_region]),
                 "bottom": layout(METAL["inner"]),
                 "front": layout(METAL["edge"]),
                 "back": layout(METAL["edge"]),
                 "left": layout(METAL["end"]),
                 "right": layout(METAL["end"])}
        tex = (SEG[0], 2, SEG[1]) if outer_region == "outer" else (4, 2, 2)
        nodes.append(box(nid + k, f"{prefix}_{k}", pos, rot, tex,
                         (chord, t, w), faces))
    return nodes


def item_model(style, gem):
    st = STYLES[style]
    nodes = band_nodes(style)
    if st["rims"]:
        # raised rims at both edges of a great band
        for side, z in (("Front", 1), ("Back", -1)):
            rim = band_nodes(style, 1100 + (0 if z > 0 else 50), 0.25,
                             0.7, f"Rim_{side}", "rim_outer")
            for node in rim:
                node["position"]["z"] = z * (st["w"] / 2 - 0.35)
            nodes += rim
    if gem:
        top = st["r"] + st["t"]
        cw, ch, cd = st["collar"]
        collar_faces = {f: layout(METAL["collar_side"])
                        for f in ("front", "back", "left", "right")}
        collar_faces["top"] = layout(METAL["collar_top"])
        collar_faces["bottom"] = layout(METAL["collar_top"])
        nodes.append(box(1200, "Collar", (0, top + ch / 2 - 0.2, 0),
                         (0, 0, 0, 1), (6, 3, 6), (cw, ch, cd),
                         collar_faces))
        g = st["gem"]
        # the brilliant's girdle sits just above the collar
        gem_y = top + ch - 0.2 + brilliant.PAVILION_H * g * 0.55
        nodes += brilliant.brilliant_nodes(g, (0, gem_y, 0), 1300)
        if st["prongs"]:
            reach = cw / 2 - 0.3
            for i, (sx, sz) in enumerate(((1, 1), (1, -1), (-1, 1),
                                          (-1, -1))):
                pf = {f: layout(METAL["prong"])
                      for f in ("front", "back", "left", "right", "top",
                                "bottom")}
                nodes.append(box(1250 + i, f"Prong_{i}",
                                 (sx * reach, gem_y + 0.3, sz * reach),
                                 (0, 0, 0, 1), (2, 4, 2), (0.6, 2.0, 0.6),
                                 pf))
    root = dict(brilliant.ROOT_SHAPE)
    lift = st["r"] + st["t"] + 1
    return {"nodes": [{"id": "1", "name": "R-Attachment", "children": nodes,
                       "position": {"x": 0, "y": lift, "z": 0},
                       "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                       "shape": root}],
            "lod": "auto"}


def metal_palette(assets, material):
    """Five shades, dark to bright, from the vanilla bar's icon."""
    with zipfile.ZipFile(assets) as z:
        data = z.read(f"Common/Icons/ItemsGenerated/Ingredient_Bar_{material}.png")
    im = Image.open(io.BytesIO(data)).convert("RGBA")
    px = [im.getpixel((x, y)) for y in range(im.height)
          for x in range(im.width) if im.getpixel((x, y))[3] > 220]
    px.sort(key=lambda p: 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2])
    return [tuple(px[int(len(px) * f)][:3])
            for f in (0.05, 0.3, 0.55, 0.8, 0.97)]


def paint_metal(im, pal, oy=0):
    dark, low, mid, high, shine = pal

    def put(x, y, c):
        im.putpixel((x, y + oy), c + (255,))

    x0, y0, w, h = METAL["outer"]
    for v in range(h):
        # across the band: a bright line down the middle
        d = abs(v + 0.5 - h / 2) / (h / 2)
        col = shine if d < 0.25 else high if d < 0.55 else mid if d < 0.85 else low
        for u in range(w):
            put(x0 + u, y0 + v, col)
    for name, col in (("inner", low), ("edge", mid), ("end", low),
                      ("collar_side", mid), ("prong", high),
                      ("rim_outer", high)):
        x0, y0, w, h = METAL[name]
        for v in range(h):
            for u in range(w):
                c = col
                if name == "collar_side" and v == 0:
                    c = shine
                if name == "edge" and v == 0:
                    c = high
                put(x0 + u, y0 + v, c)
    x0, y0, w, h = METAL["collar_top"]
    for v in range(h):
        for u in range(w):
            edge = min(u, v, w - 1 - u, h - 1 - v)
            put(x0 + u, y0 + v, high if edge == 0 else mid)


# The worn necklace's chain decals, in a free corner of every ring
# texture: the worn jewelry must use a texture the client has loaded,
# and it loads only textures that assets reference (the jewelry items
# reference these). One texel per model unit. (x, y, w, h)
# Nov 12: our own two decals replaced vanilla's necklace quads, whose
# shoulder quad lies 10 units inside the bare torso (it is made for
# the Overtops shirt layer), so no chain showed on the shoulders.
# - CHAIN_FRONT: upright on the chest's front face, a V from where the
#   chain comes over the shoulders down to the bail;
# - CHAIN_TOP: flat on top of the chest, a U round the base of the
#   neck; row 0 is the front edge, where it meets CHAIN_FRONT.
CHAIN_FRONT = (26, 44, 16, 7)
CHAIN_TOP = (43, 40, 20, 18)


def chain_line(points, a, b):
    """Texels on a line from a to b (both (u, v)), in order."""
    (u0, v0), (u1, v1) = a, b
    n = max(abs(u1 - u0), abs(v1 - v0), 1)
    for k in range(n + 1):
        p = (round(u0 + (u1 - u0) * k / n), round(v0 + (v1 - v0) * k / n))
        if p not in points:
            points.append(p)


def chain_paths():
    """{region: [(u, v), ...]} for the chain decals, left half drawn and
    mirrored."""
    front, top = [], []
    w, _ = CHAIN_FRONT[2:]
    chain_line(front, (1, 0), (7, 5))   # down to the bail
    tw = CHAIN_TOP[2]
    # front edge at x = -7, out round the neck's side, across its back
    chain_line(top, (3, 0), (1, 4))
    chain_line(top, (1, 4), (1, 14))
    chain_line(top, (1, 14), (3, 16))
    chain_line(top, (3, 16), (9, 16))
    return {CHAIN_FRONT: front + [(w - 1 - u, v) for u, v in front],
            CHAIN_TOP: top + [(tw - 1 - u, v) for u, v in top]}


def paint_chain_decal(im, assets, pal):
    dark, low, mid, high, shine = pal
    for (x0, y0, _, _), points in chain_paths().items():
        for k, (u, v) in enumerate(points):
            col = shine if k % 3 == 0 else high
            im.putpixel((x0 + u, y0 + v), col + (255,))


def item_texture(assets, material, gem):
    im = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    if gem:
        gem_tex = brilliant.texture(palette(assets, gem))
        im.paste(gem_tex.crop((0, 0, TEX, METAL_Y)), (0, 0))
    pal = metal_palette(assets, material)
    paint_metal(im, pal)
    paint_chain_decal(im, assets, pal)
    return im


def skeleton_chain(assets, side):
    """The player's bones from Belly down to the hand, from the game's
    Player.blockymodel, as shapeless nodes: [(name, position,
    orientation, offset, stretch)]."""
    with zipfile.ZipFile(assets) as z:
        player = json.loads(z.read("Common/Characters/Player.blockymodel"))

    def find(nodes, name, path):
        for n in nodes:
            if n["name"] == name:
                return path + [n]
            found = find(n.get("children") or [], name, path + [n])
            if found:
                return found
        return None

    chain = find(player["nodes"], f"{side}-Hand", [])
    start = [n["name"] for n in chain].index("Belly")
    return chain[start:]


PLAYER_BONES = {"Pelvis", "Belly", "Chest", "Head"} | {
    f"{s}-{b}" for s in "RL" for b in ("Shoulder", "Arm", "Forearm", "Hand")}


def as_pieces(model):
    """Marks every node named after a player bone "isPiece" and the file
    "format": "character", as vanilla cosmetics do. Without it the
    client doesn't attach a node to the player's bone of the same name:
    the jewelry only looked right in the rest pose, a lone top node
    stayed at the hip, and a worn ring hid the first-person arms
    (found Nov 11)."""
    def mark(nodes):
        for n in nodes:
            if n["name"] in PLAYER_BONES:
                n["shape"]["settings"] = dict(
                    n["shape"].get("settings") or {}, isPiece=True)
            mark(n.get("children") or [])
    mark(model["nodes"])
    model["format"] = "character"
    return model


def worn_model(side, assets):
    """A thin band around the index finger, in the hand's frame
    (children of the hand are placed from its box centre).

    The band hangs from the player's bone chain, Belly > Chest >
    Shoulder > Arm > Forearm > Hand, copied from the player model, each
    bone node marked isPiece (as_pieces) so it follows the arm. Tested
    Nov 11: the arms show in first person and the ring stays on the
    hand."""
    hx, hy, hz = HAND
    g, h = WORN_GAP, WORN_HEIGHT
    # the metal of the ring item textures (a coloured texture the
    # client has loaded; see CHAIN_FRONT)
    faces = {f: layout(METAL["outer"]) for f in ("front", "back", "left",
                                                 "right", "top", "bottom")}
    y = 5 + WORN_Y
    # a finger ring: the hand box has no fingers, so the band wraps a
    # finger-sized column at the hand's front outer corner (the index
    # finger), finger-wide both ways (Mark, Nov 10). Only the two
    # outside faces show; the rest would be inside the hand. The outer
    # side is -x on the right hand and +x on the left: the left arm's
    # mirrored bones don't mirror their children (in game, Nov 10, a
    # shared -x put the left ring by the thumb).
    f = FINGER
    out = -1 if side == "R" else 1
    parts = [
        ("Band_Front", (out * (hx / 2 - f / 2 + g / 2), y, hz / 2 + g / 2),
         (f + g, h, g)),
        ("Band_Out", (out * (hx / 2 + g / 2), y, hz / 2 - f / 2 + g / 2),
         (g, h, f + g)),
    ]
    children = []
    for i, (name, pos, size) in enumerate(parts):
        node = box(10 + i, name, pos, (0, 0, 0, 1), (4, 2, 4), size, faces)
        node["shape"]["offset"] = {"x": 0, "y": -5, "z": 0}
        children.append(node)
    node_id = 1
    for bone in reversed(skeleton_chain(assets, side)):
        shape = dict(brilliant.ROOT_SHAPE)
        shape["offset"] = bone["shape"].get("offset", shape["offset"])
        shape["stretch"] = bone["shape"].get("stretch", shape["stretch"])
        position = dict(bone["position"])
        if bone["name"] == "Belly":
            # top nodes are in model space, like the necklace's Belly
            # (the player's Pelvis is at y 51)
            position["y"] = 55
        children = [{"id": str(node_id), "name": bone["name"],
                     "children": children, "position": position,
                     "orientation": bone.get("orientation") or
                     {"x": 0, "y": 0, "z": 0, "w": 1},
                     "shape": shape}]
        node_id += 1
    return as_pieces({"nodes": children, "lod": "auto"})


def item_id(material, gem, style):
    parts = ["MT", "Ring", material]
    if gem:
        parts.append(gem)
    if style != "Regular":
        parts.append(style)
    return "_".join(parts)


def main():
    global SEGMENTS
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    ap.add_argument("--segments", type=int,
                    help="band segments (default: each style's own, 16)")
    ap.add_argument("--variant",
                    help="write only the item models, as "
                         "Ring_<Style>[_Set]_<variant>, and their icons "
                         "to --icons; leaves the real art alone")
    ap.add_argument("--icons", help="icon folder for --variant")
    a = ap.parse_args()
    SEGMENTS = a.segments
    suffix = f"_{a.variant}" if a.variant else ""
    icon_out = ICON_OUT
    if a.variant:
        if not a.icons:
            raise SystemExit("--variant needs --icons")
        from pathlib import Path
        icon_out = Path(a.icons)
    for folder in (RINGS_OUT, icon_out, WORN_OUT):
        folder.mkdir(parents=True, exist_ok=True)

    def write(path, data):
        path.write_text(json.dumps(data, indent=2) + "\n",
                        encoding="utf-8", newline="\n")

    def model_path(style, gem):
        return RINGS_OUT / (f"Ring_{style}" + ("_Set" if gem else "")
                            + suffix + ".blockymodel")

    for style in STYLES:
        write(model_path(style, None), item_model(style, None))
        write(model_path(style, "x"), item_model(style, "x"))
    # one scale for every ring icon, fitted to the largest ring (a set
    # Great), so a plain ring isn't blown up to fill its icon
    span = max(blocky_render.fit_span(str(model_path(style, gem)),
                                      str(RINGS_OUT / "Ring_Gold_Ruby.png"),
                                      yaw=35, pitch=24)
               for style in STYLES for gem in (None, "x"))
    icons = {}
    for material in MATERIALS:
        for gem in GEMS:
            name = f"Ring_{material}" + (f"_{gem}" if gem else "")
            tex = RINGS_OUT / f"{name}.png"
            if not a.variant:
                item_texture(a.assets, material, gem).save(tex)
            for style in STYLES:
                iid = item_id(material, gem, style)
                icons[iid] = blocky_render.render(
                    str(model_path(style, gem)), str(tex),
                    str(icon_out / f"{iid}{suffix}.png"),
                    margin=4, pitch=24, yaw=35, span=span)
    if not a.variant:
        write(WORN_OUT / "Ring_Worn_R.blockymodel", worn_model("R", a.assets))
        write(WORN_OUT / "Ring_Worn_L.blockymodel", worn_model("L", a.assets))
    print(f"Wrote {2 * len(STYLES)} ring models{suffix} and "
          f"{len(icons)} icons.")

    if a.preview:
        cell = 120
        cols = len(MATERIALS) * len(STYLES)
        sheet = Image.new("RGBA", (cell * cols, cell * len(GEMS)),
                          (55, 57, 66, 255))
        for gi, gem in enumerate(GEMS):
            col = 0
            for style in STYLES:
                for material in MATERIALS:
                    icon = icons[item_id(material, gem, style)]
                    sheet.alpha_composite(
                        icon.resize((112, 112), Image.NEAREST),
                        (col * cell + 4, gi * cell + 4))
                    col += 1
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

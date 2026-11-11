#!/usr/bin/env python3
"""Make the bracelet art: item models, icons and worn models.

Item models (held, dropped, icons), in
Common/Items/MagicTreasures/Bracelets/:
- Bracelet_<Style>.blockymodel: a chunky cuff, like the vanilla
  Bracers cosmetic: a short tube of box segments standing upright,
  with raised rims at top and bottom. Great is taller and thicker,
  with a ridge round the middle;
- Bracelet_<Style>_Set.blockymodel: the same with a brilliant gem
  set straight into the front of the cuff (on Great's ridge), its
  pavilion sunk in. (An octagonal bezel under the gem was dropped,
  Mark, Nov 10.)
They use the ring textures (Ring_<Material>[_<Gem>].png): the same
metal and gem regions, so no texture per bracelet.
History (Nov 10): first thin bangles, then a chain bracelet and a wide
ring-like cuff. Mark: they looked like rings, and worn they were too
thin; they should be chunky cuffs, like a bracer.

Worn models, Common/Cosmetics/MagicTreasures/:
- Bracelet_Worn_<Style>[_Set].blockymodel: a cuff round the bottom of
  the left forearm, resting on the hand (Regular 5 tall, Great 7, like
  the vanilla Bracers, which are 10), with rims, and the gem set into
  the outer side. It hangs from the player's bone chain, Belly >
  Chest > L-Shoulder > L-Arm > L-Forearm, like the worn rings: the
  client draws a plugin attachment only from the whole chain (see
  make_ring_art.worn_model). It is worn with the ring item texture of
  its metal and gem. Every body type uses the same player model
  (BodyCharacteristics.json), so one size fits all.

Usage (from the repo root):
    python tools/make_bracelet_art.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import json
import math

from PIL import Image

import blocky_render
import make_brilliant_gem as brilliant
import make_ring_art as ring
from make_gem_art import COMMON
from make_necklace_art import quat_x
from make_ring_art import METAL, RINGS_OUT, box, layout

BRACELET_OUT = COMMON / "Items" / "MagicTreasures" / "Bracelets"
ICON_OUT = COMMON / "Icons" / "Items" / "MagicTreasures"
WORN_OUT = COMMON / "Cosmetics" / "MagicTreasures"

MATERIALS = ["Copper", "Silver", "Gold"]
GEMS = [None, "Topaz", "Ruby", "Sapphire"]

# the item cuff, in make_ring_art.band_nodes' terms (inner radius,
# thickness, height along its axis, segments); the gem scale (item,
# then worn); the worn cuff's height and thickness
STYLES = {
    "Regular": {"r": 4.0, "t": 1.2, "w": 6.0, "n": 12, "ridge": False, "gem": 0.36, "worn_gem": 0.30,
                "worn_h": 5.0, "worn_t": 0.8},
    "Great": {"r": 4.2, "t": 1.5, "w": 8.0, "n": 12, "ridge": True, "gem": 0.44, "worn_gem": 0.36,
              "worn_h": 7.0, "worn_t": 1.0},
}
# the worn band, in the forearm's frame: children of the forearm are
# placed from its box centre; the box is 8 x 16 x 12, and the hand box
# overlaps its bottom by 1
FOREARM = (8.0, 16.0, 12.0)
WORN_GAP = 0.3
WORN_SIDE = "L"


def band_nodes(style, nid=1000, radius_add=0.0, width=None, prefix="Band",
               region="outer"):
    # make_ring_art builds bands from its own STYLES table
    key = f"Bracelet_{style}"
    ring.STYLES[key] = STYLES[style]
    return ring.band_nodes(key, nid, radius_add, width, prefix, region)


def set_gem(scale, center, rot, nid=3500):
    """A brilliant set straight into a surface: its table faces the
    surface's outward normal (+z before rot), with the girdle just
    above the surface and the pavilion sunk in."""
    inner = {"id": str(nid + 98), "name": "Gem_Facing", "children":
             brilliant.brilliant_nodes(scale, (0, 0, 0), nid),
             "position": {"x": 0, "y": 0, "z": 0},
             # the brilliant's table faces +y; turn it to +z
             "orientation": dict(zip("xyzw", quat_x(math.pi / 2))),
             "shape": dict(brilliant.ROOT_SHAPE)}
    return {"id": str(nid + 99), "name": "Gem", "children": [inner],
            "position": {"x": center[0], "y": center[1], "z": center[2]},
            "orientation": dict(zip("xyzw", rot)),
            "shape": dict(brilliant.ROOT_SHAPE)}


def item_model(style, gem):
    st = STYLES[style]
    w = st["w"]
    nodes = band_nodes(style)
    rims = [("Top", w / 2 - 0.5), ("Bottom", -(w / 2 - 0.5))]
    if st["ridge"]:
        rims.append(("Ridge", 0.0))
    for k, (name, z) in enumerate(rims):
        rim = band_nodes(style, 1100 + 50 * k, 0.35, 1.0, f"Rim_{name}",
                         "rim_outer")
        for node in rim:
            node["position"]["z"] = z
        nodes += rim
    # the band's axis is z; stand it up (axis y) so it reads as a cuff
    cuff = {"id": "2", "name": "Cuff", "children": nodes,
            "position": {"x": 0, "y": 0, "z": 0},
            "orientation": dict(zip("xyzw", quat_x(math.pi / 2))),
            "shape": dict(brilliant.ROOT_SHAPE)}
    children = [cuff]
    if gem:
        # on the front, facing +z, on the ridge on Great
        surface = st["r"] + st["t"] + (0.35 if st["ridge"] else 0)
        girdle = surface + brilliant.GIRDLE_H * st["gem"]
        children.append(set_gem(st["gem"], (0, 0, girdle), (0, 0, 0, 1)))
    return {"nodes": [{"id": "1", "name": "R-Attachment",
                       "children": children,
                       "position": {"x": 0, "y": w / 2 + 1, "z": 0},
                       "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                       "shape": dict(brilliant.ROOT_SHAPE)}],
            "lod": "auto"}


def ring_parts(prefix, y, hx, hz, t, h):
    """Four boxes round a rectangle whose inside is 2hx x 2hz."""
    return [
        (f"{prefix}_PX", (hx + t / 2, y, 0), (t, h, 2 * (hz + t))),
        (f"{prefix}_NX", (-hx - t / 2, y, 0), (t, h, 2 * (hz + t))),
        (f"{prefix}_PZ", (0, y, hz + t / 2), (2 * hx, h, t)),
        (f"{prefix}_NZ", (0, y, -hz - t / 2), (2 * hx, h, t)),
    ]


def worn_model(assets, style, gem, side=WORN_SIDE):
    st = STYLES[style]
    fx, fy, fz = FOREARM
    g, h, t = WORN_GAP, st["worn_h"], st["worn_t"]
    # resting on the hand: the bottom of the cuff is where the hand box
    # starts (1 above the forearm box's bottom)
    y = -fy / 2 + 1 + h / 2
    hx, hz = fx / 2 + g, fz / 2 + g      # the cuff's inside
    # 8 texels tall: the metal region's shading runs across the cuff,
    # dark at the edges and bright in the middle
    faces = {f: layout(METAL["outer"]) for f in ("front", "back", "left",
                                                 "right", "top", "bottom")}
    rim_faces = {f: layout(METAL["rim_outer"]) for f in faces}
    children = [box(10 + i, name, pos, (0, 0, 0, 1), (4, 8, 4), size, faces)
                for i, (name, pos, size) in
                enumerate(ring_parts("Cuff", y, hx, hz, t, h))]
    rims = [("Rim_Top", y + h / 2 - 0.4), ("Rim_Bottom", y - h / 2 + 0.4)]
    if st["ridge"]:
        rims.append(("Ridge", y))
    for k, (name, ry) in enumerate(rims):
        for i, (pname, pos, size) in enumerate(
                ring_parts(name, ry, hx, hz, t + 0.35, 0.8)):
            children.append(box(20 + 4 * k + i, pname, pos, (0, 0, 0, 1),
                                (4, 2, 4), size, rim_faces))
    if gem:
        # on the outer side: -x on the right arm, +x on the left (the
        # left arm's mirrored bones don't mirror their children)
        out = -1 if side == "R" else 1
        turn = brilliant.quat_axis((0, 1, 0), out * math.pi / 2)
        # on Great's ridge, which is at the cuff's middle
        surface = hx + t + (0.35 if st["ridge"] else 0)
        girdle = surface + brilliant.GIRDLE_H * st["worn_gem"]
        children.append(set_gem(st["worn_gem"], (out * girdle, y, 0), turn))
    node_id = 1
    for bone in reversed(ring.skeleton_chain(assets, side)[:-1]):
        shape = dict(brilliant.ROOT_SHAPE)
        shape["offset"] = bone["shape"].get("offset", shape["offset"])
        shape["stretch"] = bone["shape"].get("stretch", shape["stretch"])
        position = dict(bone["position"])
        if bone["name"] == "Belly":
            position["y"] = 55   # top nodes are in model space
        children = [{"id": str(node_id), "name": bone["name"],
                     "children": children, "position": position,
                     "orientation": bone.get("orientation") or
                     {"x": 0, "y": 0, "z": 0, "w": 1},
                     "shape": shape}]
        node_id += 1
    return {"nodes": children, "lod": "auto"}


def item_id(material, gem, style):
    parts = ["MT", "Bracelet", material]
    if gem:
        parts.append(gem)
    if style != "Regular":
        parts.append(style)
    return "_".join(parts)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    for folder in (BRACELET_OUT, ICON_OUT, WORN_OUT):
        folder.mkdir(parents=True, exist_ok=True)

    def write(path, data):
        path.write_text(json.dumps(data, indent=2) + "\n",
                        encoding="utf-8", newline="\n")

    def model_path(style, gem):
        return BRACELET_OUT / (f"Bracelet_{style}" + ("_Set" if gem else "")
                               + ".blockymodel")

    for style in STYLES:
        for gem in (None, "x"):
            write(model_path(style, gem), item_model(style, gem))
            write(WORN_OUT / (f"Bracelet_Worn_{style}"
                              + ("_Set" if gem else "") + ".blockymodel"),
                  worn_model(a.assets, style, gem))
    # one scale for every bracelet icon, fitted to the largest
    span = max(blocky_render.fit_span(str(model_path(style, gem)),
                                      str(RINGS_OUT / "Ring_Gold_Ruby.png"),
                                      yaw=35, pitch=24)
               for style in STYLES for gem in (None, "x"))
    icons = {}
    for material in MATERIALS:
        for gem in GEMS:
            tex = RINGS_OUT / (f"Ring_{material}"
                               + (f"_{gem}" if gem else "") + ".png")
            for style in STYLES:
                iid = item_id(material, gem, style)
                icons[iid] = blocky_render.render(
                    str(model_path(style, gem)), str(tex),
                    str(ICON_OUT / f"{iid}.png"),
                    margin=4, pitch=24, yaw=35, span=span)
    print(f"Wrote {4 * len(STYLES)} bracelet models and {len(icons)} icons.")

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

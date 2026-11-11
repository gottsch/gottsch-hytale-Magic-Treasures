#!/usr/bin/env python3
"""Make the necklace art: item models, icons and worn models.

Item models (held, dropped, icons), in
Common/Items/MagicTreasures/Necklaces/:
- Necklace_<Style>.blockymodel: a loop of chain links (alternate
  links turned 90 degrees, like a real chain) with a medallion at the
  bottom: an octagonal coin with a raised boss;
- Necklace_<Style>_Set.blockymodel: the same with a collar holding a
  brilliant gem facing out. Great adds four prongs.
They use the ring textures (Ring_<Material>[_<Gem>].png): the same
metal and gem regions, so no texture per necklace.

Worn models, Common/Cosmetics/MagicTreasures/:
- Necklace_Worn_<Style>[_Set].blockymodel: the chain is a decal on the
  chest, on the same two quads as the vanilla necklace
  (Cosmetics/Overtops/Overtops_Necklace), so it lies on the body and on
  armor. Our own chain texture follows the vanilla chain's path. Under
  the bail hangs a 3D pendant: the medallion, or the collar for a gem.
  The gem is part of the same model. It is worn with the ring item
  texture of its metal and gem (Ring_<Material>[_<Gem>].png, which has
  the chain decal at make_ring_art.DECAL): the client refuses an
  attachment texture that no asset references, so a worn-only
  greyscale texture never showed.

Usage (from the repo root):
    python tools/make_necklace_art.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import io
import json
import math
import zipfile

from PIL import Image

import blocky_render
import make_brilliant_gem as brilliant
from make_gem_art import COMMON
from make_ring_art import DECAL, METAL, RINGS_OUT, as_pieces, box, layout

NECK_OUT = COMMON / "Items" / "MagicTreasures" / "Necklaces"
ICON_OUT = COMMON / "Icons" / "Items" / "MagicTreasures"
WORN_OUT = COMMON / "Cosmetics" / "MagicTreasures"

MATERIALS = ["Copper", "Silver", "Gold"]
GEMS = [None, "Topaz", "Ruby", "Sapphire"]

STYLES = {
    # chain loop radii, link count and thickness; medallion size;
    # gem scale (item model, then worn). A collar is sized from its gem
    # (collar_for), so a metal rim shows around the gem.
    "Regular": {"rx": 4.5, "ry": 6.5, "links": 22, "link": 0.6,
                "coin": 5.0, "gem": 0.32, "prongs": False,
                "worn_coin": 4.4, "worn_gem": 0.26},
    "Great": {"rx": 5.0, "ry": 7.0, "links": 22, "link": 0.8,
              "coin": 6.4, "gem": 0.40, "prongs": True,
              "worn_coin": 5.6, "worn_gem": 0.32},
}


def collar_for(gem_scale):
    """A collar a little wider than the gem's girdle."""
    return 2 * brilliant.R_GIRDLE * gem_scale * 1.15


# the vanilla necklace's pendant, in the chest's frame (the chest box
# is 28 x 22 x 20, centred on the Chest node's pivot plus offset): the
# chain's V meets at about y 4.2, z 11.3, and the front quad tilts out
# about 9 degrees toward the bottom
# Lifted off the body (Nov 10): the vanilla necklace replaces the shirt,
# but ours is worn over one, and at the vanilla depth a coat covered
# the chain and pendant (only the gem, which stands further out,
# showed). So the decal and pendant sit this much further out and up.
OUT_Z = 1.5
UP_Y = 0.8
BAIL = (0.0, 4.0 + UP_Y, 11.3 + OUT_Z)
TILT = math.radians(9)
# the pendant's regions in the ring textures (the worn necklace uses the
# ring item texture of its metal and gem; see make_ring_art.DECAL)
WORN_REGIONS = {
    "coin_face": METAL["collar_top"], "coin_side": METAL["collar_side"],
    "boss": METAL["prong"], "collar_face": METAL["collar_top"],
    "collar_side": METAL["collar_side"], "prong": METAL["prong"],
    "bail": METAL["prong"],
}


def quat_x(angle):
    return brilliant.quat_axis((1, 0, 0), angle)


def faces(region_map, regions):
    return {face: layout(regions[name]) for face, name in region_map.items()}


def all_faces(name, regions):
    return faces({f: name for f in ("front", "back", "left", "right", "top",
                                    "bottom")}, regions)


def chain_nodes(st, nid=2000):
    nodes = []
    n, rx, ry, t = st["links"], st["rx"], st["ry"], st["link"]
    pts = [(rx * math.sin(2 * math.pi * k / n), ry * math.cos(2 * math.pi * k / n))
           for k in range(n)]
    for k in range(n):
        (x0, y0), (x1, y1) = pts[k], pts[(k + 1) % n]
        length = math.hypot(x1 - x0, y1 - y0) * 1.15
        ang = math.atan2(y1 - y0, x1 - x0)
        rot = brilliant.quat_axis((0, 0, 1), ang)
        if k % 2:
            # turn every other link on its long axis, like a chain
            rot = blocky_render.quat_mul(rot, quat_x(math.pi / 2))
        region = "prong" if k % 2 == 0 else "edge"
        nodes.append(box(nid + k, f"Link_{k}", ((x0 + x1) / 2, (y0 + y1) / 2, 0),
                         rot, (2, 1, 2), (length, t * 0.6, t * 1.4),
                         all_faces(region, METAL)))
    return nodes


def pendant_nodes(st, gem, center, rot, regions, coin, collar, gem_scale,
                  nid=3000, with_gem=True):
    """The pendant, facing +z before rot: a medallion (octagonal coin
    and boss), or a collar holding a brilliant. Returns a group node."""
    kids = []
    if not gem:
        for i, a in enumerate((0, math.pi / 4)):
            kids.append(box(nid + i, f"Coin_{i}", (0, 0, 0),
                            brilliant.quat_axis((0, 0, 1), a), (6, 6, 2),
                            (coin, coin, coin * 0.22),
                            # sides are 2 x 6 texels: they take a corner
                            # of the face region
                            faces({"front": "coin_face", "back": "coin_face",
                                   "left": "coin_face", "right": "coin_face",
                                   "top": "coin_side", "bottom": "coin_side"},
                                  regions)))
        kids.append(box(nid + 5, "Boss", (0, 0, coin * 0.15), (0, 0, 0, 1),
                        (2, 2, 2), (coin * 0.35, coin * 0.35, coin * 0.2),
                        all_faces("boss", regions)))
    else:
        # an octagonal bezel: two squares turned 45 degrees apart
        side = collar * 0.92
        for i, ang in enumerate((0, math.pi / 4)):
            kids.append(box(nid + i, f"Collar_{i}", (0, 0, 0),
                            brilliant.quat_axis((0, 0, 1), ang), (6, 6, 2),
                            (side, side, collar * 0.3),
                            faces({"front": "collar_face", "back": "collar_face",
                                   "left": "collar_face", "right": "collar_face",
                                   "top": "collar_side", "bottom": "collar_side"},
                                  regions)))
        if st["prongs"]:
            # short claws over the gem's girdle, on the diagonals
            reach = brilliant.R_GIRDLE * gem_scale * 0.92
            for i in range(4):
                ang = math.pi / 4 + i * math.pi / 2
                kids.append(box(nid + 10 + i, f"Prong_{i}",
                                (reach * math.cos(ang), reach * math.sin(ang),
                                 collar * 0.3),
                                brilliant.quat_axis((0, 0, 1), ang), (2, 2, 2),
                                (0.7, 0.5, 1.2),
                                all_faces("prong", regions)))
        if with_gem:
            kids += gem_group(collar, gem_scale)
    q = {"x": rot[0], "y": rot[1], "z": rot[2], "w": rot[3]}
    return {"id": str(nid + 99), "name": "Pendant", "children": kids,
            "position": {"x": center[0], "y": center[1], "z": center[2]},
            "orientation": q, "shape": dict(brilliant.ROOT_SHAPE)}


def gem_group(collar, gem_scale, nid=3500):
    """The brilliant, table facing +z, just in front of the collar."""
    girdle_z = collar * 0.175 + brilliant.PAVILION_H * gem_scale * 0.5
    nodes = brilliant.brilliant_nodes(gem_scale, (0, 0, 0), nid)
    return [{"id": str(nid + 99), "name": "Gem", "children": nodes,
             "position": {"x": 0, "y": 0, "z": girdle_z},
             "orientation": dict(zip("xyzw", quat_x(math.pi / 2))),
             "shape": dict(brilliant.ROOT_SHAPE)}]


def item_model(style, gem):
    st = STYLES[style]
    nodes = chain_nodes(st)
    bail_y = -st["ry"] - 0.4
    nodes.append(box(2900, "Bail", (0, bail_y, 0), (0, 0, 0, 1), (2, 2, 2),
                     (0.9, 1.2, 0.9), all_faces("prong", METAL)))
    size = collar_for(st["gem"]) if gem else st["coin"]
    center = (0, bail_y - 0.3 - size * 0.5, 0)
    regions = {"coin_face": METAL["collar_top"], "coin_side": METAL["collar_side"],
               "boss": METAL["prong"], "collar_face": METAL["collar_top"],
               "collar_side": METAL["collar_side"], "prong": METAL["prong"]}
    nodes.append(pendant_nodes(st, gem, center, (0, 0, 0, 1), regions,
                               st["coin"], collar_for(st["gem"]), st["gem"]))
    return {"nodes": [{"id": "1", "name": "R-Attachment", "children": nodes,
                       "position": {"x": 0, "y": 5, "z": 0},
                       "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                       "shape": dict(brilliant.ROOT_SHAPE)}],
            "lod": "auto"}


def vanilla_necklace(assets):
    with zipfile.ZipFile(assets) as z:
        model = json.loads(z.read(
            "Common/Cosmetics/Overtops/Overtops_Necklace.blockymodel"))
        tex = Image.open(io.BytesIO(z.read(
            "Common/Cosmetics/Overtops/Overtops_Necklace_Textures/"
            "Necklace_1_Greyscale.png"))).convert("RGBA")
    return model, tex


def worn_model(vanilla_model, style, gem):
    """The worn necklace, one attachment: the chain decal on the vanilla
    necklace's chest quads (pointed at DECAL in the ring texture), the
    bail, and the pendant with its gem."""
    st = STYLES[style]
    regions = WORN_REGIONS
    belly = json.loads(json.dumps(vanilla_model["nodes"][0]))
    chest = belly["children"][0]

    def retexture(node):
        for f in ((node.get("shape") or {}).get("textureLayout") or {}).values():
            f["offset"]["x"] = DECAL[0]
            f["offset"]["y"] = DECAL[1]
        for c in node.get("children") or []:
            retexture(c)

    for decal in chest["children"]:   # the vanilla chain quads
        decal["position"]["y"] += UP_Y
        decal["position"]["z"] += OUT_Z
        retexture(decal)
    bail = box(4000, "Bail", BAIL, quat_x(-TILT), (2, 2, 2), (0.9, 1.2, 0.9),
               all_faces("bail", regions))
    collar = collar_for(st["worn_gem"])
    size = collar if gem else st["worn_coin"]
    drop = 0.3 + size * 0.5
    center = (0, BAIL[1] - drop * math.cos(TILT), BAIL[2] + drop * math.sin(TILT))
    pendant = pendant_nodes(st, gem, center, quat_x(-TILT), regions,
                            st["worn_coin"], collar, st["worn_gem"],
                            with_gem=True)
    chest["children"] = chest["children"] + [bail, pendant]
    return as_pieces({"nodes": [belly], "lod": "auto"})


def item_id(material, gem, style):
    parts = ["MT", "Necklace", material]
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
    for folder in (NECK_OUT, ICON_OUT, WORN_OUT):
        folder.mkdir(parents=True, exist_ok=True)

    def write(path, data):
        path.write_text(json.dumps(data, indent=2) + "\n",
                        encoding="utf-8", newline="\n")

    def model_path(style, gem):
        return NECK_OUT / (f"Necklace_{style}" + ("_Set" if gem else "")
                           + ".blockymodel")

    for style in STYLES:
        write(model_path(style, None), item_model(style, None))
        write(model_path(style, "x"), item_model(style, "x"))
    span = max(blocky_render.fit_span(str(model_path(style, gem)),
                                      str(RINGS_OUT / "Ring_Gold_Ruby.png"),
                                      yaw=30, pitch=15)
               for style in STYLES for gem in (None, "x"))
    icons = {}
    for material in MATERIALS:
        for gem in GEMS:
            tex = RINGS_OUT / (f"Ring_{material}" + (f"_{gem}" if gem else "")
                               + ".png")
            for style in STYLES:
                iid = item_id(material, gem, style)
                icons[iid] = blocky_render.render(
                    str(model_path(style, gem)), str(tex),
                    str(ICON_OUT / f"{iid}.png"), margin=4, pitch=15, yaw=30,
                    span=span)

    vmodel, _ = vanilla_necklace(a.assets)
    for style in STYLES:
        for gem in (None, "x"):
            name = f"Necklace_Worn_{style}" + ("_Set" if gem else "")
            write(WORN_OUT / f"{name}.blockymodel", worn_model(vmodel, style, gem))
    print(f"Wrote {2 * len(STYLES)} necklace models, {len(icons)} icons and "
          "the worn necklaces.")

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
                    sheet.alpha_composite(icon.resize((112, 112), Image.NEAREST),
                                          (col * cell + 4, gi * cell + 4))
                    col += 1
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

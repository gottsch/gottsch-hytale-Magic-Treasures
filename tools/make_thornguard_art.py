#!/usr/bin/env python3
"""Make the art for Thornguard, the named iron bracer (v1.2, Nov 18).

It is the Great set bracer of make_bracer_art (a cuff with rims and a
ridge, a ruby set into the ridge) with a ring of thorns on the top and
bottom rims: tapered spikes standing out from the cuff, each a base
box and a thinner tip box, in the prong colour.

It writes:

- Rings/Ring_Iron.png and Rings/Ring_Iron_Ruby.png: the iron textures
  (make_ring_art.item_texture, iron from vanilla's iron bar). Iron had
  no art before; the worn bracer uses Ring_Iron_Ruby.png, and worn
  iron pieces now show (JewelryWear.ART_MATERIALS);
- Bracers/Bracer_Thornguard.blockymodel: the held and dropped model;
- Icons/Items/MagicTreasures/MT_Bracer_Thornguard.png: its icon, at
  the same scale as the other bracer icons;
- Cosmetics/MagicTreasures/Bracer_Worn_Thornguard.blockymodel: the worn
  model (JewelryWear.NAMED_WORN), the Great set cuff with shorter
  thorns on the outer half of the wrist only, so they don't stick into
  the body or far through gloves and sleeves (Nov 18).

Usage (from the repo root):
    python tools/make_thornguard_art.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import copy
import json
import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import blocky_render  # noqa: E402
import make_bracer_art as bracer  # noqa: E402
import make_ring_art as ring  # noqa: E402

ICON_ID = "MT_Bracer_Thornguard"
MODEL = bracer.BRACER_OUT / "Bracer_Thornguard.blockymodel"
TEXTURE = ring.RINGS_OUT / "Ring_Iron_Ruby.png"

# thorns per rim, and each thorn's steps from base to tip (width,
# length) in units: three steps, so it tapers to a point (Nov 18: two
# short steps read as gear teeth)
THORNS = 8
STEPS = [(1.0, 1.1), (0.6, 1.1), (0.3, 0.8)]
# worn: about two-thirds as long (half was too small to see, Nov 18),
# on the rims of the outer side (+x on the left arm) and the outer half
# of the front and back faces
WORN_MODEL = bracer.WORN_OUT / "Bracer_Worn_Thornguard.blockymodel"
WORN_STEPS = [(1.0, 0.8), (0.6, 0.7), (0.3, 0.5)]


def thorn_nodes(st, z, offset, nid):
    """One rim's thorns, in the band's frame (axis z): radial spikes,
    local +y pointing out, as make_ring_art.band_nodes turns its
    segments."""
    nodes = []
    # out from the rim's outer face (the rims stand 0.35 proud)
    surface = st["r"] + st["t"] + 0.35
    dark = ring.layout(ring.METAL["inner"])
    faces = {f: dark for f in ("front", "back", "left", "right", "top",
                                "bottom")}
    for k in range(THORNS):
        phi = 2 * math.pi * (k + offset) / THORNS
        rot = ring.quat_z(phi - math.pi / 2)
        out = (math.cos(phi), math.sin(phi))
        start = -0.1
        for j, (w, length) in enumerate(STEPS):
            mid = surface + start + length / 2
            nodes.append(ring.box(nid + len(STEPS) * k + j, f"Thorn_{nid}_{k}_{j}",
                                  (out[0] * mid, out[1] * mid, z), rot,
                                  (2, 4, 2), (w, length, w), faces))
            start += length
    return nodes


def worn_thorns(st, y, hx, hz, t):
    """The worn cuff's thorns (make_bracer_art.worn_model's extra): on
    the top rim three on the outer face and one on each of the front and
    back faces near it; on the bottom rim two and one, between them, so
    the rows don't line up. Each points straight out of its face."""
    dark = ring.layout(ring.METAL["inner"])
    faces = {f: dark for f in ("front", "back", "left", "right", "top",
                                "bottom")}
    # the rims stand 0.35 proud of the cuff
    sx, sz = hx + t + 0.35, hz + t + 0.35
    rh = st["worn_h"] / 2 - 0.4
    rows = [(y + rh, (-3.5, 0.0, 3.5), 2.5), (y - rh, (-1.75, 1.75), 1.0)]
    nodes, nid = [], 6000
    for ry, outer, side_x in rows:
        # (base point, direction) per thorn: +x, then +z and -z
        spots = [((sx, ry, z), (1, 0, 0)) for z in outer]
        spots += [((side_x, ry, d * sz), (0, 0, d)) for d in (1, -1)]
        for (bx, by, bz), (dx, dy, dz) in spots:
            start = -0.1
            for j, (w, length) in enumerate(WORN_STEPS):
                mid = start + length / 2
                size = (length if dx else w, w, length if dz else w)
                tex = (4 if dx else 2, 2, 4 if dz else 2)
                nodes.append(ring.box(nid, f"Thorn_{nid}",
                                      (bx + dx * mid, by, bz + dz * mid),
                                      (0, 0, 0, 1), tex, size, faces))
                nid += 1
                start += length
    return nodes


def model():
    st = bracer.STYLES["Great"]
    m = copy.deepcopy(bracer.item_model("Great", "x"))
    cuff = next(n for n in m["nodes"][0]["children"] if n["name"] == "Cuff")
    w = st["w"]
    # the two rims, the bottom row turned half a step so the rows
    # don't line up
    cuff["children"] += thorn_nodes(st, w / 2 - 0.5, 0.0, 5000)
    cuff["children"] += thorn_nodes(st, -(w / 2 - 0.5), 0.5, 5100)
    return m


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    ring.item_texture(a.assets, "Iron", None).save(ring.RINGS_OUT / "Ring_Iron.png")
    ring.item_texture(a.assets, "Iron", "Ruby").save(TEXTURE)
    MODEL.write_text(json.dumps(model(), indent=2) + "\n",
                     encoding="utf-8", newline="\n")
    WORN_MODEL.write_text(json.dumps(bracer.worn_model(a.assets, "Great", "x",
                                                       extra=worn_thorns),
                                     indent=2) + "\n",
                          encoding="utf-8", newline="\n")
    # the bracer icons' scale (make_bracer_art.main), so it matches them
    span = bracer.ICON_SHRINK * max(
        blocky_render.fit_span(str(bracer.BRACER_OUT / (f"Bracer_{s}" + ("_Set" if g else "")
                                                        + ".blockymodel")),
                               str(ring.RINGS_OUT / "Ring_Gold_Ruby.png"),
                               yaw=35, pitch=24)
        for s in bracer.STYLES for g in (None, "x"))
    icon = blocky_render.render(str(MODEL), str(TEXTURE),
                                str(bracer.ICON_OUT / f"{ICON_ID}.png"),
                                margin=4, pitch=24, yaw=35, span=span)
    print(f"Wrote {MODEL.name}, {WORN_MODEL.name}, Ring_Iron(.png, _Ruby.png) and {ICON_ID}.png")
    if a.preview:
        gold = Image.open(bracer.ICON_OUT / "MT_Bracer_Gold_Ruby_Great.png")
        sheet = Image.new("RGBA", (2 * 256 + 12, 260), (55, 57, 66, 255))
        sheet.alpha_composite(gold.resize((256, 256), Image.NEAREST), (2, 2))
        sheet.alpha_composite(icon.resize((256, 256), Image.NEAREST), (266, 2))
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

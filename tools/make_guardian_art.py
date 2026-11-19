#!/usr/bin/env python3
"""Make the art for the Guardian's Vambrace, the named silver bracer
(v1.3, Nov 19).

Plate armour rather than a cuff: three overlapping plates (lames) round
the forearm, each a little wider than the one below, so they step out
toward the elbow, with a rim at each end, and a square boss on the front
with a sapphire set into it and a small one at each of its corners,
and a ring of small sapphires round the elbow rim. It uses the silver and sapphire texture
(Ring_Silver_Sapphire.png) as the other silver sapphire bracers do.

It writes:

- Bracers/Bracer_Guardian.blockymodel: the held and dropped model;
- Icons/Items/MagicTreasures/MT_Bracer_Guardian.png: its icon, at the
  same scale as the other bracer icons;
- Cosmetics/MagicTreasures/Bracer_Worn_Guardian.blockymodel: the worn
  model (JewelryWear.NAMED_WORN), the Great cuff made 11 tall instead
  of 7, over most of the forearm, with three plates stepping out toward
  the elbow, a flared elbow rim ringed with small sapphires, and the
  boss with its gems on the outer side of the wrist.

Usage (from the repo root):
    python tools/make_guardian_art.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import blocky_render  # noqa: E402
import make_brilliant_gem as brilliant  # noqa: E402
import make_bracer_art as bracer  # noqa: E402
import make_ring_art as ring  # noqa: E402
from make_necklace_art import quat_x  # noqa: E402
import math  # noqa: E402

ICON_ID = "MT_Bracer_Guardian"
MODEL = bracer.BRACER_OUT / "Bracer_Guardian.blockymodel"
TEXTURE = ring.RINGS_OUT / "Ring_Silver_Sapphire.png"
WORN_MODEL = bracer.WORN_OUT / "Bracer_Worn_Guardian.blockymodel"

# the item's plates, elbow end first: (centre along the cuff's axis,
# how far each stands out past the plain cuff, height); they overlap by
# 0.6. The cuff turns its axis z to -y, so -z is the top.
PLATES = [(-2.6, 1.0, 3.2), (0.0, 0.5, 3.2), (2.6, 0.0, 3.2)]
# the boss: its side, and how far it stands out of the middle plate
BOSS = 6.4
BOSS_T = 0.7
# the sapphire, smaller than a Great bracer's so the boss shows round it
GEM = 0.30
# small sapphires (Nov 19, Mark: more gems): one at each corner of the
# boss, and a ring of them round the flared rim at the elbow end
CORNER_GEM = 0.11
RIM_GEM = 0.08
RIM_GEMS = 12
SPINE_W = 1.4
# the worn cuff (Nov 19, Mark: 11 units): taller than a Great bracer's
# 7, covering most of the 16-unit forearm from the wrist, as plate does
WORN_H = 11.0
# the worn boss's side, its gem and the small corner gems
WORN_BOSS = 4.0
WORN_GEM = 0.22
WORN_CORNER_GEM = 0.07
# the worn plates between the rims, wrist to elbow (+y): (offset from
# the middle as a share of half the cuff's height, how far they stand
# out, height); they step out toward the elbow, as on the item
WORN_PLATES = [(-0.62, 0.05, 3.4), (0.0, 0.15, 3.4), (0.62, 0.3, 3.4)]
# the flared rim at the elbow end, how far it stands out past the cuff,
# and its ring of small sapphires, per long (x) and short (z) side
WORN_FLARE = 0.7
WORN_RIM_GEM = 0.05
WORN_RIM_GEMS = (3, 2)


def faces(region):
    tex = ring.layout(ring.METAL[region])
    return {f: tex for f in ("front", "back", "left", "right", "top",
                             "bottom")}


def model():
    st = bracer.STYLES["Great"]
    w = st["w"]
    nodes = []
    for k, (z, out, h) in enumerate(PLATES):
        plate = bracer.band_nodes("Great", 1000 + 100 * k, out, h,
                                  f"Plate_{k}")
        for node in plate:
            node["position"]["z"] = z
        nodes += plate
    # a rim at each end, standing out of the end plates
    for k, (name, z, out) in enumerate((("Top", -(w / 2 - 0.5), 1.35),
                                        ("Bottom", w / 2 - 0.5, 0.35))):
        rim = bracer.band_nodes("Great", 1400 + 50 * k, out, 1.0,
                                f"Rim_{name}", "rim_outer")
        for node in rim:
            node["position"]["z"] = z + (ring.PROUD if z > 0 else -ring.PROUD)
        nodes += rim
    cuff = {"id": "2", "name": "Cuff", "children": nodes,
            "position": {"x": 0, "y": 0, "z": 0},
            "orientation": dict(zip("xyzw", quat_x(math.pi / 2))),
            "shape": dict(brilliant.ROOT_SHAPE)}
    # the boss on the front of the middle plate (+z), the gem set in it
    surface = st["r"] + st["t"] + PLATES[1][1]
    boss = ring.box(1600, "Boss", (0, 0, surface + BOSS_T / 2 - 0.1),
                    (0, 0, 0, 1), (4, 4, 2), (BOSS, BOSS, BOSS_T),
                    faces("rim_outer"))
    top = surface + BOSS_T - 0.1
    parts = [cuff, boss]
    gems = []
    corner = BOSS / 2 - 0.8
    for k, (cx, cy) in enumerate(((1, 1), (1, -1), (-1, 1), (-1, -1))):
        gems.append(bracer.set_gem(CORNER_GEM, (cx * corner, cy * corner,
                                                top + brilliant.GIRDLE_H * CORNER_GEM),
                                   (0, 0, 0, 1), nid=3600 + 100 * k))
    # a spine down the front, from the boss to each end, on its plate
    for k, (z, out, h) in enumerate(PLATES):
        if out == PLATES[1][1]:
            continue
        face = st["r"] + st["t"] + out
        # the cuff turns its axis z to -y
        parts.append(ring.box(1620 + k, f"Spine_{k}", (0, -z, face + 0.2),
                              (0, 0, 0, 1), (2, 4, 2), (SPINE_W, h, 0.5),
                              faces("rim_outer")))
    # the elbow rim's ring: its outer face (the band's outer radius), at
    # the top once the cuff stands up
    rim_r = st["r"] + st["t"] + 1.35
    rim_y = w / 2 - 0.5 + ring.PROUD
    for k in range(RIM_GEMS):
        phi = 2 * math.pi * (k + 0.5) / RIM_GEMS
        dist = rim_r + brilliant.GIRDLE_H * RIM_GEM
        gems.append(bracer.set_gem(RIM_GEM, (dist * math.sin(phi), rim_y,
                                             dist * math.cos(phi)),
                                   brilliant.quat_axis((0, 1, 0), phi),
                                   nid=4000 + 100 * k))
    gem = bracer.set_gem(GEM, (0, 0, top + brilliant.GIRDLE_H * GEM),
                         (0, 0, 0, 1))
    return {"nodes": [{"id": "1", "name": "R-Attachment",
                       "children": parts + [gem] + gems,
                       "position": {"x": 0, "y": w / 2 + 1, "z": 0},
                       "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                       "shape": dict(brilliant.ROOT_SHAPE)}],
            "lod": "auto"}


def worn_plates(st, y, hx, hz, t):
    """The worn cuff's plates, boss and gems (make_bracer_art.worn_model's
    extra, the cuff made without its own gem), in the forearm's frame.
    The boss is on the outer side (+x on the left arm), on the ridge: a
    sapphire in its middle and a small one at each corner."""
    nodes, nid = [], 6000
    tex = faces("outer")
    for off, out, h in WORN_PLATES:
        py = y + off * st["worn_h"] / 2
        for name, pos, size in bracer.ring_parts(f"Plate_{nid}", py, hx, hz,
                                                 t + out, h):
            nodes.append(ring.box(nid, name, pos, (0, 0, 0, 1), (4, 2, 4),
                                  size, tex))
            nid += 1
    # the flared rim at the elbow end, over the cuff's own top rim and a
    # little above it so their top faces don't meet
    rim_y = y + st["worn_h"] / 2 - 0.4 + 2 * ring.PROUD
    for name, pos, size in bracer.ring_parts("Flare", rim_y, hx, hz,
                                             t + WORN_FLARE, 0.8):
        nodes.append(ring.box(nid, name, pos, (0, 0, 0, 1), (4, 2, 4), size,
                              faces("rim_outer")))
        nid += 1
    # its ring of sapphires, on each side's outer face
    ox, oz = hx + t + WORN_FLARE, hz + t + WORN_FLARE
    lift = brilliant.GIRDLE_H * WORN_RIM_GEM
    nx, nz = WORN_RIM_GEMS
    rim_gems = []
    for sx in (1, -1):
        turn = brilliant.quat_axis((0, 1, 0), sx * math.pi / 2)
        for k in range(nx):
            z = (k - (nx - 1) / 2) * 2 * oz / nx
            rim_gems.append(((sx * (ox + lift), rim_y, z), turn))
    for sz in (1, -1):
        turn = brilliant.quat_axis((0, 1, 0), 0 if sz > 0 else math.pi)
        for k in range(nz):
            x = (k - (nz - 1) / 2) * 2 * ox / nz
            rim_gems.append(((x, rim_y, sz * (oz + lift)), turn))
    for k, (pos, turn) in enumerate(rim_gems):
        nodes.append(bracer.set_gem(WORN_RIM_GEM, pos, turn,
                                    nid=8000 + 100 * k))
    # the ridge's outer face, where worn_model sets the gem
    surface = hx + t + 0.35
    nodes.append(ring.box(nid, "Boss", (surface - 0.15, y, 0), (0, 0, 0, 1),
                          (2, 4, 4), (BOSS_T, WORN_BOSS, WORN_BOSS),
                          faces("rim_outer")))
    # the boss's outer face; each gem's table faces +x
    face = surface - 0.15 + BOSS_T / 2
    turn = brilliant.quat_axis((0, 1, 0), math.pi / 2)
    corner = WORN_BOSS / 2 - 0.4
    spots = [(WORN_GEM, 0, 0)] + [(WORN_CORNER_GEM, cy * corner, cz * corner)
                                  for cy in (1, -1) for cz in (1, -1)]
    for k, (scale, dy, dz) in enumerate(spots):
        nodes.append(bracer.set_gem(scale, (face + brilliant.GIRDLE_H * scale,
                                            y + dy, dz), turn,
                                    nid=7000 + 100 * k))
    return nodes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    MODEL.write_text(json.dumps(model(), indent=2) + "\n",
                     encoding="utf-8", newline="\n")
    WORN_MODEL.write_text(json.dumps(bracer.worn_model(a.assets, "Great", None,
                                                       extra=worn_plates,
                                                       height=WORN_H),
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
    print(f"Wrote {MODEL.name}, {WORN_MODEL.name} and {ICON_ID}.png")
    if a.preview:
        plain = Image.open(bracer.ICON_OUT / "MT_Bracer_Silver_Sapphire_Great.png")
        thorn = Image.open(bracer.ICON_OUT / "MT_Bracer_Thornguard.png")
        sheet = Image.new("RGBA", (3 * 256 + 16, 260), (55, 57, 66, 255))
        for k, im in enumerate((plain, thorn, icon)):
            sheet.alpha_composite(im.resize((256, 256), Image.NEAREST), (2 + 262 * k, 2))
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Make the art for the Amulet of the Last Stand, the named gold
necklace (v1.2, Nov 18).

It is the Great set necklace of make_necklace_art (its bunched cord,
bail and a collar holding a diamond, with prongs) with a gold heater
shield behind the gem: rows of boxes narrowing to a point, with a
raised rim along the top. The shield faces +z like the pendant, so the
icon camera sees it.

It writes:

- Necklaces/Necklace_Last_Stand.blockymodel: the held and dropped
  model;
- Icons/Items/MagicTreasures/MT_Necklace_Last_Stand.png: its icon, at
  the same scale as the other necklace icons;
- Cosmetics/MagicTreasures/Necklace_Worn_Last_Stand.blockymodel: the
  worn model (JewelryWear.NAMED_WORN), the worn Great set necklace with
  the shield behind its pendant, scaled to the worn collar (Nov 18: it
  showed as a plain necklace before).

It uses the gold diamond ring texture (Ring_Gold_Diamond.png).

Usage (from the repo root):
    python tools/make_last_stand_art.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import blocky_render  # noqa: E402
import make_necklace_art as necklace  # noqa: E402
import make_ring_art as ring  # noqa: E402

ICON_ID = "MT_Necklace_Last_Stand"
MODEL = necklace.NECK_OUT / "Necklace_Last_Stand.blockymodel"
WORN_MODEL = necklace.WORN_OUT / "Necklace_Worn_Last_Stand.blockymodel"
TEXTURE = ring.RINGS_OUT / "Ring_Gold_Diamond.png"

# the shield's rows, top to bottom: (width, height) in units
# (the diamond's octagonal setting is about 6.8 across, so the shield
# is wider and taller, to frame it: Nov 18, a smaller one hid behind it)
ROWS = [(10.0, 3.0), (9.4, 2.0), (8.4, 2.0), (7.0, 1.8), (5.2, 1.6),
        (3.2, 1.4), (1.4, 1.0)]
THICK = 0.8
# how far the shield's top stands above the pendant's centre
TOP = 3.8


def shield_nodes(collar, nid=4000, scale=1.0):
    """The shield, in the pendant's frame (facing +z), behind the
    collar; scale shrinks it for the smaller worn collar."""
    face = ring.layout(ring.METAL["collar_top"])
    side = ring.layout(ring.METAL["collar_side"])
    faces = {"front": face, "back": face, "left": side, "right": side,
             "top": side, "bottom": side}
    thick = THICK * scale
    z = -(collar * 0.15 + thick / 2)
    nodes, y = [], TOP * scale
    for k, (w, h) in enumerate(ROWS):
        w, h = w * scale, h * scale
        nodes.append(ring.box(nid + k, f"Shield_{k}", (0, y - h / 2, z),
                              (0, 0, 0, 1), (6, 6, 2), (w, h, thick),
                              faces))
        y -= h
    # a raised rim along the top edge
    rim = ring.layout(ring.METAL["prong"])
    w = ROWS[0][0] * scale
    nodes.append(ring.box(nid + 20, "Shield_Rim",
                          (0, (TOP - 0.3) * scale, z + thick / 2),
                          (0, 0, 0, 1), (2, 2, 2),
                          (w, 0.6 * scale, 0.5 * scale),
                          {f: rim for f in ("front", "back", "left", "right",
                                            "top", "bottom")}))
    return nodes


def find_pendant(nodes):
    for n in nodes:
        if n["name"] == "Pendant":
            return n
        found = find_pendant(n.get("children") or [])
        if found:
            return found
    return None


def model():
    m = necklace.icon_model("Great", "x")
    st = necklace.STYLES["Great"]
    find_pendant(m["nodes"])["children"] += shield_nodes(
        necklace.collar_for(st["gem"]))
    return m


def worn_model(assets):
    """The worn Great set necklace with the shield, scaled as the worn
    gem is to the held one. The shield takes ids from 4200: the worn
    necklace's bail is 4000 and its chain decals 4100 and 4101."""
    vmodel, _ = necklace.vanilla_necklace(assets)
    m = necklace.worn_model(vmodel, "Great", "x")
    st = necklace.STYLES["Great"]
    find_pendant(m["nodes"])["children"] += shield_nodes(
        necklace.collar_for(st["worn_gem"]), nid=4200,
        scale=st["worn_gem"] / st["gem"])
    return m


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    MODEL.write_text(json.dumps(model(), indent=2) + "\n",
                     encoding="utf-8", newline="\n")
    WORN_MODEL.write_text(json.dumps(worn_model(a.assets), indent=2) + "\n",
                          encoding="utf-8", newline="\n")
    # the necklace icons' scale (make_necklace_art.main)
    span = max(blocky_render.fit_span(
                   str(necklace.NECK_OUT / (f"Necklace_{s}" + ("_Set" if g else "")
                                            + ".blockymodel")),
                   str(ring.RINGS_OUT / "Ring_Gold_Ruby.png"), yaw=30, pitch=15)
               for s in necklace.STYLES for g in (None, "x"))
    # the shield hangs lower than any pendant: fit it if it is larger
    span = max(span, blocky_render.fit_span(str(MODEL), str(TEXTURE), yaw=30, pitch=15))
    icon = blocky_render.render(str(MODEL), str(TEXTURE),
                                str(necklace.ICON_OUT / f"{ICON_ID}.png"),
                                margin=3, pitch=15, yaw=30, span=span)
    print(f"Wrote {MODEL.name}, {WORN_MODEL.name} and {ICON_ID}.png")
    if a.preview:
        plain = Image.open(necklace.ICON_OUT / "MT_Necklace_Gold_Diamond_Great.png")
        sheet = Image.new("RGBA", (2 * 256 + 12, 260), (55, 57, 66, 255))
        sheet.alpha_composite(plain.resize((256, 256), Image.NEAREST), (2, 2))
        sheet.alpha_composite(icon.resize((256, 256), Image.NEAREST), (266, 2))
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

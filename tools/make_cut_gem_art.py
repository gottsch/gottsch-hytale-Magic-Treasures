#!/usr/bin/env python3
"""Make the cut gems' textures and icons: one per gem and cut.

Each gem has one shape (GEM_SHAPES in generate_jewelry.py; Mark, Nov
10: brilliant for diamond and ruby, the emerald cut for emerald and
sapphire, the princess cut for topaz and voidstone). The cut changes its colour and shine:

- Flawed: dull, greyer and darker, no glints;
- Fine: a little muted;
- Flawless: the gem's own colours;
- Perfect: richer and brighter, with extra white sparkles.

Run make_brilliant_gem.py, make_emerald_gem.py and make_princess_gem.py
first (they write the
models). Writes:
- Common/Items/MagicTreasures/Gems/<Gem>_<Shape>_<Cut>.png
- Common/Icons/Items/MagicTreasures/MT_Gem_<Gem>_<Cut>.png

Usage (from the repo root):
    python tools/make_cut_gem_art.py --assets <Assets.zip>
        [--preview P]
"""
import argparse
import colorsys

from PIL import Image

import blocky_render
import make_brilliant_gem as brilliant
import make_emerald_gem as emerald
import make_princess_gem as princess
from generate_jewelry import CUTS, shape_of
from make_gem_art import GEMS, ICON_OUT, TEX_OUT, palette

# (saturation, value) multipliers, whether glints stay, and the
# sparkle spacing (every nth bright texel turns white; 0: none)
TONES = {
    "Flawed": (0.55, 0.78, False, 0),
    "Fine": (0.85, 0.92, True, 0),
    "Flawless": (1.0, 1.0, True, 23),
    "Perfect": (1.15, 1.1, True, 9),
}


def tone(pal, cut):
    s_mul, v_mul, glints, _ = TONES[cut]
    out = []
    for r, g, b in pal:
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        s, v = min(1.0, s * s_mul), min(1.0, v * v_mul)
        out.append(tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h, s, v)))
    if not glints:
        # the brightest tone loses its sparkle: halfway to the next
        out[4] = tuple((a + b) // 2 for a, b in zip(out[3], out[4]))
    return out


def sparkle(im, pal, cut):
    """Turns every nth texel of the two brightest tones white."""
    step = TONES[cut][3]
    if not step:
        return im
    bright = {pal[3] + (255,), pal[4] + (255,)}
    for y in range(im.height):
        for x in range(im.width):
            if im.getpixel((x, y)) in bright and (x * 7 + y * 3) % step == 0:
                im.putpixel((x, y), (255, 255, 255, 255))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    _, emerald_regions = emerald.gem_nodes()
    _, princess_regions = princess.gem_nodes()
    icons = {}
    for gem in GEMS:
        base = palette(a.assets, gem)
        shape = shape_of(gem)
        for cut, *_ in CUTS:
            pal = tone(base, cut)
            if shape == "Princess":
                im = princess.texture(pal, princess_regions)
                model = princess.MODEL_OUT
                view = {"pitch": 24, "yaw": 30}
            elif shape == "Emerald_Cut":
                im = emerald.texture(pal, emerald_regions)
                model = emerald.MODEL_OUT
                view = {"pitch": 28, "yaw": 30}
            else:
                im = brilliant.texture(pal)
                model = brilliant.MODEL_OUT
                view = {"pitch": 18, "yaw": 22.5}
            tex = TEX_OUT / f"{gem}_{shape}_{cut}.png"
            sparkle(im, pal, cut).save(tex)
            icons[(gem, cut)] = blocky_render.render(
                str(model), str(tex), str(ICON_OUT / f"MT_Gem_{gem}_{cut}.png"),
                margin=5, **view)
    print(f"Wrote {len(icons)} cut-gem textures and icons.")
    if a.preview:
        cell = 110
        sheet = Image.new("RGBA", (cell * len(CUTS), cell * len(GEMS)),
                          (55, 57, 66, 255))
        for gi, gem in enumerate(GEMS):
            for ci, (cut, *_) in enumerate(CUTS):
                sheet.alpha_composite(
                    icons[(gem, cut)].resize((96, 96), Image.NEAREST),
                    (ci * cell + 7, gi * cell + 7))
        sheet.save(a.preview)


if __name__ == "__main__":
    main()

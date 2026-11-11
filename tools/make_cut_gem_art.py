#!/usr/bin/env python3
"""Make the cut gems' textures and icons: one per gem and cut.

Each gem has one shape (GEM_SHAPES in generate_jewelry.py; Mark, Nov
10: brilliant for diamond and ruby, the emerald cut for emerald and
sapphire, the princess cut for topaz and voidstone). The cut changes its colour and shine:

- Flawed: a little muted and darker, no glints, faint facet light;
- Fine: nearly the gem's own colours;
- Flawless: the gem's own colours, stronger facet light;
- Perfect: richer and brighter, with the strongest facet light.

All colours are lifted toward white first (LIFT), so the gems don't
read as rocks in dim places (Nov 11). Their glow and sparks come from
the item JSON (generate_jewelry.py).

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
# No white sparkles (Mark, Nov 11: the speckles on the table looked
# bad); the facet lighting and the item's sparks show the cut instead.
TONES = {
    "Flawed": (0.85, 0.85, False, 0),
    "Fine": (0.95, 0.95, True, 0),
    "Flawless": (1.05, 1.0, True, 0),
    "Perfect": (1.15, 1.1, True, 0),
}
# Every value is lifted toward white by this share first: the vanilla
# gem colours are dark (emerald 0.24-0.65), and in a dim place the cut
# gems looked like rocks (Mark, Nov 11). Lower cuts stay coloured; the
# cut shows in the sparkle and the glow instead of greying out.
LIFT = 0.3


def tone(pal, cut):
    s_mul, v_mul, glints, _ = TONES[cut]
    out = []
    for r, g, b in pal:
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        v = LIFT + (1 - LIFT) * v
        s, v = min(1.0, s * s_mul), min(1.0, v * v_mul)
        out.append(tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h, s, v)))
    if not glints:
        # the brightest tone loses its sparkle: halfway to the next
        out[4] = tuple((a + b) // 2 for a, b in zip(out[3], out[4]))
    return out


# Facet lighting like the vanilla ore textures (Mark, Nov 11: the ore
# shone, the Perfect topaz didn't): a light rim on each facet's edges,
# a darker band just inside, and a bright core toward the brightest
# tone. (rim share toward white, core share toward the bright tone)
# Edges only (Mark, Nov 11): the darker band and the glowing core
# blended each facet like anti-aliasing, so only a crisp 1-texel rim
# is kept. (rim share toward the light tone, unused)
FACET_LIGHT = {
    "Flawed": (0.15, 0),
    "Fine": (0.25, 0),
    "Flawless": (0.35, 0),
    "Perfect": (0.45, 0),
}
# colour step (sum of channel differences) that counts as a facet edge;
# the steps of a facet's own gradient are smaller
EDGE_STEP = 30


def facet_distance(im):
    """Each opaque texel's distance (in texels) to its facet's edge: a
    transparent neighbour, the texture's border, or a colour jump to a
    neighbouring facet. {(x, y): d}"""
    w, h = im.size
    px = im.load()
    dist = {}
    queue = []
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0:
                continue
            edge = False
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if not (0 <= nx < w and 0 <= ny < h) or px[nx, ny][3] == 0:
                    edge = True
                    break
                n = px[nx, ny]
                if sum(abs(a - b) for a, b in zip(c[:3], n[:3])) > EDGE_STEP:
                    edge = True
                    break
            if edge:
                dist[(x, y)] = 0
                queue.append((x, y))
    i = 0
    while i < len(queue):
        x, y = queue[i]
        i += 1
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if (0 <= nx < w and 0 <= ny < h and (nx, ny) not in dist
                    and px[nx, ny][3]):
                dist[(nx, ny)] = dist[(x, y)] + 1
                queue.append((nx, ny))
    return dist


def facet_light(im, dist, pal, cut):
    rim, core = FACET_LIGHT[cut]
    # the core glows toward the bright, saturated tone (pal[4] is often
    # pale); the rim toward the lightest tone halfway to white
    bright = pal[3]
    light = tuple((v + 255) // 2 for v in pal[4])
    px = im.load()
    for (x, y), d in dist.items():
        c = px[x, y]
        if c[:3] == (255, 255, 255):
            continue  # a sparkle
        if d != 0:
            continue  # inside the facet: left as painted
        t, target = rim, light
        px[x, y] = tuple(int(a + (b - a) * t)
                         for a, b in zip(c[:3], target)) + (c[3],)
    return im


def sparkle(im, pal, cut):
    """Turns every nth texel of the two brightest tones white."""
    step = TONES[cut][3]
    if not step:
        return im
    bright = {pal[3] + (255,), pal[4] + (255,)}
    for y in range(im.height):
        for x in range(im.width):
            # scattered, not a grid (a hash of the texel)
            if (im.getpixel((x, y)) in bright
                    and ((x * 73856093) ^ (y * 19349663)) % (step * 2) == 0):
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
            dist = facet_distance(im)
            facet_light(sparkle(im, pal, cut), dist, pal, cut).save(tex)
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

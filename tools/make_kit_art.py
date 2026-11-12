#!/usr/bin/env python3
"""Gives the Gem Cutter's Kit its own look, so it isn't mistaken for the
vanilla Iron Repair Kit it is built on: the kit's grey cloth and metal are
recoloured sapphire blue (the brown handles are left alone), on both the
model texture and the icon, and the icon gets a cut ruby in its corner.

Usage: python tools/make_kit_art.py --assets <Assets.zip>
"""
import argparse
import colorsys
import io
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
COMMON = ROOT / "src" / "main" / "resources" / "Common"
TEX_OUT = COMMON / "Items" / "MagicTreasures" / "Gems" / "Gem_Cutters_Kit_Texture.png"
ICON_OUT = COMMON / "Icons" / "Items" / "MagicTreasures" / "MT_Gem_Cutters_Kit.png"
BADGE = COMMON / "Icons" / "Items" / "MagicTreasures" / "Gem_Ruby_Brilliant.png"

VANILLA_TEX = "Common/Items/Tools/Repair_Kit/Repair_Kit_Iron_Texture.png"
VANILLA_ICON = "Common/Icons/ItemsGenerated/Tool_Repair_Kit_Iron.png"

DARK = (14, 26, 70)      # what black becomes
LIGHT = (150, 200, 255)  # what white becomes
GREY = 0.2               # saturation below this counts as grey cloth or metal


def recolour(im):
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, _, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if s > GREY and max(r, g, b) - min(r, g, b) > 20:
                continue  # coloured already: the handles
            t = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            t = min(1.0, t * 1.05)
            px[x, y] = tuple(int(DARK[i] + (LIGHT[i] - DARK[i]) * t) for i in range(3)) + (a,)
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    a = ap.parse_args()
    with zipfile.ZipFile(a.assets) as z:
        tex = Image.open(io.BytesIO(z.read(VANILLA_TEX)))
        icon = Image.open(io.BytesIO(z.read(VANILLA_ICON)))
    TEX_OUT.parent.mkdir(parents=True, exist_ok=True)
    recolour(tex).save(TEX_OUT)
    icon = recolour(icon)
    badge = Image.open(BADGE).convert("RGBA")
    size = icon.width * 3 // 8
    badge = badge.resize((size, size), Image.LANCZOS)
    icon.alpha_composite(badge, (icon.width - size - 1, icon.height - size - 1))
    icon.save(ICON_OUT)
    print("wrote", TEX_OUT.name, ICON_OUT.name)


if __name__ == "__main__":
    main()

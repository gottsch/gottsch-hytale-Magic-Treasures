#!/usr/bin/env python3
"""Make the spell scroll's texture and inventory icon (Nov 11).

Like Mage Flame's scrolls (its make_scroll_textures.py): the vanilla
scroll model (Items/Consumables/Scrolls/Scroll.blockymodel) with our
own texture, and the icon rendered from the model with that texture
(render_scroll_icon.py, copied from Mage Flame), so it looks like the
held scroll. Mark, Nov 11: a different hue from Mage Flame's tan, and
no coloured bands. So the whole parchment is moved to one hue (HUE),
keeping its shading and ink; the rolls' brown wooden ends stay brown.

Writes:
- Common/Items/MagicTreasures/Scrolls/Spell_Scroll_Texture.png
- Common/Icons/Items/MagicTreasures/MT_Spell_Scroll.png

Usage (from the repo root):
    python tools/make_scroll_art.py --assets <Assets.zip>
"""
import argparse
import colorsys
import io
import os
import tempfile
import zipfile
from pathlib import Path

from PIL import Image

import render_scroll_icon

ROOT = Path(__file__).resolve().parent.parent
COMMON = ROOT / "src" / "main" / "resources" / "Common"
TEXTURE_OUT = COMMON / "Items" / "MagicTreasures" / "Scrolls" / "Spell_Scroll_Texture.png"
ICON_OUT = COMMON / "Icons" / "Items" / "MagicTreasures" / "MT_Spell_Scroll.png"
VANILLA_TEXTURE = "Common/Items/Consumables/Scrolls/Scroll_Texture.png"
VANILLA_MODEL = "Common/Items/Consumables/Scrolls/Scroll.blockymodel"

# the parchment's new hue (0-1; vanilla tan is about 0.08), and how much
# of its saturation it keeps: an arcane violet
HUE = 0.75
SATURATION = 0.9
# the last texture row of the rolls (vanilla Scroll_Texture.png, 32 x 64)
ROLL_ROWS = 9
# Mage Flame's icon camera
ICON_YAW, ICON_PITCH = 40.0, 25.0


def wooden_end(x, y):
    """The rolls' brown wooden ends, which aren't parchment and keep their
    colour (Mark, Nov 11): in the roll rows (0-9), columns 0-1 and 23-24,
    and the end caps at 25-29."""
    return y <= ROLL_ROWS and (x <= 1 or x >= 23)


def recolour(im):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0 or wooden_end(x, y):
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            r2, g2, b2 = colorsys.hsv_to_rgb(HUE, min(1.0, s * SATURATION), v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    a = ap.parse_args()
    with zipfile.ZipFile(a.assets) as z:
        base = Image.open(io.BytesIO(z.read(VANILLA_TEXTURE))).convert("RGBA")
        model_bytes = z.read(VANILLA_MODEL)
    TEXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    ICON_OUT.parent.mkdir(parents=True, exist_ok=True)
    recolour(base).save(TEXTURE_OUT)
    with tempfile.TemporaryDirectory() as tmp:
        model_path = os.path.join(tmp, "Scroll.blockymodel")
        with open(model_path, "wb") as f:
            f.write(model_bytes)
        render_scroll_icon.render(model_path, str(TEXTURE_OUT), str(ICON_OUT), ICON_YAW, ICON_PITCH)
    print("Wrote", TEXTURE_OUT.name, "and", ICON_OUT.name)


if __name__ == "__main__":
    main()

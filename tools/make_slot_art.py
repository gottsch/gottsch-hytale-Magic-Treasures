"""Inventory slot art for scrolls: a tinted slot per quality tier.

The slot behind an item comes from its Quality asset (SlotTexture). To
make scrolls stand out without losing their rarity tier, each family and
tier gets its own quality (Server/Item/Qualities/MT_<Family>_<Tier>.json)
whose slot is the vanilla tier's slot, tinted:

- Spell scrolls: violet, with a faint rune circle and eight-point star.
- Recipe scrolls: gold, with a faint plain ring and square, so the two
  kinds read apart at a glance.

How the tint works:
- the body (dark, low-saturation texels) is blended toward the family's
  ramp by TINT (0 = vanilla, 1 = full colour), keeping the vanilla
  diagonal sheen;
- the tier's accent (the bright corner glow and rim) keeps its colour,
  so Common, Uncommon and Rare still read apart.

Each quality is a copy of the vanilla tier's JSON with only the slot
keys swapped, so the name colour, label and drop particles stay. The
textures must sit under UI/ItemQualities/ (the asset validator's root).
Vanilla ships the slots only as @2x (148 x 148) and references them
without the suffix, so ours do the same.

Usage (from the repo root):
    python tools/make_slot_art.py --assets <Assets.zip> [--preview P]
"""
import argparse
import colorsys
import io
import json
import math
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

COMMON = Path(__file__).resolve().parent.parent / "src" / "main" / \
    "resources" / "Common"
SLOTS_OUT = COMMON / "UI" / "ItemQualities" / "Slots" / "MagicTreasures"
QUALITIES_OUT = COMMON.parent / "Server" / "Item" / "Qualities"
TIERS = ["Common", "Uncommon", "Rare", "Epic", "Legendary"]

# per family: body ramp (dark, light), how strongly the body is tinted,
# the sigil's colour and alpha (0-255), and whether it has the star
FAMILIES = {
    "Spell": {"dark": (20, 12, 34), "light": (78, 42, 122), "tint": 0.5,
              "sigil": (196, 150, 255), "alpha": 26, "star": True},
    "Recipe": {"dark": (34, 24, 10), "light": (128, 92, 34), "tint": 0.5,
               "sigil": (255, 214, 130), "alpha": 26, "star": False},
}


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def clamp(v):
    return max(0.0, min(1.0, v))


def recolour(im, fam):
    out = im.copy()
    px = out.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            # the tier accent: saturated and fairly bright; keep it
            accent = clamp((s - 0.35) / 0.25) * clamp((v - 0.35) / 0.2)
            ramp = lerp(fam["dark"], fam["light"], clamp((v - 0.08) / 0.42))
            body = lerp((r, g, b), ramp, fam["tint"])
            c = lerp(body, (r, g, b), accent)
            px[x, y] = (*c, a)
    return out


def sigil(size, fam):
    """A faint double ring with tick marks, then an eight-point star
    (spells) or a single square (recipes)."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    c = size / 2
    col = (*fam["sigil"], fam["alpha"])
    for r in (size * 0.36, size * 0.30):
        d.ellipse((c - r, c - r, c + r, c + r), outline=col, width=2)
    if fam["star"]:
        # tick marks between the two rings
        for i in range(16):
            ang = i * math.tau / 16
            r0, r1 = size * 0.305, size * 0.355
            d.line((c + r0 * math.cos(ang), c + r0 * math.sin(ang),
                    c + r1 * math.cos(ang), c + r1 * math.sin(ang)),
                   fill=col, width=2)
    r = size * 0.28
    turns = (0.0, math.pi / 4) if fam["star"] else (math.pi / 4,)
    for turn in turns:
        pts = [(c + r * math.cos(turn + i * math.tau / 4),
                c + r * math.sin(turn + i * math.tau / 4))
               for i in range(4)]
        d.line(pts + [pts[0]], fill=col, width=2)
    return layer


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    SLOTS_OUT.mkdir(parents=True, exist_ok=True)
    QUALITIES_OUT.mkdir(parents=True, exist_ok=True)
    rows = {"Vanilla": []}
    with zipfile.ZipFile(a.assets) as z:
        for tier in TIERS:
            src = f"Common/UI/ItemQualities/Slots/Slot{tier}@2x.png"
            im = Image.open(io.BytesIO(z.read(src))).convert("RGBA")
            rows["Vanilla"].append(im)
            vanilla = json.loads(z.read(f"Server/Item/Qualities/{tier}.json"))
            for family, fam in FAMILIES.items():
                out = recolour(im, fam)
                mark = sigil(im.width, fam)
                # only inside the slot's own shape
                mark.putalpha(Image.composite(mark.split()[3],
                                              Image.new("L", im.size, 0),
                                              out.split()[3]))
                out = Image.alpha_composite(out, mark)
                name = f"MT_Slot_{family}_{tier}"
                out.save(SLOTS_OUT / f"{name}@2x.png")
                rows.setdefault(family, []).append(out)
                quality = dict(vanilla)
                slot = f"UI/ItemQualities/Slots/MagicTreasures/{name}.png"
                for key in ("SlotTexture", "BlockSlotTexture",
                            "SpecialSlotTexture"):
                    quality[key] = slot
                (QUALITIES_OUT / f"MT_{family}_{tier}.json").write_text(
                    json.dumps(quality, indent=2) + "\n", encoding="utf-8")
    if a.preview:
        w = rows["Vanilla"][0].width
        sheet = Image.new("RGBA", ((w + 10) * len(TIERS),
                                   (w + 10) * len(rows)), (28, 28, 32, 255))
        for ri, images in enumerate(rows.values()):
            for i, im in enumerate(images):
                sheet.paste(im, (i * (w + 10), ri * (w + 10)), im)
        sheet.save(a.preview)
    print(f"Wrote {len(TIERS) * len(FAMILIES)} slot textures and qualities.")


if __name__ == "__main__":
    main()

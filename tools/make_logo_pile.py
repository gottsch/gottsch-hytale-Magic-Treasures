"""Draws the Magic Treasures treasure-pile logo: a heap of gold coins with an amulet,
cut gems and a ring on top, shaded with gradients, a soft glow and a dark outline, in
the style of painted game icons.

Usage: python make_logo_pile.py <out_dir>
Writes magictreasures-icon-pile-512.png and magictreasures-icon-pile-400.png.
"""
import math

import numpy as np
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageChops

S = 2048
rng = random.Random(11)


def P(x, y):
    return (x * S, y * S)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(box, top, bottom, angle_down=True):
    """A vertical gradient image covering box (in pixels)."""
    x0, y0, x1, y1 = [int(v) for v in box]
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    g = Image.new("RGB", (1, h))
    for y in range(h):
        g.putpixel((0, y), lerp(top, bottom, y / max(1, h - 1)))
    return g.resize((w, h)), (x0, y0)


def fill_poly(layer, pts, top, bottom):
    pts = [P(*p) for p in pts]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    box = (min(xs), min(ys), max(xs) + 1, max(ys) + 1)
    img, at = gradient(box, top, bottom)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).polygon([(x - at[0], y - at[1]) for x, y in pts], fill=255)
    layer.paste(img, at, mask)


def fill_ellipse(layer, cx, cy, rx, ry, top, bottom):
    box = (P(cx - rx, cy - ry)[0], P(cx - rx, cy - ry)[1], P(cx + rx, cy + ry)[0], P(cx + rx, cy + ry)[1])
    img, at = gradient(box, top, bottom)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, img.size[0] - 1, img.size[1] - 1], fill=255)
    layer.paste(img, at, mask)


GOLD_HI, GOLD, GOLD_LO, GOLD_DEEP = (255, 236, 150), (240, 182, 60), (190, 118, 30), (120, 66, 18)


DIM = [1.0]  # brightness for the coins being drawn: back rows of the heap are darker


def dimmed(c):
    return tuple(int(v * DIM[0]) for v in c)


def soft_shadow(layer, cx, cy, rx, ry, alpha=75, blur=0.012):
    pad = int(blur * S * 3)
    w, h = int(rx * 2 * S) + 2 * pad, int(ry * 2 * S) + 2 * pad
    sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([pad, pad, w - pad, h - pad], fill=(20, 8, 4, alpha))
    sh = sh.filter(ImageFilter.GaussianBlur(blur * S))
    layer.alpha_composite(sh, (int((cx - rx) * S) - pad, int((cy - ry) * S) - pad))


def coin(layer, cx, cy, r, tilt=0.42, shadow=True):
    ry = r * tilt
    th = r * 0.16
    if shadow:
        soft_shadow(layer, cx + r * 0.12, cy + th + ry * 0.35, r * 1.05, ry * 1.1)
    fill_ellipse(layer, cx, cy + th, r, ry, dimmed(GOLD_LO), dimmed(GOLD_DEEP))  # edge
    fill_ellipse(layer, cx, cy, r, ry, dimmed(GOLD_HI), dimmed(GOLD))              # face
    fill_ellipse(layer, cx, cy + ry * 0.08, r * 0.72, ry * 0.72, dimmed(GOLD), dimmed(GOLD_HI))  # sunk centre
    d = ImageDraw.Draw(layer)
    # rim light along the far edge, and a specular glint
    d.arc([P(cx - r, cy - ry), P(cx + r, cy + ry)], 190, 300, fill=dimmed((255, 245, 190)), width=max(1, int(r * S * 0.06)))
    d.ellipse([P(cx - r * 0.3, cy - ry * 0.55), P(cx - r * 0.05, cy - ry * 0.3)], fill=dimmed((255, 250, 215)))


def gem_brilliant(layer, cx, cy, w, h, pal):
    lite, mid, dark, deep = pal
    t, g, c = cy - h * 0.45, cy - h * 0.12, cy + h * 0.55
    xs = [cx - w / 2, cx - w / 4, cx, cx + w / 4, cx + w / 2]
    for i, (a, b) in enumerate(zip(xs, xs[1:])):
        cols = [(mid, dark), (lite, mid), (dark, deep), (mid, deep)][i]
        fill_poly(layer, [(a, g), (b, g), (cx, c)], *cols)
    tx = [cx - w * 0.3, cx, cx + w * 0.3]
    gx = [cx - w / 2, cx - w / 6, cx + w / 6, cx + w / 2]
    crown = [([(gx[0], g), (tx[0], t), (gx[1], g)], (mid, dark)),
             ([(tx[0], t), (gx[1], g), (tx[1], t)], (lite, lite)),
             ([(gx[1], g), (tx[1], t), (gx[2], g)], (lite, mid)),
             ([(tx[1], t), (gx[2], g), (tx[2], t)], (mid, mid)),
             ([(gx[2], g), (tx[2], t), (gx[3], g)], (dark, deep))]
    for pts, cols in crown:
        fill_poly(layer, pts, *cols)


def gem_step(layer, cx, cy, w, h, pal):
    """An emerald (step) cut seen face up: an octagon with an inner table."""
    lite, mid, dark, deep = pal
    k = 0.22
    outer = [(cx - w / 2 + w * k, cy - h / 2), (cx + w / 2 - w * k, cy - h / 2), (cx + w / 2, cy - h / 2 + h * k),
             (cx + w / 2, cy + h / 2 - h * k), (cx + w / 2 - w * k, cy + h / 2), (cx - w / 2 + w * k, cy + h / 2),
             (cx - w / 2, cy + h / 2 - h * k), (cx - w / 2, cy - h / 2 + h * k)]
    fill_poly(layer, outer, mid, deep)
    s = 0.58
    inner = [(cx + (x - cx) * s, cy + (y - cy) * s) for x, y in outer]
    # bevels between the rims, lit from the top left
    for i in range(8):
        a, b = outer[i], outer[(i + 1) % 8]
        ia, ib = inner[i], inner[(i + 1) % 8]
        lit = i in (7, 0, 6)
        fill_poly(layer, [a, b, ib, ia], *((lite, mid) if lit else (dark, deep) if i in (3, 4) else (mid, dark)))
    fill_poly(layer, inner, lite, dark)
    d = ImageDraw.Draw(layer)
    hx, hy = inner[0]
    d.polygon([P(hx, hy + h * 0.04), P(hx + w * 0.12, hy + h * 0.04), P(hx, hy + h * 0.16)], fill=(240, 255, 240))


def oval_gem(layer, cx, cy, rx, ry, pal):
    lite, mid, dark, deep = pal
    fill_ellipse(layer, cx, cy, rx, ry, mid, deep)
    fill_ellipse(layer, cx - rx * 0.08, cy - ry * 0.1, rx * 0.72, ry * 0.72, lite, dark)
    d = ImageDraw.Draw(layer)
    d.ellipse([P(cx - rx * 0.45, cy - ry * 0.6), P(cx - rx * 0.1, cy - ry * 0.3)], fill=(235, 250, 255))


def amulet(layer, cx, cy):
    d = ImageDraw.Draw(layer)
    # chain: drapes from the bail on top down onto the coins either side
    for end in ((cx - 0.24, cy + 0.11), (cx + 0.25, cy + 0.13)):
        sx, sy = cx, cy - 0.15
        mx, my = (sx + end[0]) / 2, sy + 0.16
        for i in range(1, 15):
            t = i / 14
            x = (1 - t) ** 2 * sx + 2 * (1 - t) * t * mx + t * t * end[0]
            y = (1 - t) ** 2 * sy + 2 * (1 - t) * t * my + t * t * end[1]
            coin(layer, x, y, 0.014, tilt=0.8, shadow=False)
    fill_ellipse(layer, cx, cy - 0.145, 0.026, 0.03, GOLD_HI, GOLD_LO)  # bail
    fill_ellipse(layer, cx, cy - 0.145, 0.012, 0.015, GOLD_DEEP, GOLD_DEEP)
    fill_ellipse(layer, cx, cy + 0.006, 0.105, 0.135, GOLD_LO, GOLD_DEEP)
    fill_ellipse(layer, cx, cy, 0.1, 0.13, GOLD_HI, GOLD_LO)
    for i in range(12):  # beaded rim
        a = 2 * math.pi * i / 12
        coin(layer, cx + 0.088 * math.cos(a), cy + 0.116 * math.sin(a), 0.012, tilt=0.9, shadow=False)
    oval_gem(layer, cx, cy, 0.068, 0.095, [(150, 225, 255), (40, 140, 235), (20, 70, 170), (8, 30, 90)])


def ring(layer, cx, cy, r):
    d = ImageDraw.Draw(layer)
    w = int(r * 0.32 * S)
    box = [P(cx - r, cy - r * 0.8), P(cx + r, cy + r * 0.8)]
    d.ellipse(box, outline=GOLD_DEEP, width=w)
    box2 = [P(cx - r, cy - r * 0.8 - 0.006), P(cx + r, cy + r * 0.8 - 0.006)]
    d.arc(box2, 170, 360, fill=GOLD, width=int(w * 0.75))
    d.arc(box2, 200, 290, fill=GOLD_HI, width=int(w * 0.35))
    gem_brilliant(layer, cx, cy - r * 0.98, r * 0.9, r * 0.75,
                  [(200, 160, 255), (150, 90, 230), (95, 45, 170), (55, 20, 110)])


def sparkle(layer, cx, cy, r):
    glow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([P(cx - r * 0.7, cy - r * 0.7), P(cx + r * 0.7, cy + r * 0.7)], fill=(255, 245, 200, 140))
    glow = glow.filter(ImageFilter.GaussianBlur(r * S * 0.4))
    layer.alpha_composite(glow)
    d = ImageDraw.Draw(layer)
    k = r * 0.16
    d.polygon([P(cx, cy - r), P(cx + k, cy - k), P(cx + r, cy), P(cx + k, cy + k), P(cx, cy + r),
               P(cx - k, cy + k), P(cx - r, cy), P(cx - k, cy - k)], fill=(255, 252, 230))


def draw():
    bg = Image.new("RGBA", (S, S), (18, 22, 40, 255))
    # violet glow behind the pile
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([P(0.14, 0.12), P(0.86, 0.84)], fill=(110, 60, 190, 200))
    bg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(S * 0.08)))

    fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    # coin heap: back rows first, a mound from y 0.58 to 0.86
    coins = []
    for _ in range(140):
        y = rng.uniform(0.56, 0.86)
        half = 0.12 + (y - 0.56) * 0.9
        x = 0.5 + rng.uniform(-half, half)
        coins.append((y, x, rng.uniform(0.045, 0.06)))
    for y, x, r in sorted(coins):
        DIM[0] = 0.74 + 0.26 * min(1.0, (y - 0.56) / 0.3)
        coin(fg, x, y, r)
    DIM[0] = 1.0
    # shadows the treasures cast on the coins
    for cx, cy, rx, ry in ((0.29, 0.67, 0.09, 0.03), (0.5, 0.6, 0.12, 0.04), (0.7, 0.7, 0.1, 0.03),
                           (0.62, 0.86, 0.07, 0.02), (0.26, 0.84, 0.11, 0.03)):
        soft_shadow(fg, cx + 0.02, cy, rx, ry, alpha=110, blur=0.02)
    gem_step(fg, 0.29, 0.6, 0.15, 0.12, [(170, 255, 190), (40, 200, 110), (15, 120, 60), (5, 60, 30)])
    amulet(fg, 0.5, 0.46)
    gem_brilliant(fg, 0.7, 0.62, 0.17, 0.14, [(255, 140, 150), (225, 40, 70), (160, 20, 45), (90, 8, 25)])
    ring(fg, 0.26, 0.74, 0.095)
    gem_brilliant(fg, 0.62, 0.8, 0.11, 0.09, [(255, 245, 160), (250, 200, 40), (190, 130, 10), (120, 70, 5)])
    coin(fg, 0.78, 0.8, 0.055)

    # light from the top left across the treasure, darker toward the bottom right
    a = np.asarray(fg).astype(np.float32)
    yy, xx = np.mgrid[0:S, 0:S] / S
    light = 1.16 - 0.26 * ((xx + yy) / 2)
    a[..., :3] = np.clip(a[..., :3] * light[..., None], 0, 255)
    fg = Image.fromarray(a.astype(np.uint8), "RGBA")

    # coloured glow behind each gem
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for cx, cy, r, col in ((0.5, 0.46, 0.13, (60, 150, 255, 170)), (0.7, 0.62, 0.1, (255, 60, 90, 150)),
                           (0.29, 0.6, 0.09, (60, 230, 130, 140)), (0.62, 0.8, 0.07, (255, 210, 60, 140))):
        gd.ellipse([P(cx - r, cy - r), P(cx + r, cy + r)], fill=col)
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.04))
    bg.alpha_composite(glow)

    # dark outline around everything, like painted item icons
    alpha = fg.getchannel("A")
    grown = alpha.filter(ImageFilter.MaxFilter(int(S * 0.010) | 1)).filter(ImageFilter.GaussianBlur(2))
    outline = Image.new("RGBA", (S, S), (24, 12, 30, 255))
    outline.putalpha(grown)
    bg.alpha_composite(outline)
    bg.alpha_composite(fg)
    over = np.asarray(glow).astype(np.float32)
    base = np.asarray(bg).astype(np.float32)
    base[..., :3] = np.clip(base[..., :3] + over[..., :3] * (over[..., 3:] / 255) * 0.55, 0, 255)
    bg = Image.fromarray(base.astype(np.uint8), "RGBA")
    for x, y, r in ((0.36, 0.3, 0.05), (0.7, 0.36, 0.035), (0.82, 0.62, 0.03), (0.2, 0.5, 0.025), (0.55, 0.62, 0.028)):
        sparkle(bg, x, y, r)

    # bloom: the brightest parts bleed light
    img = np.asarray(bg.convert("RGB")).astype(np.float32)
    lum = img.mean(axis=2)
    bright = np.where((lum > 175)[..., None], img, 0).astype(np.uint8)
    bloom = np.asarray(Image.fromarray(bright).filter(ImageFilter.GaussianBlur(S * 0.012))).astype(np.float32)
    img = 255 - (255 - img) * (255 - bloom * 0.9) / 255  # screen
    # vignette and a little grain so it doesn't read as flat fills
    r2 = (xx - 0.5) ** 2 + (yy - 0.5) ** 2
    img *= np.clip(1.08 - r2 * 1.1, 0.55, 1.0)[..., None]
    img += np.random.default_rng(3).normal(0, 4.0, img.shape[:2])[..., None]
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    # a light smudge: blur slightly, then sharpen back the edges, softening hard vector steps
    out = out.filter(ImageFilter.GaussianBlur(S * 0.0015)).filter(ImageFilter.UnsharpMask(radius=6, percent=60, threshold=2))
    return out


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    img = draw()
    for size in (512, 400):
        path = os.path.join(out, f"magictreasures-icon-pile-{size}.png")
        img.resize((size, size), Image.LANCZOS).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()

"""Draws the Magic Treasures CurseForge logo: a gold ring set with a faceted ruby on a violet
badge, in the flat style of the Mage Flame logo.

Usage: python make_logo.py <out_dir>
Writes magictreasures-icon-512.png and magictreasures-icon-400.png.
"""
import os
import sys

from PIL import Image, ImageDraw

SS = 2048  # drawn large, then scaled down for smooth edges

BACK = (18, 25, 43)
BADGE = (44, 30, 78)
GOLD = (227, 168, 58)
GOLD_DARK = (176, 118, 30)
GOLD_LIGHT = (255, 218, 120)
RUBY = [(224, 52, 74), (178, 30, 54), (255, 110, 126), (140, 20, 40)]
SPARK = (255, 241, 176)


def p(x, y):
    return (x * SS, y * SS)


def circle(draw, cx, cy, r, fill):
    draw.ellipse([p(cx - r, cy - r), p(cx + r, cy + r)], fill=fill)


def sparkle(draw, cx, cy, r):
    k = r * 0.22
    draw.polygon([p(cx, cy - r), p(cx + k, cy - k), p(cx + r, cy), p(cx + k, cy + k),
                  p(cx, cy + r), p(cx - k, cy + k), p(cx - r, cy), p(cx - k, cy - k)], fill=SPARK)


def draw_logo():
    img = Image.new("RGB", (SS, SS), BACK)
    d = ImageDraw.Draw(img)
    circle(d, 0.5, 0.5, 0.40, BADGE)

    # band: a gold annulus with a light edge on the upper left and a dark one lower right
    bx, by, ro, ri = 0.5, 0.62, 0.22, 0.16
    circle(d, bx, by, ro, GOLD)
    w = int(0.022 * SS)
    box = [p(bx - ro + 0.012, by - ro + 0.012), p(bx + ro - 0.012, by + ro - 0.012)]
    d.arc(box, 140, 235, fill=GOLD_LIGHT, width=w)
    d.arc(box, 330, 60, fill=GOLD_DARK, width=w)
    circle(d, bx, by, ri, BADGE)

    # setting: a gold cup under the stone, and claws at the girdle
    y0, y1, y2 = 0.20, 0.29, 0.47  # table, girdle, culet
    d.polygon([p(0.325, y1 + 0.012), p(0.675, y1 + 0.012), p(0.5, y2 + 0.035)], fill=GOLD_DARK)

    # pavilion: four triangles meeting at the culet
    xs = [0.34, 0.415, 0.5, 0.585, 0.66]
    shades = [RUBY[1], RUBY[0], RUBY[3], RUBY[1]]
    for i in range(4):
        d.polygon([p(xs[i], y1), p(xs[i + 1], y1), p(0.5, y2)], fill=shades[i])
    # crown: alternating triangles between the girdle and the table
    g = [0.34, 0.44, 0.56, 0.66]
    t = [0.415, 0.5, 0.585]
    crown = [
        ([p(g[0], y1), p(t[0], y0), p(g[1], y1)], RUBY[0]),
        ([p(t[0], y0), p(g[1], y1), p(t[1], y0)], RUBY[2]),
        ([p(g[1], y1), p(t[1], y0), p(g[2], y1)], RUBY[0]),
        ([p(t[1], y0), p(g[2], y1), p(t[2], y0)], RUBY[1]),
        ([p(g[2], y1), p(t[2], y0), p(g[3], y1)], RUBY[3]),
    ]
    for pts, col in crown:
        d.polygon(pts, fill=col)
    for cx in (0.343, 0.657):
        circle(d, cx, y1 - 0.004, 0.02, GOLD)

    sparkle(d, 0.27, 0.27, 0.055)
    sparkle(d, 0.75, 0.21, 0.038)
    sparkle(d, 0.77, 0.74, 0.03)
    sparkle(d, 0.22, 0.66, 0.024)
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    img = draw_logo()
    for size in (512, 400):
        path = os.path.join(out, f"magictreasures-icon-{size}.png")
        img.resize((size, size), Image.LANCZOS).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()

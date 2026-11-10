#!/usr/bin/env python3
"""Render a Hytale .blockymodel (boxes) with its texture to a PNG icon.

Hytale's item icons (Icons/ItemsGenerated/) are rendered by the game
client, which a plugin can't run. This draws the same kind of picture
from the model and texture, so new items get icons in the vanilla
style: an orthographic 3/4 view, lit from the top left.

Supports box and quad shapes with position, offset, stretch,
orientation (quaternion), nested nodes, and per-face texture offsets
with mirror and 0/90/180/270 angles. Transparent texels are skipped,
so a quad can draw a triangle. Other shapes are skipped.

Usage:
    python tools/blocky_render.py MODEL TEXTURE OUT.png
        [--size 64] [--yaw 45] [--pitch 30] [--margin 4]
        [--tint #rrggbb]
"""
import argparse
import json
import math

from PIL import Image, ImageDraw

SUPER = 4  # supersampling factor


def quat_rotate(q, v):
    x, y, z, w = q
    vx, vy, vz = v
    # t = 2 * cross(q.xyz, v)
    tx = 2 * (y * vz - z * vy)
    ty = 2 * (z * vx - x * vz)
    tz = 2 * (x * vy - y * vx)
    return (vx + w * tx + (y * tz - z * ty),
            vy + w * ty + (z * tx - x * tz),
            vz + w * tz + (x * ty - y * tx))


def quat_mul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
            aw * bw - ax * bx - ay * by - az * bz)


def vec(d, default=0.0):
    d = d or {}
    return (d.get("x", default), d.get("y", default), d.get("z", default))


# face -> (origin corner, u axis, v axis, normal) for a unit box
# [-1,1]^3; u runs left to right, v top to bottom in the texture.
FACES = {
    "front": ((-1, 1, 1), (2, 0, 0), (0, -2, 0), (0, 0, 1)),
    "back": ((1, 1, -1), (-2, 0, 0), (0, -2, 0), (0, 0, -1)),
    "right": ((1, 1, 1), (0, 0, -2), (0, -2, 0), (1, 0, 0)),
    "left": ((-1, 1, -1), (0, 0, 2), (0, -2, 0), (-1, 0, 0)),
    "top": ((-1, 1, -1), (2, 0, 0), (0, 0, 2), (0, 1, 0)),
    "bottom": ((-1, -1, 1), (2, 0, 0), (0, 0, -2), (0, -1, 0)),
}


# quad normal -> (the box face it draws as, its box size); a quad is a
# box face of zero thickness
QUAD_FACES = {
    "+Z": lambda x, y: ("front", (x, y, 0)),
    "-Z": lambda x, y: ("back", (x, y, 0)),
    "+X": lambda x, y: ("right", (0, y, x)),
    "-X": lambda x, y: ("left", (0, y, x)),
    "+Y": lambda x, y: ("top", (x, 0, y)),
    "-Y": lambda x, y: ("bottom", (x, 0, y)),
}


def face_dims(face, size):
    sx, sy, sz = size
    if face in ("front", "back"):
        return sx, sy
    if face in ("left", "right"):
        return sz, sy
    return sx, sz


def collect(nodes, parent_pos, parent_rot, out):
    for node in nodes:
        pos = vec(node.get("position"))
        rot = vec(node.get("orientation"))
        q = node.get("orientation") or {}
        rot = (q.get("x", 0), q.get("y", 0), q.get("z", 0), q.get("w", 1))
        world_pos = tuple(p + r for p, r in
                          zip(parent_pos, quat_rotate(parent_rot, pos)))
        world_rot = quat_mul(parent_rot, rot)
        shape = node.get("shape") or {}
        if (shape.get("type") in ("box", "quad")
                and node.get("visible", True)):
            out.append((world_pos, world_rot, shape))
        # children are placed from the node's pivot plus its shape
        # offset (the player's hand hangs from the end of the forearm
        # box, not its pivot)
        off = quat_rotate(world_rot, vec(shape.get("offset")))
        child_origin = tuple(p + o for p, o in zip(world_pos, off))
        collect(node.get("children") or [], child_origin, world_rot, out)


def texel(tex, layout, face, u, v, w, h):
    """Colour at texel (u, v) of a w x h face."""
    ox, oy = layout["offset"]["x"], layout["offset"]["y"]
    mirror = layout.get("mirror") or {}
    angle = layout.get("angle", 0) % 360
    if mirror.get("x"):
        u = w - 1 - u
    if mirror.get("y"):
        v = h - 1 - v
    # the texture region is rotated by angle; map back
    if angle == 90:
        tu, tv = h - 1 - v, u
    elif angle == 180:
        tu, tv = w - 1 - u, h - 1 - v
    elif angle == 270:
        tu, tv = v, w - 1 - u
    else:
        tu, tv = u, v
    x, y = ox + tu, oy + tv
    if 0 <= x < tex.width and 0 <= y < tex.height:
        return tex.getpixel((x, y))
    return (255, 0, 255, 255)


def render(model_path, texture_path, out_path, size=64, yaw=45.0,
           pitch=30.0, margin=4, tint=None, light=(-0.45, 0.8, 0.4),
           span=None):
    """Renders the model to out_path and returns the image.

    The model is fitted to the icon unless span is given: then span
    model units (as seen from the camera) fill the icon less its
    margin, so icons of related models share one scale. fit_span()
    gives a model's own span.
    """
    model = json.load(open(model_path, encoding="utf-8"))
    tex = Image.open(texture_path).convert("RGBA")
    boxes = []
    collect(model.get("nodes") or [], (0, 0, 0), (0, 0, 0, 1), boxes)

    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p
        # yaw around y, then pitch around x; camera looks down -z
        x1 = cy * x + sy * z
        z1 = -sy * x + cy * z
        y2 = cp * y - sp * z1
        z2 = sp * y + cp * z1
        return x1, y2, z2

    lx, ly, lz = light
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    light = (lx / ln, ly / ln, lz / ln)

    quads = []  # (depth, [(sx, sy) x4], rgba)
    for pos, rot, shape in boxes:
        settings = shape.get("settings") or {}
        stretch = vec(shape.get("stretch"), 1.0)
        off = vec(shape.get("offset"))
        if shape.get("type") == "quad":
            # a single face: its size is (x, y) in the plane facing
            # the normal, and its texture is the "front" layout
            qx, qy = vec(settings.get("size"))[:2]
            face_key, size3 = QUAD_FACES[settings.get("normal", "+Z")](qx, qy)
            layouts = {face_key: (shape.get("textureLayout") or {}).get("front")}
            two_sided = shape.get("doubleSided", False)
        else:
            size3 = vec(settings.get("size"))
            layouts = shape.get("textureLayout") or {}
            two_sided = False
        half = tuple(s * k / 2 for s, k in zip(size3, stretch))
        for face, (o, ua, va, n) in FACES.items():
            layout = layouts.get(face)
            if layout is None:
                continue
            w, h = (int(round(d)) for d in face_dims(face, size3))
            if w <= 0 or h <= 0:
                continue
            normal = quat_rotate(rot, n)
            vn = view(normal)
            if vn[2] <= 1e-6:  # facing away
                if not two_sided:
                    continue
                normal = tuple(-c for c in normal)
            shade = 0.55 + 0.45 * max(0.0, sum(a * b for a, b in
                                               zip(normal, light)))

            def corner(a, b):
                local = tuple((o[i] + ua[i] * a + va[i] * b) * half[i]
                              + off[i] for i in range(3))
                world = quat_rotate(rot, local)
                return view(tuple(world[i] + pos[i] for i in range(3)))

            for v in range(h):
                for u in range(w):
                    rgba = texel(tex, layout, face, u, v, w, h)
                    if rgba[3] == 0:
                        continue
                    pts = [corner(u / w, v / h), corner((u + 1) / w, v / h),
                           corner((u + 1) / w, (v + 1) / h),
                           corner(u / w, (v + 1) / h)]
                    depth = sum(p[2] for p in pts) / 4
                    r, g, b, a = rgba
                    if tint:
                        r, g, b = (r * tint[0] // 255, g * tint[1] // 255,
                                   b * tint[2] // 255)
                    col = (min(255, int(r * shade)), min(255, int(g * shade)),
                           min(255, int(b * shade)), a)
                    quads.append((depth, pts, col))

    if not quads:
        raise SystemExit("nothing to draw")
    xs = [p[0] for q in quads for p in q[1]]
    ys = [p[1] for q in quads for p in q[1]]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    big = size * SUPER
    own_span = max(maxx - minx, maxy - miny)
    if out_path is None:
        return own_span
    scale = (big - 2 * margin * SUPER) / (span or own_span)
    cx, cyy = (minx + maxx) / 2, (miny + maxy) / 2

    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    quads.sort(key=lambda q: q[0])  # far first
    for _, pts, col in quads:
        poly = [(big / 2 + (p[0] - cx) * scale,
                 big / 2 - (p[1] - cyy) * scale) for p in pts]
        draw.polygon(poly, fill=col, outline=col)
    img = img.resize((size, size), Image.LANCZOS)
    img.save(out_path)
    return img


def fit_span(model_path, texture_path, yaw=45.0, pitch=30.0):
    """The model's extent as seen from the camera, in model units: pass
    the largest of a set as render(span=...) to draw them all at one
    scale."""
    return render(model_path, texture_path, None, yaw=yaw, pitch=pitch)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("texture")
    ap.add_argument("out")
    ap.add_argument("--size", type=int, default=64)
    ap.add_argument("--yaw", type=float, default=45.0)
    ap.add_argument("--pitch", type=float, default=30.0)
    ap.add_argument("--margin", type=int, default=4)
    ap.add_argument("--tint")
    a = ap.parse_args()
    tint = None
    if a.tint:
        t = a.tint.lstrip("#")
        tint = tuple(int(t[i:i + 2], 16) for i in (0, 2, 4))
    render(a.model, a.texture, a.out, a.size, a.yaw, a.pitch, a.margin, tint)


if __name__ == "__main__":
    main()

"""
Renders an inventory icon (64x64) of the held scroll model with a given texture, so colored bands on the rods are real
3D rings that bend over the rod's corners (a flat recolor of the vanilla icon cannot do that).

It is a small orthographic software renderer for a .blockymodel (boxes and quads, node hierarchy with position + quaternion),
sampling the texture with nearest filtering. Conventions are the common ones for blocky models; the rod's stripes are symmetric,
so face flips do not matter for the bands.

Usage (plain python with Pillow + numpy):
    python render_scroll_icon.py <model.blockymodel> <texture.png> <out.png> [yaw] [pitch]
"""
import json
import math
import sys

import numpy as np
from PIL import Image

SIZE = 64
SUPERSAMPLE = 6
MARGIN = 3


def quat_to_matrix(x, y, z, w):
    n = math.sqrt(x * x + y * y + z * z + w * w) or 1.0
    x, y, z, w = x / n, y / n, z / n, w / n
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


def node_matrix(node):
    p = node.get("position", {})
    q = node.get("orientation", {"x": 0, "y": 0, "z": 0, "w": 1})
    m = np.eye(4)
    m[:3, :3] = quat_to_matrix(q["x"], q["y"], q["z"], q.get("w", 1))
    m[:3, 3] = [p.get("x", 0), p.get("y", 0), p.get("z", 0)]
    return m


def face_quads(shape):
    """Yield (corners_local[4x3], uv_rect(x, y, w, h), mirror_x, mirror_y) for every textured face of a box or quad."""
    kind = shape.get("type")
    off = shape.get("offset", {})
    cx, cy, cz = off.get("x", 0), off.get("y", 0), off.get("z", 0)
    st = shape.get("settings", {}).get("size", {})
    layout = shape.get("textureLayout", {})

    def uv(name):
        f = layout.get(name)
        if not f:
            return None
        o, m = f["offset"], f.get("mirror", {})
        return o["x"], o["y"], bool(m.get("x")), bool(m.get("y"))

    if kind == "quad":
        sx, sy = st.get("x", 0), st.get("y", 0)
        hx, hy = sx / 2, sy / 2
        f = uv("front")
        if f:
            corners = np.array([[cx - hx, cy + hy, cz], [cx + hx, cy + hy, cz], [cx + hx, cy - hy, cz], [cx - hx, cy - hy, cz]])
            yield corners, (f[0], f[1], sx, sy), f[2], f[3]
    elif kind == "box":
        sx, sy, sz = st.get("x", 0), st.get("y", 0), st.get("z", 0)
        hx, hy, hz = sx / 2, sy / 2, sz / 2
        faces = {
            "front":  ([[-hx, hy, hz], [hx, hy, hz], [hx, -hy, hz], [-hx, -hy, hz]], sx, sy),
            "back":   ([[hx, hy, -hz], [-hx, hy, -hz], [-hx, -hy, -hz], [hx, -hy, -hz]], sx, sy),
            "right":  ([[hx, hy, hz], [hx, hy, -hz], [hx, -hy, -hz], [hx, -hy, hz]], sz, sy),
            "left":   ([[-hx, hy, -hz], [-hx, hy, hz], [-hx, -hy, hz], [-hx, -hy, -hz]], sz, sy),
            "top":    ([[-hx, hy, -hz], [hx, hy, -hz], [hx, hy, hz], [-hx, hy, hz]], sx, sz),
            "bottom": ([[-hx, -hy, hz], [hx, -hy, hz], [hx, -hy, -hz], [-hx, -hy, -hz]], sx, sz),
        }
        for name, (corners, w, h) in faces.items():
            f = uv(name)
            if f:
                yield np.array(corners) + [cx, cy, cz], (f[0], f[1], w, h), f[2], f[3]


# Icon-only tweak: in this reading of the model the three paper pieces hang about 16 units below the top rod, while the second
# rod sits about 27 units down, leaving a visible gap in the picture. Raise the second rod (the LAST "Scroll1" child of the root)
# so it meets the paper. Does not change the game model.
LOWER_ROD_RAISE = 10.5


def collect(node, parent, out, raise_lower_rod=False):
    m = parent @ node_matrix(node)
    if raise_lower_rod:
        m = m.copy()
        m[1, 3] += LOWER_ROD_RAISE
        m[2, 3] -= 1.5
    shape = node.get("shape")
    if shape and shape.get("type") in ("box", "quad"):
        for corners, rect, mx, my in face_quads(shape):
            world = (m[:3, :3] @ corners.T).T + m[:3, 3]
            out.append((world, rect, mx, my))
    children = node.get("children", [])
    for i, child in enumerate(children):
        lower_rod = node.get("name") == "R-Hand" and i == len(children) - 1 and child.get("name") == "Scroll1"
        collect(child, m if not raise_lower_rod else parent @ node_matrix(node), out, lower_rod)


def render(model_path, texture_path, out_path, yaw_deg=40.0, pitch_deg=25.0):
    model = json.load(open(model_path, encoding="utf-8"))
    tex = np.array(Image.open(texture_path).convert("RGBA"))
    th, tw = tex.shape[0], tex.shape[1]

    quads = []
    for root in model["nodes"]:
        collect(root, np.eye(4), quads)

    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    ry = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    rx = np.array([[1, 0, 0], [0, math.cos(pitch), -math.sin(pitch)], [0, math.sin(pitch), math.cos(pitch)]])
    view = rx @ ry

    projected = [((view @ w.T).T, rect, mx, my) for w, rect, mx, my in quads]
    allp = np.vstack([p for p, *_ in projected])
    mn, mx_ = allp.min(axis=0), allp.max(axis=0)
    scale = (SIZE * SUPERSAMPLE - 2 * MARGIN * SUPERSAMPLE) / max(mx_[0] - mn[0], mx_[1] - mn[1])
    big = SIZE * SUPERSAMPLE
    ox = (big - (mx_[0] - mn[0]) * scale) / 2 - mn[0] * scale
    oy = (big - (mx_[1] - mn[1]) * scale) / 2 + mx_[1] * scale

    color = np.zeros((big, big, 4), dtype=np.uint8)
    depth = np.full((big, big), -1e9)

    for p, (u0, v0, w, h), mirror_x, mirror_y in projected:
        sx = p[:, 0] * scale + ox
        sy = oy - p[:, 1] * scale
        sz = p[:, 2]
        # quad corners: 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left; two triangles
        for tri, uvs in (((0, 1, 2), ((0, 0), (1, 0), (1, 1))), ((0, 2, 3), ((0, 0), (1, 1), (0, 1)))):
            xs, ys = sx[list(tri)], sy[list(tri)]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(big - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(big - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-9:
                continue
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            l2 = 1 - l0 - l1
            inside = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
            if not inside.any():
                continue
            z = l0 * sz[tri[0]] + l1 * sz[tri[1]] + l2 * sz[tri[2]]
            a = l0 * uvs[0][0] + l1 * uvs[1][0] + l2 * uvs[2][0]
            b = l0 * uvs[0][1] + l1 * uvs[1][1] + l2 * uvs[2][1]
            if mirror_x:
                a = 1 - a
            if mirror_y:
                b = 1 - b
            tx = np.clip((u0 + a * w).astype(int), 0, tw - 1)
            ty = np.clip((v0 + b * h).astype(int), 0, th - 1)
            texel = tex[ty, tx]
            ok = inside & (z > depth[y0:y1 + 1, x0:x1 + 1]) & (texel[..., 3] > 0)
            region_depth = depth[y0:y1 + 1, x0:x1 + 1]
            region_color = color[y0:y1 + 1, x0:x1 + 1]
            region_depth[ok] = z[ok]
            region_color[ok] = texel[ok]

    img = Image.fromarray(color, "RGBA").resize((SIZE, SIZE), Image.BOX)
    img.save(out_path)
    return out_path


if __name__ == "__main__":
    args = sys.argv[1:]
    yaw = float(args[3]) if len(args) > 3 else 40.0
    pitch = float(args[4]) if len(args) > 4 else 25.0
    print("wrote", render(args[0], args[1], args[2], yaw, pitch))

#!/usr/bin/env python3
"""Software preview of the drone model + skin.

Projects the exact boxes declared in ``DroneModel`` with the real texture and a
simple painter's-algorithm renderer, so the look of the airframe can be checked
without launching Minecraft. Orthographic projection keeps every face a
parallelogram, which makes the affine texture sampling exact.

Usage::

    python3 tools/preview_model.py            # writes tools/preview/*.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

try:
    import numpy as np
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("numpy and pillow are required")

ROOT = Path(__file__).resolve().parent.parent
SKIN = ROOT / "src/main/resources/assets/dron/textures/entity/combat_drone.png"
OUT = ROOT / "tools/preview"

# name, uv(u,v), origin(x,y,z), size(w,h,d)  -- mirrored from DroneModel.java
CUBES = [
    ("body", (0, 0), (-3.5, -2.0, -5.0), (7.0, 3.5, 9.0)),
    ("top plate", (32, 0), (-2.5, -2.75, -3.5), (5.0, 1.0, 6.0)),
    ("tail boom", (0, 13), (-1.0, -1.0, 4.0), (2.0, 1.5, 4.0)),
    ("camera", (0, 20), (-1.5, -1.5, -1.5), (3.0, 3.0, 3.0)),
    ("lens", (14, 20), (-1.0, -1.0, -2.5), (2.0, 2.0, 1.0)),
    ("warhead", (0, 26), (-1.5, 0.0, -3.5), (3.0, 2.0, 7.0)),
    ("nose", (24, 26), (-1.0, 0.0, -4.5), (2.0, 2.0, 1.0)),
    ("cap", (32, 26), (-1.5, 0.0, 3.5), (3.0, 2.0, 1.0)),
    ("led", (40, 26), (-0.5, -0.8, 7.5), (1.0, 1.0, 1.0)),
    ("skid fwd", (0, 36), (-4.5, 2.6, -3.5), (9.0, 1.0, 1.0)),
    ("skid aft", (0, 40), (-4.5, 2.6, 2.5), (9.0, 1.0, 1.0)),
    ("strut l", (28, 36), (-4.0, 1.6, -3.0), (1.0, 1.0, 1.0)),
    ("strut r", (32, 36), (3.0, 1.6, -3.0), (1.0, 1.0, 1.0)),
]

# The four rotor assemblies: arm + motor bell + crossed blades (mirrors DroneModel).
# Rotor part origin is (x, -1.2, z); prop origin is (side * 7, -1.35, 0) inside it.
for name, sx, sz, side in (("fl", -4.0, -4.0, -1), ("fr", 4.0, -4.0, 1),
                           ("bl", -4.0, 4.0, -1), ("br", 4.0, 4.0, 1)):
    arm_x = 3.0 if side > 0 else -7.0
    CUBES.append((f"arm {name}", (42, 0), (sx + arm_x, -1.2 - 0.4, sz - 0.4), (4.0, 1.0, 1.0)))
    CUBES.append((f"motor {name}", (52, 0), (sx + side * 7.0 - 0.75, -2.2, sz - 0.75), (1.5, 1.5, 1.5)))
    px, py, pz = sx + side * 7.0, -1.35, sz
    CUBES.append((f"blade {name} a", (44, 8), (px - 4.6, py - 0.1, pz - 0.35), (4.3, 0.12, 0.7)))
    CUBES.append((f"blade {name} b", (44, 8), (px + 0.3, py - 0.1, pz - 0.35), (4.3, 0.12, 0.7)))
    CUBES.append((f"blade {name} c", (44, 10), (px - 0.35, py - 0.1, pz - 4.6), (0.7, 0.12, 4.3)))
    CUBES.append((f"blade {name} d", (44, 10), (px - 0.35, py - 0.1, pz + 0.3), (0.7, 0.12, 4.3)))
    CUBES.append((f"hub {name}", (56, 12), (px - 0.5, py - 0.35, pz - 0.5), (1.0, 0.5, 1.0)))


def uv_faces(u: int, v: int, w: float, h: float, d: float):
    """Return {face: (x, y, w, h, u0, v0, u1, v1)} in texture pixels.

    Layout inside ``u,v``:
      top    : (u+d,        v,        w,      d)
      bottom : (u+d+w,      v,        w,      d)
      -X     : (u,          v+d,      d,      h)
      +Z     : (u+d+w,      v+d,      d,      h)
      +X     : (u+2*(w+d),  v+d,      d,      h)
      -Z     : (u+d,        v+d,      w,      h)
    """
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "-X": (u, v + d, d, h),
        "+Z": (u + d + w, v + d, d, h),
        "+X": (u + 2 * (d + w), v + d, d, h),
        "-Z": (u + d, v + d, w, h),
    }


def rot_y(p, a):
    c, s = math.cos(a), math.sin(a)
    return np.array([c * p[0] + s * p[2], p[1], -s * p[0] + c * p[2]])


def rot_x(p, a):
    c, s = math.cos(a), math.sin(a)
    return np.array([p[0], c * p[1] - s * p[2], s * p[1] + c * p[2]])


def project(p, scale, centre, tilt_deg, spin_deg):
    q = rot_y(np.array(p, dtype=float) - np.array([0.0, -0.4, 0.0]), math.radians(spin_deg))
    q = rot_x(q, math.radians(tilt_deg))
    # screen: x right, y down; depth = q[2]
    return np.array([centre[0] + q[0] * scale, centre[1] - q[1] * scale]), q[2]


def render(scale=26, size=(760, 560), tilt=18.0, spin=38.0, bg=(18, 26, 24)):
    skin = Image.open(SKIN).convert("RGBA")
    tex = np.asarray(skin).astype(np.float32)
    canvas = np.zeros((size[1], size[0], 3), dtype=np.float32)
    canvas[:] = bg
    # soft radial vignette
    yy, xx = np.mgrid[0:size[1], 0:size[0]]
    d = np.sqrt(((xx - size[0] / 2) / size[0]) ** 2 + ((yy - size[1] / 2) / size[1]) ** 2)
    canvas *= np.clip(1.25 - d * 1.1, 0.45, 1.0)[..., None]

    centre = (size[0] / 2, size[1] / 2 + 10)
    faces = []
    for name, uv, origin, dims in CUBES:
        ox, oy, oz = origin
        w, h, dd = dims
        u, v = uv
        quads = {
            "top": [(-1, -1, -1), (1, -1, -1), (1, -1, 1), (-1, -1, 1)],       # y = oy
            "bottom": [(-1, 1, -1), (-1, 1, 1), (1, 1, 1), (1, 1, -1)],        # y = oy+h
            "-X": [(-1, -1, -1), (-1, 1, -1), (-1, 1, 1), (-1, -1, 1)],        # x = ox
            "+X": [(1, -1, -1), (1, -1, 1), (1, 1, 1), (1, 1, -1)],            # x = ox+w
            "-Z": [(-1, -1, -1), (-1, -1, 1), (1, -1, 1), (1, -1, -1)],        # z = oz
            "+Z": [(-1, -1, 1), (-1, 1, 1), (1, 1, 1), (1, -1, 1)],            # z = oz+dd
        }
        rects = uv_faces(u, v, w, h, dd)
        for face, corners in quads.items():
            pts = []
            for sx, sy, sz in corners:
                px = ox + (w if sx > 0 else 0.0)
                py = oy + h if sy > 0 else oy
                pz = oz + (dd if sz > 0 else 0.0)
                pts.append(project((px, py, pz), scale, centre, tilt, spin))
            screen = np.array([p[0] for p in pts])
            depth = sum(p[1] for p in pts) / 4.0
            faces.append((depth, name, face, screen, rects[face]))

    faces.sort(key=lambda f: -f[0])

    for depth, name, face, screen, (fx, fy, fw, fh) in faces:
        if fw < 0.5 or fh < 0.5:
            continue
        # parallelogram: origin = screen[0], edges = screen[1]-screen[0], screen[3]-screen[0]
        p0 = screen[0]
        e1 = screen[1] - screen[0]
        e2 = screen[3] - screen[0]
        minx = int(math.floor(min(screen[:, 0])))
        maxx = int(math.ceil(max(screen[:, 0])))
        miny = int(math.floor(min(screen[:, 1])))
        maxy = int(math.ceil(max(screen[:, 1])))
        minx, miny = max(minx, 0), max(miny, 0)
        maxx, maxy = min(maxx, size[0] - 1), min(maxy, size[1] - 1)
        if maxx <= minx or maxy <= miny:
            continue
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        gx, gy = np.meshgrid(np.arange(minx, maxx + 1), np.arange(miny, maxy + 1))
        dx = gx + 0.5 - p0[0]
        dy = gy + 0.5 - p0[1]
        a = (dx * e2[1] - dy * e2[0]) / det   # across e1
        b = (dy * e1[0] - dx * e1[1]) / det   # across e2
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        su = np.clip((fx + a * fw), 0, skin.width - 1)
        sv = np.clip((fy + b * fh), 0, skin.height - 1)
        sample = tex[sv.astype(int), su.astype(int)]
        alpha = (sample[..., 3:4] / 255.0) * inside[..., None]
        # crude directional light on the face normal
        light = {"top": 1.12, "bottom": 0.62, "-X": 0.86, "+X": 0.98, "-Z": 0.92, "+Z": 0.78}[face]
        region = canvas[miny:maxy + 1, minx:maxx + 1]
        region[:] = region * (1 - alpha) + sample[..., :3] * light * alpha

    out = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))
    return out


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    views = {
        "three_quarter": dict(tilt=18.0, spin=38.0),
        "front": dict(tilt=6.0, spin=0.0),
        "top": dict(tilt=78.0, spin=20.0),
        "side": dict(tilt=4.0, spin=90.0),
    }
    sheet = Image.new("RGB", (760 * 2, 560 * 2), (14, 20, 18))
    for i, (name, kwargs) in enumerate(views.items()):
        img = render(**kwargs)
        img.save(OUT / f"{name}.png")
        sheet.paste(img, ((i % 2) * 760, (i // 2) * 560))
    sheet.save(OUT / "contact_sheet.png")
    print(f"previews written to {OUT}")


if __name__ == "__main__":
    main()

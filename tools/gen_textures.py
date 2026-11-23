#!/usr/bin/env python3
"""Paint the dron mod textures (drone skin, controller icon, mod icon).

Reproducible, dependency-free (uses Pillow). Run from the repo root:

    python3 tools/gen_textures.py
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install pillow")

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/dron"

# Palette
CARBON = (34, 36, 38, 255)
CARBON_LIGHT = (56, 60, 63, 255)
CARBON_DARK = (20, 21, 23, 255)
OLIVE = (86, 92, 53, 255)
OLIVE_LIGHT = (108, 115, 68, 255)
OLIVE_DARK = (58, 62, 36, 255)
METAL = (126, 130, 134, 255)
METAL_DARK = (78, 82, 86, 255)
WARHEAD = (146, 60, 44, 255)
WARHEAD_LIGHT = (186, 84, 58, 255)
HAZARD = (222, 186, 46, 255)
LENS = (60, 210, 170, 255)
LENS_DARK = (22, 92, 76, 255)
LED = (255, 78, 62, 255)


def face(img: Image.Image, d: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int, base, shade=None, grain=0.12):
    """Flat colour face with a subtle vertical shade and noise grain."""
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=base)
    if shade is not None:
        d.rectangle([x, y, x + w - 1, y + max(0, h // 3 - 1)], fill=shade)
    if grain > 0:
        px = img.load()
        rnd = random.Random((x * 73856093) ^ (y * 19349663) ^ w)
        for gx in range(x, x + w):
            for gy in range(y, y + h):
                if rnd.random() < grain:
                    r, g, b, a = px[gx, gy]
                    delta = rnd.choice((-8, -5, 5, 8))
                    px[gx, gy] = (
                        max(0, min(255, r + delta)),
                        max(0, min(255, g + delta)),
                        max(0, min(255, b + delta)),
                        a,
                    )


def camo_patch(d, x, y, w, h, seed):
    rnd = random.Random(seed)
    for _ in range(w * h // 7):
        px = x + rnd.randrange(w)
        py = y + rnd.randrange(h)
        size = rnd.choice((1, 1, 2, 2, 3))
        colour = rnd.choice((OLIVE_LIGHT, OLIVE_DARK, (74, 79, 46, 255)))
        d.rectangle([px, py, px + size - 1, py + size - 1], fill=colour)


def build_drone_skin() -> Image.Image:
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    # --- body (0,0) 7 x 3.5 (rounded up to 4) x 9
    # The box unwraps as: top(0,0), bottom(?,?), sides... We simply paint the
    # whole 0..46 x 0..46 region deterministically by faces used by the cubes.
    # Main shell: top face 7x9 at (0,0), side band 7x4 at (0,9)? -> we paint a
    # canvas of carbon with olive camo panels, which reads correctly from all
    # sides of the small airframe.
    face(im, d, 0, 0, 7, 9, CARBON, CARBON_LIGHT, 0.10)          # top plate area
    camo_patch(d, 0, 0, 7, 9, 7)
    face(im, d, 7, 0, 9, 7, CARBON, CARBON_LIGHT, 0.08)          # side wraps
    camo_patch(d, 7, 0, 9, 7, 11)
    face(im, d, 18, 0, 7, 4, CARBON_DARK, CARBON, 0.08)          # belly
    face(im, d, 26, 0, 9, 4, CARBON_DARK, CARBON, 0.08)

    # heat sink / top plate (32,0) 5 x 1 x 6 -> 5 wide, 6 tall face
    face(im, d, 32, 0, 8, 6, METAL, METAL_DARK, 0.06)
    for i in range(0, 8, 2):
        d.line([32 + i, 0, 32 + i, 5], fill=METAL_DARK)

    # tail boom (0,13) 2 x 1.5 x 4
    face(im, d, 0, 13, 4, 5, OLIVE_DARK, OLIVE, 0.10)
    face(im, d, 4, 13, 2, 4, OLIVE, OLIVE_LIGHT, 0.10)

    # camera housing (0,20) 3x3x3
    face(im, d, 0, 20, 3, 3, CARBON_DARK, CARBON, 0.05)
    face(im, d, 3, 20, 3, 3, CARBON, CARBON_LIGHT, 0.05)
    # lens (14,20) 2x2x1 -> paint a glowing green lens
    face(im, d, 14, 20, 3, 3, LENS_DARK, LENS, 0.0)
    d.rectangle([16, 22, 17, 23], fill=LENS)
    d.point((14, 20), fill=(230, 255, 246, 255))
    # bezel
    d.rectangle([13, 19, 18, 24], outline=CARBON_DARK)
    d.rectangle([14, 19, 15, 19], fill=METAL)

    # warhead pod (0,26) 3 x 2 x 7 and its nose (24,26) 2x2x1
    face(im, d, 0, 26, 7, 3, WARHEAD, WARHEAD_LIGHT, 0.08)
    face(im, d, 7, 26, 3, 2, WARHEAD, WARHEAD_LIGHT, 0.08)
    # hazard stripes along the pod
    for i in range(0, 8, 2):
        d.line([i, 26, i, 28], fill=HAZARD)
    for i in range(9, 24, 2):
        d.point((i + 1, 26), fill=HAZARD)
    face(im, d, 24, 26, 2, 3, WARHEAD_LIGHT, WARHEAD, 0.04)

    # LED (40,26) 1x1x1
    d.rectangle([40, 26, 41, 27], fill=LED)
    d.point((40, 26), fill=(255, 210, 200, 255))

    # arms (42,0) 4x1x1 and motor bells (52,0) 1.5^3
    face(im, d, 42, 0, 4, 4, CARBON_LIGHT, METAL_DARK, 0.05)
    face(im, d, 52, 0, 4, 4, METAL, METAL_DARK, 0.04)
    d.rectangle([52, 0, 55, 1], fill=METAL_DARK)

    # props (44,8) 4x0x1 and (44,10) 1x0x4 - two thin dark blades
    face(im, d, 44, 8, 4, 3, (46, 48, 52, 255), (70, 74, 78, 255), 0.05)
    face(im, d, 44, 10, 4, 3, (46, 48, 52, 255), (70, 74, 78, 255), 0.05)
    d.point((45, 9), fill=(120, 124, 128, 255))

    # skids (0,36) 9x1x1, (0,40) and struts (28,36)/(32,36)
    for x0, y0, w, h in ((0, 36, 9, 3), (0, 40, 9, 3), (28, 36, 2, 2), (32, 36, 2, 2)):
        face(im, d, x0, y0, w, h, METAL_DARK, METAL, 0.08)

    return im


def build_remote_icon() -> Image.Image:
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    # rugged olive controller body
    d.rounded_rectangle([2, 1, 13, 14], radius=2, fill=OLIVE)
    d.rounded_rectangle([2, 1, 13, 3], radius=1, fill=OLIVE_LIGHT)
    d.rectangle([2, 12, 13, 14], fill=OLIVE_DARK)
    d.rectangle([2, 1, 2, 14], fill=OLIVE_DARK)
    d.rectangle([13, 1, 13, 14], fill=OLIVE_DARK)

    # FPV screen with a green feed
    d.rectangle([4, 3, 11, 8], fill=(12, 16, 14, 255))
    d.rectangle([5, 4, 10, 7], fill=(28, 120, 84, 255))
    d.line([5, 6, 10, 6], fill=(96, 240, 170, 255))
    d.point((7, 5), fill=(180, 255, 220, 255))
    d.point((9, 7), fill=(180, 255, 220, 255))

    # gimbal sticks
    d.rectangle([4, 9, 5, 11], fill=METAL_DARK)
    d.point((4, 9), fill=METAL)
    d.rectangle([10, 9, 11, 11], fill=METAL_DARK)
    d.point((10, 9), fill=METAL)

    # warning lamp + antenna
    d.point((6, 12), fill=LED)
    d.point((9, 12), fill=HAZARD)
    d.line([13, 1, 15, 0], fill=METAL_DARK)

    return im


def build_mod_icon() -> Image.Image:
    size = 128
    im = Image.new("RGBA", (size, size), (10, 22, 18, 255))
    d = ImageDraw.Draw(im)

    # radial green glow background
    for r in range(size // 2, 0, -1):
        t = r / (size / 2)
        colour = (
            int(10 + 20 * (1 - t)),
            int(30 + 70 * (1 - t) ** 2),
            int(24 + 50 * (1 - t) ** 2),
            255,
        )
        d.ellipse([size // 2 - r, size // 2 - r, size // 2 + r, size // 2 + r], fill=colour)

    # scanlines
    for y in range(0, size, 4):
        d.line([0, y, size, y], fill=(10, 40, 30, 255))

    # drone silhouette: X frame + four rotors
    cx = cy = size // 2
    d.line([cx - 26, cy - 26, cx + 26, cy + 26], fill=(70, 76, 80, 255), width=5)
    d.line([cx + 26, cy - 26, cx - 26, cy + 26], fill=(70, 76, 80, 255), width=5)

    for dx, dy in ((-30, -30), (30, -30), (-30, 30), (30, 30)):
        rx, ry = cx + dx, cy + dy
        d.ellipse([rx - 15, ry - 15, rx + 15, ry + 15], outline=(108, 247, 180, 255), width=2)
        d.ellipse([rx - 9, ry - 9, rx + 9, ry + 9], fill=(28, 60, 48, 255), outline=(64, 190, 130, 255))
        d.line([rx - 7, ry, rx + 7, ry], fill=(150, 230, 190, 255), width=1)
        d.line([rx, ry - 7, rx, ry + 7], fill=(150, 230, 190, 255), width=1)

    # body + warhead
    d.rounded_rectangle([cx - 14, cy - 10, cx + 14, cy + 10], radius=4, fill=(38, 40, 42, 255),
                        outline=(120, 126, 130, 255))
    d.rounded_rectangle([cx - 8, cy + 10, cx + 8, cy + 17], radius=3, fill=(146, 60, 44, 255))
    for i in range(-7, 8, 3):
        d.line([cx + i, cy + 11, cx + i, cy + 16], fill=HAZARD)

    # camera lens
    d.ellipse([cx - 5, cy - 6, cx + 5, cy + 4], fill=(14, 18, 16, 255), outline=(90, 96, 100, 255))
    d.ellipse([cx - 3, cy - 4, cx + 2, cy + 1], fill=LENS)
    d.point((cx - 2, cy - 3), fill=(230, 255, 246, 255))

    # HUD reticle corners
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        x0 = cx + sx * 46
        y0 = cy + sy * 46
        d.line([x0, y0, x0 - sx * 14, y0], fill=(108, 247, 180, 255), width=3)
        d.line([x0, y0, x0, y0 - sy * 14], fill=(108, 247, 180, 255), width=3)

    return im


def main() -> None:
    random.seed(20241122)
    (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)

    skin = build_drone_skin()
    skin.save(ASSETS / "textures/entity/combat_drone.png")
    build_remote_icon().save(ASSETS / "textures/item/drone_remote.png")
    build_mod_icon().save(ASSETS / "icon.png")
    print("textures written")


if __name__ == "__main__":
    main()

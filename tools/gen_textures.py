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
CARBON = (46, 49, 53, 255)
CARBON_LIGHT = (66, 71, 76, 255)
CARBON_DARK = (28, 30, 33, 255)
OLIVE = (96, 104, 60, 255)
OLIVE_LIGHT = (118, 127, 74, 255)
OLIVE_DARK = (68, 73, 42, 255)
METAL = (140, 146, 151, 255)
METAL_DARK = (88, 94, 99, 255)
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
    """Paint the 64x64 skin using the exact UV boxes of DroneModel.

    Box unwrap layout (per cube, ``u,v`` from ``CubeListBuilder.texOffs``):

        +---------+----+----+----+
        |   top   | -X | +Z | +X |        + -Z face of the lens is painted
        +---------+----+----+----+        here as the glowing FPV objective
        |  bottom | pz | nz | -Z |
        +---------+----+----+----+
                   | +Y | -Y |
                   +----+----+
    """
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rnd = random.Random(20241122)

    def box(u, v, w, h, depth, base, light, dark, top=True, bottom=True, sides=True, grain=0.10):
        """Paint a full cube unwrap with panel shading."""
        w, h, depth = int(round(w)), int(round(h)), int(round(depth))
        bw, bh = 2 * (w + depth), h + depth
        # top / bottom rows
        if top:
            face(im, d, u + depth, v, w, depth, light, light, grain * 0.6)
        if bottom:
            face(im, d, u + depth + w, v, w, depth, dark, dark, grain * 0.6)
        if sides:
            face(im, d, u, v + depth, depth, h, base, light, grain)
            face(im, d, u + depth + w, v + depth, depth, h, base, light, grain)
            face(im, d, u + 2 * (depth + w), v + depth, depth, h, base, light, grain)
            face(im, d, u + depth, v + depth, w, h, base, light, grain)
        # panel line around each face
        for (fx, fy, fw, fh) in (
                (u + depth, v, w, depth),
                (u, v + depth, depth, h),
                (u + depth + w, v + depth, depth, h),
                (u + 2 * (depth + w), v + depth, depth, h),
                (u + depth, v + depth, w, h),
                (u + depth + w, v, w, depth)):
            if fw <= 0 or fh <= 0:
                continue
            d.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], outline=(16, 17, 18, 255))
        return bw, bh

    # --- body shell: carbon with olive camo panels --------------------------
    box(0, 0, 7, 3.5, 9, CARBON, CARBON_LIGHT, CARBON_DARK)
    # hazard flashes on both flanks (the -X and +X side panels)
    for band_x in (0, 23):
        for i in range(0, 9, 2):
            d.line([band_x + i, 10, band_x + i, 11], fill=HAZARD)
    # olive shoulder panels along the spine sides
    d.rectangle([0, 5, 8, 8], fill=OLIVE_DARK)
    d.rectangle([23, 5, 31, 8], fill=OLIVE_DARK)
    camo_patch(d, 0, 0, 32, 12, 7)
    camo_patch(d, 0, 0, 12, 12, 17)
    # rivets along the spine and one vent bank per side
    for rx in range(1, 31, 4):
        d.point((rx, 1), fill=METAL)
    for vy in range(5, 11, 3):
        d.line([3, vy, 9, vy], fill=CARBON_DARK)
        d.line([24, vy, 30, vy], fill=CARBON_DARK)

    # --- top plate / heat sink ---------------------------------------------
    box(32, 0, 5, 1, 6, METAL, METAL_DARK, CARBON_DARK)
    for fy in range(1, 7, 2):
        d.line([38, fy, 43, fy], fill=METAL_DARK)

    # --- tail boom ---------------------------------------------------------
    box(0, 13, 2, 1.5, 4, OLIVE, OLIVE_LIGHT, OLIVE_DARK)

    # --- camera pod --------------------------------------------------------
    box(0, 20, 3, 3, 3, CARBON_DARK, CARBON, (12, 13, 14, 255))
    d.point((2, 22), fill=METAL)

    # --- lens: the -Z face of the lens cube is the FPV objective -----------
    # unwrap of the 2x2x1 cube: -Z face lives at (u+d, v+d) sized w x h
    lx, ly = 14 + 1, 20 + 1
    d.rectangle([lx, ly, lx + 1, ly + 1], fill=(12, 20, 17, 255))
    d.point((lx, ly), fill=LENS_DARK)
    d.point((lx + 1, ly), fill=LENS)
    d.point((lx, ly + 1), fill=LENS)
    d.point((lx + 1, ly + 1), fill=(236, 255, 249, 255))
    # bezel ring around the objective
    d.rectangle([lx - 1, ly - 1, lx + 2, ly + 2], outline=METAL)
    # other faces of the lens barrel
    d.rectangle([14, 20, 19, 20], fill=METAL_DARK)
    d.rectangle([14, 23, 19, 23], fill=CARBON_DARK)

    # --- warhead pod: olive body, hazard band, red nose --------------------
    box(0, 26, 3, 2, 7, OLIVE, OLIVE_LIGHT, OLIVE_DARK)
    # hazard band on the +X side panel and the top row
    for hx in range(0, 32, 4):
        d.line([hx, 28, hx + 1, 30], fill=HAZARD)
    for hy in range(26, 28):
        for hx in range(10, 20, 3):
            d.point((hx, hy), fill=HAZARD)
    # nose cone and tail cap in warhead red
    box(24, 26, 2, 2, 1, WARHEAD, WARHEAD_LIGHT, WARHEAD_LIGHT)
    box(32, 26, 3, 2, 1, WARHEAD, WARHEAD_LIGHT, WARHEAD_LIGHT)

    # --- nav LED -----------------------------------------------------------
    d.rectangle([40, 26, 43, 27], fill=LED)
    d.point((41, 26), fill=(255, 214, 206, 255))

    # --- arms and motors ---------------------------------------------------
    box(42, 0, 4, 1, 1, CARBON_LIGHT, METAL_DARK, CARBON_DARK)
    box(52, 0, 1.5, 1.5, 1.5, METAL, METAL_DARK, CARBON_DARK)
    d.point((53, 1), fill=(210, 214, 218, 255))

    # --- props: thin dark blades with a worn tip ---------------------------
    face(im, d, 44, 8, 10, 1, (44, 46, 50, 255), (66, 70, 74, 255), 0.05)
    face(im, d, 44, 10, 10, 4, (44, 46, 50, 255), (66, 70, 74, 255), 0.05)
    d.point((44, 8), fill=(150, 156, 160, 255))
    d.point((53, 8), fill=(150, 156, 160, 255))

    # --- landing skids -----------------------------------------------------
    box(0, 36, 9, 1, 1, METAL_DARK, METAL, (52, 56, 60, 255))
    box(0, 40, 9, 1, 1, METAL_DARK, METAL, (52, 56, 60, 255))
    box(28, 36, 1, 1, 1, METAL, METAL_DARK, CARBON_DARK)
    box(32, 36, 1, 1, 1, METAL, METAL_DARK, CARBON_DARK)

    # slight per-pixel wear so the Carbon isn't perfectly flat
    px = im.load()
    for _ in range(420):
        x, y = rnd.randrange(64), rnd.randrange(64)
        r, g, b, a = px[x, y]
        if a == 0:
            continue
        delta = rnd.choice((-10, -6, 6, 10))
        px[x, y] = (max(0, min(255, r + delta)), max(0, min(255, g + delta)),
                    max(0, min(255, b + delta)), a)
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

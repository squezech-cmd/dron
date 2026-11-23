#!/usr/bin/env python3
"""Synthesise the dron mod sound set (Vorbis OGG, 22.05 kHz mono).

Every effect is generated from scratch so the set is reproducible without any
sample libraries:

* ``drone_loop``    — seamless 2 s motor buzz (looped client-side)
* ``drone_buzz``    — short rev-up cue
* ``drone_launch``  — launch whoosh with a doppler-ish sweep
* ``drone_explode`` — layered warhead detonation
* ``drone_beep``    — controller blip
* ``drone_static``  — radio static bed of a degraded video link

Usage (from the repo root)::

    python3 tools/gen_sounds.py
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    import numpy as np
    import soundfile as sf
except ImportError:  # pragma: no cover
    sys.exit("numpy and soundfile are required: pip install numpy soundfile")

SR = 22050
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/dron/sounds"


# ----------------------------------------------------------------- utilities

def envelope(n: int, attack: float, decay: float, power: float = 2.0) -> np.ndarray:
    """Attack / exponential-decay envelope, in seconds."""
    t = np.arange(n) / SR
    env = np.exp(-np.maximum(0.0, t - attack) * decay)
    rise = np.clip(t / max(attack, 1e-4), 0.0, 1.0) ** power
    return env * rise


def noise(n: int, seed: int) -> np.ndarray:
    return np.random.default_rng(seed).normal(0.0, 1.0, n)


def lowpass(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    """Simple one-pole cascade low-pass (cheap and stable)."""
    alpha = 1.0 - np.exp(-2.0 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for _ in range(order):
        acc = 0.0
        out = np.empty_like(x)
        for i in range(len(x)):
            acc += alpha * (x[i] - acc)
            out[i] = acc
        x = out
    return x


def highpass(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    return x - lowpass(x, cutoff, order)


def resonator(x: np.ndarray, freq: float, q: float = 12.0) -> np.ndarray:
    """Two-pole band-pass around ``freq`` (biquad, constant skirt)."""
    w0 = 2.0 * np.pi * freq / SR
    alpha = np.sin(w0) / (2.0 * q)
    b0 = alpha
    b2 = -alpha
    a0 = 1.0 + alpha
    a1 = -2.0 * np.cos(w0)
    a2 = 1.0 - alpha
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        v = (b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1 = x1, x[i]
        y2, y1 = y1, v
        y[i] = v
    return y


def normalise(x: np.ndarray, peak: float = 0.85) -> np.ndarray:
    m = float(np.max(np.abs(x))) or 1.0
    return x * (peak / m)


def loopify(x: np.ndarray, fade: float = 0.25) -> np.ndarray:
    """Make a seamless loop by cross-fading the tail over the head."""
    n = len(x)
    k = int(fade * SR)
    if k <= 0 or k * 2 >= n:
        return x
    head = x[:k].copy()
    tail = x[-k:].copy()
    ramp = np.linspace(0.0, 1.0, k, endpoint=False)
    blend = tail * (1.0 - ramp) + head * ramp
    return np.concatenate([blend, x[k:-k]])


# ------------------------------------------------------------------- sounds

def periodic_noise(n: int, seed: int, tilt: float = 1.0, hp: float = 0.0, lp: float = SR / 2) -> np.ndarray:
    """Noise built on the loop's harmonic grid, so it wraps perfectly."""
    rng = np.random.default_rng(seed)
    bins = n // 2 + 1
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    mag = np.zeros(bins)
    band = (freqs >= hp) & (freqs <= lp)
    mag[band] = 1.0 / np.maximum(freqs[band], 20.0) ** tilt
    mag[0] = 0.0
    phase = rng.uniform(0.0, 2.0 * np.pi, bins)
    spec = mag * np.exp(1j * phase)
    y = np.fft.irfft(spec, n)
    return y / (np.max(np.abs(y)) or 1.0)


def make_loop() -> np.ndarray:
    """Seamless quad-motor buzz on a 2 s harmonic grid."""
    dur = 2.0
    n = int(SR * dur)
    t = np.arange(n) / SR
    grid = 1.0 / dur  # 0.5 Hz bins: every partial below is exactly periodic

    mix = np.zeros(n)

    # Four motors, slightly detuned, with a blade-pass tremolo.
    for freq, detune, phase in ((118.0, 0.0, 0.0), (118.0, 1.5, 1.1),
                                (118.0, -1.0, 2.3), (118.0, 2.5, 3.4)):
        f = round((freq + detune) / grid) * grid
        tone = np.zeros(n)
        for harmonic, amp in ((1, 1.0), (2, 0.55), (3, 0.30), (4, 0.16), (5, 0.09), (6, 0.05)):
            tone += amp * np.sin(2 * np.pi * f * harmonic * t + phase * harmonic)
        blade = 3.0 * f
        tone *= 1.0 + 0.28 * np.sin(2 * np.pi * blade * t + phase)
        mix += tone / 4.0

    # Prop wash: band-limited periodic noise with a slow musical swell.
    wash = periodic_noise(n, 11, tilt=0.7, hp=220.0, lp=2100.0)
    wash *= 0.30 * (0.78 + 0.22 * np.sin(2 * np.pi * 3.0 * t))
    mix += wash

    # High hiss of the props and a low mechanical rumble.
    mix += periodic_noise(n, 12, tilt=0.4, hp=4200.0) * 0.06
    mix += np.sin(2 * np.pi * 52.0 * t) * 0.20

    return normalise(mix, 0.72)


def make_buzz() -> np.ndarray:
    """Short rev-up: the motors spin up and settle."""
    dur = 1.1
    n = int(SR * dur)
    t = np.arange(n) / SR
    f = 95.0 + 55.0 * np.exp(-t * 3.2) + 8.0 * np.sin(2 * np.pi * 5.0 * t)
    phase = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.25 * np.sin(3 * phase)
    tone *= envelope(n, 0.03, 2.4)
    wash = lowpass(noise(n, 21), 2600.0, 2) * envelope(n, 0.02, 3.0) * 0.25
    return normalise(tone + wash, 0.7)


def make_launch() -> np.ndarray:
    """Whoosh of the airframe being tossed out, sweeping away from the pilot."""
    dur = 1.4
    n = int(SR * dur)
    t = np.arange(n) / SR
    sweep = 900.0 * np.exp(-t * 2.2) + 180.0
    phase = 2 * np.pi * np.cumsum(sweep) / SR
    whine = np.sin(phase) * 0.35 + np.sin(1.5 * phase) * 0.12
    wash = lowpass(noise(n, 31), 3000.0, 2)
    wash = highpass(wash, 300.0, 1)
    wash *= envelope(n, 0.01, 3.2)
    whine *= envelope(n, 0.02, 2.0)
    thump = lowpass(noise(n, 32), 120.0, 2) * envelope(n, 0.005, 6.0) * 1.4
    return normalise(whine + wash * 0.8 + thump, 0.8)


def make_explode() -> np.ndarray:
    """Layered detonation: crack, body, debris tail."""
    dur = 3.0
    n = int(SR * dur)
    t = np.arange(n) / SR

    crack = highpass(noise(n, 41), 1800.0, 1) * envelope(n, 0.001, 22.0)
    body = lowpass(noise(n, 42), 380.0, 2) * envelope(n, 0.004, 3.0) * 1.6
    sub = np.sin(2 * np.pi * (58.0 - 14.0 * t) * t) * envelope(n, 0.002, 2.6) * 1.1
    debris = np.zeros(n)
    rng = np.random.default_rng(43)
    for _ in range(60):
        start = int(rng.uniform(0.05, 1.5) * SR)
        length = int(rng.uniform(0.01, 0.09) * SR)
        if start + length >= n:
            continue
        click = rng.normal(0, 1, length) * np.exp(-np.arange(length) / (length / 4))
        debris[start:start + length] += click * rng.uniform(0.1, 0.45)

    # distant "crump" resonance
    boom = resonator(noise(n, 44), 90.0, 4.0) * envelope(n, 0.02, 2.2) * 4.0

    mix = crack * 0.7 + body + sub + debris * 0.6 + boom
    mix = np.tanh(mix * 1.4)
    return normalise(mix, 0.95)


def make_beep() -> np.ndarray:
    """Clean two-tone controller blip."""
    n = int(SR * 0.28)
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * 1320.0 * t) + 0.35 * np.sin(2 * np.pi * 2640.0 * t)
    tone *= envelope(n, 0.004, 18.0, 3.0)
    second = np.zeros_like(tone)
    start = int(SR * 0.13)
    m = n - start
    t2 = np.arange(m) / SR
    second[start:] = (np.sin(2 * np.pi * 1760.0 * t2) + 0.3 * np.sin(2 * np.pi * 3520.0 * t2)) \
        * envelope(m, 0.004, 20.0, 3.0) * 0.7
    return normalise(tone + second, 0.8)


def make_static() -> np.ndarray:
    """Seamless radio static, band-limited like a cheap analogue downlink."""
    dur = 2.0
    n = int(SR * dur)
    t = np.arange(n) / SR

    hiss = periodic_noise(n, 51, tilt=0.35, hp=900.0, lp=7200.0)
    crackle = periodic_noise(n, 52, tilt=0.1, hp=2000.0, lp=9000.0)
    crackle = np.sign(crackle) * np.abs(crackle) ** 0.35 * 0.35
    hum = np.sin(2 * np.pi * 60.0 * t) * 0.05

    mix = hiss * 0.6 + crackle + hum
    # slow drop-outs give the link a "breathing" quality
    dropouts = 0.75 + 0.25 * np.sin(2 * np.pi * 1.25 * t) * np.sin(2 * np.pi * 0.5 * t)
    return normalise(mix * dropouts, 0.6)


SOUNDS = {
    "drone_loop": make_loop,
    "drone_buzz": make_buzz,
    "drone_launch": make_launch,
    "drone_explode": make_explode,
    "drone_beep": make_beep,
    "drone_static": make_static,
}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, factory in SOUNDS.items():
        data = factory().astype(np.float32)
        path = OUT / f"{name}.ogg"
        sf.write(path, data, SR, format="OGG", subtype="VORBIS")
        size = path.stat().st_size
        print(f"{path.name:20s} {len(data) / SR:5.2f}s  {size / 1024:6.1f} KiB")


if __name__ == "__main__":
    main()

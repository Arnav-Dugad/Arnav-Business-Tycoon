"""Synthesizes the game's sound effects (original, made for this game; no third-party audio).

Writes 22.05 kHz mono 16-bit WAV files to app/src/main/res/raw/. Deterministic: same output every run.
Usage: python -I tools/audio/gen_sfx.py <repo-root>
"""
import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 22050


def env(t, dur, attack=0.004, release=None):
    release = release if release is not None else dur * 0.6
    if t < attack:
        return t / attack
    return max(0.0, 1.0 - (t - attack) / max(1e-6, dur - attack)) ** (dur / max(release, 1e-6) * 0.5 + 0.5)


def tone(freqs, dur, vol=0.5, shape="sine", glide=None):
    out = []
    n = int(RATE * dur)
    for i in range(n):
        t = i / RATE
        s = 0.0
        for k, f in enumerate(freqs):
            f = f if glide is None else f * (1 + glide * t / dur)
            ph = 2 * math.pi * f * t
            s += (math.sin(ph) if shape == "sine" else (1 if math.sin(ph) > 0 else -1) * 0.4) / (k + 1)
        out.append(s * env(t, dur) * vol)
    return out


def seq(*parts, gap=0.0):
    out = []
    for p in parts:
        out += p + [0.0] * int(RATE * gap)
    return out


def noise(dur, vol=0.4, seed=7, low=0.6):
    r = random.Random(seed)
    out, prev = [], 0.0
    for i in range(int(RATE * dur)):
        prev = prev * low + r.uniform(-1, 1) * (1 - low)
        out.append(prev * env(i / RATE, dur, 0.001, dur * 0.3) * vol * 3)
    return out


def write(path, samples):
    peak = max(1e-6, max(abs(x) for x in samples))
    g = min(1.0, 0.9 / peak)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x * g)) * 32767)) for x in samples))


def main(repo: Path):
    out = repo / "app/src/main/res/raw"
    out.mkdir(parents=True, exist_ok=True)
    sounds = {
        "sfx_tap": tone([1320, 2640], 0.045, 0.35),
        "sfx_coin": seq(tone([1568, 3136], 0.07, 0.4), tone([2093, 4186], 0.16, 0.4)),
        "sfx_level": tone([660, 990], 0.12, 0.4, glide=0.5),
        "sfx_success": seq(tone([523], 0.09), tone([659], 0.09), tone([784], 0.09), tone([1047, 2094], 0.28)),
        "sfx_error": tone([180, 360], 0.18, 0.35, shape="square"),
        "sfx_bid": seq(tone([880], 0.05, 0.3), tone([1175], 0.08, 0.3)),
        "sfx_gavel": seq(noise(0.06, 0.6, 3, 0.3), tone([140, 280], 0.18, 0.5)),
    }
    total = 0
    for name, s in sounds.items():
        p = out / f"{name}.wav"
        write(p, s)
        total += p.stat().st_size
    print(f"wrote {len(sounds)} sounds to {out}, {total / 1024:.0f} KB")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

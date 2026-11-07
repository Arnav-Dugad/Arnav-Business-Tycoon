"""Synthesizes the game's four music loops (original, composed in code for this game; no third-party audio or samples).

Each track is a seamless loop: the reverb tail of the last bar is folded back onto the start. Output: Ogg Vorbis files
in app/src/main/res/raw/ (via ffmpeg). Deterministic: the same output every run.

Usage: python -I tools/audio/gen_music.py <repo-root>
"""
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

RATE = 32000
NOTE = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11}


def hz(name: str) -> float:
    """'A4' -> 440 Hz."""
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTE[pitch] + 12 * (octave + 1) - 69) / 12)


def chord(root: str, quality: str, octave: int = 3) -> list:
    steps = {"maj7": [0, 4, 7, 11], "m7": [0, 3, 7, 10], "7": [0, 4, 7, 10], "maj9": [0, 4, 7, 11, 14], "m9": [0, 3, 7, 10, 14],
             "sus2": [0, 2, 7, 12], "maj": [0, 4, 7, 12], "min": [0, 3, 7, 12], "add9": [0, 4, 7, 14], "m11": [0, 3, 7, 10, 17]}[quality]
    base = hz(f"{root}{octave}")
    return [base * 2 ** (s / 12) for s in steps]


def t_axis(dur: float) -> np.ndarray:
    return np.arange(int(dur * RATE)) / RATE


def adsr(n: int, a: float, d: float, s: float, r: float) -> np.ndarray:
    a_n, d_n, r_n = int(a * RATE), int(d * RATE), int(r * RATE)
    env = np.full(n, s)
    env[:a_n] = np.linspace(0, 1, a_n, endpoint=False) if a_n else env[:a_n]
    if d_n:
        env[a_n:a_n + d_n] = np.linspace(1, s, min(d_n, max(0, n - a_n)), endpoint=False)[: max(0, min(d_n, n - a_n))]
    if r_n and n > r_n:
        env[-r_n:] *= np.linspace(1, 0, r_n)
    return env


def epiano(f: float, dur: float, vel: float = 0.5) -> np.ndarray:
    """FM electric piano: 1:1 carrier/modulator with a fast-decaying index (the bell 'tine') and slow amplitude decay."""
    t = t_axis(dur)
    index = 2.2 * np.exp(-t * 6) + 0.3
    tone = np.sin(2 * np.pi * f * t + index * np.sin(2 * np.pi * f * t))
    tine = 0.15 * np.sin(2 * np.pi * f * 14 * t) * np.exp(-t * 30)
    env = np.exp(-t * 1.6) * adsr(len(t), 0.003, 0.0, 1.0, 0.08)
    return (tone + tine) * env * vel


def piano(f: float, dur: float, vel: float = 0.5) -> np.ndarray:
    """Additive piano-like tone: decaying harmonics, slightly inharmonic, quicker decay for higher partials."""
    t = t_axis(dur)
    out = np.zeros_like(t)
    for k, amp in enumerate([1.0, 0.55, 0.3, 0.18, 0.1, 0.06], start=1):
        fk = f * k * (1 + 0.0004 * k * k)
        out += amp * np.sin(2 * np.pi * fk * t) * np.exp(-t * (1.2 + 0.9 * k))
    return out * adsr(len(t), 0.002, 0.0, 1.0, 0.1) * vel


def lowpass(x: np.ndarray, cutoff: float) -> np.ndarray:
    """Two cascaded one-pole low-pass filters, vectorised with an FFT-domain response (fine for static cutoffs)."""
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / RATE)
    spec /= (1 + (freqs / cutoff) ** 2)
    return np.fft.irfft(spec, len(x))


def pad(freqs: list, dur: float, vel: float = 0.25, cutoff: float = 1400, rng=None) -> np.ndarray:
    """Warm pad: three detuned saws per note, low-passed, slow attack and release."""
    t = t_axis(dur)
    out = np.zeros_like(t)
    for f in freqs:
        for det in (-0.006, 0.0, 0.0065):
            ph = (f * (1 + det) * t + (rng.random() if rng is not None else 0)) % 1.0
            out += 2 * ph - 1
    out = lowpass(out / (3 * len(freqs)), cutoff)
    return out * adsr(len(t), min(1.2, dur / 3), 0.0, 1.0, min(1.5, dur / 3)) * vel


def bass(f: float, dur: float, vel: float = 0.5) -> np.ndarray:
    t = t_axis(dur)
    tone = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t) + 0.08 * np.sin(2 * np.pi * 3 * f * t)
    return tone * np.exp(-t * 2.5) * adsr(len(t), 0.004, 0.0, 1.0, 0.05) * vel


def kick(vel: float = 0.6) -> np.ndarray:
    t = t_axis(0.35)
    f = 50 + 90 * np.exp(-t * 30)
    return np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-t * 9) * vel


def hat(rng, vel: float = 0.12, length: float = 0.06) -> np.ndarray:
    t = t_axis(length)
    noise = rng.standard_normal(len(t))
    noise = noise - lowpass(noise, 6000)  # keep the highs
    return noise * np.exp(-t * 70) * vel


def shaker(rng, vel: float = 0.06) -> np.ndarray:
    t = t_axis(0.12)
    noise = rng.standard_normal(len(t))
    noise = noise - lowpass(noise, 4000)
    return noise * np.sin(np.pi * np.clip(t / 0.12, 0, 1)) * vel


def reverb_ir(seconds: float, rng, decay: float = 3.0) -> np.ndarray:
    t = t_axis(seconds)
    ir = rng.standard_normal((2, len(t))) * np.exp(-t * decay)
    ir = np.stack([lowpass(ch, 5000) for ch in ir])
    ir[:, 0] = 0
    return ir / np.sqrt((ir ** 2).sum(axis=1, keepdims=True))


class Track:
    def __init__(self, bpm: float, bars: int, seed: int, beats_per_bar: int = 4):
        self.bpm, self.bars, self.bpb = bpm, bars, beats_per_bar
        self.beat = 60.0 / bpm
        self.length = int(round(bars * beats_per_bar * self.beat * RATE))
        self.tail = int(4.0 * RATE)
        self.dry = np.zeros((2, self.length + self.tail))
        self.send = np.zeros((2, self.length + self.tail))
        self.rng = np.random.default_rng(seed)

    def add(self, sig: np.ndarray, beat: float, pan: float = 0.0, wet: float = 0.3, swing: float = 0.0):
        """Places [sig] at [beat] (in beats from the loop start). pan -1..1; wet = reverb send."""
        if swing and (beat * 2) % 2 == 1:
            beat += swing
        start = int(round(beat * self.beat * RATE))
        end = min(start + len(sig), self.dry.shape[1])
        if start >= end:
            return
        seg = sig[: end - start]
        left, right = np.sqrt((1 - pan) / 2), np.sqrt((1 + pan) / 2)
        for ch, g in ((0, left), (1, right)):
            self.dry[ch, start:end] += seg * g
            self.send[ch, start:end] += seg * g * wet

    def render(self, ir_seconds: float = 2.8, decay: float = 2.6, gain: float = 0.9) -> np.ndarray:
        ir = reverb_ir(ir_seconds, self.rng, decay)
        n = self.dry.shape[1] + ir.shape[1]
        size = 1 << (n - 1).bit_length()
        wet = np.stack([np.fft.irfft(np.fft.rfft(self.send[c], size) * np.fft.rfft(ir[c], size), size)[:n] for c in range(2)])
        mix = np.zeros((2, n))
        mix[:, : self.dry.shape[1]] += self.dry
        mix += wet * 0.55
        # Fold everything past the loop end back onto the start: a seamless loop.
        loop = mix[:, : self.length].copy()
        rest = mix[:, self.length:]
        for k in range(0, rest.shape[1], self.length):
            chunk = rest[:, k:k + self.length]
            loop[:, : chunk.shape[1]] += chunk
        loop -= loop.mean(axis=1, keepdims=True)
        # Gentle soft-clip limiter, then normalise to -1 dBFS.
        peak = np.abs(loop).max()
        loop = np.tanh(loop / peak * 1.4) / np.tanh(1.4)
        return loop * gain * 10 ** (-1 / 20)


def empire() -> np.ndarray:
    """'Corner Office' — warm lounge jazz in F, 88 BPM. ii–V–I–vi turnarounds with an electric piano and walking bass."""
    tr = Track(88, 16, seed=11)
    prog = [("G", "m9"), ("C", "7"), ("F", "maj9"), ("D", "m7"), ("G", "m7"), ("C", "7"), ("A", "m7"), ("D", "7"),
            ("Bb", "maj7"), ("A", "m7"), ("G", "m9"), ("C", "7"), ("F", "maj7"), ("D", "m9"), ("G", "m7"), ("C", "7")]
    for bar, (root, q) in enumerate(prog):
        b0 = bar * 4
        notes = chord(root, q, 3)
        for hit in (0, 1.5, 2.5) if bar % 2 == 0 else (0, 2, 3.5):
            for i, f in enumerate(notes):
                tr.add(epiano(f, 1.6, 0.16 + 0.02 * (i == len(notes) - 1)), b0 + hit + 0.012 * i, pan=-0.25 + 0.15 * i, wet=0.35, swing=0.08)
        tr.add(pad(chord(root, q, 3)[:3], 4 * tr.beat, 0.06, 900, tr.rng), b0, pan=0.0, wet=0.6)
        root_f = hz(f"{root}2")
        walk = [root_f, root_f * 2 ** (7 / 12), root_f * 2 ** (3 / 12 if "m" in q else 4 / 12), root_f * 2 ** (10 / 12) / 2 * 2 ** (1 / 12)]
        for k, f in enumerate(walk):
            tr.add(bass(f, tr.beat * 0.95, 0.42), b0 + k, pan=0.0, wet=0.08)
        for k in range(8):
            tr.add(hat(tr.rng, 0.05 if k % 2 else 0.08), b0 + k * 0.5, pan=0.35, wet=0.1, swing=0.08)
        tr.add(kick(0.32), b0, wet=0.05)
        tr.add(kick(0.22), b0 + 2.5, wet=0.05)
    # A short melody on top in bars 9-16.
    melody = [("A4", 32, 1.5), ("C5", 33.5, 0.5), ("D5", 34, 2), ("F5", 36, 1), ("E5", 37, 1), ("D5", 38, 2), ("C5", 40, 1.5),
              ("A4", 41.5, 0.5), ("G4", 42, 2), ("Bb4", 44, 1), ("A4", 45, 1), ("F4", 46, 2), ("G4", 48, 1.5), ("A4", 49.5, 0.5),
              ("C5", 50, 2), ("D5", 52, 1), ("C5", 53, 1), ("A4", 54, 2), ("F4", 56, 3), ("E4", 59, 1), ("F4", 60, 4)]
    for n, beat, length in melody:
        tr.add(epiano(hz(n), length * tr.beat + 0.6, 0.22), beat, pan=0.2, wet=0.4, swing=0.08)
    return tr.render()


def markets() -> np.ndarray:
    """'Ticker' — a pulsing minor groove in D minor, 112 BPM: arpeggiated keys, offbeat hats, a steady pulse."""
    tr = Track(112, 16, seed=23)
    prog = [("D", "m9"), ("D", "m9"), ("Bb", "maj7"), ("Bb", "maj7"), ("G", "m7"), ("G", "m7"), ("A", "7"), ("A", "7")] * 2
    for bar, (root, q) in enumerate(prog):
        b0 = bar * 4
        notes = chord(root, q, 4)
        pattern = [0, 2, 1, 3, 2, 1, 3, 2] if bar % 4 < 2 else [0, 1, 2, 3, 2, 3, 1, 2]
        for k in range(16):
            f = notes[pattern[k % 8] % len(notes)] * (2 if k % 8 == 7 else 1)
            tr.add(epiano(f, 0.5, 0.12 if k % 4 else 0.16), b0 + k * 0.25, pan=0.3 * np.sin(k), wet=0.3)
        tr.add(pad(chord(root, q, 3)[:3], 4 * tr.beat, 0.07, 1100, tr.rng), b0, wet=0.5)
        root_f = hz(f"{root}2")
        for k in range(8):
            tr.add(bass(root_f * (2 if k in (3, 7) else 1), tr.beat * 0.45, 0.34), b0 + k * 0.5, wet=0.05)
        for k in range(4):
            tr.add(kick(0.42), b0 + k, wet=0.04)
            tr.add(hat(tr.rng, 0.09), b0 + k + 0.5, pan=0.3, wet=0.1)
        for k in range(16):
            tr.add(shaker(tr.rng, 0.03 + 0.015 * (k % 2)), b0 + k * 0.25, pan=-0.35, wet=0.1)
    return tr.render(ir_seconds=2.2, decay=3.2)


def estates() -> np.ndarray:
    """'Skyline at Dusk' — slow, spacious pads and soft piano in E major, 66 BPM."""
    tr = Track(66, 12, seed=37)
    prog = [("E", "maj9"), ("C#", "m9"), ("A", "maj7"), ("B", "sus2"), ("E", "maj7"), ("G#", "m7"), ("A", "add9"), ("B", "sus2"),
            ("C#", "m11"), ("A", "maj9"), ("F#", "m9"), ("B", "sus2")]
    for bar, (root, q) in enumerate(prog):
        b0 = bar * 4
        tr.add(pad(chord(root, q, 3), 4 * tr.beat + 1.0, 0.16, 1200, tr.rng), b0, wet=0.7)
        tr.add(bass(hz(f"{root}2"), 4 * tr.beat, 0.25), b0, wet=0.2)
        notes = chord(root, q, 4)
        for k, beat in enumerate((0.5, 1.5, 2.25, 3.0)):
            tr.add(piano(notes[(k * 2 + bar) % len(notes)], 3.0, 0.14), b0 + beat, pan=0.4 * np.cos(bar + k), wet=0.6)
    return tr.render(ir_seconds=3.6, decay=1.8)


def dynasty() -> np.ndarray:
    """'Crown and Legacy' — a stately piano theme over strings in Bb major, 76 BPM, 3/4 time."""
    tr = Track(76, 16, seed=41, beats_per_bar=3)
    prog = [("Bb", "maj"), ("G", "min"), ("Eb", "maj"), ("F", "sus2"), ("Bb", "maj"), ("D", "min"), ("Eb", "maj"), ("F", "maj"),
            ("G", "min"), ("Eb", "maj"), ("Bb", "maj"), ("F", "maj"), ("Eb", "maj7"), ("F", "sus2"), ("Bb", "add9"), ("F", "maj")]
    melody = ["D5", "F5", "Eb5", "C5", "D5", "A4", "Bb4", "C5", "D5", "Eb5", "F5", "C5", "G5", "F5", "D5", "C5"]
    for bar, (root, q) in enumerate(prog):
        b0 = bar * 3
        tr.add(pad(chord(root, q, 3), 3 * tr.beat + 0.8, 0.13, 1600, tr.rng), b0, wet=0.6)
        tr.add(piano(hz(f"{root}2"), 2.5, 0.3), b0, pan=-0.2, wet=0.35)
        notes = chord(root, q, 3)
        for k in (1, 2):
            for i, f in enumerate(notes[1:3]):
                tr.add(piano(f, 1.4, 0.12), b0 + k + 0.01 * i, pan=-0.1, wet=0.35)
        tr.add(piano(hz(melody[bar]), 2.6, 0.26), b0, pan=0.15, wet=0.45)
        if bar % 2 == 1:
            tr.add(piano(hz(melody[(bar + 1) % 16]) * 2 ** (-2 / 12), 1.2, 0.16), b0 + 2, pan=0.15, wet=0.45)
    return tr.render(ir_seconds=3.2, decay=2.0)


TRACKS = {"music_empire": empire, "music_markets": markets, "music_estates": estates, "music_dynasty": dynasty}


def main(repo: Path) -> None:
    out_dir = repo / "app/src/main/res/raw"
    out_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in TRACKS.items():
            audio = fn()
            pcm = (np.clip(audio.T, -1, 1) * 32767).astype("<i2")
            wav = Path(tmp) / f"{name}.wav"
            with wave.open(str(wav), "wb") as w:
                w.setnchannels(2)
                w.setsampwidth(2)
                w.setframerate(RATE)
                w.writeframes(pcm.tobytes())
            ogg = out_dir / f"{name}.ogg"
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "2", "-map_metadata", "-1", str(ogg)], check=True)
            print(f"{ogg.name}: {pcm.shape[0] / RATE:.1f} s, {ogg.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

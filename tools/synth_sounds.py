"""Synthesizes the GLASS sounds: a "nano" family of granular sparkles, filtered noise, glassy bells and soft reverb.

Usage: python tools/synth_sounds.py src/main/resources/assets/generallaymansaestheticspyingscreen/sounds
(needs numpy, soundfile and ffmpeg with libvorbis)."""
import os, subprocess, sys
import numpy as np
import soundfile as sf

SR = 44100
OUT = sys.argv[1]
rng = np.random.default_rng(7)


def t(dur):
    return np.arange(int(dur * SR)) / SR


def mix(*signals):
    n = max(len(x) for x in signals)
    return sum(np.pad(x, (0, n - len(x))) for x in signals)


def silence(dur):
    return np.zeros(int(dur * SR))


def place(buf, sig, at):
    i = int(at * SR)
    if i >= len(buf):
        return
    n = min(len(sig), len(buf) - i)
    buf[i:i + n] += sig[:n]


def sine_sweep(f0, f1, dur, curve='exp'):
    n = int(dur * SR)
    x = np.linspace(0, 1, n)
    f = f0 * (f1 / f0) ** x if curve == 'exp' else f0 + (f1 - f0) * x
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def env_adsr(n, a, d, s, r, sr=SR):
    a, d, r = int(a * sr), int(d * sr), int(r * sr)
    sus = max(0, n - a - d - r)
    e = np.concatenate([np.linspace(0, 1, a, endpoint=False), np.linspace(1, s, d, endpoint=False), np.full(sus, s), np.linspace(s, 0, r)])
    return np.pad(e, (0, max(0, n - len(e))))[:n]


def decay(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def svf(x, cutoff, q=0.7, mode='bp'):
    """Topology-preserving state variable filter (stable at any cutoff); cutoff may be an array (a sweep)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    g = np.tan(np.pi * np.clip(cutoff, 10, SR * 0.45) / SR)
    k = 1.0 / q
    a1 = 1.0 / (1.0 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    ic1 = ic2 = 0.0
    out = np.empty_like(x)
    for i in range(len(x)):
        v3 = x[i] - ic2
        v1 = a1[i] * ic1 + a2[i] * v3
        v2 = ic2 + a2[i] * ic1 + a3[i] * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        out[i] = v1 if mode == 'bp' else (v2 if mode == 'lp' else x[i] - k * v1 - v2)
    return out


def noise(dur):
    return rng.standard_normal(int(dur * SR))


def pink(dur):
    n = int(dur * SR)
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec /= np.sqrt(np.maximum(f, 20))
    p = np.fft.irfft(spec, n)
    return p / (np.abs(p).max() + 1e-9)


def bell(freq, dur, tau=0.4, partials=((1, 1.0), (2.76, 0.42), (5.40, 0.18), (8.93, 0.07))):
    x = t(dur)
    s = sum(a * np.sin(2 * np.pi * freq * r * x) * np.exp(-x / (tau / (1 + 0.6 * (r - 1)))) for r, a in partials)
    attack = np.minimum(1, x / 0.003)
    return s * attack


def grain(freq, dur, detune=0.0):
    x = t(dur)
    w = np.sin(np.pi * np.arange(len(x)) / max(1, len(x))) ** 2
    return np.sin(2 * np.pi * freq * (1 + detune) * x) * w


def reverb(x, size=1.6, mix=0.3, tone=5000):
    n = int(size * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR / (size / 5))
    ir = svf(ir, tone, 0.6, 'lp')
    ir[:int(0.012 * SR)] *= np.linspace(0, 1, int(0.012 * SR))
    ir /= np.sqrt(np.sum(ir ** 2))
    m = len(x) + n
    wet = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)
    dry = np.pad(x, (0, n))
    return dry * (1 - mix) + wet * mix * 1.6


def finish(x, name, peak=0.89, tail=0.02):
    x = x - np.mean(x)
    # trim trailing silence
    thr = np.abs(x).max() * 0.004
    idx = np.where(np.abs(x) > thr)[0]
    if len(idx):
        x = x[:idx[-1] + int(tail * SR)]
    fade = int(0.01 * SR)
    x[-fade:] *= np.linspace(1, 0, fade)
    x[:64] *= np.linspace(0, 1, 64)
    x = x / (np.abs(x).max() + 1e-9) * peak
    wav = os.path.join(OUT, name + '.wav')
    ogg = os.path.join(OUT, name + '.ogg')
    sf.write(wav, x.astype(np.float32), SR)
    subprocess.check_call(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', ogg])
    os.remove(wav)
    print(name, '%.2fs' % (len(x) / SR))


def sparkle(dur, f_from, f_to, count, amp_curve=None, spread=0.45, glen=(0.02, 0.06)):
    buf = silence(dur + 0.1)
    for i in range(count):
        pos = rng.random()
        at = pos * dur
        f = f_from * (f_to / f_from) ** pos * 2 ** (rng.uniform(-spread, spread))
        g = grain(f, rng.uniform(*glen), rng.uniform(-0.01, 0.01))
        a = amp_curve(pos) if amp_curve else 1.0
        place(buf, g * a * rng.uniform(0.4, 1.0), at)
    return buf


# ---- The screens ----

def nano_assemble():
    dur = 1.7
    buf = silence(dur)
    place(buf, sparkle(1.25, 500, 5200, 170, lambda p: np.sin(np.pi * min(1, p * 1.1)) ** 0.7) * 0.5, 0.0)
    n = noise(1.3)
    sweep = 300 * (6000 / 300) ** np.linspace(0, 1, len(n))
    whoosh = svf(n, sweep, 2.2, 'bp') * env_adsr(len(n), 0.9, 0.25, 0.0, 0.15)
    place(buf, whoosh * 0.35, 0.0)
    lock = mix(bell(1318.5, 0.6, 0.25) * 0.5, bell(1975.5, 0.5, 0.2) * 0.3)
    place(buf, lock, 1.18)
    place(buf, sine_sweep(90, 60, 0.25) * decay(int(0.25 * SR), 0.07) * 0.5, 1.18)
    finish(reverb(buf, 1.4, 0.28), 'nano_assemble')


def nano_dissolve():
    dur = 1.5
    buf = silence(dur)
    place(buf, bell(987.8, 0.45, 0.18) * 0.35, 0.0)
    place(buf, sparkle(1.2, 5200, 450, 150, lambda p: (1 - p) ** 0.6) * 0.5, 0.05)
    n = noise(1.2)
    sweep = 6000 * (250 / 6000) ** np.linspace(0, 1, len(n))
    place(buf, svf(n, sweep, 2.0, 'bp') * env_adsr(len(n), 0.05, 0.2, 0.6, 0.9) * 0.32, 0.05)
    finish(reverb(buf, 1.4, 0.3), 'nano_dissolve')


# ---- The cloak ----

def cloak_engage():
    dur = 2.4
    buf = silence(dur)
    x = t(dur)
    sub = sine_sweep(70, 38, dur) * env_adsr(len(x), 0.4, 0.6, 0.6, 1.2) * 0.6
    place(buf, sub, 0)
    # bending light: a cluster of detuned high sines, slowly phasing, fading in then out
    shimmer = sum(np.sin(2 * np.pi * f * x + rng.uniform(0, 6)) * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(0.4, 1.4) * x))
                  for f in (1800, 1812, 2410, 2432, 3215, 3240)) / 6
    place(buf, shimmer * env_adsr(len(x), 0.6, 0.5, 0.5, 1.0) * 0.25, 0)
    # the air pulled in
    n = pink(1.6)
    place(buf, svf(n, 900 * (180 / 900) ** np.linspace(0, 1, len(n)), 0.8, 'lp') * env_adsr(len(n), 1.2, 0.1, 0.0, 0.3) * 0.7, 0.2)
    place(buf, sparkle(1.6, 4000, 1200, 70, lambda p: np.sin(np.pi * p)) * 0.25, 0.3)
    finish(reverb(buf, 2.0, 0.35, 3500), 'cloak_engage')


def field_rise():
    dur = 2.3
    buf = silence(dur)
    x = t(dur)
    rumble = svf(noise(dur), 160, 0.7, 'lp') * env_adsr(len(x), 1.4, 0.3, 0.3, 0.6)
    place(buf, rumble * 2.2, 0)
    tone = sum(sine_sweep(80 * k, 240 * k, 2.0) / k for k in (1, 2, 3, 4))
    place(buf, tone * env_adsr(int(2.0 * SR), 0.6, 0.6, 0.5, 0.8) * 0.25, 0)
    # electric crackle, denser as the walls go up
    crackle = silence(2.0)
    for i in range(240):
        pos = rng.random() ** 0.6
        burst = noise(rng.uniform(0.002, 0.008)) * rng.uniform(0.2, 1)
        place(crackle, burst, pos * 1.9)
    place(buf, svf(crackle, 5000, 1.2, 'hp') * 0.18, 0.1)
    # the seal at the top
    seal = svf(noise(0.35), 7000, 0.8, 'hp') * decay(int(0.35 * SR), 0.08)
    place(buf, seal * 0.4, 1.95)
    place(buf, mix(bell(659.3, 0.8, 0.35) * 0.3, bell(987.8, 0.7, 0.3) * 0.2), 1.95)
    finish(reverb(buf, 1.8, 0.3, 4000), 'field_rise')


# ---- The holo cube ----

def scan_start():
    dur = 1.5
    buf = silence(dur)
    up = sine_sweep(180, 1400, 0.55)
    fm = np.sin(2 * np.pi * np.cumsum(180 * (1400 / 180) ** np.linspace(0, 1, len(up)) + 30 * np.sin(2 * np.pi * 18 * t(0.55))) / SR)
    place(buf, (up * 0.5 + fm * 0.5) * env_adsr(len(up), 0.05, 0.2, 0.8, 0.12) * 0.4, 0)
    place(buf, svf(noise(0.02), 3000, 1, 'bp') * 0.6, 0.55)
    ping = bell(1567.98, 1.0, 0.45, ((1, 1.0), (2.0, 0.25), (3.01, 0.08)))
    place(buf, ping * 0.6, 0.56)
    finish(reverb(buf, 1.8, 0.4, 5000), 'scan_start')


def scan_sweep():
    dur = 1.0
    buf = silence(dur)
    ping = bell(1174.7, 0.8, 0.28, ((1, 1.0), (1.5, 0.3), (2.0, 0.15)))
    place(buf, ping * 0.5, 0.0)
    place(buf, sparkle(0.45, 2500, 6000, 18, lambda p: 1 - p, 0.2, (0.008, 0.02)) * 0.25, 0.08)
    finish(reverb(buf, 1.6, 0.45, 4500), 'scan_sweep')


def scan_complete():
    dur = 1.8
    buf = silence(dur)
    for i, f in enumerate((880.0, 1108.7, 1318.5, 1760.0)):
        place(buf, bell(f, 1.2, 0.5) * (0.5 if i < 3 else 0.4), i * 0.085)
    place(buf, sparkle(0.8, 3000, 7000, 50, lambda p: 1 - p) * 0.2, 0.3)
    finish(reverb(buf, 2.0, 0.4, 6000), 'scan_complete')


def sim_open():
    dur = 3.0
    buf = silence(dur)
    x = t(dur)
    chord = (220.0, 277.18, 329.63, 440.0, 554.37)
    pad = sum(np.sin(2 * np.pi * f * (1 + 0.003 * np.sin(2 * np.pi * 4.5 * x)) * x + k) + 0.5 * np.sin(2 * np.pi * f * 1.003 * x)
              for k, f in enumerate(chord)) / len(chord)
    place(buf, pad * env_adsr(len(x), 1.1, 0.5, 0.6, 1.2) * 0.45, 0)
    n = pink(2.0)
    place(buf, svf(n, 200 * (5000 / 200) ** np.linspace(0, 1, len(n)), 1.4, 'bp') * env_adsr(len(n), 1.4, 0.2, 0.0, 0.4) * 1.2, 0)
    place(buf, sparkle(1.6, 1500, 6000, 120, lambda p: np.sin(np.pi * p)) * 0.3, 0.4)
    place(buf, bell(880.0, 1.4, 0.6) * 0.35, 1.45)
    finish(reverb(buf, 2.6, 0.45, 4500), 'sim_open')


def sim_close():
    dur = 2.5
    buf = silence(dur)
    x = t(dur)
    bend = 1 - 0.18 * np.linspace(0, 1, len(x)) ** 2
    chord = (220.0, 277.18, 329.63, 440.0)
    pad = sum(np.sin(2 * np.pi * np.cumsum(f * bend) / SR + k) for k, f in enumerate(chord)) / len(chord)
    place(buf, pad * env_adsr(len(x), 0.08, 0.4, 0.5, 1.6) * 0.45, 0)
    n = pink(1.8)
    place(buf, svf(n, 5000 * (180 / 5000) ** np.linspace(0, 1, len(n)), 1.4, 'bp') * env_adsr(len(n), 0.1, 0.3, 0.5, 1.2) * 1.1, 0)
    place(buf, sparkle(1.4, 6000, 900, 90, lambda p: 1 - p) * 0.28, 0.05)
    finish(reverb(buf, 2.4, 0.45, 4000), 'sim_close')


# ---- Little things ----

def ui_click():
    buf = silence(0.15)
    place(buf, bell(2349.3, 0.12, 0.03, ((1, 1.0), (2.0, 0.2))) * 0.8, 0)
    place(buf, svf(noise(0.006), 6000, 1, 'bp') * 0.3, 0)
    finish(reverb(buf, 0.4, 0.15, 7000), 'ui_click', peak=0.7)


def ui_open():
    buf = silence(0.5)
    place(buf, sine_sweep(700, 1500, 0.08) * env_adsr(int(0.08 * SR), 0.005, 0.03, 0.6, 0.03) * 0.4, 0)
    place(buf, bell(1760.0, 0.3, 0.08) * 0.5, 0.07)
    place(buf, bell(2637.0, 0.3, 0.07) * 0.35, 0.13)
    finish(reverb(buf, 0.8, 0.25, 6000), 'ui_open', peak=0.7)


def ripple():
    buf = silence(0.9)
    x = t(0.45)
    glide = np.sin(2 * np.pi * np.cumsum(700 * (330 / 700) ** np.linspace(0, 1, len(x)) * (1 + 0.04 * np.sin(2 * np.pi * 22 * x))) / SR)
    place(buf, glide * env_adsr(len(x), 0.01, 0.1, 0.5, 0.3) * 0.5, 0)
    place(buf, sparkle(0.4, 3000, 1500, 25, lambda p: 1 - p) * 0.25, 0.02)
    finish(reverb(buf, 1.0, 0.35, 5000), 'ripple', peak=0.75)


def alert():
    """Motion alert: two-tone pulses, a little buzzy, with a glassy tail."""
    buf = silence(1.3)
    for i, f in enumerate((1046.5, 784.0, 1046.5, 784.0)):
        x = t(0.16)
        tone = np.sign(np.sin(2 * np.pi * f * x)) * 0.25 + np.sin(2 * np.pi * f * x) * 0.6 + np.sin(2 * np.pi * f * 2 * x) * 0.15
        tone = svf(tone, 3200, 0.8, 'lp') * env_adsr(len(x), 0.005, 0.03, 0.8, 0.04)
        place(buf, tone * 0.6, i * 0.19)
    place(buf, bell(1567.98, 0.6, 0.2) * 0.25, 0.76)
    finish(reverb(buf, 1.0, 0.25, 5000), 'alert', peak=0.8)


ONLY = sys.argv[2:] if len(sys.argv) > 2 else None

os.makedirs(OUT, exist_ok=True)
for f in (nano_assemble, nano_dissolve, cloak_engage, field_rise, scan_start, scan_sweep, scan_complete, sim_open, sim_close, ui_click, ui_open, ripple, alert):
    if ONLY is None or f.__name__ in ONLY:
        f()

"""Efectos de sonido de dibujos animados, sintetizados por código (sin derechos de autor).

Uso: python3 sfx.py  -> genera sfx/*.wav
"""
import os
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sfx')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)


def T(d):
    return np.arange(int(SR * d)) / SR


def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], 'bandpass', fs=SR, output='sos'), x)


def lp(x, f, order=2):
    return sosfilt(butter(order, f, 'lowpass', fs=SR, output='sos'), x)


def hp(x, f, order=2):
    return sosfilt(butter(order, f, 'highpass', fs=SR, output='sos'), x)


def sweep(f0, f1, d, curve=1.0):
    t = T(d)
    f = f0 + (f1 - f0) * (t / d) ** curve
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def saw(freq):  # freq: array de frecuencias por muestra
    ph = np.cumsum(freq) / SR
    return 2 * (ph % 1) - 1


def fade(x, a=0.005, b=0.02):
    n = len(x)
    e = np.ones(n)
    ia, ib = int(a * SR), int(b * SR)
    if ia: e[:ia] = np.linspace(0, 1, ia)
    if ib: e[-ib:] = np.linspace(1, 0, ib)
    return x * e


def save(name, x, peak=0.9):
    x = fade(np.asarray(x, dtype=float))
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    wavfile.write(os.path.join(OUT, name + '.wav'), SR, (x * 32767).astype(np.int16))


# pop: burbuja que aparece
t = T(0.09)
save('pop', sweep(1000, 280, 0.09, 0.6) * np.exp(-t * 40))

# whoosh: aire que pasa
t = T(0.5)
n = rng.standard_normal(len(t))
env = np.sin(np.pi * t / 0.5) ** 2
out = np.zeros_like(n)
for i, (lo, hi) in enumerate([(300, 900), (700, 2000), (1500, 4000), (700, 2000), (300, 900)]):
    seg = np.exp(-((t - 0.1 * i - 0.05) / 0.08) ** 2)
    out += bp(n, lo, hi) * seg
save('whoosh', out * env)

# ding: campanita
t = T(1.3)
save('ding', sum(a * np.sin(2 * np.pi * 1320 * m * t) * np.exp(-t * k) for a, m, k in [(1, 1, 3.5), (.5, 2.76, 6), (.25, 5.4, 9)]))

# fail: trombón triste «wah wah wah waaah»
notes = [(196, .30), (185, .30), (174.6, .30), (164.8, 1.0)]
parts = []
for i, (f, d) in enumerate(notes):
    t = T(d)
    vib = 1 + (0.012 * np.sin(2 * np.pi * 5.5 * t) * (t / d) if i == 3 else 0)
    x = saw(np.full(len(t), f) * vib)
    wah = 600 + 1600 * np.clip(np.sin(np.pi * np.minimum(t / min(d, .3), 1)), 0, 1)
    y = np.zeros_like(x)
    for j in range(0, len(x), 512):  # filtro que se abre y cierra (efecto «wah»)
        y[j:j + 512] = lp(x[j:j + 512], float(wah[j]), 1)
    env = np.minimum(1, t / .03) * (np.exp(-t * 1.2) if i == 3 else np.minimum(1, (d - t) / .05))
    parts.append(y * env)
save('fail', np.concatenate(parts), 0.8)

# stamp: golpe de sello
t = T(0.35)
save('stamp', np.sin(2 * np.pi * 70 * t) * np.exp(-t * 14) + 0.6 * lp(rng.standard_normal(len(t)), 1200) * np.exp(-t * 45))

# sparkle: brillitos
x = np.zeros(int(SR * 0.9))
for i, f in enumerate([2093, 2637, 3136, 4186, 3520, 4699, 5274, 6272]):
    s0 = int(i * 0.06 * SR)
    t = T(0.45)
    x[s0:s0 + len(t)] += np.sin(2 * np.pi * f * t) * np.exp(-t * 10) * 0.6
save('sparkle', x, 0.7)

# crack: corazón que se rompe
t = T(0.6)
cr = np.zeros(len(t))
for c in [0.0, 0.05, 0.09, 0.16]:
    i = int(c * SR)
    cr[i:i + 900] += hp(rng.standard_normal(900), 2000) * np.exp(-np.arange(900) / 150)
save('crack', cr + 0.5 * sweep(700, 150, 0.6, 0.7) * np.exp(-t * 4))

# buzz: zumbido de moscas (6 s)
t = T(6)
f = 210 + 25 * np.sin(2 * np.pi * 0.7 * t) + 15 * np.sin(2 * np.pi * 2.3 * t)
am = 0.55 + 0.45 * np.sin(2 * np.pi * 0.9 * t) * np.sin(2 * np.pi * 0.37 * t + 1)
save('buzz', bp(saw(f), 250, 3000) * am, 0.6)

# burp: eructo
t = T(0.75)
f0 = 75 - 25 * (t / 0.75) + 6 * rng.standard_normal(len(t)).cumsum() / np.sqrt(np.arange(1, len(t) + 1)) * 0.3
pulses = (saw(f0) > 0.92).astype(float)
pulses += 0.15 * rng.standard_normal(len(t))
y = bp(pulses, 300, 500, 2) * 1.0 + bp(pulses, 700, 1100, 2) * 0.6 + lp(pulses, 250) * 0.8
env = np.minimum(1, t / 0.05) * np.exp(-((t - 0.25) / 0.45) ** 2)
save('burp', y * env)

# tick: contador
t = T(0.04)
save('tick', np.sin(2 * np.pi * 2200 * t) * np.exp(-t * 120), 0.6)

# boing: muelle
t = T(0.7)
f = 260 + 180 * np.sin(2 * np.pi * 11 * t) * np.exp(-t * 4) + 120 * t
save('boing', np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 4.5))

# impact: golpe grave
t = T(0.8)
save('impact', np.sin(2 * np.pi * np.cumsum(90 * np.exp(-t * 3) + 40) / SR) * np.exp(-t * 5) + 0.4 * lp(rng.standard_normal(len(t)), 600) * np.exp(-t * 18))

# magic: subida mágica
x = np.zeros(int(SR * 1.2))
for i, f in enumerate([523, 659, 784, 1047, 1319, 1568, 2093]):
    s0 = int(i * 0.07 * SR)
    t = T(0.7)
    x[s0:s0 + len(t)] += np.sin(2 * np.pi * f * t) * np.exp(-t * 5) * 0.5
t = T(1.2)
x += 0.15 * hp(rng.standard_normal(len(t)), 6000) * np.sin(np.pi * t / 1.2)
save('magic', x, 0.7)

# water: ducha corriendo (4 s)
t = T(4)
n = rng.standard_normal(len(t))
save('water', bp(n, 1500, 7000) * (0.8 + 0.2 * np.sin(2 * np.pi * 3 * t)) + 0.3 * bp(n, 300, 900), 0.6)

# glug: desagüe atascado
x = np.zeros(int(SR * 1.4))
for i in range(7):
    t = T(0.16)
    s0 = int((0.05 + i * 0.18 + 0.03 * rng.standard_normal()) * SR)
    f0 = 140 + 60 * rng.random()
    b = np.sin(2 * np.pi * np.cumsum(f0 + 500 * (t / 0.16) ** 2) / SR) * np.exp(-t * 18)
    x[s0:s0 + len(t)] += b[:len(x[s0:s0 + len(t)])]
save('glug', lp(x, 1800))

# pfff: bote vacío que solo echa aire
t = T(0.6)
flutter = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * (38 + 20 * t) * t))
save('pfff', (bp(rng.standard_normal(len(t)), 200, 2500) * 0.7 + 0.5 * saw(np.full(len(t), 95.0))) * flutter * np.sin(np.pi * t / 0.6) ** 0.5, 0.8)

# chatter: dientes castañeteando
x = np.zeros(int(SR * 1.2))
for i in range(22):
    s0 = int(i * 0.052 * SR)
    t = T(0.025)
    x[s0:s0 + len(t)] += np.sin(2 * np.pi * (2600 + 400 * (i % 2)) * t) * np.exp(-t * 200)
save('chatter', x, 0.7)

# whistle: silbato de entrenador
t = T(0.7)
save('whistle', np.sin(2 * np.pi * 2700 * t) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 28 * t))) * np.minimum(1, t / 0.02) * np.minimum(1, (0.7 - t) / 0.08), 0.6)

# clank: discos de pesas que chocan
t = T(0.6)
save('clank', sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t * k) for a, f, k in [(1, 523, 9), (.7, 1371, 12), (.5, 2410, 16), (.3, 3690, 22)]) + 0.3 * hp(rng.standard_normal(len(t)), 3000) * np.exp(-t * 60))

# horn: claxon de taxi
t = T(0.55)
sq = lambda f: np.sign(np.sin(2 * np.pi * f * t))
save('horn', lp(sq(392) + sq(494), 3000) * np.minimum(1, t / 0.01) * np.minimum(1, (0.55 - t) / 0.03), 0.6)

# shh: ¡chist!
t = T(0.7)
save('shh', bp(rng.standard_normal(len(t)), 2500, 7000) * np.sin(np.pi * t / 0.7) ** 0.6, 0.6)

# snip: tijeretazo
x = np.zeros(int(SR * 0.3))
for c0 in (0.0, 0.11):
    i = int(c0 * SR); n = 1600
    x[i:i + n] += hp(rng.standard_normal(n), 3500) * np.exp(-np.arange(n) / 250)
save('snip', x, 0.7)

print('Efectos:', ', '.join(sorted(f[:-4] for f in os.listdir(OUT))))

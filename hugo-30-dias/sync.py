"""Saca de la voz (MP3 de ElevenLabs) cuándo empieza y acaba cada frase del guion
y lo guarda en timings-NN.json, que usa render.js para cuadrar escenas y subtítulos.

Detecta los tramos con voz (entre silencios) y los reparte entre las frases del
guion de scene.js de forma que la duración de cada frase encaje con su longitud.
Las pausas dentro de una frase se guardan como «partes» para cuadrar las palabras.

Uso: python3 sync.py 2 [voz.mp3]   (por defecto, audio/voz-capitulo-02.mp3)
"""
import json, os, re, subprocess, sys

here = os.path.dirname(os.path.abspath(__file__))
EPN = sys.argv[1].zfill(2)
audio = sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, 'audio', f'voz-capitulo-{EPN}.mp3')
scene = open(os.path.join(here, f'ep{EPN}.js'), encoding='utf-8').read()
block = scene[scene.index('const LINES'):scene.index('];', scene.index('const LINES'))]
texts = re.findall(r"text: '([^']*)'", block)
N = len(texts)

dur = float(subprocess.check_output(
    ['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', audio]).decode())
log = subprocess.run(['ffmpeg', '-i', audio, '-af', 'silencedetect=noise=-38dB:d=0.12', '-f', 'null', '-'],
                     capture_output=True, text=True).stderr
starts = [float(x) for x in re.findall(r'silence_start: ([\d.]+)', log)]
ends = [float(x) for x in re.findall(r'silence_end: ([\d.]+)', log)]

segs, cur = [], 0.0
for s, e in zip(starts, ends + [dur] * (len(starts) - len(ends))):
    if s - cur > 0.08:
        segs.append((cur, s))
    cur = e
if dur - cur > 0.08:
    segs.append((cur, dur))
M = len(segs)
if M < N:
    sys.exit(f'Solo hay {M} tramos de voz para {N} frases: genera el audio con pausas entre frases.')

# reparto óptimo: frases consecutivas <- grupos consecutivos de tramos
w = [len(t) + 4 for t in texts]
speech = sum(b - a for a, b in segs)
exp = [speech * x / sum(w) for x in w]
INF = float('inf')
best = [[INF] * (M + 1) for _ in range(N + 1)]
prev = [[0] * (M + 1) for _ in range(N + 1)]
best[0][0] = 0
for i in range(1, N + 1):
    for j in range(i, M + 1):
        for k in range(i - 1, j):
            if best[i - 1][k] == INF:
                continue
            d = segs[j - 1][1] - segs[k][0]
            # además, penaliza meter pausas largas dentro de una misma frase
            inner = sum((segs[q + 1][0] - segs[q][1]) ** 2 for q in range(k, j - 1))
            c = best[i - 1][k] + ((d - exp[i - 1]) / exp[i - 1]) ** 2 + 3 * inner
            if c < best[i][j]:
                best[i][j], prev[i][j] = c, k
groups, j = [], M
for i in range(N, 0, -1):
    k = prev[i][j]
    groups.append(segs[k:j])
    j = k
groups.reverse()

lines = [[round(g[0][0], 3), round(g[-1][1], 3), [[round(a, 3), round(b, 3)] for a, b in g]] for g in groups]
json.dump({'lines': lines, 'total': round(dur + 1.0, 3)},
          open(os.path.join(here, f'timings-{EPN}.json'), 'w'), indent=1)
for t, (a, b, p) in zip(texts, lines):
    print(f'{a:6.2f} -> {b:6.2f}  ({len(p)} parte/s)  {t}')

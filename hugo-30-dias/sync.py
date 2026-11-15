"""Saca de la voz (MP3 de ElevenLabs) cuándo empieza y acaba cada frase del guion
y lo guarda en timings.json, que usa render.js para cuadrar escenas y subtítulos.

Uso: python3 sync.py voz.mp3
"""
import json, re, subprocess, sys

N_FRASES = 9
audio = sys.argv[1]

dur = float(subprocess.check_output(
    ['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', audio]).decode())
log = subprocess.run(['ffmpeg', '-i', audio, '-af', 'silencedetect=noise=-38dB:d=0.16', '-f', 'null', '-'],
                     capture_output=True, text=True).stderr
starts = [float(x) for x in re.findall(r'silence_start: ([\d.]+)', log)]
ends = [float(x) for x in re.findall(r'silence_end: ([\d.]+)', log)]

# tramos con voz = huecos entre silencios
segs, cur = [], 0.0
for s, e in zip(starts, ends + [dur] * (len(starts) - len(ends))):
    if s - cur > 0.08:
        segs.append([cur, s])
    cur = e
if dur - cur > 0.08:
    segs.append([cur, dur])

# juntar los tramos separados por las pausas más cortas hasta tener una por frase
while len(segs) > N_FRASES:
    gaps = [segs[i + 1][0] - segs[i][1] for i in range(len(segs) - 1)]
    i = gaps.index(min(gaps))
    segs[i:i + 2] = [[segs[i][0], segs[i + 1][1]]]
# si faltan, partir el tramo más largo
while len(segs) < N_FRASES:
    i = max(range(len(segs)), key=lambda k: segs[k][1] - segs[k][0])
    a, b = segs[i]
    segs[i:i + 1] = [[a, (a + b) / 2], [(a + b) / 2, b]]

json.dump({'lines': [[round(a, 3), round(b, 3)] for a, b in segs], 'total': round(dur + 1.0, 3)},
          open(__file__.replace('sync.py', 'timings.json'), 'w'), indent=1)
for i, (a, b) in enumerate(segs):
    print(f'frase {i + 1}: {a:6.2f} -> {b:6.2f}')

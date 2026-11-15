# Hugo · 30 días

Serie de vídeos cortos (TikTok / Reels / Shorts) animada por código.

- `scene.js`: guion, personajes, escenas y subtítulos del capítulo 1.
- `python3 sync.py voz.mp3`: cuadra el vídeo con la voz de ElevenLabs (genera `timings.json`).
- `node render.js --audio voz.mp3`: renderiza `out/capitulo-01.mp4` (1080x1920, 30 fps).
- `node render.js --preview`: fotogramas sueltos para revisar.

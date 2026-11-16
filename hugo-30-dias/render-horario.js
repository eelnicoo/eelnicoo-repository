// node render-horario.js -> out/horario.mp4 y out/horario.png
const { chromium } = require('playwright');
const path = require('path');
const { spawn } = require('child_process');
const dir = __dirname, FPS = 30;
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
  await p.goto('file://' + path.join(dir, 'horario.html'));
  await p.evaluate(() => document.fonts.ready);
  const total = await p.evaluate(() => window.TOTAL), rows = await p.evaluate(() => window.ROW_T);
  await p.evaluate(() => render(4.5));
  await p.screenshot({ path: path.join(dir, 'out', 'horario.png') });
  const cues = [[0.05, 'whoosh', 0.5], ...rows.map(t => [t, 'pop', 0.55]), [2.4, 'ding', 0.4], [0.05, 'buzz', 0.12]];
  const ff = ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-'];
  cues.forEach(c => ff.push('-i', path.join(dir, 'sfx', c[1] + '.wav')));
  const parts = cues.map((c, k) => { const ms = Math.round(c[0] * 1000); return `[${k + 1}:a]aformat=sample_rates=44100:channel_layouts=stereo,volume=${c[2]},adelay=${ms}|${ms}[a${k}]`; });
  ff.push('-filter_complex', parts.join(';') + ';' + cues.map((_, k) => `[a${k}]`).join('') + `amix=inputs=${cues.length}:normalize=0:duration=longest,apad[aout]`,
    '-map', '0:v', '-map', '[aout]', '-t', String(total), '-c:v', 'libx264', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS), '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', path.join(dir, 'out', 'horario.mp4'));
  const proc = spawn('ffmpeg', ff, { stdio: ['pipe', 'ignore', 'inherit'] });
  for (let i = 0; i < total * FPS; i++) {
    await p.evaluate(t => render(t), i / FPS);
    const buf = await p.screenshot({ type: 'jpeg', quality: 93 });
    if (!proc.stdin.write(buf)) await new Promise(r => proc.stdin.once('drain', r));
  }
  proc.stdin.end();
  await new Promise(r => proc.on('close', r));
  await b.close();
  console.log('Listo: out/horario.mp4 y out/horario.png');
})();

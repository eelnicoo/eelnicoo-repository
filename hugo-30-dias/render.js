// Uso:
//   node render.js --preview            -> fotogramas sueltos en out/preview/
//   node render.js [--audio voz.mp3]    -> out/capitulo-01.mp4
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const dir = __dirname;
const args = process.argv.slice(2);
const preview = args.includes('--preview');
const ai = args.indexOf('--audio');
const audio = ai >= 0 ? path.resolve(args[ai + 1]) : null;
const FPS = 30;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
  const tf = path.join(dir, 'timings.json');
  if (fs.existsSync(tf)) await page.addInitScript(`window.TL_OVERRIDE = ${fs.readFileSync(tf, 'utf8')};`);
  page.on('pageerror', e => { console.error('Error en la página:', e.message); process.exit(1); });
  await page.goto('file://' + path.join(dir, 'scene.html'));
  await page.evaluate(() => document.fonts.ready);
  const total = await page.evaluate(() => window.TOTAL);
  console.log('Duración:', total.toFixed(2), 's');

  if (preview) {
    const out = path.join(dir, 'out', 'preview');
    fs.mkdirSync(out, { recursive: true });
    const times = args.filter(a => /^\d+(\.\d+)?$/.test(a)).map(Number);
    const list = times.length ? times : Array.from({ length: 12 }, (_, i) => (i + 0.5) * total / 12);
    for (const [i, t] of list.entries()) {
      await page.evaluate(t => window.render(t), t);
      await page.screenshot({ path: path.join(out, `p${String(i).padStart(2, '0')}.png`) });
    }
    await browser.close();
    return;
  }

  const outFile = path.join(dir, 'out', 'capitulo-01.mp4');
  const ff = ['-y', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-'];
  if (audio) ff.push('-i', audio);
  ff.push('-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS));
  if (audio) ff.push('-c:a', 'aac', '-b:a', '192k', '-af', 'apad', '-shortest');
  ff.push('-movflags', '+faststart', outFile);
  const proc = spawn('ffmpeg', ff, { stdio: ['pipe', 'ignore', 'inherit'] });
  const n = Math.ceil(total * FPS);
  for (let i = 0; i < n; i++) {
    await page.evaluate(t => window.render(t), i / FPS);
    const buf = await page.screenshot({ type: 'jpeg', quality: 93 });
    if (!proc.stdin.write(buf)) await new Promise(r => proc.stdin.once('drain', r));
    if (i % 150 === 0) console.log(`fotograma ${i}/${n}`);
  }
  proc.stdin.end();
  await new Promise(r => proc.on('close', r));
  await browser.close();
  console.log('Listo:', outFile);
})();

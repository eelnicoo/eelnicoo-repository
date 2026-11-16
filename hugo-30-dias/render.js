// Uso:
//   node render.js --ep 2 --preview [segundos…]  -> fotogramas sueltos en out/preview/
//   node render.js --ep 2                        -> out/capitulo-02.mp4 (con audio/voz-capitulo-02.mp3 si existe)
//   node render.js --ep 2 --audio otra.mp3       -> con otra voz
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const dir = __dirname;
const args = process.argv.slice(2);
const preview = args.includes('--preview');
const ai = args.indexOf('--audio');
const ei = args.indexOf('--ep');
const EPN = String(ei >= 0 ? args[ei + 1] : '1').padStart(2, '0');
const defAudio = path.join(dir, 'audio', `voz-capitulo-${EPN}.mp3`);
const audio = ai >= 0 ? path.resolve(args[ai + 1]) : (fs.existsSync(defAudio) ? defAudio : null);
const FPS = 30;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
  const tf = path.join(dir, `timings-${EPN}.json`);
  if (fs.existsSync(tf)) await page.addInitScript(`window.TL_OVERRIDE = ${fs.readFileSync(tf, 'utf8')};`);
  page.on('pageerror', e => { console.error('Error en la página:', e.message); process.exit(1); });
  await page.goto('file://' + path.join(dir, 'scene.html') + '?ep=' + EPN);
  await page.evaluate(() => document.fonts.ready);
  const total = await page.evaluate(() => window.TOTAL);
  console.log('Duración:', total.toFixed(2), 's');

  if (preview) {
    const out = path.join(dir, 'out', 'preview');
    fs.mkdirSync(out, { recursive: true });
    const times = args.filter((a, i) => /^\d+(\.\d+)?$/.test(a) && args[i - 1] !== '--ep').map(Number);
    const list = times.length ? times : Array.from({ length: 12 }, (_, i) => (i + 0.5) * total / 12);
    for (const [i, t] of list.entries()) {
      await page.evaluate(t => window.render(t), t);
      await page.screenshot({ path: path.join(out, `p${String(i).padStart(2, '0')}.png`) });
    }
    await browser.close();
    return;
  }

  const outFile = path.join(dir, 'out', `capitulo-${EPN}.mp4`);
  const ff = ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-'];
  // audio: voz + efectos de sonido colocados en su segundo
  const cues = await page.evaluate(() => (window.sfxCues ? window.sfxCues() : []));
  const ins = [];
  if (audio) ins.push({ file: audio, chain: 'volume=1.0' });
  for (const c of cues) {
    const ms = Math.round(c.t * 1000);
    ins.push({ file: path.join(dir, 'sfx', c.f + '.wav'), chain: `${c.d ? `atrim=0:${(c.d - c.t).toFixed(3)},afade=t=out:st=${Math.max(0, c.d - c.t - 0.4).toFixed(3)}:d=0.4,` : ''}volume=${c.v},adelay=${ms}|${ms}` });
  }
  ins.forEach(i => ff.push('-i', i.file));
  ff.push('-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS));
  if (ins.length) {
    const parts = ins.map((i, k) => `[${k + 1}:a]aformat=sample_rates=44100:channel_layouts=stereo,${i.chain}[a${k}]`);
    const mix = ins.map((_, k) => `[a${k}]`).join('') + `amix=inputs=${ins.length}:normalize=0:duration=longest,loudnorm=I=-15:TP=-1.5:LRA=11,aresample=44100,apad[aout]`;
    ff.push('-filter_complex', parts.join(';') + ';' + mix, '-map', '0:v', '-map', '[aout]', '-t', total.toFixed(3), '-c:a', 'aac', '-b:a', '192k');
    console.log(`Audio: ${audio ? 'voz + ' : ''}${cues.length} efectos`);
  }
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

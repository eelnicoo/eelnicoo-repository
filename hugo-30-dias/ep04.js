// Capítulo 4 — Día 3: el gym, con Juan de entrenador (cameo pedido por un seguidor).
// Votación siguiente: PELUQUERÍA vs ROPA NUEVA.
const EP = {
  num: 4, day: 3, dayWord: 'tres', daysLeft: 27, glow: 0.09, start: { g: 0.09, sucio: 0 }, sucio: 0, teeth: 'white',
  coverHugo: { sucio: 0, mouth: 'grin', mood: 'determined' }, coverLine1: 'PRIMER DÍA', coverLine2: 'DE GYM',
  vote: {
    a: { label: 'GYM', pct: 67, color: '#ff8a2a', icon: DUMBBELL },
    b: { label: 'PELUQUERÍA', pct: 33, color: '#e64980', icon: SCISSORS },
  },
};
const NEXT = {
  A: { label: 'PELUQUERÍA', word: 'peluquería', color: '#e64980', icon: SCISSORS },
  B: { label: 'ROPA NUEVA', word: 'ropa', color: '#4dabf7', icon: SHIRT },
};

const LINES = [
  ...introLines(),
  { text: 'Habéis votado… y ganó el gym.', hl: ['gym'] },
  { text: 'Hugo se apunta al gimnasio más barato del barrio.', hl: ['barato'] },
  { text: 'Allí le espera Juan, el entrenador.', hl: ['juan'] },
  { text: 'Juan le pone diez kilos.', hl: ['diez', 'kilos'] },
  { text: 'Hugo no los levanta.', hl: ['no'], red: ['no'] },
  { text: 'Juan le quita los discos… y Hugo sigue sin poder.', hl: ['sin', 'poder'] },
  { text: 'Consejo de Juan: empieza por las escaleras.', hl: ['escaleras'] },
  { text: 'Hugo sube un piso… y pide un taxi.', hl: ['taxi'] },
  { text: 'En la oficina, Marcos le ve sudado y se ríe.', hl: ['sudado', 'ríe'], red: ['marcos'] },
  { text: 'Pero Laura le manda callar.', hl: ['laura', 'callar'] },
  { text: 'Mañana: ¿peluquería o ropa nueva?', hl: ['peluquería', 'ropa', 'nueva'] },
  { text: 'Comenta, y a lo mejor sales tú en el día cuatro.', hl: ['sales', 'tú'] },
];

// ---------- personajes y decorados ----------
function gymBg(t) {
  let s = `<rect width="1080" height="1920" fill="#343a40"/>`;
  for (let x = 0; x < 1080; x += 180) s += `<rect x="${x}" y="0" width="90" height="1430" fill="#3b4148"/>`;
  s += `<rect x="60" y="260" width="960" height="560" rx="12" fill="#a5d8ff" opacity=".35" ${st(8)}/><path d="M200,300 L320,780 M700,300 L820,780" stroke="#fff" stroke-width="10" opacity=".25"/>`;
  s += `<text x="540" y="230" text-anchor="middle" font-family="Lilita" font-size="70" fill="#ffd43b">GYM BARATO</text>`;
  s += `<rect x="0" y="1430" width="1080" height="490" fill="#212529"/><path d="M0,1430 H1080" ${st(7)}/>`;
  for (let i = 0; i < 6; i++) s += `<rect x="${(i * 190) % 1080}" y="${1500 + (i % 3) * 140}" width="120" height="14" fill="#2b3035"/>`;
  return s;
}
function barbell(cx, y, plates, lift = 0) {
  let s = `<g transform="translate(${cx},${y - lift})"><rect x="-380" y="-10" width="760" height="20" rx="8" fill="#ced4da" ${st(6)}/>`;
  for (const sx of [-1, 1]) for (let i = 0; i < plates; i++) s += `<rect x="${sx * (300 + i * 36) - 16}" y="-110" width="32" height="220" rx="10" fill="${i % 2 ? '#e03131' : '#1c7ed6'}" ${st(6)}/>`;
  return s + '</g>';
}
// ---------- escenas ----------
const SCENES = Object.assign({}, INTRO_SCENES, {
  V(lt, t) { return voteScene(lt, t, 4); },
  // «Hugo se apunta al gimnasio más barato del barrio.»
  G(lt, t) {
    let s = `<rect width="1080" height="1920" fill="#4c6ef5"/><rect y="1450" width="1080" height="470" fill="#868e96"/><path d="M0,1450 H1080" ${st(7)}/>`;
    s += `<rect x="120" y="500" width="840" height="950" fill="#495057" ${st(9)}/><rect x="400" y="1050" width="280" height="400" fill="#212529" ${st(8)}/>`;
    const flick = Math.sin(t * 23) > 0.6 ? 0.25 : 1;
    s += `<rect x="160" y="560" width="760" height="300" rx="20" fill="#212529" ${st(8)}/>`;
    s += `<text x="540" y="700" text-anchor="middle" font-family="Lilita" font-size="120" fill="#ffd43b">GYM <tspan opacity="${flick}">B</tspan>ARATO</text>`;
    s += `<text x="540" y="810" text-anchor="middle" font-family="Fredoka" font-size="56" fill="#ff8787">9,99 €/MES</text>`;
    const walk = easeOut(seg(lt, 0, 1.4));
    s += place(hugo({ t, mood: 'determined', mouth: 'smile', look: 1, blink: blinkAt(t) }), lerp(-120, 300, walk), 1880 - Math.abs(Math.sin(t * 9)) * 14 * (walk < 1 ? 1 : 0), 0.85);
    return s;
  },
  // «Allí le espera Juan, el entrenador.»
  J(lt, t) {
    let s = gymBg(t);
    const k = easeOut(seg(lt, 0, 0.35));
    s += place(juan({ t, armR: -150 + Math.sin(t * 6) * 8, armL: 150 - Math.sin(t * 6) * 8, mouth: 'smile' }), lerp(1300, 600, k), 1980, 1.05);
    s += place(hugo({ t, mood: 'sad', mouth: 'o', look: 1 }), 220, 1950, 0.75);
    const kn = back(seg(lt, 0.3, 0.6));
    s += `<g transform="translate(600,420) rotate(-4) scale(${kn})"><rect x="-220" y="-80" width="440" height="160" rx="30" fill="#e03131" ${st(9)}/><text x="0" y="40" text-anchor="middle" font-family="Lilita" font-size="110" fill="#fff">JUAN</text></g>`;
    return s;
  },
  // «Juan le pone diez kilos.» / «Hugo no los levanta.» / «Juan le quita los discos… y Hugo sigue sin poder.»
  K(lt, t) {
    const L = TL.lines, strain = t >= L[8].start - 0.05, noPlates = t >= wordTime(9, 'discos'), still = t >= wordTime(9, 'sigue');
    let s = gymBg(t);
    const jit = strain ? Math.sin(t * 50) * 5 : 0;
    s += place(juan({ t, armR: still ? -160 : -20, facepalm: still, mouth: still ? 'flat' : 'smile', look: -1 }), 900, 1960, 0.85);
    s += place(hugo({ t, mood: strain ? 'determined' : 'normal', mouth: strain ? 'grit' : 'o', armL: 4, armR: -4, look: 0, lookY: strain ? 0 : 1 }), 440 + jit, 1960, 1.0);
    if (strain) s += `<ellipse cx="${440 + jit}" cy="${1960 - 850}" rx="185" ry="165" fill="#ff6b6b" opacity="${0.3 + 0.1 * Math.sin(t * 8)}"/>` + sweat(440, 760, t);
    // discos que salen volando al quitarlos
    const fly = seg(t, wordTime(9, 'discos'), wordTime(9, 'discos') + 0.6);
    if (fly > 0 && fly < 1) for (const sx of [-1, 1]) s += `<g transform="translate(${440 + sx * (330 + fly * 500)},${1585 - Math.sin(fly * Math.PI) * 400}) rotate(${fly * 400 * sx})"><rect x="-16" y="-110" width="32" height="220" rx="10" fill="#1c7ed6" ${st(6)}/></g>`;
    s += barbell(440, 1585, noPlates ? 0 : 2, 0);
    const kp = back(seg(t, L[7].start + 0.2, L[7].start + 0.5));
    if (!strain && kp > 0) s += `<g transform="translate(440,560) scale(${kp})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="150" fill="#ffd43b" stroke="${OUT}" stroke-width="18" paint-order="stroke">10 KG</text></g>`;
    if (strain && !noPlates) s += bubble(440, 480, '¡NGHHH!', back(seg(t, L[8].start, L[8].start + 0.3)), '#e03131', 90);
    if (still) s += `<g transform="translate(440,520) scale(${back(seg(t, wordTime(9, 'sigue'), wordTime(9, 'sigue') + 0.3))})"><rect x="-200" y="-90" width="400" height="180" rx="30" fill="#fff4e0" stroke="#e03131" stroke-width="14"/><text x="0" y="45" text-anchor="middle" font-family="Lilita" font-size="120" fill="#e03131">0 CM</text></g>`;
    return s;
  },
  // «Consejo de Juan: empieza por las escaleras.»
  S(lt, t) {
    let s = gymBg(t);
    s += place(juan({ t, armR: -120 + Math.sin(t * 8) * 6, mouth: Math.sin(t * 14) > 0 ? 'open' : 'smile', look: 0 }), 540, 2400, 1.6);
    const tw = wordTime(10, 'escaleras') - TL.lines[10].start;
    s += bubble(540, 480, '¡ESCALERAS!', back(seg(lt, tw - 0.1, tw + 0.2)), '#e03131', 100);
    return s;
  },
  // «Hugo sube un piso… y pide un taxi.»
  E(lt, t) {
    let s = `<rect width="1080" height="1920" fill="#e9ecef"/>`;
    for (let i = 0; i < 10; i++) s += `<rect x="${i * 108}" y="${1900 - i * 110}" width="${1080 - i * 108}" height="110" fill="${i % 2 ? '#ced4da' : '#dee2e6'}" ${st(5)}/>`;
    s += `<path d="M0,1790 L1080,690" stroke="#868e96" stroke-width="16"/><rect x="760" y="420" width="220" height="110" rx="14" fill="#fff" ${st(7)}/><text x="870" y="495" text-anchor="middle" font-family="Lilita" font-size="56" fill="${OUT}">PISO 1</text>`;
    const ttx = wordTime(11, 'taxi') - TL.lines[11].start, climb = easeInOut(seg(lt, 0, ttx - 0.5)), done = lt > ttx - 0.4;
    s += place(hugo({ t, mood: done ? 'sad' : 'determined', mouth: done ? 'open' : 'grit', armR: done ? -150 : -6, look: 1, blink: false }), lerp(180, 430, climb), lerp(1900, 1680, climb) + (done ? Math.sin(t * 10) * 6 : 0), 0.85);
    s += sweat(lerp(180, 430, climb), 830, t, 4);
    if (done) s += bubble(700, 640, '¡TAXI! 🚕', back(seg(lt, ttx - 0.3, ttx)), OUT, 90);
    return s;
  },
  // «En la oficina, Marcos le ve sudado y se ríe.» / «Pero Laura le manda callar.»
  O(lt, t) {
    const L = TL.lines, shut = t >= wordTime(13, 'callar') - 0.25, tr = wordTime(12, 'ríe');
    let s = classroom(t);
    s += place(hugo({ t, mood: 'sad', mouth: 'sad', look: 1, blink: blinkAt(t) }), 220, 1760, 0.75);
    s += sweat(220, 980, t, 4) + `<ellipse cx="200" cy="1300" rx="60" ry="40" fill="#2b8a50" opacity=".6"/><ellipse cx="260" cy="1360" rx="40" ry="30" fill="#2b8a50" opacity=".6"/>`;
    s += place(marcos({ t }), 870, 1780 + (t > tr && !shut ? Math.abs(Math.sin(t * 16)) * -10 : 0), 0.8, t > tr && !shut ? Math.sin(t * 16) * 2 : 0);
    if (t > tr && !shut) for (let i = 0; i < 3; i++) { const k = (t * 1.3 + i / 3) % 1; s += `<text x="${780 + i * 70}" y="${760 - k * 200}" font-family="Lilita" font-size="80" fill="#e03131" opacity="${Math.sin(k * Math.PI)}" ${st(6)} paint-order="stroke">JA</text>`; }
    const lin = easeOut(seg(t, L[13].start - 0.1, L[13].start + 0.4));
    s += place(laura({ look: 1, blink: false, mouth: shut ? 'flat' : 'smile', brow: shut ? 'normal' : 'up' }), lerp(1300, 560, lin), 1780, 0.78);
    if (shut) s += bubble(560, 560, '¡SHHH!', back(seg(t, wordTime(13, 'callar') - 0.25, wordTime(13, 'callar') + 0.05)), '#e64980', 100);
    if (shut) s += `<text x="1000" y="900" font-size="80">💧</text>`;
    return s;
  },
  N(lt, t) { return nextVoteScene(lt, t, 14, NEXT.A, NEXT.B, { teeth: 'white' }); },
});
const SCENE_OF = ['I0', 'I1', 'I2', 'I3', 'V', 'G', 'J', 'K', 'K', 'K', 'S', 'E', 'O', 'O', 'N', 'N'];

// marcador: al mandar Laura callar a Marcos, GLOW 9→14 % y barra 12→17 %
EP.hud = function (t) {
  const L = TL.lines, tu = wordTime(3, 'tres'), tc = wordTime(13, 'callar') + 0.3;
  const up = easeOut(seg(t, tc, tc + 0.8));
  return {
    show: t < L[4].start - 0.1 ? seg(t, L[3].start, L[3].start + 0.35) : seg(t, L[5].start - 0.08, L[5].start + 0.25),
    days: t < tu ? 28 : 27,
    dayPulse: Math.max(0, Math.sin(seg(t, tu, tu + 0.4) * Math.PI)),
    glow: lerp(9, 14, up), glowPulse: Math.max(0, Math.sin(seg(t, tc, tc + 0.5) * Math.PI)),
    pct: lerp(12, 17, up), extra: plusBadge(t, tc, 5),
  };
};
EP.shake = (sc, lt, t) => {
  const L = TL.lines;
  if (sc === 'K' && t >= L[8].start && t < L[8].end) return 6;
  return 0;
};

// ---------- efectos de sonido ----------
window.sfxCues = function () {
  const L = TL.lines, c = [];
  const add = (t, f, v, d) => c.push({ t: Math.max(0, t), f, v, d });
  introCues(add);
  voteCues(add, 4);
  add(L[5].start - 0.08, 'whoosh', 0.4);
  // Juan
  add(L[6].start - 0.05, 'whistle', 0.45);
  add(L[6].start + 0.35, 'stamp', 0.5);
  // pesas
  add(L[7].start + 0.15, 'clank', 0.55); add(L[7].start + 0.35, 'clank', 0.45);
  add(L[8].start, 'chatter', 0.4);
  add(L[8].end - 0.2, 'fail', 0.25);
  add(wordTime(9, 'discos'), 'whoosh', 0.45); add(wordTime(9, 'discos') + 0.4, 'clank', 0.4);
  add(wordTime(9, 'sigue') + 0.05, 'stamp', 0.55);
  // escaleras y taxi
  add(wordTime(10, 'escaleras') - 0.1, 'whistle', 0.35);
  add(L[11].start - 0.08, 'whoosh', 0.35);
  add(wordTime(11, 'taxi') - 0.3, 'horn', 0.45);
  // oficina
  add(wordTime(12, 'ríe'), 'boing', 0.4);
  add(wordTime(13, 'callar') - 0.25, 'shh', 0.55);
  add(wordTime(13, 'callar') + 0.3, 'magic', 0.45);
  nextVoteCues(add, 14, NEXT.A, NEXT.B);
  return c;
};

start();

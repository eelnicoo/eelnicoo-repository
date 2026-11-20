// Capítulo 6 — Día 5: ropa nueva. Votación siguiente: AFEITADO vs LENTILLAS.
const EP = {
  num: 6, day: 5, dayWord: 'cinco', daysLeft: 25, glow: 0.22, start: { g: 0.22, sucio: 0 }, sucio: 0, teeth: 'white', hair: 'fade',
  coverHugo: { sucio: 0, mouth: 'grin', outfit: 'shirt' }, coverLine1: 'ADIÓS A LA', coverLine2: 'SUDADERA VERDE',
  vote: {
    a: { label: 'ROPA NUEVA', pct: 85, color: '#4dabf7', icon: SHIRT },
    b: { label: 'LENTILLAS', pct: 15, color: '#7950f2', icon: LENS },
  },
};
const NEXT = {
  A: { label: 'AFEITADO', word: 'afeitado', color: '#1c7ed6', icon: RAZOR },
  B: { label: 'LENTILLAS', word: 'lentillas', color: '#7950f2', icon: LENS },
};

const LINES = [
  ...introLines(),
  { text: 'Habéis votado… y ganó la ropa.', hl: ['ropa'] },
  { text: 'Hugo tiene doce sudaderas.', hl: ['doce', 'sudaderas'] },
  { text: 'Todas verdes. Todas iguales.', hl: ['verdes', 'iguales'] },
  { text: 'En la tienda, el dependiente le trae una camisa de su talla… y sale disparado un botón.', hl: ['botón'] },
  { text: 'Le da a una señora en la frente.', hl: ['frente'], red: ['frente'] },
  { text: 'Dos tallas más tarde: camisa negra, vaquero y zapatillas blancas.', hl: ['camisa', 'negra'] },
  { text: 'En la oficina, Laura le mira de arriba abajo… y le dice: te queda bien.', hl: ['te', 'queda', 'bien'] },
  { text: 'Marcos, por si acaso, se quita las gafas de sol.', hl: ['gafas'], red: ['marcos'] },
  { text: 'Mañana: ¿afeitado o lentillas?', hl: ['afeitado', 'lentillas'] },
  { text: 'Comenta, y a lo mejor sales tú en el día seis.', hl: ['sales', 'tú'] },
];

// ---------- decorados y personajes ----------
function shop(t) {
  let s = `<rect width="1080" height="1920" fill="#fff9db"/>`;
  s += `<rect x="0" y="1430" width="1080" height="490" fill="#c8a27a"/><path d="M0,1430 H1080" ${st(7)}/>`;
  s += `<path d="M60,330 H1020" ${st(12)}/>`;
  const cols = ['#e64980', '#4dabf7', '#ffd43b', '#51cf66', '#7950f2', '#ff922b'];
  for (let i = 0; i < 8; i++) { const x = 110 + i * 120; s += `<path d="M${x},330 v30" ${st(5)}/><path d="M${x - 50},365 L${x + 50},365 L${x + 60},600 L${x - 60},600 Z" fill="${cols[i % 6]}" ${st(6)}/>`; }
  s += `<text x="540" y="250" text-anchor="middle" font-family="Lilita" font-size="80" fill="#e8590c">TIENDA DE ROPA</text>`;
  return s;
}
function clerk(o = {}) {
  const { look = 0, armR = -10, mouth = 'smile' } = o;
  const skin = '#e7b48f';
  let s = `<rect x="-60" y="-400" width="52" height="370" rx="22" fill="#343a40" ${st()}/><rect x="8" y="-400" width="52" height="370" rx="22" fill="#343a40" ${st()}/>`;
  s += `<path d="M-84,-8 Q-84,-50 -36,-50 Q-6,-50 -6,-8 Z" fill="#fff" ${st()}/><path d="M84,-8 Q84,-50 36,-50 Q6,-50 6,-8 Z" fill="#fff" ${st()}/>`;
  s += arm(-110, -640, 8, '#ff922b', 52, 230, skin) + arm(110, -640, armR, '#ff922b', 52, 230, skin);
  s += `<path d="M-118,-665 Q-130,-520 -102,-380 L102,-380 Q130,-520 118,-665 Q0,-695 -118,-665 Z" fill="#ff922b" ${st()}/>`;
  s += `<path d="M120,-600 l40,0" ${st(6)}/><path d="M-40,-670 Q0,-600 40,-670" fill="none" ${st(6)}/><circle cx="-60" cy="-560" r="0"/>`;
  s += `<path d="M150,-600 v300" stroke="#ffd43b" stroke-width="10"/><rect x="-26" y="-715" width="52" height="60" fill="${skin}" ${st()}/><circle cx="0" cy="-840" r="148" fill="${skin}" ${st()}/>`;
  s += `<path d="M-148,-870 Q-150,-1010 20,-1000 Q150,-990 148,-870 Q120,-940 40,-930 Q-60,-950 -148,-870 Z" fill="#212529" ${st()}/>`;
  for (const sx of [-1, 1]) { const x = sx * 55; s += `<ellipse cx="${x}" cy="-845" rx="26" ry="30" fill="#fff" ${st(6)}/><circle cx="${x + look * 10}" cy="-841" r="12" fill="${OUT}"/>`; }
  s += mouth === 'o' ? `<ellipse cx="0" cy="-745" rx="22" ry="28" fill="#7a1f2e" ${st(6)}/>` : `<path d="M-34,-750 Q0,-722 34,-750" fill="none" ${st(7)}/>`;
  return s;
}
function lady(o = {}) {
  const { hit = 0 } = o;
  const skin = '#f1c7a5';
  let s = `<rect x="-60" y="-380" width="120" height="350" rx="30" fill="#9c36b5" ${st()}/>`;
  s += `<path d="M-84,-8 Q-84,-50 -36,-50 Q-6,-50 -6,-8 Z" fill="#212529" ${st()}/><path d="M84,-8 Q84,-50 36,-50 Q6,-50 6,-8 Z" fill="#212529" ${st()}/>`;
  s += arm(-110, -640, 8, '#cc5de8', 52, 230, skin) + arm(110, -640, -8, '#cc5de8', 52, 230, skin);
  s += `<path d="M-118,-665 Q-130,-520 -102,-370 L102,-370 Q130,-520 118,-665 Q0,-695 -118,-665 Z" fill="#cc5de8" ${st()}/>`;
  s += `<rect x="-26" y="-715" width="52" height="60" fill="${skin}" ${st()}/><circle cx="0" cy="-840" r="148" fill="${skin}" ${st()}/>`;
  for (const [x, y] of [[-120, -930], [-60, -990], [0, -1005], [60, -990], [120, -930], [-145, -860], [145, -860]]) s += `<circle cx="${x}" cy="${y}" r="52" fill="#dee2e6" ${st(6)}/>`;
  for (const sx of [-1, 1]) {
    const x = sx * 55;
    if (hit > 0.2) s += `<path d="M${x - 18},-863 l36,36 M${x + 18},-863 l-36,36" ${st(8)}/>`;
    else s += `<circle cx="${x}" cy="-845" r="40" fill="#e7f5ff" ${st(6)}/><circle cx="${x}" cy="-845" r="11" fill="${OUT}"/>`;
  }
  s += `<path d="M-15,-845 H15" ${st(6)}/><ellipse cx="0" cy="-745" rx="${hit > 0.2 ? 26 : 18}" ry="${hit > 0.2 ? 30 : 12}" fill="#7a1f2e" ${st(6)}/>`;
  if (hit > 0) s += `<circle cx="0" cy="-950" r="${20 + 20 * hit}" fill="#ff8787" ${st(5)}/>`;
  return s;
}

// ---------- escenas ----------
const SCENES = Object.assign({}, INTRO_SCENES, {
  V(lt, t) { return voteScene(lt, t, 4); },
  // «Hugo tiene doce sudaderas.» / «Todas verdes. Todas iguales.»
  A(lt, t) {
    let s = `<rect width="1080" height="1920" fill="#e7d3b5"/><rect x="60" y="260" width="960" height="1280" rx="20" fill="#8a5a32" ${st(10)}/><rect x="90" y="290" width="900" height="1220" fill="#5c3b1e"/>`;
    s += `<path d="M90,400 H990" ${st(10)}/>`;
    const n = Math.min(12, Math.floor(seg(lt, 0.2, 1.6) * 12) + 1);
    for (let i = 0; i < n; i++) {
      const x = 145 + i * 72;
      s += `<path d="M${x},400 v20" ${st(4)}/><g transform="translate(${x},${700 + Math.sin(t * 2 + i) * 4})"><path d="M-45,-270 L45,-270 L60,250 L-60,250 Z" fill="#3fae6a" ${st(5)}/><path d="M-25,60 H25 L32,140 H-32 Z" fill="#2b8a50" ${st(4)}/><circle cx="-20" cy="-60" r="9" fill="#e8b923"/></g>`;
    }
    const tv = wordTime(6, 'verdes') - TL.lines[5].start, k = back(seg(lt, 0.2, 0.5));
    s += `<g transform="translate(540,1700) rotate(-3) scale(${k})"><rect x="-240" y="-80" width="480" height="160" rx="26" fill="#fff" ${st(8)}/><text x="0" y="40" text-anchor="middle" font-family="Lilita" font-size="110" fill="#2b8a50">×${n}</text></g>`;
    if (lt > tv) s += place(hugo({ t, mood: 'normal', mouth: 'smile', look: 1, blink: blinkAt(t) }), 860, 2260, 0.8);
    return s;
  },
  // «En la tienda… sale disparado un botón.» / «Le da a una señora en la frente.»
  T(lt, t) {
    const L = TL.lines, tb = wordTime(7, 'botón') - 0.2, th = L[8].start + wordTime(8, 'frente') - L[8].start - 0.15;
    let s = shop(t);
    const fl = seg(t, tb, th), hit = seg(t, th, th + 0.3);
    s += place(clerk({ look: fl > 0 ? 1 : -1, mouth: fl > 0 ? 'o' : 'smile', armR: fl > 0 ? -160 : -20 }), 180, 1950, 0.8);
    const strain = t > tb - 0.8;
    s += place(hugo({ t, outfit: 'shirt', mood: strain ? 'determined' : 'normal', mouth: strain ? 'grit' : 'smile', look: 1 }), 470 + (strain && fl === 0 ? Math.sin(t * 40) * 4 : 0), 1960, 0.95);
    s += place(lady({ hit }), 900, 1950, 0.75);
    if (fl > 0 && hit < 1) {
      const bx = lerp(470, 900, fl), by = 1340 - Math.sin(fl * Math.PI) * 300 + fl * (1950 - 0.75 * 950 - 1340);
      s += `<g transform="translate(${bx},${by}) rotate(${fl * 900})"><circle r="26" fill="#495057" ${st(6)}/><circle cx="-8" cy="-6" r="4" fill="#fff"/><circle cx="8" cy="6" r="4" fill="#fff"/></g>`;
      for (let i = 1; i < 5; i++) s += `<circle cx="${lerp(470, 900, Math.max(0, fl - i * 0.05))}" cy="${by + i * 4}" r="${16 - i * 3}" fill="#fff" opacity="${0.5 - i * 0.1}"/>`;
    }
    if (hit > 0) s += bubble(900, 520, '¡AY!', back(hit), '#e03131', 110);
    if (strain && fl === 0) s += sweat(470, 860, t, 3);
    return s;
  },
  // «Dos tallas más tarde: camisa negra, vaquero y zapatillas blancas.» — el cambio
  F(lt, t) {
    let s = burst(540, 900, t, '#212529', '#343a40');
    s += place(hugo({ t, outfit: 'shirt', mood: 'normal', mouth: 'grin', look: 0, blink: blinkAt(t, 0.4) }), 540, 1960 + Math.sin(t * 3) * 4, 1.0);
    const items = [['CAMISA NEGRA', 'camisa', 560], ['VAQUERO', 'vaquero', 1420], ['ZAPATILLAS', 'zapatillas', 1820]];
    items.forEach(([lab, w, y], i) => {
      const k = back(seg(t, wordTime(9, w) - 0.05, wordTime(9, w) + 0.25));
      if (k > 0) s += `<g transform="translate(${i % 2 ? 860 : 220},${y}) rotate(${i % 2 ? 5 : -5}) scale(${k})"><rect x="-190" y="-50" width="380" height="100" rx="24" fill="#ffd43b" ${st(7)}/><text x="0" y="20" text-anchor="middle" font-family="Lilita" font-size="54" fill="${OUT}">${lab}</text></g>`;
    });
    for (let i = 0; i < 6; i++) { const a = (t * 2.2 + i * 1.1) % 2, sc = Math.sin(clamp(a / 2) * Math.PI); s += `<path d="M0,-40 Q6,-6 40,0 Q6,6 0,40 Q-6,6 -40,0 Q-6,-6 0,-40 Z" transform="translate(${[180, 900, 140, 940, 300, 780][i]},${[700, 660, 1100, 1060, 520, 480][i]}) scale(${sc * 1.5})" fill="#fff" ${st(5)}/>`; }
    const fl = 1 - seg(lt, 0, 0.35);
    if (fl > 0) s += `<rect width="1080" height="1920" fill="#fff" opacity="${fl}"/>`;
    return s;
  },
  // «Laura le mira de arriba abajo… y le dice: te queda bien.» / «Marcos… se quita las gafas de sol.»
  O(lt, t) {
    const L = TL.lines, tq = wordTime(10, 'te') - 0.1, tg = wordTime(11, 'gafas') - 0.2;
    let s = classroom(t);
    s += place(hugo({ t, outfit: 'shirt', mood: t > tq + 0.4 ? 'dream' : 'normal', mouth: 'grin', look: 1, blink: blinkAt(t, 1.2) }), 260, 1760, 0.75);
    const scan = t < tq ? Math.sin(t * 4) : 0;
    s += place(laura({ look: -1, blink: false, mouth: 'smile', brow: 'up' }), 560, 1760, 0.75);
    if (t < tq && lt > 0.6) s += `<path d="M500,${900 + scan * 300} L310,${900 + scan * 300}" stroke="#e64980" stroke-width="6" stroke-dasharray="14 10" opacity=".7"/>`;
    if (t >= tq && t < L[11].start) s += bubble(600, 560, 'TE QUEDA BIEN', back(seg(t, tq, tq + 0.3)), '#e64980', 72);
    const off = t > tg;
    s += place(marcos({ t, noGlasses: off }), 900, 1780, 0.75);
    if (off) {
      const f = seg(t, tg, tg + 0.6);
      s += `<g transform="translate(${900 + 160 * f},${1780 - 0.75 * 900 + 300 * f}) rotate(${f * 40})"><rect x="-80" y="-24" width="70" height="44" rx="14" fill="#141414" ${st(4)}/><rect x="10" y="-24" width="70" height="44" rx="14" fill="#141414" ${st(4)}/></g>`;
      s += sweat(900, 880, t, 3) + bubble(880, 520, '😳', back(seg(t, tg + 0.2, tg + 0.5)), OUT, 90);
    }
    if (t > tq) for (let i = 0; i < 4; i++) { const k = ((t * 0.8 + i / 4) % 1); s += heart(lerp(520, 330, k), lerp(980, 700, k), 0.4, '#ff6f9f', Math.sin(k * Math.PI)); }
    return s;
  },
  N(lt, t) { return nextVoteScene(lt, t, 12, NEXT.A, NEXT.B, { outfit: 'shirt' }); },
});
const SCENE_OF = ['I0', 'I1', 'I2', 'I3', 'V', 'A', 'A', 'T', 'T', 'F', 'O', 'O', 'N', 'N'];

// marcador: con la ropa GLOW 22→30 %; con «te queda bien» barra 25→33 %
EP.hud = function (t) {
  const L = TL.lines, tu = wordTime(3, 'cinco'), tg = wordTime(9, 'camisa') + 0.1, tq = wordTime(10, 'bien') + 0.2;
  const ug = easeOut(seg(t, tg, tg + 0.8)), ub = easeOut(seg(t, tq, tq + 0.8));
  return {
    show: t < L[4].start - 0.1 ? seg(t, L[3].start, L[3].start + 0.35) : seg(t, L[5].start - 0.08, L[5].start + 0.25),
    days: t < tu ? 26 : 25,
    dayPulse: Math.max(0, Math.sin(seg(t, tu, tu + 0.4) * Math.PI)),
    glow: lerp(22, 30, ug), glowPulse: Math.max(0, Math.sin(seg(t, tg, tg + 0.5) * Math.PI)),
    pct: lerp(25, 33, ub), extra: plusBadge(t, tg, 8),
  };
};
EP.shake = (sc, lt, t) => {
  const th = wordTime(8, 'frente') - 0.15;
  if (sc === 'T' && t > th && t < th + 0.35) return 12 * (1 - seg(t, th, th + 0.35));
  return sc === 'F' && lt < 0.4 ? 10 * (1 - lt / 0.4) : 0;
};

// ---------- efectos de sonido ----------
window.sfxCues = function () {
  const L = TL.lines, c = [];
  const add = (t, f, v, d) => c.push({ t: Math.max(0, t), f, v, d });
  introCues(add);
  voteCues(add, 4);
  // armario
  add(L[5].start - 0.08, 'whoosh', 0.4);
  for (let i = 0; i < 12; i++) add(L[5].start + 0.12 + i * 0.117, 'tick', 0.3);
  add(wordTime(6, 'iguales'), 'fail', 0.22);
  // tienda: el botón
  add(L[7].start - 0.08, 'whoosh', 0.35);
  add(wordTime(7, 'botón') - 0.25, 'chatter', 0.3);
  add(wordTime(7, 'botón') - 0.2, 'pop', 0.6);
  add(wordTime(7, 'botón') - 0.15, 'whoosh', 0.35);
  add(wordTime(8, 'frente') - 0.15, 'boing', 0.55); add(wordTime(8, 'frente') - 0.1, 'impact', 0.35);
  // el cambio
  add(L[9].start - 0.08, 'impact', 0.45); add(L[9].start, 'magic', 0.5);
  for (const w of ['camisa', 'vaquero', 'zapatillas']) add(wordTime(9, w), 'pop', 0.5);
  add(L[9].start + 0.2, 'sparkle', 0.45);
  // oficina
  add(L[10].start - 0.08, 'whoosh', 0.35);
  add(wordTime(10, 'te') - 0.1, 'pop', 0.45); add(wordTime(10, 'bien') + 0.2, 'ding', 0.45);
  add(wordTime(11, 'gafas') - 0.2, 'whoosh', 0.35); add(wordTime(11, 'gafas') + 0.3, 'clank', 0.25);
  add(wordTime(11, 'gafas') + 0.4, 'fail', 0.2);
  nextVoteCues(add, 12, NEXT.A, NEXT.B);
  return c;
};

start();

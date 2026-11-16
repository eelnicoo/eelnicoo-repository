// Capítulo 3 — Día 2: el dentista. Votación siguiente: GYM vs PELUQUERÍA.
const EP = {
  num: 3, day: 2, dayWord: 'dos', daysLeft: 28, glow: 0.04, start: { g: 0.04, sucio: 0 }, sucio: 0, teeth: 'yellow',
  coverHugo: { sucio: 0, mouth: 'grin', teeth: 'white' }, coverLine1: 'DIENTES', coverLine2: 'DEMASIADO BLANCOS',
  vote: {
    a: { label: 'GYM', pct: 29, color: '#ff8a2a', icon: DUMBBELL },
    b: { label: 'DENTISTA', pct: 71, color: '#12b886', icon: TOOTH },
  },
};
const NEXT = {
  A: { label: 'GYM', word: 'gym', color: '#ff8a2a', icon: DUMBBELL },
  B: { label: 'PELUQUERÍA', word: 'peluquería', color: '#e64980', icon: SCISSORS },
};

const LINES = [
  ...introLines(),
  { text: 'Habéis votado… y ganó el dentista.', hl: ['dentista'] },
  { text: 'Problema: le queda un euro con veinte.', hl: ['euro', 'veinte'], red: ['problema'] },
  { text: 'Así que va a la facultad, donde los estudiantes practican gratis.', hl: ['facultad', 'gratis'] },
  { text: 'El estudiante le abre la boca… y se desmaya.', hl: ['boca', 'desmaya'] },
  { text: 'Tres horas y dos estudiantes desmayados después…', hl: ['tres', 'horas', 'desmayados'] },
  { text: 'Hugo tiene los dientes blancos.', hl: ['dientes', 'blancos'] },
  { text: 'Demasiado blancos.', hl: ['demasiado', 'blancos'] },
  { text: 'En la oficina se va la luz… y Hugo la ilumina sonriendo.', hl: ['luz', 'ilumina'] },
  { text: 'Y Laura, por primera vez, se ríe con él.', hl: ['laura', 'ríe'] },
  { text: 'Mañana: ¿gym o peluquería?', hl: ['gym', 'peluquería'] },
  { text: 'Comenta, y sígueme para el día tres.', hl: ['día', 'tres'] },
];

// ---------- personajes y decorados de este capítulo ----------
// estudiante de odontología con bata y mascarilla
function student(o = {}) {
  const { faint = false, look = 0, t = 0, hair = '#2b2b2b' } = o;
  const skin = '#f1c7a5';
  let s = '';
  s += `<rect x="-66" y="-400" width="56" height="370" rx="22" fill="#4c6ef5" ${st()}/><rect x="10" y="-400" width="56" height="370" rx="22" fill="#4c6ef5" ${st()}/>`;
  s += `<path d="M-90,-8 Q-90,-52 -40,-52 Q-6,-52 -6,-8 Z" fill="#fff" ${st()}/><path d="M90,-8 Q90,-52 40,-52 Q6,-52 6,-8 Z" fill="#fff" ${st()}/>`;
  s += arm(-118, -640, faint ? 40 : 8, '#fff', 56, 240, skin) + arm(118, -640, faint ? -40 : -8, '#fff', 56, 240, skin);
  s += `<path d="M-125,-665 Q-140,-520 -110,-300 L110,-300 Q140,-520 125,-665 Q0,-695 -125,-665 Z" fill="#fff" ${st()}/>`;
  s += `<path d="M0,-665 V-300 M-40,-660 L0,-560 L40,-660" fill="none" ${st(6)}/><rect x="40" y="-560" width="50" height="60" rx="6" fill="#d0ebff" ${st(5)}/>`;
  s += `<rect x="-28" y="-715" width="56" height="60" fill="${skin}" ${st()}/><circle cx="0" cy="-840" r="150" fill="${skin}" ${st()}/>`;
  s += `<path d="M-150,-860 Q-150,-1000 0,-1000 Q150,-1000 150,-860 Q100,-930 0,-925 Q-100,-930 -150,-860 Z" fill="${hair}" ${st()}/>`;
  for (const sx of [-1, 1]) {
    const x = sx * 55, y = -850;
    if (faint) s += `<path d="M${x - 20},${y - 20} l40,40 M${x + 20},${y - 20} l-40,40" ${st(9)}/>`;
    else s += `<ellipse cx="${x}" cy="${y}" rx="26" ry="30" fill="#fff" ${st(6)}/><circle cx="${x + look * 10}" cy="${y + 4}" r="12" fill="${OUT}"/>`;
  }
  s += `<path d="M-110,-800 Q0,-830 110,-800 L95,-720 Q0,-690 -95,-720 Z" fill="#a5d8ff" ${st(6)}/><path d="M-110,-800 L-150,-830 M110,-800 L150,-830" ${st(4)}/>`;
  if (faint) for (let i = 0; i < 3; i++) { const a = t * 3 + i * 2.1; s += `<path d="M0,-12 L3,-3 L12,0 L3,3 L0,12 L-3,3 L-12,0 L-3,-3 Z" transform="translate(${Math.cos(a) * 160},${-1040 + Math.sin(a) * 30}) scale(2.4)" fill="#ffd23f" ${st(2)}/>`; }
  return s;
}
function clinic(t) {
  let s = `<rect width="1080" height="1920" fill="#e3fafc"/>`;
  for (let y = 0; y < 1430; y += 120) s += `<path d="M0,${y} H1080" stroke="#c5f6fa" stroke-width="6"/>`;
  s += `<rect x="0" y="1430" width="1080" height="490" fill="#99e9f2"/><path d="M0,1430 H1080" ${st(7)}/>`;
  s += `<rect x="640" y="250" width="340" height="230" rx="16" fill="#fff" ${st(8)}/><text x="810" y="345" text-anchor="middle" font-family="Fredoka" font-size="40" fill="#1098ad">FACULTAD</text><text x="810" y="410" text-anchor="middle" font-family="Fredoka" font-size="34" fill="#1098ad">ODONTOLOGÍA</text>`;
  // lámpara
  s += `<path d="M300,0 V230 L470,330" fill="none" ${st(14)}/><ellipse cx="500" cy="350" rx="90" ry="50" fill="#fff3bf" ${st(8)}/>`;
  return s;
}
function chairBack(x, y) {
  return `<g transform="translate(${x},${y})"><rect x="-230" y="-1150" width="460" height="760" rx="120" fill="#15aabf" ${st(9)}/><rect x="-260" y="-420" width="520" height="140" rx="40" fill="#1098ad" ${st(9)}/></g>`;
}
// oscuridad con un círculo de luz (cx, cy, r)
function darkness(cx, cy, r, op) {
  return `<defs><radialGradient id="lightG" gradientUnits="userSpaceOnUse" cx="${cx}" cy="${cy}" r="${Math.max(r, 1)}"><stop offset="0" stop-color="#000" stop-opacity="0"/><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#0b0820" stop-opacity="${op}"/></radialGradient></defs>
    <rect width="1080" height="1920" fill="url(#lightG)"/>`;
}

// ---------- escenas ----------
const SCENES = Object.assign({}, INTRO_SCENES, {
  V(lt, t) { return voteScene(lt, t, 4); },
  // «Problema: le queda un euro con veinte.»
  P(lt, t) {
    let s = clinic(t) + `<rect width="1080" height="1920" fill="${OUT}" opacity=".3"/>`;
    const k = back(seg(lt, 0.05, 0.35));
    s += `<g transform="translate(540,620) rotate(3) scale(${k})"><rect x="-330" y="-200" width="660" height="400" rx="26" fill="#fff" ${st(9)}/>
      <text x="0" y="-110" text-anchor="middle" font-family="Fredoka" font-size="44" fill="${OUT}">CLÍNICA DENTAL</text>
      <text x="-280" y="-20" font-family="Fredoka" font-size="40" fill="#495057">Limpieza</text><text x="280" y="-20" text-anchor="end" font-family="Lilita" font-size="48" fill="${OUT}">80 €</text>
      <text x="-280" y="60" font-family="Fredoka" font-size="40" fill="#495057">Blanqueamiento</text><text x="280" y="60" text-anchor="end" font-family="Lilita" font-size="48" fill="${OUT}">300 €</text>
      <text x="-280" y="140" font-family="Fredoka" font-size="40" fill="#495057">Lo de Hugo</text><text x="280" y="140" text-anchor="end" font-family="Lilita" font-size="48" fill="#e03131">???</text></g>`;
    const tw = wordTime(5, 'euro') - TL.lines[5].start, kk = back(seg(lt, tw, tw + 0.3));
    if (kk > 0) s += `<g transform="translate(540,1050) rotate(-6) scale(${kk})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="170" fill="#ff2e48" stroke="#fff" stroke-width="20" paint-order="stroke">1,20 €</text></g>`;
    s += place(hugo({ t, mood: 'sad', mouth: 'sad', look: 0, lookY: -1, blink: blinkAt(t) }), 540, 2700, 1.2);
    return s;
  },
  // «Así que va a la facultad, donde los estudiantes practican gratis.»
  F(lt, t) {
    let s = `<rect width="1080" height="1920" fill="#a5d8ff"/><rect x="0" y="1450" width="1080" height="470" fill="#ced4da"/><path d="M0,1450 H1080" ${st(7)}/>`;
    s += `<rect x="90" y="420" width="900" height="1030" fill="#f1f3f5" ${st(9)}/><path d="M60,420 L540,220 L1020,420 Z" fill="#dee2e6" ${st(9)}/>`;
    for (const x of [170, 330, 750, 910]) s += `<rect x="${x - 30}" y="520" width="60" height="930" fill="#fff" ${st(6)}/>`;
    s += `<rect x="410" y="1100" width="260" height="350" fill="#495057" ${st(8)}/>`;
    s += `<text x="540" y="380" text-anchor="middle" font-family="Lilita" font-size="56" fill="${OUT}">FACULTAD DE ODONTOLOGÍA</text>`;
    const tw = wordTime(6, 'gratis') - TL.lines[6].start, k = back(seg(lt, tw - 0.1, tw + 0.2));
    if (k > 0) s += `<g transform="translate(540,760) rotate(-8) scale(${k})"><rect x="-300" y="-110" width="600" height="220" rx="30" fill="#40c057" ${st(10)}/><text x="0" y="45" text-anchor="middle" font-family="Lilita" font-size="150" fill="#fff">¡GRATIS!</text></g>`;
    const walk = easeOut(seg(lt, 0, 1.4));
    s += place(hugo({ t, mood: 'normal', mouth: 'smile', look: 1, blink: blinkAt(t) }), lerp(-100, 300, walk), 1880 - Math.abs(Math.sin(t * 9)) * 14 * (walk < 1 ? 1 : 0), 0.85);
    return s;
  },
  // «El estudiante le abre la boca… y se desmaya.»
  D(lt, t) {
    let s = clinic(t) + chairBack(560, 1900);
    const td = wordTime(7, 'desmaya') - TL.lines[7].start - 0.05, fall = easeInOut(seg(lt, td, td + 0.45));
    const open = lt > 0.5;
    s += place(hugo({ t, mood: open ? 'normal' : 'sad', mouth: open ? 'open' : 'o', look: -1, lookY: -1, blink: false, sucio: open ? 0.6 : 0 }), 560, 1960, 1.0);
    if (open) for (let i = 0; i < 4; i++) { const k = (lt * 1.2 + i / 4) % 1; s += `<circle cx="${560 - 100 - k * 260}" cy="${1230 - k * 200}" r="${30 + k * 50}" fill="#9ccc4a" opacity="${(1 - k) * 0.7}"/>`; }
    s += place(student({ faint: fall > 0.3, t, look: -1 }), 900, 1900, 0.8, -90 * fall);
    return s;
  },
  // «Tres horas y dos estudiantes desmayados después…»
  T(lt, t) {
    let s = clinic(t) + `<rect width="1080" height="1920" fill="${OUT}" opacity=".55"/>`;
    s += place(student({ faint: true, t, hair: '#6b3f22' }), 1000, 1880, 0.62, -90);
    s += place(student({ faint: true, t: t + 1, hair: '#e8590c' }), 80, 1640, 0.62, 90);
    const k = back(seg(lt, 0.05, 0.35));
    s += `<g transform="translate(540,560) scale(${k})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="150" fill="#ffd23f">3 HORAS</text><text x="0" y="130" text-anchor="middle" font-family="Lilita" font-size="110" fill="#fff">DESPUÉS…</text></g>`;
    s += `<g transform="translate(540,960)"><circle r="90" fill="#fff" ${st(8)}/><path d="M0,0 L0,-60 M0,0 L${50 * Math.cos(lt * 10)},${50 * Math.sin(lt * 10)}" ${st(8)}/></g>`;
    return s;
  },
  // «Hugo tiene los dientes blancos.» / «Demasiado blancos.»
  W(lt, t) {
    const L = TL.lines, much = t >= L[10].start - 0.08, km = seg(t, L[10].start - 0.08, L[10].start + 0.4);
    let s = clinic(t);
    s += place(hugo({ t, mood: 'normal', mouth: 'grin', teeth: much ? 'glow' : 'white', look: 0, blink: much ? true : blinkAt(t) }), 540, 2240 + Math.sin(t * 3) * 4, 1.6);
    for (let i = 0; i < 5; i++) { const a = (t * 2.5 + i * 1.2) % 2, sc = Math.sin(clamp(a / 2) * Math.PI); s += `<path d="M0,-40 Q6,-6 40,0 Q6,6 0,40 Q-6,6 -40,0 Q-6,-6 0,-40 Z" transform="translate(${[300, 780, 250, 830, 540][i]},${[1000, 960, 1250, 1230, 1380][i]}) scale(${sc * (much ? 2 : 1.2)})" fill="#fff" ${st(4)}/>`; }
    if (much) {
      s += `<defs><radialGradient id="glowW" gradientUnits="userSpaceOnUse" cx="540" cy="1080" r="900"><stop offset="0" stop-color="#fff" stop-opacity="${0.95 * km}"/><stop offset=".35" stop-color="#fff9db" stop-opacity="${0.6 * km}"/><stop offset="1" stop-color="#fff9db" stop-opacity="0"/></radialGradient></defs><rect width="1080" height="1920" fill="url(#glowW)"/>`;
      for (let i = 0; i < 16; i++) { const a = i / 16 * Math.PI * 2 + t * 0.6; s += `<path d="M540,1080 L${540 + Math.cos(a) * 1400},${1080 + Math.sin(a) * 1400} L${540 + Math.cos(a + 0.08) * 1400},${1080 + Math.sin(a + 0.08) * 1400} Z" fill="#fff" opacity="${0.35 * km}"/>`; }
    }
    return s;
  },
  // «En la oficina se va la luz… y Hugo la ilumina sonriendo.»
  O(lt, t) {
    const tl = wordTime(11, 'luz'), ti = wordTime(11, 'ilumina') - 0.15;
    let s = classroom(t);
    s += place(laura({ look: -1, blink: blinkAt(t, 0.6), mouth: t > ti ? 'smile' : 'flat', brow: t > ti ? 'up' : 'normal' }), 790, 1720, 0.8);
    s += place(hugo({ t, mood: 'normal', mouth: t > ti ? 'grin' : 'o', teeth: 'glow', look: 1 }), 300, 1720, 0.8);
    s += desk(300, 1510, 400);
    const off = seg(t, tl, tl + 0.12), on = easeOut(seg(t, ti, ti + 0.4));
    if (off > 0) s += darkness(300, 1720 - 720 * 0.8, lerp(1, 900, on), 0.96 * off);
    return s;
  },
  // «Y Laura, por primera vez, se ríe con él.»
  R(lt, t) {
    let s = classroom(t);
    const tr = wordTime(12, 'ríe') - TL.lines[12].start;
    const laugh = lt > tr - 0.1;
    s += place(laura({ look: -1, blink: false, mouth: laugh ? 'laugh' : 'smile' }), 720, 1720 + (laugh ? Math.abs(Math.sin(t * 16)) * -10 : 0), 0.85, laugh ? Math.sin(t * 16) * 2 : 0);
    s += place(hugo({ t, mood: laugh ? 'dream' : 'normal', mouth: 'grin', teeth: 'white', look: 1, blink: blinkAt(t, 1.1) }), 320, 1720, 0.85);
    if (laugh) for (let i = 0; i < 3; i++) { const k = (lt * 1.3 + i / 3) % 1; s += `<text x="${820 + i * 50}" y="${900 - k * 200}" font-family="Lilita" font-size="70" fill="#e64980" opacity="${Math.sin(k * Math.PI)}" ${st(6)} paint-order="stroke">JA</text>`; }
    if (laugh) for (let i = 0; i < 5; i++) { const k = ((lt * 0.8 + i / 5) % 1); s += heart(lerp(380, 640, k) + Math.sin(k * 9 + i) * 30, lerp(950, 600, k), 0.45, '#ff3b5c', Math.sin(k * Math.PI)); }
    return s;
  },
  N(lt, t) { return nextVoteScene(lt, t, 13, NEXT.A, NEXT.B, { teeth: 'white' }); },
});
const SCENE_OF = ['I0', 'I1', 'I2', 'I3', 'V', 'P', 'F', 'D', 'T', 'W', 'W', 'O', 'R', 'N', 'N'];

// marcador: oculto en la votación; al reírse Laura, GLOW 4→9 % y barra 8→12 %
EP.hud = function (t) {
  const L = TL.lines, tu = wordTime(3, 'dos'), tc = wordTime(12, 'ríe') + 0.3;
  const up = easeOut(seg(t, tc, tc + 0.8));
  return {
    show: t < L[4].start - 0.1 ? seg(t, L[3].start, L[3].start + 0.35) : seg(t, L[5].start - 0.08, L[5].start + 0.25),
    days: t < tu ? 29 : 28,
    dayPulse: Math.max(0, Math.sin(seg(t, tu, tu + 0.4) * Math.PI)),
    glow: lerp(4, 9, up), glowPulse: Math.max(0, Math.sin(seg(t, tc, tc + 0.5) * Math.PI)),
    pct: lerp(8, 12, up), extra: plusBadge(t, tc, 5),
  };
};
EP.shake = (sc, lt, t) => {
  const td = wordTime(7, 'desmaya') + 0.4;
  if (sc === 'D' && t > td && t < td + 0.4) return 14 * (1 - seg(t, td, td + 0.4));
  return 0;
};

// ---------- efectos de sonido ----------
window.sfxCues = function () {
  const L = TL.lines, c = [];
  const add = (t, f, v, d) => c.push({ t: Math.max(0, t), f, v, d });
  introCues(add);
  voteCues(add, 4);
  // precios y 1,20 €
  add(L[5].start - 0.08, 'whoosh', 0.4); add(L[5].start + 0.05, 'pop', 0.5);
  add(wordTime(5, 'euro'), 'fail', 0.25);
  // facultad
  add(L[6].start - 0.08, 'whoosh', 0.4);
  add(wordTime(6, 'gratis') - 0.1, 'stamp', 0.6); add(wordTime(6, 'gratis'), 'sparkle', 0.4);
  // boca abierta y desmayo
  add(L[7].start + 0.5, 'burp', 0.35);
  add(wordTime(7, 'desmaya') + 0.4, 'impact', 0.6); add(wordTime(7, 'desmaya') + 0.5, 'boing', 0.35);
  // tres horas
  add(L[8].start - 0.08, 'whoosh', 0.35);
  for (let i = 0; i < 6; i++) add(L[8].start + 0.2 + i * 0.22, 'tick', 0.35);
  // dientes blancos y demasiado blancos
  add(L[9].start, 'sparkle', 0.45);
  add(L[10].start - 0.05, 'magic', 0.55); add(L[10].start + 0.1, 'ding', 0.4);
  // apagón e iluminación
  add(wordTime(11, 'luz'), 'impact', 0.4);
  add(wordTime(11, 'ilumina') - 0.15, 'magic', 0.45);
  // risa de Laura
  add(wordTime(12, 'ríe') - 0.05, 'sparkle', 0.4);
  add(wordTime(12, 'ríe') + 0.3, 'ding', 0.45);
  nextVoteCues(add, 13, NEXT.A, NEXT.B);
  return c;
};

start();

// Capítulo 5 — Día 4: la peluquería. Adiós al tazón. Votación siguiente: ROPA NUEVA vs LENTILLAS.
const EP = {
  num: 5, day: 4, dayWord: 'cuatro', daysLeft: 26, glow: 0.14, start: { g: 0.14, sucio: 0 }, sucio: 0, teeth: 'white',
  coverHugo: { sucio: 0, mouth: 'grin', hair: 'fade' }, coverLine1: 'ADIÓS AL', coverLine2: 'CORTE DE TAZÓN',
  vote: {
    a: { label: 'PELUQUERÍA', pct: 65, color: '#e64980', icon: SCISSORS },
    b: { label: 'ROPA NUEVA', pct: 35, color: '#4dabf7', icon: SHIRT },
  },
};
const NEXT = {
  A: { label: 'ROPA NUEVA', word: 'ropa', color: '#4dabf7', icon: SHIRT },
  B: { label: 'LENTILLAS', word: 'lentillas', color: '#7950f2', icon: LENS },
};

const LINES = [
  ...introLines(),
  { text: 'Habéis votado… y ganó la peluquería.', hl: ['peluquería'] },
  { text: 'Hugo lleva el mismo corte desde los doce años.', hl: ['doce', 'años'] },
  { text: 'Se lo hacía su madre… con un tazón.', hl: ['madre', 'tazón'] },
  { text: 'La peluquera ve el pelo… y pide refuerzos.', hl: ['refuerzos'] },
  { text: 'Dos horas y tres tijeras rotas después…', hl: ['tres', 'tijeras', 'rotas'] },
  { text: 'Hugo sale con un degradado.', hl: ['degradado'] },
  { text: 'En la oficina, Laura no le reconoce.', hl: ['no', 'reconoce'] },
  { text: 'Le pregunta si es nuevo.', hl: ['nuevo'] },
  { text: 'Y Marcos, por primera vez, se pone nervioso.', hl: ['nervioso'], red: ['marcos'] },
  { text: 'Mañana: ¿ropa nueva o lentillas?', hl: ['ropa', 'nueva', 'lentillas'] },
  { text: 'Comenta, y a lo mejor sales tú en el día cinco.', hl: ['sales', 'tú'] },
];

// ---------- personajes y decorados ----------
function hairdresser(o = {}) {
  const { look = 0, scared = false, walkie = false, t = 0 } = o;
  const skin = '#f3c9a8';
  let s = '';
  s += `<rect x="-62" y="-400" width="54" height="370" rx="22" fill="#212529" ${st()}/><rect x="8" y="-400" width="54" height="370" rx="22" fill="#212529" ${st()}/>`;
  s += `<path d="M-86,-8 Q-86,-50 -38,-50 Q-6,-50 -6,-8 Z" fill="#e64980" ${st()}/><path d="M86,-8 Q86,-50 38,-50 Q6,-50 6,-8 Z" fill="#e64980" ${st()}/>`;
  s += arm(-112, -640, 10, '#f8f0fc', 52, 230, skin) + arm(112, -640, walkie ? -150 : -10, '#f8f0fc', 52, 230, skin);
  if (walkie) s += `<g transform="translate(${112 + Math.sin(-150 * Math.PI / 180) * -240},${-640 + Math.cos(-150 * Math.PI / 180) * 240 - 40})"><rect x="-26" y="-60" width="52" height="100" rx="10" fill="#495057" ${st(5)}/><path d="M10,-60 V-110" ${st(6)}/></g>`;
  s += `<path d="M-120,-665 Q-135,-520 -105,-330 L105,-330 Q135,-520 120,-665 Q0,-695 -120,-665 Z" fill="#f8f0fc" ${st()}/>`;
  s += `<path d="M-90,-600 L90,-600 L100,-330 L-100,-330 Z" fill="#212529" ${st(6)}/><path d="M30,-520 l30,40 M60,-520 l-30,40" stroke="#ced4da" stroke-width="7"/>`;
  s += `<rect x="-26" y="-715" width="52" height="60" fill="${skin}" ${st()}/><circle cx="0" cy="-840" r="150" fill="${skin}" ${st()}/>`;
  s += `<circle cx="0" cy="-1010" r="70" fill="#f06595" ${st()}/><path d="M-150,-850 Q-155,-990 0,-995 Q155,-990 150,-850 Q90,-930 0,-930 Q-90,-930 -150,-850 Z" fill="#f06595" ${st()}/>`;
  for (const sx of [-1, 1]) {
    const x = sx * 55, y = -845, r = scared ? 36 : 28;
    s += `<ellipse cx="${x}" cy="${y}" rx="${r}" ry="${r + 4}" fill="#fff" ${st(6)}/><circle cx="${x + look * 9}" cy="${y + 4}" r="${scared ? 9 : 13}" fill="${OUT}"/>`;
    s += `<path d="M${x + sx * 18},${y - 32} l${sx * 14},-14" ${st(5)}/>`;
  }
  s += scared ? `<ellipse cx="0" cy="-740" rx="30" ry="38" fill="#7a1f2e" ${st(6)}/>` : `<path d="M-36,-745 Q0,-715 36,-745 Z" fill="#c2255c" ${st(5)}/>`;
  s += `<ellipse cx="-95" cy="-775" rx="26" ry="14" fill="#ff8aa6" opacity=".6"/><ellipse cx="95" cy="-775" rx="26" ry="14" fill="#ff8aa6" opacity=".6"/>`;
  return s;
}
function salon(t) {
  let s = `<rect width="1080" height="1920" fill="#fff0f6"/>`;
  for (let x = 0; x < 1080; x += 120) s += `<rect x="${x}" y="0" width="60" height="1430" fill="#ffdeeb" opacity=".6"/>`;
  s += `<rect x="0" y="1430" width="1080" height="490" fill="#f8f9fa"/><path d="M0,1430 H1080" ${st(7)}/>`;
  for (let x = 0; x < 1080; x += 120) for (let y = 1430; y < 1920; y += 120) if (((x + y) / 120) % 2 === 0) s += `<rect x="${x}" y="${y}" width="120" height="120" fill="#343a40"/>`;
  s += `<rect x="220" y="300" width="640" height="760" rx="320" fill="#d0ebff" ${st(10)}/><path d="M320,450 L420,350 M330,560 L520,370" stroke="#fff" stroke-width="14" opacity=".7"/>`;
  s += `<text x="540" y="230" text-anchor="middle" font-family="Lilita" font-size="70" fill="#e64980">PELUQUERÍA</text>`;
  return s;
}

// ---------- escenas ----------
const SCENES = Object.assign({}, INTRO_SCENES, {
  V(lt, t) { return voteScene(lt, t, 4); },
  // «Hugo lleva el mismo corte desde los doce años.»
  C(lt, t) {
    let s = classroom(t);
    s += place(hugo({ t, mood: 'normal', mouth: 'smile', look: 0, lookY: -1, blink: blinkAt(t) }), 540, 2240 + Math.sin(t * 3) * 4, 1.6);
    const k = back(seg(lt, 0.2, 0.5));
    s += `<g transform="translate(830,330) rotate(6) scale(${k})"><rect x="-200" y="-70" width="400" height="140" rx="26" fill="#fff" ${st(8)}/><text x="0" y="28" text-anchor="middle" font-family="Lilita" font-size="80" fill="#e03131">ESTE CORTE</text></g>`;
    s += `<path d="M760,410 Q720,470 680,500" fill="none" ${st(10)} opacity="${k}"/><path d="M680,500 l10,-38 m-10,38 l36,-12" ${st(10)} opacity="${k}"/>`;
    const tw = wordTime(5, 'doce') - TL.lines[5].start, kd = back(seg(lt, tw, tw + 0.3));
    if (kd > 0) s += `<g transform="translate(260,420) rotate(-8) scale(${kd})"><rect x="-190" y="-70" width="380" height="140" rx="26" fill="#ffd43b" ${st(8)}/><text x="0" y="28" text-anchor="middle" font-family="Lilita" font-size="80" fill="${OUT}">DESDE 2012</text></g>`;
    return s;
  },
  // «Se lo hacía su madre… con un tazón.» — recuerdo en sepia
  M(lt, t) {
    let s = `<rect width="1080" height="1920" fill="#f3e3c3"/><rect y="1350" width="1080" height="570" fill="#d9bf8f"/><path d="M0,1350 H1080" ${st(7)}/>`;
    s += `<rect x="140" y="300" width="300" height="380" rx="10" fill="#e8d3a8" ${st(8)}/><text x="290" y="520" text-anchor="middle" font-family="Fredoka" font-size="40" fill="#8a6d3b">2012</text>`;
    s += place(hugo({ t, g: 1, beard: 'none', hair: 'bowl', sucio: 0, teeth: 'none', mood: 'sad', mouth: 'o', look: 0, lookY: -1, grime: false }), 540, 1900, 0.95);
    const tz = wordTime(6, 'tazón') - TL.lines[6].start, kb = easeOut(seg(lt, tz - 0.25, tz + 0.1));
    s += `<g transform="translate(540,${lerp(400, 880, kb)})"><path d="M-230,0 Q-230,-230 0,-240 Q230,-230 230,0 Z" fill="#4dabf7" ${st(10)}/><path d="M-250,0 H250" ${st(14)}/><circle cx="-90" cy="-110" r="22" fill="#fff" opacity=".7"/></g>`;
    // mano de la madre con tijeras, dando vueltas alrededor del tazón
    const a = lt * 3, hx = 540 + Math.cos(a) * 300, hy = 900 + Math.sin(a) * 60;
    s += `<g transform="translate(${hx},${hy}) rotate(${Math.sin(t * 20) * 10})"><path d="M60,0 L300,40" ${st(40)}/><path d="M60,0 L300,40" stroke="#ffc9c9" stroke-width="30" stroke-linecap="round"/><circle cx="40" cy="0" r="42" fill="#f3c9a8" ${st(7)}/>
      <path d="M0,-10 L-110,-60 M0,10 L-110,60" ${st(14)}/><path d="M0,-10 L-110,-60 M0,10 L-110,60" stroke="#dee2e6" stroke-width="6" stroke-linecap="round"/></g>`;
    s += `<rect width="1080" height="1920" fill="#704214" opacity=".18"/><rect x="20" y="20" width="1040" height="1880" fill="none" stroke="#fff" stroke-width="16" opacity=".5"/>`;
    s += `<text x="540" y="1600" text-anchor="middle" font-family="Fredoka" font-size="50" fill="#8a6d3b" opacity=".9">— HACE 14 AÑOS —</text>`;
    return s;
  },
  // «La peluquera ve el pelo… y pide refuerzos.»
  P(lt, t) {
    let s = salon(t);
    const tr = wordTime(7, 'refuerzos') - TL.lines[7].start, scared = lt > 0.5, walkie = lt > tr - 0.3;
    s += place(hugo({ t, mood: 'normal', mouth: 'smile', look: 0, lookY: 0, blink: blinkAt(t) }), 470, 1960, 1.0);
    s += place(hairdresser({ t, scared, walkie, look: -1 }), 850 + (scared ? Math.sin(t * 40) * 4 : 0), 1940, 0.9);
    if (walkie) s += bubble(760, 450, '¡REFUERZOS!', back(seg(lt, tr - 0.2, tr + 0.1)), '#e03131', 90);
    if (scared && !walkie) s += `<text x="900" y="760" font-family="Lilita" font-size="140" fill="#e03131" ${st(10)} paint-order="stroke">!!</text>`;
    return s;
  },
  // «Dos horas y tres tijeras rotas después…»
  T(lt, t) {
    let s = salon(t) + `<rect width="1080" height="1920" fill="${OUT}" opacity=".6"/>`;
    // los refuerzos: Juan, el entrenador del día 3, sudando con unas tijeras rotas
    const kj = easeOut(seg(lt, 0, 0.4));
    s += place(juan({ t, armR: -120 + Math.sin(t * 7) * 8, mouth: 'open', look: -1 }), lerp(1400, 820, kj), 2050, 0.8);
    s += sweat(820, 1000, t, 4);
    s += `<g transform="translate(${lerp(1400, 820, kj) + 150},${1180 + Math.sin(t * 7) * 6}) rotate(-30)"><path d="M0,0 L-70,-90 M0,0 L40,-40" ${st(14)}/><path d="M0,0 L-70,-90 M0,0 L40,-40" stroke="#dee2e6" stroke-width="6"/></g>`;
    if (lt > 0.4) s += `<g transform="translate(830,1720) scale(${back(seg(lt, 0.4, 0.7))})"><rect x="-150" y="-50" width="300" height="100" rx="24" fill="#e03131" ${st(7)}/><text x="0" y="22" text-anchor="middle" font-family="Lilita" font-size="60" fill="#fff">REFUERZO</text></g>`;
    const k = back(seg(lt, 0.05, 0.35));
    s += `<g transform="translate(540,560) scale(${k})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="150" fill="#ffd23f">2 HORAS</text><text x="0" y="130" text-anchor="middle" font-family="Lilita" font-size="110" fill="#fff">DESPUÉS…</text></g>`;
    for (let i = 0; i < 3; i++) {
      const tw = wordTime(8, 'tijeras') - TL.lines[8].start + i * 0.25, f = seg(lt, tw, tw + 0.9);
      if (f <= 0) continue;
      s += `<g transform="translate(${260 + i * 280},${lerp(860, 1600, f * f)}) rotate(${f * 300 * (i % 2 ? 1 : -1)}) scale(.9)">
        <path d="M-10,-10 L-90,-120" ${st(18)}/><path d="M-10,-10 L-90,-120" stroke="#dee2e6" stroke-width="8"/><path d="M10,-10 L60,-60" ${st(18)}/><path d="M10,-10 L60,-60" stroke="#dee2e6" stroke-width="8"/>
        <circle cx="-40" cy="70" r="36" fill="none" stroke="${OUT}" stroke-width="18"/><circle cx="-40" cy="70" r="36" fill="none" stroke="#f03e3e" stroke-width="8"/>
        <path d="M70,-70 l30,-10 l-10,30 Z" fill="#dee2e6" ${st(5)}/></g>`;
    }
    return s;
  },
  // «Hugo sale con un degradado.» — el gran cambio
  F(lt, t) {
    let s = burst(540, 900, t, '#fab005', '#ffd43b');
    s += place(hugo({ t, hair: 'fade', mood: 'normal', mouth: 'grin', look: 0, blink: blinkAt(t, 0.4) }), 540, 2240 + Math.sin(t * 3) * 4, 1.6);
    for (let i = 0; i < 7; i++) { const a = (t * 2.2 + i * 1.1) % 2, sc = Math.sin(clamp(a / 2) * Math.PI); s += `<path d="M0,-40 Q6,-6 40,0 Q6,6 0,40 Q-6,6 -40,0 Q-6,-6 0,-40 Z" transform="translate(${[220, 860, 160, 920, 320, 760, 540][i]},${[560, 520, 900, 880, 360, 340, 250][i]}) scale(${sc * 1.6})" fill="#fff" ${st(5)}/>`; }
    const fl = 1 - seg(lt, 0, 0.35);
    if (fl > 0) s += `<rect width="1080" height="1920" fill="#fff" opacity="${fl}"/>`;
    return s;
  },
  // «En la oficina, Laura no le reconoce.» / «Le pregunta si es nuevo.» / «Y Marcos… se pone nervioso.»
  O(lt, t) {
    const L = TL.lines, ask = t >= L[11].start - 0.05, nerv = t >= L[12].start - 0.08;
    let s = classroom(t);
    s += place(hugo({ t, hair: 'fade', mood: ask ? 'dream' : 'normal', mouth: 'grin', look: 1, blink: blinkAt(t, 1.2) }), 260, 1760, 0.75);
    s += place(laura({ look: -1, blink: false, mouth: ask ? 'smile' : 'flat', brow: 'up' }), 560, 1760, 0.75);
    if (!ask && lt > 0.4) s += bubble(560, 560, '¿?', back(seg(lt, 0.4, 0.7)), '#7950f2', 110);
    if (ask && !nerv) s += bubble(600, 560, '¿ERES NUEVO?', back(seg(t, L[11].start, L[11].start + 0.3)), '#e64980', 80);
    s += place(marcos({ t }), 900 + (nerv ? Math.sin(t * 40) * 5 : 0), 1780, 0.75);
    if (nerv) { s += sweat(900, 880, t, 4); s += `<text x="820" y="760" font-size="110">😰</text>`; }
    if (ask) for (let i = 0; i < 4; i++) { const k = ((t * 0.8 + i / 4) % 1); s += heart(lerp(520, 330, k), lerp(980, 700, k), 0.4, '#ff6f9f', Math.sin(k * Math.PI)); }
    return s;
  },
  N(lt, t) { return nextVoteScene(lt, t, 13, NEXT.A, NEXT.B, { hair: 'fade' }); },
});
const SCENE_OF = ['I0', 'I1', 'I2', 'I3', 'V', 'C', 'M', 'P', 'T', 'F', 'O', 'O', 'O', 'N', 'N'];

// marcador: con el degradado GLOW 14→22 %; cuando Laura pregunta si es nuevo, barra 17→25 %
EP.hud = function (t) {
  const L = TL.lines, tu = wordTime(3, 'cuatro'), tg = wordTime(9, 'degradado') + 0.1, tn = wordTime(11, 'nuevo') + 0.2;
  const ug = easeOut(seg(t, tg, tg + 0.8)), ub = easeOut(seg(t, tn, tn + 0.8));
  let hx = 0;
  if (t > tn && t < tn + 0.5) hx = Math.sin(t * 80) * 8 * (1 - seg(t, tn, tn + 0.5));
  return {
    show: t < L[4].start - 0.1 ? seg(t, L[3].start, L[3].start + 0.35) : seg(t, L[5].start - 0.08, L[5].start + 0.25),
    days: t < tu ? 27 : 26,
    dayPulse: Math.max(0, Math.sin(seg(t, tu, tu + 0.4) * Math.PI)),
    glow: lerp(14, 22, ug), glowPulse: Math.max(0, Math.sin(seg(t, tg, tg + 0.5) * Math.PI)),
    pct: lerp(17, 25, ub), hx, extra: plusBadge(t, tg, 8),
  };
};
EP.shake = (sc, lt, t) => (sc === 'F' && lt < 0.4 ? 12 * (1 - lt / 0.4) : 0);

// ---------- efectos de sonido ----------
window.sfxCues = function () {
  const L = TL.lines, c = [];
  const add = (t, f, v, d) => c.push({ t: Math.max(0, t), f, v, d });
  introCues(add);
  voteCues(add, 4);
  // el corte de siempre
  add(L[5].start + 0.2, 'pop', 0.45); add(wordTime(5, 'doce'), 'stamp', 0.5);
  // recuerdo con el tazón
  add(L[6].start - 0.08, 'whoosh', 0.45);
  for (let i = 0; i < 5; i++) add(L[6].start + 0.15 + i * 0.3, 'snip', 0.4);
  add(wordTime(6, 'tazón') - 0.1, 'boing', 0.45);
  // peluquera asustada y refuerzos
  add(L[7].start + 0.5, 'impact', 0.35);
  add(wordTime(7, 'refuerzos') - 0.3, 'tick', 0.4); add(wordTime(7, 'refuerzos') - 0.15, 'tick', 0.4);
  add(wordTime(7, 'refuerzos'), 'horn', 0.3);
  // tijeras rotas
  add(L[8].start - 0.08, 'whoosh', 0.35);
  add(L[8].start + 0.05, 'whistle', 0.35);
  for (let i = 0; i < 3; i++) add(wordTime(8, 'tijeras') + i * 0.25 + 0.6, 'clank', 0.4);
  // el degradado
  add(L[9].start - 0.08, 'impact', 0.5);
  add(L[9].start, 'magic', 0.55); add(L[9].start + 0.15, 'sparkle', 0.5);
  // oficina
  add(L[10].start - 0.08, 'whoosh', 0.35);
  add(L[10].start + 0.4, 'pop', 0.45);
  add(L[11].start, 'pop', 0.45); add(wordTime(11, 'nuevo') + 0.2, 'ding', 0.45);
  add(L[12].start, 'fail', 0.25);
  nextVoteCues(add, 13, NEXT.A, NEXT.B);
  return c;
};

start();

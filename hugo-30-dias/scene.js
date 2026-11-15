// Hugo · 30 días — Capítulo 1
// render(t) dibuja el fotograma del segundo t. Todo es SVG generado por código.

// ---------- utilidades ----------
const OUT = '#1d1630';
const st = (w = 7) => `stroke="${OUT}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"`;
const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
const lerp = (a, b, k) => a + (b - a) * k;
const seg = (t, a, b) => clamp((t - a) / (b - a));
const easeOut = k => 1 - Math.pow(1 - k, 3);
const easeInOut = k => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2);
const back = k => { const c1 = 1.9, c3 = c1 + 1; return 1 + c3 * Math.pow(k - 1, 3) + c1 * Math.pow(k - 1, 2); };
const HEART = 'M0,30 C-60,-15 -50,-75 0,-42 C50,-75 60,-15 0,30 Z';
const heart = (x, y, s, fill = '#ff4d6d', op = 1, rot = 0) =>
  `<path d="${HEART}" transform="translate(${x},${y}) rotate(${rot}) scale(${s})" fill="${fill}" opacity="${op}" ${st(7 / Math.max(s, 0.01))}/>`;
const blinkAt = (t, off = 0) => ((t + off) % 3.3) < 0.12;

// ---------- guion ----------
const LINES = [
  { text: 'Este es Hugo.', hl: ['hugo'] },
  { text: 'Veintiséis años. Repitiendo primero por quinta vez.', hl: ['quinta', 'vez'] },
  { text: 'Lleva una semana sin ducharse.', hl: ['sin', 'ducharse'] },
  { text: 'Y tres años enamorado de Laura.', hl: ['laura'] },
  { text: 'Laura no sabe ni cómo se llama.', hl: ['no', 'sabe'] },
  { text: 'Porque Laura sale con Marcos.', hl: ['marcos'], red: ['marcos'] },
  { text: 'Fiesta de fin de curso: en treinta días.', hl: ['treinta', 'días'] },
  { text: 'Hugo tiene treinta días para cambiar.', hl: ['cambiar'] },
  { text: '¿Por dónde empieza?', hl: ['empieza'] },
  { text: 'Comenta: ducha o gym.', hl: ['ducha', 'gym'] },
  { text: 'Sígueme para el día uno.', hl: ['día', 'uno'] },
];
// glow-up de Hugo en este capítulo (0 = día 0, 1 = transformado)
const GLOW = 0;
const norm = w => w.toLowerCase().replace(/[¿?¡!.,:;]/g, '');

// tiempos: los de timings.json (audio real) o una estimación
const TL = (() => {
  const o = window.TL_OVERRIDE;
  let lines, total;
  if (o && o.lines && o.lines.length === LINES.length) {
    lines = o.lines.map(([s, e]) => ({ start: s, end: e }));
    total = o.total;
  } else {
    let t = 0.35; lines = [];
    for (const L of LINES) { const d = 0.3 + L.text.length / 14.5; lines.push({ start: t, end: t + d }); t += d + 0.4; }
    total = t + 1.0;
  }
  lines.forEach((ln, i) => {
    const words = LINES[i].text.split(' ');
    const w8 = words.map(w => norm(w).length + 2);
    const sum = w8.reduce((a, b) => a + b, 0);
    let acc = 0;
    ln.words = words.map((w, j) => { const ws = ln.start + (ln.end - ln.start) * acc / sum; acc += w8[j]; return { w, n: norm(w), t: ws }; });
    // trozos de subtítulo: máx. 3 palabras, corte tras puntuación
    ln.chunks = []; let cur = [];
    ln.words.forEach((wd, j) => { cur.push(j); if (cur.length === 3 || /[.,:?]$/.test(wd.w)) { ln.chunks.push(cur); cur = []; } });
    if (cur.length) ln.chunks.push(cur);
  });
  return { lines, total };
})();
window.TOTAL = TL.total;
const wordTime = (li, n) => { const w = TL.lines[li].words.find(x => x.n === n); return w ? w.t : 1e9; };

// ---------- personajes ----------
function arm(x, y, ang, fill, w = 64, len = 250, skin = '#f5c6a0', fist = false) {
  return `<g transform="translate(${x},${y}) rotate(${ang})">
    <rect x="${-w / 2}" y="-10" width="${w}" height="${len}" rx="${w / 2}" fill="${fill}" ${st()}/>
    <circle cx="0" cy="${len + 12}" r="${fist ? 44 : 38}" fill="${skin}" ${st()}/>
    ${fist ? `<path d="M-22,${len + 4} h44 M-22,${len + 20} h44" ${st(5)} fill="none"/>` : ''}
  </g>`;
}

function eyes(cx, cy, o) {
  const { mood = 'normal', look = 0, blink = false, lookY = 0 } = o;
  let s = '';
  for (const sx of [-1, 1]) {
    const x = cx + sx * 62;
    if (blink) { s += `<path d="M${x - 34},${cy} Q${x},${cy + 16} ${x + 34},${cy}" fill="none" ${st(7)}/>`; continue; }
    const ry = mood === 'determined' ? 32 : 44;
    s += `<ellipse cx="${x}" cy="${cy}" rx="38" ry="${ry}" fill="#fff" ${st()}/>`;
    if (mood === 'dream') s += heart(x + look * 6, cy + 4, 0.48, '#ff3b5c');
    else {
      const px = x + look * 15, py = cy + 6 + lookY * 14;
      s += `<circle cx="${px}" cy="${py}" r="${mood === 'determined' ? 14 : 18}" fill="${OUT}"/>`;
      s += `<circle cx="${px + 6}" cy="${py - 7}" r="6" fill="#fff"/>`;
    }
  }
  // cejas
  const by = cy - 62;
  if (mood === 'determined') s += `<path d="M${cx - 105},${by - 18} L${cx - 25},${by + 14} M${cx + 105},${by - 18} L${cx + 25},${by + 14}" ${st(16)}/>`;
  else if (mood === 'sad') s += `<path d="M${cx - 100},${by + 10} L${cx - 30},${by - 14} M${cx + 100},${by + 10} L${cx + 30},${by - 14}" ${st(12)}/>`;
  else s += `<path d="M${cx - 95},${by} Q${cx - 62},${by - 18} ${cx - 30},${by} M${cx + 95},${by} Q${cx + 62},${by - 18} ${cx + 30},${by}" fill="none" ${st(11)}/>`;
  return s;
}

function hugo(o = {}) {
  // g: glow-up de 0 (día 0) a 1 (transformado). w: ancho del cuerpo. d: dejadez. fat: gordura.
  const { mood = 'normal', mouth = 'smile', armL = 6, armR = -6, fistR = false, g = GLOW, t = 0, grime = true, look = 0, lookY = 0, blink = false } = o;
  const w = lerp(1.55, 1, g), d = clamp(1 - g * 2.2), fat = clamp(1 - g * 2);
  const skin = d > 0.5 ? '#efbd95' : '#f5c6a0', hood = '#3fae6a', hoodD = '#2b8a50', jeans = '#3b5a9a';
  const hair = '#a8552a', beardC = '#6b3f22';
  const lw = lerp(66, 88, fat), lx = lerp(12, 22, fat);
  let s = '';
  // tufo
  if (grime && d > 0) for (let i = 0; i < 4; i++) {
    const k = ((t * 0.55 + i / 4) % 1), x = (i - 1.5) * 110 * w;
    s += `<path d="M${x},${-600 - k * 520} q30,-40 0,-80 t0,-80 t0,-80" fill="none" stroke="#8cc63f" stroke-width="16" stroke-linecap="round" opacity="${Math.sin(k * Math.PI) * 0.75 * d}"/>`;
  }
  s += `<rect x="${-lx - lw}" y="-400" width="${lw}" height="370" rx="22" fill="${jeans}" ${st()}/>`;
  s += `<rect x="${lx}" y="-400" width="${lw}" height="370" rx="22" fill="${jeans}" ${st()}/>`;
  const so = (lx + lw / 2) - 45, shoe = d > 0.5 ? '#d9d2b8' : '#fff';
  s += `<path d="M${-105 - so},-8 Q${-105 - so},-58 ${-48 - so},-58 Q${-6 - so},-58 ${-6 - so},-8 Z" fill="${shoe}" ${st()}/>`;
  s += `<path d="M${105 + so},-8 Q${105 + so},-58 ${48 + so},-58 Q${6 + so},-58 ${6 + so},-8 Z" fill="${shoe}" ${st()}/>`;
  const shx = 128 * lerp(1, 1.32, fat), aw = lerp(64, 84, fat);
  s += arm(-shx, -640, armL + 6 * fat, hood, aw, 250, skin);
  s += arm(shx, -640, armR - 6 * fat, hood, aw, 250, skin, fistR);
  // barriga con pelos
  if (fat > 0.2) {
    s += `<ellipse cx="0" cy="-392" rx="${125 * w}" ry="${62 * fat}" fill="${skin}" ${st()}/>`;
    let hairs = '';
    for (let i = 0; i < 16; i++) { const x = ((i * 53) % 220) - 110, y = -425 + ((i * 37) % 60); hairs += `M${x},${y} q5,-9 10,-2 `; }
    s += `<path d="${hairs} M0,-440 q-6,20 0,40" fill="none" stroke="#5b3a26" stroke-width="3.5" stroke-linecap="round" opacity="${fat}"/>`;
    s += `<path d="M-8,-378 q8,10 16,0" fill="none" ${st(5)}/>`;
  }
  const hy = lerp(-380, -425, fat);
  s += `<path d="M${-138 * w},-662 Q${-185 * w},-540 ${-128 * w},${hy} L${128 * w},${hy} Q${185 * w},-540 ${138 * w},-662 Q0,-700 ${-138 * w},-662 Z" fill="${hood}" ${st()}/>`;
  s += `<path d="M${-72 * w},${hy - 90} L${72 * w},${hy - 90} L${88 * w},${hy - 18} L${-88 * w},${hy - 18} Z" fill="${hoodD}" ${st(6)}/>`;
  s += `<path d="M-26,-655 L-32,-565 M26,-655 L32,-565" ${st(6)}/>`;
  s += `<circle cx="-32" cy="-560" r="7" fill="#fff" ${st(4)}/><circle cx="32" cy="-560" r="7" fill="#fff" ${st(4)}/>`;
  if (d > 0) s += `<g opacity="${d}"><path d="M${-75 * w},-590 q20,-22 42,-4 q14,22 -8,30 q-26,8 -34,-26 Z" fill="#e8b923"/>
      <path d="M${55 * w},-520 q24,-16 40,4 q8,26 -18,28 q-30,0 -22,-32 Z" fill="#c0392b"/>
      <circle cx="${95 * w}" cy="-610" r="14" fill="${hoodD}"/><circle cx="${-40 * w}" cy="-470" r="11" fill="${hoodD}"/></g>`;
  s += `<rect x="-34" y="-725" width="68" height="75" fill="${skin}" ${st()}/>`;
  s += `<path d="M-110,-668 Q0,-620 110,-668 Q0,-700 -110,-668 Z" fill="${hoodD}" ${st(6)}/>`;
  // cabeza: forma de pera, más ancha abajo (mofletes) cuanto más gordo
  const jw = lerp(0.86, 1.08, fat);
  s += `<circle cx="-186" cy="-842" r="36" fill="${skin}" ${st()}/><circle cx="186" cy="-842" r="36" fill="${skin}" ${st()}/>`;
  s += `<path d="M-150,-1015 Q0,-1055 150,-1015 Q188,-925 ${196 * jw},-800 Q${206 * jw},-668 0,-660 Q${-206 * jw},-668 ${-196 * jw},-800 Q-188,-925 -150,-1015 Z" fill="${skin}" ${st()}/>`;
  // papada
  if (fat > 0.3) s += `<path d="M-125,-688 Q0,-600 125,-688 Q0,-650 -125,-688 Z" fill="${skin}" ${st(7)}/><path d="M-70,-648 Q0,-622 70,-648" fill="none" ${st(5)}/>`;
  // barba
  if (d > 0.3) {
    s += `<g fill="${beardC}" opacity=".92">
      <path d="M-196,-830 Q-200,-720 -115,-680 Q-150,-740 -158,-805 Z"/>
      <path d="M196,-840 Q205,-700 80,-668 Q55,-700 118,-716 Q160,-760 152,-840 Z"/>
      <path d="M-55,-670 Q0,-650 38,-668 Q22,-700 -30,-698 Z"/></g>`;
    let dots = '';
    for (let i = 0; i < 40; i++) { const a = Math.PI * (0.05 + 0.9 * ((i * 0.618) % 1)), r = 120 + ((i * 29) % 70); dots += `<circle cx="${Math.cos(a) * r * 1.05}" cy="${-800 + Math.sin(a) * r * 0.75}" r="3.5"/>`; }
    s += `<g fill="${beardC}" opacity=".75">${dots}</g>`;
    s += `<path d="M-48,-748 q22,-12 42,-2 M8,-750 q22,-6 40,8" fill="none" stroke="${beardC}" stroke-width="7" stroke-linecap="round"/>`;
  } else if (g >= 0.5) {
    s += `<path d="M-192,-830 Q-198,-680 0,-658 Q198,-680 192,-830 Q172,-725 100,-712 Q40,-738 0,-730 Q-40,-738 -100,-712 Q-172,-725 -192,-830 Z" fill="${beardC}"/>`;
    s += `<path d="M-50,-750 Q0,-772 50,-750 Q0,-760 -50,-750 Z" fill="${beardC}" ${st(4)}/>`;
  }
  // granos
  if (d > 0) for (const [x, y, r] of [[-125, -905, 10], [118, -935, 9], [-62, -985, 8], [145, -790, 10], [-160, -775, 9], [35, -1002, 7], [80, -890, 7]])
    s += `<g opacity="${d}"><circle cx="${x}" cy="${y}" r="${r}" fill="#e0493e"/><circle cx="${x - 2}" cy="${y - 2}" r="${r * 0.35}" fill="#fff6d8"/></g>`;
  // pelo
  if (d > 0.5) {
    s += `<path d="M-205,-845 Q-222,-1068 0,-1082 Q222,-1068 205,-845 L192,-885 L172,-900 L150,-935 L128,-912 L100,-952 L66,-922 L36,-958 L2,-928 L-34,-962 L-66,-926 L-104,-958 L-128,-920 L-160,-940 L-176,-895 L-196,-878 Z" fill="${hair}" ${st()}/>`;
    s += `<path d="M-10,-1076 Q-46,-1140 4,-1170 Q-14,-1128 22,-1080 Z" fill="${hair}" ${st(6)}/>`;
    s += `<path d="M-120,-1030 q50,-22 100,-14 M40,-1050 q50,-4 90,16" stroke="#fff" stroke-width="7" stroke-linecap="round" opacity=".4" fill="none"/>`;
  } else {
    s += `<path d="M-188,-880 Q-205,-1045 -40,-1072 Q62,-1118 165,-1052 Q205,-990 188,-880 Q162,-962 90,-978 Q0,-994 -92,-972 Q-162,-962 -188,-880 Z" fill="${hair}" ${st()}/>`;
  }
  // ojos (pequeños, de adulto) con ojeras
  for (const sx of [-1, 1]) {
    const x = sx * 68, y = -850;
    if (d > 0) s += `<path d="M${x - 28},${y + 30} q28,16 56,0" fill="none" stroke="#b07a5a" stroke-width="5" stroke-linecap="round" opacity="${d}"/>`;
    if (blink) { s += `<path d="M${x - 26},${y} Q${x},${y + 12} ${x + 26},${y}" fill="none" ${st(6)}/>`; continue; }
    const ry = mood === 'determined' ? 18 : lerp(30, 24, d);
    s += `<ellipse cx="${x}" cy="${y}" rx="${lerp(30, 25, d)}" ry="${ry}" fill="#fff" ${st(6)}/>`;
    if (mood === 'dream') s += heart(x + look * 4, y + 3, 0.34, '#ff3b5c');
    else {
      const px = x + look * 10, py = y + 4 + lookY * 8;
      s += `<circle cx="${px}" cy="${py}" r="${mood === 'determined' ? 10 : 12}" fill="${OUT}"/><circle cx="${px + 4}" cy="${py - 4}" r="4" fill="#fff"/>`;
    }
  }
  const by = -912;
  if (mood === 'determined') s += `<path d="M-120,${by - 16} L-28,${by + 18} M120,${by - 16} L28,${by + 18}" ${st(18)}/>`;
  else if (mood === 'sad') s += `<path d="M-115,${by + 12} L-34,${by - 12} M115,${by + 12} L34,${by - 12}" ${st(14)}/>`;
  else s += `<path d="M-112,${by + 4} Q-70,${by - 18} -26,${by} M112,${by + 4} Q70,${by - 18} 26,${by}${d > 0.5 ? ` M-30,${by} Q0,${by - 10} 30,${by}` : ''}" fill="none" ${st(d > 0.5 ? 15 : 11)}/>`;
  // gafas torcidas
  if (d > 0.5) s += `<g transform="rotate(-4,0,-850)">
      <path d="M-120,-860 L-186,-875 M120,-860 L186,-875" ${st(9)}/>
      <circle cx="-68" cy="-850" r="54" fill="#dff3ff" fill-opacity=".22" stroke="#4a2e1c" stroke-width="14"/>
      <circle cx="68" cy="-850" r="54" fill="#dff3ff" fill-opacity=".22" stroke="#4a2e1c" stroke-width="14"/>
      <path d="M-16,-856 Q0,-870 16,-856" fill="none" stroke="#4a2e1c" stroke-width="12"/>
      <path d="M-98,-872 l22,-16 M38,-872 l22,-16" stroke="#fff" stroke-width="7" stroke-linecap="round" opacity=".7"/></g>`;
  // nariz
  s += `<path d="M-14,-815 Q-34,-772 -2,-766 Q32,-768 16,-815" fill="${d > 0.5 ? '#f0a88a' : skin}" ${st(6)}/>`;
  // mocos
  if (d > 0) {
    const len = 24 + 16 * Math.sin(t * 2.2);
    s += `<path d="M-14,-770 q-6,${len * 0.5} 0,${len} q9,8 13,-4 q0,${-len * 0.5} -3,${-len} Z" fill="#b5d96a" opacity="${d}" ${st(4)}/>`;
  }
  // boca
  if (mouth === 'smile') s += `<path d="M-42,-718 Q0,-694 42,-718" fill="none" ${st(8)}/>`;
  if (mouth === 'open') s += `<path d="M-48,-728 Q0,-672 48,-728 Z" fill="#7a1f2e" ${st(7)}/>`;
  if (mouth === 'o') s += `<ellipse cx="0" cy="-712" rx="18" ry="22" fill="#7a1f2e" ${st(6)}/>`;
  if (mouth === 'sad') s += `<path d="M-38,-700 Q0,-730 38,-700" fill="none" ${st(8)}/>`;
  if (mouth === 'grit') s += `<rect x="-52" y="-738" width="104" height="36" rx="12" fill="#fff" ${st(7)}/><path d="M-52,-720 H52 M-18,-738 V-702 M18,-738 V-702" ${st(4)}/>`;
  if (mouth === 'chew') s += (Math.sin(t * 22) > 0 ? `<path d="M-44,-726 Q0,-684 44,-726 Z" fill="#7a1f2e" ${st(7)}/>` : `<path d="M-38,-716 Q0,-704 38,-716" fill="none" ${st(8)}/>`) + `<circle cx="-50" cy="-700" r="6" fill="#e8b923"/><circle cx="44" cy="-694" r="5" fill="#e8b923"/>`;
  // mal aliento
  if (grime && d > 0 && ['open', 'chew', 'o', 'grit'].includes(mouth)) for (let i = 0; i < 3; i++) {
    const k = (t * 0.9 + i / 3) % 1;
    s += `<circle cx="${50 + k * 170}" cy="${-712 - k * 90 + Math.sin(k * 8) * 12}" r="${16 + k * 26}" fill="#9ccc4a" opacity="${(1 - k) * 0.55 * d}"/>`;
  }
  // moscas
  if (grime && d > 0) for (let i = 0; i < 3; i++) {
    const a = t * (3.2 + i * 0.7) + i * 2.1;
    const x = Math.cos(a) * 260 + Math.sin(a * 2.3) * 30, y = -880 + Math.sin(a * 1.3) * 140;
    const fl = Math.sin(t * 90 + i) > 0 ? 14 : 8;
    s += `<g transform="translate(${x},${y})" opacity="${d}"><ellipse cx="-8" cy="-8" rx="10" ry="${fl}" fill="#fff" opacity=".8" ${st(3)}/><ellipse cx="8" cy="-8" rx="10" ry="${fl}" fill="#fff" opacity=".8" ${st(3)}/><circle r="10" fill="${OUT}"/></g>`;
  }
  return s;
}

function laura(o = {}) {
  const { mouth = 'smile', look = 0, blink = false, brow = 'normal' } = o;
  const skin = '#f8d3b4', hair = '#4a2c1f', top = '#ff6f9f', topD = '#e2507f', jeans = '#7fa6d9';
  let s = '';
  s += `<path d="M-188,-860 Q-205,-1065 0,-1065 Q205,-1065 188,-860 L205,-545 Q130,-505 95,-590 L-95,-590 Q-130,-505 -205,-545 Z" fill="${hair}" ${st()}/>`;
  s += `<rect x="-70" y="-400" width="58" height="370" rx="22" fill="${jeans}" ${st()}/>`;
  s += `<rect x="12" y="-400" width="58" height="370" rx="22" fill="${jeans}" ${st()}/>`;
  s += `<path d="M-95,-8 Q-95,-52 -44,-52 Q-8,-52 -8,-8 Z" fill="#ffe3ec" ${st()}/>`;
  s += `<path d="M95,-8 Q95,-52 44,-52 Q8,-52 8,-8 Z" fill="#ffe3ec" ${st()}/>`;
  s += arm(-112, -640, 7, top, 56, 240, skin);
  s += arm(112, -640, -7, top, 56, 240, skin);
  s += `<path d="M-122,-662 Q-142,-540 -108,-385 L108,-385 Q142,-540 122,-662 Q0,-692 -122,-662 Z" fill="${top}" ${st()}/>`;
  s += `<path d="M-108,-430 L108,-430" ${st(6)}/><path d="M-60,-560 Q0,-530 60,-560" fill="none" stroke="${topD}" stroke-width="8"/>`;
  s += `<rect x="-26" y="-715" width="52" height="65" fill="${skin}" ${st()}/>`;
  s += `<circle cx="0" cy="-845" r="162" fill="${skin}" ${st()}/>`;
  s += `<path d="M-168,-878 Q-155,-1012 0,-1012 Q155,-1012 168,-878 Q120,-942 40,-932 Q10,-900 -20,-936 Q-100,-952 -168,-878 Z" fill="${hair}" ${st()}/>`;
  s += `<circle cx="-158" cy="-768" r="13" fill="#ffd23f" ${st(5)}/><circle cx="158" cy="-768" r="13" fill="#ffd23f" ${st(5)}/>`;
  s += `<ellipse cx="-98" cy="-775" rx="30" ry="16" fill="#ff8aa6" opacity=".6"/><ellipse cx="98" cy="-775" rx="30" ry="16" fill="#ff8aa6" opacity=".6"/>`;
  for (const sx of [-1, 1]) {
    const x = sx * 58, y = -845;
    if (blink) { s += `<path d="M${x - 30},${y} Q${x},${y + 14} ${x + 30},${y}" fill="none" ${st(7)}/>`; continue; }
    s += `<ellipse cx="${x}" cy="${y}" rx="32" ry="38" fill="#fff" ${st()}/>`;
    s += `<circle cx="${x + look * 12}" cy="${y + 6}" r="19" fill="#5a3a26"/><circle cx="${x + look * 12}" cy="${y + 6}" r="10" fill="${OUT}"/><circle cx="${x + look * 12 + 6}" cy="${y - 2}" r="6" fill="#fff"/>`;
    s += `<path d="M${x + sx * 22},${y - 34} l${sx * 16},-16 M${x + sx * 32},${y - 22} l${sx * 18},-10" ${st(6)}/>`;
  }
  if (brow === 'up') s += `<path d="M-90,-905 Q-58,-930 -28,-912 M28,-925 Q60,-950 92,-930" fill="none" ${st(9)}/>`;
  else s += `<path d="M-88,-902 Q-58,-918 -28,-904 M28,-904 Q58,-918 88,-902" fill="none" ${st(9)}/>`;
  s += `<path d="M-2,-800 Q10,-790 0,-780" fill="none" ${st(5)}/>`;
  if (mouth === 'smile') s += `<path d="M-38,-742 Q0,-708 38,-742 Z" fill="#d93a5e" ${st(6)}/>`;
  if (mouth === 'flat') s += `<path d="M-30,-735 Q0,-728 30,-738" fill="none" ${st(7)}/>`;
  return s;
}

function marcos(o = {}) {
  const { t = 0 } = o;
  const skin = '#d99a6c', jacket = '#26263a', jeans = '#2f3045';
  let s = '';
  s += `<rect x="-92" y="-430" width="78" height="400" rx="24" fill="${jeans}" ${st()}/>`;
  s += `<rect x="14" y="-430" width="78" height="400" rx="24" fill="${jeans}" ${st()}/>`;
  s += `<path d="M-120,-8 Q-120,-60 -55,-60 Q-8,-60 -8,-8 Z" fill="#111" ${st()}/>`;
  s += `<path d="M120,-8 Q120,-60 55,-60 Q8,-60 8,-8 Z" fill="#111" ${st()}/>`;
  s += arm(-185, -690, 10, jacket, 86, 270, skin);
  s += arm(185, -690, -10, jacket, 86, 270, skin);
  s += `<path d="M-195,-712 Q-205,-560 -145,-405 L145,-405 Q205,-560 195,-712 Q0,-752 -195,-712 Z" fill="${jacket}" ${st()}/>`;
  s += `<path d="M-62,-718 L0,-560 L62,-718 Z" fill="#fff" ${st(6)}/>`;
  s += `<path d="M-62,-718 L-95,-600 L-40,-640 Z M62,-718 L95,-600 L40,-640 Z" fill="#3a3a55" ${st(5)}/>`;
  s += `<rect x="-42" y="-790" width="84" height="85" fill="${skin}" ${st()}/>`;
  s += `<rect x="-150" y="-1045" width="300" height="335" rx="115" fill="${skin}" ${st()}/>`;
  s += `<path d="M-152,-930 Q-172,-1082 -20,-1092 Q62,-1158 152,-1082 Q178,-1000 152,-930 Q122,-1002 0,-1002 Q-100,-992 -152,-930 Z" fill="#141414" ${st()}/>`;
  s += `<rect x="-125" y="-925" width="110" height="62" rx="22" fill="#141414" ${st(6)}/><rect x="15" y="-925" width="110" height="62" rx="22" fill="#141414" ${st(6)}/>`;
  s += `<path d="M-15,-905 H15" ${st(8)}/><path d="M-100,-910 l30,0 M40,-910 l30,0" stroke="#fff" stroke-width="6" opacity=".7"/>`;
  s += `<path d="M-40,-790 Q20,-772 58,-808" fill="none" ${st(8)}/>`;
  s += `<path d="M-15,-738 Q0,-728 15,-738" fill="none" ${st(5)}/>`;
  // brillitos
  for (let i = 0; i < 4; i++) {
    const a = (t * 2 + i * 1.7) % 2, sc = Math.sin(clamp(a / 2) * Math.PI) * 1.2;
    const [x, y] = [[-230, -1000], [230, -820], [-220, -520], [210, -1080]][i];
    s += `<path d="M0,-40 Q6,-6 40,0 Q6,6 0,40 Q-6,6 -40,0 Q-6,-6 0,-40 Z" transform="translate(${x},${y}) scale(${sc})" fill="#ffe14d" ${st(5)}/>`;
  }
  return s;
}

const place = (svg, x, y, sc = 1, rot = 0) => `<g transform="translate(${x},${y}) rotate(${rot}) scale(${sc})">${svg}</g>`;

// ---------- decorados ----------
function classroom(t) {
  let s = '';
  s += `<rect x="0" y="0" width="1080" height="1430" fill="#f1dfb8"/>`;
  for (let x = 0; x < 1080; x += 90) s += `<rect x="${x}" y="0" width="45" height="1430" fill="#ebd5a8" opacity=".6"/>`;
  s += `<rect x="0" y="1250" width="1080" height="180" fill="#dcc08c"/><path d="M0,1250 H1080" ${st(6)}/>`;
  s += `<rect x="0" y="1430" width="1080" height="490" fill="#b9794a"/><path d="M0,1430 H1080" ${st(7)}/>`;
  for (let y = 1500; y < 1920; y += 85) s += `<path d="M0,${y} H1080" stroke="#9c6237" stroke-width="5"/>`;
  s += `<rect x="110" y="300" width="860" height="470" rx="16" fill="#8a5a32" ${st()}/>`;
  s += `<rect x="135" y="322" width="810" height="425" rx="8" fill="#2f5d4a"/>`;
  s += `<text x="190" y="420" font-family="Fredoka" font-size="64" fill="#fff" opacity=".75">MATES</text>`;
  s += `<text x="190" y="510" font-family="Fredoka" font-size="52" fill="#fff" opacity=".6">x² + 2x = ?</text>`;
  s += `<path d="M190,560 q60,-30 120,0 t120,0" fill="none" stroke="#fff" stroke-width="5" opacity=".45"/>`;
  // corazón «H + L» pintado con tiza
  s += `<g transform="translate(780,600)" opacity=".7"><path d="${HEART}" transform="scale(1.6)" fill="none" stroke="#fff" stroke-width="3"/>
        <text x="0" y="8" text-anchor="middle" font-family="Fredoka" font-size="34" fill="#fff">H+L</text></g>`;
  s += `<circle cx="960" cy="200" r="58" fill="#fff" ${st()}/><path d="M960,200 L960,160 M960,200 L${960 + 30 * Math.cos(t)},${200 + 30 * Math.sin(t)}" ${st(6)}/>`;
  return s;
}
function desk(x, y, w = 380) {
  return `<g transform="translate(${x},${y})"><rect x="${-w / 2 + 25}" y="30" width="${w - 50}" height="200" fill="#a86d38" ${st()}/>
    <rect x="${-w / 2}" y="0" width="${w}" height="44" rx="12" fill="#cf9157" ${st()}/></g>`;
}
function burst(cx, cy, t, c1, c2) {
  let s = `<rect width="1080" height="1920" fill="${c1}"/>`;
  const n = 18, r = 2400;
  for (let i = 0; i < n; i += 2) {
    const a1 = (i / n) * Math.PI * 2 + t * 0.4, a2 = ((i + 1) / n) * Math.PI * 2 + t * 0.4;
    s += `<path d="M${cx},${cy} L${cx + r * Math.cos(a1)},${cy + r * Math.sin(a1)} L${cx + r * Math.cos(a2)},${cy + r * Math.sin(a2)} Z" fill="${c2}"/>`;
  }
  return s;
}
function card(x, y, rot, sc, color, label, icon) {
  return `<g transform="translate(${x},${y}) rotate(${rot}) scale(${sc})">
    <rect x="-190" y="-250" width="380" height="500" rx="38" fill="#fff" ${st(9)}/>
    <rect x="-168" y="-228" width="336" height="300" rx="26" fill="${color}"/>
    ${icon}
    <text x="0" y="148" text-anchor="middle" font-family="Lilita" font-size="66" fill="${OUT}">${label}</text>
    <text x="0" y="205" text-anchor="middle" font-family="Fredoka" font-size="36" fill="${color}">¿PRIMERO?</text>
  </g>`;
}
const DUMBBELL = `<g transform="translate(0,-80) rotate(-20)">
  <rect x="-110" y="-12" width="220" height="24" rx="10" fill="#e9e9f2" ${st(7)}/>
  <rect x="-130" y="-60" width="40" height="120" rx="12" fill="#3a3a55" ${st(7)}/><rect x="90" y="-60" width="40" height="120" rx="12" fill="#3a3a55" ${st(7)}/>
  <rect x="-165" y="-40" width="34" height="80" rx="10" fill="#3a3a55" ${st(7)}/><rect x="131" y="-40" width="34" height="80" rx="10" fill="#3a3a55" ${st(7)}/></g>`;
const SHOWER = `<g transform="translate(0,-80)">
  <path d="M-120,-120 L-120,-150 Q-120,-170 -100,-170 L40,-170" fill="none" ${st(14)}/><path d="M-120,-120 L-120,-150 Q-120,-170 -100,-170 L40,-170" fill="none" stroke="#e9e9f2" stroke-width="6"/>
  <path d="M-10,-170 L90,-170 L110,-110 L-30,-110 Z" fill="#e9e9f2" ${st(7)}/>
  ${[-10, 25, 60, 95].map((x, i) => [0, 1, 2].map(j => `<ellipse cx="${x - j * 12}" cy="${-60 + j * 55 + (i % 2) * 20}" rx="9" ry="16" fill="#bfe3ff" ${st(5)}/>`).join('')).join('')}
  <ellipse cx="-70" cy="100" rx="40" ry="22" fill="#fff" ${st(6)}/><ellipse cx="-30" cy="90" rx="26" ry="18" fill="#fff" ${st(6)}/></g>`;
const BROCCOLI = `<g transform="translate(0,-70)">
  <ellipse cx="0" cy="70" rx="150" ry="34" fill="#fff" ${st(7)}/>
  <path d="M-22,60 L-14,-10 L14,-10 L22,60 Z" fill="#b6e07a" ${st(7)}/>
  <circle cx="-55" cy="-30" r="50" fill="#2f9e44" ${st(7)}/><circle cx="55" cy="-30" r="50" fill="#2f9e44" ${st(7)}/>
  <circle cx="0" cy="-75" r="58" fill="#37b24d" ${st(7)}/><circle cx="-20" cy="-90" r="10" fill="#8ce99a"/><circle cx="40" cy="-40" r="8" fill="#8ce99a"/></g>`;

function hud(t, show, pct, dayPulse) {
  if (show <= 0) return '';
  const y = lerp(-240, 60, easeOut(show));
  const p = clamp(pct / 100);
  const ds = 1 + 0.18 * dayPulse;
  return `<g transform="translate(0,${y})">
    <rect x="36" y="60" width="200" height="146" rx="28" fill="${OUT}" opacity=".93" stroke="#fff" stroke-opacity=".15" stroke-width="4"/>
    <text x="136" y="108" text-anchor="middle" font-family="Fredoka" font-size="32" fill="#ffd23f">DÍAS</text>
    <text x="136" y="188" text-anchor="middle" font-family="Lilita" font-size="${80 * ds}" fill="#fff">30</text>
    <rect x="36" y="222" width="200" height="62" rx="20" fill="${OUT}" opacity=".93"/>
    <text x="136" y="264" text-anchor="middle" font-family="Lilita" font-size="34" fill="#ff9f43">GLOW ${Math.round(GLOW * 100)}%</text>
    <rect x="256" y="60" width="788" height="146" rx="28" fill="${OUT}" opacity=".93" stroke="#fff" stroke-opacity=".15" stroke-width="4"/>
    <text x="292" y="110" font-family="Fredoka" font-size="34" fill="#46d17f">HUGO</text>
    <text x="1008" y="110" text-anchor="end" font-family="Fredoka" font-size="34" fill="#ff4d5e">MARCOS</text>
    <text x="650" y="110" text-anchor="middle" font-family="Lilita" font-size="34" fill="#fff" opacity=".8">LAURA</text>
    <rect x="292" y="130" width="716" height="52" rx="26" fill="#ff4d5e"/>
    <rect x="292" y="130" width="${Math.max(52, 716 * p)}" height="52" rx="26" fill="#46d17f"/>
    <rect x="292" y="130" width="716" height="52" rx="26" fill="none" stroke="#fff" stroke-width="4"/>
    <text x="314" y="169" font-family="Lilita" font-size="36" fill="${OUT}">${Math.round(pct)}%</text>
    <text x="986" y="169" text-anchor="end" font-family="Lilita" font-size="36" fill="#fff">${100 - Math.round(pct)}%</text>
  </g>`;
}

// ---------- escenas ----------
const SCENES = {
  // «Este es Hugo.»
  A(lt, t) {
    let s = classroom(t);
    const bob = Math.sin(t * 3) * 5;
    s += place(hugo({ t, mood: 'normal', mouth: 'chew', look: 0, blink: blinkAt(t) }), 540, 2240 + bob, 1.6);
    s += desk(540, 1640, 820);
    s += `<g transform="translate(330,1610) rotate(-12)"><path d="M0,0 L150,-40 L120,40 Z" fill="#ffcc5c" ${st(7)}/><path d="M0,0 L150,-40" stroke="#d4893a" stroke-width="22" stroke-linecap="round"/>
      <circle cx="80" cy="-5" r="12" fill="#d9480f"/><circle cx="110" cy="10" r="10" fill="#d9480f"/></g>
      <g transform="translate(760,1600)"><rect x="-70" y="-40" width="140" height="40" rx="6" fill="#e8d5b0" ${st(6)}/><path d="M-70,-40 L-50,-70 L90,-70 L70,-40" fill="#f3e3c3" ${st(6)}/></g>`;
    return s;
  },
  // «Veintiséis años. Repitiendo primero por quinta vez.»
  R(lt, t) {
    let s = classroom(t);
    s += place(hugo({ t, mood: 'normal', mouth: 'smile', look: -0.5, blink: blinkAt(t, 0.9) }), 540, 2240 + Math.sin(t * 3) * 5, 1.6);
    s += desk(540, 1640, 820);
    const k = back(seg(lt, 0.1, 0.4));
    s += `<g transform="translate(540,450) rotate(5) scale(${k})">
      <rect x="-260" y="-150" width="520" height="300" rx="20" fill="#fff" ${st(9)}/>
      <text x="0" y="-70" text-anchor="middle" font-family="Fredoka" font-size="44" fill="${OUT}">1º DE CARRERA</text>
      ${[0, 1, 2, 3, 4].map(i => `<text x="${-180 + i * 90}" y="40" text-anchor="middle" font-family="Lilita" font-size="70" fill="#e03131" opacity="${seg(lt, 0.4 + i * 0.12, 0.5 + i * 0.12)}">${['3', '2', '4', '1', '0'][i]}</text>`).join('')}
      <text x="0" y="115" text-anchor="middle" font-family="Fredoka" font-size="38" fill="#e03131">INTENTO Nº 5</text></g>`;
    return s;
  },
  // «Lleva una semana sin ducharse.»
  S(lt, t) {
    let s = classroom(t) + `<rect width="1080" height="1920" fill="#8cc63f" opacity=".18"/>`;
    s += place(hugo({ t: t * 1.6, mood: 'normal', mouth: 'smile', look: Math.sin(t * 4) * 0.5, blink: blinkAt(t, 0.5) }), 540, 2330 + Math.sin(t * 3) * 5, 1.75);
    const k = back(seg(lt, 0.15, 0.45)), n = Math.min(7, 1 + Math.floor(seg(lt, 0.3, 1.1) * 7));
    s += `<g transform="translate(540,440) rotate(-4) scale(${k})">
      <rect x="-300" y="-120" width="600" height="230" rx="34" fill="#fff" ${st(9)}/>
      <text x="0" y="-38" text-anchor="middle" font-family="Fredoka" font-size="46" fill="${OUT}">DÍAS SIN DUCHA</text>
      <text x="0" y="78" text-anchor="middle" font-family="Lilita" font-size="120" fill="#5c940d">${n}</text></g>`;
    return s;
  },
  // «Y tres años enamorado de Laura.»
  B(lt, t) {
    let s = classroom(t);
    s += place(laura({ look: -0.2, blink: blinkAt(t, 1.1), mouth: 'smile' }), 790, 1640 + Math.sin(t * 2.6) * 4, 0.82);
    s += place(hugo({ t, mood: 'dream', mouth: 'open', look: 1 }), 300, 1640 + Math.sin(t * 3) * 6, 0.82, Math.sin(t * 2) * 2);
    s += desk(280, 1450, 380) + desk(790, 1450, 380);
    for (let i = 0; i < 7; i++) {
      const k = ((lt * 0.7 + i / 7) % 1);
      const x = lerp(330, 760, k) + Math.sin(k * 9 + i) * 30, y = lerp(920, 560, k) - Math.sin(k * Math.PI) * 120;
      s += heart(x, y, 0.5 + 0.4 * Math.sin(k * Math.PI), '#ff3b5c', clamp(Math.sin(k * Math.PI) * 1.5));
    }
    return s;
  },
  // «Laura no sabe ni cómo se llama.»
  C(lt, t) {
    let s = classroom(t);
    s += place(laura({ look: 0.6, blink: blinkAt(t, 0.4), mouth: 'flat', brow: 'up' }), 540, 2228 + Math.sin(t * 2.4) * 4, 1.6, Math.sin(t * 1.5) * 1.5);
    const k = back(seg(lt, 0.25, 0.55));
    s += `<g transform="translate(540,430) scale(${k})">
      <rect x="-380" y="-140" width="760" height="270" rx="60" fill="#fff" ${st(9)}/>
      <path d="M-40,128 L10,210 L50,128" fill="#fff" ${st(9)}/><rect x="-45" y="110" width="100" height="25" fill="#fff"/>
      <text x="0" y="-30" text-anchor="middle" font-family="Lilita" font-size="86" fill="${OUT}">¿HUGO?</text>
      <text x="0" y="80" text-anchor="middle" font-family="Lilita" font-size="74" fill="#ff4d5e">¿QUIÉN ES HUGO?</text></g>`;
    return s;
  },
  // «Porque Laura sale con Marcos.»
  D(lt, t) {
    let s = classroom(t);
    const mx = lerp(1450, 830, easeOut(seg(lt, 0, 0.55)));
    s += place(hugo({ t, mood: 'sad', mouth: 'sad', look: 1, blink: blinkAt(t, 2) }), 200, 1700, 0.62);
    s += place(laura({ look: 1, blink: blinkAt(t, 0.7), mouth: 'smile' }), 560, 1690 + Math.sin(t * 2.6) * 4, 0.78);
    s += place(marcos({ t }), mx, 1700, 0.86);
    // corazón roto encima de Hugo
    const br = easeOut(seg(lt, 0.7, 1.1));
    s += `<g transform="translate(190,830)"><g transform="translate(${-br * 26},0) rotate(${-br * 18})"><path d="M0,30 C-60,-15 -50,-75 0,-42 L-12,-10 L8,6 Z" transform="scale(1.3)" fill="#ff3b5c" ${st(5)}/></g>
       <g transform="translate(${br * 26},0) rotate(${br * 18})"><path d="M0,30 C60,-15 50,-75 0,-42 L-12,-10 L8,6 Z" transform="scale(1.3)" fill="#ff3b5c" ${st(5)}/></g></g>`;
    for (let i = 0; i < 4; i++) { const k = ((lt * 0.8 + i / 4) % 1); s += heart(lerp(620, 780, k), lerp(900, 640, k), 0.35, '#ff6f9f', Math.sin(k * Math.PI)); }
    return s;
  },
  // «Fiesta de fin de curso: en treinta días.»
  E(lt, t) {
    let s = `<rect width="1080" height="1920" fill="#f1dfb8"/>`;
    s += `<rect x="70" y="250" width="940" height="900" rx="20" fill="#c8935a" ${st()}/>`;
    for (let i = 0; i < 40; i++) s += `<circle cx="${100 + (i * 197) % 880}" cy="${280 + (i * 131) % 840}" r="4" fill="#a87440"/>`;
    const sw = Math.sin(t * 2) * 1.5;
    s += `<g transform="translate(540,690) rotate(${-4 + sw})">
      <rect x="-330" y="-400" width="660" height="790" rx="14" fill="#2b1650" ${st(9)}/>
      <circle cx="0" cy="-400" r="16" fill="#ff4d5e" ${st(6)}/>
      <g transform="translate(0,-195) rotate(${t * 40})"><circle r="95" fill="#c9d3e6" ${st(7)}/>
        <path d="M-95,0 H95 M0,-95 V95 M-80,-50 H80 M-80,50 H80 M-50,-80 V80 M50,-80 V80" stroke="#8d9ab3" stroke-width="5"/></g>
      ${[0, 1, 2, 3, 4, 5].map(i => { const a = i * 1.05 + t * 1.5; return `<path d="M0,-195 L${Math.cos(a) * 330},${-195 + Math.sin(a) * 330}" stroke="${['#ff4d6d', '#ffd23f', '#46d17f'][i % 3]}" stroke-width="10" opacity=".35"/>`; }).join('')}
      <text x="0" y="40" text-anchor="middle" font-family="Lilita" font-size="150" fill="#ffd23f" ${st(10)} paint-order="stroke">FIESTA</text>
      <text x="0" y="130" text-anchor="middle" font-family="Lilita" font-size="80" fill="#fff">FIN DE CURSO</text>
      <text x="0" y="215" text-anchor="middle" font-family="Fredoka" font-size="42" fill="#ff8ab0">VIERNES · 22:00 · GIMNASIO</text>
      <text x="0" y="300" text-anchor="middle" font-family="Fredoka" font-size="36" fill="#fff" opacity=".7">¡VEN CON PAREJA!</text></g>`;
    const ws = wordTime(6, 'treinta') - TL.lines[6].start;
    const k = seg(lt, ws, ws + 0.22);
    if (k > 0) {
      const sc = lerp(3, 1, easeOut(k));
      s += `<g transform="translate(560,980) rotate(-9) scale(${sc})" opacity="${k}">
        <rect x="-300" y="-85" width="600" height="170" rx="20" fill="#fff4e0" stroke="#ff2e48" stroke-width="16"/>
        <text x="0" y="40" text-anchor="middle" font-family="Lilita" font-size="118" fill="#ff2e48">EN 30 DÍAS</text></g>`;
    }
    return s;
  },
  // «Hugo tiene treinta días para que Laura se fije en él.»
  F(lt, t) {
    let s = burst(540, 820, t, '#ff7a2f', '#ff9a45');
    const up = easeOut(seg(lt, 0.15, 0.45));
    const tc = wordTime(7, 'cambiar') - TL.lines[7].start, kc = easeOut(seg(lt, tc - 0.1, tc + 0.35));
    s += `<defs><filter id="sil"><feFlood flood-color="#fff"/><feComposite in2="SourceAlpha" operator="in"/></filter></defs>`;
    if (kc > 0) s += `<g opacity="${kc * 0.9}" filter="url(#sil)">${place(hugo({ g: 1, grime: false, mouth: 'smile' }), 800, 1880, 0.95)}</g>
      <text x="800" y="${980 + Math.sin(t * 4) * 10}" text-anchor="middle" font-family="Lilita" font-size="200" fill="#ff7a2f" ${st(12)} paint-order="stroke">?</text>`;
    s += place(hugo({ t, mood: 'determined', mouth: 'grit', armR: lerp(-6, -152, up), fistR: true, armL: 8 }), lerp(540, 330, kc), lerp(2150, 1960, up), lerp(1.18, 0.95, kc));
    for (let i = 0; i < 8; i++) {
      const k = (lt * 1.6 + i / 8) % 1, a = i * 0.8;
      s += `<path d="M${540 + Math.cos(a) * (420 + k * 300)},${800 + Math.sin(a) * (420 + k * 300)} l${Math.cos(a) * 90},${Math.sin(a) * 90}" stroke="#fff" stroke-width="12" stroke-linecap="round" opacity="${1 - k}"/>`;
    }
    return s;
  },
  // «¿Por dónde empieza? / Comenta: ducha o gym. / Sígueme para el día uno.»
  G(lt, t) {
    let s = classroom(t);
    s += `<rect width="1080" height="1920" fill="${OUT}" opacity=".45"/>`;
    s += place(hugo({ t, mood: 'normal', mouth: 'o', look: Math.sin(t * 1.6), lookY: -1, blink: blinkAt(t, 1.4), armR: -150 + Math.sin(t * 3) * 4 }), 540, 2060 + Math.sin(t * 3) * 5, 1.15);
    for (let i = 0; i < 3; i++) {
      const k = (lt * 0.6 + i / 3) % 1;
      s += `<text x="${300 + i * 240}" y="${lerp(1000, 820, k)}" text-anchor="middle" font-family="Lilita" font-size="${90 + i * 10}" fill="#ffd23f" opacity="${Math.sin(k * Math.PI)}" ${st(8)} paint-order="stroke">?</text>`;
    }
    const tA = wordTime(9, 'ducha'), tB = wordTime(9, 'gym');
    const pulse = (tw) => { const d = t - tw; return d > 0 && d < 0.5 ? Math.sin(d / 0.5 * Math.PI) * 0.12 : 0; };
    const k1 = back(seg(lt, 0.05, 0.4)), k2 = back(seg(lt, 0.2, 0.55));
    s += card(290, 600 + Math.sin(t * 2.2) * 8, -6, k1 * (1 + pulse(tA)), '#3b8de0', 'DUCHA', SHOWER);
    s += card(790, 600 + Math.sin(t * 2.2 + 1) * 8, 6, k2 * (1 + pulse(tB)), '#ff8a2a', 'GYM', DUMBBELL);
    const kv = back(seg(lt, 0.4, 0.7));
    s += `<text x="540" y="650" text-anchor="middle" font-family="Lilita" font-size="${130 * kv}" fill="#ffd23f" ${st(12)} paint-order="stroke">VS</text>`;
    return s;
  },
};
const SCENE_OF = ['A', 'R', 'S', 'B', 'C', 'D', 'E', 'F', 'G', 'G', 'G'];

// ---------- subtítulos ----------
function captions(t) {
  const L = TL.lines;
  let li = -1;
  for (let i = 0; i < L.length; i++) if (t >= L[i].start - 0.02 && t < L[i].end + 0.3) li = i;
  if (li < 0) return '';
  const ln = L[li];
  let wi = 0;
  ln.words.forEach((w, j) => { if (t >= w.t) wi = j; });
  const chunk = ln.chunks.find(c => c.includes(wi));
  const hl = LINES[li].hl || [], red = LINES[li].red || [];
  return chunk.filter(j => j <= wi).map(j => {
    const w = ln.words[j];
    const pop = j === wi ? 1 + 0.25 * (1 - easeOut(seg(t, w.t, w.t + 0.14))) : 1;
    const col = red.includes(w.n) ? '#ff4d5e' : hl.includes(w.n) ? '#ffd23f' : '#fff';
    return `<span style="color:${col};transform:scale(${pop})">${w.w}</span>`;
  }).join('');
}

// ---------- fotograma ----------
function render(t) {
  const L = TL.lines;
  let li = 0;
  for (let i = 0; i < L.length; i++) if (t >= L[i].start - 0.08) li = i;
  const sc = SCENE_OF[li];
  const firstOf = SCENE_OF.indexOf(sc);
  const s0 = li === 0 ? 0 : L[firstOf].start - 0.08;
  const lt = t - s0;
  const enter = easeOut(seg(lt, 0, 0.3));
  const cam = lerp(1.1, 1, enter) * (1 + 0.035 * seg(lt, 0, 5));
  let shx = 0, shy = 0;
  if (sc === 'F' && lt < 0.6) { const a = (1 - lt / 0.6) * 18; shx = Math.sin(t * 90) * a; shy = Math.cos(t * 77) * a; }
  const hudStart = L[5].start + 0.5;
  const pct = t < hudStart + 0.3 ? 50 : lerp(50, 5, easeOut(seg(t, hudStart + 0.3, hudStart + 1.3)));
  let hx = 0;
  if (t > hudStart + 1.1 && t < hudStart + 1.6) hx = Math.sin(t * 80) * 10 * (1 - seg(t, hudStart + 1.1, hudStart + 1.6));
  const dayPulse = Math.max(0, Math.sin(seg(t, wordTime(6, 'treinta'), wordTime(6, 'treinta') + 0.4) * Math.PI), Math.sin(seg(t, wordTime(7, 'treinta'), wordTime(7, 'treinta') + 0.4) * Math.PI));
  const flash = li === 0 ? 0 : (1 - seg(lt, 0, 0.12)) * 0.45;
  const fadeEnd = seg(t, TL.total - 0.7, TL.total);

  let svg = `<g transform="translate(540,960) scale(${cam}) translate(-540,-960) translate(${shx},${shy})">${SCENES[sc](lt, t)}</g>`;
  svg += `<defs><radialGradient id="vig" cx="50%" cy="45%" r="75%"><stop offset="60%" stop-color="#000" stop-opacity="0"/><stop offset="100%" stop-color="#000" stop-opacity=".45"/></radialGradient></defs>`;
  svg += `<rect width="1080" height="1920" fill="url(#vig)"/>`;
  svg += `<g transform="translate(${hx},0)">${hud(t, seg(t, hudStart, hudStart + 0.35), pct, dayPulse)}</g>`;
  if (flash > 0) svg += `<rect width="1080" height="1920" fill="#fff" opacity="${flash}"/>`;
  if (fadeEnd > 0) svg += `<rect width="1080" height="1920" fill="#000" opacity="${fadeEnd}"/>`;
  document.getElementById('s').innerHTML = svg;
  const cap = document.getElementById('cap');
  cap.innerHTML = captions(t);
  cap.style.opacity = 1 - fadeEnd;
}
window.render = render;
render(0);

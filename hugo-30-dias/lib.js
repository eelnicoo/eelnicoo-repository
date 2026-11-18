// Hugo · 30 días — motor común: personajes, decorados, marcador, subtítulos y render.
// Cada capítulo (epNN.js) define EP, LINES, SCENES, SCENE_OF y sfxCues, y llama a start().

// ---------- utilidades ----------
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
const norm = w => w.toLowerCase().replace(/[¿?¡!.,:;…]/g, '');

// ---------- tiempos ----------
// los de timings-NN.json (audio real) o una estimación
let TL;
function buildTL() {
  TL = (() => {
  const o = window.TL_OVERRIDE;
  let lines, total;
  if (o && o.lines && o.lines.length === LINES.length) {
    lines = o.lines.map(([s, e, parts]) => ({ start: s, end: e, parts: parts || [[s, e]] }));
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
    // reparte las palabras por el tiempo con voz (saltando las pausas internas)
    const parts = ln.parts || [[ln.start, ln.end]];
    const talk = parts.reduce((a, [x, y]) => a + (y - x), 0);
    const at = f => { let r = f * talk; for (const [x, y] of parts) { if (r <= y - x) return x + r; r -= y - x; } return ln.end; };
    ln.words = words.map((w, j) => { const ws = at(acc / sum); acc += w8[j]; return { w, n: norm(w), t: ws }; });
    // trozos de subtítulo: máx. 3 palabras, corte tras puntuación
    ln.chunks = []; let cur = [];
    ln.words.forEach((wd, j) => { cur.push(j); if (cur.length === 3 || /[.,:?]$/.test(wd.w)) { ln.chunks.push(cur); cur = []; } });
    if (cur.length) ln.chunks.push(cur);
  });
  return { lines, total };
})();
  window.TOTAL = TL.total;
}
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
  const { mood = 'normal', mouth = 'smile', armL = 6, armR = -6, fistR = false, g = EP.glow, t = 0, grime = true, look = 0, lookY = 0, blink = false } = o;
  const w = lerp(1.55, 1, g), d = clamp(1 - g * 2.2), fat = clamp(1 - g * 2);
  const su = o.sucio ?? (EP.sucio ?? d); // suciedad: moscas, tufo, manchas, mocos, aliento
  const skin = d > 0.5 ? '#efbd95' : '#f5c6a0', jeans = '#3b5a9a';
  const nk = !!o.naked; // sin sudadera (en la ducha)
  const hood = nk ? skin : '#3fae6a', hoodD = nk ? '#e0a47c' : '#2b8a50';
  const hair = '#a8552a', beardC = '#6b3f22';
  const lw = lerp(66, 88, fat), lx = lerp(12, 22, fat);
  let s = '';
  // tufo
  if (grime && su > 0) for (let i = 0; i < 4; i++) {
    const k = ((t * 0.55 + i / 4) % 1), x = (i - 1.5) * 110 * w;
    s += `<path d="M${x},${-600 - k * 520} q30,-40 0,-80 t0,-80 t0,-80" fill="none" stroke="#8cc63f" stroke-width="16" stroke-linecap="round" opacity="${Math.sin(k * Math.PI) * 0.75 * su}"/>`;
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
  if (!nk) s += `<path d="M${-72 * w},${hy - 90} L${72 * w},${hy - 90} L${88 * w},${hy - 18} L${-88 * w},${hy - 18} Z" fill="${hoodD}" ${st(6)}/>`;
  if (nk) { let ch = ''; for (let i = 0; i < 22; i++) { const x = ((i * 47) % 200) - 100, y = -640 + ((i * 31) % 150); ch += `M${x},${y} q5,-9 10,-2 `; } s += `<path d="${ch}" fill="none" stroke="#5b3a26" stroke-width="3.5" stroke-linecap="round"/><circle cx="-60" cy="-560" r="7" fill="#d98c74"/><circle cx="60" cy="-560" r="7" fill="#d98c74"/>`; }
  if (!nk) s += `<path d="M-26,-655 L-32,-565 M26,-655 L32,-565" ${st(6)}/>`;
  if (!nk) s += `<circle cx="-32" cy="-560" r="7" fill="#fff" ${st(4)}/><circle cx="32" cy="-560" r="7" fill="#fff" ${st(4)}/>`;
  if (su > 0 && !nk) s += `<g opacity="${su}"><path d="M${-75 * w},-590 q20,-22 42,-4 q14,22 -8,30 q-26,8 -34,-26 Z" fill="#e8b923"/>
      <path d="M${55 * w},-520 q24,-16 40,4 q8,26 -18,28 q-30,0 -22,-32 Z" fill="#c0392b"/>
      <circle cx="${95 * w}" cy="-610" r="14" fill="${hoodD}"/><circle cx="${-40 * w}" cy="-470" r="11" fill="${hoodD}"/></g>`;
  s += `<rect x="-34" y="-725" width="68" height="75" fill="${skin}" ${st()}/>`;
  if (!nk) s += `<path d="M-110,-668 Q0,-620 110,-668 Q0,-700 -110,-668 Z" fill="${hoodD}" ${st(6)}/>`;
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
  if (su > 0) {
    const len = 24 + 16 * Math.sin(t * 2.2);
    s += `<path d="M-14,-770 q-6,${len * 0.5} 0,${len} q9,8 13,-4 q0,${-len * 0.5} -3,${-len} Z" fill="#b5d96a" opacity="${su}" ${st(4)}/>`;
  }
  // boca
  if (mouth === 'smile') s += `<path d="M-42,-718 Q0,-694 42,-718" fill="none" ${st(8)}/>`;
  // dientes amarillos y torcidos (con uno de menos) mientras va descuidado
  const tk = o.teeth ?? EP.teeth ?? (d > 0.5 ? 'yellow' : 'none');
  const teeth = (y0) => {
    if (tk === 'yellow') return `<g>${[[-38, 0, 2], [-19, 3, -4], [0, -1, 3], [38, 2, 5]].map(([x, dy, r]) => `<rect x="${x - 9}" y="${y0 + dy}" width="18" height="20" rx="4" transform="rotate(${r},${x},${y0})" fill="#e3c75c" stroke="${OUT}" stroke-width="3"/>`).join('')}</g>`;
    if (tk === 'white' || tk === 'glow') return `<g>${[-36, -18, 0, 18, 36].map(x => `<rect x="${x - 9}" y="${y0}" width="18" height="20" rx="4" fill="#fff" stroke="${OUT}" stroke-width="3"/>`).join('')}</g>` +
      (tk === 'glow' ? `<circle cx="0" cy="${y0 + 10}" r="120" fill="#fffbe6" opacity=".35"/><circle cx="0" cy="${y0 + 10}" r="70" fill="#fff" opacity=".5"/>` : '');
    return '';
  };
  if (mouth === 'open') s += `<path d="M-48,-728 Q0,-672 48,-728 Z" fill="#7a1f2e" ${st(7)}/>` + teeth(-731);
  if (mouth === 'grin') s += `<path d="M-60,-732 Q0,-660 60,-732 Z" fill="#7a1f2e" ${st(7)}/>` + teeth(-733);
  if (mouth === 'o') s += `<ellipse cx="0" cy="-712" rx="18" ry="22" fill="#7a1f2e" ${st(6)}/>`;
  if (mouth === 'sad') s += `<path d="M-38,-700 Q0,-730 38,-700" fill="none" ${st(8)}/>`;
  if (mouth === 'grit') s += `<rect x="-52" y="-738" width="104" height="36" rx="12" fill="#fff" ${st(7)}/><path d="M-52,-720 H52 M-18,-738 V-702 M18,-738 V-702" ${st(4)}/>`;
  if (mouth === 'chew') s += (Math.sin(t * 22) > 0 ? `<path d="M-44,-726 Q0,-684 44,-726 Z" fill="#7a1f2e" ${st(7)}/>` + teeth(-729) : `<path d="M-38,-716 Q0,-704 38,-716" fill="none" ${st(8)}/>`) + `<circle cx="-50" cy="-700" r="6" fill="#e8b923"/><circle cx="44" cy="-694" r="5" fill="#e8b923"/>`;
  // mal aliento
  if (grime && su > 0 && ['open', 'chew', 'o', 'grit'].includes(mouth)) for (let i = 0; i < 3; i++) {
    const k = (t * 0.9 + i / 3) % 1;
    s += `<circle cx="${50 + k * 170}" cy="${-712 - k * 90 + Math.sin(k * 8) * 12}" r="${16 + k * 26}" fill="#9ccc4a" opacity="${(1 - k) * 0.55 * su}"/>`;
  }
  // moscas
  if (grime && su > 0) for (let i = 0; i < 3; i++) {
    const a = t * (3.2 + i * 0.7) + i * 2.1;
    const x = Math.cos(a) * 260 + Math.sin(a * 2.3) * 30, y = -880 + Math.sin(a * 1.3) * 140;
    const fl = Math.sin(t * 90 + i) > 0 ? 14 : 8;
    s += `<g transform="translate(${x},${y})" opacity="${su}"><ellipse cx="-8" cy="-8" rx="10" ry="${fl}" fill="#fff" opacity=".8" ${st(3)}/><ellipse cx="8" cy="-8" rx="10" ry="${fl}" fill="#fff" opacity=".8" ${st(3)}/><circle r="10" fill="${OUT}"/></g>`;
  }
  return s;
}

function laura(o = {}) {
  const { mouth = 'smile', look = 0, blink = false, brow = 'normal' } = o;
  const skin = '#f8d3b4', hair = '#4a2c1f', top = '#ff6f9f', topD = '#e2507f', jeans = '#2f3e5c', blazer = '#3d4f7a';
  let s = '';
  s += `<path d="M-188,-860 Q-205,-1065 0,-1065 Q205,-1065 188,-860 L205,-545 Q130,-505 95,-590 L-95,-590 Q-130,-505 -205,-545 Z" fill="${hair}" ${st()}/>`;
  s += `<rect x="-70" y="-400" width="58" height="370" rx="22" fill="${jeans}" ${st()}/>`;
  s += `<rect x="12" y="-400" width="58" height="370" rx="22" fill="${jeans}" ${st()}/>`;
  s += `<path d="M-95,-8 Q-95,-52 -44,-52 Q-8,-52 -8,-8 Z" fill="#ffe3ec" ${st()}/>`;
  s += `<path d="M95,-8 Q95,-52 44,-52 Q8,-52 8,-8 Z" fill="#ffe3ec" ${st()}/>`;
  s += arm(-118, -640, 7, blazer, 58, 240, skin);
  s += arm(118, -640, -7, blazer, 58, 240, skin);
  s += `<path d="M-122,-662 Q-142,-540 -108,-385 L108,-385 Q142,-540 122,-662 Q0,-692 -122,-662 Z" fill="${top}" ${st()}/>`;
  s += `<path d="M-128,-664 Q-148,-540 -112,-385 L-40,-385 L-50,-560 L-20,-664 Z" fill="${blazer}" ${st()}/><path d="M128,-664 Q148,-540 112,-385 L40,-385 L50,-560 L20,-664 Z" fill="${blazer}" ${st()}/>`;
  s += `<path d="M-20,-664 L-60,-600 L-40,-560 M20,-664 L60,-600 L40,-560" fill="none" ${st(6)}/><circle cx="-80" cy="-470" r="7" fill="#ffd23f"/>`;
  s += `<rect x="-26" y="-715" width="52" height="65" fill="${skin}" ${st()}/>`;
  s += `<circle cx="0" cy="-845" r="162" fill="${skin}" ${st()}/>`;
  s += `<path d="M-168,-878 Q-155,-1012 0,-1012 Q155,-1012 168,-878 Q120,-942 40,-932 Q10,-900 -20,-936 Q-100,-952 -168,-878 Z" fill="${hair}" ${st()}/>`;
  s += `<circle cx="-158" cy="-768" r="13" fill="#ffd23f" ${st(5)}/><circle cx="158" cy="-768" r="13" fill="#ffd23f" ${st(5)}/>`;
  s += `<ellipse cx="-98" cy="-775" rx="30" ry="16" fill="#ff8aa6" opacity=".6"/><ellipse cx="98" cy="-775" rx="30" ry="16" fill="#ff8aa6" opacity=".6"/>`;
  for (const sx of [-1, 1]) {
    const x = sx * 58, y = -845;
    if (blink) { s += `<path d="M${x - 30},${y} Q${x},${y + 14} ${x + 30},${y}" fill="none" ${st(7)}/>`; continue; }
    if (mouth === 'laugh') { s += `<path d="M${x - 30},${y + 6} Q${x},${y - 26} ${x + 30},${y + 6}" fill="none" ${st(8)}/>`; continue; }
    s += `<ellipse cx="${x}" cy="${y}" rx="32" ry="38" fill="#fff" ${st()}/>`;
    s += `<circle cx="${x + look * 12}" cy="${y + 6}" r="19" fill="#5a3a26"/><circle cx="${x + look * 12}" cy="${y + 6}" r="10" fill="${OUT}"/><circle cx="${x + look * 12 + 6}" cy="${y - 2}" r="6" fill="#fff"/>`;
    s += `<path d="M${x + sx * 22},${y - 34} l${sx * 16},-16 M${x + sx * 32},${y - 22} l${sx * 18},-10" ${st(6)}/>`;
  }
  if (brow === 'up') s += `<path d="M-90,-905 Q-58,-930 -28,-912 M28,-925 Q60,-950 92,-930" fill="none" ${st(9)}/>`;
  else s += `<path d="M-88,-902 Q-58,-918 -28,-904 M28,-904 Q58,-918 88,-902" fill="none" ${st(9)}/>`;
  s += `<path d="M-2,-800 Q10,-790 0,-780" fill="none" ${st(5)}/>`;
  if (mouth === 'smile') s += `<path d="M-38,-742 Q0,-708 38,-742 Z" fill="#d93a5e" ${st(6)}/>`;
  if (mouth === 'flat') s += `<path d="M-30,-735 Q0,-728 30,-738" fill="none" ${st(7)}/>`;
  if (mouth === 'laugh') s += `<path d="M-50,-750 Q0,-670 50,-750 Z" fill="#a61e4d" ${st(6)}/><path d="M-36,-748 H36" stroke="#fff" stroke-width="12"/>`;
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
function classroom(t) { // oficina
  let s = '';
  s += `<rect x="0" y="0" width="1080" height="1430" fill="#dfe6ee"/>`;
  s += `<rect x="0" y="1180" width="1080" height="250" fill="#c9d3de"/><path d="M0,1180 H1080" ${st(6)}/>`;
  s += `<rect x="0" y="1430" width="1080" height="490" fill="#7d889b"/><path d="M0,1430 H1080" ${st(7)}/>`;
  for (let i = 0; i < 60; i++) s += `<rect x="${(i * 173) % 1080}" y="${1450 + (i * 97) % 460}" width="18" height="6" fill="#6f7a8d"/>`;
  // ventanal con la ciudad
  s += `<rect x="90" y="290" width="560" height="500" rx="10" fill="#9fd3f5" ${st()}/>`;
  const b = [[110, 560, 90], [210, 470, 70], [290, 600, 110], [410, 520, 80], [500, 430, 120]];
  for (const [x, y, w] of b) { s += `<rect x="${x}" y="${y}" width="${w}" height="${790 - y}" fill="#6c8db5"/>`; for (let yy = y + 20; yy < 770; yy += 40) for (let xx = x + 12; xx < x + w - 12; xx += 26) s += `<rect x="${xx}" y="${yy}" width="12" height="18" fill="#cfe8ff" opacity=".8"/>`; }
  s += `<path d="M370,290 V790 M90,540 H650" ${st(10)}/><rect x="90" y="290" width="560" height="500" rx="10" fill="none" ${st()}/>`;
  // pizarra blanca con gráfica y el corazón escondido
  s += `<rect x="690" y="330" width="320" height="300" rx="12" fill="#fff" ${st()}/>`;
  s += `<path d="M720,590 L780,540 L830,560 L890,470 L960,420" fill="none" stroke="#2f9e44" stroke-width="8" stroke-linecap="round"/>`;
  s += `<text x="720" y="390" font-family="Fredoka" font-size="38" fill="#1c7ed6">VENTAS</text>`;
  s += `<g transform="translate(950,560)" opacity=".8"><path d="${HEART}" transform="scale(.9)" fill="none" stroke="#e03131" stroke-width="5"/><text x="0" y="6" text-anchor="middle" font-family="Fredoka" font-size="20" fill="#e03131">H+L</text></g>`;
  // planta y reloj
  s += `<rect x="925" y="1000" width="90" height="120" rx="10" fill="#e8590c" ${st()}/><path d="M970,1000 q-60,-90 -20,-160 M970,1000 q10,-110 50,-150 M970,1000 q-10,-70 -70,-90" fill="none" stroke="#2f9e44" stroke-width="16" stroke-linecap="round"/>`;
  s += `<circle cx="960" cy="200" r="58" fill="#fff" ${st()}/><path d="M960,200 L960,160 M960,200 L${960 + 30 * Math.cos(t)},${200 + 30 * Math.sin(t)}" ${st(6)}/>`;
  return s;
}
function desk(x, y, w = 380) {
  return `<g transform="translate(${x},${y})"><rect x="${-w / 2 + 25}" y="30" width="${w - 50}" height="200" fill="#9aa6b8" ${st()}/>
    <rect x="${-w / 2}" y="0" width="${w}" height="44" rx="12" fill="#e9edf2" ${st()}/></g>`;
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
function card(x, y, rot, sc, color, label, icon, sub = '¿PRIMERO?') {
  return `<g transform="translate(${x},${y}) rotate(${rot}) scale(${sc})">
    <rect x="-190" y="-250" width="380" height="500" rx="38" fill="#fff" ${st(9)}/>
    <rect x="-168" y="-228" width="336" height="300" rx="26" fill="${color}"/>
    ${icon}
    <text x="0" y="148" text-anchor="middle" font-family="Lilita" font-size="66" fill="${OUT}">${label}</text>
    <text x="0" y="205" text-anchor="middle" font-family="Fredoka" font-size="36" fill="${color}">${sub}</text>
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

function hud(t, show, pct, dayPulse, glowPct = EP.glow * 100, days = EP.daysLeft, glowPulse = 0) {
  if (show <= 0) return '';
  const y = lerp(-240, 60, easeOut(show));
  const p = clamp(pct / 100);
  const ds = 1 + 0.18 * dayPulse;
  return `<g transform="translate(0,${y})">
    <rect x="36" y="60" width="200" height="146" rx="28" fill="${OUT}" opacity=".93" stroke="#fff" stroke-opacity=".15" stroke-width="4"/>
    <text x="136" y="108" text-anchor="middle" font-family="Fredoka" font-size="32" fill="#ffd23f">DÍAS</text>
    <text x="136" y="188" text-anchor="middle" font-family="Lilita" font-size="${80 * ds}" fill="#fff">${days}</text>
    <rect x="36" y="222" width="200" height="62" rx="20" fill="${OUT}" opacity=".93"/>
    <text x="136" y="264" text-anchor="middle" font-family="Lilita" font-size="34" fill="#ff9f43" transform="translate(136,252) scale(${1 + 0.25 * glowPulse}) translate(-136,-252)">GLOW ${Math.round(glowPct)}%</text>
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

// ---------- subtítulos ----------
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

// ---------- portada (primer fotograma) ----------
function cover() {
  let s = burst(540, 900, 0, '#ff7a2f', '#ffa94d');
  s += `<defs><radialGradient id="cg" cx="50%" cy="47%" r="60%"><stop offset="0" stop-color="#ffe066" stop-opacity=".9"/><stop offset="1" stop-color="#ffe066" stop-opacity="0"/></radialGradient></defs><rect width="1080" height="1920" fill="url(#cg)"/>`;
  s += place(hugo(Object.assign({ t: 1.35, mood: 'normal', mouth: 'open' }, EP.coverHugo || {})), 540, 2270, 1.5);
  s += `<g transform="translate(540,530) rotate(-5)">
    <text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="250" fill="#ffd23f" stroke="${OUT}" stroke-width="30" paint-order="stroke" stroke-linejoin="round">DÍA ${EP.day}</text></g>`;
  s += `<g transform="translate(540,1500) rotate(-2)">
    <rect x="-470" y="-95" width="940" height="190" rx="40" fill="#ff2e48" ${st(10)}/>
    <text x="0" y="-12" text-anchor="middle" font-family="Lilita" font-size="70" fill="#fff">${EP.coverLine1 || '30 DÍAS PARA'}</text>
    <text x="0" y="62" text-anchor="middle" font-family="Lilita" font-size="70" fill="#ffd23f">${EP.coverLine2 || 'CONQUISTAR A LAURA'}</text></g>`;
  return s;
}

// ---------- fotograma ----------
function render(t) {
  if (t < 1 / 30) {
    document.getElementById('s').innerHTML = cover();
    document.getElementById('cap').innerHTML = '';
    return;
  }
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
  const shake = EP.shake ? EP.shake(sc, lt, t) : 0;
  if (shake > 0) { shx = Math.sin(t * 90) * shake; shy = Math.cos(t * 77) * shake; }
  const h = EP.hud(t);
  const flash = li === 0 ? 0 : (1 - seg(lt, 0, 0.12)) * 0.45;
  const fadeEnd = seg(t, TL.total - 0.7, TL.total);

  let svg = `<g transform="translate(540,960) scale(${cam}) translate(-540,-960) translate(${shx},${shy})">${SCENES[sc](lt, t)}</g>`;
  svg += `<defs><radialGradient id="vig" cx="50%" cy="45%" r="75%"><stop offset="60%" stop-color="#000" stop-opacity="0"/><stop offset="100%" stop-color="#000" stop-opacity=".45"/></radialGradient></defs>`;
  svg += `<rect width="1080" height="1920" fill="url(#vig)"/>`;
  svg += `<g transform="translate(${h.hx || 0},0)">${hud(t, h.show, h.pct, h.dayPulse || 0, h.glow, h.days, h.glowPulse || 0)}</g>`;
  if (h.extra) svg += h.extra;
  if (flash > 0) svg += `<rect width="1080" height="1920" fill="#fff" opacity="${flash}"/>`;
  if (fadeEnd > 0) svg += `<rect width="1080" height="1920" fill="#000" opacity="${fadeEnd}"/>`;
  document.getElementById('s').innerHTML = svg;
  const cap = document.getElementById('cap');
  cap.innerHTML = captions(t);
  cap.style.opacity = 1 - fadeEnd;
}

function start() {
  buildTL();
  window.render = render;
  render(0);
}

// ---------- arranque común de cada capítulo (desde el 2) ----------
// «Hugo: cero duchas. Quiere conquistar a Laura. Tú eliges cada cambio. Día N.»
// EP.dayWord: el día en letra («uno», «dos»…). EP.start: cómo está Hugo al empezar el día ({ g, sucio }).
function introLines() {
  return [
    { text: 'Hugo: cero duchas.', hl: ['cero', 'duchas'], red: ['cero'] },
    { text: 'Quiere conquistar a Laura.', hl: ['laura'] },
    { text: 'Tú eliges cada cambio.', hl: ['tú', 'eliges'] },
    { text: `Día ${EP.dayWord}.`, hl: ['día', norm(EP.dayWord)] },
  ];
}
const INTRO_SCENES = {
  I0(lt, t) {
    let s = classroom(t);
    s += place(hugo({ t, g: 0, sucio: 1, mood: 'normal', mouth: 'chew', blink: blinkAt(t) }), 540, 2240 + Math.sin(t * 3) * 5, 1.6);
    s += desk(540, 1640, 820);
    const tw = wordTime(0, 'cero') - TL.lines[0].start, k = seg(lt, tw, tw + 0.2);
    if (k > 0) s += `<g transform="translate(540,470) rotate(-8) scale(${lerp(2.6, 1, easeOut(k))})" opacity="${k}">
      <rect x="-300" y="-90" width="600" height="180" rx="22" fill="#fff4e0" stroke="#ff2e48" stroke-width="16"/>
      <text x="0" y="42" text-anchor="middle" font-family="Lilita" font-size="120" fill="#ff2e48">0 DUCHAS</text></g>`;
    return s;
  },
  I1(lt, t) {
    let s = classroom(t);
    s += place(laura({ look: -0.3, blink: blinkAt(t, 1.1), mouth: 'smile' }), lerp(1250, 790, easeOut(seg(lt, 0, 0.35))), 1640 + Math.sin(t * 2.6) * 4, 0.82);
    s += place(hugo({ t, g: 0, sucio: 1, mood: 'dream', mouth: 'open', look: 1 }), 300, 1640 + Math.sin(t * 3) * 6, 0.82, Math.sin(t * 2) * 2);
    s += desk(300, 1450, 400) + desk(790, 1450, 380);
    for (let i = 0; i < 6; i++) {
      const k = ((lt * 0.8 + i / 6) % 1);
      s += heart(lerp(350, 760, k) + Math.sin(k * 9 + i) * 30, lerp(900, 560, k) - Math.sin(k * Math.PI) * 120, 0.5 + 0.4 * Math.sin(k * Math.PI), '#ff3b5c', clamp(Math.sin(k * Math.PI) * 1.5));
    }
    return s;
  },
  I2(lt, t) {
    let s = burst(540, 900, t, '#3b5bdb', '#4c6ef5');
    const msgs = [['DUCHA 🚿', 260, 420, -6], ['¡GYM YA! 💪', 760, 640, 5], ['DENTISTA 🦷', 300, 880, 4], ['¡VAMOS HUGO!', 740, 1460, -5], ['DUCHA Y LUEGO GYM', 460, 1680, -3]];
    msgs.forEach(([txt, x, y, r], i) => {
      const k = back(seg(lt, 0.05 + i * 0.12, 0.35 + i * 0.12));
      if (k <= 0) return;
      const w = 60 + txt.length * 30;
      s += `<g transform="translate(${x},${y + Math.sin(t * 3 + i) * 8}) rotate(${r}) scale(${k})">
        <rect x="${-w / 2}" y="-60" width="${w}" height="120" rx="40" fill="#fff" ${st(8)}/>
        <path d="M${-w / 2 + 40},55 l-20,40 l50,-40" fill="#fff" ${st(8)}/><rect x="${-w / 2 + 30}" y="44" width="70" height="16" fill="#fff"/>
        <circle cx="${-w / 2 + 50}" cy="0" r="30" fill="#ffd23f" ${st(5)}/>
        <text x="${-w / 2 + 95}" y="16" font-family="Lilita" font-size="46" fill="${OUT}">${txt}</text></g>`;
    });
    return s;
  },
  I3(lt, t) {
    let s = classroom(t);
    const L = TL.lines, mid = (L[3].end - L[3].start) * 0.35 + 0.08;
    const after = lt > mid, st0 = EP.start || {};
    s += place(hugo(after ? { t, g: st0.g ?? EP.glow, sucio: st0.sucio, mood: 'normal', mouth: 'smile', blink: blinkAt(t) } : { t, g: 0, sucio: 1, mood: 'normal', mouth: 'o' }), 540, 2060 + Math.sin(t * 3) * 5, 1.2);
    const fl = 1 - Math.abs(lt - mid) / 0.18;
    if (fl > 0) s += `<rect width="1080" height="1920" fill="#fff" opacity="${clamp(fl)}"/>`;
    if (after) for (let i = 0; i < 6; i++) { const a = (t * 2 + i * 1.1) % 2, sc = Math.sin(clamp(a / 2) * Math.PI); s += `<path d="M0,-40 Q6,-6 40,0 Q6,6 0,40 Q-6,6 -40,0 Q-6,-6 0,-40 Z" transform="translate(${[220, 860, 180, 900, 300, 780][i]},${[700, 640, 1100, 1060, 520, 480][i]}) scale(${sc * 1.3})" fill="#ffe14d" ${st(5)}/>`; }
    const k = back(seg(lt, 0.05, 0.35));
    s += `<g transform="translate(540,470) rotate(-4) scale(${k})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="220" fill="#ffd23f" stroke="${OUT}" stroke-width="28" paint-order="stroke" stroke-linejoin="round">DÍA ${EP.day}</text></g>`;
    return s;
  },
};
function introCues(add) {
  const L = TL.lines;
  add(0.05, 'buzz', 0.2, L[1].start);
  add(wordTime(0, 'cero') + 0.05, 'stamp', 0.7);
  add(L[1].start - 0.08, 'whoosh', 0.45);
  add(L[1].start + 0.2, 'sparkle', 0.35);
  for (let i = 0; i < 5; i++) add(L[2].start - 0.08 + 0.1 + i * 0.12, 'pop', 0.45);
  add(L[3].start - 0.08, 'whoosh', 0.4);
  add(L[3].start - 0.08 + (L[3].end - L[3].start) * 0.35 + 0.08, 'magic', 0.5);
  add(wordTime(3, norm(EP.dayWord)), 'ding', 0.35);
}

// ---------- iconos de cartas compartidos ----------
const TOOTH = `<g transform="translate(0,-80)">
  <path d="M-90,-110 Q-120,-150 -60,-160 Q-20,-165 0,-140 Q20,-165 60,-160 Q120,-150 90,-110 Q80,-40 70,40 Q60,110 35,110 Q10,110 10,40 Q0,10 -10,40 Q-10,110 -35,110 Q-60,110 -70,40 Q-80,-40 -90,-110 Z" fill="#fff6d0" ${st(8)}/>
  <ellipse cx="-30" cy="-60" rx="22" ry="14" fill="#e3c75c"/><ellipse cx="40" cy="-10" rx="18" ry="24" fill="#e3c75c"/><circle cx="-10" cy="40" r="10" fill="#8a6d1f"/></g>`;
const SCISSORS = `<g transform="translate(0,-80) rotate(-30)">
  <path d="M-10,-10 L-120,-150 M10,-10 L120,-150" ${st(20)}/><path d="M-10,-10 L-120,-150 M10,-10 L120,-150" stroke="#dee2e6" stroke-width="10" stroke-linecap="round"/>
  <circle cx="-60" cy="90" r="50" fill="none" stroke="${OUT}" stroke-width="24"/><circle cx="-60" cy="90" r="50" fill="none" stroke="#f03e3e" stroke-width="12"/>
  <circle cx="60" cy="90" r="50" fill="none" stroke="${OUT}" stroke-width="24"/><circle cx="60" cy="90" r="50" fill="none" stroke="#f03e3e" stroke-width="12"/>
  <path d="M-10,-10 L-45,50 M10,-10 L45,50" ${st(18)}/><circle cx="0" cy="-10" r="12" fill="#adb5bd" ${st(5)}/></g>`;

// ---------- resultado de la votación anterior ----------
// EP.vote = { a: { label, pct, color, icon }, b: { … } }; li: frase «Habéis votado… y ganó …»
function voteScene(lt, t, li) {
  let s = burst(540, 820, t, '#1d1630', '#2a2350');
  const { a, b } = EP.vote, fill = easeOut(seg(lt, 0.35, 1.2));
  const tw = wordTime(li, 'ganó') - TL.lines[li].start + 0.08;
  const win = easeOut(seg(lt, tw, tw + 0.35));
  const k1 = back(seg(lt, 0.05, 0.35)), k2 = back(seg(lt, 0.15, 0.45));
  const aWins = a.pct >= b.pct;
  const pos = (isWin, x) => isWin ? [lerp(x, 540, win), lerp(620, 640, win), lerp(1, 1.35, win), 0] : [x + (x < 540 ? -1 : 1) * 300 * win, 620 + 600 * win * win, 1, (x < 540 ? -1 : 1) * 25 * win];
  const [ax, ay, as, ar] = pos(aWins, 290), [bx, by, bs, br] = pos(!aWins, 790);
  const pct = (p) => Math.round(p * fill);
  const ca = card(ax, ay, -6 + ar, k1 * as, a.color, a.label, a.icon, `${pct(a.pct)}%`);
  const cb = card(bx, by, 6 + br, k2 * bs, b.color, b.label, b.icon, `${pct(b.pct)}%`);
  s += aWins ? `<g opacity="${1 - win}">${cb}</g>` + ca : `<g opacity="${1 - win}">${ca}</g>` + cb;
  if (win < 0.5) s += `<text x="540" y="660" text-anchor="middle" font-family="Lilita" font-size="130" fill="#ffd23f" ${st(12)} paint-order="stroke" opacity="${1 - win * 2}">VS</text>`;
  const ks = seg(lt, tw + 0.25, tw + 0.45);
  if (ks > 0) s += `<g transform="translate(560,1000) rotate(-10) scale(${lerp(2.6, 1, easeOut(ks))})" opacity="${ks}">
    <rect x="-330" y="-80" width="660" height="160" rx="22" fill="#fff4e0" stroke="#2f9e44" stroke-width="14"/>
    <text x="0" y="35" text-anchor="middle" font-family="Lilita" font-size="96" fill="#2f9e44">¡HA GANADO!</text></g>`;
  s += `<text x="540" y="300" text-anchor="middle" font-family="Fredoka" font-size="52" fill="#fff" opacity="${seg(lt, 0, 0.3)}">VUESTRA VOTACIÓN</text>`;
  return s;
}
function voteCues(add, li) {
  const v0 = TL.lines[li].start - 0.08;
  add(v0 + 0.05, 'pop', 0.5); add(v0 + 0.15, 'pop', 0.5);
  for (let i = 0; i < 8; i++) add(v0 + 0.35 + i * 0.1, 'tick', 0.35);
  add(wordTime(li, 'ganó') + 0.33, 'stamp', 0.75);
  add(wordTime(li, 'ganó') + 0.4, 'sparkle', 0.4);
}
// votación para mañana: dos cartas sobre Hugo pensando
function nextVoteScene(lt, t, li, A, B, hugoOpts = {}) {
  let s = classroom(t) + `<rect width="1080" height="1920" fill="${OUT}" opacity=".45"/>`;
  s += place(hugo(Object.assign({ t, mood: 'normal', mouth: 'o', look: Math.sin(t * 1.6), lookY: -1, blink: blinkAt(t, 1.4), armR: -150 + Math.sin(t * 3) * 4 }, hugoOpts)), 540, 2060 + Math.sin(t * 3) * 5, 1.15);
  const tA = wordTime(li, norm(A.word)), tB = wordTime(li, norm(B.word));
  const pulse = (tw) => { const d = t - tw; return d > 0 && d < 0.5 ? Math.sin(d / 0.5 * Math.PI) * 0.12 : 0; };
  const k1 = back(seg(lt, 0.05, 0.4)), k2 = back(seg(lt, 0.2, 0.55));
  s += card(290, 600 + Math.sin(t * 2.2) * 8, -6, k1 * (1 + pulse(tA)), A.color, A.label, A.icon, '¿MAÑANA?');
  s += card(790, 600 + Math.sin(t * 2.2 + 1) * 8, 6, k2 * (1 + pulse(tB)), B.color, B.label, B.icon, '¿MAÑANA?');
  const kv = back(seg(lt, 0.4, 0.7));
  s += `<text x="540" y="650" text-anchor="middle" font-family="Lilita" font-size="${130 * kv}" fill="#ffd23f" ${st(12)} paint-order="stroke">VS</text>`;
  return s;
}
function nextVoteCues(add, li, A, B) {
  const L = TL.lines;
  add(L[li].start + 0.12, 'pop', 0.5); add(L[li].start + 0.28, 'pop', 0.5);
  add(L[li].start + 0.42, 'impact', 0.25);
  add(wordTime(li, norm(A.word)), 'boing', 0.35);
  add(wordTime(li, norm(B.word)), 'ding', 0.3);
  add(TL.total - 0.9, 'whoosh', 0.3);
}
// destello «+N %» bajo el marcador
function plusBadge(t, tc, n) {
  return t > tc && t < tc + 1.0 ? `<g transform="translate(270,${440 - 60 * seg(t, tc, tc + 1.0)})" opacity="${1 - seg(t, tc + 0.6, tc + 1.0)}"><text font-family="Lilita" font-size="80" fill="#46d17f" stroke="${OUT}" stroke-width="12" paint-order="stroke">+${n}%</text></g>` : '';
}
const SHIRT = `<g transform="translate(0,-80)">
  <path d="M-60,-140 L-150,-100 L-190,-20 L-120,10 L-110,-30 L-110,130 L110,130 L110,-30 L120,10 L190,-20 L150,-100 L60,-140 Q0,-90 -60,-140 Z" fill="#fff" ${st(8)}/>
  <path d="M-60,-140 Q0,-90 60,-140" fill="none" ${st(6)}/><circle cx="-50" cy="20" r="14" fill="#4dabf7"/><path d="M30,30 l20,-20 l20,20" fill="none" stroke="#4dabf7" stroke-width="10"/></g>`;

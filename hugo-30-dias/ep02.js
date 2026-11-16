// Capítulo 2 — Día 1: la primera ducha. Votación siguiente: GYM vs DENTISTA.
const EP = {
  num: 2, day: 1, dayWord: 'uno', daysLeft: 29, glow: 0, start: { g: 0, sucio: 1 },
  coverHugo: { sucio: 0, mouth: 'smile' }, coverLine1: 'LA PRIMERA', coverLine2: 'DUCHA EN UNA SEMANA',
  // resultado de la votación del capítulo anterior (se actualiza con los datos reales)
  vote: { a: 'DUCHA', pa: 86, b: 'GYM', pb: 14 },
};

const LINES = [
  ...introLines(),
  { text: 'Habéis votado… y ganó la ducha.', hl: ['ducha'] },
  { text: 'Primera ducha en una semana.', hl: ['primera', 'ducha'] },
  { text: 'Problema: no le queda gel, y tiene un euro con veinte.', hl: ['problema', 'euro', 'veinte'], red: ['problema'] },
  { text: 'Así que se lava con lavavajillas.', hl: ['lavavajillas'] },
  { text: 'Y encima, no hay agua caliente.', hl: ['agua', 'caliente'], red: ['no'] },
  { text: 'Hugo grita como un niño.', hl: ['grita'] },
  { text: 'Pero aguanta.', hl: ['aguanta'] },
  { text: 'Cuarenta minutos después… el desagüe se rinde.', hl: ['desagüe', 'rinde'] },
  { text: 'Y hasta las moscas le abandonan.', hl: ['moscas'] },
  { text: 'En la oficina, Laura pasa a su lado.', hl: ['laura'] },
  { text: 'Y por primera vez… no se tapa la nariz.', hl: ['no', 'tapa', 'nariz'] },
  { text: 'Mañana: ¿gym o dentista?', hl: ['gym', 'dentista'] },
  { text: 'Comenta, y sígueme para el día dos.', hl: ['día', 'dos'] },
];

// ---------- decorados y objetos de este capítulo ----------
function bathroom(t, dim = 0) {
  let s = `<rect width="1080" height="1920" fill="#cdebf3"/>`;
  for (let x = 0; x <= 1080; x += 90) s += `<path d="M${x},0 V1450" stroke="#a7d3df" stroke-width="5"/>`;
  for (let y = 0; y <= 1450; y += 90) s += `<path d="M0,${y} H1080" stroke="#a7d3df" stroke-width="5"/>`;
  s += `<rect x="0" y="1450" width="1080" height="470" fill="#e6eef2"/><path d="M0,1450 H1080" ${st(7)}/>`;
  for (let x = 0; x < 1080; x += 120) for (let y = 1450; y < 1920; y += 120) if (((x + y) / 120) % 2 === 0) s += `<rect x="${x}" y="${y}" width="120" height="120" fill="#c3d2da"/>`;
  // ducha: barra, cortina recogida y alcachofa
  s += `<path d="M120,240 H960" ${st(14)}/><path d="M120,240 H960" stroke="#dfe7ec" stroke-width="6"/>`;
  s += `<path d="M130,250 Q170,700 140,1440 L330,1440 Q300,800 330,250 Z" fill="#ff8fab" ${st()}/>`;
  s += `<path d="M190,270 Q215,800 190,1430 M260,270 Q280,800 260,1430" stroke="#e86f8f" stroke-width="7" fill="none"/>`;
  s += `<path d="M1000,120 H820 V300" fill="none" ${st(16)}/><path d="M1000,120 H820 V300" fill="none" stroke="#dfe7ec" stroke-width="7"/>`;
  s += `<path d="M760,300 H880 L905,350 H735 Z" fill="#dfe7ec" ${st(7)}/>`;
  s += `<rect x="300" y="1420" width="700" height="60" rx="14" fill="#fff" ${st()}/>`;
  if (dim) s += `<rect width="1080" height="1920" fill="${OUT}" opacity="${dim}"/>`;
  return s;
}
function showerWater(t, k = 1, cold = false) {
  let s = '';
  for (let i = 0; i < 26; i++) {
    const x = 750 + ((i * 37) % 170) + (i % 3) * 5, y = 360 + ((t * 1600 + i * 233) % 1100);
    s += `<path d="M${x},${y} l${(x - 820) * 0.15},70" stroke="${cold ? '#74c0fc' : '#a5d8ff'}" stroke-width="7" stroke-linecap="round" opacity="${0.8 * k}"/>`;
  }
  return s;
}
function bubbles(t, cx, cy, n, r0) {
  let s = '';
  for (let i = 0; i < n; i++) {
    const a = i * 2.39996, rr = r0 * Math.sqrt((i + 1) / n);
    const x = cx + Math.cos(a) * rr * 1.3, y = cy + Math.sin(a) * rr * 0.8 + Math.sin(t * 3 + i) * 6;
    const r = 26 + (i * 13) % 34;
    s += `<circle cx="${x}" cy="${y}" r="${r}" fill="#fff" fill-opacity=".85" stroke="#9ed3ec" stroke-width="4"/><circle cx="${x - r * 0.35}" cy="${y - r * 0.35}" r="${r * 0.2}" fill="#fff"/>`;
  }
  return s;
}
const TOOTH = `<g transform="translate(0,-80)">
  <path d="M-90,-110 Q-120,-150 -60,-160 Q-20,-165 0,-140 Q20,-165 60,-160 Q120,-150 90,-110 Q80,-40 70,40 Q60,110 35,110 Q10,110 10,40 Q0,10 -10,40 Q-10,110 -35,110 Q-60,110 -70,40 Q-80,-40 -90,-110 Z" fill="#fff6d0" ${st(8)}/>
  <ellipse cx="-30" cy="-60" rx="22" ry="14" fill="#e3c75c"/><ellipse cx="40" cy="-10" rx="18" ry="24" fill="#e3c75c"/><circle cx="-10" cy="40" r="10" fill="#8a6d1f"/></g>`;
function fly(x, y, t, i, suitcase = false, tears = false) {
  const fl = Math.sin(t * 90 + i) > 0 ? 18 : 10;
  let s = `<g transform="translate(${x},${y})">`;
  if (suitcase) s += `<path d="M0,12 L0,30" ${st(4)}/><rect x="-14" y="30" width="28" height="20" rx="4" fill="#a0522d" ${st(4)}/>`;
  s += `<ellipse cx="-11" cy="-11" rx="13" ry="${fl}" fill="#fff" opacity=".8" ${st(3)}/><ellipse cx="11" cy="-11" rx="13" ry="${fl}" fill="#fff" opacity=".8" ${st(3)}/>`;
  s += `<circle r="14" fill="${OUT}"/><circle cx="-5" cy="-3" r="4" fill="#fff"/><circle cx="5" cy="-3" r="4" fill="#fff"/>`;
  if (tears) s += `<path d="M-5,4 q-3,10 0,14 q3,-4 0,-14 M5,4 q-3,10 0,14 q3,-4 0,-14" fill="#74c0fc"/>`;
  return s + '</g>';
}

// ---------- escenas ----------
const SCENES = Object.assign({}, INTRO_SCENES, {
  // «Habéis votado… y ganó la ducha.» — resultado de la votación
  V(lt, t) {
    let s = burst(540, 820, t, '#1d1630', '#2a2350');
    const v = EP.vote, fill = easeOut(seg(lt, 0.35, 1.2));
    const tw = wordTime(4, 'ganó') - TL.lines[4].start + 0.08;
    const win = easeOut(seg(lt, tw, tw + 0.35));
    const k1 = back(seg(lt, 0.05, 0.35)), k2 = back(seg(lt, 0.15, 0.45));
    const aWins = v.pa >= v.pb;
    const pos = (isWin, x) => isWin ? [lerp(x, 540, win), lerp(620, 640, win), lerp(1, 1.35, win), 0] : [x + (x < 540 ? -1 : 1) * 300 * win, 620 + 600 * win * win, 1, (x < 540 ? -1 : 1) * 25 * win];
    const [ax, ay, as, ar] = pos(aWins, 290), [bx, by, bs, br] = pos(!aWins, 790);
    const pct = (p) => Math.round(p * fill);
    s += `<g opacity="${aWins ? 1 : 1 - win}">${card(bx, by, 6 + br, k2 * bs, '#ff8a2a', v.b, DUMBBELL, `${pct(v.pb)}%`)}</g>`;
    s += card(ax, ay, -6 + ar, k1 * as, '#3b8de0', v.a, SHOWER, `${pct(v.pa)}%`);
    if (win < 0.5) s += `<text x="540" y="660" text-anchor="middle" font-family="Lilita" font-size="130" fill="#ffd23f" ${st(12)} paint-order="stroke" opacity="${1 - win * 2}">VS</text>`;
    const ks = seg(lt, tw + 0.25, tw + 0.45);
    if (ks > 0) s += `<g transform="translate(560,1000) rotate(-10) scale(${lerp(2.6, 1, easeOut(ks))})" opacity="${ks}">
      <rect x="-330" y="-80" width="660" height="160" rx="22" fill="#fff4e0" stroke="#2f9e44" stroke-width="14"/>
      <text x="0" y="35" text-anchor="middle" font-family="Lilita" font-size="96" fill="#2f9e44">¡HA GANADO!</text></g>`;
    s += `<text x="540" y="300" text-anchor="middle" font-family="Fredoka" font-size="52" fill="#fff" opacity="${seg(lt, 0, 0.3)}">VUESTRA VOTACIÓN</text>`;
    return s;
  },
  // «Día uno: primera ducha en una semana.»
  H(lt, t) {
    let s = bathroom(t);
    s += place(hugo({ t, sucio: 1, mood: 'sad', mouth: 'o', look: 1, blink: blinkAt(t) }), 470, 1900 + Math.sin(t * 3) * 5, 1.05);
    return s;
  },
  // «Problema: no le queda gel, y tiene un euro con veinte.»
  G(lt, t) {
    let s = bathroom(t, 0.35);
    const tw = wordTime(6, 'tiene') - TL.lines[6].start;
    if (lt < tw) {
      const sq = Math.max(0, Math.sin(seg(lt, 0.3, tw) * Math.PI * 3));
      s += `<g transform="translate(540,900) rotate(180) scale(${1 + 0.25 * sq},${1 - 0.3 * sq})">
        <rect x="-140" y="-300" width="280" height="560" rx="70" fill="#9775fa" ${st(10)}/><rect x="-60" y="-370" width="120" height="80" rx="16" fill="#e5dbff" ${st(8)}/>
        <rect x="-100" y="-120" width="200" height="220" rx="20" fill="#fff" ${st(6)}/>
        <text x="0" y="10" text-anchor="middle" font-family="Lilita" font-size="80" fill="#7048e8" transform="rotate(180)">GEL</text>
        <text x="0" y="70" text-anchor="middle" font-family="Fredoka" font-size="30" fill="#7048e8" transform="rotate(180)">0 ml</text></g>`;
      if (sq > 0.3) for (let i = 0; i < 4; i++) s += `<circle cx="${540 + (i - 1.5) * 40}" cy="${1300 + i * 30 + sq * 40}" r="${20 + i * 8}" fill="#fff" opacity="${0.6 * sq}"/>`;
      s += place(hugo({ t, sucio: 1, mood: 'sad', mouth: 'o', look: 0 }), 540, 2600, 1.2);
    } else {
      const k = back(seg(lt, tw, tw + 0.3)), op = easeOut(seg(lt, tw + 0.15, tw + 0.45));
      s += `<g transform="translate(540,820) scale(${k})">
        <rect x="-300" y="-170" width="600" height="340" rx="40" fill="#8b5a2b" ${st(10)}/>
        <path d="M-300,-30 Q0,${-30 - 240 * op} 300,-30" fill="#6f4520" ${st(8)}/>
        <rect x="-240" y="-20" width="480" height="150" rx="16" fill="#5a3818"/>
        <circle cx="-60" cy="70" r="40" fill="#ffd43b" ${st(6)}/><circle cx="20" cy="80" r="30" fill="#ced4da" ${st(6)}/><circle cx="80" cy="74" r="22" fill="#e8590c" ${st(5)}/></g>`;
      const m = seg(lt, tw + 0.3, tw + 1.4);
      if (m > 0) s += `<g transform="translate(${540 + m * 220 + Math.sin(t * 9) * 30},${700 - m * 420})"><ellipse cx="-30" cy="0" rx="34" ry="${18 + 12 * Math.abs(Math.sin(t * 20))}" fill="#c9b79c" ${st(4)}/><ellipse cx="30" cy="0" rx="34" ry="${18 + 12 * Math.abs(Math.sin(t * 20))}" fill="#c9b79c" ${st(4)}/><ellipse rx="10" ry="26" fill="#6d5a45"/></g>`;
      const kk = back(seg(lt, tw + 0.5, tw + 0.8));
      s += `<g transform="translate(540,1120) rotate(-6) scale(${kk})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="170" fill="#ff2e48" stroke="#fff" stroke-width="20" paint-order="stroke">1,20 €</text></g>`;
    }
    return s;
  },
  // «Así que se lava con lavavajillas.»
  W(lt, t) {
    let s = bathroom(t);
    s += showerWater(t, 0.6);
    const k = easeOut(seg(lt, 0.2, 1.4));
    s += place(hugo({ t, naked: true, sucio: 1 - k * 0.6, mood: 'normal', mouth: 'smile', look: 0.3, blink: blinkAt(t, 0.3), armR: -150 }), 560, 1900, 1.15);
    s += `<g transform="translate(${660 + 140},${640 + Math.sin(t * 6) * 10}) rotate(${150 + Math.sin(t * 8) * 8})">
      <rect x="-70" y="-190" width="140" height="300" rx="40" fill="#51cf66" ${st(8)}/><rect x="-26" y="-240" width="52" height="60" rx="10" fill="#fff" ${st(6)}/>
      <text x="0" y="-60" text-anchor="middle" font-family="Lilita" font-size="38" fill="#fff" transform="rotate(-90 0 -60)">LAVA-TODO</text></g>`;
    s += bubbles(t, 560, 880, Math.round(6 + 26 * k), 60 + 240 * k);
    for (let i = 0; i < 5; i++) { const a = (t * 2 + i * 1.3) % 2, sc = Math.sin(clamp(a / 2) * Math.PI); s += `<path d="M0,-40 Q6,-6 40,0 Q6,6 0,40 Q-6,6 -40,0 Q-6,-6 0,-40 Z" transform="translate(${[300, 820, 250, 860, 540][i]},${[700, 760, 1100, 1180, 500][i]}) scale(${sc})" fill="#fff" ${st(4)}/>`; }
    // cortina delantera
    s += `<path d="M0,1350 Q540,1320 1080,1350 V1920 H0 Z" fill="#ff8fab" ${st()}/><path d="M150,1360 V1920 M330,1350 V1920 M520,1345 V1920 M720,1350 V1920 M910,1355 V1920" stroke="#e86f8f" stroke-width="8"/>`;
    return s;
  },
  // «Y encima, no hay agua caliente.» / «Hugo grita como un niño.» / «Pero aguanta.»
  C(lt, t) {
    const L = TL.lines, scream = t >= L[9].start - 0.05 && t < L[10].start - 0.05, hold = t >= L[10].start - 0.05;
    let s = bathroom(t) + `<rect width="1080" height="1920" fill="#4dabf7" opacity=".22"/>`;
    s += showerWater(t, 1, true);
    const jit = hold ? Math.sin(t * 60) * 6 : scream ? Math.sin(t * 45) * 14 : 0;
    s += place(hugo({ t, naked: true, sucio: 0.4, mood: hold ? 'determined' : scream ? 'sad' : 'normal', mouth: hold ? 'grit' : scream ? 'open' : 'o', look: 0, lookY: -1, armR: -6, armL: 6 }), 560 + jit, 1900, 1.15);
    // carámbanos y copos
    for (let i = 0; i < 7; i++) s += `<path d="M${408 + i * 40 + jit},${872} l12,${40 + (i % 3) * 22} l12,${-40 - (i % 3) * 22} Z" fill="#e7f5ff" ${st(4)}/>`;
    for (let i = 0; i < 14; i++) { const x = (i * 157 + t * 40) % 1080, y = (i * 241 + t * 160) % 1400; s += `<text x="${x}" y="${y}" font-size="46" fill="#fff" opacity=".8">❄</text>`; }
    if (scream) {
      const k = back(seg(t, L[9].start, L[9].start + 0.3));
      s += `<g transform="translate(540,470) rotate(${Math.sin(t * 30) * 4}) scale(${k})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="190" fill="#fff" stroke="${OUT}" stroke-width="22" paint-order="stroke">¡AAAAH!</text></g>`;
    }
    if (hold) {
      const k = back(seg(t, L[10].start + 0.1, L[10].start + 0.4));
      s += `<g transform="translate(840,700) scale(${k})"><circle r="90" fill="#ffd23f" ${st(8)}/><text x="0" y="34" text-anchor="middle" font-size="100">👍</text></g>`;
    }
    s += `<path d="M0,1350 Q540,1320 1080,1350 V1920 H0 Z" fill="#ff8fab" ${st()}/><path d="M150,1360 V1920 M330,1350 V1920 M520,1345 V1920 M720,1350 V1920 M910,1355 V1920" stroke="#e86f8f" stroke-width="8"/>`;
    return s;
  },
  // «Cuarenta minutos después… el desagüe se rinde.»
  D(lt, t) {
    const tw = wordTime(11, 'el') - TL.lines[11].start;
    if (lt < tw) {
      let s = `<rect width="1080" height="1920" fill="${OUT}"/>`;
      for (let i = 0; i < 30; i++) s += `<circle cx="${(i * 197) % 1080}" cy="${(i * 331 + lt * 200) % 1920}" r="${30 + (i % 5) * 20}" fill="#fff" opacity=".07"/>`;
      const k = back(seg(lt, 0.05, 0.35));
      s += `<g transform="translate(540,800) scale(${k})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="150" fill="#ffd23f">40 MIN</text><text x="0" y="130" text-anchor="middle" font-family="Lilita" font-size="110" fill="#fff">DESPUÉS…</text></g>`;
      s += `<g transform="translate(540,1160)"><circle r="80" fill="#fff" ${st(8)}/><path d="M0,0 L0,-55 M0,0 L${45 * Math.cos(lt * 12)},${45 * Math.sin(lt * 12)}" ${st(8)}/></g>`;
      return s;
    }
    const k = lt - tw;
    let s = `<rect width="1080" height="1920" fill="#e6eef2"/>`;
    for (let x = 0; x < 1080; x += 180) for (let y = 0; y < 1920; y += 180) if (((x + y) / 180) % 2 === 0) s += `<rect x="${x}" y="${y}" width="180" height="180" fill="#c3d2da"/>`;
    s += `<ellipse cx="540" cy="1000" rx="420" ry="230" fill="#8a7a5a" opacity=".55"/>`;
    s += `<circle cx="540" cy="1000" r="170" fill="#adb5bd" ${st(9)}/><circle cx="540" cy="1000" r="130" fill="#4a4033"/>`;
    for (let i = -2; i <= 2; i++) s += `<path d="M${540 + i * 45},880 V1120" stroke="#adb5bd" stroke-width="16"/>`;
    for (let i = 0; i < 6; i++) { const b = (k * 1.4 + i / 6) % 1; s += `<circle cx="${480 + (i * 37) % 120}" cy="${1000 - b * 80}" r="${10 + b * 22}" fill="#6d5f45" opacity="${1 - b}" ${st(3)}/>`; }
    const f = easeOut(seg(k, 0.25, 0.8));
    s += `<g transform="translate(540,${1000 - 420 * f})"><path d="M0,0 V-260" ${st(14)}/><path d="M0,0 V-260" stroke="#c9a46a" stroke-width="7"/>
      <path d="M8,-260 Q90,${-250 + Math.sin(t * 8) * 14} 170,-260 Q160,-200 170,-150 Q90,${-160 + Math.sin(t * 8 + 1) * 14} 8,-150 Z" fill="#fff" ${st(7)}/></g>`;
    if (k > 0.5) s += `<g transform="translate(540,1420) scale(${back(seg(k, 0.5, 0.8))})"><text x="0" y="0" text-anchor="middle" font-family="Lilita" font-size="120" fill="#fff" stroke="${OUT}" stroke-width="16" paint-order="stroke">¡ME RINDO!</text></g>`;
    return s;
  },
  // «Y hasta las moscas le abandonan.»
  M(lt, t) {
    let s = bathroom(t);
    s += place(hugo({ t, sucio: 0, mood: 'normal', mouth: 'smile', look: 1, lookY: -1, blink: blinkAt(t, 0.8), armR: lerp(-6, -150, easeOut(seg(lt, 0.4, 0.7))) + Math.sin(t * 14) * 10 * seg(lt, 0.7, 0.8) }), 420, 1940, 1.05);
    for (let i = 0; i < 4; i++) { const a = (t * 2 + i * 1.6) % 2, sc = Math.sin(clamp(a / 2) * Math.PI); s += `<path d="M0,-40 Q6,-6 40,0 Q6,6 0,40 Q-6,6 -40,0 Q-6,-6 0,-40 Z" transform="translate(${[180, 660, 230, 640][i]},${[820, 760, 1240, 1180][i]}) scale(${sc})" fill="#fff" ${st(4)}/>`; }
    for (let i = 0; i < 3; i++) {
      const k = easeInOut(seg(lt, 0.1 + i * 0.15, 1.6 + i * 0.15));
      s += fly(lerp(380 + i * 60, 1150, k) + Math.sin(t * 7 + i) * 20, lerp(820 + i * 40, 150, k) + Math.sin(t * 9 + i) * 15, t, i, true, true);
    }
    return s;
  },
  // «En la oficina, Laura pasa a su lado.» / «Y por primera vez… no se tapa la nariz.»
  O(lt, t) {
    const L = TL.lines, tc = wordTime(14, 'tapa');
    let s = classroom(t);
    const walk = easeInOut(seg(t, L[13].start, L[14].start + 0.3));
    s += place(hugo({ t, sucio: 0, mood: t > tc + 0.3 ? 'dream' : 'normal', mouth: t > tc + 0.3 ? 'smile' : 'o', look: 1, blink: blinkAt(t, 1.2) }), 280, 1700, 0.8);
    s += desk(280, 1490, 400);
    s += place(laura({ t, look: -1, blink: blinkAt(t, 0.4), mouth: 'smile' }), lerp(1250, 720, walk), 1720 + Math.abs(Math.sin(t * 9)) * -14 * (walk < 1 ? 1 : 0), 0.8);
    const kc = back(seg(t, tc + 0.1, tc + 0.4));
    if (kc > 0) s += `<g transform="translate(720,560) scale(${kc})"><circle r="100" fill="#46d17f" ${st(9)}/><path d="M-45,0 L-10,38 L50,-38" fill="none" stroke="#fff" stroke-width="22" stroke-linecap="round" stroke-linejoin="round"/></g>`;
    return s;
  },
  // «Mañana: ¿gym o dentista?» / «Comenta, y sígueme para el día dos.»
  P(lt, t) {
    let s = classroom(t) + `<rect width="1080" height="1920" fill="${OUT}" opacity=".45"/>`;
    s += place(hugo({ t, sucio: 0, mood: 'normal', mouth: 'o', look: Math.sin(t * 1.6), lookY: -1, blink: blinkAt(t, 1.4), armR: -150 + Math.sin(t * 3) * 4 }), 540, 2060 + Math.sin(t * 3) * 5, 1.15);
    const tA = wordTime(15, 'gym'), tB = wordTime(15, 'dentista');
    const pulse = (tw) => { const d = t - tw; return d > 0 && d < 0.5 ? Math.sin(d / 0.5 * Math.PI) * 0.12 : 0; };
    const k1 = back(seg(lt, 0.05, 0.4)), k2 = back(seg(lt, 0.2, 0.55));
    s += card(290, 600 + Math.sin(t * 2.2) * 8, -6, k1 * (1 + pulse(tA)), '#ff8a2a', 'GYM', DUMBBELL, '¿MAÑANA?');
    s += card(790, 600 + Math.sin(t * 2.2 + 1) * 8, 6, k2 * (1 + pulse(tB)), '#12b886', 'DENTISTA', TOOTH, '¿MAÑANA?');
    const kv = back(seg(lt, 0.4, 0.7));
    s += `<text x="540" y="650" text-anchor="middle" font-family="Lilita" font-size="${130 * kv}" fill="#ffd23f" ${st(12)} paint-order="stroke">VS</text>`;
    return s;
  },
});
const SCENE_OF = ['I0', 'I1', 'I2', 'I3', 'V', 'H', 'G', 'W', 'C', 'C', 'C', 'D', 'M', 'O', 'O', 'P', 'P'];

// marcador: visible desde «Día uno»; los días pasan de 30 a 29; al no taparse la nariz, GLOW 0→4 % y barra 5→8 %
EP.hud = function (t) {
  const L = TL.lines, tu = wordTime(3, 'uno'), tc = wordTime(14, 'tapa') + 0.3;
  const up = easeOut(seg(t, tc, tc + 0.8));
  const extra = t > tc && t < tc + 1.6 ? `<g transform="translate(260,${330 - 60 * seg(t, tc, tc + 1.6)})" opacity="${1 - seg(t, tc + 1.1, tc + 1.6)}"><text font-family="Lilita" font-size="80" fill="#46d17f" stroke="${OUT}" stroke-width="12" paint-order="stroke">+4%</text></g>` : '';
  return {
    show: t < L[4].start - 0.1 ? seg(t, L[3].start, L[3].start + 0.35) : seg(t, L[5].start - 0.08, L[5].start + 0.25), // oculto durante la votación
    days: t < tu ? 30 : 29,
    dayPulse: Math.max(0, Math.sin(seg(t, tu, tu + 0.4) * Math.PI)),
    glow: lerp(0, 4, up), glowPulse: Math.max(0, Math.sin(seg(t, tc, tc + 0.5) * Math.PI)),
    pct: lerp(5, 8, up), extra,
  };
};
EP.shake = (sc, lt, t) => {
  const L = TL.lines;
  if (sc === 'C' && t >= L[9].start - 0.05 && t < L[9].start + 0.5) return 16 * (1 - seg(t, L[9].start, L[9].start + 0.5));
  return 0;
};

// ---------- efectos de sonido ----------
window.sfxCues = function () {
  const L = TL.lines, c = [];
  const add = (t, f, v, d) => c.push({ t: Math.max(0, t), f, v, d });
  introCues(add);
  const s0 = (i) => L[SCENE_OF.indexOf(SCENE_OF[i])].start - 0.08;
  // votación
  const v0 = L[4].start - 0.08;
  add(v0 + 0.05, 'pop', 0.5); add(v0 + 0.15, 'pop', 0.5);
  for (let i = 0; i < 8; i++) add(v0 + 0.35 + i * 0.1, 'tick', 0.35);
  add(wordTime(4, 'ganó') + 0.33, 'stamp', 0.75);
  add(wordTime(4, 'ganó') + 0.4, 'sparkle', 0.4);
  // ducha
  add(L[5].start - 0.08, 'whoosh', 0.5);
  add(L[5].start, 'buzz', 0.2, L[6].end);
  // gel vacío y cartera
  add(s0(6) + 0.35, 'pfff', 0.6);
  add(wordTime(6, 'tiene'), 'pop', 0.5);
  add(wordTime(6, 'tiene') + 0.35, 'buzz', 0.12, wordTime(6, 'tiene') + 1.3);
  add(wordTime(6, 'euro') + 0.1, 'fail', 0.25);
  // lavavajillas
  add(L[7].start - 0.08, 'water', 0.3, L[11].start - 0.1);
  add(L[7].start + 0.2, 'sparkle', 0.45);
  for (let i = 0; i < 5; i++) add(L[7].start + 0.3 + i * 0.22, 'pop', 0.35);
  // agua fría, grito y aguante
  add(L[8].start, 'impact', 0.45);
  add(L[9].start - 0.05, 'boing', 0.5);
  add(L[10].start, 'chatter', 0.55);
  add(L[10].start + 0.1, 'ding', 0.3);
  // desagüe
  add(L[11].start - 0.08, 'tick', 0.4); add(L[11].start + 0.2, 'tick', 0.4); add(L[11].start + 0.45, 'tick', 0.4);
  add(wordTime(11, 'el'), 'glug', 0.6);
  add(wordTime(11, 'el') + 0.6, 'fail', 0.28);
  // moscas que se van
  add(L[12].start - 0.08, 'buzz', 0.25, L[12].end + 0.3);
  add(L[12].start + 0.2, 'whoosh', 0.4);
  add(L[12].start, 'sparkle', 0.35);
  // oficina
  add(L[13].start - 0.08, 'whoosh', 0.35);
  add(wordTime(14, 'tapa') + 0.1, 'ding', 0.45);
  add(wordTime(14, 'tapa') + 0.3, 'magic', 0.45);
  // votación nueva
  add(L[15].start + 0.12, 'pop', 0.5); add(L[15].start + 0.28, 'pop', 0.5);
  add(L[15].start + 0.42, 'impact', 0.25);
  add(wordTime(15, 'gym'), 'boing', 0.35);
  add(wordTime(15, 'dentista'), 'ding', 0.3);
  add(TL.total - 0.9, 'whoosh', 0.3);
  return c;
};

start();

// Capítulo 1 — Día 0: presentación. Votación: DUCHA vs GYM.
const EP = { num: 1, day: 0, daysLeft: 30, glow: 0 };

const LINES = [
  { text: 'Este es Hugo.', hl: ['hugo'] },
  { text: 'Veintiséis años. Cinco de becario.', hl: ['becario'] },
  { text: 'Lleva una semana sin ducharse.', hl: ['sin', 'ducharse'] },
  { text: 'Y dos años enamorado de Laura.', hl: ['laura'] },
  { text: 'Laura no sabe ni cómo se llama.', hl: ['no', 'sabe'] },
  { text: 'Porque Laura sale con Marcos.', hl: ['marcos'], red: ['marcos'] },
  { text: 'Cena de empresa: en treinta días.', hl: ['treinta', 'días'] },
  { text: 'Hugo tiene treinta días para cambiar.', hl: ['cambiar'] },
  { text: '¿Por dónde empieza?', hl: ['empieza'] },
  { text: 'Comenta: ducha o gym.', hl: ['ducha', 'gym'] },
  { text: 'Sígueme para el día uno.', hl: ['día', 'uno'] },
];

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
    s += `<g transform="translate(540,520) rotate(5) scale(${k})">
      <path d="M-60,-150 L-20,-300 M60,-150 L20,-300" stroke="#1c7ed6" stroke-width="16"/>
      <rect x="-250" y="-150" width="500" height="300" rx="24" fill="#fff" ${st(9)}/>
      <rect x="-250" y="-150" width="500" height="70" rx="24" fill="#1c7ed6"/><rect x="-250" y="-100" width="500" height="20" fill="#1c7ed6"/>
      <text x="0" y="-100" text-anchor="middle" font-family="Fredoka" font-size="36" fill="#fff">OFICINAS S.L.</text>
      <rect x="-220" y="-55" width="130" height="160" rx="12" fill="#ffe0b8" ${st(5)}/><circle cx="-155" cy="0" r="38" fill="#a8552a"/><circle cx="-155" cy="10" r="30" fill="#efbd95"/>
      <text x="80" y="20" text-anchor="middle" font-family="Lilita" font-size="64" fill="#e03131">BECARIO</text>
      <text x="80" y="90" text-anchor="middle" font-family="Fredoka" font-size="38" fill="${OUT}" opacity="${seg(lt, 0.6, 0.9)}">DESDE 2021</text></g>`;
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
    let s = `<rect width="1080" height="1920" fill="#dfe6ee"/>`;
    s += `<rect x="70" y="250" width="940" height="900" rx="20" fill="#c8935a" ${st()}/>`;
    for (let i = 0; i < 40; i++) s += `<circle cx="${100 + (i * 197) % 880}" cy="${280 + (i * 131) % 840}" r="4" fill="#a87440"/>`;
    const sw = Math.sin(t * 2) * 1.5;
    s += `<g transform="translate(540,690) rotate(${-4 + sw})">
      <rect x="-330" y="-400" width="660" height="790" rx="14" fill="#7a1022" ${st(9)}/>
      <circle cx="0" cy="-400" r="16" fill="#ff4d5e" ${st(6)}/>
      <g transform="translate(0,-190)">
        <path d="M0,-150 L110,40 L-110,40 Z M0,-90 L130,110 L-130,110 Z" fill="#2f9e44" ${st(7)}/><rect x="-22" y="110" width="44" height="40" fill="#8a5a32" ${st(6)}/>
        <path d="M0,-185 l14,30 l32,4 l-24,22 l7,32 l-29,-16 l-29,16 l7,-32 l-24,-22 l32,-4 Z" fill="#ffd23f" ${st(5)}/>
        ${[[-40, -30, '#ff4d6d'], [45, 10, '#4dabf7'], [-60, 80, '#ffd23f'], [55, 80, '#ff4d6d'], [0, 40, '#fff']].map(([x, y, c], i) => `<circle cx="${x}" cy="${y}" r="15" fill="${c}" opacity="${0.6 + 0.4 * Math.sin(t * 6 + i)}" ${st(4)}/>`).join('')}</g>
      <text x="0" y="40" text-anchor="middle" font-family="Lilita" font-size="120" fill="#ffd23f" ${st(10)} paint-order="stroke">CENA DE</text>
      <text x="0" y="140" text-anchor="middle" font-family="Lilita" font-size="110" fill="#fff">EMPRESA</text>
      <text x="0" y="220" text-anchor="middle" font-family="Fredoka" font-size="42" fill="#ff8ab0">NAVIDAD · VIERNES · 21:00</text>
      <text x="0" y="300" text-anchor="middle" font-family="Fredoka" font-size="36" fill="#fff" opacity=".7">¡TRAED PAREJA!</text></g>`;
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

// marcador: aparece cuando entra Marcos y la barra cae de 50 a 5
EP.hud = function (t) {
  const L = TL.lines, hudStart = L[5].start + 0.5;
  const pct = t < hudStart + 0.3 ? 50 : lerp(50, 5, easeOut(seg(t, hudStart + 0.3, hudStart + 1.3)));
  let hx = 0;
  if (t > hudStart + 1.1 && t < hudStart + 1.6) hx = Math.sin(t * 80) * 10 * (1 - seg(t, hudStart + 1.1, hudStart + 1.6));
  const dayPulse = Math.max(0, Math.sin(seg(t, wordTime(6, 'treinta'), wordTime(6, 'treinta') + 0.4) * Math.PI), Math.sin(seg(t, wordTime(7, 'treinta'), wordTime(7, 'treinta') + 0.4) * Math.PI));
  return { show: seg(t, hudStart, hudStart + 0.35), pct, hx, dayPulse };
};
EP.shake = (sc, lt) => (sc === 'F' && lt < 0.6 ? (1 - lt / 0.6) * 18 : 0);

// ---------- efectos de sonido ----------
// Cada efecto: { t: segundo, f: archivo de sfx/, v: volumen, d: duración máxima opcional }
window.sfxCues = function () {
  const L = TL.lines, c = [];
  const add = (t, f, v, d) => c.push({ t: Math.max(0, t), f, v, d });
  add(0.05, 'buzz', 0.22, L[2].end);                         // moscas mientras se le presenta
  add(L[0].end - 0.05, 'burp', 0.5);                          // «Este es Hugo.» *eructo*
  add(L[1].start + 0.1, 'pop', 0.5);                          // sale la acreditación
  add(wordTime(1, 'becario'), 'boing', 0.4);
  for (let i = 0; i < 7; i++) add(L[2].start - 0.08 + 0.3 + i * 0.115, 'tick', 0.45); // contador de días sin ducha
  add(L[3].start, 'sparkle', 0.4);                            // corazones
  add(L[4].start - 0.08 + 0.25, 'pop', 0.55);                 // bocadillo de Laura
  add(L[5].start - 0.08, 'whoosh', 0.6);                      // entra Marcos
  add(L[5].start + 0.35, 'sparkle', 0.3);
  add(L[5].start - 0.08 + 0.7, 'crack', 0.55);                // corazón roto
  add(L[5].start + 0.85, 'fail', 0.22);                       // la barra se hunde
  add(wordTime(6, 'treinta'), 'stamp', 0.75);                 // sello «EN 30 DÍAS»
  add(L[7].start - 0.08, 'whoosh', 0.4);
  add(L[7].start, 'impact', 0.6);                             // Hugo se levanta
  add(wordTime(7, 'cambiar') - 0.1, 'magic', 0.5);            // silueta del Hugo transformado
  add(L[8].start + 0.12, 'pop', 0.5);                         // cartas
  add(L[8].start + 0.28, 'pop', 0.5);
  add(L[8].start + 0.42, 'impact', 0.25);                     // VS
  add(wordTime(9, 'ducha'), 'ding', 0.3);
  add(wordTime(9, 'gym'), 'boing', 0.35);
  add(TL.total - 0.9, 'whoosh', 0.3);
  return c;
};


start();

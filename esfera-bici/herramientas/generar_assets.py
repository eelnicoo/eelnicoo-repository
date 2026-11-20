#!/usr/bin/env python3
"""Genera todas las imágenes de la esfera «Ruta Bici» (480x480, Amazfit T-Rex 3).

Uso:  python3 herramientas/generar_assets.py
Escribe en assets/trex3/ y crea una vista previa en preview.png.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FUENTES = os.path.join(RAIZ, "herramientas", "fuentes")
SALIDA = os.path.join(RAIZ, "assets", "trex3")
IMG = os.path.join(SALIDA, "images")

S = 4            # supermuestreo para bordes suaves
W = H = 480
CX = CY = 240

# Paleta
NEGRO = (0, 0, 0, 255)
BLANCO = (255, 255, 255, 255)
LIMA = (198, 245, 58, 255)          # acento ciclista
LIMA_OSC = (110, 140, 36, 255)
GRIS = (140, 146, 155, 255)
GRIS_OSC = (40, 44, 50, 255)
PISTA = (34, 38, 44, 255)
ROJO = (255, 77, 94, 255)
NARANJA = (255, 140, 40, 255)
AMARILLO = (255, 210, 50, 255)


def fuente(peso, tam, italica=False):
    nombre = f"barlow-condensed-latin-{peso}-{'italic' if italica else 'normal'}.woff"
    return ImageFont.truetype(os.path.join(FUENTES, nombre), tam)


def lienzo(w=W, h=H):
    return Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))


def reducir(im):
    return im.resize((im.width // S, im.height // S), Image.LANCZOS)


def guardar(im, ruta):
    ruta = os.path.join(IMG, ruta)
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    im.save(ruta)
    return im


def mezcla(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def polar(r, ang, cx=CX, cy=CY):
    a = math.radians(ang)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def texto_centrado(d, xy, txt, f, color, espaciado=0, ancla="mm"):
    """Texto con espaciado entre letras (coordenadas ya multiplicadas por S)."""
    if not espaciado:
        d.text(xy, txt, font=f, fill=color, anchor=ancla)
        return
    anchos = [f.getlength(c) for c in txt]
    total = sum(anchos) + espaciado * (len(txt) - 1)
    x = xy[0] - total / 2 if ancla[0] == "m" else xy[0]
    for c, a in zip(txt, anchos):
        d.text((x, xy[1]), c, font=f, fill=color, anchor="l" + ancla[1])
        x += a + espaciado


# ---------------------------------------------------------------- dígitos
def juego_digitos(carpeta, tam, color, peso=600, ancho=None, alto=None, italica=False,
                  extras=None):
    """Crea 0.png..9.png con caja fija para que IMG_TIME/TEXT_IMG alineen bien."""
    f = fuente(peso, tam * S, italica)
    cajas = [f.getbbox(str(n), anchor="ls") for n in range(10)]
    if ancho is None:
        ancho = math.ceil(max(b[2] - b[0] for b in cajas) / S)
    asc = max(-b[1] for b in cajas)
    if alto is None:
        alto = math.ceil(asc / S) + 2
    for n in range(10):
        im = Image.new("RGBA", (ancho * S, alto * S), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.text((ancho * S / 2, alto * S - S), str(n), font=f, fill=color, anchor="ms")
        guardar(reducir(im), f"{carpeta}/{n}.png")
    for nombre, txt in (extras or {}).items():
        b = f.getbbox(txt, anchor="ls")
        aw = math.ceil((b[2] - b[0]) / S) + 4
        im = Image.new("RGBA", (aw * S, alto * S), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.text((aw * S / 2, alto * S - S), txt, font=f, fill=color, anchor="ms")
        guardar(reducir(im), f"{carpeta}/{nombre}.png")
    return ancho, alto


def unidad(ruta, txt, tam, color, alto, peso=600):
    """Imagen de unidad (°, km…) con la misma altura que los dígitos, alineada a la base."""
    f = fuente(peso, tam * S)
    b = f.getbbox(txt, anchor="ls")
    aw = math.ceil((b[2] - b[0]) / S) + 4
    im = Image.new("RGBA", (aw * S, alto * S), (0, 0, 0, 0))
    ImageDraw.Draw(im).text((2 * S - b[0], alto * S - S), txt, font=f, fill=color, anchor="ls")
    guardar(reducir(im), ruta)


# ---------------------------------------------------------------- iconos
def bici(d, cx, cy, esc, color, grosor):
    """Silueta de bicicleta de carretera centrada en (cx, cy). esc ~ radio de rueda."""
    r = esc
    sep = esc * 2.35
    ra = (cx - sep / 2, cy + r * 0.25)
    rb = (cx + sep / 2, cy + r * 0.25)
    g = grosor
    for c in (ra, rb):
        d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], outline=color, width=g)
    eje = (cx - sep * 0.02, ra[1])                       # pedalier
    sillin = (cx - sep * 0.2, cy - r * 1.05)
    direc = (cx + sep * 0.3, cy - r * 0.95)
    for a, b in ((ra, eje), (eje, sillin), (sillin, ra), (eje, direc), (sillin, direc),
                 (direc, rb)):
        d.line([a, b], fill=color, width=g)
    # sillín y manillar
    d.line([(sillin[0] - r * 0.35, sillin[1] - g * 0.6), (sillin[0] + r * 0.3, sillin[1] - g * 0.6)],
           fill=color, width=int(g * 1.2))
    d.line([direc, (direc[0] + r * 0.25, direc[1] - r * 0.35)], fill=color, width=g)
    d.line([(direc[0] + r * 0.25, direc[1] - r * 0.35), (direc[0] + r * 0.55, direc[1] - r * 0.3)],
           fill=color, width=g)
    d.ellipse([eje[0] - g, eje[1] - g, eje[0] + g, eje[1] + g], fill=color)


def icono_corazon(tam, color):
    im = lienzo(tam, tam)
    d = ImageDraw.Draw(im)
    t = tam * S
    r = t * 0.26
    d.ellipse([t * 0.06, t * 0.12, t * 0.06 + 2 * r, t * 0.12 + 2 * r], fill=color)
    d.ellipse([t * 0.94 - 2 * r, t * 0.12, t * 0.94, t * 0.12 + 2 * r], fill=color)
    d.polygon([(t * 0.08, t * 0.42), (t * 0.92, t * 0.42), (t * 0.5, t * 0.92)], fill=color)
    return reducir(im)


def icono_rayo(tam, color):
    im = lienzo(tam, tam)
    d = ImageDraw.Draw(im)
    t = tam * S
    d.polygon([(t * .58, t * .04), (t * .2, t * .56), (t * .46, t * .56), (t * .38, t * .96),
               (t * .8, t * .4), (t * .53, t * .4)], fill=color)
    return reducir(im)


def icono_bateria(w, h, color):
    im = lienzo(w, h)
    d = ImageDraw.Draw(im)
    g = int(1.6 * S)
    d.rounded_rectangle([g, g, (w - 4) * S, h * S - g], radius=2.5 * S, outline=color, width=g)
    d.rectangle([(w - 3.5) * S, h * S * .32, (w - .5) * S, h * S * .68], fill=color)
    d.rectangle([g * 2.6, g * 2.6, (w - 4) * S * .55, h * S - g * 2.6], fill=color)
    return reducir(im)


def icono_bt_off(tam, color):
    im = lienzo(tam, tam)
    d = ImageDraw.Draw(im)
    t = tam * S
    g = int(1.8 * S)
    x0, x1 = t * .32, t * .68
    d.line([(x0, t * .3), (x1, t * .66), (t * .5, t * .86), (t * .5, t * .14), (x1, t * .34), (x0, t * .7)],
           fill=color, width=g, joint="curve")
    d.line([(t * .12, t * .12), (t * .88, t * .88)], fill=ROJO, width=g)
    return reducir(im)


# --- tiempo
def nube(d, cx, cy, s, color):
    d.ellipse([cx - s * .9, cy - s * .1, cx - s * .1, cy + s * .6], fill=color)
    d.ellipse([cx - s * .45, cy - s * .55, cx + s * .45, cy + s * .45], fill=color)
    d.ellipse([cx + s * .05, cy - s * .2, cx + s * .85, cy + s * .6], fill=color)
    d.rectangle([cx - s * .5, cy + s * .2, cx + s * .5, cy + s * .6], fill=color)


def sol(d, cx, cy, r, color):
    for i in range(8):
        a = i * 45
        p1, p2 = polar(r * 1.35, a, cx, cy), polar(r * 1.85, a, cx, cy)
        d.line([p1, p2], fill=color, width=int(r * .28))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)


def luna(d, cx, cy, r, color):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    d.ellipse([cx - r * .35, cy - r * 1.25, cx + r * 1.4, cy + r * .5], fill=(0, 0, 0, 0))


def icono_tiempo(tipo):
    t = 40 * S
    im = lienzo(40, 40)
    d = ImageDraw.Draw(im)
    nube_c = (205, 212, 222, 255)
    sol_c = (255, 196, 46, 255)
    agua = (90, 180, 255, 255)
    if tipo == "sol":
        sol(d, t / 2, t / 2, t * .2, sol_c)
    elif tipo == "luna":
        luna(d, t / 2, t / 2, t * .3, (230, 230, 245, 255))
    elif tipo == "solnube":
        sol(d, t * .62, t * .36, t * .14, sol_c)
        nube(d, t * .42, t * .58, t * .3, nube_c)
    elif tipo == "lunanube":
        luna(d, t * .64, t * .34, t * .18, (230, 230, 245, 255))
        nube(d, t * .42, t * .58, t * .3, nube_c)
    elif tipo == "nube":
        nube(d, t / 2, t * .52, t * .38, nube_c)
    elif tipo in ("lluvia", "nieve", "tormenta"):
        nube(d, t / 2, t * .38, t * .34, nube_c)
        if tipo == "lluvia":
            for x in (.3, .5, .7):
                d.line([(t * x, t * .74), (t * (x - .06), t * .92)], fill=agua, width=int(t * .06))
        elif tipo == "nieve":
            for x in (.3, .5, .7):
                d.ellipse([t * x - t * .045, t * .8 - t * .045, t * x + t * .045, t * .8 + t * .045],
                          fill=BLANCO)
        else:
            d.polygon([(t * .52, t * .62), (t * .36, t * .82), (t * .48, t * .82), (t * .42, t * .98),
                       (t * .64, t * .74), (t * .52, t * .74), (t * .6, t * .62)], fill=sol_c)
    elif tipo in ("niebla", "polvo"):
        c = nube_c if tipo == "niebla" else (214, 170, 110, 255)
        for i, y in enumerate((.3, .5, .7)):
            d.line([(t * (.18 + .06 * (i % 2)), t * y), (t * (.82 - .06 * (i % 2)), t * y)],
                   fill=c, width=int(t * .07))
    return reducir(im)


TIEMPO = ["solnube", "lluvia", "nieve", "sol", "nube", "lluvia", "nieve", "lluvia", "nieve", "nieve",
          "lluvia", "polvo", "nieve", "niebla", "niebla", "tormenta", "nieve", "polvo", "lluvia",
          "lluvia", "tormenta", "lluvia", "polvo", "polvo", "lluvia", "nube", "lunanube", "lluvia",
          "luna"]


# ---------------------------------------------------------------- barras laterales
SEGMENTOS = 18
R_IN, R_OUT = 213, 226
HUECO = 1.2  # grados entre segmentos


def angulos_barra(lado):
    """Lista de (a0, a1) de abajo hacia arriba. Izquierda 138°→222°, derecha 42°→-42°."""
    if lado == "izq":
        ini, fin = 138, 222
    else:
        ini, fin = 42, -42
    paso = (fin - ini) / SEGMENTOS
    res = []
    for i in range(SEGMENTOS):
        a0 = ini + i * paso
        a1 = a0 + paso
        if paso > 0:
            res.append((a0 + HUECO / 2, a1 - HUECO / 2))
        else:
            res.append((a1 + HUECO / 2, a0 - HUECO / 2))
    return res


def segmento(d, a0, a1, color):
    pts = []
    n = 8
    for k in range(n + 1):
        pts.append(polar(R_OUT * S, a0 + (a1 - a0) * k / n, CX * S, CY * S))
    for k in range(n, -1, -1):
        pts.append(polar(R_IN * S, a0 + (a1 - a0) * k / n, CX * S, CY * S))
    d.polygon(pts, fill=color)


def color_bateria(i, encendidos):
    if encendidos <= 3:
        return ROJO
    if encendidos <= 5:
        return NARANJA
    return mezcla((235, 240, 245, 255), LIMA, i / (SEGMENTOS - 1))


def color_bio(i):
    t = i / (SEGMENTOS - 1)
    tramos = [(0, ROJO), (.35, NARANJA), (.6, AMARILLO), (1, LIMA)]
    for (t0, c0), (t1, c1) in zip(tramos, tramos[1:]):
        if t <= t1:
            return mezcla(c0, c1, (t - t0) / (t1 - t0))
    return LIMA


def barras():
    cajas = {}
    for lado, nombre in (("izq", "bateria"), ("der", "bio")):
        segs = angulos_barra(lado)
        # caja común (todas las imágenes iguales para IMG_LEVEL)
        ref = lienzo()
        dr = ImageDraw.Draw(ref)
        for a0, a1 in segs:
            segmento(dr, a0, a1, BLANCO)
        caja = reducir(ref).getbbox()
        caja = (caja[0] - 1, caja[1] - 1, caja[2] + 1, caja[3] + 1)
        cajas[nombre] = caja
        for nivel in range(SEGMENTOS + 1):
            im = lienzo()
            d = ImageDraw.Draw(im)
            for i, (a0, a1) in enumerate(segs[:nivel]):
                c = color_bateria(i, nivel) if nombre == "bateria" else color_bio(i)
                segmento(d, a0, a1, c)
            guardar(reducir(im).crop(caja), f"barra_{nombre}/{nivel}.png")
    return cajas


# ---------------------------------------------------------------- días de la semana
DIAS = ["LU", "MA", "MI", "JU", "VI", "SA", "DO"]
D_CIRC, PASO_DIA = 34, 40


def semana():
    ancho = PASO_DIA * 6 + D_CIRC + 2
    f = fuente(700, 17 * S)
    for hoy in range(7):
        im = lienzo(ancho, D_CIRC + 2)
        d = ImageDraw.Draw(im)
        for i, txt in enumerate(DIAS):
            x = (1 + i * PASO_DIA) * S
            caja = [x, S, x + D_CIRC * S, (D_CIRC + 1) * S]
            if i == hoy:
                d.ellipse(caja, fill=BLANCO)
                col = NEGRO
            else:
                d.ellipse(caja, fill=(30, 33, 38, 255))
                col = (200, 205, 212, 255) if i < 5 else GRIS
            d.text((x + D_CIRC * S / 2, (D_CIRC / 2 + 1.5) * S), txt, font=f, fill=col, anchor="mm")
        guardar(reducir(im), f"semana/{hoy}.png")
    return ancho


# ---------------------------------------------------------------- fondo
def mapa(d, caja):
    """Callejero estilizado con una ruta en bici resaltada."""
    x0, y0, x1, y1 = [v * S for v in caja]
    calles = [
        [(x0, y0 + 30 * S), (x1, y0 + 8 * S)],
        [(x0, y0 + 70 * S), (x1, y0 + 52 * S)],
        [(x0 + 10 * S, y1), (x1, y0 + 92 * S)],
        [(x0 + 25 * S, y0), (x0 + 5 * S, y1)],
        [(x0 + 70 * S, y0), (x0 + 60 * S, y1)],
        [(x0 + 118 * S, y0), (x0 + 122 * S, y1)],
        [(x0 + 150 * S, y0 + 10 * S), (x1, y1 - 10 * S)],
        [(x0, y0 + 110 * S), (x0 + 90 * S, y1)],
        [(x0 + 40 * S, y0), (x1 - 10 * S, y0 + 130 * S)],
    ]
    for c in calles:
        d.line(c, fill=(58, 63, 70, 255), width=int(1.6 * S))
    for c in calles[:3]:
        d.line(c, fill=(70, 76, 84, 255), width=int(3 * S))


def ruta(capa, caja):
    x0, y0 = caja[0] * S, caja[1] * S
    pts = [(14, 104), (22, 82), (40, 70), (58, 72), (66, 54), (84, 40), (104, 36), (120, 22),
           (140, 26), (150, 44), (138, 60), (122, 62), (118, 86), (100, 104), (76, 110), (56, 122)]
    pts = [(x0 + x * S, y0 + y * S) for x, y in pts]
    brillo = Image.new("RGBA", capa.size, (0, 0, 0, 0))
    db = ImageDraw.Draw(brillo)
    db.line(pts, fill=(198, 245, 58, 150), width=int(9 * S), joint="curve")
    brillo = brillo.filter(ImageFilter.GaussianBlur(5 * S))
    capa.alpha_composite(brillo)
    d = ImageDraw.Draw(capa)
    d.line(pts, fill=LIMA, width=int(3.2 * S), joint="curve")
    for (x, y), c in ((pts[0], BLANCO), (pts[-1], LIMA)):
        r = 4.2 * S
        d.ellipse([x - r, y - r, x + r, y + r], fill=c, outline=NEGRO, width=int(1.2 * S))


def boton(d, caja, pulsado=False):
    x0, y0, x1, y1 = [v * S for v in caja]
    r = (y1 - y0) / 2
    if pulsado:
        d.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=(198, 245, 58, 110))
        return
    d.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=(12, 14, 10, 235), outline=LIMA,
                        width=int(2.4 * S))
    f = fuente(700, 27 * S, italica=True)
    bici(d, x0 + 30 * S, (y0 + y1) / 2 - 1 * S, 7.2 * S, LIMA, int(2.2 * S))
    d.text((x0 + 92 * S, (y0 + y1) / 2 + 1 * S), "A RODAR", font=f, fill=BLANCO, anchor="mm")


def fondo(cajas, pos):
    base = lienzo()
    d = ImageDraw.Draw(base)
    d.ellipse([0, 0, W * S, H * S], fill=NEGRO)

    # marcas del bisel
    for i in range(120):
        ang = i * 3
        larga = i % 10 == 0
        if any(lo <= (ang % 360) <= hi for lo, hi in ((136, 224), (0, 44), (316, 360))):
            r0, r1 = 230, 237
        else:
            r0, r1 = (222, 236) if larga else (229, 236)
        col = (110, 116, 124, 255) if larga else (52, 56, 62, 255)
        d.line([polar(r0 * S, ang, CX * S, CY * S), polar(r1 * S, ang, CX * S, CY * S)], fill=col,
               width=int((2.2 if larga else 1.4) * S))

    # pista apagada de las barras
    for lado in ("izq", "der"):
        for a0, a1 in angulos_barra(lado):
            segmento(d, a0, a1, PISTA)

    # mapa con degradado radial
    caja_mapa = pos["mapa"]
    capa = lienzo()
    dm = ImageDraw.Draw(capa)
    mapa(dm, caja_mapa)
    ruta(capa, caja_mapa)
    mascara = Image.new("L", capa.size, 0)
    mx0, my0, mx1, my1 = [v * S for v in caja_mapa]
    ImageDraw.Draw(mascara).ellipse([mx0 - 10 * S, my0 - 6 * S, mx1 + 10 * S, my1 + 6 * S], fill=255)
    mascara = mascara.filter(ImageFilter.GaussianBlur(16 * S))
    capa.putalpha(Image.composite(capa.getchannel("A"), Image.new("L", capa.size, 0), mascara))
    base.alpha_composite(capa)
    d = ImageDraw.Draw(base)

    boton(d, pos["boton"])

    # etiquetas
    fe = fuente(600, 15 * S)
    for clave, txt in (("et_bat", "BATERÍA"), ("et_bio", "ENERGÍA"), ("et_pasos", "PASOS"),
                       ("et_dist", "DISTANCIA · KM")):
        x, y = pos[clave]
        anc = "mm" if clave in ("et_bat", "et_bio") else "lm"
        texto_centrado(d, (x * S, y * S), txt, fe, GRIS, espaciado=int(1.2 * S), ancla=anc)

    # iconos fijos
    base.alpha_composite(lienzo_icono(icono_corazon(22, ROJO)), dest=(pos["corazon"][0] * S, pos["corazon"][1] * S))
    base.alpha_composite(lienzo_icono(icono_bateria(24, 13, GRIS)), dest=(pos["ico_bat"][0] * S, pos["ico_bat"][1] * S))
    base.alpha_composite(lienzo_icono(icono_rayo(20, AMARILLO)), dest=(pos["ico_bio"][0] * S, pos["ico_bio"][1] * S))

    # logo inferior: ▸ bici ◂
    bx, by = pos["logo"]
    bici(d, bx * S, by * S, 11 * S, LIMA, int(3.2 * S))
    for lado in (-1, 1):
        x = bx * S + lado * 50 * S
        p = 6 * S
        d.polygon([(x - lado * p, by * S - p), (x - lado * p, by * S + p), (x + lado * p * .3, by * S)],
                  fill=NARANJA)
    return reducir(base)


def lienzo_icono(im):
    """Escala un icono ya reducido a la resolución supermuestreada para pegarlo en el fondo."""
    return im.resize((im.width * S, im.height * S), Image.LANCZOS)


# ---------------------------------------------------------------- vista previa
def vista_previa(fondo_im, pos, cajas, dig, ancho_sem):
    im = fondo_im.copy()

    def pegar(ruta, xy):
        p = Image.open(os.path.join(IMG, ruta))
        im.alpha_composite(p, dest=(int(xy[0]), int(xy[1])))
        return p

    def numero(txt, carpeta, x, y, w, alin="c", unidad_img=None, espacio=1):
        imgs = []
        for c in txt:
            nombre = {"." : "punto", "-": "menos"}.get(c, c)
            imgs.append(Image.open(os.path.join(IMG, carpeta, f"{nombre}.png")))
        if unidad_img:
            imgs.append(Image.open(os.path.join(IMG, unidad_img)))
        total = sum(i.width for i in imgs) + espacio * (len(imgs) - 1)
        cx = x + (w - total) / 2 if alin == "c" else x
        for i in imgs:
            im.alpha_composite(i, dest=(int(cx), int(y)))
            cx += i.width + espacio

    pegar("tiempo/0.png", pos["tiempo_ico"])
    numero("23", "num", pos["temp"][0], pos["temp"][1], 0, alin="l", unidad_img="num/grado.png")
    numero("68", "num", pos["pulso"][0], pos["pulso"][1], 0, alin="l")
    hx, hy = pos["horas"]
    mx, my = pos["minutos"]
    a = Image.open(os.path.join(IMG, "hora/1.png")).width
    for i, c in enumerate("10"):
        pegar(f"hora/{c}.png", (hx + i * (a + ESPACIO_HORA), hy))
    for i, c in enumerate("09"):
        pegar(f"minuto/{c}.png", (mx + i * (a + ESPACIO_HORA), my))
    x, y, w = pos["val_bat"]
    numero("92", "num", x, y, w)
    x, y, w = pos["val_bio"]
    numero("74", "num", x, y, w)
    pegar("semana/2.png", pos["semana"])
    numero("28", "num", pos["dia"][0], pos["dia"][1], 0, alin="l")
    numero("8642", "num", pos["pasos"][0], pos["pasos"][1], 0, alin="l")
    numero("5.31", "num", pos["dist"][0], pos["dist"][1], 0, alin="l")
    pegar(f"barra_bateria/{round(SEGMENTOS * .92)}.png", cajas["bateria"][:2])
    pegar(f"barra_bio/{round(SEGMENTOS * .74)}.png", cajas["bio"][:2])
    marco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    m = Image.new("L", (W * S, H * S), 0)
    ImageDraw.Draw(m).ellipse([0, 0, W * S, H * S], fill=255)
    marco.paste(im, (0, 0), m.resize((W, H), Image.LANCZOS))
    return marco


# ---------------------------------------------------------------- principal
ESPACIO_HORA = 4    # entre los dos dígitos
HUECO_HORA = 14     # entre horas y minutos
POS = {
    "tiempo_ico": (148, 40),
    "temp": (192, 45),
    "corazon": (282, 48),
    "pulso": (310, 45),
    "horas": None,      # se calcula según el ancho de los dígitos
    "minutos": None,
    "val_bat": (34, 140, 66),
    "et_bat": (67, 180),
    "val_bio": (380, 140, 66),
    "et_bio": (413, 180),
    "semana": (79, 222),
    "dia": (370, 225),
    "mapa": (92, 270, 258, 404),
    "boton": (100, 315, 252, 361),
    "et_pasos": (284, 287),
    "pasos": (284, 300),
    "et_dist": (284, 344),
    "dist": (284, 357),
    "ico_bat": (55, 120),
    "ico_bio": (403, 115),
    "logo": (240, 431),
    "bt": (230, 12),
}


def main():
    os.makedirs(IMG, exist_ok=True)
    # dígitos grandes de la hora (misma caja para horas y minutos)
    ancho_h, alto_h = juego_digitos("hora", 138, BLANCO, peso=600)
    juego_digitos("minuto", 138, LIMA, peso=600, ancho=ancho_h, alto=alto_h)
    juego_digitos("aod_hora", 138, (150, 150, 150, 255), peso=500, ancho=ancho_h, alto=alto_h)
    juego_digitos("aod_minuto", 138, LIMA_OSC, peso=500, ancho=ancho_h, alto=alto_h)
    bloque = 2 * ancho_h + ESPACIO_HORA
    POS["horas"] = (CX - HUECO_HORA // 2 - bloque, 98)
    POS["minutos"] = (CX + HUECO_HORA // 2, 98)
    # números pequeños (valores)
    _, alto_n = juego_digitos("num", 34, BLANCO, peso=600,
                              extras={"punto": ".", "menos": "-"})
    unidad("num/grado.png", "°", 34, BLANCO, alto_n)
    unidad("num/porcentaje.png", "%", 22, GRIS, alto_n)
    unidad("num/sin_datos.png", "--", 34, GRIS, alto_n)

    for i, tipo in enumerate(TIEMPO):
        guardar(icono_tiempo(tipo), f"tiempo/{i}.png")

    cajas = barras()
    ancho_sem = semana()

    # pulsación de botones
    p = lienzo()
    boton(ImageDraw.Draw(p), (0, 0, POS["boton"][2] - POS["boton"][0], POS["boton"][3] - POS["boton"][1]),
          pulsado=True)
    guardar(reducir(p).crop((0, 0, POS["boton"][2] - POS["boton"][0], POS["boton"][3] - POS["boton"][1])),
            "boton_pulsado.png")
    guardar(Image.new("RGBA", (120, 40), (198, 245, 58, 60)), "logo_pulsado.png")
    guardar(icono_bt_off(20, GRIS), "bt_off.png")
    guardar(Image.new("RGBA", (4, 4), (0, 0, 0, 0)), "vacio.png")

    # logo para modo siempre activo
    aod = lienzo(80, 40)
    bici(ImageDraw.Draw(aod), 40 * S, 20 * S, 11 * S, LIMA_OSC, int(3 * S))
    guardar(reducir(aod), "aod_logo.png")

    fondo_im = fondo(cajas, POS)
    guardar(fondo_im, "fondo.png")

    prev = vista_previa(fondo_im, POS, cajas, (ancho_h, alto_h), ancho_sem)
    prev.save(os.path.join(RAIZ, "preview.png"))
    icono = prev.resize((248, 248), Image.LANCZOS)
    icono.save(os.path.join(SALIDA, "icon.png"))

    # datos de geometría que necesita el JS
    import json
    meta = {
        "digito_hora": [ancho_h, alto_h],
        "espacio_hora": ESPACIO_HORA,
        "alto_num": alto_n,
        "barra_bateria": cajas["bateria"],
        "barra_bio": cajas["bio"],
        "segmentos": SEGMENTOS,
        "ancho_semana": ancho_sem,
        "pos": POS,
    }
    ruta_js = os.path.join(RAIZ, "watchface", "disposicion.js")
    with open(ruta_js, "w", encoding="utf8") as fjs:
        fjs.write("// Generado por herramientas/generar_assets.py: no editar a mano.\n")
        fjs.write("export const D = " + json.dumps(meta, indent=2) + "\n")
    print(json.dumps(meta))


if __name__ == "__main__":
    main()

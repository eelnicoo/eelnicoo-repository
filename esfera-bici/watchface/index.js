import { D } from './disposicion'

const P = D.pos
const lista = (carpeta, n = 10) => Array.from({ length: n }, (_, i) => `images/${carpeta}/${i}.png`)

const NUM = lista('num')
const SIN_DATOS = 'images/num/sin_datos.png'
const PUNTO = 'images/num/punto.png'
const MENOS = 'images/num/menos.png'

function numero(x, y, w, type, align, extra = {}) {
  return hmUI.createWidget(hmUI.widget.TEXT_IMG, {
    x,
    y,
    w,
    h: D.alto_num,
    font_array: NUM,
    type,
    h_space: 1,
    align_h: align,
    invalid_image: SIN_DATOS,
    show_level: hmUI.show_level.ONLY_NORMAL,
    ...extra
  })
}

function zonaPulsable(x, y, w, h, type, src = 'images/vacio.png') {
  if (type === undefined) return
  hmUI.createWidget(hmUI.widget.IMG_CLICK, { x, y, w, h, src, type, show_level: hmUI.show_level.ONLY_NORMAL })
}

function hora(digitosHora, digitosMinuto, nivel) {
  const [ancho] = D.digito_hora
  hmUI.createWidget(hmUI.widget.IMG_TIME, {
    hour_zero: 1,
    hour_startX: P.horas[0],
    hour_startY: P.horas[1],
    hour_array: digitosHora,
    hour_space: D.espacio_hora,
    hour_align: hmUI.align.LEFT,
    minute_zero: 1,
    minute_startX: P.minutos[0],
    minute_startY: P.minutos[1],
    minute_array: digitosMinuto,
    minute_space: D.espacio_hora,
    minute_align: hmUI.align.LEFT,
    minute_follow: 0,
    show_level: nivel
  })
  return ancho
}

WatchFace({
  build() {
    const NORMAL = hmUI.show_level.ONLY_NORMAL
    const AOD = hmUI.show_level.ONAL_AOD
    const T = hmUI.data_type
    // BioCharge (HybridCharge): solo existe en firmwares recientes.
    const BIO = T.BIO_CHARGE

    hmUI.createWidget(hmUI.widget.IMG, { x: 0, y: 0, src: 'images/fondo.png', show_level: NORMAL })

    // --- barras laterales: batería (izquierda) y BioCharge (derecha)
    const niveles = D.segmentos + 1
    hmUI.createWidget(hmUI.widget.IMG_LEVEL, {
      x: D.barra_bateria[0],
      y: D.barra_bateria[1],
      image_array: lista('barra_bateria', niveles),
      image_length: niveles,
      type: T.BATTERY,
      show_level: NORMAL
    })
    if (BIO !== undefined) {
      hmUI.createWidget(hmUI.widget.IMG_LEVEL, {
        x: D.barra_bio[0],
        y: D.barra_bio[1],
        image_array: lista('barra_bio', niveles),
        image_length: niveles,
        type: BIO,
        show_level: NORMAL
      })
    }

    // --- fila superior: tiempo, temperatura y pulso
    hmUI.createWidget(hmUI.widget.IMG_LEVEL, {
      x: P.tiempo_ico[0],
      y: P.tiempo_ico[1],
      image_array: lista('tiempo', 29),
      image_length: 29,
      type: T.WEATHER_CURRENT,
      show_level: NORMAL
    })
    numero(P.temp[0], P.temp[1], 80, T.WEATHER_CURRENT, hmUI.align.LEFT, {
      unit_sc: 'images/num/grado.png',
      unit_tc: 'images/num/grado.png',
      unit_en: 'images/num/grado.png',
      imperial_unit_sc: 'images/num/grado.png',
      imperial_unit_tc: 'images/num/grado.png',
      imperial_unit_en: 'images/num/grado.png',
      negative_image: MENOS
    })
    numero(P.pulso[0], P.pulso[1], 70, T.HEART, hmUI.align.LEFT)

    // --- hora
    hora(lista('hora'), lista('minuto'), NORMAL)

    // --- valores junto a la hora
    numero(P.val_bat[0], P.val_bat[1], P.val_bat[2], T.BATTERY, hmUI.align.CENTER_H)
    if (BIO !== undefined) {
      numero(P.val_bio[0], P.val_bio[1], P.val_bio[2], BIO, hmUI.align.CENTER_H)
    } else {
      hmUI.createWidget(hmUI.widget.IMG, {
        x: P.val_bio[0] + 20,
        y: P.val_bio[1],
        src: SIN_DATOS,
        show_level: NORMAL
      })
    }

    // --- semana en español (LU..DO) y día del mes
    hmUI.createWidget(hmUI.widget.IMG_WEEK, {
      x: P.semana[0],
      y: P.semana[1],
      week_en: lista('semana', 7),
      week_tc: lista('semana', 7),
      week_sc: lista('semana', 7),
      show_level: NORMAL
    })
    hmUI.createWidget(hmUI.widget.IMG_DATE, {
      day_startX: P.dia[0],
      day_startY: P.dia[1],
      day_zero: 1,
      day_space: 1,
      day_align: hmUI.align.LEFT,
      day_follow: 0,
      day_en_array: NUM,
      day_sc_array: NUM,
      day_tc_array: NUM,
      show_level: NORMAL | AOD
    })

    // --- actividad
    numero(P.pasos[0], P.pasos[1], 110, T.STEP, hmUI.align.LEFT)
    numero(P.dist[0], P.dist[1], 110, T.DISTANCE, hmUI.align.LEFT, { dot_image: PUNTO })

    // --- accesos directos
    const [bx0, by0, bx1, by1] = P.boton
    zonaPulsable(bx0, by0, bx1 - bx0, by1 - by0, T.OUTDOOR_CYCLING, 'images/boton_pulsado.png')
    zonaPulsable(P.logo[0] - 60, P.logo[1] - 20, 120, 40, T.OUTDOOR_CYCLING, 'images/logo_pulsado.png')
    zonaPulsable(P.tiempo_ico[0] - 6, P.tiempo_ico[1] - 6, 110, 50, T.WEATHER_CURRENT)
    zonaPulsable(P.corazon[0] - 6, P.corazon[1] - 8, 100, 46, T.HEART)
    zonaPulsable(P.pasos[0] - 4, P.et_pasos[1] - 10, 120, 50, T.STEP)
    zonaPulsable(P.val_bio[0], P.ico_bio[1] - 4, P.val_bio[2], 80, BIO)

    // --- aviso de Bluetooth desconectado
    hmUI.createWidget(hmUI.widget.IMG_STATUS, {
      x: P.bt[0],
      y: P.bt[1],
      type: hmUI.system_status.DISCONNECT,
      src: 'images/bt_off.png',
      show_level: NORMAL
    })

    // --- pantalla siempre activa (AOD): hora atenuada y logo
    hora(lista('aod_hora'), lista('aod_minuto'), AOD)
    hmUI.createWidget(hmUI.widget.IMG, {
      x: P.logo[0] - 40,
      y: P.logo[1] - 20,
      src: 'images/aod_logo.png',
      show_level: AOD
    })
  }
})

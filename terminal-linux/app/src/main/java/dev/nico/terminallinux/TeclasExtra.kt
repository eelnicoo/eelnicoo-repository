package dev.nico.terminallinux

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Dos filas de teclas que faltan en el teclado de la pantalla: ESC, TAB, CTRL, ALT, flechas…
 * CTRL y ALT se quedan pulsadas hasta la siguiente tecla (también las del teclado normal).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class TeclasExtra(context: Context, private val alPulsar: (Tecla) -> Unit) : LinearLayout(context) {

    sealed class Tecla {
        data class Codigo(val keyCode: Int) : Tecla()
        data class Texto(val texto: String) : Tecla()
        object Ctrl : Tecla()
        object Alt : Tecla()
        object Teclado : Tecla()
        object Pegar : Tecla()
    }

    private class Def(val etiqueta: String, val tecla: Tecla, val repite: Boolean = false)

    private val filas = listOf(
        listOf(
            Def("ESC", Tecla.Codigo(KeyEvent.KEYCODE_ESCAPE)),
            Def("/", Tecla.Texto("/")),
            Def("-", Tecla.Texto("-")),
            Def("|", Tecla.Texto("|")),
            Def("HOME", Tecla.Codigo(KeyEvent.KEYCODE_MOVE_HOME)),
            Def("↑", Tecla.Codigo(KeyEvent.KEYCODE_DPAD_UP), repite = true),
            Def("END", Tecla.Codigo(KeyEvent.KEYCODE_MOVE_END)),
            Def("PGUP", Tecla.Codigo(KeyEvent.KEYCODE_PAGE_UP), repite = true),
            Def("⌨", Tecla.Teclado),
        ),
        listOf(
            Def("TAB", Tecla.Codigo(KeyEvent.KEYCODE_TAB)),
            Def("CTRL", Tecla.Ctrl),
            Def("ALT", Tecla.Alt),
            Def("~", Tecla.Texto("~")),
            Def("←", Tecla.Codigo(KeyEvent.KEYCODE_DPAD_LEFT), repite = true),
            Def("↓", Tecla.Codigo(KeyEvent.KEYCODE_DPAD_DOWN), repite = true),
            Def("→", Tecla.Codigo(KeyEvent.KEYCODE_DPAD_RIGHT), repite = true),
            Def("PGDN", Tecla.Codigo(KeyEvent.KEYCODE_PAGE_DOWN), repite = true),
            Def("PEGAR", Tecla.Pegar),
        ),
    )

    var ctrl = false
        private set
    var alt = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var repeticion: Runnable? = null
    private lateinit var botonCtrl: TextView
    private lateinit var botonAlt: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#FF101010"))
        val alto = dp(40)
        for (fila in filas) {
            val linea = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (def in fila) {
                val b = boton(def)
                linea.addView(b, LayoutParams(0, alto, 1f))
                if (def.tecla == Tecla.Ctrl) botonCtrl = b
                if (def.tecla == Tecla.Alt) botonAlt = b
            }
            addView(linea, LayoutParams(LayoutParams.MATCH_PARENT, alto))
        }
        refrescar()
    }

    /** Lee CTRL y la suelta (si estaba pulsada). */
    fun consumirCtrl(): Boolean = ctrl.also { if (it) { ctrl = false; refrescar() } }

    /** Lee ALT y la suelta (si estaba pulsada). */
    fun consumirAlt(): Boolean = alt.also { if (it) { alt = false; refrescar() } }

    private fun boton(def: Def): TextView = TextView(context).apply {
        text = def.etiqueta
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (def.etiqueta.length > 2) 12f else 16f)
        typeface = Typeface.MONOSPACE
        isClickable = true
        contentDescription = def.etiqueta
        setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.alpha = 0.55f
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    pulsar(def)
                    if (def.repite) {
                        val r = object : Runnable {
                            override fun run() {
                                alPulsar(def.tecla)
                                handler.postDelayed(this, 50)
                            }
                        }
                        repeticion = r
                        handler.postDelayed(r, 400)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.alpha = 1f
                    repeticion?.let { handler.removeCallbacks(it) }
                    repeticion = null
                    if (ev.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
        background = fondo(false)
    }

    private fun pulsar(def: Def) {
        when (def.tecla) {
            Tecla.Ctrl -> { ctrl = !ctrl; refrescar() }
            Tecla.Alt -> { alt = !alt; refrescar() }
            else -> alPulsar(def.tecla)
        }
    }

    private fun refrescar() {
        if (!::botonCtrl.isInitialized) return
        botonCtrl.background = fondo(ctrl)
        botonAlt.background = fondo(alt)
        botonCtrl.setTextColor(if (ctrl) Color.BLACK else Color.WHITE)
        botonAlt.setTextColor(if (alt) Color.BLACK else Color.WHITE)
    }

    private fun fondo(activo: Boolean) = GradientDrawable().apply {
        setColor(if (activo) Color.parseColor("#FF4FC3F7") else Color.TRANSPARENT)
        cornerRadius = dp(6).toFloat()
        setStroke(1, Color.parseColor("#22FFFFFF"))
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}

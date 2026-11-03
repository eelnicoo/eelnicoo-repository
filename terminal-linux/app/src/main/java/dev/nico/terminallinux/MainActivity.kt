package dev.nico.terminallinux

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.ContextMenu
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class MainActivity : Activity(), TerminalService.Oyente {

    private lateinit var linux: Linux
    private var servicio: TerminalService? = null
    private var actual: TerminalSession? = null

    // Pantallas
    private lateinit var raiz: LinearLayout
    private lateinit var pantallaInstalar: ScrollView
    private lateinit var pantallaTerminal: LinearLayout

    // Instalación
    private lateinit var campoUsuario: EditText
    private lateinit var botonAlmacenamiento: Button
    private lateinit var botonInstalar: Button
    private lateinit var barra: ProgressBar
    private lateinit var estado: TextView

    // Terminal
    private lateinit var pestanas: LinearLayout
    private lateinit var terminal: TerminalView
    private lateinit var teclas: TeclasExtra

    private val prefs by lazy { getSharedPreferences("ajustes", Context.MODE_PRIVATE) }
    private var tamLetra = 0

    private val conexion = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as TerminalService.Enlace).servicio
            servicio = s
            s.oyente = this@MainActivity
            s.actividadVisible = estaVisible
            mostrarPantalla()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            servicio = null
        }
    }
    private var estaVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        linux = Linux(this)
        construirUi()
        TerminalService.iniciar(this)
        bindService(Intent(this, TerminalService::class.java), conexion, Context.BIND_AUTO_CREATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISO_NOTIF)
        }
    }

    override fun onResume() {
        super.onResume()
        estaVisible = true
        servicio?.actividadVisible = true
        actualizarBotonAlmacenamiento()
        if (pantallaTerminal.visibility == View.VISIBLE) {
            terminal.onScreenUpdated()
            terminal.requestFocus()
        }
    }

    override fun onPause() {
        estaVisible = false
        servicio?.actividadVisible = false
        super.onPause()
    }

    override fun onDestroy() {
        servicio?.let { s ->
            s.oyente = null
            s.actividadVisible = false
            if (s.sesiones.isEmpty() && !s.instalando) s.stopSelf()
        }
        try {
            unbindService(conexion)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    // ================================================================ UI

    private fun construirUi() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        pantallaInstalar = construirInstalar()
        pantallaTerminal = construirTerminal()
        raiz.addView(pantallaInstalar, LinearLayout.LayoutParams(MATCH, MATCH))
        raiz.addView(pantallaTerminal, LinearLayout.LayoutParams(MATCH, MATCH))
        pantallaInstalar.visibility = View.GONE
        pantallaTerminal.visibility = View.GONE
        setContentView(raiz)
    }

    private fun construirInstalar(): ScrollView {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }
        col.addView(texto("Terminal Linux", 30f, negrita = true))
        col.addView(
            texto(
                "Instala Ubuntu 24.04 LTS dentro de esta app. Tendrás una terminal de Linux de verdad " +
                    "con sudo y apt (sudo apt update, sudo apt install…), donde puedes instalar Claude Code " +
                    "y dejar que cree cosas en la tablet.\n\n" +
                    "No hace falta rootear la tablet: Ubuntu funciona dentro de proot y, dentro de Ubuntu, " +
                    "tu usuario es administrador con sudo sin contraseña.\n\n" +
                    "Se descargan unos 30 MB y, al configurarse, ocupa unos 500 MB.",
                15f,
            ).apply { setPadding(0, dp(12), 0, dp(20)) },
        )
        col.addView(texto("Tu nombre de usuario en Linux", 14f, color = GRIS))
        campoUsuario = EditText(this).apply {
            setText(linux.username)
            setTextColor(Color.WHITE)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            typeface = Typeface.MONOSPACE
        }
        col.addView(campoUsuario, LinearLayout.LayoutParams(MATCH, WRAP))

        botonAlmacenamiento = boton("Dar acceso a los archivos de la tablet") { pedirAlmacenamiento() }
        col.addView(botonAlmacenamiento, margen(top = 16))
        col.addView(
            texto(
                "Opcional: así Ubuntu (y Claude Code) pueden leer y guardar en la memoria de la tablet, en ~/tablet.",
                13f, color = GRIS,
            ),
        )

        botonInstalar = boton("Instalar Ubuntu") { empezarInstalacion() }
        col.addView(botonInstalar, margen(top = 24))

        barra = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            visibility = View.GONE
        }
        col.addView(barra, margen(top = 16))
        estado = texto("", 14f, color = GRIS)
        col.addView(estado, margen(top = 8))

        return ScrollView(this).apply { addView(col) }
    }

    private fun construirTerminal(): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Barra de pestañas
        val barraSup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#FF101010"))
            gravity = Gravity.CENTER_VERTICAL
        }
        pestanas = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(pestanas)
        }
        barraSup.addView(scroll, LinearLayout.LayoutParams(0, dp(40), 1f))
        val mas = botonIcono("+", "Nueva pestaña") { nuevaSesion(TerminalService.Tipo.USUARIO) }
        mas.setOnLongClickListener { menuNueva(it); true }
        barraSup.addView(mas, LinearLayout.LayoutParams(dp(48), dp(40)))
        barraSup.addView(botonIcono("⋮", "Menú") { menu(it) }, LinearLayout.LayoutParams(dp(48), dp(40)))
        col.addView(barraSup, LinearLayout.LayoutParams(MATCH, dp(40)))

        // Terminal
        val densidad = resources.displayMetrics.density
        tamLetra = prefs.getInt("letra", (13 * densidad).toInt())
        terminal = TerminalView(this, null).apply {
            setTerminalViewClient(clienteVista)
            setTextSize(tamLetra)
            setTypeface(Typeface.MONOSPACE)
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.BLACK)
        }
        registerForContextMenu(terminal)
        col.addView(terminal, LinearLayout.LayoutParams(MATCH, 0, 1f))

        teclas = TeclasExtra(this) { alPulsarTecla(it) }
        col.addView(teclas, LinearLayout.LayoutParams(MATCH, WRAP))
        return col
    }

    private fun mostrarPantalla() {
        val s = servicio ?: return
        if (s.instalando || !linux.isInstalled) {
            pantallaTerminal.visibility = View.GONE
            pantallaInstalar.visibility = View.VISIBLE
            when {
                !linux.hasProot -> mostrarError("Esta compilación no incluye proot para tu tablet (${Build.SUPPORTED_ABIS.joinToString()}).")
                linux.ubuntuArch == null -> mostrarError("Ubuntu no está disponible para el procesador de esta tablet (${Build.SUPPORTED_ABIS.joinToString()}).")
                s.instalando -> onInstalacion(s.progreso, false, null)
                s.errorInstalacion != null -> onInstalacion(null, true, s.errorInstalacion)
            }
            return
        }
        pantallaInstalar.visibility = View.GONE
        pantallaTerminal.visibility = View.VISIBLE
        if (s.sesiones.isEmpty()) {
            nuevaSesion(TerminalService.Tipo.USUARIO)
        } else {
            mostrarSesion(actual?.takeIf { it in s.sesiones } ?: s.sesiones.last())
        }
    }

    private fun mostrarError(msg: String) {
        botonInstalar.isEnabled = false
        estado.text = msg
        estado.setTextColor(ROJO)
    }

    // ================================================================ instalación

    private fun empezarInstalacion() {
        val usuario = campoUsuario.text.toString().trim().lowercase()
        if (!Regex("^[a-z_][a-z0-9_-]{0,30}$").matches(usuario) || usuario == "root") {
            campoUsuario.error = "Solo minúsculas, números, - y _ (empezando por letra)"
            return
        }
        linux.username = usuario
        ocultarTeclado(campoUsuario)
        servicio?.instalar()
    }

    override fun onInstalacion(p: Instalador.Progreso?, terminada: Boolean, error: String?) {
        if (terminada && error == null) {
            mostrarPantalla()
            return
        }
        val trabajando = !terminada
        botonInstalar.isEnabled = !trabajando
        campoUsuario.isEnabled = !trabajando
        botonInstalar.text = if (error != null) "Reintentar" else "Instalar Ubuntu"
        barra.visibility = if (trabajando) View.VISIBLE else View.GONE
        if (p != null) {
            barra.isIndeterminate = p.fraccion < 0
            if (p.fraccion >= 0) barra.progress = (p.fraccion * 1000).toInt()
        }
        if (error != null) {
            estado.text = "No se pudo instalar: $error"
            estado.setTextColor(ROJO)
        } else {
            estado.text = p?.texto ?: ""
            estado.setTextColor(GRIS)
        }
    }

    // ================================================================ sesiones

    private fun nuevaSesion(tipo: TerminalService.Tipo) {
        val s = servicio ?: return
        val sesion = try {
            s.nuevaSesion(tipo)
        } catch (e: Exception) {
            Log.e("TerminalLinux", "No se pudo abrir la sesión", e)
            Toast.makeText(this, "No se pudo abrir la terminal: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        mostrarSesion(sesion)
    }

    private fun mostrarSesion(sesion: TerminalSession) {
        actual = sesion
        terminal.attachSession(sesion)
        terminal.onScreenUpdated()
        terminal.requestFocus()
        dibujarPestanas()
    }

    private fun dibujarPestanas() {
        val s = servicio ?: return
        pestanas.removeAllViews()
        s.sesiones.forEachIndexed { i, sesion ->
            val titulo = sesion.title?.takeIf { it.isNotBlank() } ?: sesion.mSessionName ?: "terminal"
            val activa = sesion == actual
            val t = TextView(this).apply {
                text = "${i + 1} · ${titulo.take(28)}"
                setTextColor(if (activa) Color.WHITE else GRIS)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = if (activa) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                background = GradientDrawable().apply {
                    setColor(if (activa) Color.parseColor("#FF262626") else Color.TRANSPARENT)
                    cornerRadius = dp(8).toFloat()
                }
                setOnClickListener { mostrarSesion(sesion) }
                setOnLongClickListener { confirmarCerrar(sesion); true }
            }
            pestanas.addView(t, LinearLayout.LayoutParams(WRAP, dp(32)).apply { setMargins(dp(4), dp(4), 0, dp(4)) })
        }
    }

    private fun confirmarCerrar(sesion: TerminalSession) {
        AlertDialog.Builder(this)
            .setTitle("¿Cerrar esta pestaña?")
            .setMessage("Se terminarán los programas que estén funcionando en ella.")
            .setPositiveButton("Cerrar") { _, _ -> cerrar(sesion) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun cerrar(sesion: TerminalSession) {
        val s = servicio ?: return
        val i = s.sesiones.indexOf(sesion)
        val eraActual = sesion == actual
        s.cerrarSesion(sesion)
        if (s.sesiones.isEmpty()) {
            actual = null
            s.stopSelf()
            finish()
        } else if (eraActual) {
            mostrarSesion(s.sesiones[(i - 1).coerceIn(0, s.sesiones.lastIndex)])
        } else {
            dibujarPestanas()
        }
    }

    override fun onSesiones() {
        dibujarPestanas()
    }

    override fun onTexto(session: TerminalSession) {
        if (session == actual) terminal.onScreenUpdated()
    }

    override fun onTitulo(session: TerminalSession) {
        dibujarPestanas()
    }

    override fun onTerminada(session: TerminalSession) {
        // Al salir con «exit» la pestaña se cierra sola; si terminó con error se queda
        // a la vista hasta pulsar Intro.
        if (session.exitStatus == 0) cerrar(session) else if (session == actual) terminal.onScreenUpdated()
    }

    override fun onColores(session: TerminalSession) {
        if (session == actual) terminal.invalidate()
    }

    // ================================================================ teclado

    private fun alPulsarTecla(t: TeclasExtra.Tecla) {
        when (t) {
            is TeclasExtra.Tecla.Codigo -> {
                var meta = 0
                if (teclas.consumirCtrl()) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
                if (teclas.consumirAlt()) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
                val ahora = System.currentTimeMillis()
                terminal.onKeyDown(t.keyCode, KeyEvent(ahora, ahora, KeyEvent.ACTION_DOWN, t.keyCode, 0, meta))
            }
            is TeclasExtra.Tecla.Texto -> {
                val ctrl = teclas.consumirCtrl()
                val alt = teclas.consumirAlt()
                t.texto.codePoints().forEach { terminal.inputCodePoint(it, ctrl, alt) }
            }
            TeclasExtra.Tecla.Teclado -> alternarTeclado()
            TeclasExtra.Tecla.Pegar -> servicio?.pegar(actual)
            else -> {}
        }
    }

    private fun alternarTeclado() {
        val imm = getSystemService(InputMethodManager::class.java)
        val visible = Build.VERSION.SDK_INT >= 30 &&
            window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        if (visible) {
            imm.hideSoftInputFromWindow(terminal.windowToken, 0)
        } else {
            mostrarTeclado()
        }
    }

    private fun mostrarTeclado() {
        terminal.requestFocus()
        getSystemService(InputMethodManager::class.java).showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun ocultarTeclado(v: View) {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(v.windowToken, 0)
    }

    private val clienteVista = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) {
                cambiarLetra(scale > 1f)
                return 1f
            }
            return scale
        }

        override fun onSingleTapUp(e: MotionEvent?) {
            mostrarTeclado()
        }

        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput() = false
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean {
            val s = session ?: return false
            if (!s.isRunning) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) cerrar(s)
                return true
            }
            // Atajos con teclado físico: Ctrl+Mayús+V pega, Ctrl+Mayús+T abre pestaña.
            if (e.isCtrlPressed && e.isShiftPressed) {
                when (keyCode) {
                    KeyEvent.KEYCODE_V -> { servicio?.pegar(s); return true }
                    KeyEvent.KEYCODE_T -> { nuevaSesion(TerminalService.Tipo.USUARIO); return true }
                    KeyEvent.KEYCODE_W -> { confirmarCerrar(s); return true }
                }
            }
            return false
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
        override fun onLongPress(event: MotionEvent?) = false
        override fun readControlKey() = teclas.consumirCtrl()
        override fun readAltKey() = teclas.consumirAlt()
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?) = false
        override fun onEmulatorSet() {}
        override fun logError(tag: String?, message: String?) { Log.e(tag ?: "TerminalView", message ?: "") }
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: "TerminalView", message, e) }
        override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: "TerminalView", "", e) }
    }

    private fun cambiarLetra(mas: Boolean) {
        val d = resources.displayMetrics.density
        tamLetra = (tamLetra + if (mas) d.toInt().coerceAtLeast(1) else -d.toInt().coerceAtLeast(1))
            .coerceIn((6 * d).toInt(), (40 * d).toInt())
        prefs.edit().putInt("letra", tamLetra).apply()
        terminal.setTextSize(tamLetra)
    }

    // ================================================================ menús

    private fun menuNueva(ancla: View) {
        PopupMenu(this, ancla).apply {
            menu.add("Nueva pestaña (${linux.username})").setOnMenuItemClickListener { nuevaSesion(TerminalService.Tipo.USUARIO); true }
            menu.add("Nueva pestaña como root").setOnMenuItemClickListener { nuevaSesion(TerminalService.Tipo.ROOT); true }
            menu.add("Shell de Android (emergencia)").setOnMenuItemClickListener { nuevaSesion(TerminalService.Tipo.ANDROID); true }
            show()
        }
    }

    private fun menu(ancla: View) {
        val s = servicio
        PopupMenu(this, ancla).apply {
            menu.add("Nueva pestaña").setOnMenuItemClickListener { nuevaSesion(TerminalService.Tipo.USUARIO); true }
            menu.add("Nueva pestaña como root").setOnMenuItemClickListener { nuevaSesion(TerminalService.Tipo.ROOT); true }
            menu.add("Shell de Android (emergencia)").setOnMenuItemClickListener { nuevaSesion(TerminalService.Tipo.ANDROID); true }
            menu.add("Letra más grande").setOnMenuItemClickListener { cambiarLetra(true); true }
            menu.add("Letra más pequeña").setOnMenuItemClickListener { cambiarLetra(false); true }
            menu.add("Mantener activa con la pantalla apagada").apply {
                isCheckable = true
                isChecked = s?.despierto == true
                setOnMenuItemClickListener { alternarDespierto(); true }
            }
            if (!linux.hasStorageAccess) {
                menu.add("Dar acceso a los archivos de la tablet").setOnMenuItemClickListener { pedirAlmacenamiento(); true }
            }
            menu.add("Ayuda").setOnMenuItemClickListener { ayuda(); true }
            menu.add("Reinstalar Ubuntu…").setOnMenuItemClickListener { confirmarReinstalar(); true }
            menu.add("Salir").setOnMenuItemClickListener {
                servicio?.salir()
                finish()
                true
            }
            show()
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Pegar")
        menu.add(Menu.NONE, 2, Menu.NONE, "Copiar todo el texto")
        menu.add(Menu.NONE, 3, Menu.NONE, "Compartir el texto")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val s = actual ?: return true
        val todo = s.emulator?.screen?.transcriptText?.trim() ?: ""
        when (item.itemId) {
            1 -> servicio?.pegar(s)
            2 -> s.onCopyTextToClipboard(todo)
            3 -> startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, todo), "Compartir"),
            )
        }
        return true
    }

    private fun alternarDespierto() {
        val s = servicio ?: return
        s.alternarDespierto()
        if (s.despierto) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    @Suppress("BatteryLife")
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun confirmarReinstalar() {
        AlertDialog.Builder(this)
            .setTitle("¿Reinstalar Ubuntu?")
            .setMessage(
                "Se borra Ubuntu entero, con todo lo que hayas instalado y los archivos de tu carpeta personal " +
                    "(${"/home/" + linux.username}). Los archivos de la tablet (~/tablet) no se tocan.",
            )
            .setPositiveButton("Borrar y reinstalar") { _, _ ->
                val s = servicio ?: return@setPositiveButton
                actual = null
                pantallaTerminal.visibility = View.GONE
                pantallaInstalar.visibility = View.VISIBLE
                botonInstalar.isEnabled = false
                estado.setTextColor(GRIS)
                estado.text = "Borrando Ubuntu…"
                s.desinstalar {
                    botonInstalar.isEnabled = true
                    botonInstalar.text = "Instalar Ubuntu"
                    estado.text = ""
                    mostrarPantalla()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ayuda() {
        val v = texto(
            "• Dentro de Ubuntu eres «${linux.username}» y tienes sudo sin contraseña: " +
                "sudo apt update, sudo apt install <paquete>…\n\n" +
                "• Claude Code: escribe instalar-claude y después claude. La primera vez se abrirá " +
                "el navegador para iniciar sesión.\n\n" +
                "• Archivos de la tablet: ~/tablet (o /sdcard). Si no aparecen, da permiso desde el menú.\n\n" +
                "• abrir <archivo o URL> lo abre con las apps de Android (navegador, visor de PDF…).\n\n" +
                "• Teclas: CTRL y ALT se quedan pulsadas para la siguiente tecla. Con teclado físico, " +
                "Ctrl+Mayús+V pega, Ctrl+Mayús+T abre pestaña. Pellizca para cambiar la letra. " +
                "Mantén pulsado para seleccionar y copiar.\n\n" +
                "• Pestañas: + abre otra (mantén pulsado para root o la shell de Android), " +
                "mantén pulsada una pestaña para cerrarla.\n\n" +
                "• Si Android cierra la terminal en segundo plano: activa «Mantener activa con la pantalla " +
                "apagada» y, en Android 14 o superior, Opciones de desarrollador → «Desactivar restricciones " +
                "de procesos secundarios».\n\n" +
                "• Ubuntu funciona dentro de proot, sin root real en la tablet: no hay systemd, Docker ni " +
                "módulos del kernel. Casi todo lo demás (apt, git, Python, Node.js, compiladores…) sí funciona.",
            15f,
        ).apply { setPadding(dp(24), dp(16), dp(24), dp(8)) }
        AlertDialog.Builder(this)
            .setTitle("Ayuda")
            .setView(ScrollView(this).apply { addView(v) })
            .setPositiveButton("Entendido", null)
            .show()
    }

    // ================================================================ almacenamiento

    private fun pedirAlmacenamiento() {
        if (linux.hasStorageAccess) return
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")),
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISO_ALMACEN,
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        actualizarBotonAlmacenamiento()
        if (requestCode == PERMISO_ALMACEN && linux.hasStorageAccess) avisoAlmacenamiento()
    }

    private var teniaAlmacenamiento: Boolean? = null

    private fun actualizarBotonAlmacenamiento() {
        val tiene = linux.hasStorageAccess
        botonAlmacenamiento.isEnabled = !tiene
        botonAlmacenamiento.text = if (tiene) "✓ Acceso a los archivos de la tablet" else "Dar acceso a los archivos de la tablet"
        if (teniaAlmacenamiento == false && tiene && pantallaTerminal.visibility == View.VISIBLE) avisoAlmacenamiento()
        teniaAlmacenamiento = tiene
    }

    private fun avisoAlmacenamiento() {
        if (servicio?.sesiones?.isNotEmpty() == true) {
            Toast.makeText(this, "Listo. Las pestañas nuevas ya ven ~/tablet", Toast.LENGTH_LONG).show()
        }
    }

    // ================================================================ utilidades

    private fun texto(t: String, sp: Float, negrita: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
        text = t
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (negrita) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
    }

    private fun boton(t: String, alPulsar: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { alPulsar() }
    }

    private fun botonIcono(t: String, descripcion: String, alPulsar: (View) -> Unit) = TextView(this).apply {
        text = t
        contentDescription = descripcion
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        gravity = Gravity.CENTER
        isClickable = true
        setOnClickListener { alPulsar(it) }
    }

    private fun margen(top: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val PERMISO_NOTIF = 1
        private const val PERMISO_ALMACEN = 2
        private val GRIS = Color.parseColor("#FFAAAAAA")
        private val ROJO = Color.parseColor("#FFFF6E6E")
    }
}

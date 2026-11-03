package dev.nico.terminallinux

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.TimeZone

/**
 * El Ubuntu de la tablet: dónde está, cómo se arranca con proot y los scripts que se
 * copian dentro. Todo vive en la carpeta privada de la app (files/linux).
 */
class Linux(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    val base = File(app.filesDir, "linux")
    val rootfs = File(base, "rootfs")
    private val tmp = File(base, "tmp")
    private val libDir = File(base, "lib")
    private val shm = File(base, "shm")
    private val sysdata = File(base, "sysdata")
    private val setupLock = File(base, "configurando")
    private val installedMarker = File(base, ".instalado")

    private val nativeDir = File(app.applicationInfo.nativeLibraryDir)
    private val proot = File(nativeDir, "libproot.so")
    private val loader = File(nativeDir, "libproot-loader.so")
    private val loader32 = File(nativeDir, "libproot-loader32.so")
    private val talloc = File(nativeDir, "libtalloc.so")

    val setupMarker get() = File(rootfs, "etc/terminal-linux/setup-ok")
    val openRequests get() = File(rootfs, OPEN_DIR)

    var username: String
        get() = prefs.getString("usuario", "nico") ?: "nico"
        set(v) = prefs.edit().putString("usuario", v).apply()

    /** Arquitectura de Ubuntu que corresponde a los binarios que Android ha instalado. */
    val ubuntuArch: String?
        get() = when (nativeDir.name) {
            "arm64" -> "arm64"
            "arm" -> "armhf"
            "x86_64" -> "amd64"
            else -> Build.SUPPORTED_ABIS.firstNotNullOfOrNull {
                when (it) {
                    "arm64-v8a" -> "arm64"
                    "armeabi-v7a" -> "armhf"
                    "x86_64" -> "amd64"
                    else -> null
                }
            }
        }

    val hasProot get() = proot.isFile && loader.isFile && talloc.isFile

    val isInstalled get() = installedMarker.isFile && File(rootfs, "usr/bin/bash").isFile

    fun markInstalled() {
        installedMarker.writeText("ok\n")
    }

    /** ¿Puede Ubuntu ver la memoria de la tablet (/sdcard)? */
    val hasStorageAccess: Boolean
        get() = if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            app.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** Borra Ubuntu entero sin seguir nunca enlaces (~/tablet apunta a la memoria de la tablet). */
    fun delete() {
        installedMarker.delete()
        deleteTree(rootfs)
        deleteTree(tmp)
        deleteTree(setupLock)
    }

    /** Quita el cerrojo de «configurando» si se quedó de una sesión que murió a medias. */
    fun clearStaleSetupLock() {
        deleteTree(setupLock)
    }

    /**
     * Deja todo listo para abrir una sesión: carpetas, enlace a talloc, archivos falsos de
     * /proc, scripts de ayuda dentro de Ubuntu y los scripts de arranque. Se hace cada vez
     * porque la ruta de las librerías nativas cambia con cada actualización de la app.
     */
    fun prepare() {
        for (d in listOf(tmp, libDir, shm, File(rootfs, ".l2s"))) d.mkdirs()
        val tallocLink = File(libDir, "libtalloc.so.2")
        tallocLink.delete()
        try {
            Os.symlink(talloc.absolutePath, tallocLink.absolutePath)
        } catch (_: ErrnoException) {
        }
        writeFakeSysdata()
        installGuestFiles()
        writeLaunchScripts()
    }

    // ---------------------------------------------------------------- arranque

    enum class Mode { USER, ROOT }

    fun scriptFor(mode: Mode) = File(base, if (mode == Mode.ROOT) "entrar-root.sh" else "entrar.sh")

    /** Variables con las que se lanza /system/bin/sh (y de ahí proot). */
    fun hostEnv(): Array<String> {
        val env = mutableListOf(
            "PATH=/system/bin:/system/xbin",
            "HOME=${base.absolutePath}",
            "TMPDIR=${tmp.absolutePath}",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "ANDROID_ROOT=" + (System.getenv("ANDROID_ROOT") ?: "/system"),
            "ANDROID_DATA=" + (System.getenv("ANDROID_DATA") ?: "/data"),
            "PROOT_TMP_DIR=${tmp.absolutePath}",
            "PROOT_LOADER=${loader.absolutePath}",
            "PROOT_L2S_DIR=${File(rootfs, ".l2s").absolutePath}",
            "LD_LIBRARY_PATH=${libDir.absolutePath}",
        )
        if (loader32.isFile) env += "PROOT_LOADER_32=${loader32.absolutePath}"
        return env.toTypedArray()
    }

    private fun prootArgs(changeId: String, cwd: String): List<String> {
        val a = mutableListOf(
            proot.absolutePath,
            "--kill-on-exit",
            "--link2symlink",
            "--sysvipc",
            "-L",
            "--kernel-release=$FAKE_KERNEL",
            "--change-id=$changeId",
            "--rootfs=${rootfs.absolutePath}",
            "--cwd=$cwd",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--bind=/dev/urandom:/dev/random",
            "--bind=${shm.absolutePath}:/dev/shm",
        )
        if (!File("/dev/fd").exists()) a += "--bind=/proc/self/fd:/dev/fd"
        listOf("stdin", "stdout", "stderr").forEachIndexed { i, name ->
            if (!File("/dev/$name").exists()) a += "--bind=/proc/self/fd/$i:/dev/$name"
        }
        // /proc que Android no deja leer a las apps: se sustituyen por archivos falsos.
        for ((name, real, _) in fakeEntries()) {
            if (!canRead(real)) a += "--bind=${File(sysdata, name).absolutePath}:$real"
        }
        a += "--bind=${File(sysdata, "sys_empty").absolutePath}:/sys/fs/selinux"
        // Memoria de la tablet (si la app tiene permiso).
        if (hasStorageAccess) {
            val sd = Environment.getExternalStorageDirectory().absolutePath
            if (File("/storage").canRead()) a += "--bind=/storage"
            a += "--bind=$sd:/sdcard"
        }
        return a
    }

    private fun guestLogin(user: String, home: String): List<String> = listOf(
        "/usr/bin/env", "-i",
        "HOME=$home",
        "USER=$user",
        "LOGNAME=$user",
        "SHELL=/bin/bash",
        "TERM=xterm-256color",
        "COLORTERM=truecolor",
        "LANG=C.UTF-8",
        "PATH=$GUEST_PATH",
        "TMPDIR=/tmp",
        "TZ=${TimeZone.getDefault().id}",
        "UV_USE_IO_URING=0",
        "BROWSER=abrir",
        "/bin/bash", "--login",
    )

    private fun writeLaunchScripts() {
        val user = username
        val root = prootArgs("0:0", "/root")
        val rootLogin = sh(root + guestLogin("root", "/root"))
        val userLogin = sh(prootArgs("$USER_UID:$USER_UID", "/home/$user") + guestLogin(user, "/home/$user"))
        val setup = sh(
            root + listOf(
                "/usr/bin/env", "-i", "HOME=/root", "TERM=xterm-256color", "PATH=$GUEST_PATH",
                "/bin/bash", "$GUEST_LIB/primer-arranque.sh", user, TimeZone.getDefault().id, USER_UID.toString(),
            ),
        )
        scriptFor(Mode.USER).writeText(
            userScript(user, setupMarker.absolutePath, setupLock.absolutePath, setup, rootLogin, userLogin),
        )
        scriptFor(Mode.ROOT).writeText(rootScript(rootLogin))
    }

    // ---------------------------------------------------------------- archivos dentro de Ubuntu

    private fun installGuestFiles() {
        if (!File(rootfs, "usr").isDirectory) return
        copyAsset("primer-arranque.sh", File(rootfs, "$GUEST_LIB/primer-arranque.sh"), 0x1ED)
        copyAsset("abrir", File(rootfs, "usr/local/bin/abrir"), 0x1ED)
        copyAsset("instalar-claude", File(rootfs, "usr/local/bin/instalar-claude"), 0x1ED)
        copyAsset("perfil.sh", File(rootfs, "etc/profile.d/terminal-linux.sh"), 0x1A4)
        val xdg = File(rootfs, "usr/local/bin/xdg-open")
        if (!isSymlink(xdg)) {
            xdg.delete()
            try {
                Os.symlink("abrir", xdg.absolutePath)
            } catch (_: ErrnoException) {
            }
        }
        openRequests.mkdirs()
        chmod(openRequests, 0x3FF) // 1777
    }

    private fun copyAsset(name: String, dest: File, mode: Int) {
        dest.parentFile?.mkdirs()
        val bytes = app.assets.open("linux/$name").use { it.readBytes() }
        if (!dest.isFile || !dest.readBytes().contentEquals(bytes)) {
            if (isSymlink(dest)) dest.delete()
            dest.writeBytes(bytes)
        }
        chmod(dest, mode)
    }

    // ---------------------------------------------------------------- /proc falsos

    private fun fakeEntries(): List<Triple<String, String, String>> {
        val cpus = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val rows = (0 until cpus).map { CPU_ROWS[it % CPU_ROWS.size] }
        val total = (0 until 10).map { col -> rows.sumOf { it[col] } }
        val stat = buildString {
            append("cpu  ").append(total.joinToString(" ")).append('\n')
            rows.forEachIndexed { i, r -> append("cpu$i ").append(r.joinToString(" ")).append('\n') }
            append("intr 127541 38 290 0 0 0 0 4 0 1 0\nctxt 140223\nbtime 1680020856\n")
            append("processes 772\nprocs_running 2\nprocs_blocked 0\nsoftirq 75663 0 5903 6 25375 10774 0 243 11685 0 21677\n")
        }
        val idle = rows.sumOf { it[3] } / 100.0
        return listOf(
            Triple("loadavg", "/proc/loadavg", "0.12 0.07 0.02 2/165 765\n"),
            Triple("stat", "/proc/stat", stat),
            Triple("uptime", "/proc/uptime", "124.08 ${"%.2f".format(java.util.Locale.ROOT, idle)}\n"),
            Triple("version", "/proc/version", "Linux version $FAKE_KERNEL (terminal-linux@android) #1 SMP PREEMPT_DYNAMIC\n"),
            Triple("vmstat", "/proc/vmstat", VMSTAT),
            Triple("cap_last_cap", "/proc/sys/kernel/cap_last_cap", "40\n"),
            Triple("max_user_watches", "/proc/sys/fs/inotify/max_user_watches", "4096\n"),
            Triple("overflowuid", "/proc/sys/kernel/overflowuid", "65534\n"),
            Triple("overflowgid", "/proc/sys/kernel/overflowgid", "65534\n"),
        )
    }

    private fun writeFakeSysdata() {
        sysdata.mkdirs()
        File(sysdata, "sys_empty").mkdirs()
        for ((name, _, content) in fakeEntries()) {
            val f = File(sysdata, name)
            if (!f.isFile || f.readText() != content) f.writeText(content)
        }
    }

    private fun canRead(path: String): Boolean = try {
        File(path).inputStream().use { it.read() }
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        const val USER_UID = 1000
        const val FAKE_KERNEL = "6.2.1-terminal-linux"
        const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        const val GUEST_LIB = "/usr/local/lib/terminal-linux"
        const val OPEN_DIR = "var/lib/terminal-linux/abrir"

        private val CPU_ROWS = listOf(
            listOf(31, 0, 226, 12027, 82, 10, 4, 9, 0, 0),
            listOf(45, 0, 664, 11144, 21, 263, 233, 12, 0, 0),
            listOf(494, 0, 537, 11283, 27, 10, 3, 8, 0, 0),
            listOf(359, 0, 234, 11723, 24, 26, 5, 7, 0, 0),
            listOf(295, 0, 268, 11772, 10, 12, 2, 12, 0, 0),
            listOf(270, 0, 251, 11833, 15, 3, 1, 10, 0, 0),
            listOf(430, 0, 520, 11386, 30, 8, 1, 12, 0, 0),
            listOf(30, 0, 172, 12108, 50, 8, 1, 13, 0, 0),
        )

        private const val VMSTAT = "nr_free_pages 1743136\nnr_zone_inactive_anon 179281\n" +
            "nr_zone_active_anon 7183\nnr_zone_inactive_file 22858\nnr_zone_active_file 51328\n" +
            "nr_zone_unevictable 642\nnr_zone_write_pending 0\nnr_mlock 0\nnr_bounce 0\n" +
            "nr_zspages 0\nnr_free_cma 0\nnuma_hit 1259626\nnuma_miss 0\nnuma_foreign 0\n" +
            "numa_interleave 720\nswap_ra 0\nswap_ra_hit 0\nnr_unstable 0\n"

        /**
         * Script de la sesión normal: la primera vez (o si la configuración falló) configura
         * Ubuntu como root; solo una pestaña a la vez, las demás esperan. Luego entra como [user].
         */
        fun userScript(user: String, marker: String, lock: String, setup: String, rootLogin: String, userLogin: String): String {
            val m = q(marker)
            val l = q(lock)
            return """
                |# Terminal Linux: abre Ubuntu como «$user». La app reescribe este archivo, no lo edites.
                |while [ ! -f $m ]; do
                |  if mkdir $l 2>/dev/null; then
                |    trap "rmdir $l 2>/dev/null; exit 130" INT TERM HUP
                |    $setup
                |    rmdir $l
                |    trap - INT TERM HUP
                |    if [ ! -f $m ]; then
                |      printf '\n\033[1;31mLa configuración de Ubuntu no ha terminado.\033[0m\n'
                |      printf 'Revisa la conexión a internet y abre otra pestaña (+) para reintentarlo.\n'
                |      printf 'Mientras tanto tienes una consola como root.\n\n'
                |      exec $rootLogin
                |    fi
                |  else
                |    printf '\rUbuntu se está configurando en otra pestaña, espera…'
                |    esperando=1
                |    sleep 2
                |  fi
                |done
                |[ -n "${'$'}esperando" ] && printf '\n'
                |exec $userLogin
                |""".trimMargin()
        }

        fun rootScript(rootLogin: String) = """
            |# Terminal Linux: abre Ubuntu como root. La app reescribe este archivo, no lo edites.
            |exec $rootLogin
            |""".trimMargin()

        /** Comillas simples para /system/bin/sh. */
        fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

        fun sh(args: List<String>) = args.joinToString(" ") { q(it) }

        fun isSymlink(f: File): Boolean = try {
            OsConstants.S_ISLNK(Os.lstat(f.absolutePath).st_mode)
        } catch (_: ErrnoException) {
            false
        }

        fun chmod(f: File, mode: Int) {
            try {
                Os.chmod(f.absolutePath, mode)
            } catch (_: ErrnoException) {
            }
        }

        /** rm -rf que nunca entra por un enlace simbólico. */
        fun deleteTree(f: File) {
            val st = try {
                Os.lstat(f.absolutePath)
            } catch (_: ErrnoException) {
                return
            }
            if (OsConstants.S_ISDIR(st.st_mode)) {
                chmod(f, (st.st_mode and 0x1FF) or 0x1C0)
                f.list()?.forEach { deleteTree(File(f, it)) }
            }
            try {
                Os.remove(f.absolutePath)
            } catch (_: ErrnoException) {
            }
        }
    }
}

/** [Fs] de Android para [TarExtractor]. */
object AndroidFs : Fs {
    override fun symlink(target: String, link: File) = Os.symlink(target, link.absolutePath)
    override fun chmod(file: File, mode: Int) = Os.chmod(file.absolutePath, mode)
    override fun isSymlink(file: File) = Linux.isSymlink(file)
}

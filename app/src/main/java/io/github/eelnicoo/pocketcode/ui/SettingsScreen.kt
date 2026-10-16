package io.github.eelnicoo.pocketcode.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eelnicoo.pocketcode.tools.ShellTool
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val c = LocalClaude.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.settingsState.collectAsStateWithLifecycle()

    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(state.displayName) }
    var workdir by remember { mutableStateOf(state.workingDir) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.border, cursorColor = c.accent,
        focusedLabelColor = c.accent, unfocusedLabelColor = c.textTertiary,
        focusedTextColor = c.text, unfocusedTextColor = c.text,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = c.text) }
            Text("Ajustes", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Section("Cuenta") {
                if (state.loggedIn) {
                    Text("Sesión iniciada con tu cuenta Claude.", color = c.text, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Cerrar sesión", outlined = true) { vm.settings.logout() }
                } else {
                    Text("Inicia sesión con tu suscripción de Claude (mismo flujo que Claude Code).", color = c.textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("1 · Abrir inicio de sesión") {
                        val url = vm.oauth.buildAuthorizeUrl()
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            .onFailure { status = "No se pudo abrir el navegador: ${it.message}" }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code, onValueChange = { code = it },
                        label = { Text("2 · Pega aquí el código") },
                        singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    PillButton("3 · Canjear código", enabled = code.isNotBlank()) {
                        scope.launch {
                            status = "Canjeando código…"
                            val r = vm.oauth.exchangeCode(code)
                            status = if (r.isSuccess) "Sesión iniciada" else "Error: ${r.exceptionOrNull()?.message}"
                            code = ""
                        }
                    }
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = c.accent, fontSize = 14.sp)
                }
            }

            Section("Perfil") {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; vm.settings.displayName = it.trim() },
                    label = { Text("Tu nombre (para el saludo)") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Apariencia") {
                Label("Tema")
                Segmented(
                    options = listOf("system" to "Sistema", "light" to "Claro", "dark" to "Oscuro"),
                    selected = state.themeMode,
                ) { vm.settings.themeMode = it }
                Spacer(Modifier.height(14.dp))
                Label("Fuente de las respuestas")
                Segmented(
                    options = listOf("serif" to "Serif", "sans" to "Sans"),
                    selected = state.chatFont,
                ) { vm.settings.chatFont = it }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Así se verán las respuestas de Claude.",
                    color = c.textSecondary, fontSize = 15.sp,
                    fontFamily = if (state.chatFont == "sans") FontFamily.Default else FontFamily.Serif,
                )
            }

            Section("Directorio de trabajo") {
                OutlinedTextField(
                    value = workdir, onValueChange = { workdir = it },
                    label = { Text("Ruta (vacío = almacenamiento de la app)") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                )
                Spacer(Modifier.height(10.dp))
                PillButton("Guardar", outlined = true) { vm.settings.workingDir = workdir.trim(); status = "Directorio guardado" }
            }

            Section("Shizuku") {
                Text(
                    "Con Shizuku, los comandos de shell se ejecutan con permisos de depuración (adb), sin root. " +
                        "Instala la app Shizuku (de Rikka), iníciala y concede el permiso.",
                    color = c.textSecondary, fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Usar Shizuku para la shell", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.useShizuku, onCheckedChange = { vm.settings.useShizuku = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White),
                    )
                }
                Spacer(Modifier.height(8.dp))
                PillButton("Comprobar / pedir permiso", outlined = true) {
                    status = runCatching {
                        if (ShellTool.shizukuReady()) "Shizuku listo y con permiso"
                        else {
                            runCatching { rikka.shizuku.Shizuku.requestPermission(1001) }
                            "Solicitud enviada. Si no aparece, comprueba que Shizuku está en marcha."
                        }
                    }.getOrElse { "Shizuku no disponible: ${it.message}" }
                }
            }

            Text(
                "PocketCode · cliente no oficial que usa tu propia cuenta de Claude. Los chats se guardan solo en este dispositivo.",
                color = c.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalClaude.current
    Column {
        Text(title, color = c.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.composer)
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun Label(text: String) {
    val c = LocalClaude.current
    Text(text, color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun PillButton(text: String, outlined: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalClaude.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .then(if (outlined) Modifier.border(1.dp, c.border, shape) else Modifier.background(if (enabled) c.accent else c.accent.copy(alpha = 0.4f)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (outlined) c.text else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = LocalClaude.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.userBubble).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.composer else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) c.text else c.textSecondary, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
    Spacer(Modifier.size(0.dp))
}

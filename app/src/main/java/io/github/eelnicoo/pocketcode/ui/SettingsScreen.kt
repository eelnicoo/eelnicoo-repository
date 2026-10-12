package io.github.eelnicoo.pocketcode.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eelnicoo.pocketcode.data.Settings
import io.github.eelnicoo.pocketcode.tools.ShellTool
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AgentViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.settingsState.collectAsStateWithLifecycle()

    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var workdir by remember { mutableStateOf(vm.settings.workingDir) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Section("Cuenta Claude")
            if (state.loggedIn) {
                Text("✅ Sesión iniciada con tu cuenta Claude.")
                OutlinedButton(onClick = { vm.settings.logout() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Cerrar sesión")
                }
            } else {
                Text("Inicia sesión con tu suscripción de Claude (flujo OAuth, igual que Claude Code).")
                Button(
                    onClick = {
                        val url = vm.oauth.buildAuthorizeUrl()
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure { status = "No se pudo abrir el navegador: ${it.message}" }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("1) Iniciar sesión en el navegador") }

                Text(
                    "2) Autoriza, copia el código que te muestra la web y pégalo aquí:",
                    Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("código#state") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Button(
                    onClick = {
                        scope.launch {
                            status = "Canjeando código…"
                            val r = vm.oauth.exchangeCode(code)
                            status = if (r.isSuccess) "✅ ¡Sesión iniciada!" else "❌ ${r.exceptionOrNull()?.message}"
                            code = ""
                        }
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("3) Canjear código") }
            }
            if (status.isNotBlank()) {
                Text(status, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
            }

            Divider(Modifier.padding(vertical = 16.dp))
            Section("Modelo")
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Settings.MODELS.take(3).forEach { m ->
                    FilterChip(
                        selected = state.model == m,
                        onClick = { vm.settings.model = m },
                        label = { Text(m.removePrefix("claude-")) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Settings.MODELS.drop(3).forEach { m ->
                    FilterChip(
                        selected = state.model == m,
                        onClick = { vm.settings.model = m },
                        label = { Text(m.removePrefix("claude-")) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }

            Divider(Modifier.padding(vertical = 16.dp))
            Section("Directorio de trabajo")
            OutlinedTextField(
                value = workdir,
                onValueChange = { workdir = it },
                label = { Text("Ruta (vacío = almacenamiento interno de la app)") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedButton(
                onClick = { vm.settings.workingDir = workdir.trim() },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Guardar directorio") }

            Divider(Modifier.padding(vertical = 16.dp))
            Section("Shizuku (acceso de sistema)")
            Text(
                "Con Shizuku los comandos de shell se ejecutan con permisos de sistema (adb), " +
                    "ideal en GrapheneOS sin root. Instala la app Shizuku, iníciala y concede el permiso.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = state.useShizuku, onCheckedChange = { vm.settings.useShizuku = it })
                Text("  Usar Shizuku para shell", Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = {
                    status = runCatching {
                        if (ShellTool.shizukuReady()) "Shizuku listo y con permiso ✅"
                        else {
                            runCatching { rikka.shizuku.Shizuku.requestPermission(1001) }
                            "Solicitud enviada. Revisa Shizuku y vuelve a comprobar."
                        }
                    }.getOrElse { "Shizuku no disponible: ${it.message}" }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Comprobar / pedir permiso Shizuku") }

            Divider(Modifier.padding(vertical = 16.dp))
            Text(
                "PocketCode — cliente no oficial. Usa tu propia cuenta de Claude.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

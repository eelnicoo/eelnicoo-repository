package dev.nico.mtsamp4

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val list by Conversions.items.collectAsState()
    val scanning by Conversions.scanning.collectAsState()
    val notice by Conversions.notice.collectAsState()
    var aac by remember { mutableStateOf(Settings.audioAac(context)) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        val msg = notice ?: return@LaunchedEffect
        Conversions.clearNotice()
        snackbar.showSnackbar(msg)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            askNotifications()
            Conversions.enqueue(context, uris)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            askNotifications()
            Conversions.enqueueTree(context, uri)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MTS a MP4") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Pasa los vídeos al formato MP4 copiando la imagen tal cual: " +
                        "misma calidad, sin recomprimir. Se guardan en Películas › ${ConversionService.OUTPUT_DIR}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pickFiles.launch(arrayOf("video/*", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Elegir vídeos")
                    }
                    OutlinedButton(
                        onClick = { pickFolder.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Elegir carpeta")
                    }
                }
            }
            item {
                AudioOption(aac) {
                    aac = it
                    Settings.setAudioAac(context, it)
                }
            }
            if (scanning) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Buscando archivos .MTS…")
                    }
                }
            }
            if (list.isEmpty()) {
                if (!scanning) {
                    item {
                        Text(
                            "Elige uno o varios archivos .MTS, o la carpeta de la tarjeta de la cámara " +
                                "(PRIVATE › AVCHD) para convertir todos a la vez. También puedes " +
                                "compartirlos con esta app desde Archivos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Vídeos",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (list.any { it.finished }) {
                            TextButton(onClick = { Conversions.removeFinished() }) {
                                Text("Quitar terminados")
                            }
                        }
                    }
                }
                items(list, key = { it.id }) { ItemCard(it) }
            }
        }
    }
}

@Composable
private fun AudioOption(checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Audio compatible (AAC)", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (checked) {
                        "El sonido se convierte a AAC de alta calidad. El vídeo se sigue copiando tal cual."
                    } else {
                        "El sonido se copia tal cual (AC-3, sin pérdida). Si luego el MP4 suena " +
                            "mudo en el móvil, activa esto y conviértelo otra vez."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ItemCard(item: ConversionItem) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(item.status)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.outputName ?: item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusLine(context, item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.status == Status.RUNNING) {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            item.videoInfo?.let { InfoLine("Vídeo", it) }
            item.audioInfo?.let { InfoLine("Audio", it) }
            dateLine(item)?.let { InfoLine("Fecha", it) }
            if (item.status == Status.FAILED) {
                Text(
                    item.error ?: "No se pudo convertir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (item.status) {
                    Status.PENDING, Status.RUNNING ->
                        TextButton(onClick = { Conversions.cancel(item.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancelar")
                        }
                    Status.DONE -> {
                        TextButton(onClick = { openVideo(context, item) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ver")
                        }
                        TextButton(onClick = { shareVideo(context, item) }) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartir")
                        }
                    }
                    Status.FAILED, Status.CANCELLED ->
                        TextButton(onClick = { Conversions.retry(context, item.id) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reintentar")
                        }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: Status) {
    val (icon: ImageVector?, tint) = when (status) {
        Status.DONE -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        Status.FAILED -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
        Status.CANCELLED -> Icons.Filled.Close to MaterialTheme.colorScheme.onSurfaceVariant
        Status.PENDING -> Icons.Filled.PlayArrow to MaterialTheme.colorScheme.onSurfaceVariant
        Status.RUNNING -> null to MaterialTheme.colorScheme.primary
    }
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
    } else {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun statusLine(context: Context, item: ConversionItem): String {
    val size = if (item.size > 0) Formatter.formatShortFileSize(context, item.size) else null
    return when (item.status) {
        Status.PENDING -> listOfNotNull(size, "en cola").joinToString(" · ")
        Status.RUNNING -> listOfNotNull(size, "convirtiendo… ${(item.progress * 100).toInt()} %").joinToString(" · ")
        Status.DONE -> {
            val secs = ((item.finishedAt - item.startedAt) / 1000).coerceAtLeast(1)
            val took = if (secs < 60) "$secs s" else "${secs / 60} min ${secs % 60} s"
            val out = if (item.outputSize > 0) Formatter.formatShortFileSize(context, item.outputSize) else null
            listOfNotNull(out, "listo en $took", "Películas › ${ConversionService.OUTPUT_DIR}").joinToString(" · ")
        }
        Status.FAILED -> listOfNotNull(item.name, "error").joinToString(" · ")
        Status.CANCELLED -> listOfNotNull(item.name, "cancelado").joinToString(" · ")
    }
}

private fun dateLine(item: ConversionItem): String? {
    if (item.recordedMs <= 0) return null
    val text = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(item.recordedMs).atZone(ZoneId.systemDefault()))
    return when (item.dateSource) {
        NativeRemuxer.DATE_CAMERA -> "$text (la de la cámara)"
        NativeRemuxer.DATE_FILE -> "$text (la del archivo)"
        else -> text
    }
}

private fun openVideo(context: Context, item: ConversionItem) {
    val uri = item.output ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "video/mp4")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No hay ninguna app para ver vídeos.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareVideo(context: Context, item: ConversionItem) {
    val uri = item.output ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, item.outputName ?: "Compartir vídeo"))
}

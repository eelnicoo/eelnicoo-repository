package io.github.eelnicoo.pocketcode.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eelnicoo.pocketcode.chat.AssistantRow
import io.github.eelnicoo.pocketcode.chat.ImageRef
import io.github.eelnicoo.pocketcode.chat.Part
import io.github.eelnicoo.pocketcode.chat.TextPart
import io.github.eelnicoo.pocketcode.chat.ThinkingPart
import io.github.eelnicoo.pocketcode.chat.ToolPart
import io.github.eelnicoo.pocketcode.chat.ToolState
import io.github.eelnicoo.pocketcode.chat.UserRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ---------------------------------------------------------------------------- user

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessage(row: UserRow, canEdit: Boolean, onEdit: (Int) -> Unit) {
    val c = LocalClaude.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (row.images.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.images.forEach { Base64Thumb(it, 120) }
            }
        }
        row.files.forEach { FileChip(it, Modifier.padding(bottom = 6.dp)) }
        if (row.text.isNotBlank()) {
            Box {
                Box(
                    Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.userBubble)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Text(row.text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copiar") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { clipboard.setText(AnnotatedString(row.text)); menu = false },
                    )
                    if (canEdit) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onEdit(row.index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileChip(name: String, modifier: Modifier = Modifier) {
    val c = LocalClaude.current
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        Text(" $name", fontSize = 13.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp))
    }
}

@Composable
private fun Base64Thumb(img: ImageRef, sizeDp: Int) {
    val c = LocalClaude.current
    val bitmap by produceState<ImageBitmap?>(null, img.data) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(img.data, Base64.DEFAULT)
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp)).background(c.userBubble)) {
        bitmap?.let { Image(it, contentDescription = "Imagen adjunta", contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp)) }
    }
}

// ---------------------------------------------------------------------------- assistant

@Composable
fun AssistantTurn(row: AssistantRow, onRetry: () -> Unit) {
    val c = LocalClaude.current
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var i = 0
        val parts = row.parts
        while (i < parts.size) {
            val p = parts[i]
            if (p is ToolPart) {
                val group = ArrayList<ToolPart>()
                while (i < parts.size && parts[i] is ToolPart) { group.add(parts[i] as ToolPart); i++ }
                ToolGroup(group)
                continue
            }
            when (p) {
                is TextPart -> if (p.text.isNotEmpty()) SelectionContainer { MarkdownView(p.text) }
                is ThinkingPart -> ThinkingView(p)
                else -> Unit
            }
            i++
        }
        if (row.streaming) {
            val working = parts.lastOrNull().let { it == null || it is ToolPart || (it is ThinkingPart) }
            ClaudeSpark(size = if (working) 26.dp else 20.dp, animated = true, modifier = Modifier.padding(top = 2.dp))
        } else if (row.copyText.isNotEmpty()) {
            ActionRow(row.copyText, showRetry = row.isLast, onRetry = onRetry)
        } else if (row.isLast && parts.isEmpty()) {
            Text("(sin respuesta)", color = c.textTertiary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ThinkingView(p: ThinkingPart) {
    val c = LocalClaude.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClaudeSpark(size = 14.dp, color = c.textTertiary, animated = p.streaming)
            Text(
                if (p.streaming) "  Pensando…" else "  Razonamiento",
                color = c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null, tint = c.textTertiary, modifier = Modifier.size(18.dp),
            )
        }
        if (!open && p.streaming && p.text.isNotBlank()) {
            Text(
                p.text.trim().lines().last(),
                color = c.textTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        AnimatedVisibility(open) {
            val bar = c.border
            Text(
                p.text.trim(),
                color = c.textSecondary, fontSize = 14.sp, lineHeight = 21.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .drawBehind { drawRect(bar, size = Size(2.dp.toPx(), size.height)) }
                    .padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun ToolGroup(tools: List<ToolPart>) {
    val c = LocalClaude.current
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, c.border, shape)) {
        tools.forEachIndexed { k, t ->
            if (k > 0) HorizontalDivider(color = c.border)
            ToolRow(t)
        }
    }
}

@Composable
private fun ToolRow(t: ToolPart) {
    val c = LocalClaude.current
    var open by remember { mutableStateOf(false) }
    val (icon, label, target) = describe(t)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.size(10.dp))
            Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(8.dp))
            Text(
                target, color = c.textTertiary, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            when (t.state) {
                ToolState.WRITING, ToolState.RUNNING ->
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = c.accent)
                ToolState.DONE -> Icon(Icons.Filled.Check, "Hecho", tint = c.success, modifier = Modifier.size(16.dp))
                ToolState.ERROR -> Icon(Icons.Filled.ErrorOutline, "Error", tint = c.danger, modifier = Modifier.size(16.dp))
                ToolState.INTERRUPTED -> Icon(Icons.Outlined.PauseCircle, "Interrumpido", tint = c.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailBox("Entrada", t.partialInput ?: prettyInput(t.input))
                t.output?.let { DetailBox(if (t.state == ToolState.ERROR) "Error" else "Salida", it) }
            }
        }
    }
}

@Composable
private fun DetailBox(title: String, body: String) {
    val c = LocalClaude.current
    Column {
        Text(title, fontSize = 11.sp, color = c.textTertiary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.codeBg)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            SelectionContainer {
                Text(body.take(20_000), style = CodeStyle.copy(color = c.codeText, fontSize = 12.sp), softWrap = false)
            }
        }
    }
}

private fun JsonObject.s(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

private fun prettyInput(input: JsonObject): String = input.entries.joinToString("\n") { (k, v) ->
    val value = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
    "$k: $value"
}

private data class ToolLook(val icon: ImageVector, val label: String, val target: String)

private fun describe(t: ToolPart): ToolLook {
    val i = t.input
    return when (t.name) {
        "read_file" -> ToolLook(Icons.Outlined.Description, "Leer", i.s("path"))
        "write_file" -> ToolLook(Icons.Outlined.EditNote, "Escribir", i.s("path"))
        "edit_file" -> ToolLook(Icons.Outlined.Edit, "Editar", i.s("path"))
        "list_dir" -> ToolLook(Icons.Outlined.Folder, "Listar", i.s("path").ifEmpty { "." })
        "grep" -> ToolLook(Icons.Outlined.Search, "Buscar", i.s("pattern"))
        "run_shell" -> ToolLook(Icons.Outlined.Terminal, "Ejecutar", i.s("command").lines().firstOrNull().orEmpty())
        "build_apk" -> ToolLook(Icons.Outlined.Android, "Construir APK", i.s("mode"))
        "web_search" -> ToolLook(Icons.Outlined.Language, "Buscar en la web", i.s("query"))
        "web_fetch" -> ToolLook(Icons.Outlined.Language, "Leer página", i.s("url"))
        else -> ToolLook(Icons.Outlined.Build, t.name, "")
    }.let { look -> if (look.target.isEmpty() && t.partialInput != null) look.copy(target = "…") else look }
}

@Composable
private fun ActionRow(copyText: String, showRetry: Boolean, onRetry: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    Row(Modifier.padding(start = 0.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallAction(if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy, "Copiar") {
            clipboard.setText(AnnotatedString(copyText))
            copied = true
            scope.launch { delay(1800); copied = false }
        }
        if (showRetry) SmallAction(Icons.Outlined.Refresh, "Reintentar", onRetry)
        SmallAction(Icons.Outlined.Share, "Compartir") {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, copyText)
            }
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = LocalClaude.current
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, tint = c.textTertiary, modifier = Modifier.size(18.dp))
    }
}

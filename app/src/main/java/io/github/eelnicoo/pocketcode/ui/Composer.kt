package io.github.eelnicoo.pocketcode.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<Attachment>,
    attaching: Boolean,
    onRemoveAttachment: (String) -> Unit,
    busy: Boolean,
    loggedIn: Boolean,
    editing: Boolean,
    onCancelEdit: () -> Unit,
    modelName: String,
    onModelClick: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    focusTick: Int,
    modifier: Modifier = Modifier,
) {
    val c = LocalClaude.current
    val shape = RoundedCornerShape(24.dp)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Editing a message or picking a suggestion moves the cursor here, like the Claude app.
    LaunchedEffect(focusTick) {
        if (focusTick > 0 && loggedIn) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    Column(modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
        if (editing) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Edit, null, tint = c.accent, modifier = Modifier.size(16.dp))
                Text("  Editando mensaje", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "Cancelar", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCancelEdit).padding(6.dp),
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.composer)
                .border(1.dp, c.border, shape),
        ) {
            if (attachments.isNotEmpty() || attaching) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    attachments.forEach { AttachmentPreview(it) { onRemoveAttachment(it.id) } }
                    if (attaching) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = c.accent)
                }
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                enabled = loggedIn,
                textStyle = TextStyle(color = c.text, fontSize = 16.sp, lineHeight = 23.sp),
                cursorBrush = SolidColor(c.accent),
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .focusRequester(focusRequester)
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                if (loggedIn) "Escribe a Claude" else "Inicia sesión en Ajustes para empezar",
                                color = c.textTertiary, fontSize = 16.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachButton(enabled = loggedIn && !busy, onPickImage = onPickImage, onTakePhoto = onTakePhoto, onPickFile = onPickFile)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onModelClick)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(modelName, color = c.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp))
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(6.dp))
                val canSend = loggedIn && !attaching && (text.isNotBlank() || attachments.isNotEmpty() || editing)
                SendButton(busy = busy, enabled = canSend, onSend = onSend, onStop = onStop)
            }
        }
    }
}

@Composable
private fun AttachButton(enabled: Boolean, onPickImage: () -> Unit, onTakePhoto: () -> Unit, onPickFile: () -> Unit) {
    val c = LocalClaude.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, c.border, CircleShape)
                .clickable(enabled = enabled) { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, "Adjuntar", tint = if (enabled) c.textSecondary else c.textTertiary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Fotos") },
                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = { menu = false; onPickImage() },
            )
            DropdownMenuItem(
                text = { Text("Cámara") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                onClick = { menu = false; onTakePhoto() },
            )
            DropdownMenuItem(
                text = { Text("Archivo (texto o PDF)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null) },
                onClick = { menu = false; onPickFile() },
            )
        }
    }
}

@Composable
private fun SendButton(busy: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val c = LocalClaude.current
    val active = busy || enabled
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) c.accent else c.accent.copy(alpha = 0.35f))
            .clickable(enabled = active) { if (busy) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            Icon(Icons.Filled.Stop, "Parar", tint = Color.White, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Filled.ArrowUpward, "Enviar", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AttachmentPreview(a: Attachment, onRemove: () -> Unit) {
    val c = LocalClaude.current
    Box {
        if (a.kind == Attachment.Kind.IMAGE && a.thumbnail != null) {
            Image(
                a.thumbnail, contentDescription = a.name, contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Row(
                Modifier
                    .heightIn(min = 60.dp)
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.userBubble)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    Text(a.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (a.kind == Attachment.Kind.PDF) "PDF" else "Texto", color = c.textTertiary, fontSize = 11.sp)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, "Quitar", tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

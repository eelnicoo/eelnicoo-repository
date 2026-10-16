@file:OptIn(ExperimentalFoundationApi::class)

package io.github.eelnicoo.pocketcode.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eelnicoo.pocketcode.data.ChatMeta
import java.util.Calendar

@Composable
fun HistoryDrawer(
    open: Boolean,
    chats: List<ChatMeta>,
    currentId: String?,
    displayName: String,
    loggedIn: Boolean,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onToggleStar: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val c = LocalClaude.current
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ChatMeta?>(null) }
    var deleting by remember { mutableStateOf<ChatMeta?>(null) }
    // Each time the drawer opens, start from the full list.
    LaunchedEffect(open) { if (!open) query = "" }

    val filtered = remember(chats, query) {
        val q = query.trim()
        if (q.isEmpty()) chats else chats.filter { it.title.contains(q, ignoreCase = true) }
    }
    val starred = filtered.filter { it.starred }
    val groups = remember(filtered) {
        val now = System.currentTimeMillis()
        filtered.filterNot { it.starred }.groupBy { groupLabel(it.updatedAt, now) }.toList()
    }

    Column(Modifier.fillMaxSize().background(c.drawer)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ClaudeSpark(size = 22.dp)
            Text("  Claude", fontFamily = FontFamily.Serif, fontSize = 24.sp, color = c.text, fontWeight = FontWeight.Medium)
        }
        // Search
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.selected.copy(alpha = if (c.dark) 0.7f else 1f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box { if (query.isEmpty()) Text("Buscar chats", color = c.textTertiary, fontSize = 15.sp); inner() }
                },
            )
        }
        DrawerAction(Icons.Outlined.AddComment, "Nuevo chat", c.accent, onNewChat)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (chats.isEmpty()) "Aún no hay chats. Los que empieces aparecerán aquí." else "Sin resultados",
                        color = c.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(20.dp),
                    )
                }
            }
            if (starred.isNotEmpty()) {
                item { SectionHeader("Destacados") }
                items(starred, key = { "s" + it.id }) { chat ->
                    ChatItem(chat, chat.id == currentId, onOpen, { renaming = it }, onToggleStar, { deleting = it })
                }
            }
            groups.forEach { (label, list) ->
                item(key = "h$label") { SectionHeader(label) }
                items(list, key = { it.id }) { chat ->
                    ChatItem(chat, chat.id == currentId, onOpen, { renaming = it }, onToggleStar, { deleting = it })
                }
            }
        }

        HorizontalDivider(color = c.border)
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onSettings)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Text(initial, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(displayName.ifBlank { "Tu perfil" }, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(if (loggedIn) "Cuenta Claude conectada" else "Sin sesión", color = c.textTertiary, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.Settings, "Ajustes", tint = c.textSecondary)
        }
    }

    renaming?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renombrar chat") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onRename(chat.id, title); renaming = null }, enabled = title.isNotBlank()) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
            containerColor = c.composer,
        )
    }
    deleting?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar chat?") },
            text = { Text("“${chat.title}” se borrará de este dispositivo.") },
            confirmButton = {
                TextButton(onClick = { onDelete(chat.id); deleting = null }) { Text("Eliminar", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
            containerColor = c.composer,
        )
    }
}

@Composable
private fun DrawerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val c = LocalClaude.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text("  $label", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionHeader(label: String) {
    val c = LocalClaude.current
    Text(
        label, color = c.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun ChatItem(
    chat: ChatMeta,
    selected: Boolean,
    onOpen: (String) -> Unit,
    onRename: (ChatMeta) -> Unit,
    onToggleStar: (String) -> Unit,
    onDelete: (ChatMeta) -> Unit,
) {
    val c = LocalClaude.current
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 8.dp, vertical = 1.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) c.selected else Color.Transparent)
                .combinedClickable(
                    onClick = { onOpen(chat.id) },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                chat.title, color = if (selected) c.text else c.textSecondary, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (chat.starred) Icon(Icons.Filled.Star, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (chat.starred) "Quitar de destacados" else "Destacar") },
                leadingIcon = { Icon(if (chat.starred) Icons.Outlined.StarOutline else Icons.Filled.Star, null) },
                onClick = { menu = false; onToggleStar(chat.id) },
            )
            DropdownMenuItem(
                text = { Text("Renombrar") },
                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                onClick = { menu = false; onRename(chat) },
            )
            DropdownMenuItem(
                text = { Text("Eliminar", color = c.danger) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = c.danger) },
                onClick = { menu = false; onDelete(chat) },
            )
        }
    }
}

private fun groupLabel(time: Long, now: Long): String {
    fun dayIndex(t: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        return cal.get(Calendar.YEAR) * 400L + cal.get(Calendar.DAY_OF_YEAR)
    }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val sameYear = today.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val diffDays = if (sameYear) dayIndex(now) - dayIndex(time) else ((now - time) / 86_400_000L) + 1
    return when {
        diffDays <= 0L -> "Hoy"
        diffDays == 1L -> "Ayer"
        diffDays < 7L -> "Últimos 7 días"
        diffDays < 30L -> "Últimos 30 días"
        else -> "Más antiguos"
    }
}

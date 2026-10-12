package io.github.eelnicoo.pocketcode.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AgentViewModel, onOpenSettings: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val state by vm.settingsState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(items.size, busy) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                Column {
                    Text("Claude Code", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        state.model.removePrefix("claude-"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                Box(
                    Modifier.padding(start = 12.dp).size(30.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(">_", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
            },
            actions = {
                IconButton(onClick = { vm.clear() }) {
                    Icon(Icons.Outlined.AddComment, contentDescription = "Nuevo chat")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        if (items.isEmpty()) {
            WelcomeState(
                loggedIn = state.loggedIn,
                onSuggestion = { input = it },
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items) { item -> MessageItem(item) }
                if (busy) item { TypingIndicator() }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            enabled = state.loggedIn && !busy,
            onSend = { vm.send(input); input = "" },
        )
    }
}

@Composable
private fun WelcomeState(
    loggedIn: Boolean,
    onSuggestion: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(">_", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 26.sp) }
        Spacer(Modifier.size(16.dp))
        Text("Claude Code en tu bolsillo", fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.size(6.dp))
        Text(
            if (loggedIn) "Pídeme que lea, escriba o edite archivos, ejecute comandos o construya APKs."
            else "Inicia sesión con tu cuenta Claude para empezar.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.size(20.dp))
        if (loggedIn) {
            SUGGESTIONS.forEach { s ->
                SuggestionChip(s) { onSuggestion(s) }
                Spacer(Modifier.size(8.dp))
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onOpenSettings() },
            ) {
                Text("Ir a Ajustes para iniciar sesión", color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Text(text, modifier = Modifier.padding(14.dp), fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MessageItem(item: UiItem) {
    when (item) {
        is UiItem.User -> UserBubble(item.text)
        is UiItem.Assistant -> AssistantBlock(item.text)
        is UiItem.Thinking -> Unit
        is UiItem.Notice -> NoticeCard(item.text)
        is UiItem.ToolCall -> ToolCard(item)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 15.sp)
        }
    }
}

@Composable
private fun AssistantBlock(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("C", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Spacer(Modifier.size(10.dp))
        MarkdownText(
            markdown = text.ifBlank { "…" },
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NoticeCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
    }
}

@Composable
private fun ToolCard(item: UiItem.ToolCall) {
    var expanded by remember { mutableStateOf(false) }
    val running = item.output == null
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(toolIcon(item.name), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                when {
                    running -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    item.error -> Icon(Icons.Default.ErrorOutline, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    else -> Icon(Icons.Default.Check, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(4.dp))
                Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
            if (!expanded) {
                Text(
                    item.input.replace("\n", " "),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Text("entrada", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(item.input, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val out = item.output
                    if (out != null) {
                        Text("salida", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp))
                        Text(
                            out, style = CodeStyle,
                            color = if (item.error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("C", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Spacer(Modifier.size(10.dp))
        val transition = rememberInfiniteTransition(label = "typing")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                val a by transition.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(600, delayMillis = i * 150), RepeatMode.Reverse),
                    label = "dot$i",
                )
                Box(
                    Modifier.size(7.dp).alpha(a)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensaje a Claude…") },
                enabled = enabled,
                maxLines = 6,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions.Default,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.size(8.dp))
            val canSend = enabled && value.isNotBlank()
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Enviar",
                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "read_file" -> Icons.Default.Description
    "write_file" -> Icons.Default.Edit
    "edit_file" -> Icons.Default.Edit
    "list_dir" -> Icons.Default.Folder
    "grep" -> Icons.Default.Search
    "run_shell" -> Icons.Default.Terminal
    "build_apk" -> Icons.Default.Android
    else -> Icons.Default.Code
}

private val SUGGESTIONS = listOf(
    "Lista los archivos del directorio de trabajo",
    "Crea un archivo hola.txt con un saludo",
    "¿Qué herramientas tienes disponibles?",
    "Ejecuta: uname -a",
)

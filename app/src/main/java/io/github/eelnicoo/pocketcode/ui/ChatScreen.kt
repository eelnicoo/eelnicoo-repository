package io.github.eelnicoo.pocketcode.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eelnicoo.pocketcode.api.History
import io.github.eelnicoo.pocketcode.chat.AssistantRow
import io.github.eelnicoo.pocketcode.chat.ChatRows
import io.github.eelnicoo.pocketcode.chat.UserRow
import io.github.eelnicoo.pocketcode.data.Settings
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val c = LocalClaude.current
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    var showModels by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.addImage(it) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.addFile(it) }
    }
    val context = LocalContext.current
    // Saveable: the camera app may outlive this activity (rotation, process death).
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        photoUri = null
        if (ok && uri != null) vm.addImage(uri)
        else if (ok) vm.showNotice("No se pudo recuperar la foto. Vuelve a intentarlo.")
    }

    // Tool results are indexed once per history, so 50 ms streaming ticks stay cheap.
    val resultIndex = remember(state.messages) { ChatRows.index(state.messages) }
    val rows = remember(state.messages, state.draft, state.runningTools, state.busy, resultIndex) {
        ChatRows.build(state.messages, state.draft, state.runningTools, state.busy, resultIndex)
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && state.editingIndex != null) { vm.cancelEdit() }

    // Closing the drawer must not leave the keyboard typing into its hidden search box.
    LaunchedEffect(drawer.isClosed) { if (drawer.isClosed) focus.clearFocus() }

    // While the user reads higher up inside a streaming answer, compensate its growth so the
    // text does not slide away. Done after layout and never during a fling or animation.
    val streamingNow by rememberUpdatedState(state.busy)
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val draggedNow by rememberUpdatedState(dragged)
    LaunchedEffect(listState) {
        var lastKey: Any? = null
        var lastSize = 0
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key to it.size } }
            .collect { info ->
                val (key, size) = info ?: return@collect
                val delta = size - lastSize
                // A finger drag cannot be interrupted by the request (it has higher priority), while
                // flings and the jump animation must not be: only compensate when idle or dragging.
                if (key == lastKey && delta > 0 && streamingNow && (draggedNow || !listState.isScrollInProgress) &&
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 0
                ) {
                    listState.requestScrollToItem(0, listState.firstVisibleItemScrollOffset + delta)
                }
                lastKey = key
                lastSize = size
            }
    }

    // Jump to the newest message whenever a new row appears.
    LaunchedEffect(rows.size, state.chatId) {
        if (rows.isNotEmpty()) listState.scrollToItem(0)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = c.drawer,
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                windowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
            ) {
                HistoryDrawer(
                    open = drawer.isOpen,
                    chats = state.chats,
                    currentId = state.chatId,
                    running = state.runningChats,
                    displayName = settings.displayName,
                    loggedIn = settings.loggedIn,
                    onNewChat = { vm.newChat(); scope.launch { drawer.close() } },
                    onOpen = { vm.openChat(it); scope.launch { drawer.close() } },
                    onRename = vm::renameChat,
                    onToggleStar = vm::toggleStar,
                    onDelete = vm::deleteChat,
                    onSettings = { scope.launch { drawer.close() }; onOpenSettings() },
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            TopBar(
                title = if (state.chatId == null) "" else state.title,
                starred = state.starred,
                hasChat = state.chatId != null,
                onMenu = { focus.clearFocus(); scope.launch { drawer.open() } },
                onNewChat = { vm.newChat() },
                onRename = { t -> state.chatId?.let { vm.renameChat(it, t) } },
                onToggleStar = { state.chatId?.let { vm.toggleStar(it) } },
                onDelete = { state.chatId?.let { vm.deleteChat(it) } },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isEmpty()) {
                    Greeting(
                        name = settings.displayName,
                        loggedIn = settings.loggedIn,
                        onSuggestion = { vm.applySuggestion(it) },
                        onOpenSettings = onOpenSettings,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.Top),
                    ) {
                        items(rows.asReversed(), key = { it.key }) { row ->
                            when (row) {
                                is UserRow -> UserMessage(row, canEdit = !state.busy) { vm.startEdit(it) }
                                is AssistantRow -> {
                                    val failed = state.failedTurn
                                    if (row.isLast && failed != null) {
                                        AssistantTurn(row, resumeLabel = "Editar mensaje", onRetry = { vm.startEdit(failed) })
                                    } else {
                                        AssistantTurn(row, onRetry = { vm.retry() })
                                    }
                                }
                            }
                        }
                    }
                    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 400 } }
                    JumpToBottom(
                        visible = showJump,
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    )
                }
            }

            // Takes layout space (instead of floating over the list) so it never hides the answer.
            state.notice?.let { notice ->
                NoticeBar(
                    text = notice,
                    // Only when the chat really ends with an unanswered message; other notices
                    // (attachments, camera, storage) must never re-run the last answer.
                    canRetry = !state.busy && state.editingIndex == null &&
                        state.messages.lastOrNull()?.let { History.role(it) == "user" } == true,
                    onRetry = { vm.retry() },
                    onDismiss = { vm.dismissNotice() },
                )
            }

            Composer(
                text = state.composerText,
                onTextChange = vm::setComposerText,
                attachments = state.attachments,
                attaching = state.attaching,
                onRemoveAttachment = vm::removeAttachment,
                busy = state.busy,
                loggedIn = settings.loggedIn,
                editing = state.editingIndex != null,
                onCancelEdit = vm::cancelEdit,
                modelName = Settings.modelInfo(settings.model).name,
                onModelClick = { focus.clearFocus(); showModels = true },
                onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onTakePhoto = {
                    runCatching {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        // Old shots were already copied into the chat; keep recent ones that may still be decoding.
                        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 10 * 60_000L }?.forEach { it.delete() }
                        val file = File(dir, "foto-${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                        photoUri = uri
                        takePhoto.launch(uri)
                    }.onFailure { vm.showNotice("No se pudo abrir la cámara.") }
                },
                onPickFile = { pickFile.launch(arrayOf("text/*", "application/pdf", "application/json", "application/xml", "image/*", "*/*")) },
                onSend = { vm.send(); scope.launch { if (rows.isNotEmpty()) listState.scrollToItem(0) } },
                onStop = vm::stop,
                focusTick = state.focusTick,
            )
        }
    }

    if (showModels) {
        ModelSheet(
            current = settings.model,
            extendedThinking = settings.extendedThinking,
            onSelect = vm::setModel,
            onThinking = vm::setExtendedThinking,
            onDismiss = { showModels = false },
        )
    }
}

@Composable
private fun JumpToBottom(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalClaude.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(c.composer)
                .border(1.dp, c.border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.ArrowDownward, "Ir al final", tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun TopBar(
    title: String,
    starred: Boolean,
    hasChat: Boolean,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onRename: (String) -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalClaude.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Historial", tint = c.text) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (hasChat) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { menu = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (starred) "Quitar de destacados" else "Destacar") },
                        leadingIcon = { Icon(if (starred) Icons.Outlined.StarOutline else Icons.Filled.Star, null) },
                        onClick = { menu = false; onToggleStar() },
                    )
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = c.danger) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = c.danger) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        }
        IconButton(onClick = onNewChat) { Icon(Icons.Outlined.AddComment, "Nuevo chat", tint = c.text) }
    }
    if (renaming) {
        var text by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar chat") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(text); renaming = false }, enabled = text.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
            containerColor = c.composer,
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("¿Eliminar chat?") },
            text = { Text("Se borrará de este dispositivo.") },
            confirmButton = { TextButton(onClick = { onDelete(); deleting = false }) { Text("Eliminar", color = c.danger) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
            containerColor = c.composer,
        )
    }
}

@Composable
private fun Greeting(name: String, loggedIn: Boolean, onSuggestion: (String) -> Unit, onOpenSettings: () -> Unit) {
    val c = LocalClaude.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val hello = when (hour) {
        in 6..11 -> "Buenos días"
        in 12..20 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val first = name.trim().split(" ").firstOrNull().orEmpty()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClaudeSpark(size = 34.dp)
            Spacer(Modifier.size(12.dp))
            Text(
                if (first.isNotEmpty()) "$hello, $first" else hello,
                fontFamily = FontFamily.Serif, fontSize = 30.sp, color = c.text, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.size(28.dp))
        if (loggedIn) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Suggestion(Icons.Outlined.Code, "Código") { onSuggestion("Ayúdame a escribir un script que ") }
                Suggestion(Icons.Outlined.Folder, "Archivos") { onSuggestion("Lista los archivos de mi directorio de trabajo y resúmelos") }
                Suggestion(Icons.Outlined.PhoneAndroid, "Mi móvil") { onSuggestion("Dime información de mi dispositivo: modelo, versión de Android y almacenamiento libre") }
                Suggestion(Icons.Outlined.Android, "Crear APK") { onSuggestion("Quiero crear una app Android sencilla que ") }
            }
        } else {
            Text(
                "Inicia sesión con tu cuenta Claude para empezar",
                color = c.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(14.dp))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(c.accent).clickable(onClick = onOpenSettings)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) { Text("Iniciar sesión", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun Suggestion(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalClaude.current
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(17.dp))
        Text("  $label", color = c.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun NoticeBar(text: String, canRetry: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalClaude.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.composer)
            .border(1.dp, c.danger.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (canRetry) {
            TextButton(onClick = { onDismiss(); onRetry() }) { Text("Reintentar", color = c.accent) }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, "Cerrar", tint = c.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

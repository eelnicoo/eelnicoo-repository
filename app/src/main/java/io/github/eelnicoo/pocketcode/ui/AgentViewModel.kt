package io.github.eelnicoo.pocketcode.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eelnicoo.pocketcode.api.AgentEvent
import io.github.eelnicoo.pocketcode.api.ApiMessage
import io.github.eelnicoo.pocketcode.api.ClaudeClient
import io.github.eelnicoo.pocketcode.api.ContentBlock
import io.github.eelnicoo.pocketcode.build.ApkBuilder
import io.github.eelnicoo.pocketcode.data.Settings
import io.github.eelnicoo.pocketcode.oauth.ClaudeOAuth
import io.github.eelnicoo.pocketcode.tools.BuildApkTool
import io.github.eelnicoo.pocketcode.tools.EditFileTool
import io.github.eelnicoo.pocketcode.tools.GrepTool
import io.github.eelnicoo.pocketcode.tools.ListDirTool
import io.github.eelnicoo.pocketcode.tools.ReadFileTool
import io.github.eelnicoo.pocketcode.tools.ShellTool
import io.github.eelnicoo.pocketcode.tools.ToolRegistry
import io.github.eelnicoo.pocketcode.tools.WriteFileTool
import io.github.eelnicoo.pocketcode.tools.Workspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** A UI-facing chat entry (what the user sees), distinct from the raw API history. */
sealed interface UiItem {
    data class User(val text: String) : UiItem
    data class Assistant(val text: String) : UiItem
    data class Thinking(val text: String) : UiItem
    data class ToolCall(val name: String, val input: String, var output: String? = null, var error: Boolean = false) : UiItem
    data class Notice(val text: String) : UiItem
}

class AgentViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings.get(app)
    val oauth = ClaudeOAuth(settings)

    private val workspace = Workspace {
        val dir = settings.workingDir.ifBlank { File(app.filesDir, "workspace").absolutePath }
        File(dir).apply { if (!exists()) mkdirs() }
    }
    private val apkBuilder = ApkBuilder(app)

    private val registry = ToolRegistry(
        listOf(
            ReadFileTool(workspace),
            WriteFileTool(workspace),
            EditFileTool(workspace),
            ListDirTool(workspace),
            GrepTool(workspace),
            ShellTool(useShizuku = { settings.useShizuku }, workingDir = { workspace.resolve(".") }),
            BuildApkTool(workspace, apkBuilder),
        )
    )
    private val client = ClaudeClient(oauth, registry)

    private val history = mutableListOf<ApiMessage>()

    private val _items = MutableStateFlow<List<UiItem>>(emptyList())
    val items: StateFlow<List<UiItem>> = _items

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val settingsState get() = settings.state

    private fun push(item: UiItem) { _items.value = _items.value + item }
    private fun replaceLast(transform: (UiItem) -> UiItem) {
        val list = _items.value.toMutableList()
        if (list.isNotEmpty()) { list[list.lastIndex] = transform(list.last()); _items.value = list }
    }

    fun send(text: String) {
        if (text.isBlank() || _busy.value) return
        push(UiItem.User(text))
        history.add(ApiMessage("user", listOf(ContentBlock(type = "text", text = text))))
        _busy.value = true

        viewModelScope.launch {
            var assistantOpen = false
            client.runTurn(settings.model, settings.maxTokens, history).collect { ev ->
                when (ev) {
                    is AgentEvent.AssistantTextDelta -> {
                        if (!assistantOpen) { push(UiItem.Assistant(ev.text)); assistantOpen = true }
                        else replaceLast { (it as UiItem.Assistant).copy(text = it.text + ev.text) }
                    }
                    is AgentEvent.ThinkingDelta -> { /* reasoning hidden by default */ }
                    is AgentEvent.ToolStarted -> {
                        assistantOpen = false
                        push(UiItem.ToolCall(ev.name, ev.input))
                    }
                    is AgentEvent.ToolFinished -> {
                        // attach output to the matching last tool-call item
                        val list = _items.value.toMutableList()
                        val idx = list.indexOfLast { it is UiItem.ToolCall && (it as UiItem.ToolCall).output == null }
                        if (idx >= 0) {
                            val tc = list[idx] as UiItem.ToolCall
                            list[idx] = tc.copy(output = ev.output.take(4000), error = ev.isError)
                            _items.value = list
                        }
                    }
                    is AgentEvent.Error -> push(UiItem.Notice("⚠️ ${ev.message}"))
                    AgentEvent.TurnComplete -> {}
                }
            }
            _busy.value = false
        }
    }

    fun clear() {
        history.clear()
        _items.value = emptyList()
    }
}

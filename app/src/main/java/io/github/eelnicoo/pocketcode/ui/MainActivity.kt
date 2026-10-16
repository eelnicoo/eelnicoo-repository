package io.github.eelnicoo.pocketcode.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
fun App() {
    val vm: ChatViewModel = viewModel()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    var onSettings by rememberSaveable { mutableStateOf(false) }
    PocketTheme(themeMode = settings.themeMode, chatFont = settings.chatFont) {
        if (onSettings) {
            BackHandler { onSettings = false }
            SettingsScreen(vm = vm, onBack = { onSettings = false })
        } else {
            ChatScreen(vm = vm, onOpenSettings = { onSettings = true })
        }
    }
}

package io.github.eelnicoo.pocketcode.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
    val dark = isDarkTheme(settings.themeMode)

    // Bar icons must follow the in-app theme, which can differ from the system one.
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(dark, activity) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    PocketTheme(dark = dark, chatFont = settings.chatFont) {
        if (onSettings) {
            BackHandler { onSettings = false }
            SettingsScreen(vm = vm, onBack = { onSettings = false })
        } else {
            ChatScreen(vm = vm, onOpenSettings = { onSettings = true })
        }
    }
}

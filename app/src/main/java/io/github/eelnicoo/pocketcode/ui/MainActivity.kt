package io.github.eelnicoo.pocketcode.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

private val ClaudeOrange = Color(0xFFD97757)

@Composable
fun App() {
    val colors = darkColorScheme(
        primary = ClaudeOrange,
        secondary = ClaudeOrange,
    )
    MaterialTheme(colorScheme = colors) {
        Surface {
            val vm: AgentViewModel = viewModel()
            var screen by remember { mutableStateOf(Screen.Chat) }
            when (screen) {
                Screen.Chat -> ChatScreen(
                    vm = vm,
                    onOpenSettings = { screen = Screen.Settings },
                )
                Screen.Settings -> SettingsScreen(
                    vm = vm,
                    onBack = { screen = Screen.Chat },
                )
            }
        }
    }
}

enum class Screen { Chat, Settings }

package dev.nico.mtsamp4

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Si Android no dejó arrancar el servicio en segundo plano, se arranca ahora.
        if (Conversions.hasPending() && !ConversionService.isRunning) {
            ConversionService.start(this)
        }
    }

    /** Vídeos que llegan con «Compartir» o «Abrir con». */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.streamUri()) + intent.clipUris()
            Intent.ACTION_SEND_MULTIPLE -> intent.streamUris() + intent.clipUris()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }.distinct()
        if (uris.isNotEmpty()) Conversions.enqueue(this, uris)
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }?.filterNotNull() ?: emptyList()

    private fun Intent.clipUris(): List<Uri> {
        val clip = clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }
}

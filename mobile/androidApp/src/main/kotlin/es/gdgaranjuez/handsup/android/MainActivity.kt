package es.gdgaranjuez.handsup.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import es.gdgaranjuez.handsup.AppConfig
import es.gdgaranjuez.handsup.HandsupApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val config = AppConfig(
            useEmulator = BuildConfig.USE_EMULATOR,
            emulatorHost = BuildConfig.EMULATOR_HOST,
            defaultEventId = BuildConfig.DEFAULT_EVENT,
            webBaseUrl = BuildConfig.WEB_BASE_URL,
            googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { null },
        )
        val services = AndroidServices(this)
        setContent { HandsupApp(config, services) }
    }
}

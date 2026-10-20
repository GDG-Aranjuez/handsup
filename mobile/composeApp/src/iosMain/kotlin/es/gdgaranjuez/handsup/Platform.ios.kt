package es.gdgaranjuez.handsup.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system back button; screens carry an explicit back control.
}

actual fun Modifier.exposeTestTags(): Modifier = this

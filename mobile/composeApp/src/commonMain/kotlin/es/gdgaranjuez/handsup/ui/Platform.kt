package es.gdgaranjuez.handsup.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** System back gesture/button. A no-op on iOS, where screens have an explicit back button. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Lets UI tests (UiAutomator) find composables by test tag on Android; a no-op elsewhere. */
expect fun Modifier.exposeTestTags(): Modifier

package es.gdgaranjuez.handsup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.exposeTestTags(): Modifier = semantics { testTagsAsResourceId = true }

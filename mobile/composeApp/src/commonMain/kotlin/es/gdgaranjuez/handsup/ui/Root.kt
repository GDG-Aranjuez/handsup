package es.gdgaranjuez.handsup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.gdgaranjuez.handsup.AppModel
import es.gdgaranjuez.handsup.Route

@Composable
fun HandsupRoot(app: AppModel) {
    HandsupTheme(app.branding) {
        Scaffold(
            modifier = Modifier.fillMaxSize().exposeTestTags(),
            snackbarHost = { SnackbarHost(app.snackbar) },
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                when (val route = app.nav.current) {
                    Route.Home -> HomeScreen(app)
                    is Route.Event -> EventScreen(app, route)
                    is Route.Talk -> TalkScreen(app, route)
                    Route.AdminHome -> AdminHomeScreen(app)
                    is Route.AdminEvent -> AdminEventScreen(app, route)
                    is Route.AdminQueue -> AdminQueueScreen(app, route)
                    is Route.AdminSetup -> AdminSetupScreen(app, route)
                }
            }
        }
    }
}

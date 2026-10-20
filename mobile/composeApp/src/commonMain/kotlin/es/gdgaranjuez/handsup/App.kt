package es.gdgaranjuez.handsup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import com.russhwolf.settings.Settings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import es.gdgaranjuez.handsup.data.FirestoreBackend
import es.gdgaranjuez.handsup.data.QaBackend
import es.gdgaranjuez.handsup.domain.Branding
import es.gdgaranjuez.handsup.i18n.I18n
import es.gdgaranjuez.handsup.i18n.Lang
import es.gdgaranjuez.handsup.ui.HandsupRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Per-build configuration, supplied by the platform entry point. */
data class AppConfig(
    /** Talk to the local Firebase emulators (firebase/ → pnpm emulators). */
    val useEmulator: Boolean = false,
    val emulatorHost: String = "127.0.0.1",
    /** Event opened from the home screen. Empty shows only the join-by-slug form. */
    val defaultEventId: String = "",
    /** Where the web app lives; used for links to share (join, speaker, stage). */
    val webBaseUrl: String = "https://gdg-aranjuez.github.io/handsup",
    /** OAuth web client id for Google sign-in on Android. Null hides the button. */
    val googleWebClientId: String? = null,
)

/** What only the host platform can do. */
interface PlatformServices {
    fun systemLanguage(): String
    fun share(text: String)
    fun openUrl(url: String)
    /** Runs the native Google sign-in and returns an ID token, or null if cancelled/unsupported. */
    suspend fun googleIdToken(webClientId: String): String?
}

sealed interface Route {
    data object Home : Route
    data class Event(val eventId: String, val browse: Boolean = false) : Route
    data class Talk(val eventId: String, val code: String) : Route
    data object AdminHome : Route
    data class AdminEvent(val eventId: String) : Route
    data class AdminQueue(val eventId: String, val code: String) : Route
    data class AdminSetup(val eventId: String) : Route
}

class Navigator(start: Route = Route.Home) {
    val stack = mutableStateListOf(start)
    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        stack.add(route)
    }

    fun replace(route: Route) {
        stack[stack.lastIndex] = route
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun popTo(route: Route) {
        val i = stack.indexOfLast { it == route }
        if (i >= 0) while (stack.lastIndex > i) stack.removeAt(stack.lastIndex)
    }
}

class AppModel(
    val config: AppConfig,
    val services: PlatformServices,
    val backend: QaBackend,
    val scope: CoroutineScope,
    private val settings: Settings = Settings(),
) {
    val nav = Navigator()
    val snackbar = SnackbarHostState()

    /** Branding of the event being viewed; drives the theme. */
    var branding by mutableStateOf(Branding())

    init {
        I18n.lang = Lang.fromCode(settings.getStringOrNull(KEY_LANG)) ?: Lang.fromSystem(services.systemLanguage())
    }

    fun toast(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun setLang(lang: Lang) {
        I18n.lang = lang
        settings.putString(KEY_LANG, lang.code)
    }

    fun rememberedRoom(eventId: String): String? = settings.getStringOrNull("room.$eventId")
    fun rememberRoom(eventId: String, roomId: String) = settings.putString("room.$eventId", roomId)

    fun joinUrl(eventId: String) = "${config.webBaseUrl}/e/$eventId"
    fun talkUrl(eventId: String, code: String) = "${config.webBaseUrl}/e/$eventId/t/$code"
    fun speakerUrl(eventId: String, code: String, key: String) = "${config.webBaseUrl}/speaker/$eventId/$code/$key"
    fun stageUrl(eventId: String, roomId: String) = "${config.webBaseUrl}/stage/$eventId/$roomId"

    private companion object {
        const val KEY_LANG = "lang"
    }
}

private var emulatorConfigured = false

/** Must run before the first Firebase call. Safe to call more than once per process. */
private fun configureEmulator(config: AppConfig) {
    if (!config.useEmulator || emulatorConfigured) return
    emulatorConfigured = true
    Firebase.auth.useEmulator(config.emulatorHost, 9099)
    Firebase.firestore.useEmulator(config.emulatorHost, 8080)
}

/** The whole app. Platform entry points call this. */
@Composable
fun HandsupApp(config: AppConfig, services: PlatformServices) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val model = remember {
        configureEmulator(config)
        AppModel(config, services, FirestoreBackend(), scope)
    }
    HandsupRoot(model)
}

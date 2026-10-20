package es.gdgaranjuez.handsup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import es.gdgaranjuez.handsup.AppModel
import es.gdgaranjuez.handsup.domain.Branding
import es.gdgaranjuez.handsup.domain.EventDoc
import es.gdgaranjuez.handsup.domain.Room
import es.gdgaranjuez.handsup.domain.Talk
import es.gdgaranjuez.handsup.domain.TalkState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Live view of one Event (document, rooms, talks) shared by the screens that show it. */
class EventState(val eventId: String) {
    var event by mutableStateOf<EventDoc?>(null)
    var rooms by mutableStateOf<List<Room>>(emptyList())
    var talks by mutableStateOf<List<Talk>>(emptyList())
    var loaded by mutableStateOf(false)
    /** null until the gate ran; false when the signed-in user is not allowed to see this event. */
    var allowed by mutableStateOf<Boolean?>(null)
    var error by mutableStateOf<String?>(null)

    fun talk(code: String): Talk? = talks.firstOrNull { it.id == code }
    fun room(id: String): Room? = rooms.firstOrNull { it.id == id }
    val liveTalks: List<Talk> get() = talks.filter { it.state == TalkState.LIVE }
}

/**
 * Subscribes to the event for as long as the caller is composed and themes the app with its branding.
 * [gate] runs first (e.g. the organizer check); when it returns false nothing is subscribed.
 */
@Composable
fun rememberEventState(app: AppModel, eventId: String, gate: suspend () -> Boolean = { true }): EventState {
    val state = remember(eventId) { EventState(eventId) }
    LaunchedEffect(eventId) {
        try {
            app.backend.ensureAnonymous()
            state.allowed = gate()
            if (state.allowed != true) {
                state.loaded = true
                return@LaunchedEffect
            }
            coroutineScope {
                launch {
                    app.backend.watchEvent(eventId).catch { state.error = it.message; state.loaded = true }
                        .collect { state.event = it; state.loaded = true }
                }
                launch { app.backend.watchRooms(eventId).catch { }.collect { state.rooms = it } }
                launch { app.backend.watchTalks(eventId).catch { }.collect { state.talks = it } }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            state.error = e.message
            state.loaded = true
        }
    }
    LaunchedEffect(state.event) { app.branding = state.event?.branding ?: Branding() }
    DisposableEffect(eventId) { onDispose { app.branding = Branding() } }
    return state
}

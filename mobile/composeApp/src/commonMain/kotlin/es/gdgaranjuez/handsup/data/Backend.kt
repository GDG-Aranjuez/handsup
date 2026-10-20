package es.gdgaranjuez.handsup.data

import es.gdgaranjuez.handsup.domain.Attendee
import es.gdgaranjuez.handsup.domain.Delivery
import es.gdgaranjuez.handsup.domain.EventDoc
import es.gdgaranjuez.handsup.domain.ImportPlan
import es.gdgaranjuez.handsup.domain.Question
import es.gdgaranjuez.handsup.domain.QuestionStatus
import es.gdgaranjuez.handsup.domain.Room
import es.gdgaranjuez.handsup.domain.Talk
import kotlinx.coroutines.flow.Flow

data class AuthUser(val uid: String, val email: String?, val emailVerified: Boolean, val isAnonymous: Boolean)

enum class QuestionScope { PUBLIC, MINE, ALL }

data class ExportRow(
    val talk: String,
    val talkTitle: String,
    val pseudonym: String,
    val text: String,
    val delivery: Delivery,
    val status: QuestionStatus,
    val votes: Int,
    val createdAtMs: Long,
    val answer: String,
)

/** Permission failures surface as [Kind.DENIED]. */
class BackendError(val kind: Kind, message: String = "") : Exception(message) {
    enum class Kind { DENIED, OFFLINE, NOT_FOUND, UNKNOWN }
}

/**
 * Everything the UI needs from a backend. Firestore is the first implementation (ADR 0001);
 * the web app implements the same contract in TypeScript (web/src/lib/backend/types.ts).
 */
interface QaBackend {
    // auth
    val authState: Flow<AuthUser?>
    suspend fun ensureAnonymous(): AuthUser
    suspend fun signInEmail(email: String, password: String): AuthUser
    /** Creates the account and sends a verification email; the rules need a verified email. */
    suspend fun registerEmail(email: String, password: String): AuthUser
    suspend fun signInGoogle(idToken: String): AuthUser
    suspend fun signOut()
    suspend fun isSuperadmin(): Boolean
    suspend fun isOrganizer(eventId: String): Boolean

    // reading
    suspend fun listEvents(): List<EventDoc>
    fun watchEvent(eventId: String): Flow<EventDoc?>
    fun watchRooms(eventId: String): Flow<List<Room>>
    fun watchTalks(eventId: String): Flow<List<Talk>>
    fun watchQuestions(eventId: String, talkId: String, scope: QuestionScope, uid: String?): Flow<List<Question>>
    fun watchMyVotes(eventId: String, talkId: String, uid: String): Flow<Set<String>>
    fun watchOrganizers(eventId: String): Flow<List<String>>

    // attendee
    suspend fun joinEvent(eventId: String, uid: String): Attendee
    suspend fun changePseudonym(eventId: String, uid: String, name: String? = null): String
    suspend fun ask(
        eventId: String, talkId: String, uid: String, pseudonym: String,
        text: String, delivery: Delivery, preApproval: Boolean,
    )
    suspend fun vote(eventId: String, talkId: String, questionId: String, uid: String)
    suspend fun unvote(eventId: String, talkId: String, questionId: String, uid: String)
    suspend fun withdrawQuestion(eventId: String, talkId: String, questionId: String)
    suspend fun editQuestion(eventId: String, talkId: String, questionId: String, text: String?, delivery: Delivery?)

    // organizer
    suspend fun setQuestionStatus(eventId: String, talkId: String, questionId: String, status: QuestionStatus)
    /** Moves a question on stage and marks the previously on-stage ones answered. */
    suspend fun putOnStage(eventId: String, talkId: String, questionId: String, previousIds: List<String>)
    suspend fun goLive(eventId: String, talkId: String, roomId: String)
    suspend fun endTalk(eventId: String, talkId: String, roomId: String)
    suspend fun setQuestionsClosed(eventId: String, talkId: String, closed: Boolean)
    suspend fun saveTalk(eventId: String, talk: Talk)
    suspend fun deleteTalk(eventId: String, talkId: String)
    suspend fun saveRoom(eventId: String, room: Room)
    suspend fun deleteRoom(eventId: String, roomId: String)
    suspend fun saveEvent(event: EventDoc)
    suspend fun createEvent(event: EventDoc)
    suspend fun deleteEvent(eventId: String)
    suspend fun addOrganizer(eventId: String, email: String)
    suspend fun removeOrganizer(eventId: String, email: String)
    suspend fun importPlan(eventId: String, plan: ImportPlan)
    /** The speaker link secret for a talk, created if needed. */
    suspend fun speakerKey(eventId: String, talkId: String): String
    suspend fun exportQuestions(eventId: String): List<ExportRow>
    /** Removes attendee identifiers (uids, votes, profiles, claims). Keeps pseudonym + text. */
    suspend fun purgeIdentities(eventId: String): Int
}

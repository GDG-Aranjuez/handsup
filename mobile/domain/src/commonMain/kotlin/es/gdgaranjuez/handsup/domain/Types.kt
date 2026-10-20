package es.gdgaranjuez.handsup.domain

/** How an attendee wants a question handled: take the mic, or have the host read it. */
enum class Delivery(val wire: String) {
    SPEAK("speak"),
    READ("read");

    companion object {
        fun fromWire(value: String?): Delivery = entries.firstOrNull { it.wire == value } ?: READ
    }
}

enum class QuestionStatus(val wire: String) {
    PENDING("pending"),
    OPEN("open"),
    ONSTAGE("onstage"),
    ANSWERED("answered"),
    FOLLOWUP("followup"),
    HIDDEN("hidden"),
    WITHDRAWN("withdrawn");

    companion object {
        fun fromWire(value: String?): QuestionStatus = entries.firstOrNull { it.wire == value } ?: OPEN
        /** What other attendees may see (matches the Firestore rules). */
        val visible: List<QuestionStatus> = listOf(OPEN, ONSTAGE, ANSWERED, FOLLOWUP)
    }
}

enum class TalkState(val wire: String) {
    SCHEDULED("scheduled"),
    LIVE("live"),
    ENDED("ended");

    companion object {
        fun fromWire(value: String?): TalkState = entries.firstOrNull { it.wire == value } ?: SCHEDULED
    }
}

enum class SortMode(val wire: String) {
    VOTES("votes"),
    TIME("time");

    companion object {
        fun fromWire(value: String?): SortMode = entries.firstOrNull { it.wire == value } ?: VOTES
    }
}

data class Branding(
    val primary: String = DEFAULT_PRIMARY,
    val accent: String = DEFAULT_ACCENT,
    val logo: String? = null,
    val tagline: String? = null,
) {
    companion object {
        const val DEFAULT_PRIMARY = "#d91e5b"
        const val DEFAULT_ACCENT = "#1e6ad9"
    }
}

data class EventSettings(
    val voting: Boolean = true,
    val defaultSort: SortMode = SortMode.VOTES,
    val preApproval: Boolean = false,
    val graceMin: Int = 10,
)

data class EventDoc(
    val id: String,
    val name: String,
    val branding: Branding = Branding(),
    val settings: EventSettings = EventSettings(),
)

data class Room(val id: String, val name: String, val liveTalkId: String? = null, val order: Int = 0)

/** Times are epoch milliseconds. [id] is the Talk code. */
data class Talk(
    val id: String,
    val title: String,
    val speakers: String,
    val roomId: String,
    val startMs: Long,
    val endMs: Long,
    val state: TalkState = TalkState.SCHEDULED,
    val questionsClosed: Boolean = false,
    val endedAtMs: Long? = null,
)

data class Answer(val text: String, val atMs: Long)

data class Question(
    val id: String,
    val text: String,
    val delivery: Delivery,
    val authorUid: String,
    val pseudonym: String,
    val status: QuestionStatus,
    val votes: Int,
    val createdAtMs: Long,
    val answer: Answer? = null,
)

data class Attendee(val uid: String, val pseudonym: String)

const val MAX_QUESTION_LENGTH = 280
const val QUESTION_COOLDOWN_MS = 31_000L // server rule is 30 s; one extra second absorbs clock skew

/** The Talk accepts questions and votes: live, or ended within the grace period. */
fun Talk.acceptsQuestions(graceMin: Int, nowMs: Long): Boolean {
    if (questionsClosed) return false
    return when (state) {
        TalkState.LIVE -> true
        TalkState.ENDED -> endedAtMs != null && nowMs < endedAtMs + graceMin * 60_000L
        TalkState.SCHEDULED -> false
    }
}

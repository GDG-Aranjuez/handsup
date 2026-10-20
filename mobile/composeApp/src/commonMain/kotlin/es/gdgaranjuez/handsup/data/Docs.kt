package es.gdgaranjuez.handsup.data

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.Serializable

// Wire shapes of the Firestore documents (docs/data-model.md). Optional fields default to null so
// they are omitted on write (encodeDefaults = false): the rules reject explicit nulls for some.

@Serializable
internal data class BrandingWire(
    val primary: String,
    val accent: String,
    val logo: String? = null,
    val tagline: String? = null,
)

@Serializable
internal data class SettingsWire(
    val voting: Boolean,
    val defaultSort: String,
    val preApproval: Boolean,
    val graceMin: Int,
)

@Serializable
internal data class EventWrite(
    val name: String,
    val branding: BrandingWire,
    val settings: SettingsWire,
    val createdAt: BaseTimestamp? = null,
)

@Serializable
internal data class EventRead(
    val name: String = "",
    val branding: BrandingWire = BrandingWire("#d91e5b", "#1e6ad9"),
    val settings: SettingsWire = SettingsWire(true, "votes", false, 10),
)

@Serializable
internal data class RoomWire(val name: String, val liveTalkId: String? = null, val order: Int = 0)

@Serializable
internal data class TalkWrite(
    val title: String,
    val speakers: String,
    val roomId: String,
    val start: Timestamp,
    val end: Timestamp,
    val state: String,
    val questionsClosed: Boolean,
    val endedAt: Timestamp? = null,
)

@Serializable
internal data class TalkRead(
    val title: String = "",
    val speakers: String = "",
    val roomId: String = "",
    val start: Timestamp? = null,
    val end: Timestamp? = null,
    val state: String = "scheduled",
    val questionsClosed: Boolean = false,
    val endedAt: Timestamp? = null,
)

@Serializable
internal data class QuestionWrite(
    val text: String,
    val delivery: String,
    val authorUid: String,
    val pseudonym: String,
    val status: String,
    val votes: Int,
    val createdAt: BaseTimestamp,
)

@Serializable
internal data class AnswerRead(val text: String = "", val at: Timestamp? = null)

@Serializable
internal data class QuestionRead(
    val text: String = "",
    val delivery: String = "read",
    val authorUid: String = "",
    val pseudonym: String = "",
    val status: String = "open",
    // counters changed with increment() may come back as doubles from some SDKs
    val votes: Double = 0.0,
    val createdAt: Timestamp? = null,
    val answer: AnswerRead? = null,
)

@Serializable
internal data class AttendeeWire(val pseudonym: String, val lastQuestionAt: Timestamp, val qCount: Int)

@Serializable
internal data class AttendeeRead(val pseudonym: String = "", val qCount: Int = 0)

@Serializable internal data class ClaimWire(val uid: String, val name: String)
@Serializable internal data class VoteWire(val uid: String, val q: String)
@Serializable internal data class VoteRead(val uid: String = "", val q: String = "")
@Serializable internal data class PrefWire(val pseudonym: String)
@Serializable internal data class PrefRead(val pseudonym: String? = null)
@Serializable internal data class SpeakerKeyWire(val eventId: String, val talkId: String)
@Serializable internal data class SecretWire(val speakerKey: String)
@Serializable internal data class SecretRead(val speakerKey: String = "")
@Serializable internal data class OrganizerWire(val addedAt: BaseTimestamp)
@Serializable internal data class ExportTalkRead(val title: String = "")

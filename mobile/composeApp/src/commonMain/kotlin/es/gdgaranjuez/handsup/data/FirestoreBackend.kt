package es.gdgaranjuez.handsup.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.WriteBatch
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.firestore
import es.gdgaranjuez.handsup.currentTimeMillis
import es.gdgaranjuez.handsup.domain.Answer
import es.gdgaranjuez.handsup.domain.Attendee
import es.gdgaranjuez.handsup.domain.Branding
import es.gdgaranjuez.handsup.domain.Delivery
import es.gdgaranjuez.handsup.domain.EventDoc
import es.gdgaranjuez.handsup.domain.EventSettings
import es.gdgaranjuez.handsup.domain.ImportPlan
import es.gdgaranjuez.handsup.domain.Question
import es.gdgaranjuez.handsup.domain.QuestionStatus
import es.gdgaranjuez.handsup.domain.Room
import es.gdgaranjuez.handsup.domain.SortMode
import es.gdgaranjuez.handsup.domain.Talk
import es.gdgaranjuez.handsup.domain.TalkState
import es.gdgaranjuez.handsup.domain.cleanPseudonym
import es.gdgaranjuez.handsup.domain.generatePseudonym
import es.gdgaranjuez.handsup.domain.generateSpeakerKey
import es.gdgaranjuez.handsup.domain.pseudonymKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private fun Timestamp?.ms(): Long = if (this == null) 0L else seconds * 1000L + nanoseconds / 1_000_000L
private fun ms(v: Long): Timestamp = Timestamp(v.floorDiv(1000L), (v.mod(1000L) * 1_000_000L).toInt())

private fun Throwable.toBackendError(): BackendError {
    if (this is BackendError) return this
    val denied = (this is FirebaseFirestoreException && code == FirestoreExceptionCode.PERMISSION_DENIED) ||
        message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
        message?.contains("Missing or insufficient permissions", ignoreCase = true) == true
    return when {
        denied -> BackendError(BackendError.Kind.DENIED, message.orEmpty())
        message?.contains("UNAVAILABLE", ignoreCase = true) == true -> BackendError(BackendError.Kind.OFFLINE, message.orEmpty())
        else -> BackendError(BackendError.Kind.UNKNOWN, message.orEmpty())
    }
}

private inline fun <T> guard(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    throw e.toBackendError()
}

private fun FirebaseUser.toAuthUser() = AuthUser(uid, email, isEmailVerified, isAnonymous)

/** Firestore implementation of [QaBackend], enforced server-side by firebase/firestore.rules. */
class FirestoreBackend : QaBackend {
    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore
    private val anonymousLock = Mutex()

    private fun event(e: String) = db.collection("events").document(e)
    private fun talkRef(e: String, t: String) = event(e).collection("talks").document(t)
    private fun questions(e: String, t: String) = talkRef(e, t).collection("questions")
    private fun questionRef(e: String, t: String, q: String) = questions(e, t).document(q)
    private fun attendeeRef(e: String, u: String) = event(e).collection("attendees").document(u)
    private fun claimRef(e: String, name: String) = event(e).collection("pseudonyms").document(pseudonymKey(name))
    private fun voteRef(e: String, t: String, u: String, q: String) =
        talkRef(e, t).collection("votes").document("${u}_$q")

    // ---------- auth ----------

    override val authState: Flow<AuthUser?> = Firebase.auth.authStateChanged.map { it?.toAuthUser() }

    override suspend fun ensureAnonymous(): AuthUser = anonymousLock.withLock {
        // Single-flight: two callers racing would otherwise create two anonymous users.
        auth.currentUser?.let { return it.toAuthUser() }
        guard { auth.signInAnonymously().user!!.toAuthUser() }
    }

    override suspend fun signInEmail(email: String, password: String): AuthUser =
        guard { auth.signInWithEmailAndPassword(email.trim(), password).user!!.toAuthUser() }

    override suspend fun registerEmail(email: String, password: String): AuthUser = guard {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).user!!
        user.sendEmailVerification()
        user.toAuthUser()
    }

    override suspend fun signInGoogle(idToken: String): AuthUser =
        guard { auth.signInWithCredential(GoogleAuthProvider.credential(idToken, null)).user!!.toAuthUser() }

    override suspend fun signOut() = auth.signOut()

    override suspend fun isSuperadmin(): Boolean {
        val u = auth.currentUser ?: return false
        if (u.isAnonymous) return false
        return try {
            db.collection("config").document("superadmins").get()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun isOrganizer(eventId: String): Boolean {
        val u = auth.currentUser ?: return false
        val email = u.email
        if (u.isAnonymous || email == null || !u.isEmailVerified) return false
        if (isSuperadmin()) return true
        return try {
            event(eventId).collection("organizers").document(email.lowercase()).get().exists
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }

    // ---------- reading ----------

    private fun EventRead.toDomain(id: String) = EventDoc(
        id = id,
        name = name,
        branding = Branding(branding.primary, branding.accent, branding.logo, branding.tagline),
        settings = EventSettings(
            settings.voting, SortMode.fromWire(settings.defaultSort), settings.preApproval, settings.graceMin,
        ),
    )

    override suspend fun listEvents(): List<EventDoc> = guard {
        db.collection("events").get().documents.map { it.data<EventRead>().toDomain(it.id) }
    }

    override fun watchEvent(eventId: String): Flow<EventDoc?> =
        event(eventId).snapshots.map { if (it.exists) it.data<EventRead>().toDomain(it.id) else null }
            .catch { throw it.toBackendError() }

    override fun watchRooms(eventId: String): Flow<List<Room>> =
        event(eventId).collection("rooms").snapshots.map { snap ->
            snap.documents.map {
                val r = it.data<RoomWire>()
                Room(it.id, r.name, r.liveTalkId, r.order)
            }.sortedWith(compareBy<Room> { it.order }.thenBy { it.name })
        }.catch { throw it.toBackendError() }

    override fun watchTalks(eventId: String): Flow<List<Talk>> =
        event(eventId).collection("talks").snapshots.map { snap ->
            snap.documents.map {
                val t = it.data<TalkRead>()
                Talk(
                    id = it.id, title = t.title, speakers = t.speakers, roomId = t.roomId,
                    startMs = t.start.ms(), endMs = t.end.ms(), state = TalkState.fromWire(t.state),
                    questionsClosed = t.questionsClosed, endedAtMs = t.endedAt?.ms(),
                )
            }.sortedBy { it.startMs }
        }.catch { throw it.toBackendError() }

    private fun mapQuestion(id: String, q: QuestionRead, nowMs: Long) = Question(
        id = id,
        text = q.text,
        delivery = Delivery.fromWire(q.delivery),
        authorUid = q.authorUid,
        pseudonym = q.pseudonym,
        status = QuestionStatus.fromWire(q.status),
        votes = q.votes.toInt(),
        // a just-written question has no server timestamp yet: show it as "now"
        createdAtMs = q.createdAt?.ms() ?: nowMs,
        answer = q.answer?.let { Answer(it.text, it.at.ms()) },
    )

    override fun watchQuestions(
        eventId: String, talkId: String, scope: QuestionScope, uid: String?,
    ): Flow<List<Question>> {
        val col = questions(eventId, talkId)
        val query = when (scope) {
            QuestionScope.PUBLIC -> col.where { "status" inArray QuestionStatus.visible.map { it.wire } }
            QuestionScope.MINE -> col.where { "authorUid" equalTo (uid ?: "") }
            QuestionScope.ALL -> col
        }
        return query.snapshots.map { snap ->
            val now = currentTimeMillis()
            snap.documents.map { mapQuestion(it.id, it.data<QuestionRead>(), now) }
        }.catch { throw it.toBackendError() }
    }

    override fun watchMyVotes(eventId: String, talkId: String, uid: String): Flow<Set<String>> =
        talkRef(eventId, talkId).collection("votes").where { "uid" equalTo uid }.snapshots
            .map { snap -> snap.documents.map { it.data<VoteRead>().q }.toSet() }
            .catch { throw it.toBackendError() }

    override fun watchOrganizers(eventId: String): Flow<List<String>> =
        event(eventId).collection("organizers").snapshots.map { snap -> snap.documents.map { it.id }.sorted() }
            .catch { emit(emptyList()) }

    // ---------- attendee ----------

    // Claim `name` and point the attendee at it in one atomic batch. A clash on the claim doc
    // surfaces as a permission error and nothing is written.
    private suspend fun claimAndSet(eventId: String, uid: String, name: String, existing: String?) {
        val b = db.batch()
        val sameKey = existing != null && pseudonymKey(existing) == pseudonymKey(name)
        if (!sameKey) b.set(claimRef(eventId, name), ClaimWire(uid, name))
        if (existing == null) {
            b.set(attendeeRef(eventId, uid), AttendeeWire(name, ms(0), 0))
        } else {
            b.update(attendeeRef(eventId, uid), "pseudonym" to name)
            if (!sameKey) b.delete(claimRef(eventId, existing))
        }
        b.commit()
    }

    private suspend fun rememberPseudonym(uid: String, name: String) {
        try {
            db.collection("users").document(uid).set(PrefWire(name))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            /* preference is a convenience only */
        }
    }

    override suspend fun joinEvent(eventId: String, uid: String): Attendee {
        val existing = guard { attendeeRef(eventId, uid).get() }
        if (existing.exists) return Attendee(uid, existing.data<AttendeeRead>().pseudonym)

        val preferred = try {
            val pref = db.collection("users").document(uid).get()
            if (pref.exists) pref.data<PrefRead>().pseudonym else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        var name = preferred ?: generatePseudonym()
        repeat(12) {
            try {
                claimAndSet(eventId, uid, name, null)
                if (preferred == null) rememberPseudonym(uid, name)
                return Attendee(uid, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e.toBackendError().kind != BackendError.Kind.DENIED) throw e.toBackendError()
                name = generatePseudonym() // clash: reroll for this event only
            }
        }
        throw BackendError(BackendError.Kind.DENIED, "could not claim a pseudonym")
    }

    override suspend fun changePseudonym(eventId: String, uid: String, name: String?): String {
        val cur = guard { attendeeRef(eventId, uid).get() }
        if (!cur.exists) throw BackendError(BackendError.Kind.NOT_FOUND, "join the event first")
        val existing = cur.data<AttendeeRead>().pseudonym
        if (name != null) {
            val clean = cleanPseudonym(name)
            if (clean.length < 2) throw BackendError(BackendError.Kind.DENIED, "name too short")
            guard { claimAndSet(eventId, uid, clean, existing) }
            rememberPseudonym(uid, clean)
            return clean
        }
        repeat(12) {
            val candidate = generatePseudonym()
            try {
                claimAndSet(eventId, uid, candidate, existing)
                rememberPseudonym(uid, candidate)
                return candidate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e.toBackendError().kind != BackendError.Kind.DENIED) throw e.toBackendError()
            }
        }
        throw BackendError(BackendError.Kind.DENIED, "could not claim a pseudonym")
    }

    override suspend fun ask(
        eventId: String, talkId: String, uid: String, pseudonym: String,
        text: String, delivery: Delivery, preApproval: Boolean,
    ) = guard {
        val att = attendeeRef(eventId, uid).get()
        if (!att.exists) throw BackendError(BackendError.Kind.NOT_FOUND, "join the event first")
        val next = att.data<AttendeeRead>().qCount + 1
        val b = db.batch()
        b.set(
            questionRef(eventId, talkId, "${uid}_$next"),
            QuestionWrite(
                text = text, delivery = delivery.wire, authorUid = uid, pseudonym = pseudonym,
                status = if (preApproval) "pending" else "open", votes = 0, createdAt = Timestamp.ServerTimestamp,
            ),
        )
        b.update(
            attendeeRef(eventId, uid),
            "lastQuestionAt" to FieldValue.serverTimestamp,
            // explicit integer: GitLive's increment(Int) sends a double, which would break the id rule
            "qCount" to next,
        )
        b.commit()
    }

    override suspend fun vote(eventId: String, talkId: String, questionId: String, uid: String) = guard {
        val b = db.batch()
        b.set(voteRef(eventId, talkId, uid, questionId), VoteWire(uid, questionId))
        b.update(questionRef(eventId, talkId, questionId), "votes" to FieldValue.increment(1))
        b.commit()
    }

    override suspend fun unvote(eventId: String, talkId: String, questionId: String, uid: String) = guard {
        val b = db.batch()
        b.delete(voteRef(eventId, talkId, uid, questionId))
        b.update(questionRef(eventId, talkId, questionId), "votes" to FieldValue.increment(-1))
        b.commit()
    }

    override suspend fun withdrawQuestion(eventId: String, talkId: String, questionId: String) =
        guard { questionRef(eventId, talkId, questionId).update("status" to "withdrawn") }

    override suspend fun editQuestion(
        eventId: String, talkId: String, questionId: String, text: String?, delivery: Delivery?,
    ) = guard {
        val pairs = buildList {
            if (text != null) add("text" to text)
            if (delivery != null) add("delivery" to delivery.wire)
        }
        if (pairs.isNotEmpty()) questionRef(eventId, talkId, questionId).update(*pairs.toTypedArray())
    }

    // ---------- organizer ----------

    override suspend fun setQuestionStatus(eventId: String, talkId: String, questionId: String, status: QuestionStatus) =
        guard { questionRef(eventId, talkId, questionId).update("status" to status.wire) }

    override suspend fun putOnStage(eventId: String, talkId: String, questionId: String, previousIds: List<String>) = guard {
        val b = db.batch()
        previousIds.filter { it != questionId }.forEach { b.update(questionRef(eventId, talkId, it), "status" to "answered") }
        b.update(questionRef(eventId, talkId, questionId), "status" to "onstage")
        b.commit()
    }

    override suspend fun goLive(eventId: String, talkId: String, roomId: String) = guard {
        val roomRef = event(eventId).collection("rooms").document(roomId)
        val room = roomRef.get()
        val liveId = if (room.exists) room.data<RoomWire>().liveTalkId else null
        val b = db.batch()
        if (liveId != null && liveId != talkId) {
            b.update(talkRef(eventId, liveId), "state" to "ended", "endedAt" to FieldValue.serverTimestamp)
        }
        b.update(talkRef(eventId, talkId), "state" to "live", "questionsClosed" to false)
        b.update(roomRef, "liveTalkId" to talkId)
        b.commit()
    }

    override suspend fun endTalk(eventId: String, talkId: String, roomId: String) = guard {
        val roomRef = event(eventId).collection("rooms").document(roomId)
        val room = roomRef.get()
        val b = db.batch()
        b.update(talkRef(eventId, talkId), "state" to "ended", "endedAt" to FieldValue.serverTimestamp)
        if (room.exists && room.data<RoomWire>().liveTalkId == talkId) b.update(roomRef, "liveTalkId" to null)
        b.commit()
    }

    override suspend fun setQuestionsClosed(eventId: String, talkId: String, closed: Boolean) =
        guard { talkRef(eventId, talkId).update("questionsClosed" to closed) }

    private fun Talk.toWire() = TalkWrite(
        title = title, speakers = speakers, roomId = roomId, start = ms(startMs), end = ms(endMs),
        state = state.wire, questionsClosed = questionsClosed, endedAt = endedAtMs?.let(::ms),
    )

    override suspend fun saveTalk(eventId: String, talk: Talk) =
        guard { talkRef(eventId, talk.id).set(talk.toWire(), encodeDefaults = false) }

    override suspend fun deleteTalk(eventId: String, talkId: String) = guard { talkRef(eventId, talkId).delete() }

    override suspend fun saveRoom(eventId: String, room: Room) = guard {
        event(eventId).collection("rooms").document(room.id).set(RoomWire(room.name, room.liveTalkId, room.order))
    }

    override suspend fun deleteRoom(eventId: String, roomId: String) =
        guard { event(eventId).collection("rooms").document(roomId).delete() }

    private fun EventDoc.toWrite(createdAt: Boolean) = EventWrite(
        name = name,
        branding = BrandingWire(branding.primary, branding.accent, branding.logo, branding.tagline),
        settings = SettingsWire(settings.voting, settings.defaultSort.wire, settings.preApproval, settings.graceMin),
        createdAt = if (createdAt) Timestamp.ServerTimestamp else null,
    )

    override suspend fun saveEvent(event: EventDoc) = guard { event(event.id).set(event.toWrite(false), encodeDefaults = false) }
    override suspend fun createEvent(event: EventDoc) = guard { event(event.id).set(event.toWrite(true), encodeDefaults = false) }

    private suspend fun commitChunks(ops: List<(WriteBatch) -> Unit>) {
        ops.chunked(400).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { it(b) }
            b.commit()
        }
    }

    override suspend fun deleteEvent(eventId: String) = guard {
        val ops = mutableListOf<(WriteBatch) -> Unit>()
        for (t in event(eventId).collection("talks").get().documents) {
            questions(eventId, t.id).get().documents.forEach { d -> ops += { b -> b.delete(d.reference) } }
            talkRef(eventId, t.id).collection("votes").get().documents.forEach { d -> ops += { b -> b.delete(d.reference) } }
            val secret = event(eventId).collection("talkSecrets").document(t.id).get()
            if (secret.exists) {
                val key = secret.data<SecretRead>().speakerKey
                ops += { b -> b.delete(db.collection("speakerKeys").document(key)) }
                ops += { b -> b.delete(secret.reference) }
            }
            ops += { b -> b.delete(t.reference) }
        }
        for (sub in listOf("rooms", "attendees", "pseudonyms")) {
            event(eventId).collection(sub).get().documents.forEach { d -> ops += { b -> b.delete(d.reference) } }
        }
        commitChunks(ops)
        event(eventId).delete()
        if (isSuperadmin()) {
            commitChunks(event(eventId).collection("organizers").get().documents.map { d -> { b: WriteBatch -> b.delete(d.reference) } })
        }
    }

    override suspend fun addOrganizer(eventId: String, email: String) = guard {
        event(eventId).collection("organizers").document(email.trim().lowercase())
            .set(OrganizerWire(Timestamp.ServerTimestamp))
    }

    override suspend fun removeOrganizer(eventId: String, email: String) =
        guard { event(eventId).collection("organizers").document(email.lowercase()).delete() }

    override suspend fun importPlan(eventId: String, plan: ImportPlan) = guard {
        val roomsCol = event(eventId).collection("rooms")
        val existing = roomsCol.get().documents.map { it.id }.toSet()
        val ops = mutableListOf<(WriteBatch) -> Unit>()
        plan.rooms.forEachIndexed { i, r ->
            // update keeps a live room's liveTalkId; new rooms are created
            if (r.id in existing) {
                ops += { b -> b.update(roomsCol.document(r.id), "name" to r.name, "order" to i) }
            } else {
                ops += { b -> b.set(roomsCol.document(r.id), RoomWire(r.name, null, i)) }
            }
        }
        plan.talks.forEach { t ->
            ops += { b ->
                b.set(
                    talkRef(eventId, t.code),
                    TalkWrite(t.title, t.speakers, t.room, ms(t.startMs), ms(t.endMs), "scheduled", false),
                    encodeDefaults = false,
                )
            }
        }
        commitChunks(ops)
    }

    override suspend fun speakerKey(eventId: String, talkId: String): String = guard {
        val secretRef = event(eventId).collection("talkSecrets").document(talkId)
        val snap = secretRef.get()
        if (snap.exists) return@guard snap.data<SecretRead>().speakerKey
        val key = generateSpeakerKey()
        val b = db.batch()
        b.set(db.collection("speakerKeys").document(key), SpeakerKeyWire(eventId, talkId))
        b.set(secretRef, SecretWire(key))
        b.commit()
        key
    }

    override suspend fun exportQuestions(eventId: String): List<ExportRow> = guard {
        val rows = mutableListOf<ExportRow>()
        val now = currentTimeMillis()
        for (t in event(eventId).collection("talks").get().documents) {
            val title = t.data<ExportTalkRead>().title
            for (q in questions(eventId, t.id).get().documents) {
                val d = mapQuestion(q.id, q.data<QuestionRead>(), now)
                rows += ExportRow(
                    t.id, title, d.pseudonym, d.text, d.delivery, d.status, d.votes, d.createdAtMs, d.answer?.text.orEmpty(),
                )
            }
        }
        rows.sortedWith(compareBy<ExportRow> { it.talk }.thenBy { it.createdAtMs })
    }

    override suspend fun purgeIdentities(eventId: String): Int = guard {
        val ops = mutableListOf<(WriteBatch) -> Unit>()
        var n = 0
        for (t in event(eventId).collection("talks").get().documents) {
            for (q in questions(eventId, t.id).get().documents) {
                if (q.data<QuestionRead>().authorUid != "") {
                    n++
                    ops += { b -> b.update(q.reference, "authorUid" to "") }
                }
            }
            talkRef(eventId, t.id).collection("votes").get().documents.forEach { d -> ops += { b -> b.delete(d.reference) } }
        }
        for (sub in listOf("attendees", "pseudonyms")) {
            event(eventId).collection(sub).get().documents.forEach { d -> ops += { b -> b.delete(d.reference) } }
        }
        commitChunks(ops)
        n
    }
}

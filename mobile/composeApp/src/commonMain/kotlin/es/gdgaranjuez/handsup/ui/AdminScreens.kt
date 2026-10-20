package es.gdgaranjuez.handsup.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.gdgaranjuez.handsup.AppModel
import es.gdgaranjuez.handsup.Route
import es.gdgaranjuez.handsup.data.AuthUser
import es.gdgaranjuez.handsup.data.QuestionScope
import es.gdgaranjuez.handsup.domain.Branding
import es.gdgaranjuez.handsup.domain.Delivery
import es.gdgaranjuez.handsup.domain.EventDoc
import es.gdgaranjuez.handsup.domain.EventSettings
import es.gdgaranjuez.handsup.domain.Question
import es.gdgaranjuez.handsup.domain.QuestionStatus
import es.gdgaranjuez.handsup.domain.SortMode
import es.gdgaranjuez.handsup.domain.Talk
import es.gdgaranjuez.handsup.domain.TalkState
import es.gdgaranjuez.handsup.domain.buildQueue
import es.gdgaranjuez.handsup.i18n.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Runs [block], toasting a generic error if it fails. */
internal fun AppModel.launchGuarded(block: suspend () -> Unit) {
    scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            toast(t("err.denied"))
        }
    }
}

@Composable
fun AdminHomeScreen(app: AppModel) {
    val user by app.backend.authState.collectAsState(initial = null)
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.backend.authState.collect { ready = true } }

    PlatformBackHandler(app.nav.canGoBack) { app.nav.pop() }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(t("admin.title"), onBack = if (app.nav.canGoBack) ({ app.nav.pop() }) else null, actions = {
            if (user != null && user?.isAnonymous == false) {
                ToggleChip("", false, { app.launchGuarded { app.backend.signOut() } }, icon = AppIcon.Logout, tag = "sign-out")
                Spacer(Modifier.width(8.dp))
            }
            LangToggle(app)
            Spacer(Modifier.width(8.dp))
        })
        val u = user
        when {
            !ready -> LoadingBox()
            u == null || u.isAnonymous -> AdminLogin(app)
            !u.emailVerified -> VerifyEmailNotice(app, u)
            else -> AdminEvents(app, u)
        }
    }
}

@Composable
private fun AdminLogin(app: AppModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val clientId = app.config.googleWebClientId

    fun run(block: suspend () -> Unit) {
        busy = true
        app.scope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { app.toast(t("admin.signInFailed")) } finally { busy = false }
        }
    }

    ContentColumn(spacing = 14.dp) {
        Text(t("admin.signInTitle"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        MutedText(t("admin.signInHelp"), fontSize = 15)
        if (clientId != null) {
            AppButton(t("admin.google"), {
                run {
                    val token = app.services.googleIdToken(clientId) ?: return@run
                    app.backend.signInGoogle(token)
                }
            }, Modifier.fillMaxWidth(), enabled = !busy, tag = "google-signin")
        }
        AppCard {
            AppTextField(email, { email = it }, t("admin.email"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), tag = "email")
            AppTextField(
                password, { password = it }, t("admin.password"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), tag = "password",
            )
            AppButton(t("admin.signIn"), { run { app.backend.signInEmail(email, password) } }, Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.length >= 6, tag = "email-signin")
            AppButton(t("admin.register"), {
                run {
                    app.backend.registerEmail(email, password)
                    app.toast(t("admin.verifySent"))
                }
            }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, enabled = !busy && email.isNotBlank() && password.length >= 6, tag = "email-register")
        }
    }
}

@Composable
private fun VerifyEmailNotice(app: AppModel, user: AuthUser) {
    ContentColumn {
        AppCard {
            Text(t("admin.verifyNeeded", "email" to (user.email ?: "")), fontSize = 16.sp)
            AppButton(t("admin.signOut"), { app.launchGuarded { app.backend.signOut() } }, kind = ButtonKind.Ghost)
        }
    }
}

@Composable
private fun AdminEvents(app: AppModel, user: AuthUser) {
    var events by remember { mutableStateOf<List<EventDoc>>(emptyList()) }
    var superadmin by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var slug by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    LaunchedEffect(user.uid, reload) {
        try {
            superadmin = app.backend.isSuperadmin()
            events = app.backend.listEvents().filter { app.backend.isOrganizer(it.id) }
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { app.toast(t("err.generic")) }
        loaded = true
    }
    val slugOk = Regex("^[a-z0-9-]{3,40}$").matches(slug)

    ContentLazyColumn {
        item { Text(t("admin.events"), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item { MutedText(user.email.orEmpty()) }
        if (!loaded) item { LoadingBox() }
        if (loaded && events.isEmpty()) item { AppCard { MutedText(t("admin.noEvents")) } }
        items(events, key = { it.id }) { e ->
            AppCard(Modifier.clickable { app.nav.push(Route.AdminEvent(e.id)) }.testTag("admin-event")) {
                Text(e.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                MutedText(e.id)
            }
        }
        if (superadmin) item {
            AppCard(Modifier.testTag("create-event")) {
                Text(t("admin.createEvent"), fontWeight = FontWeight.SemiBold)
                AppTextField(name, { name = it }, t("admin.eventName"), tag = "event-name")
                AppTextField(
                    slug, { slug = it.lowercase().trim() }, t("admin.eventSlug"), placeholder = "fresonfest-2027",
                    isError = slug.isNotEmpty() && !slugOk, tag = "event-slug",
                )
                if (slug.isNotEmpty() && !slugOk) Text(t("admin.slugHelp"), color = tokens.danger, fontSize = 12.sp)
                AppButton(t("admin.create"), {
                    app.launchGuarded {
                        app.backend.createEvent(EventDoc(slug, name.trim(), Branding(), EventSettings()))
                        app.nav.push(Route.AdminSetup(slug))
                    }
                }, Modifier.fillMaxWidth(), enabled = slugOk && name.isNotBlank(), tag = "create-event-go")
            }
        }
    }
}

@Composable
fun AdminEventScreen(app: AppModel, route: Route.AdminEvent) {
    val eventId = route.eventId
    val st = rememberEventState(app, eventId, gate = { app.backend.isOrganizer(eventId) })
    var qrFor by remember { mutableStateOf<String?>(null) }
    PlatformBackHandler(true) { app.nav.pop() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(st.event?.name ?: eventId, onBack = { app.nav.pop() }, actions = {
            ToggleChip(t("admin.tab.setup"), false, { app.nav.push(Route.AdminSetup(eventId)) }, icon = AppIcon.Pencil, tag = "open-setup")
            Spacer(Modifier.width(8.dp))
        })
        if (st.allowed == false) {
            ContentColumn { AppCard { Text(t("admin.noAccess"), modifier = Modifier.testTag("no-access")) } }
            return@Column
        }
        if (st.allowed == null) { LoadingBox(); return@Column }

        ContentLazyColumn {
            item {
                AppCard {
                    Text(t("admin.joinTitle"), fontWeight = FontWeight.SemiBold)
                    MutedText(app.joinUrl(eventId))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton("QR", { qrFor = if (qrFor == "event") null else "event" }, kind = ButtonKind.Ghost, icon = AppIcon.Copy)
                        AppButton(t("common.share"), { app.services.share(app.joinUrl(eventId)) }, kind = ButtonKind.Ghost, icon = AppIcon.Upload)
                    }
                    if (qrFor == "event") QrView(app.joinUrl(eventId), size = 220.dp)
                }
            }
            if (st.loaded && st.talks.isEmpty()) item {
                AppCard {
                    MutedText(t("admin.noTalks"))
                    AppButton(t("admin.goSetup"), { app.nav.push(Route.AdminSetup(eventId)) })
                }
            }
            for (room in st.rooms) {
                val talks = st.talks.filter { it.roomId == room.id }
                if (talks.isEmpty()) continue
                item(key = "room-${room.id}") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(room.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        AppButton(t("admin.stage"), { app.services.openUrl(app.stageUrl(eventId, room.id)) }, kind = ButtonKind.Ghost, icon = AppIcon.Monitor)
                    }
                }
                items(talks, key = { it.id }) { tk -> AdminTalkCard(app, st, tk, qrFor) { qrFor = it } }
            }
        }
    }
}

@Composable
private fun AdminTalkCard(app: AppModel, st: EventState, tk: Talk, qrFor: String?, setQr: (String?) -> Unit) {
    val eventId = st.eventId
    val live = tk.state == TalkState.LIVE
    AppCard(
        Modifier.testTag("admin-talk-${tk.id}"),
        borderColor = if (live) tokens.brand else tokens.line,
        borderWidth = if (live) 2.dp else 1.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text(formatClock(tk.startMs), fontFamily = FontFamily.Monospace, color = tokens.muted, fontSize = 14.sp, modifier = Modifier.width(48.dp))
            Column(Modifier.weight(1f)) {
                Text(tk.title, fontWeight = FontWeight.SemiBold)
                if (tk.speakers.isNotBlank()) MutedText(tk.speakers)
            }
            Pill(tk.id, PillTone.Neutral)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live) Pill("● " + t("talk.state.live"), PillTone.Live) else if (tk.state == TalkState.ENDED) Pill(t("talk.state.ended"))
            if (tk.questionsClosed) Pill(t("admin.questionsClosed"), PillTone.Warn)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScrollRow()) {
            if (!live) {
                AppButton(t("admin.goLive"), { app.launchGuarded { app.backend.goLive(eventId, tk.id, tk.roomId) } }, icon = AppIcon.Play, tag = "go-live-${tk.id}")
            } else {
                AppButton(t("admin.endTalk"), { app.launchGuarded { app.backend.endTalk(eventId, tk.id, tk.roomId) } }, kind = ButtonKind.Ghost, icon = AppIcon.Square, tag = "end-${tk.id}")
            }
            AppButton(t("admin.queue"), { app.nav.push(Route.AdminQueue(eventId, tk.id)) }, kind = ButtonKind.Ghost, icon = AppIcon.Message, tag = "queue-${tk.id}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScrollRow()) {
            if (tk.state != TalkState.SCHEDULED) {
                AppButton(
                    if (tk.questionsClosed) t("admin.reopenQuestions") else t("admin.closeQuestions"),
                    { app.launchGuarded { app.backend.setQuestionsClosed(eventId, tk.id, !tk.questionsClosed) } },
                    kind = ButtonKind.Ghost, icon = if (tk.questionsClosed) AppIcon.Unlock else AppIcon.Lock,
                )
            }
            AppButton("QR", { setQr(if (qrFor == tk.id) null else tk.id) }, kind = ButtonKind.Ghost)
            AppButton(t("admin.speakerLink"), {
                app.launchGuarded { app.services.share(app.speakerUrl(eventId, tk.id, app.backend.speakerKey(eventId, tk.id))) }
            }, kind = ButtonKind.Ghost, icon = AppIcon.Copy, tag = "speaker-link-${tk.id}")
        }
        if (qrFor == tk.id) {
            QrView(app.talkUrl(eventId, tk.id), size = 200.dp)
            MutedText(app.talkUrl(eventId, tk.id), fontSize = 12)
        }
    }
}

@Composable
private fun Modifier.horizontalScrollRow(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

private enum class Lane(val key: String) { OPEN("lane.open"), PENDING("lane.pending"), FOLLOWUP("lane.followup"), ANSWERED("lane.answered"), HIDDEN("lane.hidden") }
private enum class DeliveryFilter { ALL, SPEAK, READ }

@Composable
fun AdminQueueScreen(app: AppModel, route: Route.AdminQueue) {
    val eventId = route.eventId
    val code = route.code.uppercase()
    val st = rememberEventState(app, eventId, gate = { app.backend.isOrganizer(eventId) })
    val talk = st.talk(code)
    var all by remember { mutableStateOf<List<Question>>(emptyList()) }
    var lane by remember { mutableStateOf(Lane.OPEN) }
    var filter by remember { mutableStateOf(DeliveryFilter.ALL) }
    var sortChoice by remember { mutableStateOf<SortMode?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(eventId, code, st.allowed) {
        if (st.allowed != true) return@LaunchedEffect
        app.backend.watchQuestions(eventId, code, QuestionScope.ALL, null).catch { app.toast(t("err.generic")) }.collect { all = it }
    }
    PlatformBackHandler(true) { app.nav.pop() }

    val sortMode = sortChoice ?: st.event?.settings?.defaultSort ?: SortMode.VOTES
    val filtered = all.filter { filter == DeliveryFilter.ALL || (filter == DeliveryFilter.SPEAK) == (it.delivery == Delivery.SPEAK) }
    val queue = remember(filtered, sortMode) { buildQueue(filtered, sortMode) }
    val lanes = if (queue.pending.isNotEmpty() || st.event?.settings?.preApproval == true) Lane.entries else Lane.entries.filter { it != Lane.PENDING }
    val shown = when (lane) {
        Lane.OPEN -> queue.open; Lane.PENDING -> queue.pending; Lane.FOLLOWUP -> queue.followup
        Lane.ANSWERED -> queue.answered; Lane.HIDDEN -> queue.hidden
    }
    fun count(l: Lane) = when (l) {
        Lane.OPEN -> queue.open.size; Lane.PENDING -> queue.pending.size; Lane.FOLLOWUP -> queue.followup.size
        Lane.ANSWERED -> queue.answered.size; Lane.HIDDEN -> queue.hidden.size
    }

    fun act(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { app.toast(t("err.denied")) } finally { busy = false }
        }
    }
    fun setStatus(q: Question, s: QuestionStatus) = act { app.backend.setQuestionStatus(eventId, code, q.id, s) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(talk?.title ?: code, onBack = { app.nav.pop() })
        if (st.allowed == false) { ContentColumn { AppCard { Text(t("admin.noAccess")) } }; return@Column }
        ContentLazyColumn {
            if (talk != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (talk.state == TalkState.LIVE) Pill("● " + t("talk.state.live"), PillTone.Live) else if (talk.state == TalkState.ENDED) Pill(t("talk.state.ended"))
                        MutedText(talk.speakers)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
                        if (talk.state != TalkState.LIVE) {
                            AppButton(t("admin.goLive"), { act { app.backend.goLive(eventId, code, talk.roomId) } }, enabled = !busy, icon = AppIcon.Play)
                        } else {
                            AppButton(t("admin.endTalk"), { act { app.backend.endTalk(eventId, code, talk.roomId) } }, kind = ButtonKind.Ghost, enabled = !busy, icon = AppIcon.Square)
                        }
                        if (talk.state != TalkState.SCHEDULED) {
                            AppButton(
                                if (talk.questionsClosed) t("admin.reopenQuestions") else t("admin.closeQuestions"),
                                { act { app.backend.setQuestionsClosed(eventId, code, !talk.questionsClosed) } },
                                kind = ButtonKind.Ghost, enabled = !busy, icon = if (talk.questionsClosed) AppIcon.Unlock else AppIcon.Lock,
                            )
                        }
                        AppButton(t("admin.stage"), { app.services.openUrl(app.stageUrl(eventId, talk.roomId)) }, kind = ButtonKind.Ghost, icon = AppIcon.Monitor)
                    }
                }
            }
            items(queue.onStage, key = { "s-${it.id}" }) { q ->
                AdminQuestionCard(q, onStage = true) {
                    AppButton(t("admin.markAnswered"), { setStatus(q, QuestionStatus.ANSWERED) }, enabled = !busy, icon = AppIcon.Check, tag = "act-answered")
                    AppButton(t("admin.followup"), { setStatus(q, QuestionStatus.FOLLOWUP) }, kind = ButtonKind.Ghost, enabled = !busy, icon = AppIcon.Message)
                    AppButton(t("admin.backToQueue"), { setStatus(q, QuestionStatus.OPEN) }, kind = ButtonKind.Ghost, enabled = !busy)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
                        for (l in lanes) ToggleChip(t(l.key) + " · ${count(l)}", lane == l, { lane = l }, tag = "lane-${l.name.lowercase()}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
                        ToggleChip(t("filter.all"), filter == DeliveryFilter.ALL, { filter = DeliveryFilter.ALL }, selectedColor = MaterialTheme3Secondary(), tag = "filter-all")
                        ToggleChip(t("delivery.speak.short"), filter == DeliveryFilter.SPEAK, { filter = DeliveryFilter.SPEAK }, icon = AppIcon.Mic, selectedColor = MaterialTheme3Secondary(), tag = "filter-speak")
                        ToggleChip(t("delivery.read.short"), filter == DeliveryFilter.READ, { filter = DeliveryFilter.READ }, icon = AppIcon.Volume, selectedColor = MaterialTheme3Secondary(), tag = "filter-read")
                        ToggleChip(t("talk.sortVotes"), sortMode == SortMode.VOTES, { sortChoice = SortMode.VOTES }, selectedColor = tokens.brand)
                        ToggleChip(t("talk.sortTime"), sortMode == SortMode.TIME, { sortChoice = SortMode.TIME }, selectedColor = tokens.brand)
                    }
                }
            }
            items(shown, key = { "q-${it.id}" }) { q ->
                AdminQuestionCard(q, onStage = false) {
                    when (lane) {
                        Lane.OPEN -> {
                            AppButton(t("admin.onStage"), { act { app.backend.putOnStage(eventId, code, q.id, queue.onStage.map { it.id }) } }, enabled = !busy, icon = AppIcon.Pin, tag = "act-stage")
                            AppButton(t("admin.markAnswered"), { setStatus(q, QuestionStatus.ANSWERED) }, kind = ButtonKind.Ghost, enabled = !busy, icon = AppIcon.Check)
                            AppButton(t("admin.followup"), { setStatus(q, QuestionStatus.FOLLOWUP) }, kind = ButtonKind.Ghost, enabled = !busy, icon = AppIcon.Message, tag = "act-followup")
                            AppButton(t("admin.hide"), { setStatus(q, QuestionStatus.HIDDEN) }, kind = ButtonKind.Danger, enabled = !busy, icon = AppIcon.EyeOff, tag = "act-hide")
                        }
                        Lane.PENDING -> {
                            AppButton(t("admin.approve"), { setStatus(q, QuestionStatus.OPEN) }, enabled = !busy, icon = AppIcon.Check, tag = "act-approve")
                            AppButton(t("admin.hide"), { setStatus(q, QuestionStatus.HIDDEN) }, kind = ButtonKind.Danger, enabled = !busy, icon = AppIcon.EyeOff)
                        }
                        Lane.FOLLOWUP, Lane.ANSWERED -> {
                            AppButton(t("admin.backToQueue"), { setStatus(q, QuestionStatus.OPEN) }, kind = ButtonKind.Ghost, enabled = !busy)
                            if (lane == Lane.FOLLOWUP) AppButton(t("admin.markAnswered"), { setStatus(q, QuestionStatus.ANSWERED) }, kind = ButtonKind.Ghost, enabled = !busy, icon = AppIcon.Check)
                        }
                        Lane.HIDDEN -> AppButton(t("admin.restore"), { setStatus(q, QuestionStatus.OPEN) }, kind = ButtonKind.Ghost, enabled = !busy)
                    }
                }
            }
            if (shown.isEmpty()) item { Text(t("admin.emptyLane"), color = tokens.muted, modifier = Modifier.padding(vertical = 24.dp).testTag("queue-empty")) }
        }
    }
}

@Composable
private fun MaterialTheme3Secondary() = androidx.compose.material3.MaterialTheme.colorScheme.secondary

@Composable
private fun AdminQuestionCard(q: Question, onStage: Boolean, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    AppCard(
        Modifier.testTag("admin-question"),
        borderColor = if (onStage) tokens.brand else tokens.line,
        borderWidth = if (onStage) 2.dp else 1.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(q.pseudonym, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.testTag("q-author"))
                    MutedText("· " + timeAgo(q.createdAtMs), fontSize = 13)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeliveryBadge(q.delivery)
                    if (onStage) Pill(t("status.onstage"), PillTone.Brand)
                }
                Text(q.text, fontSize = 18.sp, lineHeight = 23.sp, modifier = Modifier.testTag("q-text"))
                q.answer?.let { MutedText(t("talk.speakerAnswer") + ": " + it.text, fontSize = 13) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppIconView(AppIcon.ArrowUp, size = 16.dp, tint = tokens.muted)
                Text("${q.votes}", fontWeight = FontWeight.Bold, modifier = Modifier.testTag("vote-count"))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow(), content = actions)
    }
}

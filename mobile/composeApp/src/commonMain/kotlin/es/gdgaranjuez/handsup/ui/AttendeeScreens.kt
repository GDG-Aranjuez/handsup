package es.gdgaranjuez.handsup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.gdgaranjuez.handsup.AppModel
import es.gdgaranjuez.handsup.Route
import es.gdgaranjuez.handsup.currentTimeMillis
import es.gdgaranjuez.handsup.data.BackendError
import es.gdgaranjuez.handsup.data.QuestionScope
import es.gdgaranjuez.handsup.domain.Attendee
import es.gdgaranjuez.handsup.domain.Delivery
import es.gdgaranjuez.handsup.domain.MAX_QUESTION_LENGTH
import es.gdgaranjuez.handsup.domain.QUESTION_COOLDOWN_MS
import es.gdgaranjuez.handsup.domain.Question
import es.gdgaranjuez.handsup.domain.QuestionStatus
import es.gdgaranjuez.handsup.domain.SortMode
import es.gdgaranjuez.handsup.domain.Talk
import es.gdgaranjuez.handsup.domain.TalkState
import es.gdgaranjuez.handsup.domain.acceptsQuestions
import es.gdgaranjuez.handsup.domain.buildQueue
import es.gdgaranjuez.handsup.domain.isValidTalkCode
import es.gdgaranjuez.handsup.domain.normalizeTalkCode
import es.gdgaranjuez.handsup.domain.sortQuestions
import es.gdgaranjuez.handsup.i18n.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun HomeScreen(app: AppModel) {
    val default = app.config.defaultEventId
    LaunchedEffect(Unit) { if (default.isNotEmpty()) app.nav.replace(Route.Event(default)) }
    if (default.isNotEmpty()) return

    var slug by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContentColumn(spacing = 20.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("handsup", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = tokens.brandText)
                Spacer(Modifier.weight(1f))
                LangToggle(app)
            }
            MutedText(t("home.tagline"), fontSize = 16)
            AppCard {
                AppTextField(
                    slug, { slug = it.lowercase().trim() }, t("home.eventLabel"), placeholder = "fresonfest-2026",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None), tag = "home-slug",
                )
                AppButton(t("home.go"), { app.nav.push(Route.Event(slug)) }, Modifier.fillMaxWidth(), enabled = slug.isNotBlank(), tag = "home-go")
            }
            AppButton(t("home.admin"), { app.nav.push(Route.AdminHome) }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, tag = "home-admin")
        }
    }
}

@Composable
fun EventScreen(app: AppModel, route: Route.Event) {
    val st = rememberEventState(app, route.eventId)
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }
    var redirected by remember { mutableStateOf(false) }

    // Auto-open the live talk: the only one, or the one in the room this device chose before.
    LaunchedEffect(st.loaded, st.liveTalks, route.browse) {
        if (redirected || route.browse || !st.loaded || st.liveTalks.isEmpty()) return@LaunchedEffect
        val target = if (st.liveTalks.size == 1) st.liveTalks[0]
        else st.liveTalks.firstOrNull { it.roomId == app.rememberedRoom(route.eventId) }
        if (target != null) {
            redirected = true
            app.nav.replace(Route.Talk(route.eventId, target.id))
        }
    }

    fun open(talk: Talk) {
        app.rememberRoom(route.eventId, talk.roomId)
        app.nav.push(Route.Talk(route.eventId, talk.id))
    }

    PlatformBackHandler(app.nav.canGoBack) { app.nav.pop() }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(st.event?.name ?: "…", onBack = if (app.nav.canGoBack) ({ app.nav.pop() }) else null, actions = { LangToggle(app); Spacer(Modifier.width(8.dp)) })
        if (!st.loaded) { LoadingBox(); return@Column }
        if (st.event == null) {
            ContentColumn { Text(t("event.notFound"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("event-not-found")) }
            return@Column
        }
        ContentLazyColumn {
            st.event?.branding?.tagline?.let { item { MutedText(it, fontSize = 16) } }
            item { SectionTitle(t("event.liveNow")) }
            if (st.liveTalks.isEmpty()) item { AppCard { MutedText(t("event.noLive")) } }
            items(st.liveTalks, key = { it.id }) { talk ->
                AppCard(borderColor = tokens.brand, borderWidth = 2.dp, modifier = Modifier.clickable { open(talk) }.testTag("live-talk")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("● " + t("talk.state.live"), PillTone.Live)
                        if (st.rooms.size > 1) MutedText(st.room(talk.roomId)?.name.orEmpty())
                    }
                    Text(talk.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (talk.speakers.isNotBlank()) MutedText(talk.speakers, fontSize = 15)
                    AppButton(t("event.ask"), { open(talk) }, Modifier.fillMaxWidth())
                }
            }
            item {
                AppCard {
                    Text(t("event.codeLabel"), color = tokens.muted, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppTextField(
                            code, { code = normalizeTalkCode(it); codeError = false }, "ABCDE",
                            modifier = Modifier.weight(1f), isError = codeError,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            tag = "code-input",
                        )
                        AppButton(t("home.go"), {
                            val talk = st.talk(code)
                            if (isValidTalkCode(code) && talk != null) open(talk) else codeError = true
                        }, enabled = code.isNotBlank(), tag = "code-go")
                    }
                    if (codeError) Text(t("event.codeInvalid"), color = tokens.danger, fontSize = 13.sp)
                }
            }
            item { SectionTitle(t("event.agenda")) }
            if (st.loaded && st.talks.isEmpty()) item { MutedText(t("event.noTalks")) }
            items(st.talks, key = { "a-${it.id}" }) { talk ->
                AppCard(Modifier.clickable { open(talk) }.testTag("agenda-item")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Text(formatClock(talk.startMs), fontFamily = FontFamily.Monospace, color = tokens.muted, fontSize = 14.sp, modifier = Modifier.width(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(talk.title, fontWeight = FontWeight.Medium)
                            val sub = listOfNotNull(talk.speakers.ifBlank { null }, if (st.rooms.size > 1) st.room(talk.roomId)?.name else null).joinToString(" · ")
                            if (sub.isNotEmpty()) MutedText(sub)
                        }
                        when (talk.state) {
                            TalkState.LIVE -> Pill("● " + t("talk.state.live"), PillTone.Live)
                            TalkState.ENDED -> Pill(t("talk.state.ended"))
                            else -> {}
                        }
                    }
                }
            }
            item {
                AppButton(t("home.admin"), { app.nav.push(Route.AdminHome) }, Modifier.fillMaxWidth().padding(top = 8.dp), kind = ButtonKind.Ghost, tag = "home-admin")
            }
        }
    }
}

private enum class Tab { ALL, MINE }

@Composable
fun TalkScreen(app: AppModel, route: Route.Talk) {
    val st = rememberEventState(app, route.eventId)
    val eventId = route.eventId
    val code = route.code.uppercase()
    val talk = st.talk(code)
    val settings = st.event?.settings
    val scope = rememberCoroutineScope()

    var me by remember { mutableStateOf<Attendee?>(null) }
    var joinError by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf(false) }
    var publicQs by remember { mutableStateOf<List<Question>>(emptyList()) }
    var mineQs by remember { mutableStateOf<List<Question>>(emptyList()) }
    var myVotes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var now by remember { mutableLongStateOf(currentTimeMillis()) }
    var tab by remember { mutableStateOf(Tab.ALL) }
    var sortChoice by remember { mutableStateOf<SortMode?>(null) }
    var draft by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf(Delivery.READ) }
    var cooldownUntil by remember { mutableLongStateOf(0L) }
    var sending by remember { mutableIntStateOf(0) }
    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { while (true) { delay(1000); now = currentTimeMillis() } }

    LaunchedEffect(eventId, code, retry) {
        me = null; joinError = false; publicQs = emptyList(); mineQs = emptyList(); myVotes = emptySet()
        try {
            val user = app.backend.ensureAnonymous()
            me = app.backend.joinEvent(eventId, user.uid)
            coroutineScope {
                launch { app.backend.watchQuestions(eventId, code, QuestionScope.PUBLIC, user.uid).catch { listError = true }.collect { publicQs = it; listError = false } }
                launch { app.backend.watchQuestions(eventId, code, QuestionScope.MINE, user.uid).catch { }.collect { mineQs = it } }
                launch { app.backend.watchMyVotes(eventId, code, user.uid).catch { }.collect { myVotes = it } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            joinError = true
        }
    }

    val sortMode = sortChoice ?: settings?.defaultSort ?: SortMode.VOTES
    val open = talk != null && settings != null && talk.acceptsQuestions(settings.graceMin, now)
    val cooldown = ((cooldownUntil - now + 999) / 1000).coerceAtLeast(0)
    val queue = remember(publicQs, sortMode) { buildQueue(publicQs, sortMode) }
    val mineSorted = remember(mineQs) { sortQuestions(mineQs, SortMode.TIME).reversed() }
    val mineIds = remember(mineQs) { mineQs.map { it.id }.toSet() }
    val canSend = open && me != null && draft.isNotBlank() && draft.length <= MAX_QUESTION_LENGTH && cooldown == 0L

    fun errorText(e: Throwable) = if (e is BackendError && e.kind == BackendError.Kind.DENIED) t("err.denied") else t("err.generic")

    fun send() {
        val m = me ?: return
        val s = settings ?: return
        if (!canSend) return
        val text = draft.trim()
        val d = delivery
        cooldownUntil = currentTimeMillis() + QUESTION_COOLDOWN_MS
        draft = ""
        sending++
        scope.launch {
            try {
                app.backend.ask(eventId, code, m.uid, m.pseudonym, text, d, s.preApproval)
                app.toast(if (s.preApproval) t("talk.sentPending") else t("talk.sent"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                cooldownUntil = 0
                draft = text
                app.toast(errorText(e))
            } finally {
                sending--
            }
        }
    }

    // Actions on one question run in tap order: a second tap while the first write is still pending
    // is queued (not dropped).
    val locks = remember { mutableMapOf<String, Mutex>() }
    fun guarded(id: String, block: suspend () -> Unit) {
        val lock = locks.getOrPut(id) { Mutex() }
        scope.launch {
            lock.withLock {
                try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { app.toast(errorText(e)) }
            }
        }
    }

    fun canVote(q: Question) = open && settings?.voting == true && (q.status == QuestionStatus.OPEN || q.status == QuestionStatus.ONSTAGE)

    fun goBack() {
        val prev = app.nav.stack.getOrNull(app.nav.stack.lastIndex - 1)
        if (prev is Route.Event) app.nav.pop() else app.nav.replace(Route.Event(eventId, browse = true))
    }
    PlatformBackHandler(true) { goBack() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(st.event?.name ?: "…", onBack = ::goBack, actions = { LangToggle(app); Spacer(Modifier.width(8.dp)) })
        if (st.loaded && st.event != null && talk == null) {
            ContentColumn { AppCard { Text(t("talk.notFound"), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("talk-not-found")) } }
            return@Column
        }
        if (talk == null) { LoadingBox(); return@Column }

        ContentLazyColumn {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (talk.state) {
                            TalkState.LIVE -> Pill("● " + t("talk.state.live"), PillTone.Live)
                            TalkState.ENDED -> Pill(t("talk.state.ended"))
                            TalkState.SCHEDULED -> Pill(t("talk.state.scheduled") + " · " + formatClock(talk.startMs))
                        }
                        if (st.rooms.size > 1) MutedText(st.room(talk.roomId)?.name.orEmpty())
                    }
                    Text(talk.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, modifier = Modifier.testTag("talk-title"))
                    if (talk.speakers.isNotBlank()) MutedText(talk.speakers, fontSize = 16)
                }
            }
            if (joinError) item {
                AppCard {
                    Text(t("err.join"))
                    AppButton(t("common.retry"), { retry++ }, kind = ButtonKind.Ghost)
                }
            }
            me?.let { m ->
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("pseudonym-bar"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (editingName) {
                            AppTextField(nameDraft, { nameDraft = it.take(30) }, t("talk.customName"), Modifier.weight(1f), tag = "name-input")
                            AppButton(t("common.save"), {
                                scope.launch {
                                    try {
                                        me = m.copy(pseudonym = app.backend.changePseudonym(eventId, m.uid, nameDraft))
                                        editingName = false
                                    } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                                        app.toast(if (e is BackendError && e.kind == BackendError.Kind.DENIED) t("talk.nameTaken") else errorText(e))
                                    }
                                }
                            }, enabled = nameDraft.trim().length >= 2, tag = "name-save")
                        } else {
                            Column(Modifier.weight(1f)) {
                                MutedText(t("talk.askingAs"), fontSize = 12)
                                Text(m.pseudonym, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.testTag("my-pseudonym"))
                            }
                            ToggleChip(t("talk.reroll"), false, {
                                scope.launch {
                                    try { me = m.copy(pseudonym = app.backend.changePseudonym(eventId, m.uid)) }
                                    catch (e: CancellationException) { throw e } catch (e: Throwable) { app.toast(errorText(e)) }
                                }
                            }, icon = AppIcon.Dice, tag = "reroll")
                            ToggleChip("", false, { nameDraft = m.pseudonym; editingName = true }, icon = AppIcon.Pencil)
                        }
                    }
                }
            }
            item {
                AppCard(Modifier.testTag("composer")) {
                    if (talk.state == TalkState.SCHEDULED) {
                        MutedText(t("talk.notStarted"))
                    } else if (!open) {
                        MutedText(t("talk.closed"))
                    } else {
                        AppTextField(
                            draft, { if (it.length <= MAX_QUESTION_LENGTH) draft = it }, t("talk.placeholder"),
                            singleLine = false, minLines = 3, enabled = me != null, tag = "question-input",
                        )
                        Row(Modifier.fillMaxWidth()) {
                            MutedText(t("delivery.label"), fontSize = 12)
                            Spacer(Modifier.weight(1f))
                            Text("${draft.length}/$MAX_QUESTION_LENGTH", fontSize = 12.sp, color = if (draft.length > MAX_QUESTION_LENGTH - 20) tokens.warn else tokens.muted)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (d in listOf(Delivery.READ, Delivery.SPEAK)) {
                                DeliveryChoice(d, delivery == d, { delivery = d }, Modifier.weight(1f))
                            }
                        }
                        AppButton(
                            when {
                                sending > 0 -> t("talk.sending")
                                cooldown > 0 -> t("talk.wait", "n" to cooldown)
                                else -> t("talk.send")
                            },
                            ::send, Modifier.fillMaxWidth(), enabled = canSend, tag = "send",
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleChip(t("talk.tabAll") + " · ${publicQs.size}", tab == Tab.ALL, { tab = Tab.ALL }, tag = "tab-all")
                        ToggleChip(t("talk.tabMine") + " · ${mineQs.size}", tab == Tab.MINE, { tab = Tab.MINE }, tag = "tab-mine")
                    }
                    if (tab == Tab.ALL) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleChip(t("talk.sortVotes"), sortMode == SortMode.VOTES, { sortChoice = SortMode.VOTES }, selectedColor = tokens.brand)
                        ToggleChip(t("talk.sortTime"), sortMode == SortMode.TIME, { sortChoice = SortMode.TIME }, selectedColor = tokens.brand)
                    }
                }
            }
            if (listError) item { Text(t("err.list"), color = tokens.danger, fontSize = 13.sp) }

            if (tab == Tab.ALL) {
                val lanes = queue.onStage + queue.open
                items(lanes, key = { it.id }) { q ->
                    AttendeeQuestionCard(
                        q, mine = q.id in mineIds, voted = q.id in myVotes, canVote = canVote(q), showStatus = false,
                        onVote = { me?.let { m -> guarded(q.id) { if (q.id in myVotes) app.backend.unvote(eventId, code, q.id, m.uid) else app.backend.vote(eventId, code, q.id, m.uid) } } },
                        onWithdraw = { guarded(q.id) { app.backend.withdrawQuestion(eventId, code, q.id) } },
                        onEdit = { text, d -> guarded(q.id) { app.backend.editQuestion(eventId, code, q.id, text, d) } },
                    )
                }
                if (publicQs.isEmpty()) item { Text(t("talk.empty"), color = tokens.muted, modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth().testTag("empty")) }
                val rest = queue.followup + queue.answered
                if (rest.isNotEmpty()) {
                    item { SectionTitle(t("talk.answeredSection", "n" to rest.size)) }
                    items(rest, key = { "r-${it.id}" }) { q ->
                        AttendeeQuestionCard(
                            q, mine = q.id in mineIds, voted = q.id in myVotes, canVote = canVote(q), showStatus = false,
                            onVote = { me?.let { m -> guarded(q.id) { if (q.id in myVotes) app.backend.unvote(eventId, code, q.id, m.uid) else app.backend.vote(eventId, code, q.id, m.uid) } } },
                            onWithdraw = {}, onEdit = { _, _ -> },
                        )
                    }
                }
            } else {
                items(mineSorted, key = { "m-${it.id}" }) { q ->
                    AttendeeQuestionCard(
                        q, mine = true, voted = q.id in myVotes, canVote = canVote(q), showStatus = true,
                        onVote = { me?.let { m -> guarded(q.id) { if (q.id in myVotes) app.backend.unvote(eventId, code, q.id, m.uid) else app.backend.vote(eventId, code, q.id, m.uid) } } },
                        onWithdraw = { guarded(q.id) { app.backend.withdrawQuestion(eventId, code, q.id) } },
                        onEdit = { text, d -> guarded(q.id) { app.backend.editQuestion(eventId, code, q.id, text, d) } },
                    )
                }
                if (mineQs.isEmpty()) item { Text(t("talk.emptyMine"), color = tokens.muted, modifier = Modifier.padding(vertical = 24.dp)) }
            }
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("privacy.note"), color = tokens.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DeliveryChoice(d: Delivery, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tk = tokens
    val border = if (selected) tk.brand else tk.line
    Column(
        modifier
            .clickable(onClick = onClick)
            .then(Modifier.testTag("delivery-choice-${d.wire}"))
            .background(if (selected) tk.brand.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .border(2.dp, border, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconView(if (d == Delivery.SPEAK) AppIcon.Mic else AppIcon.Volume, size = 18.dp, tint = if (selected) MaterialTheme.colorScheme.onSurface else tk.muted)
            Text(t("delivery.${d.wire}.short"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = if (selected) MaterialTheme.colorScheme.onSurface else tk.muted)
        }
        Text(t("delivery.${d.wire}.hint"), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = tk.muted)
    }
}

@Composable
private fun AttendeeQuestionCard(
    q: Question,
    mine: Boolean,
    voted: Boolean,
    canVote: Boolean,
    showStatus: Boolean,
    onVote: () -> Unit,
    onWithdraw: () -> Unit,
    onEdit: (text: String?, delivery: Delivery?) -> Unit,
) {
    val tk = tokens
    var editing by remember(q.id) { mutableStateOf(false) }
    var text by remember(q.id) { mutableStateOf(q.text) }
    var delivery by remember(q.id) { mutableStateOf(q.delivery) }
    val editable = mine && (q.status == QuestionStatus.OPEN || q.status == QuestionStatus.PENDING)
    val textEditable = editable && q.votes == 0
    val dim = q.status == QuestionStatus.WITHDRAWN || q.status == QuestionStatus.HIDDEN

    AppCard(
        Modifier.testTag("question"),
        borderColor = if (q.status == QuestionStatus.ONSTAGE) tk.brand else tk.line,
        borderWidth = if (q.status == QuestionStatus.ONSTAGE) 2.dp else 1.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(q.pseudonym, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.testTag("q-author"))
                    if (mine) Pill(t("talk.you"), PillTone.Brand)
                    MutedText("· " + timeAgo(q.createdAtMs), fontSize = 13)
                }
                if (editing) {
                    if (textEditable) {
                        AppTextField(text, { if (it.length <= MAX_QUESTION_LENGTH) text = it }, t("talk.editText"), singleLine = false, minLines = 3)
                    } else {
                        Text(q.text)
                        MutedText(t("talk.editLocked"), fontSize = 12)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleChip(t("delivery.read.short"), delivery == Delivery.READ, { delivery = Delivery.READ }, icon = AppIcon.Volume, selectedColor = tk.brand)
                        ToggleChip(t("delivery.speak.short"), delivery == Delivery.SPEAK, { delivery = Delivery.SPEAK }, icon = AppIcon.Mic, selectedColor = tk.brand)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(t("common.save"), {
                            onEdit(if (textEditable && text.trim() != q.text) text.trim() else null, if (delivery != q.delivery) delivery else null)
                            editing = false
                        }, enabled = !textEditable || text.isNotBlank())
                        AppButton(t("common.cancel"), { editing = false }, kind = ButtonKind.Ghost)
                    }
                } else {
                    Text(q.text, fontSize = 17.sp, lineHeight = 22.sp, color = if (dim) tk.muted else MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("q-text"))
                }
                q.answer?.let { a ->
                    Column(
                        Modifier.fillMaxWidth().background(tk.surface2, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(12.dp).testTag("q-answer"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconView(AppIcon.Message, size = 14.dp, tint = tk.accentText)
                            Text(t("talk.speakerAnswer"), color = tk.accentText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text(a.text, fontSize = 14.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeliveryBadge(q.delivery)
                    if (showStatus || q.status in listOf(QuestionStatus.ONSTAGE, QuestionStatus.ANSWERED, QuestionStatus.FOLLOWUP)) {
                        Pill(
                            t("status.${q.status.wire}"),
                            when (q.status) {
                                QuestionStatus.ONSTAGE -> PillTone.Brand
                                QuestionStatus.ANSWERED -> PillTone.Ok
                                QuestionStatus.FOLLOWUP -> PillTone.Warn
                                else -> PillTone.Neutral
                            },
                        )
                    }
                }
                if (editable && !editing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip(t("common.edit"), false, { text = q.text; delivery = q.delivery; editing = true }, icon = AppIcon.Pencil, tag = "q-edit")
                    ToggleChip(t("talk.withdraw"), false, onWithdraw, icon = AppIcon.X, tag = "q-withdraw")
                }
            }
            if (q.status in QuestionStatus.visible) {
                Column(
                    Modifier
                        .size(width = 52.dp, height = 56.dp)
                        .background(if (voted) tk.brand else MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .border(1.dp, if (voted) tk.brand else tk.line, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickable(enabled = canVote || voted, onClick = onVote)
                        .testTag("vote"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val c = if (voted) tk.onBrand else MaterialTheme.colorScheme.onSurface
                    AppIconView(AppIcon.ArrowUp, size = 18.dp, tint = c.copy(alpha = if (canVote || voted) 1f else 0.4f))
                    Text("${q.votes}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.copy(alpha = if (canVote || voted) 1f else 0.4f), modifier = Modifier.testTag("vote-count"))
                }
            }
        }
    }
}

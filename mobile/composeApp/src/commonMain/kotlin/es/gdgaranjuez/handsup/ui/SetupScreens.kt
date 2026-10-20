package es.gdgaranjuez.handsup.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.gdgaranjuez.handsup.AppModel
import es.gdgaranjuez.handsup.Route
import es.gdgaranjuez.handsup.currentTimeMillis
import es.gdgaranjuez.handsup.domain.Branding
import es.gdgaranjuez.handsup.domain.EventDoc
import es.gdgaranjuez.handsup.domain.EventSettings
import es.gdgaranjuez.handsup.domain.ImportException
import es.gdgaranjuez.handsup.domain.ImportPlan
import es.gdgaranjuez.handsup.domain.Room
import es.gdgaranjuez.handsup.domain.SortMode
import es.gdgaranjuez.handsup.domain.Talk
import es.gdgaranjuez.handsup.domain.contrast
import es.gdgaranjuez.handsup.domain.isHexColor
import es.gdgaranjuez.handsup.domain.isValidTalkCode
import es.gdgaranjuez.handsup.domain.normalizeTalkCode
import es.gdgaranjuez.handsup.domain.onColor
import es.gdgaranjuez.handsup.domain.parseImport
import es.gdgaranjuez.handsup.domain.toCsv
import es.gdgaranjuez.handsup.domain.uniqueTalkCode
import es.gdgaranjuez.handsup.i18n.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private enum class SetupTab(val key: String) {
    ROOMS("setup.tab.rooms"), TALKS("setup.tab.talks"), BRANDING("setup.tab.branding"),
    SETTINGS("setup.tab.settings"), TEAM("setup.tab.team"), DATA("setup.tab.data"),
}

@Composable
fun AdminSetupScreen(app: AppModel, route: Route.AdminSetup) {
    val eventId = route.eventId
    val st = rememberEventState(app, eventId, gate = { app.backend.isOrganizer(eventId) })
    var tab by remember { mutableStateOf(SetupTab.ROOMS) }
    PlatformBackHandler(true) { app.nav.pop() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(st.event?.name ?: eventId, onBack = { app.nav.pop() })
        if (st.allowed == false) { ContentColumn { AppCard { Text(t("admin.noAccess")) } }; return@Column }
        if (st.allowed == null) { LoadingBox(); return@Column }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (k in SetupTab.entries) ToggleChip(t(k.key), tab == k, { tab = k }, tag = "setup-tab-${k.name.lowercase()}")
        }
        when (tab) {
            SetupTab.ROOMS -> RoomsTab(app, st)
            SetupTab.TALKS -> TalksTab(app, st)
            SetupTab.BRANDING -> BrandingTab(app, st)
            SetupTab.SETTINGS -> SettingsTab(app, st)
            SetupTab.TEAM -> TeamTab(app, st)
            SetupTab.DATA -> DataTab(app, st)
        }
    }
}

private fun slug(s: String): String =
    s.lowercase()
        .replace(Regex("[áàäâ]"), "a").replace(Regex("[éèëê]"), "e").replace(Regex("[íìïî]"), "i")
        .replace(Regex("[óòöô]"), "o").replace(Regex("[úùüû]"), "u").replace("ñ", "n")
        .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30)

@Composable
private fun RoomsTab(app: AppModel, st: EventState) {
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    ContentColumn {
        Text(t("setup.rooms"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        for (r in st.rooms) {
            var name by remember(r.id, r.name) { mutableStateOf(r.name) }
            AppCard(Modifier.testTag("room-${r.id}")) {
                AppTextField(name, { name = it.take(60) }, t("setup.roomName"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MutedText(r.id)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    AppButton(t("common.save"), { app.launchGuarded { app.backend.saveRoom(st.eventId, r.copy(name = name.trim())) } }, kind = ButtonKind.Ghost, enabled = name.isNotBlank() && name.trim() != r.name)
                    AppButton(
                        if (confirmDelete == r.id) t("common.sure") else t("common.delete"),
                        {
                            if (st.talks.any { it.roomId == r.id }) app.toast(t("setup.roomInUse"))
                            else if (confirmDelete != r.id) confirmDelete = r.id
                            else { confirmDelete = null; app.launchGuarded { app.backend.deleteRoom(st.eventId, r.id) } }
                        },
                        kind = ButtonKind.Danger, icon = AppIcon.Trash,
                    )
                }
            }
        }
        AppCard {
            AppTextField(newName, { newName = it.take(60) }, t("setup.roomName"), tag = "new-room-name")
            val id = slug(newName)
            AppButton(t("common.add"), {
                app.launchGuarded {
                    app.backend.saveRoom(st.eventId, Room(id, newName.trim(), null, st.rooms.size))
                    newName = ""
                }
            }, enabled = id.isNotEmpty() && st.room(id) == null, icon = AppIcon.Plus, tag = "add-room")
        }
    }
}

@Composable
private fun TalksTab(app: AppModel, st: EventState) {
    var editing by remember { mutableStateOf<String?>(null) } // code, or "new"
    var code by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var speakers by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    fun startNew() {
        val last = st.talks.lastOrNull()
        val base = last?.endMs?.plus(10 * 60_000L) ?: currentTimeMillis()
        editing = "new"
        code = uniqueTalkCode(st.talks.map { it.id }.toMutableSet())
        title = ""; speakers = ""
        roomId = st.rooms.firstOrNull()?.id.orEmpty()
        date = formatDate(base); start = formatClock(base); end = formatClock(base + 45 * 60_000L)
    }
    fun startEdit(tk: Talk) {
        editing = tk.id; code = tk.id; title = tk.title; speakers = tk.speakers; roomId = tk.roomId
        date = formatDate(tk.startMs); start = formatClock(tk.startMs); end = formatClock(tk.endMs)
    }

    val startMs = parseLocal(date, start)
    val endMs = parseLocal(date, end)
    val valid = isValidTalkCode(code) && title.isNotBlank() && roomId.isNotEmpty() && startMs != null && endMs != null && endMs > startMs &&
        (editing != "new" || st.talk(code) == null)

    ContentColumn {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(t("setup.talks"), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            AppButton(t("setup.addTalk"), ::startNew, enabled = st.rooms.isNotEmpty(), icon = AppIcon.Plus, tag = "new-talk")
        }
        if (st.rooms.isEmpty()) Text(t("setup.needRoom"), color = tokens.warn, fontSize = 13.sp)

        if (editing != null) {
            AppCard(Modifier.testTag("talk-form")) {
                AppTextField(title, { title = it.take(160) }, t("setup.talkTitle"), tag = "tk-title")
                AppTextField(speakers, { speakers = it.take(200) }, t("setup.speakers"), tag = "tk-speakers")
                SectionTitle(t("setup.room"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in st.rooms) ToggleChip(r.name, roomId == r.id, { roomId = r.id }, selectedColor = tokens.brand)
                }
                AppTextField(date, { date = it.trim() }, t("setup.date"), placeholder = "2026-10-24", isError = date.isNotEmpty() && startMs == null, tag = "tk-date")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(start, { start = it.trim() }, t("setup.start"), Modifier.weight(1f), placeholder = "10:00", tag = "tk-start")
                    AppTextField(end, { end = it.trim() }, t("setup.end"), Modifier.weight(1f), placeholder = "10:45", tag = "tk-end")
                }
                AppTextField(
                    code, { code = normalizeTalkCode(it) }, t("admin.talkCode"), enabled = editing == "new",
                    isError = code.isNotEmpty() && !isValidTalkCode(code), tag = "tk-code",
                )
                if (code.isNotEmpty() && !isValidTalkCode(code)) Text(t("setup.codeHelp"), color = tokens.warn, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(t("common.save"), {
                        val existing = st.talk(code)
                        app.launchGuarded {
                            app.backend.saveTalk(
                                st.eventId,
                                Talk(
                                    id = code, title = title.trim(), speakers = speakers.trim(), roomId = roomId,
                                    startMs = startMs!!, endMs = endMs!!, state = existing?.state ?: es.gdgaranjuez.handsup.domain.TalkState.SCHEDULED,
                                    questionsClosed = existing?.questionsClosed ?: false, endedAtMs = existing?.endedAtMs,
                                ),
                            )
                            editing = null
                        }
                    }, enabled = valid, tag = "save-talk")
                    AppButton(t("common.cancel"), { editing = null }, kind = ButtonKind.Ghost)
                }
            }
        }

        for (tk in st.talks) {
            AppCard(Modifier.testTag("setup-talk")) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Text(formatClock(tk.startMs), fontFamily = FontFamily.Monospace, color = tokens.muted, fontSize = 14.sp)
                    Column(Modifier.weight(1f)) {
                        Text(tk.title, fontWeight = FontWeight.Medium)
                        MutedText(listOf(tk.speakers, st.room(tk.roomId)?.name ?: "—").filter { it.isNotBlank() }.joinToString(" · "))
                    }
                    Pill(tk.id)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(t("common.edit"), { startEdit(tk) }, kind = ButtonKind.Ghost, icon = AppIcon.Pencil)
                    AppButton(
                        if (confirmDelete == tk.id) t("common.sure") else t("common.delete"),
                        {
                            if (confirmDelete != tk.id) confirmDelete = tk.id
                            else { confirmDelete = null; app.launchGuarded { app.backend.deleteTalk(st.eventId, tk.id) } }
                        },
                        kind = ButtonKind.Danger, icon = AppIcon.Trash,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandingTab(app: AppModel, st: EventState) {
    var name by remember { mutableStateOf("") }
    var tagline by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf("") }
    var accent by remember { mutableStateOf("") }
    var loadedFor by remember { mutableStateOf("") }
    val ev = st.event

    LaunchedEffect(ev) {
        if (ev != null && loadedFor != ev.id) {
            loadedFor = ev.id; name = ev.name; tagline = ev.branding.tagline.orEmpty()
            primary = ev.branding.primary; accent = ev.branding.accent
        }
    }
    val valid = name.isNotBlank() && isHexColor(primary) && isHexColor(accent)
    // live preview while editing
    LaunchedEffect(primary, accent) { if (isHexColor(primary) && isHexColor(accent)) app.branding = Branding(primary, accent, ev?.branding?.logo, ev?.branding?.tagline) }

    ContentColumn {
        Text(t("setup.branding"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        AppCard {
            AppTextField(name, { name = it.take(80) }, t("admin.eventName"), tag = "brand-name")
            AppTextField(tagline, { tagline = it.take(120) }, t("setup.tagline"))
            ColorField(primary, { primary = it }, t("setup.primary"), "brand-primary")
            ColorField(accent, { accent = it }, t("setup.accent"), "brand-accent")
            if (isHexColor(primary)) {
                val c = contrast(primary, onColor(primary))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(t("setup.preview"), {}, enabled = true)
                    Text(t("setup.contrast", "n" to (kotlin.math.round(c * 10) / 10.0)), color = if (c >= 4.5) tokens.ok else tokens.warn, fontSize = 13.sp)
                }
            }
            MutedText(t("setup.logoWebOnly"), fontSize = 12)
        }
        AppButton(t("common.save"), {
            val e = ev ?: return@AppButton
            app.launchGuarded {
                app.backend.saveEvent(e.copy(name = name.trim(), branding = Branding(primary, accent, e.branding.logo, tagline.trim().ifEmpty { null })))
                app.toast(t("common.saved"))
            }
        }, enabled = valid, tag = "save-branding")
    }
}

@Composable
private fun ColorField(value: String, onChange: (String) -> Unit, label: String, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(44.dp).background(if (isHexColor(value)) hex(value) else tokens.surface2, RoundedCornerShape(12.dp)),
        )
        AppTextField(value, { onChange(it.take(7)) }, label, Modifier.weight(1f), isError = value.isNotEmpty() && !isHexColor(value), tag = tag)
    }
}

@Composable
private fun SettingsTab(app: AppModel, st: EventState) {
    var s by remember { mutableStateOf(EventSettings()) }
    var grace by remember { mutableStateOf("10") }
    var loadedFor by remember { mutableStateOf("") }
    val ev = st.event
    LaunchedEffect(ev) {
        if (ev != null && loadedFor != ev.id) { loadedFor = ev.id; s = ev.settings; grace = ev.settings.graceMin.toString() }
    }
    ContentColumn {
        Text(t("setup.settings"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        AppCard {
            SwitchRow(t("setup.voting"), t("setup.votingHelp"), s.voting, "set-voting") { s = s.copy(voting = it) }
            SwitchRow(t("setup.preApproval"), t("setup.preApprovalHelp"), s.preApproval, "set-preapproval") { s = s.copy(preApproval = it) }
            SectionTitle(t("setup.defaultSort"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(t("talk.sortVotes"), s.defaultSort == SortMode.VOTES, { s = s.copy(defaultSort = SortMode.VOTES) }, selectedColor = tokens.brand)
                ToggleChip(t("talk.sortTime"), s.defaultSort == SortMode.TIME, { s = s.copy(defaultSort = SortMode.TIME) }, selectedColor = tokens.brand)
            }
            AppTextField(grace, { grace = it.filter(Char::isDigit).take(3) }, t("setup.grace"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            MutedText(t("setup.graceHelp"), fontSize = 12)
        }
        AppButton(t("common.save"), {
            val e = ev ?: return@AppButton
            app.launchGuarded {
                app.backend.saveEvent(e.copy(settings = s.copy(graceMin = (grace.toIntOrNull() ?: 10).coerceIn(0, 240))))
                app.toast(t("common.saved"))
            }
        }, tag = "save-settings")
    }
}

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            MutedText(help, fontSize = 13)
        }
        Switch(checked, onChange, Modifier.testTag(tag))
    }
}

@Composable
private fun TeamTab(app: AppModel, st: EventState) {
    var emails by remember { mutableStateOf<List<String>>(emptyList()) }
    var superadmin by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    LaunchedEffect(st.eventId) { superadmin = app.backend.isSuperadmin() }
    LaunchedEffect(st.eventId) { app.backend.watchOrganizers(st.eventId).catch { }.collect { emails = it } }
    val ok = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    ContentColumn {
        Text(t("setup.team"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        MutedText(t("setup.teamHelp"))
        for (e in emails) AppCard(Modifier.testTag("organizer")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e, Modifier.weight(1f))
                if (superadmin) AppButton("", { app.launchGuarded { app.backend.removeOrganizer(st.eventId, e) } }, kind = ButtonKind.Danger, icon = AppIcon.Trash)
            }
        }
        if (superadmin) {
            AppCard {
                AppTextField(email, { email = it }, "organizer@gmail.com", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), tag = "organizer-email")
                AppButton(t("common.add"), { app.launchGuarded { app.backend.addOrganizer(st.eventId, email); email = "" } }, enabled = ok, icon = AppIcon.Plus, tag = "add-organizer")
            }
        } else {
            MutedText(t("setup.teamSuperadminOnly"))
        }
    }
}

@Composable
private fun DataTab(app: AppModel, st: EventState) {
    var text by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    var importError by remember { mutableStateOf("") }
    var confirmPurge by remember { mutableStateOf(false) }
    var deleteSlug by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun validate(value: String) {
        text = value; plan = null; importError = ""
        if (value.isBlank()) return
        try {
            plan = parseImport(value, st.talks.map { it.id })
        } catch (e: ImportException) {
            importError = t("import.err.${e.code}") + if (e.detail.isNotEmpty()) " (${e.detail})" else ""
        }
    }
    fun run(block: suspend () -> Unit) {
        busy = true
        app.scope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { app.toast(t("err.denied")) } finally { busy = false }
        }
    }

    ContentColumn {
        Text(t("setup.data"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        AppCard {
            Text(t("setup.import"), fontWeight = FontWeight.SemiBold)
            MutedText(t("setup.importHelp"), fontSize = 13)
            AppTextField(text, ::validate, "JSON", singleLine = false, minLines = 5, placeholder = "{ \"rooms\": [...], \"talks\": [...] }", tag = "import-text")
            if (importError.isNotEmpty()) Text(importError, color = tokens.danger, fontSize = 13.sp, modifier = Modifier.testTag("import-error"))
            plan?.let { p ->
                Text(t("setup.importPreview", "rooms" to p.rooms.size, "talks" to p.talks.size), color = tokens.ok, fontSize = 13.sp, modifier = Modifier.testTag("import-preview"))
                AppButton(t("setup.importRun"), {
                    run { app.backend.importPlan(st.eventId, p); app.toast(t("setup.imported", "n" to p.talks.size)); text = ""; plan = null }
                }, enabled = !busy, icon = AppIcon.Upload, tag = "run-import")
            }
        }
        AppCard {
            Text(t("setup.export"), fontWeight = FontWeight.SemiBold)
            AppButton("CSV", {
                run {
                    val rows = app.backend.exportQuestions(st.eventId)
                    val csv = toCsv(
                        listOf("talk", "title", "pseudonym", "text", "delivery", "status", "votes", "created_at", "answer"),
                        rows.map { listOf(it.talk, it.talkTitle, it.pseudonym, it.text, it.delivery.wire, it.status.wire, it.votes, it.createdAtMs, it.answer) },
                    )
                    app.services.share(csv)
                }
            }, kind = ButtonKind.Ghost, enabled = !busy, icon = AppIcon.Download, tag = "export-csv")
        }
        AppCard {
            Text(t("setup.privacy"), fontWeight = FontWeight.SemiBold)
            MutedText(t("setup.purgeHelp"), fontSize = 13)
            AppButton(if (confirmPurge) t("common.sure") else t("setup.purge"), {
                if (!confirmPurge) confirmPurge = true
                else { confirmPurge = false; run { app.toast(t("setup.purged", "n" to app.backend.purgeIdentities(st.eventId))) } }
            }, kind = ButtonKind.Danger, enabled = !busy, tag = "purge")
        }
        AppCard(borderColor = tokens.danger.copy(alpha = 0.5f)) {
            Text(t("setup.danger"), fontWeight = FontWeight.SemiBold, color = tokens.danger)
            MutedText(t("setup.deleteHelp", "slug" to st.eventId), fontSize = 13)
            AppTextField(deleteSlug, { deleteSlug = it }, t("setup.deleteConfirm"), placeholder = st.eventId, tag = "delete-slug")
            AppButton(t("setup.deleteEvent"), {
                run { app.backend.deleteEvent(st.eventId); app.nav.popTo(Route.AdminHome) }
            }, kind = ButtonKind.Danger, enabled = !busy && deleteSlug == st.eventId, icon = AppIcon.Trash, tag = "delete-event")
        }
    }
}

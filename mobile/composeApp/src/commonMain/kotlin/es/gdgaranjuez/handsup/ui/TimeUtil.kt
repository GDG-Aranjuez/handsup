package es.gdgaranjuez.handsup.ui

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private fun two(v: Int) = v.toString().padStart(2, '0')

private fun local(ms: Long, zone: TimeZone) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone)

/** HH:mm in the device time zone (or [zone]). */
fun formatClock(ms: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    local(ms, zone).let { "${two(it.hour)}:${two(it.minute)}" }

/** YYYY-MM-DD in the device time zone (or [zone]). */
fun formatDate(ms: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    local(ms, zone).let { "${it.year}-${two((it.month.ordinal + 1))}-${two(it.day)}" }

/** Epoch millis for a local "YYYY-MM-DD" + "HH:mm", or null when either is malformed. */
fun parseLocal(date: String, time: String, zone: TimeZone = TimeZone.currentSystemDefault()): Long? = try {
    val padded = if (Regex("^\\d:\\d\\d$").matches(time)) "0$time" else time
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) || !Regex("^\\d{2}:\\d{2}$").matches(padded)) null
    else LocalDateTime.parse("${date}T$padded").toInstant(zone).toEpochMilliseconds()
} catch (e: IllegalArgumentException) {
    null
}

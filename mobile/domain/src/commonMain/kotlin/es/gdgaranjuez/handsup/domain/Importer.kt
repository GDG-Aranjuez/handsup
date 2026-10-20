package es.gdgaranjuez.handsup.domain

import kotlin.random.Random
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ImportRoom(val id: String, val name: String)
data class ImportTalk(
    val code: String,
    val title: String,
    val speakers: String,
    val room: String,
    val startMs: Long,
    val endMs: Long,
)
data class ImportPlan(val rooms: List<ImportRoom>, val talks: List<ImportTalk>)

/** Invalid import. [code] is a stable id the UI can translate; [detail] says where. */
class ImportException(val code: String, val detail: String = "") : Exception("$code $detail".trim())

/**
 * Schema (docs/import-schema.md):
 * `{ "rooms": [{ "id", "name" }], "talks": [{ "code"?, "title", "speakers"?, "room", "start", "end" }] }`
 * with ISO-8601 timestamps including an offset.
 */
fun parseImport(json: String, existingCodes: Collection<String> = emptyList(), random: Random = Random.Default): ImportPlan {
    val root = try {
        Json.parseToJsonElement(json)
    } catch (e: Exception) {
        throw ImportException("badJson")
    }
    val obj = root as? JsonObject ?: throw ImportException("notObject")
    val roomsArr = obj["rooms"] as? JsonArray
    if (roomsArr == null || roomsArr.isEmpty()) throw ImportException("rooms")
    val talksArr = obj["talks"] as? JsonArray ?: throw ImportException("talks")

    fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    val idPattern = Regex("^[a-z0-9-]{1,30}$")
    val rooms = roomsArr.mapIndexed { i, el ->
        val r = el as? JsonObject
        val id = r?.get("id").str()
        if (id == null || !idPattern.matches(id)) throw ImportException("roomId", "rooms[$i]")
        val name = r?.get("name").str()?.trim()
        if (name.isNullOrEmpty()) throw ImportException("roomName", "rooms[$i]")
        ImportRoom(id, name.take(60))
    }
    val roomIds = rooms.map { it.id }.toSet()

    val taken = existingCodes.toMutableSet()
    talksArr.forEach { (it as? JsonObject)?.get("code").str()?.let(taken::add) }
    val seen = mutableSetOf<String>()

    val talks = talksArr.mapIndexed { i, el ->
        val t = el as? JsonObject ?: throw ImportException("talk", "talks[$i]")
        val title = t["title"].str()?.trim()
        if (title.isNullOrEmpty()) throw ImportException("title", "talks[$i]")
        val room = t["room"].str()
        if (room == null || room !in roomIds) throw ImportException("room", "talks[$i]")
        val start = t["start"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: throw ImportException("start", "talks[$i]")
        val end = t["end"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: throw ImportException("end", "talks[$i]")
        if (end <= start) throw ImportException("order", "talks[$i]")
        val explicit = t["code"]
        val code = if (explicit == null) {
            uniqueTalkCode(taken, random)
        } else {
            val c = explicit.str()
            if (c == null || !isValidTalkCode(c)) throw ImportException("code", "talks[$i]")
            if (!seen.add(c)) throw ImportException("codeRepeated", "talks[$i]")
            c
        }
        seen.add(code)
        ImportTalk(
            code = code,
            title = title.take(160),
            speakers = t["speakers"].str()?.trim()?.take(200).orEmpty(),
            room = room,
            startMs = start.toEpochMilliseconds(),
            endMs = end.toEpochMilliseconds(),
        )
    }
    return ImportPlan(rooms, talks)
}

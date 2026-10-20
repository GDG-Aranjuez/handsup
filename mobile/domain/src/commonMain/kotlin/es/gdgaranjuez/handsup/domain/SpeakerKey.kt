package es.gdgaranjuez.handsup.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val KEY_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** 32 characters from a CSPRNG (random v4 UUIDs); the secret half of a speaker link. */
@OptIn(ExperimentalUuidApi::class)
fun generateSpeakerKey(): String {
    val bytes = Uuid.random().toByteArray() + Uuid.random().toByteArray()
    return bytes.take(32).joinToString("") { KEY_CHARS[(it.toInt() and 0xff) % KEY_CHARS.length].toString() }
}

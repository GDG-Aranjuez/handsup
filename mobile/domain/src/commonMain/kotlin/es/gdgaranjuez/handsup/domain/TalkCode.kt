package es.gdgaranjuez.handsup.domain

import kotlin.random.Random

/** No 0/O/1/I/L: codes are typed and read aloud. Must match the rules' talk id pattern. */
const val TALK_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
const val TALK_CODE_LENGTH = 5

fun generateTalkCode(random: Random = Random.Default): String =
    buildString { repeat(TALK_CODE_LENGTH) { append(TALK_CODE_ALPHABET[random.nextInt(TALK_CODE_ALPHABET.length)]) } }

/** Uppercases and strips anything that is not a letter or digit. */
fun normalizeTalkCode(input: String): String =
    input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(TALK_CODE_LENGTH)

fun isValidTalkCode(code: String): Boolean =
    code.length == TALK_CODE_LENGTH && code.all { it in TALK_CODE_ALPHABET }

/** Returns a code not in [taken] and records it there. */
fun uniqueTalkCode(taken: MutableSet<String>, random: Random = Random.Default): String {
    repeat(1000) {
        val code = generateTalkCode(random)
        if (taken.add(code)) return code
    }
    error("could not generate a unique talk code")
}

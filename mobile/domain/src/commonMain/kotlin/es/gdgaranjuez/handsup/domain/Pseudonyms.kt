package es.gdgaranjuez.handsup.domain

import kotlin.random.Random

private val FIRST = listOf(
    "Marie", "Albert", "Ada", "Alan", "Grace", "Isaac", "Nikola", "Rosalind", "Katherine", "Linus",
    "Margaret", "Richard", "Hedy", "Carl", "Tim", "Dennis", "Barbara", "Galileo", "Stephen", "Niels",
    "Emmy", "Johannes", "Charles", "Lise", "Jocelyn", "Donald", "Radia", "Vint", "Frances", "Edsger",
    "Mary", "Michael", "Santiago", "Margarita", "Ramon", "Severo", "Claude", "John", "Anita", "Guido",
)
private val LAST = listOf(
    "Curie", "Einstein", "Lovelace", "Turing", "Hopper", "Newton", "Tesla", "Franklin", "Johnson",
    "Torvalds", "Hamilton", "Feynman", "Lamarr", "Sagan", "Berners-Lee", "Ritchie", "Liskov", "Galilei",
    "Hawking", "Bohr", "Noether", "Kepler", "Darwin", "Meitner", "Bell", "Knuth", "Babbage", "Faraday",
    "Maxwell", "Pasteur", "Planck", "Hubble", "Goodall", "Perlman", "Cerf", "Allen", "Dijkstra",
    "Cajal", "Ochoa", "Salas", "Shannon", "McCarthy", "Borg", "Rossum", "Hertz", "Volta", "Ampere",
)

val PSEUDONYM_COMBINATIONS: Int = FIRST.size * LAST.size

/** A random "First Last" mixing famous science and technology names, e.g. "Marie Einstein". */
fun generatePseudonym(random: Random = Random.Default): String =
    "${FIRST[random.nextInt(FIRST.size)]} ${LAST[random.nextInt(LAST.size)]}"

/** Uniqueness key inside an Event. Must match the rules (`name.lower()`). */
fun pseudonymKey(name: String): String = name.lowercase()

fun cleanPseudonym(input: String): String =
    input.replace(Regex("[/\\n\\r\\t]+"), " ").replace(Regex("\\s+"), " ").trim().take(30)

fun isValidPseudonym(name: String): Boolean = name.length in 2..30 && '/' !in name

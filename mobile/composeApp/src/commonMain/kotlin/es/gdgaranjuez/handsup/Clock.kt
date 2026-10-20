package es.gdgaranjuez.handsup

import kotlin.time.Clock

fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

package ru.stohastika.sklyanka.ui.token

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

object SklyankaMotion {

    private const val FAST_MS = 150
    private const val SHORT_MS = 200
    private const val MEDIUM_MS = 300

    val DurationFast: Int get() = FAST_MS
    val DurationShort: Int get() = SHORT_MS
    val DurationMedium: Int get() = MEDIUM_MS

    val EasingEnter: Easing get() = LinearOutSlowInEasing
    val EasingExit: Easing get() = FastOutLinearInEasing
}

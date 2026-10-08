package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Коэффициент контраста по WCAG 2.1: `(L1 + 0,05) / (L2 + 0,05)`.
 *
 * Считается прямо по токенам, а не по снимку экрана: расхождение с таблицей
 * `docs/v2/12-design.md` §6.1 должно находиться сборкой, а не глазами на
 * ревью (НФТ-32, критерий 7).
 */
internal fun contrastRatio(foreground: Color, background: Color): Double {
    val first = relativeLuminance(foreground)
    val second = relativeLuminance(background)
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Color): Double =
    RED_WEIGHT * linear(color.red) + GREEN_WEIGHT * linear(color.green) + BLUE_WEIGHT * linear(color.blue)

private fun linear(channel: Float): Double {
    val value = channel.toDouble()
    return if (value <= SRGB_KNEE) value / SRGB_SLOPE else ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
}

private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
private const val SRGB_KNEE = 0.03928
private const val SRGB_SLOPE = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_GAMMA = 2.4

/** AA для обычного текста. */
internal const val AA_NORMAL = 4.5

/** AA для крупного текста: от 18,66 px при 700 или от 24 px. */
internal const val AA_LARGE = 3.0

/** Допуск сверки с таблицей §6.1: она посчитана с точностью до сотых. */
internal const val TABLE_TOLERANCE = 0.02

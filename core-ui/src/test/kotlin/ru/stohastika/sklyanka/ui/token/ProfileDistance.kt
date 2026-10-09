package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Различимость цветов закрытой палитры профилей (ПРФ-15, `docs/v2/12-design.md` §2.2).
 *
 * Мера и пороги живут одним местом, потому что их спрашивают двое: тест набора ролей
 * и шаги приёмки SKL-109. Два экземпляра одной меры разошлись бы на первой правке
 * порога — и «отличимо» в коде означало бы не то же, что «отличимо» в нормативе.
 */
internal fun profileDistance(first: Color, second: Color): Int {
    val red = (first.red - second.red) * MAX_CHANNEL
    val green = (first.green - second.green) * MAX_CHANNEL
    val blue = (first.blue - second.blue) * MAX_CHANNEL
    return sqrt((red * red + green * green + blue * blue).toDouble()).roundToInt()
}

private const val MAX_CHANNEL = 255f

/**
 * Порог различимости цвета профиля и основного акцента.
 *
 * Задан по ближайшей к акценту паре набора — глубокая олива `#3C5014`, 53. Решением
 * Р-20 (SKL-109) набор пересмотрен: прежняя олива `#7A5B2E` стояла к новому акценту
 * `#5F6F2E` в 34 — цвет профиля, неотличимый от акцента, то есть человек перестал бы
 * замечать, что ведёт чужие приёмы. Порог поднят вместе с палитрой, чтобы более
 * близкий цвет в набор уже не прошёл.
 */
internal const val MIN_PROFILE_ACCENT_DISTANCE = 50

/**
 * Порог различимости цветов профилей между собой.
 *
 * Ниже порога отличия от акцента, и это находка, а не послабление: ближайшая пара
 * набора — `Teal #1F5F6B` и `Plum #6B4A6B`, между ними 79. Порог зафиксирован так,
 * чтобы более близкая пара в набор уже не прошла.
 */
internal const val MIN_PROFILE_PAIR_DISTANCE = 75

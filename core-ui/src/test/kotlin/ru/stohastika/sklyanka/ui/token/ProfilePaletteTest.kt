package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Закрытый набор цветов профиля (ПРФ-15, критерий 3).
 *
 * Смысл проверки — не в пересчёте констант, а в том, что цвет, неотличимый от
 * основного акцента, в набор попасть не может: цена такой ошибки — человек
 * перестаёт замечать, что ведёт чужие приёмы.
 */
class ProfilePaletteTest {

    @Test
    fun `palette is closed and not empty`() {
        assertEquals(ProfileColor.entries.size, ProfilePalette.all.size)
        assertTrue(ProfilePalette.all.isNotEmpty())
    }

    @Test
    fun `the main accent is not part of the palette`() {
        assertTrue(
            "the application accent must not be selectable as a profile colour",
            ProfilePalette.all.none { it.color == ProfilePalette.ownerAccent },
        )
    }

    @Test
    fun `every profile colour is distinguishable from the main accent`() {
        ProfilePalette.all.forEach { profile ->
            val distance = distance(profile.color, ProfilePalette.ownerAccent)
            assertTrue(
                "${profile.name} is $distance away from the accent, below $MIN_DISTANCE",
                distance >= MIN_DISTANCE,
            )
        }
    }

    @Test
    fun `profile colours are distinguishable from each other`() {
        ProfilePalette.all.forEachIndexed { index, first ->
            ProfilePalette.all.drop(index + 1).forEach { second ->
                val distance = distance(first.color, second.color)
                assertTrue(
                    "${first.name} and ${second.name} are $distance apart",
                    distance >= MIN_PROFILE_DISTANCE,
                )
            }
        }
    }

    @Test
    fun `the specification profile colour is in the palette`() {
        assertEquals(Palette.ProfileForeign, ProfileColor.Terracotta.color)
    }

    private fun distance(first: Color, second: Color): Int {
        val red = (first.red - second.red) * MAX_CHANNEL
        val green = (first.green - second.green) * MAX_CHANNEL
        val blue = (first.blue - second.blue) * MAX_CHANNEL
        return sqrt((red * red + green * green + blue * blue).toDouble()).roundToInt()
    }

    private companion object {
        const val MAX_CHANNEL = 255f

        /**
         * Порог различимости в sRGB.
         *
         * Задан по самой близкой паре набора — «цвет чужого профиля» `#8F3E23` и
         * основной акцент `#B4512F`, между ними 43. Это цвет из `SPEC-original.md`
         * §9, убрать его нельзя; порог зафиксирован так, чтобы более близкий цвет в
         * набор уже не прошёл.
         */
        const val MIN_DISTANCE = 40

        /**
         * Порог различимости цветов профилей между собой.
         *
         * Ниже порога отличия от акцента, и это находка, а не послабление: самая
         * близкая пара набора — `Terracotta #8F3E23` и `Olive #7A5B2E`, между ними
         * 37. Оба цвета взяты из макетов, состав набора дизайном не утверждён
         * (ОВ-10). Порог зафиксирован так, чтобы более близкая пара в набор уже не
         * прошла, а сам факт вынесен в отчёт задачи.
         */
        const val MIN_PROFILE_DISTANCE = 35
    }
}

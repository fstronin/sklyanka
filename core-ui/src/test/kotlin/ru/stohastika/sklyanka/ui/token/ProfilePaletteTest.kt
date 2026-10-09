package ru.stohastika.sklyanka.ui.token

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Закрытый набор цветов профиля (ПРФ-15, критерий 3).
 *
 * Смысл проверки — не в пересчёте констант, а в том, что цвет, неотличимый от
 * основного акцента, в набор попасть не может: цена такой ошибки — человек
 * перестаёт замечать, что ведёт чужие приёмы.
 *
 * Мера и пороги различимости — [profileDistance], [MIN_PROFILE_ACCENT_DISTANCE],
 * [MIN_PROFILE_PAIR_DISTANCE]: их же спрашивают шаги приёмки SKL-109.
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
            val distance = profileDistance(profile.color, ProfilePalette.ownerAccent)
            assertTrue(
                "${profile.name} is $distance away from the accent, below $MIN_PROFILE_ACCENT_DISTANCE",
                distance >= MIN_PROFILE_ACCENT_DISTANCE,
            )
        }
    }

    @Test
    fun `profile colours are distinguishable from each other`() {
        ProfilePalette.all.forEachIndexed { index, first ->
            ProfilePalette.all.drop(index + 1).forEach { second ->
                val distance = profileDistance(first.color, second.color)
                assertTrue(
                    "${first.name} and ${second.name} are $distance apart",
                    distance >= MIN_PROFILE_PAIR_DISTANCE,
                )
            }
        }
    }

    @Test
    fun `the specification profile colour is in the palette`() {
        assertEquals(Palette.ProfileForeign, ProfileColor.Terracotta.color)
    }
}

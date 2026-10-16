package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.stohastika.sklyanka.domain.appearance.AccentTheme

/**
 * Индикация чужого профиля выживает в тёмной среде (ПРФ-10, §9, §2.2, SKL-120).
 *
 * §9 говорит прямо: тонирование считается **теми же долями от тёмных ролей**, «иначе
 * подкраска пропала бы именно там, где её легче всего не заметить, — ночью». Роли выбирает
 * среда, и уже по ним раскрашивается профиль: обратный порядок стирал бы полосу, подкраску
 * шапки и акцент действий.
 */
class ForeignProfileDarkTest {

    private val terracotta: Color = ProfileColor.Terracotta.color

    @Test
    fun theStripeCarriesTheRaisedProfileColour() {
        val raised = tint(Palette.Background, terracotta, 0.55f)
        AccentTheme.entries.forEach { theme ->
            val tinted = tintedColors(darkColors(theme), terracotta, dark = true)
            assertEquals("the stripe is the raised profile colour at night (§2.2)", raised, tinted.profileStripe)
            assertNotEquals(
                "the theme's accent must not stand in for it",
                darkColors(theme).accent,
                tinted.profileStripe,
            )
        }
    }

    @Test
    fun theAccentIsTheRaisedProfileColourAndNotTheThemeOne() {
        val raised = tint(Palette.Background, terracotta, 0.55f)
        AccentTheme.entries.forEach { theme ->
            val dark = tintedColors(darkColors(theme), terracotta, dark = true)
            assertEquals("the foreign accent at night is the raised profile colour (§2.2)", raised, dark.accent)
            assertNotEquals(
                "the dark accent of the theme must not survive in a foreign profile",
                darkColors(theme).accent,
                dark.accent,
            )
            assertEquals(
                "in the light environment the accent is the profile colour as it is (§2.2)",
                terracotta,
                tintedColors(lightColors(theme), terracotta, dark = false).accent,
            )
        }
    }

    @Test
    fun theHeaderIsTintedApartFromTheBackground() {
        AccentTheme.entries.forEach { theme ->
            val dark = tintedColors(darkColors(theme), terracotta, dark = true)
            assertNotEquals("the header of a foreign profile is tinted (§2.2)", dark.background, dark.profileHeader)
            val light = tintedColors(lightColors(theme), terracotta, dark = false)
            assertNotEquals("and it is tinted by day too", light.background, light.profileHeader)
        }
    }
}

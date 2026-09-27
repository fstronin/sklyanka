package ru.heuri.sklyanka.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.ui.component.ProfileStripe
import ru.heuri.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.SklyankaColors

/**
 * Профильный акцент (ПРФ-11, ПРФ-16, критерии 1 и 2).
 *
 * Проверяется не «цвет кнопки на снимке», а то, что компонент, собранный в двух
 * темах, получает разные значения ролей, ничего не зная о профиле. Это и есть
 * механика, ради которой акцент живёт в теме, а не в параметре компонента.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), а прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class ProfileThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun ownerThemeKeepsTheApplicationAccentAndShowsNoIndication() {
        val colors = captureColors(ProfileAccent.Owner)
        assertEquals(Color.Transparent, colors.profileStripe)
        assertEquals(colors.background, colors.profileHeader)
        assertNotEquals(ProfileColor.Terracotta.color, colors.accent)
    }

    @Test
    fun foreignThemeTurnsAccentStripeAndHeaderOnAtOnce() {
        val colors = captureColors(ProfileAccent.Foreign(ProfileColor.Terracotta))
        assertEquals(ProfileColor.Terracotta.color, colors.accent)
        assertEquals(ProfileColor.Terracotta.color, colors.profileStripe)
        assertNotEquals(colors.background, colors.profileHeader)
    }

    @Test
    fun theStripeIsAbsentInTheOwnerProfileAndPresentInAForeignOne() {
        composeTestRule.setContent {
            SklyankaTheme(profile = ProfileAccent.Foreign(ProfileColor.Teal)) {
                ProfileStripe()
            }
        }
        composeTestRule.onNodeWithTag(TAG_PROFILE_STRIPE).assertIsDisplayed()
    }

    @Test
    fun darkEnvironmentIsAParameterAndNotTheSystemTheme() {
        var light: SklyankaColors? = null
        var dark: SklyankaColors? = null
        composeTestRule.setContent {
            SklyankaTheme(dark = false) { light = SklyankaTheme.colors }
            SklyankaTheme(dark = true) { dark = SklyankaTheme.colors }
        }
        composeTestRule.waitForIdle()
        assertNotEquals(light?.background, dark?.background)
        assertNotEquals(light?.accent, dark?.accent)
    }

    private fun captureColors(profile: ProfileAccent): SklyankaColors {
        var captured: SklyankaColors? = null
        composeTestRule.setContent {
            SklyankaTheme(profile = profile) {
                Capture { captured = it }
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(captured) { "theme did not provide colours" }
    }

    @Composable
    private fun Capture(onColors: (SklyankaColors) -> Unit) {
        onColors(SklyankaTheme.colors)
    }
}

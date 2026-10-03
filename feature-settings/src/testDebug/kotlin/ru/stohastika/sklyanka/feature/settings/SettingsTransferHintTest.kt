package ru.stohastika.sklyanka.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.experiment.VariantKey
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Витрина варианта подписи «Перенос данных» (SKL-83, TDD §12.2).
 *
 * Проверяется настоящий экран и настоящие строки: база — текущая подпись, вариант A
 * показывается только когда его назвал каталог, всё остальное читается как база.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class SettingsTransferHintTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the base transfer caption is shown by default`() {
        show(VariantKey.BASE)

        composeRule.onNodeWithText(text(R.string.settings_transfer_caption)).assertIsDisplayed()
    }

    @Test
    fun `variant a shows the alternative transfer caption`() {
        show(VariantKey.A)

        composeRule.onNodeWithText(text(R.string.settings_transfer_caption_alt)).assertIsDisplayed()
    }

    @Test
    fun `a variant outside the catalog falls back to the base caption`() {
        show(VariantKey.C)

        composeRule.onNodeWithText(text(R.string.settings_transfer_caption)).assertIsDisplayed()
    }

    private fun show(hint: VariantKey) {
        composeRule.setContent {
            SklyankaTheme {
                SettingsScreen(
                    state = SettingsScreenFixtures.state().copy(transferHint = hint),
                    onIntent = {},
                )
            }
        }
    }

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}

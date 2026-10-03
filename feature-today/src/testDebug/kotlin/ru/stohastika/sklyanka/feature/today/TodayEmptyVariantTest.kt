package ru.stohastika.sklyanka.feature.today

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
import java.time.LocalDate

/**
 * Витрина варианта пустого состояния «Сегодня» (SKL-83, TDD §12.1).
 *
 * Проверяется настоящий экран и настоящие строки: база — текущая формулировка, вариант A
 * показывается только когда его назвал каталог, всё остальное читается как база.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h800dp-mdpi")
class TodayEmptyVariantTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 10, 3)

    @Test
    fun `the base copy is shown by default`() {
        show(VariantKey.BASE)

        composeRule.onNodeWithText(text(R.string.today_empty_text)).assertIsDisplayed()
    }

    @Test
    fun `variant a shows the alternative copy`() {
        show(VariantKey.A)

        composeRule.onNodeWithText(text(R.string.today_empty_text_alt)).assertIsDisplayed()
    }

    @Test
    fun `a variant outside the catalog falls back to the base copy`() {
        show(VariantKey.B)

        composeRule.onNodeWithText(text(R.string.today_empty_text)).assertIsDisplayed()
    }

    private fun show(copy: VariantKey) {
        composeRule.setContent {
            SklyankaTheme {
                TodayScreen(state = TodayUiState.Empty(date, copy), onIntent = {})
            }
        }
    }

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}

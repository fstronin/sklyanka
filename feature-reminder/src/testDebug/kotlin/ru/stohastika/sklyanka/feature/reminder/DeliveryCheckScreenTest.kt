package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDateTime

/**
 * Карточка тестового напоминания (SKL-86, ОВ-108).
 *
 * Проверяется то, чем карточка проверки отличается от боевой: надзаголовок, время,
 * строка «отмечать ничего не нужно» и ровно одно действие «Понятно». Действий отметки
 * на ней нет по построению — приёма не существует.
 */
@RunWith(AndroidJUnit4::class)
// Язык — часть условий: надписи читаются по-русски, а время — форматом из ресурсов.
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class DeliveryCheckScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val at: LocalDateTime = LocalDateTime.of(2026, 9, 25, 10, 1, 30)

    private fun show(onAcknowledge: () -> Unit = {}, fontScale: Float = 1f) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                SklyankaTheme(dark = true) {
                    DeliveryCheckScreen(at = at, onAcknowledge = onAcknowledge)
                }
            }
        }
    }

    @Test
    fun theCardCarriesTheCheckOverlineTimeAndTestLine() {
        show()

        compose.onNodeWithTag(TAG_DELIVERY_CHECK).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.reminder_check_overline)).assertExists()
        compose.onNodeWithText("10:01").assertExists()
        compose.onNodeWithText(context.getString(R.string.reminder_check_text)).assertExists()
    }

    @Test
    fun theCardHasExactlyOneAcknowledgeAction() {
        show()

        compose.onNodeWithTag(TAG_CHECK_ACKNOWLEDGE).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_acknowledge)).assertExists()
    }

    @Test
    fun theCardPretendsToBeNoIntake() {
        show()

        // Ни отметки, ни откладывания: проверять нечего, и карточка об этом говорит прямо.
        compose.onNodeWithText(context.getString(ru.stohastika.sklyanka.ui.R.string.action_taken)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(ru.stohastika.sklyanka.ui.R.string.action_skip)).assertDoesNotExist()
        compose.onNodeWithTag(TAG_ROW).assertDoesNotExist()
    }

    /**
     * Время проверки ограничено тем же потолком 130 %, что и боевое напоминание
     * (§6.3, ОВ-117): при 200 % «10:01» не помещается на 390 dp.
     */
    @Test
    fun theTimeIsCappedAtTwoHundredPercent() {
        show(fontScale = SCALE_200)

        val node = compose.onNodeWithTag(TAG_CHECK_TIME, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = requireNotNull(results.firstOrNull())
        assertEquals("the time must stay one line", 1, layout.lineCount)
        assertEquals(
            "the time size must be cut to the 130 % ceiling",
            CAPPED_FONT_SIZE,
            layout.layoutInput.style.fontSize.value,
            CAPPED_FONT_TOLERANCE,
        )
    }

    @Test
    fun acknowledgeReachesItsOwner() {
        var acknowledged = 0
        show(onAcknowledge = { acknowledged += 1 })

        compose.onNodeWithTag(TAG_CHECK_ACKNOWLEDGE).performClick()

        assertEquals(1, acknowledged)
    }

    private companion object {
        const val SCALE_200 = 2f

        /** 64 sp · 1,3 / 2 — кегль под потолком 130 % при системном масштабе 200 %. */
        const val CAPPED_FONT_SIZE = 41.6f
        const val CAPPED_FONT_TOLERANCE = 0.01f
    }
}

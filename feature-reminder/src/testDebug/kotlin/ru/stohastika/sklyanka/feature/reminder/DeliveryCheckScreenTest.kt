package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    private fun show(onAcknowledge: () -> Unit = {}) {
        compose.setContent {
            SklyankaTheme(dark = true) {
                DeliveryCheckScreen(at = at, onAcknowledge = onAcknowledge)
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

    @Test
    fun acknowledgeReachesItsOwner() {
        var acknowledged = 0
        show(onAcknowledge = { acknowledged += 1 })

        compose.onNodeWithTag(TAG_CHECK_ACKNOWLEDGE).performClick()

        assertEquals(1, acknowledged)
    }
}

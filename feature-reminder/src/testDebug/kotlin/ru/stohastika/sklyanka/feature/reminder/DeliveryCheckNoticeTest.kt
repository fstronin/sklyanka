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
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant

/**
 * Баннер результата самопроверки (SKL-86, ОВ-108).
 *
 * Успех закрывается «Понятно», неудача ведёт «Исправить»: у неудачи человеку есть что
 * чинить, у успеха — нет. «Проверка не состоялась» не объявляется вовсе: человек
 * в приложении, и исход говорится блоком.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class DeliveryCheckNoticeTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun finished(outcome: DeliveryCheckOutcome, lateMinutes: Long? = null) = DeliveryCheckState.Finished(
        outcome = outcome,
        dueAt = Instant.parse("2026-09-25T07:01:30Z"),
        resolvedAt = Instant.parse("2026-09-25T07:02:00Z"),
        lateMinutes = lateMinutes,
    )

    private fun show(state: DeliveryCheckState.Finished, onAcknowledge: () -> Unit = {}, onFix: () -> Unit = {}) {
        compose.setContent {
            SklyankaTheme {
                DeliveryCheckNotice(state = state, onAcknowledge = onAcknowledge, onFix = onFix)
            }
        }
    }

    @Test
    fun anOnTimeResultIsClosedWithAcknowledge() {
        var acknowledged = 0
        var fixed = 0
        show(finished(DeliveryCheckOutcome.ON_TIME), onAcknowledge = { acknowledged += 1 }, onFix = { fixed += 1 })

        compose.onNodeWithTag(TAG_CHECK_NOTICE).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_on_time)).assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_acknowledge)).performClick()

        assertEquals(1, acknowledged)
        assertEquals("success offers no fix", 0, fixed)
    }

    @Test
    fun aLateResultNamesTheMinutesAndLeadsToTheFix() {
        var acknowledged = 0
        var fixed = 0
        show(
            finished(DeliveryCheckOutcome.LATE, lateMinutes = 12),
            onAcknowledge = { acknowledged += 1 },
            onFix = { fixed += 1 },
        )

        compose
            .onNodeWithText(
                context.getString(
                    R.string.diagnostics_check_late,
                    context.resources.getQuantityString(R.plurals.diagnostics_late, 12, 12),
                ),
            )
            .assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_fix)).performClick()

        assertEquals(1, fixed)
        assertEquals(0, acknowledged)
    }

    @Test
    fun aMissedResultLeadsToTheFix() {
        var fixed = 0
        show(finished(DeliveryCheckOutcome.NOT_DELIVERED), onFix = { fixed += 1 })

        compose.onNodeWithText(context.getString(R.string.diagnostics_check_not_delivered)).assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_fix)).performClick()

        assertEquals(1, fixed)
    }

    @Test
    fun anUncompletedCheckIsNotAnnouncedAtAll() {
        show(finished(DeliveryCheckOutcome.NOT_COMPLETED))

        compose.onNodeWithTag(TAG_CHECK_NOTICE).assertDoesNotExist()
    }
}

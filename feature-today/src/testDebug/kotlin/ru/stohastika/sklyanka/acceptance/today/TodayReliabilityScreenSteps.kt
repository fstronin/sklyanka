package ru.stohastika.sklyanka.acceptance.today

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.feature.today.R
import ru.stohastika.sklyanka.feature.today.TAG_RELIABILITY_BANNER
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки предупреждения о ненадёжности напоминаний (SKL-29, группа `@screen`;
 * заведены вместе с задачей).
 *
 * Состояние экрана собирает настоящий домен из мира сценария ([TodayStand]): причину
 * ненадёжности сводит `ReminderReliability` (SKL-13), а не шаг. Проверки говорят
 * о собранной композиции: видно ли предупреждение, называет ли оно причину словами
 * человека, остаётся ли приём отмечаемым.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayReliabilityScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Тогда("^на экране видно предупреждение, что напоминания сейчас не приходят$")
    fun theWarningSaysRemindersDoNotComeNow() {
        val warning = reliability()
        assertTrue(
            "«сейчас не приходят» must be the revoked notifications, not another cause: $warning",
            warning == ReminderWarning.NOTIFICATIONS_OFF || warning == ReminderWarning.BOTH_OFF,
        )
        // Для BOTH_OFF у предупреждения своя формулировка: она называет обе причины
        // одним сообщением (сценарий «предупреждение называет обе причины»).
        val text = if (warning == ReminderWarning.BOTH_OFF) {
            R.string.today_reliability_both_off
        } else {
            R.string.today_reliability_notifications_off
        }
        assertBannerShows(context.getString(text))
    }

    @Но("^ни одно уведомление о приёме не показывается$")
    fun noIntakeNotificationIsShown() {
        assertFalse(
            "the scenario must have revoked the notification permission",
            world.reminders.notificationsEnabled(),
        )
        // Срабатывание идёт боевым путём: будильник ставится и доставляется настоящим
        // сценарием, и только показ роняет система — уведомления отозваны (НАП-18).
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.reminders.deliverDue()
        assertTrue(
            "revoked notifications must leave no card in the shade: ${world.reminders.shownCards}",
            world.reminders.shownCards.isEmpty(),
        )
    }

    @Тогда("^человек видит предупреждение, что напоминания могут приходить с опозданием$")
    fun theWarningSaysRemindersMayBeLate() {
        assertEquals(
            "the scenario must have revoked the exact-alarm right",
            ReminderWarning.EXACT_ALARMS_OFF,
            reliability(),
        )
        assertBannerShows(context.getString(R.string.today_reliability_exact_alarms_off))
    }

    @И("^приложение не сообщает, что напоминания настроены правильно$")
    fun theAppDoesNotClaimRemindersAreConfigured() {
        // «Не сообщает, что настроены правильно» — значит несёт причину, а не тишину:
        // состояние экрана ненадёжно, и предупреждение об этом видно человеку (НАП-18).
        assertEquals(ReminderWarning.EXACT_ALARMS_OFF, reliability())
        assertTrue(
            "the warning must be on the screen, not silence",
            TodayScreenProbe.textsOnScreen(world, TAG_RELIABILITY_BANNER).isNotEmpty(),
        )
    }

    @И("^приложение позволяет отметить приём вручную$")
    fun theAppAllowsManualMarking() {
        // Приём рядом с предупреждением остаётся отмечаемым: баннер стоит в шапке
        // и не блокирует список (НАП-18). Нажатие идёт по собранной карточке, а отметку
        // применяет к миру тот же домен, что в продукте.
        val intake = requireNotNull(
            world.intakes.ofProfile(world.profiles, world.profiles.active)
                .firstOrNull { it.scheduledAt.toLocalDate() == world.clock.today() && it.isPending },
        ) { "the day of the scenario must have a pending intake" }
        val name = requireNotNull(
            world.medications.names().firstOrNull { world.medications.get(it).id == intake.medicationId },
        ) { "unknown medication: ${intake.medicationId}" }
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNode(
                hasText(context.getString(UiR.string.action_taken)) and TodayScreenProbe.insideCard(world, name),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue("the card must offer a manual mark: $intents", TodayIntent.MarkTaken(intake.id) in intents)
        assertEquals(IntakeStatus.TAKEN, world.intakes.get(intake.id)?.status)
    }

    // --- Обвязка ---------------------------------------------------------------

    private fun reliability() = ReadReminderReliabilityUseCase(world.system)().warning

    private fun assertBannerShows(text: String) {
        val shown = TodayScreenProbe.textsOnScreen(world, TAG_RELIABILITY_BANNER)
        assertTrue("the warning banner must read «$text», it reads $shown", text in shown)
    }
}

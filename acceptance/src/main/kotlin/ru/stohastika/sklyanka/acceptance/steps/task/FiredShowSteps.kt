package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.FiredShowPolicy
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.SuppressReason
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Шаги приёмки SKL-88 — опоздавшее обычное срабатывание не предъявляется (ПМ-22, ОВ-109).
 *
 * Правила не копируются: срабатывание доставляет настоящий
 * [ru.stohastika.sklyanka.domain.reminder.HandleFiredReminderUseCase], откладывание —
 * настоящий `SnoozeReminderUseCase`, а порог проверяется доменным [FiredShowPolicy]
 * там, где сценарий называет опоздание числом. Отдельной копии числа «30 минут»
 * в шагах нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class FiredShowSteps(private val world: AcceptanceWorld) {

    // --- Предыстория ---------------------------------------------------------------------------

    /** Напоминание уже предъявлено: данность сценария, проживающего затем подавление. */
    @Допустим("^в (.+?), (\\d{2}:\\d{2}) напоминание об этом приёме уже было предъявлено$")
    fun theReminderWasAlreadyShown(date: String, time: String) {
        val intake = world.intakes.currentOrScheduled()
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(world.localMoment(date, time))
        world.reminders.deliverDue()
        assertTrue(world.reminders.isNotificationShown(intake.id), "the premise says the reminder was shown")
    }

    /**
     * Подавленное срабатывание как данность: опоздание либо названо минутами,
     * либо только «больше порога» — тогда проверяется, что порог действительно превышен.
     */
    @Допустим(
        "^(?:затем )?в (\\d{2}:\\d{2}) срабатывание этого приёма было подавлено" +
            "(?:: опоздание (?:(\\d+) минут|больше порога))?$",
    )
    fun theFiringWasSuppressed(time: String, minutes: Int?) {
        val suppressed = suppressFiring(world.clock.today().atTime(LocalTime.parse(time)))
        if (minutes != null) {
            assertEquals(minutes * SECONDS_IN_MINUTE, suppressed.deviationSeconds, "the named lateness")
        } else {
            assertFalse(
                FiredShowPolicy.shouldShow(
                    now = requireNotNull(suppressed.firedAt),
                    scheduledAt = requireNotNull(suppressed.scheduledAt),
                ),
                "the premise says the lateness is over the threshold",
            )
        }
    }

    @Допустим("^в (.+?), (\\d{2}:\\d{2}) срабатывание с опозданием (\\d+) минут было подавлено$")
    fun theLateFiringWasSuppressed(date: String, time: String, minutes: Int) {
        val suppressed = suppressFiring(world.localMoment(date, time))
        assertEquals(minutes * SECONDS_IN_MINUTE, suppressed.deviationSeconds, "the named lateness")
    }

    /** Показанные напоминания за неделю: на их фоне проверяется, что подавленное не считается. */
    @Допустим("^за последние 7 дней были и показанные напоминания$")
    fun theLastSevenDaysHadShownReminders() {
        val now = world.clock.now()
        repeat(SHOWN_LAST_WEEK) { index ->
            val planned = now.minus(Duration.ofHours((index + 1).toLong()))
            record(
                ReminderLogEntry(
                    occurredAt = planned,
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-shown-$index",
                    scheduledAt = planned,
                    firedAt = planned.plusSeconds(index.toLong()),
                    deviationSeconds = index.toLong(),
                    delivered = true,
                ),
            )
        }
    }

    // --- Действия ------------------------------------------------------------------------------

    /** Будильник приёма срабатывает в названный момент — в том числе позже планового. */
    @Когда("^в (.+?), (\\d{2}:\\d{2})(?::(\\d{2}))? срабатывает будильник этого приёма$")
    fun theAlarmOfThatIntakeFires(date: String, time: String, seconds: String?) {
        world.intakes.current()
        world.rememberState()
        world.reminders.reschedule(RescheduleReason.MANUAL)
        val moment = world.localMoment(date, if (seconds == null) time else "$time:$seconds")
        world.clock.moveTo(moment)
        world.reminders.deliverDue()
    }

    /** Ручное «Отложить» настоящим сценарием домена: повтор назначен на названный момент. */
    @Допустим("^человек отложил напоминание об этом приёме: повтор назначен на (.+?), (\\d{2}:\\d{2})$")
    fun thePersonSnoozedUntil(date: String, time: String) {
        val intake = world.intakes.current()
        val target = world.localMoment(date, time)
        world.clock.moveTo(intake.scheduledAt.plusMinutes(1))
        val minutes = Duration.between(world.clock.nowLocal(), target).toMinutes().toInt()
        assertTrue(
            runBlocking { world.reminders.snoozeReminder()(intake.id, minutes) },
            "the snooze must take effect",
        )
        assertEquals(
            world.clock.momentOf(target),
            world.reminders.alarmOf(intake.id),
            "the repeat must be armed for the named moment",
        )
    }

    @Допустим("^телефон задержал повтор и отдал его только в (.+?), (\\d{2}:\\d{2})$")
    fun thePhoneDelayedTheRepeat(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
    }

    /** Доставка давно назначенного повтора: система отдаёт его позже назначенного момента. */
    @Когда("^повтор срабатывает$")
    fun theRepeatFires() {
        world.reminders.deliverDue()
    }

    /** Повтор предъявлен: правило повторов порогом не затронуто (SKL-63). */
    @Тогда("^напоминание предъявляется$")
    fun theReminderIsPresented() {
        val intake = world.intakes.current()
        world.reminders.deliverDue()
        assertTrue(world.reminders.isNotificationShown(intake.id), "the repeat must be presented")
    }

    @Допустим("^первое напоминание об этом приёме показано (.+?), (\\d{2}:\\d{2})$")
    fun theFirstReminderWasShown(date: String, time: String) {
        val intake = world.intakes.currentOrScheduled()
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(world.localMoment(date, time))
        world.reminders.deliverDue()
        assertTrue(world.reminders.isNotificationShown(intake.id), "the premise says the first reminder was shown")
    }

    @И("^назначен настойчивый повтор на (.+?), (\\d{2}:\\d{2})$")
    fun thePersistentRepeatIsArmed(date: String, time: String) {
        val intake = world.intakes.currentOrScheduled()
        world.reminders.armRepeat(intake.id, world.clock.momentOf(world.localMoment(date, time)))
    }

    // --- Проверки журнала ----------------------------------------------------------------------

    @Тогда("^в журнале записан показ с отклонением (\\d+) секунд$")
    fun theJournalHoldsAShownWithDeviation(seconds: Long) {
        val shown = runBlocking { world.reminders.journal.last(ReminderEvent.SHOWN) }
        assertEquals(seconds, shown?.deviationSeconds, "the show must carry its deviation")
    }

    @И("^в журнале напоминаний есть запись срабатывания с отклонением (\\d+) секунд$")
    fun theJournalHoldsALateFiring(seconds: Long) {
        val suppressed = runBlocking { world.reminders.journal.last(ReminderEvent.SUPPRESSED) }
        assertEquals(SuppressReason.TOO_LATE.code, suppressed?.reason, "the firing must be recorded as too late")
        assertEquals(seconds, suppressed?.deviationSeconds, "the record must carry the actual deviation")
    }

    @И("^запись подписана как непредъявленная$")
    fun theRecordIsSignedAsNotPresented() {
        val suppressed = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.SUPPRESSED) }) {
            "the journal must carry the suppressed firing"
        }
        assertEquals(false, suppressed.delivered, "the record must say the show did not happen")
    }

    @И("^записи о показе этого приёма в журнале нет$")
    fun thereIsNoShowOfThatIntake() {
        val intake = world.intakes.current()
        val shown = runBlocking { world.reminders.journal.recent(RECENT_LIMIT) }
            .filter {
                (it.event == ReminderEvent.SHOWN || it.event == ReminderEvent.REPEATED) && it.intakeId == intake.id
            }
        assertEquals(emptyList<ReminderLogEntry>(), shown, "a suppressed firing is not a show")
    }

    @И("^названия лекарства в записи нет — только идентификаторы$")
    fun theRecordCarriesNoMedicationName() {
        val names = world.medications.names()
        val fields = runBlocking { world.reminders.journal.recent(RECENT_LIMIT) }.flatMap { entry ->
            listOfNotNull(entry.intakeId, entry.medicationId, entry.profileId, entry.reason, entry.watchResult)
        }
        assertTrue(fields.isNotEmpty(), "the journal must carry identifiers")
        assertTrue(
            fields.none { field -> names.any { field.contains(it) } },
            "the record must not carry the medication name (НФТ-19)",
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    /**
     * Доставить опоздавшее обычное срабатывание и убедиться, что оно подавлено.
     *
     * @return журнальная запись подавления: ею проверяются названное опоздание и подпись
     */
    private fun suppressFiring(moment: LocalDateTime): ReminderLogEntry {
        val intake = world.intakes.currentOrScheduled()
        world.rememberState()
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(moment)
        val shownBefore = world.reminders.shownWithChannels().size
        world.reminders.deliverDue()
        val suppressed = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.SUPPRESSED) }) {
            "the firing must be recorded as suppressed"
        }
        assertEquals(SuppressReason.TOO_LATE.code, suppressed.reason, "the firing must be suppressed by the threshold")
        assertEquals(
            shownBefore,
            world.reminders.shownWithChannels().size,
            "a suppressed firing must not be presented",
        )
        assertTrue(world.intakes.get(intake.id)?.isPending == true, "the intake must stay pending and markable")
        return suppressed
    }

    private fun record(entry: ReminderLogEntry) {
        runBlocking { world.reminders.journal.record(entry) }
    }

    private companion object {
        const val RECENT_LIMIT = 100
        const val SHOWN_LAST_WEEK = 3
        const val SECONDS_IN_MINUTE = 60L
    }
}

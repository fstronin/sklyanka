package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.DeferredReason
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Шаги приёмки SKL-81 — просроченный повтор не показывается (НАП-41, ОВ-99).
 *
 * Правило не копируется: восстановление плана идёт настоящим
 * [ru.stohastika.sklyanka.domain.reminder.RescheduleRemindersUseCase] поверх хранилища
 * отложенных в памяти, а порог показа решает доменный
 * [ru.stohastika.sklyanka.domain.reminder.DeferredShowPolicy]. «Опоздал» — это просто
 * момент на часах сценария; отдельной копии порога в шагах нет.
 *
 * Механика SKL-35 не пересказывается: откладывание делает настоящий
 * [ru.stohastika.sklyanka.domain.reminder.SnoozeReminderUseCase], «перезагрузка» —
 * стирание будильников при живых записях ([ru.stohastika.sklyanka.acceptance.world.ScenarioReminders.simulateReboot]).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class DeferredShowSteps(private val world: AcceptanceWorld) {

    /** Остаток лекарства на момент Предыстории: им проверяется «снятие повтора его не тронуло». */
    private var stockAtStart: BigDecimal? = null

    /** Снимок диагностики, собранный последним «открытием» экрана «Напоминания». */
    private var diagnostics: ReminderDiagnostics? = null

    // --- Предыстория ---------------------------------------------------------------------------

    @Допустим("^у профиля есть лекарство «([^»]+)» с приёмом ежедневно в (\\d{2}:\\d{2}) и дозой (\\S+) таблетк\\S*$")
    fun theActiveProfileHasADailyMedicationWithDose(name: String, time: String, dose: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            times = listOf(LocalTime.parse(time)),
        )
        world.medications.update(name) { it.copy(doseAmount = RussianDates.amount(dose)) }
        stockAtStart = world.medications.stockOf(name)
    }

    @Допустим("^приём «([^»]+)» на (.+?), (\\d{2}:\\d{2}) ожидается и не отмечен$")
    fun thatIntakeIsPendingAndUnmarked(name: String, date: String, time: String) {
        val intake = world.intakes.pending(name, world.localMoment(date, time))
        assertTrue(intake.isPending, "the intake must start unmarked")
    }

    @Допустим("^человек отложил напоминание об этом приёме на (\\d+) минут: повтор назначен на (.+?), (\\d{2}:\\d{2})$")
    fun thePersonSnoozedTheReminder(minutes: Int, date: String, time: String) {
        val intake = world.intakes.current()
        // Человек откладывает минутой позже планового времени — как в соседних сценариях
        // SKL-35: карточка показана в 14:00, нажатие в 14:01, повтор в 14:16.
        val at = intake.scheduledAt.plusMinutes(1)
        world.clock.moveTo(at)
        assertTrue(
            runBlocking { world.reminders.snoozeReminder()(intake.id, minutes) },
            "the snooze must take effect",
        )
        assertEquals(
            world.clock.momentOf(world.localMoment(date, time)),
            world.reminders.alarmOf(intake.id),
            "the repeat must be armed for the named moment",
        )
    }

    @Допустим("^с момента откладывания приложение не работало, и повтор не предъявлялся$")
    fun theAppWasOffSinceTheSnooze() {
        world.reminders.simulateReboot()
    }

    // --- Восстановление плана ------------------------------------------------------------------

    @Когда("^в (.+?), (\\d{2}:\\d{2}) приложение восстанавливает план напоминаний$")
    fun theAppRestoresThePlan(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        world.reminders.reschedule(RescheduleReason.BOOT)
    }

    @Когда("^в (.+?), (\\d{2}:\\d{2}):(\\d{2}) приложение восстанавливает план напоминаний$")
    fun theAppRestoresThePlanWithSeconds(date: String, hourMinute: String, seconds: String) {
        world.clock.moveTo(world.localMoment(date, "$hourMinute:$seconds"))
        world.reminders.reschedule(RescheduleReason.BOOT)
    }

    @Тогда("^напоминание об этом приёме предъявляется$")
    fun theReminderIsPresented() {
        val intake = world.intakes.current()
        // Система предъявляет прошедший будильник немедленно — здесь это доставка вручную.
        world.reminders.deliverDue()
        assertTrue(
            world.reminders.isNotificationShown(intake.id),
            "a repeat within the threshold must be shown",
        )
    }

    @Тогда("^напоминание об этом приёме не предъявляется$")
    fun theReminderIsNotPresented() {
        val intake = world.intakes.current()
        assertNull(world.reminders.alarmOf(intake.id), "a too-late repeat must not be armed")
        world.reminders.deliverDue()
        assertFalse(world.reminders.isNotificationShown(intake.id), "a too-late repeat must not be shown")
    }

    // --- Журнал --------------------------------------------------------------------------------

    @Тогда("^в журнале напоминаний есть запись `DEFERRED\\{dropped\\}` об этом приёме$")
    fun theJournalHoldsADroppedEntry() {
        val intake = world.intakes.current()
        val dropped = runBlocking { world.reminders.journal.recent(RECENT_LIMIT) }
            .filter {
                it.event == ReminderEvent.DEFERRED &&
                    it.reason == DeferredReason.DROPPED &&
                    it.intakeId == intake.id
            }
        assertEquals(1, dropped.size, "one drop is one journal entry")
    }

    @Тогда("^записи о предъявлении \\(первый показ или повтор\\) по этому приёму нет$")
    fun thereIsNoShowOfThisIntake() {
        val intake = world.intakes.current()
        val shown = runBlocking { world.reminders.journal.recent(RECENT_LIMIT) }
            .filter {
                (it.event == ReminderEvent.SHOWN || it.event == ReminderEvent.REPEATED) &&
                    it.intakeId == intake.id
            }
        assertEquals(emptyList<ReminderLogEntry>(), shown, "a drop is a journal fact, not a show")
    }

    @Тогда("^в журнале нет названия лекарства — только идентификаторы$")
    fun theJournalHasNoMedicationName() {
        val name = world.medications.names().last()
        val fields = runBlocking { world.reminders.journal.recent(RECENT_LIMIT) }.flatMap { entry ->
            listOfNotNull(entry.intakeId, entry.medicationId, entry.profileId, entry.reason, entry.watchResult)
        }
        assertTrue(fields.isNotEmpty(), "the journal must carry identifiers")
        assertTrue(fields.none { it.contains(name) }, "the journal must not carry the medication name")
    }

    // --- Состояние приёма ----------------------------------------------------------------------

    @Тогда("^плановое время приёма остаётся (.+?), (\\d{2}:\\d{2})$")
    fun theScheduledTimeStays(date: String, time: String) {
        assertEquals(world.localMoment(date, time), world.intakes.current().scheduledAt)
    }

    @Тогда("^остаток упаковки «([^»]+)» не меняется$")
    fun theStockOfThatMedicationIsUnchanged(name: String) {
        assertEquals(stockAtStart, world.medications.stockOf(name), "the drop must not touch the stock")
    }

    @Тогда("^приём по-прежнему можно отметить на «Сегодня»$")
    fun theIntakeIsStillMarkable() {
        assertTrue(world.intakes.current().isPending, "the intake must stay waiting and markable")
    }

    // --- Диагностика ---------------------------------------------------------------------------

    @Допустим("^ранее (.+?), в (\\d{2}:\\d{2}) первое напоминание по этому приёму уже было предъявлено$")
    fun theFirstReminderWasShownEarlier(date: String, time: String) {
        // Данность Предыстории: показ состоялся до откладывания. Пишется тем же журналом,
        // что и продукт: собрать снимок диагностики больше не из чего, а сценарий говорит
        // именно о разборе журнала, а не о механике показа.
        val intake = world.intakes.current()
        val moment = world.clock.momentOf(world.localMoment(date, time))
        runBlocking {
            world.reminders.journal.record(
                ReminderLogEntry(
                    occurredAt = moment,
                    event = ReminderEvent.SHOWN,
                    intakeId = intake.id,
                    medicationId = intake.medicationId,
                    scheduledAt = moment,
                    firedAt = moment,
                    delivered = true,
                ),
            )
        }
    }

    @И("^затем в (\\d{2}:\\d{2}) восстановленный повтор был снят без показа: опоздание больше порога$")
    fun theRestoredRepeatWasDroppedWithoutShowing(time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        world.reminders.reschedule(RescheduleReason.BOOT)
        assertNull(world.reminders.alarmOf(world.intakes.current().id), "the too-late repeat must not be armed")
    }

    @Когда("^человек открывает экран «Напоминания»$")
    fun thePersonOpensTheRemindersScreen() {
        // Настоящая сборка снимка диагностики из тех же портов, что в `app`: последним
        // напоминанием считается показ или повтор, а снятый повтор — нет.
        val useCase = BuildReminderDiagnosticsUseCase(
            journal = world.reminders.journal,
            alarms = world.reminders.alarms,
            permissions = world.system,
            plan = world.reminders.planSource(),
            reliability = ReadReminderReliabilityUseCase(world.system),
            medications = world.repositories.medicationStore,
            measurement = world.reminders.measurement,
            acknowledgements = world.reminders.acknowledgements,
            time = world.clock.time,
        )
        diagnostics = runBlocking { useCase() }
    }

    @Тогда("^последним напоминанием указано раннее предъявление в (\\d{2}:\\d{2}), а не снятый повтор$")
    fun theLastShownIsTheEarlierMoment(time: String) {
        val snapshot = requireNotNull(diagnostics) { "the reminders screen must be open" }
        val expected = world.clock.momentOf(world.clock.today().atTime(LocalTime.parse(time)))
        assertEquals(ReminderEvent.SHOWN, snapshot.lastShown?.event, "the drop must not be taken for a show")
        assertEquals(expected, snapshot.lastShown?.occurredAt, "the earlier show must stay the last one")
    }

    @Тогда("^в журнале снятие подписано как отложенное срабатывание$")
    fun theDropIsSignedAsADeferredEvent() {
        val dropped = runBlocking { world.reminders.journal.recent(RECENT_LIMIT) }
            .filter { it.event == ReminderEvent.DEFERRED && it.reason == DeferredReason.DROPPED }
        assertTrue(dropped.isNotEmpty(), "the drop must be signed as a deferred event")
    }

    private companion object {
        const val RECENT_LIMIT = 100
    }
}

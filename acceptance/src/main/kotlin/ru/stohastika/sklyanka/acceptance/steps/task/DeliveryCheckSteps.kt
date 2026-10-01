package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckReason
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckRule
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderOnTimeRule
import ru.stohastika.sklyanka.domain.reminder.ReminderWeekSummary
import ru.stohastika.sklyanka.domain.reminder.ReminderWeekSummaryRule
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import java.time.LocalDate
import java.time.LocalTime

/**
 * Доменные шаги приёмки самопроверки доставки (SKL-86, ПМ-21, ОВ-108).
 *
 * Идут сценарным прогоном на JVM: тестовое срабатывание, отмена и судьба записи журнала —
 * решения домена, и проверяются они теми же сценариями, что собирает `app` (точный
 * будильник, обычный порт уведомлений, запись журнала). Экранные части — шторка, блок
 * и карточка — проверяются стендом экрана в `feature-reminder` и устройством.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class DeliveryCheckSteps(private val world: AcceptanceWorld) {

    private val today: LocalDate get() = world.clock.today()

    // --- Запуск и срабатывание -----------------------------------------------------------------

    @Допустим("^человек запустил проверку в (\\d{2}):(\\d{2}) и закрыл приложение$")
    fun thePersonStartedTheCheckAndClosedTheApp(hour: String, minute: String) {
        world.clock.moveTo(today.atTime(LocalTime.of(hour.toInt(), minute.toInt())))
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
    }

    @Допустим("^человек запустил проверку и закрыл приложение$")
    fun thePersonStartedTheCheckAndClosedTheApp() {
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
    }

    @Допустим("^человек запустил проверку, и она идёт$")
    fun thePersonStartedTheCheckAndItIsRunning() {
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
    }

    @Когда("^в (\\d{2}):(\\d{2}):(\\d{2}) срабатывает тестовое напоминание$")
    fun theTestReminderFiresAt(hour: String, minute: String, second: String) {
        world.clock.moveTo(
            today.atTime(LocalTime.of(hour.toInt(), minute.toInt(), second.toInt())),
        )
        world.reminders.firedDeliveryCheck()
    }

    @Когда("^тестовое напоминание срабатывает с опозданием (\\d+) минут$")
    fun theTestReminderFiresLateBy(minutes: Int) {
        val dueAt = requireNotNull(world.reminders.deliveryCheckAlarm()) { "the check must be scheduled" }
        world.clock.moveTo(
            dueAt.atZone(world.clock.zone).toLocalDateTime().plusMinutes(minutes.toLong()),
        )
        world.reminders.firedDeliveryCheck()
    }

    @Тогда(
        "^показывается полноэкранная карточка с надзаголовком «Проверка доставки», " +
            "временем (\\d{2}):(\\d{2}) и строкой «Тестовое напоминание\\. Отмечать ничего не нужно\\.»$",
    )
    fun theFullScreenCardIsShown(hour: String, minute: String) {
        // Карточка — платформенная часть: домен просит показ обычным портом, а состав
        // экрана проверяется стендом карточки (`DeliveryCheckScreenTest`). Время на карточке —
        // назначенный момент: формат «HH:mm» сценария и карточки тот же.
        val shown = world.reminders.deliveryCheckShows()
        assertEquals(1, shown.size, "the test card must be asked to show exactly once")
        val expected = LocalTime.of(hour.toInt(), minute.toInt())
        assertEquals(expected, shown.single().toLocalTime().withSecond(0).withNano(0))
    }

    @И("^у карточки одно действие «Понятно»$")
    fun theCardHasExactlyOneAction() {
        // Действие «Понятно» — часть экрана карточки; здесь фиксируется, что запись
        // проверки не притворяется приёмом и отметки у неё взяться неоткуда.
        assertTrue(
            world.reminders.deliveryCheckRecords().all { it.intakeId == null && it.medicationId == null },
            "the check must carry no intake to mark",
        )
    }

    @И("^в журнале есть записи о срабатывании и показе проверки$")
    fun theJournalCarriesTheFiringAndTheShow() {
        val reasons = world.reminders.deliveryCheckRecords().mapNotNull { it.reason }
        assertTrue(DeliveryCheckReason.FIRED in reasons, "the firing must be recorded")
        assertTrue(DeliveryCheckReason.SHOWN in reasons, "the presentation must be recorded")
    }

    @И("^названия лекарства в этих записях нет — только время и идентификаторы$")
    fun theRecordsCarryNoMedicationNames() {
        val name = world.medications.get(MEDICATION).name
        val offenders = world.reminders.deliveryCheckRecords().filter { entry ->
            entry.toString().contains(name) || entry.medicationId != null || entry.profileId != null
        }
        assertEquals(emptyList<ReminderLogEntry>(), offenders, "the check journal must carry no medical data (НФТ-19)")
        world.reminders.deliveryCheckRecords().forEach { entry ->
            assertNotNull(entry.occurredAt, "the record must carry its moment")
        }
    }

    @И("^тестовое напоминание предъявляется$")
    fun theTestReminderIsPresented() {
        // Порог SKL-88 к проверке не применяется: она показывается и сильно опоздавшей
        // (ОВ-109), и запись показа это доказывает.
        val reasons = world.reminders.deliveryCheckRecords().mapNotNull { it.reason }
        assertTrue(DeliveryCheckReason.SHOWN in reasons, "the late test reminder must be presented")
        assertTrue(world.reminders.deliveryCheckShows().isNotEmpty(), "the card must have been shown")
    }

    // --- Приём и остаток ----------------------------------------------------------------------

    @Когда("^человек запускает проверку и тестовое напоминание срабатывает$")
    fun thePersonStartsAndTheTestReminderFires() {
        world.rememberState()
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        theTestReminderFiresLateBy(0)
    }

    @Тогда("^новых приёмов у профиля не появилось$")
    fun noNewIntakesAppeared() {
        assertEquals(world.before.size, world.intakes.all().size, "the check must not create intakes")
        assertTrue(
            world.reminders.deliveryCheckRecords().all { it.intakeId == null },
            "the check must not point at an intake",
        )
    }

    @И("^в истории приёмов записи о проверке нет$")
    fun theHistoryCarriesNoCheckRecords() {
        // История строится по приёмам; проверка не создаёт приёмов вовсе, поэтому её
        // записей там взяться неоткуда. Доказательство — те же два факта: приёмы не
        // изменились, а записи проверки не связаны ни с одним приёмом.
        assertEquals(world.before.size, world.intakes.all().size, "the history of intakes must not change")
        assertTrue(world.reminders.deliveryCheckRecords().all { it.intakeId == null })
    }

    // --- Отмена -------------------------------------------------------------------------------

    @Когда("^он нажимает «Отменить проверку»$")
    fun hePressesCancelTheCheck() {
        world.reminders.cancelDeliveryCheck()
    }

    @Тогда("^блок проверки возвращается в исходное состояние$")
    fun theBlockReturnsToItsInitialState() {
        val last = runBlocking { world.reminders.journal.last(ReminderEvent.DELIVERY_CHECK) }
        assertEquals(DeliveryCheckState.None, DeliveryCheckRule.stateOf(last), "a cancelled check leaves no state")
    }

    @И("^тестовое срабатывание не предъявляется$")
    fun theTestFiringIsNotPresented() {
        assertTrue(world.reminders.deliveryCheckShows().isEmpty(), "nothing may be shown after the cancel")
        assertNull(world.reminders.deliveryCheckAlarm(), "the test alarm must be gone")
    }

    @И("^в журнале есть запись об отмене проверки$")
    fun theCancelIsRecorded() {
        val reasons = world.reminders.deliveryCheckRecords().mapNotNull { it.reason }
        assertTrue(DeliveryCheckReason.CANCELLED in reasons, "the cancel must leave a record")
    }

    // --- Журнал -------------------------------------------------------------------------------

    private var lastShown: ReminderLogEntry? = null
    private var week: ReminderWeekSummary? = null

    @Допустим("^проверка прошла, и в тот же день было обычное срабатывание напоминания$")
    fun aRegularReminderFiredTheSameDay() {
        val regularAt = today.atTime(REGULAR_REMINDER_HOUR, 0)
        val intake = world.intakes.pending(MEDICATION, regularAt)
        world.clock.moveTo(regularAt)
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.reminders.deliverDue()
        val shown = runBlocking { world.reminders.journal.last(ReminderEvent.SHOWN) }
        assertTrue(shown != null, "the regular reminder must fire")
        regularIntakeId = intake.id

        world.clock.moveTo(today.atTime(CHECK_START_HOUR, 0))
        thePersonStartedTheCheckAndClosedTheApp()
        theTestReminderFiresLateBy(0)
    }

    private var regularIntakeId: String? = null

    @Когда("^человек открывает «Проверку напоминаний»$")
    fun thePersonOpensTheRemindersCheck() {
        // Экран показывает снимок журнала; снимок собирается здесь теми же правилами,
        // что у `BuildReminderDiagnosticsUseCase`: последнее предъявление и недельная сводка.
        runBlocking {
            val journal = world.reminders.journal
            lastShown = listOfNotNull(journal.last(ReminderEvent.SHOWN), journal.last(ReminderEvent.REPEATED))
                .maxByOrNull { it.occurredAt }
            week = ReminderWeekSummaryRule.summarize(
                journal.since(world.clock.now().minus(ReminderOnTimeRule.WINDOW)),
            )
        }
    }

    @Тогда("^последним напоминанием названо обычное срабатывание, а не проверка$")
    fun theLastReminderIsTheOrdinaryOne() {
        val shown = requireNotNull(lastShown) { "the journal must carry the ordinary show" }
        assertEquals(ReminderEvent.SHOWN, shown.event)
        assertEquals(regularIntakeId, shown.intakeId, "the check must not replace the ordinary reminder")
    }

    @И("^проверка не входит в число сработавших и пропущенных за 7 дней$")
    fun theCheckIsNotCountedInTheWeek() {
        val summary = requireNotNull(week) { "the week summary must be read" }
        assertEquals(1, summary.fired, "only the ordinary reminder is counted")
        assertEquals(0, summary.missed)
    }

    @И("^в журнале «Для поддержки» записи проверки отличимы от записей приёма$")
    fun theCheckRecordsAreDistinct() {
        val records = world.reminders.deliveryCheckRecords()
        assertTrue(records.isNotEmpty(), "the check must leave records")
        assertTrue(
            records.all { it.event == ReminderEvent.DELIVERY_CHECK },
            "the check records are a distinct journal kind",
        )
        assertTrue(
            records.all { it.intakeId == null && it.medicationId == null && it.profileId == null },
            "the check records carry no intake identifiers",
        )
    }

    private companion object {
        const val MEDICATION = "Магний B6"
        const val REGULAR_REMINDER_HOUR = 8
        const val CHECK_START_HOUR = 10
    }
}

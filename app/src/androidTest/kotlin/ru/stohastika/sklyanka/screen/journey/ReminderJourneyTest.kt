package ru.stohastika.sklyanka.screen.journey

import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.feature.reminder.ReminderActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SKIP
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SNOOZE
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_TAKEN
import ru.stohastika.sklyanka.feature.reminder.TAG_NAME
import ru.stohastika.sklyanka.feature.reminder.TAG_REMINDER
import ru.stohastika.sklyanka.feature.reminder.TAG_TIME
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.test.R
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Поход «Напоминание» (SKL-140, реестр походов № 9).
 *
 * Три напоминания одного дня: карточку на 08:00 человек закрывает отметкой «Принято»,
 * карточку на 12:00 — откладыванием, карточку на 16:00 — пропуском. Поход утверждает
 * следствия в **данных**: статус приёма, остаток упаковки, отложенное срабатывание
 * и журналы — операций и напоминаний.
 *
 * Отложенное срабатывание человека оставляет два следа, и поход утверждает оба: запись
 * `DeferredReminders` — обещание, по которому повтор переживёт перезагрузку (SKL-35), —
 * и запись `ReminderEvent.SNOOZED` в журнале напоминаний, след самого действия с плановым
 * и отложенным моментом. Обещание система снимает или переписывает, как только повтор
 * показан, отмечен или исчерпан; след действия живёт в журнале, и утверждение о поступке
 * человека не зависит от того, когда именно система покажет повтор.
 *
 * Настоящий будильник ставит только система, и ждать его срабатывания набор не имеет
 * права (`TDD §1`). Поэтому карточка открывается **своим же намерением продукта** —
 * тем, которое несёт полноэкранное уведомление (`ReminderActivity.intentFor`): это и есть
 * замена нажатия по уведомлению. Доставку уведомления проверяют испытания оснастки
 * и человек.
 *
 * Одно лекарство с тремя временами, а не три лекарства на одно время: карточка собирается
 * по слоту, поэтому у каждого приёма своя карточка со своими действиями. Одновременные
 * приёмы показаны одной карточкой со строками, но кнопки строки тега не несут — нажать
 * их поход не может.
 *
 * Часы идут только вперёд: стенд стоит на 09:01, на час позже первого приёма (напоминание
 * на 08:00 уже сработало и ждёт человека — как у того, кто взял телефон позже), а к 12:00
 * и 16:00 поход переводит их явно (`TDD §10`).
 *
 * Допущение о правах: уведомления и доступ к политике «Не беспокоить» выдаёт скрипт группы
 * (`TDD §9`). Точные будильники процессу прогона не выданы: продукт ставит их от имени
 * приложения, а проверки идут от имени пакета стенда, и система отказывает — в журнале
 * устройства это видно строкой `Package ru.stohastika.sklyanka.test … lost permission to set
 * exact alarms!`. Поэтому поход и не опирается на срабатывание будильника: карточку он
 * открывает намерением продукта, а обещание повтора читает в записи, не дожидаясь показа.
 */
@HiltAndroidTest
class ReminderJourneyTest : JourneyTest() {

    private val name: String get() = testContext.getString(R.string.device_test_medication_name, NAME_SUFFIX)

    /**
     * Остаток упаковки читается настоящим репозиторием продукта: читателя остатка в сиде нет,
     * а следствие «Принято» живёт в базе, а не в состоянии экрана.
     */
    @Inject
    lateinit var medications: MedicationRepository

    @Test
    fun thePersonSettlesTheThreeRemindersOfTheDay() {
        seedMedication(
            id = MEDICATION_ID,
            name = name,
            times = listOf(MORNING, NOON, EVENING),
            stockLeft = STOCK,
        )
        planIntakes()

        launchApp()
        awaitRoot()
        capture("day-waiting")

        openReminder(MORNING)
        capture("reminder-morning")
        click(TAG_ACTION_TAKEN)
        awaitScreenClosed(ReminderActivity::class.java)
        awaitRoot()
        capture("day-after-taken")

        val taken = intakeAt(MORNING)
        assertEquals("the taken action must mark the intake", IntakeStatus.TAKEN, taken.status)
        assertEquals("the dose must leave the stock", stockAfterOneDose(), stockLeft())
        assertEquals("the mark must leave one trace", 1, opLogCount(OpEvent.INTAKE_MARKED))

        // Второе напоминание ещё не срабатывало: поход ведёт часы вперёд, до его слота.
        DeviceTestClock.advanceTo(DeviceTestClock.today.atTime(NOON))
        openReminder(NOON)
        capture("reminder-noon")
        click(TAG_ACTION_SNOOZE)
        awaitScreenClosed(ReminderActivity::class.java)
        awaitRoot()
        capture("day-after-snooze")

        val snoozed = intakeAt(NOON)
        assertEquals("the snooze must leave the intake waiting", IntakeStatus.PENDING, snoozed.status)
        assertEquals("the planned time must not move", DeviceTestClock.today.atTime(NOON), snoozed.scheduledAt)
        val promise = requireNotNull(DeviceTestSeed.deferredFor(targetContext, snoozed.id)) {
            "the snooze must leave a deferred record"
        }
        assertEquals("a manual snooze is the first attempt", 0, promise.attempt)
        assertTrue("the deferred firing must move forward", promise.fireAt.isAfter(DeviceTestClock.source.now()))

        // Второй след того же действия — запись журнала напоминаний: обещание повтора
        // живёт до первого показа, а след действия его переживает (НАП-30).
        val trace = requireNotNull(DeviceTestSeed.lastJournalEvent(targetContext, ReminderEvent.SNOOZED)) {
            "the snooze must be recorded in the reminder journal"
        }
        assertEquals("the trace must belong to the snoozed intake", snoozed.id, trace.intakeId)
        assertEquals("the trace must keep the planned moment", planned(NOON), trace.scheduledAt)
        assertTrue(
            "the deferred show must be later than the planned time",
            requireNotNull(trace.firedAt).isAfter(planned(NOON)),
        )
        assertEquals("the snooze must not touch the stock", stockAfterOneDose(), stockLeft())
        assertEquals("the snooze must not mark anything", 1, opLogCount(OpEvent.INTAKE_MARKED))

        DeviceTestClock.advanceTo(DeviceTestClock.today.atTime(EVENING))
        openReminder(EVENING)
        capture("reminder-evening")
        click(TAG_ACTION_SKIP)
        awaitScreenClosed(ReminderActivity::class.java)
        awaitRoot()
        capture("day-after-skipped")

        val skipped = intakeAt(EVENING)
        assertEquals("the skip must mark the intake skipped", IntakeStatus.SKIPPED, skipped.status)
        assertEquals("a skip must not touch the stock", stockAfterOneDose(), stockLeft())
        assertTrue("the skip must leave a trace in the journal", opLogHas(OpEvent.INTAKE_MARKED))
        assertEquals("both human marks must be traced", 2, opLogCount(OpEvent.INTAKE_MARKED))
    }

    /**
     * Открыть карточку напоминания слота [at] — намерением продукта, тем же, каким её
     * поднимает полноэкранное уведомление (нажать по уведомлению поход не умеет).
     *
     * Карточка обязана быть про **этот** слот: время на ней и название лекарства —
     * единственное, чем она отличается от карточек других времён.
     */
    private fun openReminder(at: LocalTime) {
        launchScreen(ReminderActivity.intentFor(targetContext, ownerProfile.id, DeviceTestClock.today.atTime(at)))
        awaitActivity(ReminderActivity::class.java)
        waitForTag(TAG_REMINDER)
        waitUntil(SCREEN_TIMEOUT_MS, "карточки приёма «$name»") { exists(TAG_NAME) }
        assertEquals("the card must show the fired slot", slotLabel(at), nodeText(TAG_TIME))
        assertEquals("the card must name the medication of the slot", name, nodeText(TAG_NAME))
    }

    /** Приём слота [at]: день похода утверждается состоянием приёмов, а не экраном. */
    private fun intakeAt(at: LocalTime): Intake {
        val scheduled = DeviceTestClock.today.atTime(at)
        return dayIntakes().singleOrNull { it.medicationId == MEDICATION_ID && it.scheduledAt == scheduled }
            ?: error("the day must hold the intake of $MEDICATION_ID at $scheduled")
    }

    /** Остаток упаковки числом: экран показал бы его словом, а поход спрашивает хранилище. */
    private fun stockLeft(): BigDecimal? = runBlocking { medications.get(MEDICATION_ID)?.stockLeft }

    /**
     * Остаток после одной списанной дозы.
     *
     * `seedMedication` заводит лекарство доменным черновиком, а доза у него — единица
     * (`MedicationDraft.new`), и «Принято» списывает ровно дозу (`StockLedger`).
     */
    private fun stockAfterOneDose(): BigDecimal = STOCK.subtract(DOSE)

    /** Плановый момент приёма как точка на абсолютной шкале — так его пишет журнал напоминаний. */
    private fun planned(at: LocalTime): Instant =
        DeviceTestClock.today.atTime(at).atZone(DeviceTestClock.zone).toInstant()

    /**
     * Подпись времени карточки: продукт печатает слот шаблоном `reminder_time_pattern`,
     * и во всех шести локалях он один — `HH:mm`.
     */
    private fun slotLabel(at: LocalTime): String = at.format(DateTimeFormatter.ofPattern(TIME_PATTERN))

    private companion object {
        /** Идентификатор лекарства похода: по нему читаются приёмы и остаток. */
        const val MEDICATION_ID = "dev-med-journey-reminder"

        /** Кусок имени стенда: имя лекарства обязано быть латиницей (НФТ-42). */
        const val NAME_SUFFIX = "journey-reminder"

        /** Шаблон подписи времени у продукта (`R.string.reminder_time_pattern`). */
        const val TIME_PATTERN = "HH:mm"

        /** Начальный остаток упаковки: после отметки «Принято» он обязан упасть на дозу. */
        val STOCK: BigDecimal = BigDecimal("9")

        /** Доза приёма, которую списывает отметка. */
        val DOSE: BigDecimal = BigDecimal.ONE

        val MORNING: LocalTime = LocalTime.of(8, 0)
        val NOON: LocalTime = LocalTime.of(12, 0)
        val EVENING: LocalTime = LocalTime.of(16, 0)
    }
}

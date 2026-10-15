package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OpLogRecord
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.feature.today.TAG_PRN_CARD
import ru.stohastika.sklyanka.feature.today.TAG_PRN_LIMIT_REASON
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.component.TAG_MISSED_STATUS
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.ui.component.TAG_TAKEN_STATUS
import java.math.BigDecimal
import ru.stohastika.sklyanka.feature.today.R as TodayR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Поход «День: отметка, отмена, пропуск и приём по потребности» (SKL-140, реестр походов № 3).
 *
 * Человек проходит день целиком: отмечает приём «Принято», тут же отменяет отметку в окне
 * отмены, отмечает «Пропустить» и принимает лекарство «по потребности» до суточного предела.
 * Поход идёт на настоящем файле базы: приём, остаток и след в журнале операций — записи,
 * а не состояние экрана, и утверждаются они по данным.
 *
 * **Три расхождения со сценарием, названные честно.**
 *
 * 1. Время приёма — [DeviceTestClock.scheduledTime] (09:00), а не «08:00» сценария: это
 *    посеянное время волны, и базовый момент стоит на минуту позже него, поэтому приём
 *    сегодня уже наступил и ещё не считается несостоявшимся (`OutageDetector.GRACE`).
 *    Курс начинается вчера (правило `seedMedication`), иначе сегодняшнего приёма
 *    не существовало бы вовсе (`ScheduleExpander.skipPastTimesOnCourseStart`).
 * 2. Второе лекарство заводит стенд, а не форма: `seedMedication` умеет только расписание,
 *    и имени «Нурофен» в стенде нет. Имя берётся у существующего ресурса стенда
 *    (`device_test_medication_name` с добавкой), а запись собирается тем же доменным черновиком, что
 *    у формы (`MedicationDraft`: режим `PRN`, пустой список времён, предел 3 на сутки), —
 *    инварианты записи те же (ИНВ-6, ИНВ-17).
 * 3. Остаток утверждается журналом операций, а не экраном карточки: записи списания
 *    и возврата несут значения «до» и «после», и по ним видно, что доза списана ровно одна
 *    и ровно она же возвращена. Карточку остатка показывает поход № 4 («Остаток, порог
 *    покупки и пополнение»), а здесь важно следствие отметки, а не вид экрана.
 *
 * **Чего поход не утверждает.** Слов «пропущено осознанно» на дне нет: осознанный пропуск
 * и пропуск без отметки на экране дня выглядят одинаково (`BuildTodayAgenda` — сделать уже
 * нечего), различие живёт в коде состояния (`SKIPPED`), и поход утверждает именно его. Ту
 * историю, где пропуск назван словами, показывает поход № 6.
 *
 * Отказ по суточному пределу продукт выражает недоступной кнопкой с названной человеку
 * причиной (ПР-7, НФТ-33), а не тостом: четвёртое нажатие не начинается, и записи не
 * появляется — это и есть отказ, а не только его подпись.
 */
@HiltAndroidTest
class TodayMarkingJourneyTest : JourneyTest() {

    @Test
    fun thePersonMarksUndoesSkipsAndTakesOnDemand() {
        seedDay()
        launchApp()
        awaitRoot()
        waitUntil(SCREEN_TIMEOUT_MS, "обеих карточек на «Сегодня»") {
            textExists(magnesium) && exists(TAG_PRN_CARD)
        }
        capture("day-both")

        markTakenAndUndo()
        markSkipped()
        takeOnDemandUpToTheDailyLimit()
    }

    /** Сид дня: приём по расписанию с остатком 9 и лекарство «по потребности» с пределом 3. */
    private fun seedDay() {
        seedMedication(
            id = MAGNESIUM_ID,
            name = magnesium,
            times = listOf(DeviceTestClock.scheduledTime),
            stockLeft = BigDecimal(STOCK_LEFT),
        )
        seedOnDemandMedication()
        planIntakes()
    }

    /**
     * Лекарство «по потребности» — тем же доменным путём, что и у формы лекарства.
     *
     * Тот же черновик и тот же репозиторий, что у `seedMedication`: отличается только режим —
     * у «по потребности» времён нет вовсе (ИНВ-6), а есть предел на сутки (ИНВ-17).
     */
    private fun seedOnDemandMedication() {
        val draft = MedicationDraft.new(
            profileId = ownerProfile.id,
            today = DeviceTestClock.today.minusDays(1),
        ).copy(
            name = painkiller,
            doseAmount = BigDecimal.ONE,
            scheduleMode = ScheduleMode.PRN,
            maxPerDay = DAILY_LIMIT,
        )
        DeviceTestSeed.medication(targetContext, draft.toMedication(PRN_ID))
    }

    /**
     * Отметка «Принято», след в журнале, списание дозы и отмена в окне отмены.
     *
     * Отмена идёт сразу за кадром: окно отмены живёт пять секунд
     * (`UndoableActionStore.WINDOW`), и долгая работа между отметкой и отменой сняла бы
     * возможность отменить, а не саму отметку.
     */
    private fun markTakenAndUndo() {
        pressInCard(magnesium, ui(UiR.string.action_taken))
        waitUntil(SCREEN_TIMEOUT_MS, "карточки, названной принятой") { exists(TAG_TAKEN_STATUS) }

        val taken = scheduledIntake()
        assertEquals("the intake must be stored as taken", IntakeStatus.TAKEN, taken.status)
        assertTheMarkIsTracedWithoutMedicalData(taken)
        val writeOff = journalEntry(OpEvent.STOCK_DECREMENTED) { it.medicationId == MAGNESIUM_ID }
        assertEquals("the mark must write off the packed stock", STOCK_LEFT, writeOff.before)
        assertEquals("the mark must write off one dose", STOCK_LEFT_AFTER_MARK, writeOff.after)
        capture("day-taken")

        compose.onNodeWithText(ui(TodayR.string.today_undo_action), useUnmergedTree = true).performClick()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия окна отмены") { !exists(TAG_SNACKBAR) }
        waitUntil(SCREEN_TIMEOUT_MS, "возврата приёма в ожидание") {
            textExists(ui(UiR.string.action_taken))
        }

        assertEquals(
            "the undo must return the intake to pending",
            IntakeStatus.PENDING,
            scheduledIntake().status,
        )
        assertTrue("the undo must be in the journal", opLogHas(OpEvent.INTAKE_MARK_UNDONE))
        val restore = journalEntry(OpEvent.STOCK_RESTORED) { it.medicationId == MAGNESIUM_ID }
        assertEquals("the undo must give the dose back", STOCK_LEFT_AFTER_MARK, restore.before)
        assertEquals("the stock must be back at the seeded value", STOCK_LEFT, restore.after)
    }

    /**
     * След отметки в журнале без медицинских данных (TDD §11).
     *
     * В записи — только коды и идентификаторы: название лекарства и доза ищутся в тех же
     * текстах, которыми их называет экран дня, и не находятся.
     */
    private fun assertTheMarkIsTracedWithoutMedicalData(intake: Intake) {
        val mark = journalEntry(OpEvent.INTAKE_MARKED) { it.intakeId == intake.id }
        assertEquals("the record must name the previous state", IntakeStatus.PENDING.name, mark.before)
        assertEquals("the record must name the new state", IntakeStatus.TAKEN.name, mark.after)
        assertEquals("the record must identify the intake by id", intake.id, mark.intakeId)
        assertNull("the record must carry no free text", mark.detail)
        val texts = mark.textFields()
        assertTrue(
            "the record must carry neither the medication name nor its dose: $texts",
            magnesium !in texts && doseLabel !in texts,
        )
    }

    /** Отметка «Пропустить»: состояние `SKIPPED`, остаток не тронут. */
    private fun markSkipped() {
        pressInCard(magnesium, ui(UiR.string.action_skip))
        waitUntil(SCREEN_TIMEOUT_MS, "окна отмены пропуска и пропущенной карточки") {
            exists(TAG_MISSED_STATUS) && textExists(skipNotice)
        }
        capture("day-skipped")

        val skipped = scheduledIntake()
        assertEquals("the skip must be stored as skipped", IntakeStatus.SKIPPED, skipped.status)
        val skip = journalEntry(OpEvent.INTAKE_MARKED) {
            it.intakeId == skipped.id && it.after == IntakeStatus.SKIPPED.name
        }
        assertEquals(
            "the skip must be applied to the intake the undo returned to pending",
            IntakeStatus.PENDING.name,
            skip.before,
        )
        assertEquals(
            "the journal must keep both decisions of the day",
            2,
            journalCount(OpEvent.INTAKE_MARKED) { it.intakeId == skipped.id },
        )
        assertEquals(
            "the skip must not write off a second dose",
            1,
            journalCount(OpEvent.STOCK_DECREMENTED) { it.medicationId == MAGNESIUM_ID },
        )
        assertEquals(
            "the skip must not give the dose back again",
            1,
            journalCount(OpEvent.STOCK_RESTORED) { it.medicationId == MAGNESIUM_ID },
        )
    }

    /**
     * «Принять» до суточного предела: три записи, четвёртое нажатие продукт не выполняет.
     *
     * Окно отмены прошлого нажатия обязано закрыться прежде нового: иначе появление окна
     * не отличит новую запись от прежней, и утверждение о числе прочтёт старое. А появление
     * окна — свидетельство, что запись уже легла в базу: `TodayViewModel` открывает окно
     * после состоявшегося создания (`PrnOutcome.Created`), а не до него.
     */
    private fun takeOnDemandUpToTheDailyLimit() {
        assertEquals(
            "the on-demand medication must start the day without records",
            0,
            onDemandIntakes().size,
        )
        repeat(DAILY_LIMIT) { press ->
            waitUntil(SCREEN_TIMEOUT_MS, "закрытия окна отмены перед нажатием № ${press + 1}") {
                !exists(TAG_SNACKBAR)
            }
            pressOnDemandTake()
            waitUntil(SCREEN_TIMEOUT_MS, "окна отмены записи № ${press + 1}") { exists(TAG_SNACKBAR) }
            assertEquals(
                "the on-demand press № ${press + 1} must add its own record",
                press + 1,
                onDemandIntakes().size,
            )
        }
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия окна отмены третьей записи") { !exists(TAG_SNACKBAR) }
        capture("day-at-the-limit")
        assertTheDailyLimitRefuses()
    }

    /** Предел на сутки: кнопка недоступна, причина названа, четвёртой записи нет. */
    private fun assertTheDailyLimitRefuses() {
        compose.onNodeWithText(ui(UiR.string.action_take)).assertIsNotEnabled()
        waitForTag(TAG_PRN_LIMIT_REASON)
        assertEquals(
            "the refusal must name the daily limit",
            targetContext.resources.getQuantityString(
                TodayR.plurals.today_prn_limit_reached,
                DAILY_LIMIT,
                DAILY_LIMIT,
            ),
            nodeText(TAG_PRN_LIMIT_REASON),
        )
        val records = onDemandIntakes()
        assertEquals("the refusal must not add a fourth record", DAILY_LIMIT, records.size)
        assertEquals(
            "no refused press may leave a trace of creation",
            DAILY_LIMIT,
            journalCount(OpEvent.INTAKE_PRN_CREATED) { it.medicationId == PRN_ID },
        )
        records.forEach { record ->
            assertEquals("every on-demand record must be stored as taken", IntakeStatus.TAKEN, record.status)
            assertEquals(
                "an on-demand record carries no planned time: its slot is the moment of the press",
                record.actedAt?.atZone(DeviceTestClock.zone)?.toLocalDateTime()?.withSecond(0)?.withNano(0),
                record.scheduledAt,
            )
        }
        assertEquals(
            "no on-demand record may stand on the scheduled time",
            0,
            records.count { it.scheduledAt.toLocalTime() == DeviceTestClock.scheduledTime },
        )
    }

    /** Нажатие подписи действия в карточке названного лекарства: строк дня бывает несколько. */
    private fun pressInCard(medication: String, label: String) {
        compose.onNode(
            hasText(label) and hasAnyAncestor(cardOf(medication)),
            useUnmergedTree = true,
        ).performClick()
    }

    /** Карточка названного лекарства: имя лежит у её потомков, а не у самого узла карточки. */
    private fun cardOf(medication: String): SemanticsMatcher =
        hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(medication))

    /**
     * Кнопка «Принять» карточки «по потребности»: своего тега у неё нет, а подпись есть.
     *
     * Немёрженое дерево — как у нажатия в приёмке: подпись и действие у одного узла.
     */
    private fun pressOnDemandTake() {
        compose.onNode(
            hasText(ui(UiR.string.action_take)) and hasAnyAncestor(hasTestTag(TAG_PRN_CARD)),
            useUnmergedTree = true,
        ).performClick()
    }

    /** Приём по расписанию: он один на дне, и его состояние поход утверждает. */
    private fun scheduledIntake(): Intake {
        val ofMedication = dayIntakes().filter { it.medicationId == MAGNESIUM_ID }
        assertEquals("the day must hold exactly one intake of «$magnesium»", 1, ofMedication.size)
        return ofMedication.single()
    }

    /** Записи «по потребности» за сегодня: они рождаются нажатием, а не расписанием. */
    private fun onDemandIntakes(): List<Intake> = dayIntakes().filter { it.medicationId == PRN_ID }

    /** Ровно одна запись журнала по условию: так утверждается и след, и его единственность. */
    private fun journalEntry(event: OpEvent, matches: (OpLogRecord) -> Boolean): OpLogRecord {
        val found = opLog().filter { it.event == event && matches(it) }
        assertEquals("the journal must hold exactly one $event record", 1, found.size)
        return found.single()
    }

    /** Число записей журнала по условию: повторное действие видно числом, а не признаком. */
    private fun journalCount(event: OpEvent, matches: (OpLogRecord) -> Boolean): Int =
        opLog().count { it.event == event && matches(it) }

    /** Тексты записи журнала: единственное место, где могло бы оказаться медицинское данное. */
    private fun OpLogRecord.textFields(): String = listOfNotNull(before, after, source, detail).joinToString(" ")

    private val magnesium: String get() = testContext.getString(R.string.store_medication_magnesium)

    private val painkiller: String
        get() = testContext.getString(R.string.device_test_medication_name, "intake-on-demand")

    /** «1 таблетка» — словами продукта: доза записи и подпись дня читаются из одних ресурсов. */
    private val doseLabel: String
        get() = targetContext.resources.getQuantityString(UiR.plurals.dose_tablets, DOSE_UNITS, DOSE)

    /** «Магний B6 — приём отмечен как пропущенный»: снэкбар говорит то же, что карточка. */
    private val skipNotice: String
        get() = targetContext.resources.getString(TodayR.string.today_undo_skipped_single, magnesium)

    private companion object {
        const val MAGNESIUM_ID = "journey-today-magnesium"
        const val PRN_ID = "journey-today-on-demand"
        const val STOCK_LEFT = "9"
        const val STOCK_LEFT_AFTER_MARK = "8"
        const val DOSE = "1"
        const val DOSE_UNITS = 1
        const val DAILY_LIMIT = 3
    }
}

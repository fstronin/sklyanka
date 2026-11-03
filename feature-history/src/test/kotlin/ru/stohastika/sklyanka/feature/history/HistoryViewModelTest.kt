package ru.stohastika.sklyanka.feature.history

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.BuildExtendedSectionsUseCase
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.domain.history.ReportScope
import ru.stohastika.sklyanka.domain.history.ReportShareResult
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import java.time.LocalDate
import java.time.YearMonth

/**
 * Состояние экрана истории.
 *
 * Проверяется на JVM: месяц, переключение и отчёт — арифметика по потокам, устройство ей
 * не нужно, а в среде задачи его и нет. Вёрстка и тексты проверяются на экранном стенде
 * (`src/testDebug`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = HistoryTestData.clock()
    private val activeProfile = FakeActiveProfile()
    private val profiles = FakeProfiles(
        HistoryTestData.profile(),
        HistoryTestData.profile(id = HistoryTestData.OTHER_PROFILE_ID, name = "profile-b", isSelf = false),
    )
    private val history = FakeHistory()
    private val log = RecordingLog()
    private val renderer = FakeRenderer()
    private val transfer = FakeTransfer()

    /** Доступ «Про»: им решается, доступен ли расширенный состав (Р-85 п. 1, Р-22). */
    private val subscriptions = FakeSubscriptionCache()

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the screen opens on the current month`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(HistoryTestData.SEPTEMBER, viewModel.state.value.month)
        assertEquals(LocalDate.of(2026, 9, 1), viewModel.state.value.period.from)
        assertEquals(LocalDate.of(2026, 9, 30), viewModel.state.value.period.to)
    }

    /** «Сегодня» берётся из источника времени, а не из системных часов (НФТ-48). */
    @Test
    fun `today comes from the injected clock`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        val today = viewModel.state.value.period.days.single { it.isToday }
        assertEquals(HistoryTestData.TODAY, today.date)
    }

    @Test
    fun `the previous month recalculates every block`() = runTest(dispatcher) {
        fillSeptember()
        history.put(
            profileId = HistoryTestData.PROFILE_ID,
            intakes = HistoryTestData.daily(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)) +
                HistoryTestData.daily(
                    from = LocalDate.of(2026, 9, 1),
                    to = LocalDate.of(2026, 9, 10),
                    status = IntakeStatus.MISSED,
                ),
            medications = listOf(HistoryTestData.medication()),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.PreviousMonth)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(YearMonth.of(2026, 8), state.month)
        assertEquals(0, state.period.missesInPeriod)
        assertEquals(3, state.period.daysWithoutMisses)
        assertEquals(1, state.period.byMedication.size)
        assertEquals(3, state.period.byMedication.single().planned)
    }

    @Test
    fun `the next month is shown as empty when nothing was planned there`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.NextMonth)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(YearMonth.of(2026, 10), state.month)
        assertTrue(state.isEmptyMonth)
        assertTrue(state.period.byMedication.isEmpty())
        assertNull(state.insightTime)
        assertEquals(OnTimeShare.Absent, state.period.onTimeShare)
        // Календарь остаётся календарём: пустой месяц — это тридцать один нейтральный
        // день, а не отсутствие сетки.
        assertEquals(31, state.period.days.size)
        assertTrue(state.period.days.all { it.state == DayState.NO_INTAKES || it.state == DayState.BEFORE_HISTORY })
    }

    /** ПР-10: история собирается для активного профиля, и переключение её пересобирает. */
    @Test
    fun `switching the profile rebuilds the history`() = runTest(dispatcher) {
        fillSeptember()
        history.put(
            profileId = HistoryTestData.OTHER_PROFILE_ID,
            intakes = HistoryTestData.daily(
                from = LocalDate.of(2026, 9, 1),
                to = LocalDate.of(2026, 9, 20),
                medicationId = HistoryTestData.SECOND_MEDICATION_ID,
            ),
            medications = listOf(
                HistoryTestData.medication(
                    id = HistoryTestData.SECOND_MEDICATION_ID,
                    profileId = HistoryTestData.OTHER_PROFILE_ID,
                    name = "med-b",
                ),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(HistoryTestData.MEDICATION_ID, viewModel.state.value.period.byMedication.single().medicationId)

        activeProfile.switchTo(HistoryTestData.OTHER_PROFILE_ID)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(HistoryTestData.OTHER_PROFILE_ID, state.period.profileId)
        assertEquals(HistoryTestData.SECOND_MEDICATION_ID, state.period.byMedication.single().medicationId)
    }

    /** Строка-вывод приходит из домена; на шуме её нет. */
    @Test
    fun `the insight line appears only when the leader is clear`() = runTest(dispatcher) {
        history.put(
            profileId = HistoryTestData.PROFILE_ID,
            intakes = HistoryTestData.daily(
                from = LocalDate.of(2026, 9, 1),
                to = LocalDate.of(2026, 9, 9),
                time = HistoryTestData.EVENING,
                status = IntakeStatus.MISSED,
            ) + HistoryTestData.daily(
                from = LocalDate.of(2026, 9, 1),
                to = LocalDate.of(2026, 9, 3),
                time = HistoryTestData.MORNING,
                status = IntakeStatus.MISSED,
            ),
            medications = listOf(HistoryTestData.medication(times = listOf(HistoryTestData.MORNING))),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(HistoryTestData.EVENING, viewModel.state.value.insightTime)
    }

    @Test
    fun `an even split leaves the insight line out`() = runTest(dispatcher) {
        history.put(
            profileId = HistoryTestData.PROFILE_ID,
            intakes = HistoryTestData.daily(
                from = LocalDate.of(2026, 9, 1),
                to = LocalDate.of(2026, 9, 6),
                time = HistoryTestData.EVENING,
                status = IntakeStatus.MISSED,
            ) + HistoryTestData.daily(
                from = LocalDate.of(2026, 9, 1),
                to = LocalDate.of(2026, 9, 6),
                time = HistoryTestData.MORNING,
                status = IntakeStatus.MISSED,
            ),
            medications = listOf(HistoryTestData.medication()),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.state.value.insightTime)
    }

    @Test
    fun `the report opens on the current month by default`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.report)
        val report = renderer.rendered.single()
        assertEquals(LocalDate.of(2026, 9, 1), report.period.from)
        assertEquals(LocalDate.of(2026, 9, 30), report.period.to)
        assertEquals("profile-a", report.profileName)
        assertEquals(ReportPreset.THIS_MONTH, viewModel.state.value.report?.preset)
    }

    /**
     * Период отчёта отвязан от месяца «Истории» (ОВ-125): стрелки листают экран,
     * а не период выгрузки. Отчёт открывается на «Этом месяце» независимо от того,
     * каким был показанный месяц.
     */
    @Test
    fun `the report period does not follow the shown month`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.PreviousMonth)
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 1), renderer.rendered.last().period.from)
    }

    /** Смена пресета пересобирает состав тем же расчётом, что экран. */
    @Test
    fun `choosing the previous month preset rebuilds the report`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ReportPresetChosen(ReportPreset.PREVIOUS_MONTH))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 1), renderer.rendered.last().period.from)
        assertEquals(LocalDate.of(2026, 8, 31), renderer.rendered.last().period.to)
        assertEquals(ReportPreset.PREVIOUS_MONTH, viewModel.state.value.report?.preset)
    }

    /**
     * Смена пресета не обнуляет открытый отчёт: пока собирается новый состав, старый
     * остаётся на месте. Обнуление здесь заставляло шторку исчезнуть и вернуться —
     * она дёргалась и теряла прокрутку (SKL-101, дефект 1).
     */
    @Test
    fun `changing the preset never blanks the open report`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        val reports = mutableListOf<ReportUi?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { reports += it.report }
        }
        viewModel.onIntent(HistoryIntent.ReportPresetChosen(ReportPreset.PREVIOUS_MONTH))
        advanceUntilIdle()
        collector.cancel()

        assertTrue(
            "the open report must never disappear: $reports",
            reports.isNotEmpty() && reports.none { it == null },
        )
        assertEquals(ReportPreset.PREVIOUS_MONTH, viewModel.state.value.report?.preset)
    }

    @Test
    fun `the last three months preset spans three calendar months`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ReportPresetChosen(ReportPreset.LAST_THREE_MONTHS))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 7, 1), renderer.rendered.last().period.from)
        assertEquals(LocalDate.of(2026, 9, 30), renderer.rendered.last().period.to)
    }

    /**
     * «Другой период»: выбранная дата пересобирает состав, «С» позже «По» подтягивает
     * «По» (ОВ-125). Дата не может быть в будущем — это гарантирует диалог, а домен
     * отказывает предусловием.
     */
    @Test
    fun `a custom start later than the end pulls the end along`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportPresetChosen(ReportPreset.CUSTOM))
        advanceUntilIdle()
        // «По» уводится назад, чтобы «С», выбранная позже, действительно обогнала её.
        viewModel.onIntent(HistoryIntent.ReportEndPicked(LocalDate.of(2026, 9, 5)))
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ReportStartPicked(LocalDate.of(2026, 9, 20)))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 20), renderer.rendered.last().period.from)
        assertEquals(LocalDate.of(2026, 9, 20), renderer.rendered.last().period.to)
        assertEquals(LocalDate.of(2026, 9, 20), viewModel.state.value.report?.customFrom)
    }

    @Test
    fun `a custom end rebuilds the report and keeps the start`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportPresetChosen(ReportPreset.CUSTOM))
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ReportEndPicked(LocalDate.of(2026, 9, 10)))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 1), renderer.rendered.last().period.from)
        assertEquals(LocalDate.of(2026, 9, 10), renderer.rendered.last().period.to)
    }

    /** Смена периода сбрасывает сообщение исхода: оно относилось к прошлому составу. */
    @Test
    fun `changing the period clears the outcome`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ShareReport)
        advanceUntilIdle()
        assertEquals(ReportOutcome.SHARED, viewModel.state.value.reportOutcome)

        viewModel.onIntent(HistoryIntent.ReportPresetChosen(ReportPreset.PREVIOUS_MONTH))
        advanceUntilIdle()

        assertNull(viewModel.state.value.reportOutcome)
    }

    /** Успех — отдельное состояние содержимого шторки; шторка не закрывается (ОВ-125). */
    @Test
    fun `sharing the report keeps it open and reports success`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ShareReport)
        advanceUntilIdle()

        assertEquals(1, transfer.shared.size)
        assertNotNull(viewModel.state.value.report)
        assertEquals(ReportOutcome.SHARED, viewModel.state.value.reportOutcome)
    }

    /**
     * Успех не объявляется, пока системный выбор открыт: результат приходит только
     * с возвратом диалога (SKL-101, дефект 4).
     */
    @Test
    fun `the success waits for the chosen target`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        val decision = CompletableDeferred<ReportShareResult>()
        viewModel.attachShareLauncher { decision.await() }
        viewModel.onIntent(HistoryIntent.ShareReport)
        advanceUntilIdle()

        assertNull("no success may be shown while the chooser is open", viewModel.state.value.reportOutcome)
        assertNotNull("the report must stay open", viewModel.state.value.report)

        decision.complete(ReportShareResult.CHOSEN)
        advanceUntilIdle()

        assertEquals(ReportOutcome.SHARED, viewModel.state.value.reportOutcome)
    }

    /** Закрытие системного выбора без получателя — отмена, а не ложный успех. */
    @Test
    fun `a dismissed chooser is a cancellation, not a success`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.attachShareLauncher { ReportShareResult.DISMISSED }
        viewModel.onIntent(HistoryIntent.ShareReport)
        advanceUntilIdle()

        assertEquals(ReportOutcome.CANCELLED, viewModel.state.value.reportOutcome)
        assertNotNull(viewModel.state.value.report)
    }

    @Test
    fun `saving the report writes it to the chosen place`() = runTest(dispatcher) {
        fillSeptember()
        transfer.next = ReportTransferOutcome.Saved
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.SaveTo("content://place"))
        advanceUntilIdle()

        assertEquals("content://place", transfer.saved.single().second.handle)
        assertEquals(ReportOutcome.SAVED, viewModel.state.value.reportOutcome)
    }

    /**
     * Отмена системного диалога — не ошибка и не молчание.
     *
     * Отчёт остаётся открытым: человек хотел его передать и, скорее всего, попробует
     * снова. Молчаливая отмена выглядит для человека так же, как отказ записи.
     */
    @Test
    fun `cancelling the place chooser keeps the report open and says so`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.SaveCancelled)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.report)
        assertEquals(ReportOutcome.CANCELLED, viewModel.state.value.reportOutcome)
        assertTrue(transfer.saved.isEmpty())
    }

    /** Отказ сохранения и отказа передачи различаются словом: текст называет действие. */
    @Test
    fun `a failed write is reported as a failed save`() = runTest(dispatcher) {
        fillSeptember()
        transfer.next = ReportTransferOutcome.Failed("write_failed")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.SaveTo("content://place"))
        advanceUntilIdle()

        assertEquals(ReportOutcome.FAILED_SAVE, viewModel.state.value.reportOutcome)
        assertNotNull(viewModel.state.value.report)
    }

    @Test
    fun `a failed share is reported as a failed share`() = runTest(dispatcher) {
        fillSeptember()
        transfer.next = ReportTransferOutcome.Failed("share_failed")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ShareReport)
        advanceUntilIdle()

        assertEquals(ReportOutcome.FAILED_SHARE, viewModel.state.value.reportOutcome)
        assertNotNull(viewModel.state.value.report)
    }

    /**
     * Сохранить некому — честный отказ, а не падение (SKL-46).
     *
     * Шторка остаётся открытой: человек хотел сохранить и попробует снова там, где
     * системный выбор места есть. След в журнале — тот же, что у сорванной записи,
     * с платформенной причиной.
     */
    @Test
    fun `a missing place chooser refuses honestly`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.SaveUnavailable)
        advanceUntilIdle()

        assertEquals(ReportOutcome.FAILED_SAVE, viewModel.state.value.reportOutcome)
        assertNotNull(viewModel.state.value.report)
        assertTrue(transfer.saved.isEmpty())
        val transferRecord = log.records.last { it.event == OpEvent.DOCTOR_REPORT_TRANSFERRED }
        assertTrue(
            "the journal must name the platform cause: ${transferRecord.detail}",
            transferRecord.detail?.startsWith("failed:no_activity_for_create_document") == true,
        )
    }

    /**
     * Журнал: след есть, медицинских данных в нём нет (НФТ-19).
     *
     * Ни названий лекарств, ни имён профилей, ни агрегированных чисел приверженности:
     * «86 %» рядом с `profile_id` — это медицинские данные, просто агрегированные.
     */
    @Test
    fun `the journal keeps identifiers and counts only`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ShareReport)
        advanceUntilIdle()

        val events = log.records.map { it.event }
        assertTrue(events.contains(OpEvent.HISTORY_PERIOD_CALCULATED))
        assertTrue(events.contains(OpEvent.DOCTOR_REPORT_BUILT))
        assertTrue(events.contains(OpEvent.DOCTOR_REPORT_TRANSFERRED))
        val text = log.records.joinToString { listOfNotNull(it.before, it.after, it.detail).joinToString() }
        assertFalse(text.contains("med-a"))
        assertFalse(text.contains("profile-a"))
        assertFalse(text.contains("%"))
    }

    /** Р-21/Р-27: доля считается по наступившим плановым приёмам — числитель к знаменателю. */
    @Test
    fun `the on time share counts due scheduled intakes`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        // Все приёмы сентября отмечены в назначенное время: числитель равен знаменателю.
        assertEquals(OnTimeShare.Value(onTime = 29, due = 29), viewModel.state.value.period.onTimeShare)
        assertFalse(log.records.any { it.event == OpEvent.HISTORY_ON_TIME_NOT_MEASURED })
    }

    /** Р-33: наступивших плановых приёмов нет — мерить нечего, и журнал называет причину. */
    @Test
    fun `no due scheduled intakes leaves the share absent and the journal says so`() = runTest(dispatcher) {
        history.put(
            profileId = HistoryTestData.PROFILE_ID,
            intakes = HistoryTestData.daily(
                from = LocalDate.of(2026, 9, 1),
                to = LocalDate.of(2026, 9, 29),
                status = IntakeStatus.PENDING,
            ),
            medications = listOf(HistoryTestData.medication()),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(OnTimeShare.Absent, viewModel.state.value.period.onTimeShare)
        val trace = log.records.last { it.event == OpEvent.HISTORY_ON_TIME_NOT_MEASURED }
        assertEquals("on_time_not_measured", trace.detail)
    }

    /** ПРФ-14: отметка владельца телефона в чужом профиле подписана. */
    @Test
    fun `a mark made by the phone owner in another profile is flagged`() = runTest(dispatcher) {
        activeProfile.switchTo(HistoryTestData.OTHER_PROFILE_ID)
        history.put(
            profileId = HistoryTestData.OTHER_PROFILE_ID,
            intakes = listOf(
                HistoryTestData.intake(
                    at = LocalDate.of(2026, 9, 20).atTime(HistoryTestData.MORNING),
                    medicationId = HistoryTestData.SECOND_MEDICATION_ID,
                    actorProfileId = HistoryTestData.PROFILE_ID,
                ),
            ),
            medications = listOf(
                HistoryTestData.medication(
                    id = HistoryTestData.SECOND_MEDICATION_ID,
                    profileId = HistoryTestData.OTHER_PROFILE_ID,
                    name = "med-b",
                ),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.period.byMedication.single().markedByOtherProfile)
    }

    /** ПРФ-14: в истории своего профиля подписи не бывает по построению. */
    @Test
    fun `the history of your own profile carries no such flag`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.period.byMedication.single().markedByOtherProfile)
    }

    // --- Состав отчёта: выбор и гейт «Про» (SKL-119, Р-85 п. 1, Р-22) -------------------

    /**
     * Без активного доступа нажатие расширенной строки **выбор не меняет** (П-7, §5.15):
     * открывается витрина, а печатается тот состав, который отмечен. Недоступное действие
     * говорит «нельзя», а правда — «можно, по подписке».
     */
    @Test
    fun `without access the extended composition asks for the showcase and keeps the base one`() = runTest(dispatcher) {
        fillSeptemberAndAugust()
        subscriptions.none()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        val showcase = mutableListOf<Unit>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.showcase.collect { showcase += it }
        }
        viewModel.onIntent(HistoryIntent.ReportScopeChosen(ReportScope.EXTENDED))
        advanceUntilIdle()
        collector.cancel()

        assertEquals(1, showcase.size)
        assertEquals(ReportScope.BASE, viewModel.state.value.report?.scope)
        assertEquals(
            "no unrequested document may be assembled",
            ExtendedReportSections.NONE,
            renderer.sections.last(),
        )
    }

    @Test
    fun `with access the extended composition is chosen and the document carries the sections`() = runTest(dispatcher) {
        fillSeptemberAndAugust()
        subscriptions.own(subscriptionUntil)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ReportScopeChosen(ReportScope.EXTENDED))
        advanceUntilIdle()

        assertEquals(ReportScope.EXTENDED, viewModel.state.value.report?.scope)
        assertEquals(
            "the extended document is assembled with the 'Pro' sections",
            sectionsOfSeptember(),
            renderer.sections.last().comparison?.current?.taken,
        )
    }

    /** После отмены подписки базовый состав работает, расширенный закрыт (Р-22). */
    @Test
    fun `after the subscription is cancelled the extended composition is closed again`() = runTest(dispatcher) {
        fillSeptemberAndAugust()
        subscriptions.none()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportScopeChosen(ReportScope.BASE))
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ReportScopeChosen(ReportScope.EXTENDED))
        advanceUntilIdle()

        assertEquals(ReportScope.BASE, viewModel.state.value.report?.scope)
        assertNotNull("the base document must stay assembled", viewModel.state.value.report?.document)
    }

    /**
     * Возвращение из витрины с оформленным доступом: повторное нажатие выбирает состав.
     *
     * Экран держит снимок доступа с момента открытия шторки, а покупка пишет кэш подписки:
     * без перечитывания повторное нажатие снова открывало бы витрину (§5.15, SKL-117).
     */
    @Test
    fun `refreshing the access after the showcase lets the extended composition be chosen`() = runTest(dispatcher) {
        fillSeptemberAndAugust()
        subscriptions.none()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportScopeChosen(ReportScope.EXTENDED))
        advanceUntilIdle()
        assertEquals(ReportScope.BASE, viewModel.state.value.report?.scope)

        // Человек оформил подписку в витрине и вернулся: экран перечитывает доступ.
        subscriptions.own(subscriptionUntil)
        viewModel.refreshProAccess()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportScopeChosen(ReportScope.EXTENDED))
        advanceUntilIdle()

        assertEquals(ReportScope.EXTENDED, viewModel.state.value.report?.scope)
    }

    /** Сентябрь и август: у периода появляется предыдущий, с которым его сравнивают. */
    private fun fillSeptemberAndAugust() = history.put(
        profileId = HistoryTestData.PROFILE_ID,
        intakes = HistoryTestData.daily(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)) +
            HistoryTestData.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29)),
        medications = listOf(HistoryTestData.medication()),
    )

    private fun sectionsOfSeptember(): Int = renderer.rendered.last().takenTotal

    private val subscriptionUntil: LocalDate = LocalDate.of(2026, 11, 13)

    private fun fillSeptember() = history.put(
        profileId = HistoryTestData.PROFILE_ID,
        intakes = HistoryTestData.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29)),
        medications = listOf(HistoryTestData.medication()),
    )

    private fun viewModel() = HistoryViewModel(
        observeAdherence = ObserveAdherenceUseCase(
            activeProfile = activeProfile,
            history = history,
            calculator = AdherenceCalculator(OnTimeRule.TwoHours),
            time = clock,
            log = log,
        ),
        buildReport = BuildDoctorReportUseCase(
            activeProfile = activeProfile,
            profiles = profiles,
            history = history,
            calculator = AdherenceCalculator(OnTimeRule.TwoHours),
            time = clock,
            log = log,
        ),
        transferReport = TransferDoctorReportUseCase(
            transfer = transfer,
            activeProfile = activeProfile,
            log = log,
        ),
        buildExtended = BuildExtendedSectionsUseCase(
            activeProfile = activeProfile,
            history = history,
            calculator = AdherenceCalculator(OnTimeRule.TwoHours),
            missedPattern = MissedPatternRule(),
            time = clock,
        ),
        renderer = renderer,
        missedPattern = MissedPatternRule(),
        proStatus = ObserveProStatusUseCase(subscriptions, clock),
        time = clock,
    ).apply {
        // По умолчанию системный выбор отвечает «выбран»: проверкам, которым важен
        // не он, не нужно возиться с его результатом.
        attachShareLauncher { ReportShareResult.CHOSEN }
    }
}

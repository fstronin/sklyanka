package ru.stohastika.sklyanka.feature.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeStatus
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
        assertEquals(OnTimeShare.Undefined, state.period.onTimeShare)
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
    fun `the report is built for the shown month and opens on the screen`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.report)
        val report = renderer.rendered.single()
        assertEquals(LocalDate.of(2026, 9, 1), report.period.from)
        assertEquals("profile-a", report.profileName)
    }

    @Test
    fun `the report of another month is built for that month`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.PreviousMonth)
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 1), renderer.rendered.single().period.from)
    }

    @Test
    fun `sharing the report closes it and says so`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.ShareReport)
        advanceUntilIdle()

        assertEquals(1, transfer.shared.size)
        assertNull(viewModel.state.value.report)
        assertEquals(HistoryNotice.REPORT_SHARED, viewModel.state.value.notice)
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
        assertEquals(HistoryNotice.REPORT_SAVED, viewModel.state.value.notice)
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
        assertEquals(HistoryNotice.REPORT_CANCELLED, viewModel.state.value.notice)
        assertTrue(transfer.saved.isEmpty())
    }

    @Test
    fun `a failed write is reported to the person`() = runTest(dispatcher) {
        fillSeptember()
        transfer.next = ReportTransferOutcome.Failed("write_failed")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(HistoryIntent.ReportRequested)
        advanceUntilIdle()

        viewModel.onIntent(HistoryIntent.SaveTo("content://place"))
        advanceUntilIdle()

        assertEquals(HistoryNotice.REPORT_FAILED, viewModel.state.value.notice)
        assertNotNull(viewModel.state.value.report)
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

    /** ОВ-4: правило «вовремя» не зарегистрировано — плитка показывает прочерк. */
    @Test
    fun `the on time share stays undefined until the threshold is agreed`() = runTest(dispatcher) {
        fillSeptember()
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(OnTimeShare.Undefined, viewModel.state.value.period.onTimeShare)
        assertTrue(log.records.any { it.event == OpEvent.HISTORY_ON_TIME_UNDEFINED })
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

    private fun fillSeptember() = history.put(
        profileId = HistoryTestData.PROFILE_ID,
        intakes = HistoryTestData.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29)),
        medications = listOf(HistoryTestData.medication()),
    )

    private fun viewModel() = HistoryViewModel(
        observeAdherence = ObserveAdherenceUseCase(
            activeProfile = activeProfile,
            history = history,
            calculator = AdherenceCalculator(),
            time = clock,
            log = log,
        ),
        buildReport = BuildDoctorReportUseCase(
            activeProfile = activeProfile,
            profiles = profiles,
            history = history,
            calculator = AdherenceCalculator(),
            time = clock,
            log = log,
        ),
        transferReport = TransferDoctorReportUseCase(
            transfer = transfer,
            activeProfile = activeProfile,
            log = log,
        ),
        renderer = renderer,
        missedPattern = MissedPatternRule(),
        time = clock,
    )
}

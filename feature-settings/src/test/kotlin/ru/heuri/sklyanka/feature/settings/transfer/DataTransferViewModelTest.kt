package ru.heuri.sklyanka.feature.settings.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import ru.heuri.sklyanka.domain.transfer.AppStamp
import ru.heuri.sklyanka.domain.transfer.ApplyTransferUseCase
import ru.heuri.sklyanka.domain.transfer.BuildTransferPlan
import ru.heuri.sklyanka.domain.transfer.DeviceState
import ru.heuri.sklyanka.domain.transfer.ExportDataUseCase
import ru.heuri.sklyanka.domain.transfer.MatchDecision
import ru.heuri.sklyanka.domain.transfer.ReadTransferFileUseCase
import ru.heuri.sklyanka.domain.transfer.TransferDecoded
import ru.heuri.sklyanka.domain.transfer.TransferMode
import ru.heuri.sklyanka.domain.transfer.TransferReject
import ru.heuri.sklyanka.domain.transfer.TransferRejectReason
import ru.heuri.sklyanka.domain.transfer.ValidateSnapshot
import java.time.ZoneId

/**
 * Порядок шагов переноса и то, что до подтверждения ничего не применяется
 * (ПЕР-15, ПЕР-16, ПЕР-17, ПЕР-27).
 *
 * Проверяется на JVM: устройства в среде задачи нет, а последовательность шагов в нём
 * и не нуждается. Это главный тест «ничего не применено до подтверждения» — именно он
 * ловит превращение «Заменить всё» в необратимое действие в одно нажатие.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataTransferViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val time = FixedTimeSource(TransferData.EXPORTED_AT, ZoneId.of("Europe/Moscow"))
    private val store = FakeTransferStore(
        DeviceState(
            profiles = listOf(TransferData.self()),
            medications = listOf(TransferData.medication(id = "device-med")),
            intakes = emptyList(),
        ),
    )
    private val files = FakeTransferFiles()
    private val settings = FakeSettingsStore()
    private val log = RecordingLog()
    private val replanner = FakeReplanner()
    private val ids = FixedIds()
    private val codec = FakeCodec(TransferDecoded.Loaded(TransferData.snapshot(), emptyList()))

    @Before
    fun replaceMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the screen shows the composition of the current data and the file name`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertEquals(1, state.deviceCounts?.medications)
        assertEquals(1, state.deviceCounts?.profiles)
        assertEquals("sklyanka-2026-10-05.json", state.exportFileName)
    }

    @Test
    fun `before the first export there is no line about it`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(model.state.value.lastExportDate)
    }

    @Test
    fun `a successful export moves the date of the last export`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.SaveTo("content://target"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(time.today(), model.state.value.lastExportDate)
        assertEquals(FakeCodec.ENCODED, files.written)
    }

    @Test
    fun `an interrupted export leaves no ready file and does not move the date`() = runTest(dispatcher) {
        files.failOnWrite = true
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.SaveTo("content://target"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransferFailure.ExportFailed, model.state.value.failure)
        assertTrue("the half written document must be removed", files.discarded)
        assertNull(model.state.value.lastExportDate)
    }

    @Test
    fun `choosing a file shows the summary and applies nothing`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransferStep.SUMMARY, model.state.value.step)
        assertEquals(1, model.state.value.fileCounts?.medications)
        assertTrue("nothing may be written before the confirmation", store.applied.isEmpty())
    }

    @Test
    fun `the merge mode brings the matches up for a decision before applying`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.ContinueFromSummary)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransferStep.MATCHES, model.state.value.step)
        assertEquals(1, model.state.value.matches.size)
        assertTrue(store.applied.isEmpty())
    }

    @Test
    fun `keep mine is the decision in force until the person chooses`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(MatchDecision.KEEP_MINE, model.state.value.decisionFor(TransferData.MEDICATION_ID))
    }

    @Test
    fun `dropping all matches sets every decision to do not load`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.ContinueFromSummary)
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.DropAllMatches)

        assertEquals(MatchDecision.DO_NOT_LOAD, model.state.value.decisionFor(TransferData.MEDICATION_ID))
    }

    @Test
    fun `the replace mode asks for a separate confirmation and applies nothing before it`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.SelectMode(TransferMode.REPLACE_ALL))
        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.ContinueFromSummary)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransferStep.REPLACE_CONFIRM, model.state.value.step)
        assertTrue("replacing must not happen before the confirmation", store.applied.isEmpty())
    }

    @Test
    fun `the replacement makes a safety copy before erasing anything`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.SelectMode(TransferMode.REPLACE_ALL))
        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.ContinueFromSummary)
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.Apply)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FakeCodec.ENCODED, files.safetyCopyContent)
        assertEquals(TransferStep.REPORT, model.state.value.step)
        assertEquals(true, model.state.value.report?.let { it.profilesAdded >= 0 })
        assertTrue(store.applied.single().erase)
    }

    @Test
    fun `an unreadable file is refused with an explanation and nothing is applied`() = runTest(dispatcher) {
        codec.outcome = TransferDecoded.Rejected(TransferReject(TransferRejectReason.NOT_JSON, "root"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()

        val failure = model.state.value.failure
        assertTrue(failure is TransferFailure.NotAnExport)
        assertEquals(TransferStep.OVERVIEW, model.state.value.step)
        assertTrue(store.applied.isEmpty())
    }

    @Test
    fun `a file from a newer format version is told apart from a broken one`() = runTest(dispatcher) {
        codec.outcome = TransferDecoded.Rejected(TransferReject(TransferRejectReason.FUTURE_FORMAT, "v2"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransferFailure.FromFuture, model.state.value.failure)
    }

    @Test
    fun `an interrupted apply reports that the import did not happen`() = runTest(dispatcher) {
        store.failWith = IllegalStateException("the process was killed in the middle")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.ContinueFromSummary)
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.Apply)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransferFailure.ImportNotApplied, model.state.value.failure)
        assertEquals(false, settings.importRunning)
    }

    @Test
    fun `an import interrupted in a previous run is reported at the next start`() = runTest(dispatcher) {
        settings.importRunning = true
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.state.value.importDidNotHappen)
        assertEquals(false, settings.importRunning)
    }

    @Test
    fun `leaving the screen starts the transfer from scratch`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.Leave)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransferStep.OVERVIEW, model.state.value.step)
        assertNull(model.state.value.fileCounts)
        assertTrue(store.applied.isEmpty())
    }

    @Test
    fun `an archived course from the file gets its own planner pass`() = runTest(dispatcher) {
        codec.outcome = TransferDecoded.Loaded(
            TransferData.snapshot(
                medications = listOf(TransferData.medication(id = "archived-1", archived = true)),
                intakes = emptyList(),
            ),
            emptyList(),
        )
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.SelectMode(TransferMode.REPLACE_ALL))
        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.ContinueFromSummary)
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(TransferIntent.Apply)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("archived-1"), replanner.archivedSeen)
    }

    @Test
    fun `the journal of a whole transfer carries no medication names`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.SaveTo("content://target"))
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.FileChosen("content://file"))
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.ContinueFromSummary)
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(TransferIntent.Apply)
        dispatcher.scheduler.advanceUntilIdle()

        val journal = log.asText()
        assertTrue("the journal must not be empty", journal.isNotEmpty())
        assertEquals(false, journal.contains(TransferData.MEDICATION_NAME))
        assertEquals(false, journal.contains("Owner"))
        assertEquals(false, journal.contains(TransferData.STRENGTH_UNIT))
    }

    private fun viewModel(): DataTransferViewModel {
        val export = ExportDataUseCase(
            store = store,
            settings = settings,
            codec = codec,
            files = files,
            time = time,
            log = log,
            app = AppStamp(versionName = "1.0", versionCode = 10),
        )
        val read = ReadTransferFileUseCase(
            files = files,
            codec = codec,
            validate = ValidateSnapshot(),
            log = log,
        )
        val apply = ApplyTransferUseCase(
            store = store,
            settings = settings,
            codec = codec,
            files = files,
            activeProfile = FakeActiveProfile(),
            replanner = replanner,
            log = log,
            buildPlan = BuildTransferPlan(ids),
        )
        return DataTransferViewModel(
            exportData = export,
            readFile = read,
            applyTransfer = apply,
            store = store,
            settings = settings,
            files = files,
            ids = ids,
            time = time,
        )
    }
}

package ru.stohastika.sklyanka.feature.settings.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import ru.stohastika.sklyanka.domain.profile.DeleteProfileUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileEraser
import ru.stohastika.sklyanka.domain.profile.ProfileLosses
import ru.stohastika.sklyanka.domain.profile.ProfileLossesSource
import ru.stohastika.sklyanka.domain.profile.ProfileSetReplanner
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.domain.transfer.ExportDataUseCase
import ru.stohastika.sklyanka.domain.transfer.TransferCodec
import ru.stohastika.sklyanka.domain.transfer.TransferDecoded
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import ru.stohastika.sklyanka.feature.settings.FakeActiveProfile
import ru.stohastika.sklyanka.feature.settings.FakeProfileContext
import ru.stohastika.sklyanka.feature.settings.FakeProfiles
import ru.stohastika.sklyanka.feature.settings.SettingsFixtures
import ru.stohastika.sklyanka.feature.settings.transfer.FakeSettingsStore
import ru.stohastika.sklyanka.feature.settings.transfer.FakeTransferFiles
import ru.stohastika.sklyanka.feature.settings.transfer.FakeTransferStore
import ru.stohastika.sklyanka.feature.settings.transfer.RecordingLog
import java.time.Instant
import java.time.ZoneId

/**
 * Состояние экрана удаления профиля (ОВ-7, SKL-36).
 *
 * Проверяется сборка состояния, а не вёрстка: удаляемый профиль и числа потерь при
 * открытии, слово в состоянии, подтверждение через настоящий `DeleteProfileUseCase`,
 * факт и отказы выгрузки настоящим `ExportDataUseCase`. Ни одно действие, кроме
 * подтверждения, не стирает: «выгрузка не удаляет» — утверждение о состоянии, и оно
 * проверяется отсутствием вызова стирания.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Europe/Moscow")
    private val clock = FixedTimeSource(Instant.parse("2026-09-30T12:34:00Z"), zone)

    private val profiles = FakeProfiles(
        listOf(
            SettingsFixtures.foreign("Mother"),
            SettingsFixtures.foreign("Grandma").copy(id = SECOND_FOREIGN_ID),
            SettingsFixtures.self("Owner"),
        ),
    )
    private val active = FakeActiveProfile(SettingsFixtures.FOREIGN_ID)
    private val eraser = RecordingEraser()
    private val planning = CountingReplanner()
    private val losses = FakeLosses()
    private val files = FakeTransferFiles()

    private val exportData = ExportDataUseCase(
        store = FakeTransferStore(),
        settings = FakeSettingsStore(),
        codec = EncodingCodec,
        files = files,
        time = clock,
        log = RecordingLog(),
        app = AppStamp(versionName = "1.0", versionCode = 1),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theProfileAndItsLossesAreShownOnOpen() = runTest(dispatcher) {
        losses.numbers = ProfileLosses(medications = 4, archived = 2, intakes = 318)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertEquals(SettingsFixtures.FOREIGN_ID, state.profileId)
        assertEquals("Mother", state.profileName)
        assertEquals(ProfileLosses(medications = 4, archived = 2, intakes = 318), state.losses)
        assertEquals("sklyanka-2026-09-30.json", state.exportFileName)
        assertEquals(zone, state.zone)
        assertEquals(DeleteProfileStep.CONFIRM, state.step)
    }

    @Test
    fun theWordIsKeptInTheState() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(DeleteProfileIntent.WordChanged("confirm"))

        assertEquals("confirm", model.state.value.word)
    }

    @Test
    fun confirmingDeletesTheProfileAndShowsTheResult() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(DeleteProfileIntent.Confirm)
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertEquals(listOf(SettingsFixtures.FOREIGN_ID), eraser.erased)
        assertEquals(listOf(SwitchReason.PROFILE_DELETED), active.reasons)
        assertEquals(1, planning.calls)
        assertEquals(DeleteProfileStep.RESULT, state.step)
        assertEquals("Mother", state.profileName)
    }

    @Test
    fun exportingSetsTheFactAndDeletesNothing() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(DeleteProfileIntent.ExportTargetChosen("handle"))
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertEquals(clock.now(), state.exportedAt)
        assertNull(state.failure)
        assertEquals(DeleteProfileStep.CONFIRM, state.step)
        assertTrue("exporting must not erase anything", eraser.erased.isEmpty())
    }

    @Test
    fun anExportFailureIsNamed() = runTest(dispatcher) {
        files.failOnWrite = true
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(DeleteProfileIntent.ExportTargetChosen("handle"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DeleteProfileFailure.ExportFailed, model.state.value.failure)
        assertNull(model.state.value.exportedAt)
        assertTrue(eraser.erased.isEmpty())
    }

    @Test
    fun aMissingSpaceIsNamed() = runTest(dispatcher) {
        files.freeSpace = 0
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(DeleteProfileIntent.ExportTargetChosen("handle"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DeleteProfileFailure.NoSpace, model.state.value.failure)
    }

    @Test
    fun aMissingFilePickerIsNamed() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(DeleteProfileIntent.NoFilePicker)

        assertEquals(DeleteProfileFailure.NoFilePicker, model.state.value.failure)
    }

    @Test
    fun leavingResetsTheStateAndLoadsTheProfileAgain() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(DeleteProfileIntent.WordChanged("confirm"))
        model.onIntent(DeleteProfileIntent.ExportTargetChosen("handle"))
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(DeleteProfileIntent.Leave)
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertEquals(DeleteProfileStep.CONFIRM, state.step)
        assertEquals("", state.word)
        assertNull(state.exportedAt)
        assertEquals(SettingsFixtures.FOREIGN_ID, state.profileId)
    }

    /**
     * Повторный вход после смены профиля читает новый профиль (SKL-36).
     *
     * ViewModel живёт в Activity и создаётся, как только открыт любой экран второго
     * уровня настроек: к новому входу состояние могло быть снято для другого профиля.
     * Вход обязан сверить снимок с активным — иначе экран назовёт и удалит не того,
     * кого нажали.
     */
    @Test
    fun enteringAgainAfterTheProfileChangedLoadsTheNewProfile() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(DeleteProfileIntent.Leave)
        dispatcher.scheduler.advanceUntilIdle()

        active.switchTo(SECOND_FOREIGN_ID)
        model.onIntent(DeleteProfileIntent.Refresh)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SECOND_FOREIGN_ID, model.state.value.profileId)
        assertEquals("Grandma", model.state.value.profileName)
    }

    /** Поворот экрана: профиль тот же — набранное слово и факт выгрузки остаются. */
    @Test
    fun enteringWithTheSameProfileKeepsTheTypedWordAndTheExportFact() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(DeleteProfileIntent.WordChanged("confirm"))
        model.onIntent(DeleteProfileIntent.ExportTargetChosen("handle"))
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(DeleteProfileIntent.Refresh)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("confirm", model.state.value.word)
        assertEquals(clock.now(), model.state.value.exportedAt)
        assertEquals(SettingsFixtures.FOREIGN_ID, model.state.value.profileId)
    }

    /** Итог удаления не сбрасывается повторным входом (поворот после подтверждения). */
    @Test
    fun enteringAfterTheDeletionKeepsTheResult() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(DeleteProfileIntent.Confirm)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(DeleteProfileStep.RESULT, model.state.value.step)

        model.onIntent(DeleteProfileIntent.Refresh)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DeleteProfileStep.RESULT, model.state.value.step)
        assertEquals("Mother", model.state.value.profileName)
    }

    private fun viewModel() = DeleteProfileViewModel(
        context = FakeProfileContext(profiles, active),
        deleteProfile = DeleteProfileUseCase(
            profiles = profiles,
            eraser = eraser,
            active = active,
            replan = planning,
            log = RecordingLog(),
        ),
        losses = losses,
        exportData = exportData,
        time = clock,
    )
}

private const val SECOND_FOREIGN_ID = "grandma"

/** Стирание в памяти: помнит, кого стёрли; больше о нём знать нечего. */
private class RecordingEraser : ProfileEraser {

    val erased: MutableList<String> = mutableListOf()

    override suspend fun erase(profileId: String) {
        erased += profileId
    }
}

private class CountingReplanner : ProfileSetReplanner {

    var calls: Int = 0
        private set

    override suspend fun onProfileSetChanged() {
        calls++
    }
}

private class FakeLosses(var numbers: ProfileLosses = ProfileLosses(0, 0, 0)) : ProfileLossesSource {

    override fun observe(profileId: String): Flow<ProfileLosses> = MutableStateFlow(numbers)
}

/** Кодек только для записи: экран удаления файл не читает. */
private object EncodingCodec : TransferCodec {

    override fun encode(snapshot: TransferSnapshot): String = ENCODED

    override fun decode(text: String): TransferDecoded = error("the deletion screen never reads a file")

    private const val ENCODED = "{}"
}

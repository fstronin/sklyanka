package ru.stohastika.sklyanka.feature.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.onboarding.AutostartHint
import ru.stohastika.sklyanka.domain.onboarding.AutostartOpenResult
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission

/**
 * Поведение экрана разрешений (НАП-18 … НАП-22, НФТ-52).
 *
 * Проверяется на JVM: устройства в среде задачи нет, а расчёту состояния цепочки,
 * порядку вызовов и следу в журнале оно и не нужно. Что остаётся за устройством —
 * открывается ли настоящий системный экран и доходит ли настоящее напоминание —
 * перечислено в `TDD.md §12.1`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val world = OnboardingTestWorld()

    @Before
    fun replaceMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun started(source: OnboardingSource = OnboardingSource.FIRST_LAUNCH): OnboardingViewModel =
        world.viewModel().also {
            it.start(source)
            dispatcher.scheduler.advanceUntilIdle()
        }

    private fun OnboardingViewModel.settle() {
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `the screen shows three steps in the order of REMINDER-19`() = runTest(dispatcher) {
        val model = started()

        assertEquals(
            listOf(PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS, PermissionStep.AUTOSTART),
            model.state.value.steps.map { it.first },
        )
    }

    @Test
    fun `before the first read the screen claims nothing about permissions`() = runTest(dispatcher) {
        val model = world.viewModel()

        // Ни «всё выдано», ни «ничего не выдано»: систему ещё не спрашивали.
        assertNull(model.state.value.checklist)
        assertTrue(model.state.value.steps.isEmpty())
    }

    @Test
    fun `the step indicator is shown at first launch and hidden on re-entry from settings`() = runTest(dispatcher) {
        assertTrue(started(OnboardingSource.FIRST_LAUNCH).state.value.showStepIndicator)
        assertFalse(started(OnboardingSource.SETTINGS).state.value.showStepIndicator)
    }

    @Test
    fun `the step indicator shows the second of three steps as the mock-up does`() = runTest(dispatcher) {
        val state = started().state.value

        assertEquals(3, state.totalSteps)
        assertEquals(2, state.currentStep)
    }

    @Test
    fun `granting a permission changes the card on the next read without leaving the screen`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.EXACT_ALARMS)
        val model = started()
        assertFalse(model.state.value.checklist!!.exactAlarms.isResolved)

        world.permissions.grant(ReminderPermission.EXACT_ALARMS)
        model.refresh()
        model.settle()

        assertTrue(model.state.value.checklist!!.exactAlarms.isResolved)
        assertNull("the person stays on the screen", model.exit.value)
    }

    @Test
    fun `a refused permission leaves the card unresolved and the screen open`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.EXACT_ALARMS)
        val model = started()

        model.onPermissionResult(PermissionStep.EXACT_ALARMS, granted = false)
        model.settle()

        assertFalse(model.state.value.checklist!!.exactAlarms.isResolved)
        assertNull(model.exit.value)
        assertTrue(world.log.records.contains("result:EXACT_ALARMS:false"))
    }

    @Test
    fun `the notifications step asks the system to show its dialog`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.NOTIFICATIONS)
        val model = started()

        val needsDialog = model.onStepAction(PermissionStep.NOTIFICATIONS)

        assertTrue("the dialog belongs to the Activity, not to the model", needsDialog)
        assertTrue(world.log.records.contains("requested:NOTIFICATIONS"))
        assertTrue(world.navigator.opened.isEmpty())
    }

    @Test
    fun `the exact alarms step opens the special access screen and no dialog`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.EXACT_ALARMS)
        val model = started()

        val needsDialog = model.onStepAction(PermissionStep.EXACT_ALARMS)

        assertFalse(needsDialog)
        assertEquals(listOf(FakeNavigator.EXACT_ALARMS), world.navigator.opened)
    }

    @Test
    fun `a granted step does nothing when tapped`() = runTest(dispatcher) {
        val model = started()

        assertFalse(model.onStepAction(PermissionStep.NOTIFICATIONS))
        assertTrue(world.navigator.opened.isEmpty())
    }

    @Test
    fun `the autostart step opens the manufacturer screen`() = runTest(dispatcher) {
        val model = started()

        model.onStepAction(PermissionStep.AUTOSTART)
        model.settle()

        assertEquals(listOf(FakeNavigator.AUTOSTART), world.navigator.opened)
        assertFalse(model.state.value.showAutostartInstructions)
        assertTrue(world.log.records.contains("autostart:OPENED"))
    }

    @Test
    fun `on a firmware without that screen the person gets the written instructions`() = runTest(dispatcher) {
        world.navigator.autostartResult = AutostartOpenResult.FALLBACK_TO_INSTRUCTIONS
        val model = started()

        model.onStepAction(PermissionStep.AUTOSTART)
        model.settle()

        assertTrue(model.state.value.showAutostartInstructions)
        assertTrue(world.log.records.contains("autostart:FALLBACK_TO_INSTRUCTIONS"))
    }

    @Test
    fun `the autostart step stays unresolved with the battery hint in place`() = runTest(dispatcher) {
        world.hints.batteryIgnored = true

        val autostart = started().state.value.checklist!!.autostart

        assertEquals(setOf(AutostartHint.BATTERY_OPTIMIZATION_IGNORED), autostart.hints)
        assertFalse("REMINDER-20: hints never resolve the step", autostart.isResolved)
        assertFalse(autostart.verifiedBySystem)
    }

    @Test
    fun `manual confirmation resolves the autostart step and is recorded`() = runTest(dispatcher) {
        val model = started()

        model.onAutostartConfirmed(true)
        model.settle()

        assertTrue(model.state.value.checklist!!.autostart.confirmedByUser)
        assertTrue(world.log.records.contains("confirmed:true"))
    }

    @Test
    fun `confirming the autostart step hides the written instructions`() = runTest(dispatcher) {
        world.navigator.autostartResult = AutostartOpenResult.FALLBACK_TO_INSTRUCTIONS
        val model = started()
        model.onStepAction(PermissionStep.AUTOSTART)
        model.settle()

        model.onAutostartConfirmed(true)
        model.settle()

        assertFalse(model.state.value.showAutostartInstructions)
    }

    @Test
    fun `skipping the onboarding opens Today and loses nothing`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.NOTIFICATIONS)
        world.permissions.deny(ReminderPermission.EXACT_ALARMS)
        val model = started()

        model.onSkip()
        model.settle()

        assertEquals(OnboardingExit.TODAY, model.exit.value)
        assertTrue(world.store.seen())
        assertTrue(world.log.records.contains("left:SKIPPED:3"))
    }

    @Test
    fun `a passed chain leads on to the watch setup`() = runTest(dispatcher) {
        world.store.setAutostartConfirmed(true)
        val model = started()

        model.onNext()
        model.settle()

        assertEquals(OnboardingExit.WATCH_SETUP, model.exit.value)
        assertTrue(world.log.records.contains("left:NEXT:0"))
    }

    @Test
    fun `next with an unfinished chain leads where skipping leads`() = runTest(dispatcher) {
        val model = started()

        model.onNext()
        model.settle()

        // Обещать подключение часов тому, у кого напоминание ещё не дойдёт до телефона,
        // значит увести его от единственной причины, по которой он на этом экране.
        assertEquals(OnboardingExit.TODAY, model.exit.value)
    }

    @Test
    fun `passing and skipping store the very same fact`() = runTest(dispatcher) {
        world.store.setAutostartConfirmed(true)
        val model = started()

        model.onNext()
        model.settle()

        // НАП-21, НФТ-52: хранится «видел экран», а не «прошёл онбординг».
        assertTrue(world.store.seen())
    }

    @Test
    fun `a permission revoked between two reads is noticed and named`() = runTest(dispatcher) {
        val model = started()
        assertTrue(model.state.value.revoked.isEmpty())

        world.permissions.deny(ReminderPermission.NOTIFICATIONS)
        model.refresh()
        model.settle()

        assertEquals(listOf(PermissionStep.NOTIFICATIONS), model.state.value.revoked)
        assertTrue(world.log.records.contains("revoked:NOTIFICATIONS"))
    }

    @Test
    fun `a chain with every permission granted is still unreliable until the autostart step`() = runTest(dispatcher) {
        val state = started().state.value

        assertFalse(state.chainComplete)
        assertTrue(state.checklist!!.remindersUnreliable)
        assertEquals(1, state.checklist!!.unresolvedCount)
    }

    @Test
    fun `opening the screen is logged once with its source and step states`() = runTest(dispatcher) {
        val model = started(OnboardingSource.SETTINGS)

        model.refresh(logOpening = true)
        model.settle()

        assertEquals(1, world.log.records.count { it.startsWith("opened:") })
        assertTrue(world.log.records.contains("opened:SETTINGS:1"))
    }

    @Test
    fun `entering the screen clears the exit left by the previous leave`() = runTest(dispatcher) {
        val model = started()

        model.onSkip()
        model.settle()
        assertEquals(OnboardingExit.TODAY, model.exit.value)

        model.start(OnboardingSource.TODAY_WARNING)
        model.settle()

        // ОВ-86: исход — событие ухода, а не свойство экрана. Унаследованный исход закрыл бы
        // повторно открытый экран сам или не дал бы закрыть его тем же исходом.
        assertNull(model.exit.value)
    }

    @Test
    fun `every entry emits the same exit again after the start reset`() = runTest(dispatcher) {
        val exits = mutableListOf<OnboardingExit?>()
        val model = started()
        val observer = launch { model.exit.collect { exits += it } }
        model.settle()

        model.onSkip()
        model.settle()
        model.start(OnboardingSource.TODAY_WARNING)
        model.settle()
        model.onSkip()
        model.settle()

        // StateFlow не эмитит равное значение: без сброса второй onSkip не породил бы
        // эмиссии, и LaunchedEffect(exit) в маршруте не перезапустился бы (ОВ-86).
        assertEquals(listOf(null, OnboardingExit.TODAY, null, OnboardingExit.TODAY), exits)
        observer.cancel()
    }
}

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
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.OnboardingEntry
import ru.stohastika.sklyanka.domain.analytics.OnboardingStep
import ru.stohastika.sklyanka.domain.analytics.StepResult
import ru.stohastika.sklyanka.domain.onboarding.ManualStepHint
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import java.time.Instant
import ru.stohastika.sklyanka.domain.analytics.OnboardingOutcome as AnalyticsOutcome

/**
 * Поведение экрана «Настроить надёжность» (НАП-18 … НАП-21, НФТ-52, ОВ-106, ОВ-107).
 *
 * Проверяется на JVM: устройства в среде задачи нет, а расчёту состояния цепочки,
 * порядку вызовов и следу в журнале оно и не нужно. Что остаётся за устройством —
 * открывается ли настоящий системный экран и доходит ли настоящее напоминание —
 * перечислено в `TDD.md §12.1`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val world = OnboardingTestWorld(dispatcher.scheduler)

    @Before
    fun replaceMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun started(
        source: OnboardingSource = OnboardingSource.FIRST_LAUNCH,
        outageSince: Instant? = null,
    ): OnboardingViewModel = world.viewModel().also {
        it.start(source, outageSince)
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun OnboardingViewModel.settle() {
        dispatcher.scheduler.advanceUntilIdle()
    }

    private suspend fun allVendorConfirmed() {
        world.store.setAppStartupConfirmed(true)
        world.store.setBatterySavingConfirmed(true)
        world.store.setRecentsPinConfirmed(true)
    }

    @Test
    fun `on EMUI the screen shows five steps in the order of OV-106`() = runTest(dispatcher) {
        val model = started()

        assertEquals(
            listOf(
                PermissionStep.NOTIFICATIONS,
                PermissionStep.EXACT_ALARMS,
                PermissionStep.APP_STARTUP,
                PermissionStep.BATTERY_SAVING,
                PermissionStep.RECENTS_PIN,
            ),
            model.state.value.steps.map { it.first },
        )
    }

    @Test
    fun `on any other phone the screen shows only the two common steps`() = runTest(dispatcher) {
        world.vendor.vendor = VendorProfile.GENERIC

        assertEquals(
            listOf(PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS),
            started().state.value.steps.map { it.first },
        )
    }

    @Test
    fun `before the first read the screen claims nothing about permissions`() = runTest(dispatcher) {
        val model = world.viewModel()

        // Ни «всё выдано», ни «ничего не выдано»: систему ещё не спрашивали.
        assertNull(model.state.value.checklist)
        assertTrue(model.state.value.steps.isEmpty())
        assertFalse(model.state.value.allConfigured)
    }

    @Test
    fun `the step indicator is shown at first launch and hidden on re-entry`() = runTest(dispatcher) {
        assertTrue(started(OnboardingSource.FIRST_LAUNCH).state.value.showStepIndicator)
        assertFalse(started(OnboardingSource.SETTINGS).state.value.showStepIndicator)
        assertFalse(started(OnboardingSource.TODAY_WARNING).state.value.showStepIndicator)
        assertFalse(started(OnboardingSource.OUTAGE_BANNER).state.value.showStepIndicator)
    }

    @Test
    fun `a banner entry focuses the first unresolved step`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.NOTIFICATIONS)

        val fromOutage = started(OnboardingSource.OUTAGE_BANNER)
        assertEquals(PermissionStep.NOTIFICATIONS, fromOutage.state.value.focusStep)

        world.permissions.grant(ReminderPermission.NOTIFICATIONS)
        world.permissions.deny(ReminderPermission.EXACT_ALARMS)
        val fromToday = started(OnboardingSource.TODAY_WARNING)
        assertEquals(PermissionStep.EXACT_ALARMS, fromToday.state.value.focusStep)
    }

    @Test
    fun `the first launch and settings entries do not focus anything`() = runTest(dispatcher) {
        assertNull(started(OnboardingSource.FIRST_LAUNCH).state.value.focusStep)
        assertNull(started(OnboardingSource.SETTINGS).state.value.focusStep)
    }

    @Test
    fun `a return to the screen does not re-focus the step`() = runTest(dispatcher) {
        val model = started(OnboardingSource.TODAY_WARNING)
        val focus = model.state.value.focusStep

        model.refresh()
        model.settle()

        // Фокус — событие входа, а не свойство экрана: после возврата из системных настроек
        // список не должен прыгать заново.
        assertEquals(focus, model.state.value.focusStep)
    }

    @Test
    fun `the outage moment of the banner entry lives in the state`() = runTest(dispatcher) {
        val since = Instant.parse("2026-10-01T07:30:00Z")

        assertEquals(since, started(OnboardingSource.OUTAGE_BANNER, outageSince = since).state.value.outageSince)
        assertNull(started(OnboardingSource.SETTINGS).state.value.outageSince)
    }

    @Test
    fun `the banner entry is recorded with its focus`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.EXACT_ALARMS)

        started(OnboardingSource.OUTAGE_BANNER)

        assertTrue(world.log.records.contains("route:OUTAGE_BANNER:EXACT_ALARMS"))
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
    fun `a channel issue opens the notification settings instead of the dialog`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.NOTIFICATION_CHANNEL)
        val model = started()

        val needsDialog = model.onStepAction(PermissionStep.NOTIFICATIONS)
        model.settle()

        assertFalse("the channel is fixed on the system screen", needsDialog)
        assertEquals(listOf(FakeNavigator.NOTIFICATIONS), world.navigator.opened)
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
    fun `the app startup step opens the manufacturer screen`() = runTest(dispatcher) {
        val model = started()

        model.onStepAction(PermissionStep.APP_STARTUP)
        model.settle()

        assertEquals(listOf(FakeNavigator.APP_STARTUP), world.navigator.opened)
        assertTrue(model.state.value.instructions.isEmpty())
        assertTrue(world.log.records.contains("vendor:APP_STARTUP:OPENED"))
    }

    @Test
    fun `a missing screen gives the written instructions for that step`() = runTest(dispatcher) {
        world.navigator.appStartupResult = StepOpenResult.FALLBACK_TO_INSTRUCTIONS
        val model = started()

        model.onStepAction(PermissionStep.APP_STARTUP)
        model.settle()

        assertEquals(setOf(PermissionStep.APP_STARTUP), model.state.value.instructions)
        assertTrue(world.log.records.contains("vendor:APP_STARTUP:FALLBACK_TO_INSTRUCTIONS"))
    }

    @Test
    fun `a failed battery step does not show the app startup instruction`() = runTest(dispatcher) {
        world.navigator.batterySavingResult = StepOpenResult.FALLBACK_TO_INSTRUCTIONS
        val model = started()

        model.onStepAction(PermissionStep.BATTERY_SAVING)
        model.settle()

        // Инструкции по шагам: запасной путь одного шага не подменяет путь другого.
        assertEquals(setOf(PermissionStep.BATTERY_SAVING), model.state.value.instructions)
    }

    @Test
    fun `the recents pin step has no system screen to open`() = runTest(dispatcher) {
        val model = started()

        assertFalse(model.onStepAction(PermissionStep.RECENTS_PIN))
        assertTrue(world.navigator.opened.isEmpty())
    }

    @Test
    fun `a vendor step stays unresolved with the battery hint in place`() = runTest(dispatcher) {
        world.hints.batteryIgnored = true

        val battery = started().state.value.checklist!!.batterySaving

        assertEquals(setOf(ManualStepHint.BATTERY_OPTIMIZATION_IGNORED), battery.hints)
        assertFalse("REMINDER-20: hints never resolve the step", battery.isResolved)
        assertFalse(battery.verifiedBySystem)
    }

    @Test
    fun `the hint belongs to the battery step and not to the app startup one`() = runTest(dispatcher) {
        world.hints.batteryIgnored = true

        val checklist = started().state.value.checklist!!

        assertTrue(checklist.appStartup.hints.isEmpty())
        assertTrue(checklist.recentsPin.hints.isEmpty())
    }

    @Test
    fun `manual confirmation resolves its own vendor step and is recorded`() = runTest(dispatcher) {
        val model = started()

        model.onManualConfirmed(PermissionStep.APP_STARTUP, true)
        model.settle()

        val checklist = model.state.value.checklist!!
        assertTrue(checklist.appStartup.confirmedByUser)
        assertFalse("other steps stay unresolved", checklist.batterySaving.isResolved)
        assertTrue(world.log.records.contains("manual:APP_STARTUP:true"))
    }

    @Test
    fun `a vendor confirmation can be withdrawn`() = runTest(dispatcher) {
        val model = started()
        model.onManualConfirmed(PermissionStep.BATTERY_SAVING, true)
        model.settle()

        model.onManualConfirmed(PermissionStep.BATTERY_SAVING, false)
        model.settle()

        assertFalse(model.state.value.checklist!!.batterySaving.isResolved)
    }

    @Test
    fun `confirming a vendor step hides its written instructions`() = runTest(dispatcher) {
        world.navigator.appStartupResult = StepOpenResult.FALLBACK_TO_INSTRUCTIONS
        val model = started()
        model.onStepAction(PermissionStep.APP_STARTUP)
        model.settle()

        model.onManualConfirmed(PermissionStep.APP_STARTUP, true)
        model.settle()

        assertTrue(model.state.value.instructions.isEmpty())
    }

    @Test
    fun `the chain is configured only when every vendor step is confirmed too`() = runTest(dispatcher) {
        val model = started()

        assertFalse(model.state.value.allConfigured)
        allVendorConfirmed()
        model.refresh()
        model.settle()

        assertTrue(model.state.value.allConfigured)
    }

    @Test
    fun `on a non-Huawei phone the two permissions alone close the chain`() = runTest(dispatcher) {
        world.vendor.vendor = VendorProfile.GENERIC

        assertTrue(started().state.value.allConfigured)
    }

    @Test
    fun `skipping the onboarding opens Today and loses nothing`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.NOTIFICATIONS)
        world.permissions.deny(ReminderPermission.EXACT_ALARMS)
        val model = started()

        model.onSkip()
        model.settle()

        assertEquals(Unit, model.exit.value)
        assertTrue(world.store.seen())
        assertTrue(world.log.records.contains("left:SKIPPED:5"))
    }

    @Test
    fun `a passed chain leaves the onboarding for Today`() = runTest(dispatcher) {
        allVendorConfirmed()
        val model = started()

        model.onNext()
        model.settle()

        assertEquals(Unit, model.exit.value)
        assertTrue(world.log.records.contains("left:NEXT:0"))
    }

    @Test
    fun `next with an unfinished chain leads where skipping leads`() = runTest(dispatcher) {
        val model = started()

        model.onNext()
        model.settle()

        assertEquals(Unit, model.exit.value)
    }

    @Test
    fun `passing and skipping store the very same fact`() = runTest(dispatcher) {
        allVendorConfirmed()
        val model = started()

        model.onNext()
        model.settle()

        // НАП-21, НФТ-52: хранится «видел экран», а не «прошёл онбординг».
        assertTrue(world.store.seen())
    }

    @Test
    fun `the quiet done of a re-entry returns to the entry point`() = runTest(dispatcher) {
        val model = started(OnboardingSource.SETTINGS)

        model.onDone()
        model.settle()

        assertEquals(Unit, model.exit.value)
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
    fun `opening the screen is logged once with its source and step states`() = runTest(dispatcher) {
        val model = started(OnboardingSource.SETTINGS)

        model.refresh(logOpening = true)
        model.settle()

        assertEquals(1, world.log.records.count { it.startsWith("opened:") })
        assertTrue(world.log.records.contains("opened:SETTINGS:3"))
    }

    @Test
    fun `entering the screen clears the exit left by the previous leave`() = runTest(dispatcher) {
        val model = started()

        model.onSkip()
        model.settle()
        assertEquals(Unit, model.exit.value)

        model.start(OnboardingSource.TODAY_WARNING)
        model.settle()

        // ОВ-86: исход — событие ухода, а не свойство экрана. Унаследованный исход закрыл бы
        // повторно открытый экран сам или не дал бы закрыть его тем же исходом.
        assertNull(model.exit.value)
    }

    @Test
    fun `every entry emits the same exit again after the start reset`() = runTest(dispatcher) {
        val exits = mutableListOf<Unit?>()
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
        assertEquals(listOf(null, Unit, null, Unit), exits)
        observer.cancel()
    }

    @Test
    fun `each entry reports its own onboarding entry`() = runTest(dispatcher) {
        val entries = mapOf(
            OnboardingSource.FIRST_LAUNCH to OnboardingEntry.FIRST_LAUNCH,
            OnboardingSource.SETTINGS to OnboardingEntry.SETTINGS,
            OnboardingSource.TODAY_WARNING to OnboardingEntry.TODAY_BANNER,
            OnboardingSource.OUTAGE_BANNER to OnboardingEntry.BREAK_BANNER,
        )

        entries.keys.forEach { started(it) }

        // Каталог знает входы под своими именами: баннер «Сегодня» — это today_banner,
        // а баннер перерыва — break_banner. Отображение живёт в одном месте модели.
        assertEquals(
            entries.values.map { it.wire },
            world.analytics.records()
                .filter { it.name == AnalyticsEventName.ONBOARDING_STARTED }
                .map { it.properties[AnalyticsProperty.ENTRY] },
        )
    }

    @Test
    fun `a return to the screen does not repeat the entry event`() = runTest(dispatcher) {
        val model = started(OnboardingSource.TODAY_WARNING)

        model.refresh()
        model.settle()

        // Событие принадлежит входу, а не показу: возврат из системных настроек его не повторяет.
        assertEquals(
            1,
            world.analytics.names().count { it == AnalyticsEventName.ONBOARDING_STARTED },
        )
    }

    @Test
    fun `a granted permission is reported as granted and a refusal as skipped`() = runTest(dispatcher) {
        val model = started()

        model.onPermissionResult(PermissionStep.NOTIFICATIONS, granted = true)
        model.settle()
        model.onPermissionResult(PermissionStep.EXACT_ALARMS, granted = false)
        model.settle()

        assertEquals(
            listOf(
                OnboardingStep.NOTIFICATIONS.wire to StepResult.GRANTED.wire,
                OnboardingStep.EXACT_ALARMS.wire to StepResult.SKIPPED.wire,
            ),
            stepResults(),
        )
    }

    @Test
    fun `a manual confirmation is reported as confirmed and its withdrawal as skipped`() = runTest(dispatcher) {
        val model = started()

        model.onManualConfirmed(PermissionStep.APP_STARTUP, confirmed = true)
        model.settle()
        model.onManualConfirmed(PermissionStep.BATTERY_SAVING, confirmed = false)
        model.settle()

        assertEquals(
            listOf(
                OnboardingStep.APP_LAUNCH.wire to StepResult.CONFIRMED.wire,
                OnboardingStep.BATTERY.wire to StepResult.SKIPPED.wire,
            ),
            stepResults(),
        )
    }

    @Test
    fun `opening a system screen without a result reports nothing`() = runTest(dispatcher) {
        world.permissions.deny(ReminderPermission.EXACT_ALARMS)
        val model = started()

        model.onStepAction(PermissionStep.EXACT_ALARMS)
        model.onStepAction(PermissionStep.APP_STARTUP)
        model.settle()

        assertEquals(listOf(FakeNavigator.EXACT_ALARMS, FakeNavigator.APP_STARTUP), world.navigator.opened)
        // У открытия системного экрана результата ещё нет: событие — про исход шага.
        assertTrue(stepResults().isEmpty())
    }

    @Test
    fun `leaving by next is reported as completed and by skip as skipped`() = runTest(dispatcher) {
        allVendorConfirmed()
        val passed = started(OnboardingSource.FIRST_LAUNCH)
        passed.onNext()
        passed.settle()

        val skipped = started(OnboardingSource.TODAY_WARNING)
        skipped.onSkip()
        skipped.settle()

        assertEquals(
            listOf(
                OnboardingEntry.FIRST_LAUNCH.wire to AnalyticsOutcome.COMPLETED.wire,
                OnboardingEntry.TODAY_BANNER.wire to AnalyticsOutcome.SKIPPED.wire,
            ),
            world.analytics.records()
                .filter { it.name == AnalyticsEventName.ONBOARDING_FINISHED }
                .map { it.properties[AnalyticsProperty.ENTRY] to it.properties[AnalyticsProperty.OUTCOME] },
        )
    }

    @Test
    fun `without consent not a single product event is written`() = runTest(dispatcher) {
        listOf(AnalyticsConsentState.NOT_ASKED, AnalyticsConsentState.DECLINED).forEach { consent ->
            val quiet = OnboardingTestWorld(dispatcher.scheduler, analyticsConsent = consent)
            val model = quiet.viewModel()

            model.start(OnboardingSource.OUTAGE_BANNER)
            model.onPermissionResult(PermissionStep.NOTIFICATIONS, granted = true)
            model.onManualConfirmed(PermissionStep.APP_STARTUP, confirmed = true)
            model.onSkip()
            dispatcher.scheduler.advanceUntilIdle()

            // АН-2: гейт согласия — в записи, а не в точках вызова, поэтому молчит всё.
            assertTrue(
                "$consent: ${quiet.analytics.queue.records}",
                quiet.analytics.queue.records.isEmpty(),
            )
            assertTrue(
                "$consent: ${quiet.analytics.transport.delivered}",
                quiet.analytics.transport.delivered.isEmpty(),
            )
        }
    }

    /** Пары «шаг → результат» в порядке записи. */
    private fun stepResults(): List<Pair<String?, String?>> = world.analytics.records()
        .filter { it.name == AnalyticsEventName.ONBOARDING_STEP_RESULT }
        .map { it.properties[AnalyticsProperty.STEP] to it.properties[AnalyticsProperty.RESULT] }
}

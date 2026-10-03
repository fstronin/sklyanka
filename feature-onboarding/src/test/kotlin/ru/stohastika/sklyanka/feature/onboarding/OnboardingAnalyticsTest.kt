package ru.stohastika.sklyanka.feature.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.OnboardingEntry
import ru.stohastika.sklyanka.domain.analytics.OnboardingOutcome
import ru.stohastika.sklyanka.domain.analytics.OnboardingStep
import ru.stohastika.sklyanka.domain.analytics.StepResult
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep

/**
 * Воронка онбординга уходит через порт `Analytics` (SKL-82, ПМ-26).
 *
 * Проверяются точки, а не пересказ шагов: вход, результат ручного подтверждения и исход
 * ухода. Значения — закрытые перечисления каталога, доменных данных в событиях нет.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingAnalyticsTest {

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

    @Test
    fun `opening the screen reports the entry`() = runTest {
        world.viewModel().start(OnboardingSource.FIRST_LAUNCH)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            world.analytics.events.contains(AnalyticsEvent.OnboardingStarted(OnboardingEntry.FIRST_LAUNCH)),
        )
    }

    @Test
    fun `the banner entry is reported with its own product name`() = runTest {
        world.viewModel().start(OnboardingSource.TODAY_WARNING)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            world.analytics.events.contains(AnalyticsEvent.OnboardingStarted(OnboardingEntry.TODAY_BANNER)),
        )
    }

    @Test
    fun `a manual confirmation reports the step result`() = runTest {
        val viewModel = world.viewModel()
        viewModel.start(OnboardingSource.SETTINGS)
        dispatcher.scheduler.advanceUntilIdle()
        world.analytics.events.clear()

        viewModel.onManualConfirmed(PermissionStep.APP_STARTUP, true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf<AnalyticsEvent>(
                AnalyticsEvent.OnboardingStepResult(OnboardingStep.APP_LAUNCH, StepResult.CONFIRMED),
            ),
            world.analytics.events,
        )
    }

    @Test
    fun `skipping reports the finish with the outcome`() = runTest {
        val viewModel = world.viewModel()
        viewModel.start(OnboardingSource.FIRST_LAUNCH)
        dispatcher.scheduler.advanceUntilIdle()
        world.analytics.events.clear()

        viewModel.onSkip()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            world.analytics.events.contains(
                AnalyticsEvent.OnboardingFinished(OnboardingEntry.FIRST_LAUNCH, OnboardingOutcome.SKIPPED),
            ),
        )
    }
}

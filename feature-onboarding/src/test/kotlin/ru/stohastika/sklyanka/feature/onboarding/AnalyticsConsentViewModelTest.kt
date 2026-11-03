package ru.stohastika.sklyanka.feature.onboarding

import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.DecideAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ShouldShowAnalyticsConsentUseCase

/**
 * Поведение экрана согласия (SKL-165, АН-1, АН-2, TDD §5.5).
 *
 * Проверяется на JVM: очередь, согласие и поставщик — настоящие доменные объекты с
 * подменёнными портами, поэтому тест видит ровно то, что увидит продукт. Запись
 * асинхронна, и события появляются после `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsConsentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun replaceMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun world(consent: AnalyticsConsentState = AnalyticsConsentState.NOT_ASKED): AnalyticsWorld =
        AnalyticsWorld(consent)

    private fun AnalyticsWorld.viewModel(): AnalyticsConsentViewModel {
        val recorder = recorder(CoroutineScope(dispatcher))
        return AnalyticsConsentViewModel(
            shouldShow = ShouldShowAnalyticsConsentUseCase(store),
            decideConsent = DecideAnalyticsConsentUseCase(store, recorder),
            recorder = recorder,
        )
    }

    @Test
    fun `at first launch the question is shown and its display is remembered`() = runTest(dispatcher) {
        val world = world()
        val model = world.viewModel()

        model.show(AnalyticsConsentSource.FIRST_LAUNCH)
        assertNull("before the answer nothing is claimed", model.visible.value)
        advanceUntilIdle()

        assertEquals(true, model.visible.value)
        assertEquals(
            listOf(AnalyticsEventName.CONSENT_SHOWN),
            world.names(),
        )
        assertEquals(listOf("first_launch"), world.property(AnalyticsProperty.SOURCE))
    }

    @Test
    fun `an already refused question is not shown again`() = runTest(dispatcher) {
        val world = world(AnalyticsConsentState.DECLINED)
        val model = world.viewModel()

        model.show()
        advanceUntilIdle()

        assertEquals(false, model.visible.value)
        assertTrue("a refuser sends nothing, not even the display", world.names().isEmpty())
    }

    @Test
    fun `allow records the choice and leaves the screen`() = runTest(dispatcher) {
        val world = world()
        val model = world.viewModel()
        model.show()
        advanceUntilIdle()

        model.choose(AnalyticsConsentState.ALLOWED, AnalyticsConsentSource.FIRST_LAUNCH)
        advanceUntilIdle()

        assertEquals(false, model.visible.value)
        assertEquals(
            listOf(AnalyticsEventName.CONSENT_SHOWN, AnalyticsEventName.CONSENT_CHOICE),
            world.names(),
        )
        assertEquals(listOf("accepted"), world.property(AnalyticsProperty.CHOICE))
        assertEquals(0L, world.queue.size.toLong())
    }

    @Test
    fun `not now records the refusal and wipes the queue`() = runTest(dispatcher) {
        val world = world()
        val model = world.viewModel()
        model.show()
        advanceUntilIdle()
        assertEquals(1L, world.queue.size.toLong())

        model.choose(AnalyticsConsentState.DECLINED, AnalyticsConsentSource.FIRST_LAUNCH)
        advanceUntilIdle()

        assertEquals(false, model.visible.value)
        assertEquals(0L, world.queue.size.toLong())
        assertTrue("a refuser sends nothing at all", world.transport.delivered.isEmpty())
    }

    @Test
    fun `a repeated show does not duplicate the consent display`() = runTest(dispatcher) {
        val world = world()
        val model = world.viewModel()

        model.show()
        model.show()
        advanceUntilIdle()

        assertEquals(1, world.names().count { it == AnalyticsEventName.CONSENT_SHOWN })
    }

    @Test
    fun `a repeated decision is taken once`() = runTest(dispatcher) {
        val world = world()
        val model = world.viewModel()
        model.show()
        advanceUntilIdle()

        model.choose(AnalyticsConsentState.ALLOWED, AnalyticsConsentSource.FIRST_LAUNCH)
        model.choose(AnalyticsConsentState.DECLINED, AnalyticsConsentSource.FIRST_LAUNCH)
        advanceUntilIdle()

        assertEquals(1, world.names().count { it == AnalyticsEventName.CONSENT_CHOICE })
        assertFalse(world.names().contains(AnalyticsEventName.ANALYTICS_ENABLED))
    }
}

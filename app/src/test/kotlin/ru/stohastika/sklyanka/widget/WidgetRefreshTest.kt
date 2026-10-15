package ru.stohastika.sklyanka.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetRefresher

/**
 * Наблюдатель обновления плит (SKL-118, `TDD.md §7`).
 *
 * Проверяется ровно то, что ловит молчаливую регрессию: наблюдатель просит перерисовку
 * и на текущем состоянии (пропущенное событие — плита со вчерашним днём, П-7), и на
 * каждом следующем изменении. Слежение идёт без ожидания диспетчера: значение из потока
 * доходит до сборщика сразу, как в продукте при готовом хранилище.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRefreshTest {

    @Test
    fun `the observer asks for a redraw from the first change on`() = runTest {
        val sources = FakeWidgetSources()
        var refreshes = 0
        val refresher = object : WidgetRefresher {
            override suspend fun refreshAll() {
                refreshes++
            }
        }

        WidgetRefresh(sources = sources, refresher = refresher)
            .start(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        assertEquals("the current state already deserves a redraw", 1, refreshes)

        sources.emit()
        assertEquals("every next change redraws the plates", 2, refreshes)
    }

    /**
     * Источник изменений в руках теста.
     *
     * Состоянием, а не событием: у настоящих источников (потоки хранилища) подписчик сразу
     * получает текущее значение, и наблюдатель обязан перерисовать уже по нему.
     */
    private class FakeWidgetSources : WidgetSources {

        private val revision = MutableStateFlow(0)

        override fun changes(): Flow<Unit> = revision.map { Unit }

        fun emit() {
            revision.value += 1
        }
    }
}

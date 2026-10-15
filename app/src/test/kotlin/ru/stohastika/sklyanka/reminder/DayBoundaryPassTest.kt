package ru.stohastika.sklyanka.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Полуночный проход и перерисовка плит (SKL-118, ПР-1, П-7).
 *
 * Сторож ровно одного утверждения: перерисовка идёт вслед за проходом. Отсутствие её —
 * молчаливая регрессия: плита после полуночи показывала бы вчерашние строки с живой
 * кнопкой, а нажатие по ним молча отбивалось бы.
 */
class DayBoundaryPassTest {

    @Test
    fun `the plates are redrawn after the midnight sweep`() = runTest {
        val order = mutableListOf<String>()

        DayBoundaryPass(
            sweep = { order += SWEEP },
            refresh = { order += REFRESH },
        ).run()

        assertEquals(listOf(SWEEP, REFRESH), order)
    }

    private companion object {
        const val SWEEP = "sweep"
        const val REFRESH = "refresh"
    }
}

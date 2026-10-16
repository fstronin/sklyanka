package ru.stohastika.sklyanka.domain.history

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.today.FakeActiveProfileRepository
import java.time.LocalDate
import java.time.YearMonth

/**
 * Разделы «Про» расширенного отчёта (`docs/norm/06-design.md` §5.15, SKL-119).
 *
 * Проверяется то, ради чего разделы существуют: **что печатать и чего не печатать вовсе**.
 * Пустой раздел — не пустая таблица, а его отсутствие (Р-33), и пороги паттерна здесь
 * не пересказываются: их держит `MissedPatternRuleTest`, а второй экземпляр правила
 * в продукте не заводится (Р-85 п. 4).
 */
class ExtendedReportSectionsTest {

    private val clock = Months.clock()
    private val activeProfile = FakeActiveProfileRepository(Months.PROFILE_ID)
    private val history = FakeHistoryRepository()

    @Test
    fun `the comparison takes a period of the same length right before the reporting one`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = Months.daily(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)) +
                    Months.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                medications = listOf(Months.medication(times = listOf(Months.MORNING))),
            ),
        )

        val comparison = build().invoke(Months.WINDOW).comparison
        assertNotNull(comparison)

        // Сентябрь — 30 суток; предыдущий период такой же длины — 2…31 августа,
        // а не «август целиком»: иначе сравнение мерило бы разные отрезки.
        assertEquals(HistoryWindow(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 31)), history.windows[1])
        assertEquals(30, comparison!!.previous.taken)
        assertEquals(30, comparison.previous.planned)
        assertEquals(30, comparison.current.taken)
        assertEquals(30, comparison.current.planned)
    }

    @Test
    fun `a previous period without intakes leaves no comparison section`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(intakes = Months.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))),
        )

        assertNull(build().invoke(Months.WINDOW).comparison)
    }

    /** Период, целиком лежащий в будущем, сравнивать не с чем: наступивших приёмов в нём нет. */
    @Test
    fun `a previous period that has not started leaves no comparison section`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = Months.daily(
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 31),
                    status = IntakeStatus.PENDING,
                ) +
                    Months.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            ),
        )

        assertNull(build().invoke(Months.WINDOW).comparison)
    }

    @Test
    fun `the dynamics lists the months of the period and skips the ones with nothing to divide`() = runTest {
        val window = HistoryWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31))
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = Months.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)) +
                    listOf(Months.intake(LocalDate.of(2026, 10, 5).atTime(Months.MORNING), IntakeStatus.TAKEN)),
                medications = listOf(Months.medication()),
            ),
        )

        val dynamics = build().invoke(window).dynamics
        assertNotNull(dynamics)

        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), dynamics!!.months.map { it.month })
        assertEquals(30, dynamics.months.first().taken)
        assertEquals(30, dynamics.months.first().planned)
        assertEquals(1, dynamics.months[1].taken)
    }

    @Test
    fun `one month with intakes is not a dynamics`() = runTest {
        val window = HistoryWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31))
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = Months.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)) +
                    listOf(Months.intake(LocalDate.of(2026, 10, 5).atTime(Months.MORNING), IntakeStatus.PENDING)),
                medications = listOf(Months.medication()),
            ),
        )

        assertNull(build().invoke(window).dynamics)
    }

    @Test
    fun `the pattern splits the dates of the leading time by their nature`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = patternIntakes(),
                medications = listOf(Months.medication(times = listOf(Months.MORNING, Months.EVENING))),
            ),
        )

        val pattern = build().invoke(Months.WINDOW).pattern
        assertNotNull(pattern)

        assertEquals(Months.EVENING, pattern!!.time)
        assertEquals(4, pattern.misses)
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 9, 24),
            ),
            pattern.missedDates,
        )
        assertEquals(4, pattern.skippedByPerson)
        assertEquals(0, pattern.missedWithoutMark)
    }

    /** Правило молчит — раздела нет: ложный вывод хуже отсутствующего (Р-85 п. 4). */
    @Test
    fun `an even split of misses leaves no pattern section`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = patternIntakes(eveningMisses = 2, morningMisses = 2),
                medications = listOf(Months.medication(times = listOf(Months.MORNING, Months.EVENING))),
            ),
        )

        assertNull(build().invoke(Months.WINDOW).pattern)
    }

    @Test
    fun `an empty period gives no sections at all`() = runTest {
        history.put(Months.PROFILE_ID, Months.slice(intakes = emptyList()))

        val sections = build().invoke(Months.WINDOW)

        assertTrue(sections.isEmpty)
        assertNull(sections.comparison)
        assertNull(sections.dynamics)
        assertNull(sections.pattern)
    }

    @Test
    fun `the sections are counted for the active profile only`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = Months.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                medications = listOf(Months.medication()),
            ),
        )
        history.put(
            Months.OTHER_PROFILE_ID,
            Months.slice(
                intakes = Months.daily(
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 31),
                    medicationId = Months.SECOND_MEDICATION_ID,
                ),
                medications = listOf(
                    Months.medication(
                        id = Months.SECOND_MEDICATION_ID,
                        profileId = Months.OTHER_PROFILE_ID,
                        name = "med-b",
                    ),
                ),
                profileId = Months.OTHER_PROFILE_ID,
            ),
        )

        assertNull(build().invoke(Months.WINDOW).comparison)
        assertEquals(listOf(Months.PROFILE_ID, Months.PROFILE_ID), history.asked)
    }

    private fun build() = BuildExtendedSectionsUseCase(
        activeProfile = activeProfile,
        history = history,
        calculator = AdherenceCalculator(OnTimeRule.TwoHours),
        missedPattern = MissedPatternRule(),
        time = clock,
    )

    /**
     * Пропуски, у которых явный лидер — вечерний приём: четыре отметки человека в 20:00
     * против двух автоматических пропусков утра (Р-85 п. 4: 0,6 / четыре / два времени / лидер один).
     */
    private fun patternIntakes(eveningMisses: Int = 4, morningMisses: Int = 2) = buildList {
        repeat(morningMisses) { day ->
            add(Months.intake(LocalDate.of(2026, 9, 1 + day).atTime(Months.MORNING), IntakeStatus.MISSED))
        }
        repeat(eveningMisses) { day ->
            add(Months.intake(LocalDate.of(2026, 9, 3 + day * 7).atTime(Months.EVENING), IntakeStatus.SKIPPED))
        }
        repeat(4) { day ->
            add(Months.intake(LocalDate.of(2026, 9, 2 + day).atTime(Months.MORNING), IntakeStatus.TAKEN))
        }
    }
}

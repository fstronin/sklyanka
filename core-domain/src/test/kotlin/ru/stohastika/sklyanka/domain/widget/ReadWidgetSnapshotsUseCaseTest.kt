package ru.stohastika.sklyanka.domain.widget

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.history.FakeHistoryRepository
import ru.stohastika.sklyanka.domain.history.HistorySlice
import ru.stohastika.sklyanka.domain.medication.WeekCounter
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.schedule.FakeIntakeRepository
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.time.FixedTimeSource

/**
 * Чтение снимка виджета из домена (SKL-118, `TDD.md §3`, `§7`).
 *
 * Проверяется то, чего чистые сборки не доказывают: снимок берётся по активному профилю,
 * по своим суткам, а неделя — из истории, единственного источника без фильтра архива (ПР-11).
 */
class ReadWidgetSnapshotsUseCaseTest {

    private val clock = FixedTimeSource(Widget.moment(Widget.at(10)), Widget.ZONE)
    private val profiles = FakeProfileContextProvider(Widget.SELF)
    private val medications = FakeMedicationRepository(clock)
    private val intakes = WidgetIntakeRepository(FakeIntakeRepository(clock), medications)
    private val history = FakeHistoryRepository()
    private val read = ReadWidgetSnapshotsUseCase(intakes, medications, history, profiles, clock)

    @Test
    fun `no active profile means nothing to draw`() = runTest {
        profiles.switchTo(null)

        assertNull(read.day())
        assertNull(read.week())
        assertNull(read.medication("mag"))
    }

    @Test
    fun `the snapshot follows the active profile`() = runTest {
        medications.put(Widget.medication("mag", name = "magnesium"))
        medications.put(Widget.medication("asp", name = "aspirin", profileId = Widget.FOREIGN_PROFILE))
        intakes.put(Widget.intake("i-mag", "mag", Widget.at(8)))
        intakes.put(Widget.intake("i-asp", "asp", Widget.at(9)))

        assertEquals(listOf("magnesium"), read.day()?.items?.map { it.medicationName })

        profiles.switchTo(Widget.FOREIGN)
        val foreign = requireNotNull(read.day())
        assertEquals(listOf("aspirin"), foreign.items.map { it.medicationName })
        assertEquals(WidgetProfileIndication("mother", Widget.FOREIGN_COLOUR), foreign.profile)
    }

    @Test
    fun `a medication of another profile is not drawn`() = runTest {
        medications.put(Widget.medication("asp", name = "aspirin", profileId = Widget.FOREIGN_PROFILE))
        intakes.put(Widget.intake("i-asp", "asp", Widget.at(9)))

        assertNull(read.medication("asp"))

        profiles.switchTo(Widget.FOREIGN)
        assertEquals(listOf("i-asp"), read.medication("asp")?.items?.map { it.intakeId })
    }

    @Test
    fun `after midnight the snapshot names the new day and keeps no yesterday intakes`() = runTest {
        medications.put(Widget.medication("mag", name = "magnesium"))
        intakes.put(Widget.intake("i-today", "mag", Widget.at(8)))
        intakes.put(Widget.intake("i-old", "mag", Widget.TODAY.minusDays(1).atTime(8, 0)))

        assertEquals(Widget.TODAY, read.day()?.date)

        clock.advanceTo(Widget.moment(Widget.TODAY.plusDays(1).atTime(0, 1)))
        val next = requireNotNull(read.day())
        assertEquals(Widget.TODAY.plusDays(1), next.date)
        assertEquals(emptyList<String>(), next.items.map { it.intakeId })
        assertTrue(next.empty)
    }

    @Test
    fun `the week asks the history for the last seven days of the active profile`() = runTest {
        history.put(Widget.SELF_PROFILE, slice())

        val week = requireNotNull(read.week())

        assertEquals(BuildWidgetSnapshots.weekOf(Widget.TODAY), week.days.map { it.date })
        assertEquals(Widget.TODAY, week.date)
        assertEquals(listOf(Widget.SELF_PROFILE), history.asked)
        assertEquals(Widget.TODAY.minusDays(6) to Widget.TODAY, history.windows.single().let { it.from to it.to })
    }

    @Test
    fun `a course archived inside the week keeps its taken intakes`() = runTest {
        // История не фильтрует архив (ПР-11): неделя виджета считается по тем же данным,
        // что блок «Последние 7 дней» карточки лекарства.
        history.put(
            Widget.SELF_PROFILE,
            slice(
                intakes = listOf(
                    Widget.intake("i-mag", "mag", Widget.TODAY.minusDays(1).atTime(8, 0), status = IntakeStatus.TAKEN),
                ),
                medications = listOf(Widget.medication("mag", name = "magnesium", archived = true)),
            ),
        )

        val week = requireNotNull(read.week())

        assertEquals(WeekCounter.Fraction(taken = 1, planned = 1), week.counter)
    }

    private fun slice(
        intakes: List<ru.stohastika.sklyanka.domain.model.Intake> = emptyList(),
        medications: List<ru.stohastika.sklyanka.domain.model.Medication> = emptyList(),
    ) = HistorySlice(
        profileId = Widget.SELF_PROFILE,
        intakes = intakes,
        medications = medications,
        historyStartsAt = Widget.TODAY.minusMonths(1),
    )
}

package ru.stohastika.sklyanka.domain.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.medication.WeekCounter
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.profile.ProfileContext
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Снимки дня, недели и одного лекарства (SKL-118, `TDD.md §10.1`).
 *
 * Проверяется снимок, а не разметка: разметка Glance в сценарной приёмке не проверяется,
 * а всё, что виджет показывает и решает, собрано здесь — значит, здесь и проверяется.
 */
class WidgetSnapshotsTest {

    private val magnesium = Widget.medication(
        id = "mag",
        name = "magnesium",
        times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
    )
    private val aspirin = Widget.medication(id = "asp", name = "aspirin", times = listOf(LocalTime.of(9, 0)))

    @Test
    fun `the day keeps the intakes of the today screen in time order`() {
        val snapshot = day(
            intakes = listOf(
                Widget.intake("i-late", "mag", Widget.at(20)),
                Widget.intake("i-early", "asp", Widget.at(9)),
                Widget.intake("i-morning", "mag", Widget.at(8)),
            ),
            medications = listOf(aspirin, magnesium),
        )

        assertEquals(Widget.TODAY, snapshot.date)
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(20, 0)),
            snapshot.items.map { it.time },
        )
        assertEquals(listOf("i-morning", "i-early", "i-late"), snapshot.items.map { it.intakeId })
        assertEquals(
            listOf("magnesium", "aspirin", "magnesium"),
            snapshot.items.map { it.medicationName },
        )
    }

    @Test
    fun `the row tells a due intake from a future one and from a missed one`() {
        val snapshot = day(
            intakes = listOf(
                Widget.intake("i-due", "mag", Widget.at(8)),
                Widget.intake("i-ahead", "asp", Widget.at(20)),
                Widget.intake("i-taken", "mag", Widget.at(8), status = IntakeStatus.TAKEN),
                Widget.intake("i-missed", "asp", Widget.at(9), status = IntakeStatus.MISSED),
            ),
            medications = listOf(magnesium, aspirin),
        )

        // «Сейчас» — время уже наступило (сейчас 10:00), 20:00 — ещё впереди.
        assertEquals(IntakePresentation.DUE_NOW, snapshot.items.single { it.intakeId == "i-due" }.presentation)
        assertEquals(IntakePresentation.UPCOMING, snapshot.items.single { it.intakeId == "i-ahead" }.presentation)
        assertEquals(IntakePresentation.TAKEN, snapshot.items.single { it.intakeId == "i-taken" }.presentation)
        assertEquals(IntakePresentation.MISSED, snapshot.items.single { it.intakeId == "i-missed" }.presentation)
    }

    @Test
    fun `only an expected intake is markable`() {
        val snapshot = day(
            intakes = listOf(
                Widget.intake("i-due", "mag", Widget.at(8)),
                Widget.intake("i-ahead", "asp", Widget.at(20)),
                Widget.intake("i-taken", "mag", Widget.at(8), status = IntakeStatus.TAKEN),
                Widget.intake("i-missed", "asp", Widget.at(9), status = IntakeStatus.MISSED),
            ),
            medications = listOf(magnesium, aspirin),
        )

        assertTrue(snapshot.items.single { it.intakeId == "i-due" }.markable)
        assertTrue(snapshot.items.single { it.intakeId == "i-ahead" }.markable)
        assertFalse(snapshot.items.single { it.intakeId == "i-taken" }.markable)
        assertFalse(snapshot.items.single { it.intakeId == "i-missed" }.markable)
        assertTrue(snapshot.items.single { it.intakeId == "i-taken" }.taken)
    }

    @Test
    fun `the dose is the snapshot of the intake with the unit of the medication`() {
        val stepped = Widget.medication(id = "mag", name = "magnesium", dose = "1", strength = "50")
        val snapshot = day(
            intakes = listOf(Widget.intake("i-stepped", "mag", Widget.at(8), dose = "2")),
            medications = listOf(stepped),
        )

        val dose = snapshot.items.single().dose
        assertEquals(BigDecimal("2"), dose.amount)
        assertEquals("tablet", dose.unit)
        assertEquals(BigDecimal("50"), dose.strength)
        assertEquals("mg", dose.strengthUnit)
    }

    @Test
    fun `an empty day is quiet rather than broken`() {
        val snapshot = day(intakes = emptyList(), medications = listOf(magnesium, aspirin))

        assertTrue(snapshot.empty)
        assertEquals(emptyList<String>(), snapshot.items.map { it.intakeId })
    }

    @Test
    fun `an empty profile is a quiet day too`() {
        // Приём есть, а лекарства у профиля нет: лекарство архивное или чужое. Показывать
        // его приём значило бы показывать чужое (ПР-10, ПР-11).
        val snapshot = day(intakes = listOf(Widget.intake("i-orphan", "mag", Widget.at(8))), medications = emptyList())

        assertTrue(snapshot.empty)
    }

    @Test
    fun `a prn course is not part of the day`() {
        // У виджета нет действия «принять по потребности»: приём по потребности рождается
        // нажатием, а нажатия на этой поверхности не существует (ПР-4, «Вне рамок»).
        val prn = Widget.medication(id = "prn", times = emptyList(), mode = ScheduleMode.PRN)
        val snapshot = day(
            intakes = listOf(Widget.intake("i-prn", "prn", Widget.at(11), status = IntakeStatus.TAKEN)),
            medications = listOf(prn),
        )

        assertTrue(snapshot.empty)
    }

    @Test
    fun `the owner profile carries no indication`() {
        val snapshot = day(intakes = emptyList(), medications = emptyList(), context = Widget.SELF)

        assertEquals(null, snapshot.profile)
    }

    @Test
    fun `a foreign profile is named by name and colour`() {
        val snapshot = day(intakes = emptyList(), medications = emptyList(), context = Widget.FOREIGN)

        assertEquals(WidgetProfileIndication(name = "mother", color = Widget.FOREIGN_COLOUR), snapshot.profile)
    }

    @Test
    fun `yesterday's snapshot is not fresh today`() {
        val yesterday = Widget.TODAY.minusDays(1)
        val snapshot = day(
            date = yesterday,
            intakes = listOf(Widget.intake("i-old", "mag", yesterday.atTime(8, 0))),
            medications = listOf(magnesium),
        )

        assertTrue(snapshot.isFresh(yesterday))
        assertFalse(snapshot.isFresh(Widget.TODAY))
    }

    @Test
    fun `the week ends today and runs seven days back`() {
        assertEquals(
            (0..6).map { Widget.TODAY.minusDays((6 - it).toLong()) },
            BuildWidgetSnapshots.weekOf(Widget.TODAY),
        )
        assertEquals(Widget.TODAY, BuildWidgetSnapshots.weekOf(Widget.TODAY).last())
    }

    @Test
    fun `there is neither a day after today nor an eighth day in the window`() {
        val week = week(intakes = emptyList(), medications = emptyList())

        assertEquals(7, week.days.size)
        assertEquals(Widget.TODAY, week.days.last().date)
        assertTrue(week.days.none { it.date.isAfter(Widget.TODAY) })
        assertTrue(week.days.none { it.date == Widget.TODAY.minusDays(7) })
    }

    @Test
    fun `a completed day carries the state and the numbers of the norm`() {
        val week = week(
            intakes = listOf(
                Widget.intake("i-1", "mag", Widget.TODAY.minusDays(3).atTime(8, 0), status = IntakeStatus.TAKEN),
                Widget.intake("i-2", "mag", Widget.TODAY.minusDays(3).atTime(20, 0), status = IntakeStatus.TAKEN),
                Widget.intake("i-3", "mag", Widget.TODAY.minusDays(2).atTime(8, 0), status = IntakeStatus.TAKEN),
                Widget.intake("i-4", "mag", Widget.TODAY.minusDays(2).atTime(20, 0), status = IntakeStatus.MISSED),
                Widget.intake("i-5", "mag", Widget.TODAY.minusDays(1).atTime(8, 0), status = IntakeStatus.MISSED),
            ),
            medications = listOf(magnesium),
        )

        val allTaken = week.days.single { it.date == Widget.TODAY.minusDays(3) }
        assertEquals(DayState.ALL_TAKEN, allTaken.state)
        assertEquals(2, allTaken.taken)
        assertEquals(2, allTaken.planned)

        val partial = week.days.single { it.date == Widget.TODAY.minusDays(2) }
        assertEquals(DayState.PARTIAL, partial.state)
        assertEquals(1, partial.taken)

        val missed = week.days.single { it.date == Widget.TODAY.minusDays(1) }
        assertEquals(DayState.MISSED, missed.state)
        assertEquals(0, missed.taken)

        val none = week.days.single { it.date == Widget.TODAY.minusDays(6) }
        assertEquals(DayState.NO_INTAKES, none.state)
        assertEquals(0, none.planned)
    }

    @Test
    fun `today is ahead and does not join the counter numerator`() {
        val week = week(
            intakes = listOf(
                Widget.intake("i-1", "mag", Widget.TODAY.minusDays(1).atTime(8, 0), status = IntakeStatus.TAKEN),
                Widget.intake("i-2", "mag", Widget.TODAY.atTime(8, 0), status = IntakeStatus.TAKEN),
            ),
            medications = listOf(magnesium),
        )

        assertEquals(DayState.AHEAD, week.days.last().state)
        // Числитель — только завершившиеся сутки (ЭКР-6): сегодняшняя отметка в него не входит.
        assertEquals(WeekCounter.Fraction(taken = 1, planned = 2), week.counter)
    }

    @Test
    fun `a week without completed days with intakes has no fraction`() {
        val week = week(intakes = emptyList(), medications = listOf(magnesium))

        assertEquals(WeekCounter.NoRecords, week.counter)
    }

    @Test
    fun `a day before the history begins is told from a day without intakes`() {
        val week = week(
            intakes = emptyList(),
            medications = emptyList(),
            historyStartsAt = Widget.TODAY.minusDays(2),
        )

        assertEquals(DayState.BEFORE_HISTORY, week.days.first().state)
        assertEquals(DayState.NO_INTAKES, week.days.last().state)
        assertEquals(WeekCounter.NoRecords, week.counter)
    }

    @Test
    fun `the week is a plan of scheduled courses only`() {
        val prn = Widget.medication(id = "prn", times = emptyList(), mode = ScheduleMode.PRN)
        val week = week(
            intakes = listOf(Widget.intake("i-prn", "prn", Widget.at(11), status = IntakeStatus.TAKEN)),
            medications = listOf(prn),
        )

        assertEquals(0, week.days.sumOf { it.planned })
        assertEquals(0, week.days.sumOf { it.taken })
    }

    @Test
    fun `a course outside the profile is not counted in the week`() {
        val week = week(intakes = listOf(Widget.intake("i-orphan", "mag", Widget.at(8))), medications = emptyList())

        assertEquals(0, week.days.sumOf { it.planned })
    }

    @Test
    fun `only the chosen medication of today is in the medication snapshot`() {
        val snapshot = BuildWidgetSnapshots.medication(
            date = Widget.TODAY,
            medication = magnesium,
            intakes = listOf(
                Widget.intake("i-mag", "mag", Widget.at(8)),
                Widget.intake("i-asp", "asp", Widget.at(9)),
                Widget.intake("i-old", "mag", Widget.TODAY.minusDays(1).atTime(8, 0)),
            ),
            now = Widget.at(10),
            zone = Widget.ZONE,
            context = Widget.SELF,
        )

        assertEquals("mag", snapshot.medicationId)
        assertEquals("magnesium", snapshot.name)
        assertEquals(listOf("i-mag"), snapshot.items.map { it.intakeId })
        assertEquals(null, snapshot.profile)
    }

    @Test
    fun `the medication intakes go by time and carry the intake dose`() {
        val snapshot = BuildWidgetSnapshots.medication(
            date = Widget.TODAY,
            medication = magnesium,
            intakes = listOf(
                Widget.intake("i-evening", "mag", Widget.at(20)),
                Widget.intake("i-morning", "mag", Widget.at(8), dose = "2"),
            ),
            now = Widget.at(10),
            zone = Widget.ZONE,
            context = Widget.FOREIGN,
        )

        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), snapshot.items.map { it.time })
        assertEquals(BigDecimal("2"), snapshot.items.first().dose.amount)
        assertEquals(IntakePresentation.DUE_NOW, snapshot.items.first().presentation)
        assertEquals(IntakePresentation.UPCOMING, snapshot.items.last().presentation)
        assertEquals(WidgetProfileIndication("mother", Widget.FOREIGN_COLOUR), snapshot.profile)
    }

    private fun day(
        intakes: List<Intake>,
        medications: List<Medication>,
        date: LocalDate = Widget.TODAY,
        context: ProfileContext = Widget.SELF,
    ): WidgetDaySnapshot = BuildWidgetSnapshots.day(
        date = date,
        intakes = intakes,
        activeMedications = medications,
        now = Widget.at(10),
        zone = Widget.ZONE,
        context = context,
    )

    private fun week(
        intakes: List<Intake>,
        medications: List<Medication>,
        context: ProfileContext = Widget.SELF,
        historyStartsAt: LocalDate? = Widget.TODAY.minusMonths(1),
    ): WidgetWeekSnapshot = BuildWidgetSnapshots.week(
        today = Widget.TODAY,
        intakes = intakes,
        medications = medications,
        historyStartsAt = historyStartsAt,
        context = context,
    )
}

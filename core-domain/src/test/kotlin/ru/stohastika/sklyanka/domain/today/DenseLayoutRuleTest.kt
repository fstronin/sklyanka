package ru.stohastika.sklyanka.domain.today

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalDateTime

/**
 * Свёртка плотного дня (ОВ-33, `MainDense.dc.html`).
 *
 * Расклад сценария приёмки: тридцать приёмов у двенадцати лекарств, утро с 07:00 до
 * 11:00 отмечено целиком, вечер с 18:00 до 23:00 ещё не наступил, день развёрнут.
 */
class DenseLayoutRuleTest {

    private val rule = DenseLayoutRule()
    private val now: LocalDateTime = Today.at(14, 5)

    @Test
    fun `at the threshold the day stays plain`() {
        val layout = rule(groupsOf(intakes(count = 10, fromHour = 8, marked = 0, step = 30)), now)

        assertInstanceOf(TodayLayout.Plain::class.java, layout)
    }

    @Test
    fun `above the threshold the day collapses into sections`() {
        val layout = rule(groupsOf(intakes(count = 11, fromHour = 7, marked = 0, step = 25)), now)

        assertInstanceOf(TodayLayout.Dense::class.java, layout)
    }

    @Test
    fun `an empty day is plain, not dense`() {
        assertInstanceOf(TodayLayout.Plain::class.java, rule(emptyList(), now))
    }

    @Test
    fun `a past section with everything marked reads as all marked`() {
        val morning = section(PartOfDay.MORNING)

        assertEquals(SectionState.Collapsed(SectionSummary.ALL_MARKED), morning.state)
        assertEquals(8, morning.total)
        assertEquals(8, morning.marked)
    }

    @Test
    fun `a future section reads as ahead`() {
        val evening = section(PartOfDay.EVENING)

        assertEquals(SectionState.Collapsed(SectionSummary.AHEAD), evening.state)
        assertEquals(13, evening.total)
    }

    @Test
    fun `the current section stays expanded and carries a counter`() {
        val day = section(PartOfDay.DAY)

        assertTrue(day.state.isExpanded)
        assertEquals(3, day.marked)
        assertEquals(9, day.total)
    }

    @Test
    fun `a past section with unmarked intakes is neither all marked nor ahead`() {
        val items = intakes(count = 8, fromHour = 7, marked = 5, step = 30) +
            intakes(count = 9, fromHour = 12, marked = 0, step = 30)

        val layout = rule(groupsOf(items), now) as TodayLayout.Dense
        val morning = layout.sections.single { it.partOfDay == PartOfDay.MORNING }

        assertEquals(SectionState.Collapsed(SectionSummary.PARTIAL), morning.state)
    }

    /** Секция плотного дня из расклада сценария приёмки. */
    private fun section(partOfDay: PartOfDay): DaySection {
        val layout = rule(groupsOf(denseDay()), now) as TodayLayout.Dense
        return layout.sections.single { it.partOfDay == partOfDay }
    }

    private fun denseDay(): List<IntakeItem> = intakes(count = 8, fromHour = 7, marked = 8, step = 30) +
        intakes(count = 9, fromHour = 12, marked = 3, step = 30) +
        intakes(count = 13, fromHour = 18, marked = 0, step = 25)

    private fun groupsOf(items: List<IntakeItem>): List<TimeGroup> = GroupIntakesByTime(items, now)

    /**
     * Приёмы подряд с заданным шагом от заданного часа.
     *
     * Шаг задаётся вызовом, а не константой: тринадцать вечерних приёмов обязаны
     * уместиться между 18:00 и 23:00, как в макете, а восемь утренних — между 07:00
     * и 11:00.
     */
    private fun intakes(count: Int, fromHour: Int, marked: Int, step: Long): List<IntakeItem> {
        val medication = Today.medication("m-$fromHour")
        return (0 until count).map { index ->
            val at = Today.at(fromHour, 0).plusMinutes(step * index)
            val status = if (index < marked) IntakeStatus.TAKEN else IntakeStatus.PENDING
            Today.item(
                intake = Today.intake("i-$fromHour-$index", medication.id, at, status),
                medication = medication,
                presentation = if (status == IntakeStatus.TAKEN) IntakePresentation.TAKEN else presentationAt(at),
            )
        }
    }

    private fun presentationAt(at: LocalDateTime): IntakePresentation =
        if (at.isAfter(now)) IntakePresentation.UPCOMING else IntakePresentation.DUE_NOW
}

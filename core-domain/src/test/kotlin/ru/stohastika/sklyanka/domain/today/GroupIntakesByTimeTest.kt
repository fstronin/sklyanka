package ru.stohastika.sklyanka.domain.today

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** Группировка приёмов суток по времени и метка «Сейчас» (`Main.dc.html`). */
class GroupIntakesByTimeTest {

    private val eutirox = Today.medication("m-eutirox")
    private val vitaminD = Today.medication("m-vitamin-d")
    private val magnesium = Today.medication("m-magnesium")
    private val losartan = Today.medication("m-losartan")

    @Test
    fun `intakes of the same time form one group`() {
        val groups = GroupIntakesByTime(morningPair(), now = Today.at(7, 30))

        assertEquals(1, groups.size)
        assertEquals(LocalTime.of(8, 0), groups.single().time)
        assertEquals(2, groups.single().items.size)
    }

    @Test
    fun `groups are ordered by time and labelled by part of day`() {
        val groups = GroupIntakesByTime(wholeDay(), now = Today.at(7, 30))

        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(21, 0)), groups.map { it.time })
        assertEquals(PartOfDay.MORNING, groups[0].partOfDay)
        assertEquals(PartOfDay.DAY, groups[1].partOfDay)
        assertEquals(PartOfDay.EVENING, groups[2].partOfDay)
    }

    @Test
    fun `before the first intake no group is current`() {
        val groups = GroupIntakesByTime(wholeDay(), now = Today.at(7, 30))

        assertTrue(groups.none { it.isNow })
    }

    @Test
    fun `the latest passed group is the current one`() {
        val groups = GroupIntakesByTime(wholeDay(), now = Today.at(14, 5))

        assertEquals(LocalTime.of(14, 0), groups.single { it.isNow }.time)
        assertFalse(groups.first { it.time == LocalTime.of(8, 0) }.isNow)
    }

    @Test
    fun `late in the evening the last group is the current one`() {
        val groups = GroupIntakesByTime(wholeDay(), now = Today.at(23, 50))

        assertEquals(LocalTime.of(21, 0), groups.single { it.isNow }.time)
    }

    @Test
    fun `a night intake stays in its own day and carries the current mark`() {
        val melatonin = Today.medication("m-melatonin", times = listOf(LocalTime.of(23, 30)))
        val items = listOf(
            Today.item(Today.intake("i-night", melatonin.id, Today.at(23, 30)), melatonin),
        )

        val groups = GroupIntakesByTime(items, now = Today.at(23, 45))

        assertEquals(LocalTime.of(23, 30), groups.single().time)
        assertTrue(groups.single().isNow)
        assertEquals(PartOfDay.EVENING, groups.single().partOfDay)
    }

    private fun morningPair() = listOf(
        Today.item(Today.intake("i-1", eutirox.id, Today.at(8, 0)), eutirox),
        Today.item(Today.intake("i-2", vitaminD.id, Today.at(8, 0)), vitaminD),
    )

    private fun wholeDay() = morningPair() + listOf(
        Today.item(Today.intake("i-3", magnesium.id, Today.at(14, 0)), magnesium),
        Today.item(Today.intake("i-4", losartan.id, Today.at(21, 0)), losartan),
    )
}

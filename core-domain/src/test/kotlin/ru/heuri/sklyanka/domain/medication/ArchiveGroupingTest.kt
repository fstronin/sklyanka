package ru.heuri.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.Medication
import java.time.LocalDate

/** Две группы архива и подпись как следствие причины архивации (ЭКР-8). */
class ArchiveGroupingTest {

    @Test
    fun `the reason of archiving decides the group`() {
        val content = ArchiveGrouping.of(
            listOf(finished("a"), manual("b"), finished("c")),
            emptyList(),
            Cards.ZONE,
        )

        assertEquals(listOf("a", "c"), content.finished.map { it.medicationId })
        assertEquals(listOf("b"), content.manual.map { it.medicationId })
    }

    @Test
    fun `the counter of the archive adds both groups up`() {
        val content = ArchiveGrouping.of(
            (1..7).map { finished("finished-$it") } + (1..5).map { manual("manual-$it") },
            emptyList(),
            Cards.ZONE,
        )

        assertEquals(12, content.total)
    }

    @Test
    fun `a finished course is captioned with the outcome of the course`() {
        val medication = finished("a", start = AUGUST_15, end = AUGUST_21, archivedOn = AUGUST_22)
        val intakes = (0..6).flatMap { day ->
            Cards.day(AUGUST_15.plusDays(day.toLong()), taken = if (day == 6) 1 else 2, medicationId = "a")
        }

        val content = ArchiveGrouping.of(listOf(medication), intakes, Cards.ZONE)

        assertEquals(ArchiveCaption.Outcome(days = 7, taken = 13, planned = 14), content.finished.single().caption)
    }

    @Test
    fun `a manually removed course is captioned with the date it went to the archive`() {
        val content = ArchiveGrouping.of(
            listOf(manual("b", archivedOn = LocalDate.of(2026, 8, 3))),
            emptyList(),
            Cards.ZONE,
        )

        assertEquals(ArchiveCaption.ArchivedOn(LocalDate.of(2026, 8, 3)), content.manual.single().caption)
    }

    @Test
    fun `a manually removed course never gets an adherence caption`() {
        val intakes = Cards.day(AUGUST_15, taken = 1, medicationId = "b")

        val content = ArchiveGrouping.of(listOf(manual("b", archivedOn = AUGUST_22)), intakes, Cards.ZONE)

        // Приверженность у курса, прерванного досрочно, — неверный вывод: сколько приёмов
        // планировалось, неизвестно (ЭКР-8). Типом это невозможно, а не оставлено на внимательность.
        assertTrue(content.manual.single().caption is ArchiveCaption.ArchivedOn)
    }

    @Test
    fun `an empty group is an empty list and an empty archive knows it is empty`() {
        val onlyFinished = ArchiveGrouping.of(listOf(finished("a")), emptyList(), Cards.ZONE)
        val nothing = ArchiveGrouping.of(emptyList(), emptyList(), Cards.ZONE)

        assertTrue(onlyFinished.manual.isEmpty())
        assertTrue(nothing.isEmpty)
        assertEquals(0, nothing.total)
    }

    @Test
    fun `a medication without the moment of archiving never reaches the archive`() {
        val content = ArchiveGrouping.of(listOf(Cards.scheduled(id = "active")), emptyList(), Cards.ZONE)

        assertEquals(0, content.total)
    }

    @Test
    fun `the title and the release form travel to the card`() {
        val content = ArchiveGrouping.of(listOf(manual("b", name = "med-b")), emptyList(), Cards.ZONE)

        val card = content.manual.single()
        assertEquals("med-b", card.title)
        assertEquals(Cards.scheduled().form, card.form)
    }

    private fun finished(
        id: String,
        name: String = "med-$id",
        start: LocalDate = AUGUST_15,
        end: LocalDate? = AUGUST_21,
        archivedOn: LocalDate = AUGUST_22,
    ): Medication = archived(id, name, start, end, archivedOn, ArchiveReason.COURSE_FINISHED)

    private fun manual(id: String, name: String = "med-$id", archivedOn: LocalDate = AUGUST_22): Medication =
        archived(id, name, AUGUST_15, null, archivedOn, ArchiveReason.MANUAL)

    @Suppress("LongParameterList")
    private fun archived(
        id: String,
        name: String,
        start: LocalDate,
        end: LocalDate?,
        archivedOn: LocalDate,
        reason: ArchiveReason,
    ): Medication = Cards.scheduled(
        id = id,
        name = name,
        startDate = start,
        endDate = end,
        archivedAt = Cards.instant(archivedOn.atTime(Cards.MORNING)),
        archiveReason = reason,
    )

    private companion object {
        val AUGUST_15: LocalDate = LocalDate.of(2026, 8, 15)
        val AUGUST_21: LocalDate = LocalDate.of(2026, 8, 21)
        val AUGUST_22: LocalDate = LocalDate.of(2026, 8, 22)
    }
}

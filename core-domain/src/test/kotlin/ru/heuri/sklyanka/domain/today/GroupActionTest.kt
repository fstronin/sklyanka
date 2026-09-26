package ru.heuri.sklyanka.domain.today

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.LocalTime

/**
 * Групповая отметка одновременных приёмов (М-08-1, решение ОВ-32).
 *
 * Подпись кнопки склоняется по числу приёмов: «Отметить оба», «Отметить все три»,
 * «Отметить все N». Формы, а не строки: текст собирается из ресурсов (НФТ-44).
 */
class GroupActionTest {

    @ParameterizedTest
    @CsvSource("2,BOTH", "3,ALL_THREE", "4,ALL_N", "7,ALL_N")
    fun `the label form follows the number of intakes`(count: Int, expected: GroupLabelForm) {
        assertEquals(expected, GroupLabelForm.of(count))
    }

    @Test
    fun `a group of one gets no group button`() {
        assertNull(GroupAction.of(group(pending = 1, marked = 0)))
    }

    @Test
    fun `a group where everything is marked gets no group button`() {
        assertNull(GroupAction.of(group(pending = 0, marked = 3)))
    }

    @Test
    fun `only the pending intakes enter the group operation`() {
        val action = GroupAction.of(group(pending = 2, marked = 1))

        assertEquals(2, action?.count)
        assertEquals(GroupLabelForm.BOTH, action?.labelForm)
    }

    @Test
    fun `three pending intakes give the all three form`() {
        assertEquals(GroupLabelForm.ALL_THREE, GroupAction.of(group(pending = 3, marked = 0))?.labelForm)
    }

    private fun group(pending: Int, marked: Int): TimeGroup {
        val medication = Today.medication("m-group")
        val at = Today.at(14, 0)
        val items = (0 until pending).map { index ->
            Today.item(Today.intake("p-$index", medication.id, at), medication, IntakePresentation.DUE_NOW)
        } + (0 until marked).map { index ->
            Today.item(
                intake = Today.intake("t-$index", medication.id, at, IntakeStatus.TAKEN),
                medication = medication,
                presentation = IntakePresentation.TAKEN,
            )
        }
        return TimeGroup(
            time = LocalTime.of(14, 0),
            partOfDay = PartOfDay.DAY,
            isNow = true,
            items = items,
        )
    }
}

package ru.stohastika.sklyanka.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.UndoableMarkStore
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Возврат на «Сегодня» при живом окне отмены (SKL-43, `TDD.md §4.2`).
 *
 * Проверяется обе стороны правила: живое окно приводит человека на «Сегодня» с любого
 * экрана, а истёкшее или отсутствующее — не переключает ничего. Вторая сторона не менее
 * важна первой: переключение «на всякий случай» уводило бы человека с его экрана без
 * всякого окна отмены.
 */
class UndoReturnTest {

    private val moment: Instant = Instant.parse("2026-09-25T11:01:00Z")
    private val clock = FixedTimeSource(moment, ZoneId.of("Europe/Moscow"))
    private val undoMarks = UndoableMarkStore(clock)
    private val navigation = RootNavigation()

    private fun publishWindow() {
        undoMarks.publish(
            intakeIds = listOf("intake-1"),
            result = MarkResult.TAKEN,
            medicationNames = listOf("med-1"),
        )
    }

    @Test
    fun `a live window brings the person from a medication card to today`() {
        navigation.openMeds(MedsDestination.Card("med-1"))
        publishWindow()

        assertTrue(UndoReturn.onAppeared(undoMarks, navigation))
        assertTrue(navigation.showsToday)
    }

    @Test
    fun `a live window brings the person from a settings second level to today`() {
        navigation.openTab(RootTab.SETTINGS)
        navigation.openSection(SettingsDestination.HISTORY)
        publishWindow()

        assertTrue(UndoReturn.onAppeared(undoMarks, navigation))
        assertTrue(navigation.showsToday)
    }

    @Test
    fun `an expired window switches nothing`() {
        navigation.openMeds(MedsDestination.List)
        publishWindow()
        clock.advanceTo(moment.plus(UndoableMarkStore.WINDOW))

        assertFalse(UndoReturn.onAppeared(undoMarks, navigation))
        assertEquals(MedsDestination.List, navigation.meds)
        assertFalse(navigation.showsToday)
    }

    @Test
    fun `no window at all switches nothing`() {
        navigation.openTab(RootTab.SETTINGS)

        assertFalse(UndoReturn.onAppeared(undoMarks, navigation))
        assertEquals(RootTab.SETTINGS, navigation.tab)
    }

    @Test
    fun `a person already on today stays there`() {
        publishWindow()

        assertTrue(UndoReturn.onAppeared(undoMarks, navigation))
        assertTrue(navigation.showsToday)
    }
}

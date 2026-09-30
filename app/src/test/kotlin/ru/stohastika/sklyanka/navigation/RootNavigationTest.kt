package ru.stohastika.sklyanka.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Состояние корня приложения (SKL-43, `TDD.md §4.1`).
 *
 * Проверяется полный возврат на «Сегодня»: недостаточно выбрать вкладку, не закрыв
 * открытый экран второго уровня, — человек увидел бы раздел поверх или вовсе не «Сегодня».
 * Эти состояния сейчас живут в композиции `MainActivity`, и ошибка в них была бы видна
 * только на устройстве.
 */
class RootNavigationTest {

    @Test
    fun `today is what the root shows before any transition`() {
        assertTrue(RootNavigation().showsToday)
    }

    @Test
    fun `an open medication card wins over the tab`() {
        val navigation = RootNavigation()

        navigation.openMeds(MedsDestination.Card("med-1"))

        assertFalse(navigation.showsToday)
        assertEquals(MedsDestination.Card("med-1"), navigation.meds)
    }

    @Test
    fun `returning to today closes the medications section`() {
        val navigation = RootNavigation()
        navigation.openMeds(MedsDestination.NewMedication)

        navigation.openToday()

        assertTrue(navigation.showsToday)
        assertNull(navigation.meds)
    }

    @Test
    fun `returning to today closes a settings second level and selects the tab`() {
        val navigation = RootNavigation()
        navigation.openTab(RootTab.SETTINGS)
        navigation.openSection(SettingsDestination.TRANSFER)

        navigation.openToday()

        assertEquals(RootTab.TODAY, navigation.tab)
        assertNull(navigation.section)
        assertTrue(navigation.showsToday)
    }

    @Test
    fun `returning to today closes the history opened from today`() {
        val navigation = RootNavigation()
        navigation.openHistory()

        navigation.openToday()

        assertFalse(navigation.historyFromToday)
        assertTrue(navigation.showsToday)
    }
}

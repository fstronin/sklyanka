package ru.stohastika.sklyanka.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.ui.motion.LayerMotion

class RootLayerMotionTest {

    @Test
    fun `the root starts on today with an adjacent motion`() {
        val navigation = RootNavigation()

        assertEquals(RootLayer.Today, navigation.layer())
        assertEquals(LayerMotion.Crossfade, navigation.rootMotion)
    }

    @Test
    fun `a medication card opened from today goes forward`() {
        val navigation = RootNavigation()

        navigation.openMeds(MedsDestination.Card("med-1"))

        assertEquals(RootLayer.Meds(MedsDestination.Card("med-1")), navigation.layer())
        assertEquals(LayerMotion.Forward, navigation.rootMotion)
    }

    @Test
    fun `the medication list is a tab and moves as an adjacent layer`() {
        val navigation = RootNavigation()

        navigation.openMeds(MedsDestination.List)

        assertEquals(RootLayer.Meds(MedsDestination.List), navigation.layer())
        assertEquals(LayerMotion.Crossfade, navigation.rootMotion)
    }

    @Test
    fun `the shortcut to the new medication form goes forward`() {
        val navigation = RootNavigation()

        navigation.openFromLaunch(AddMedicationLaunch.ACTION)

        assertEquals(RootLayer.Meds(MedsDestination.NewMedication), navigation.layer())
        assertEquals(LayerMotion.Forward, navigation.rootMotion)
    }

    @Test
    fun `a settings second level goes forward and comes back`() {
        val navigation = RootNavigation()
        navigation.openTab(RootTab.SETTINGS)

        navigation.openSection(SettingsDestination.TRANSFER)

        assertEquals(RootLayer.SettingsScreen(SettingsDestination.TRANSFER), navigation.layer())
        assertEquals(LayerMotion.Forward, navigation.rootMotion)

        navigation.leaveSection()

        assertEquals(RootLayer.Settings, navigation.layer())
        assertEquals(LayerMotion.Back, navigation.rootMotion)
    }

    @Test
    fun `leaving the medications section by back moves backwards`() {
        val navigation = RootNavigation()
        navigation.openMeds(MedsDestination.Card("med-1"))

        navigation.leaveMeds()

        assertEquals(RootLayer.Today, navigation.layer())
        assertEquals(LayerMotion.Back, navigation.rootMotion)
    }

    @Test
    fun `switching the tab moves as an adjacent layer`() {
        val navigation = RootNavigation()

        navigation.openTab(RootTab.SETTINGS)

        assertEquals(RootLayer.Settings, navigation.layer())
        assertEquals(LayerMotion.Crossfade, navigation.rootMotion)
    }

    @Test
    fun `history from today goes forward and returns backwards`() {
        val navigation = RootNavigation()

        navigation.openHistory()

        assertEquals(RootLayer.HistoryFromToday, navigation.layer())
        assertEquals(LayerMotion.Forward, navigation.rootMotion)

        navigation.leaveHistory()

        assertEquals(RootLayer.Today, navigation.layer())
        assertEquals(LayerMotion.Back, navigation.rootMotion)
    }

    @Test
    fun `returning to today closes every layer at once`() {
        val navigation = RootNavigation()
        navigation.openTab(RootTab.SETTINGS)
        navigation.openSection(SettingsDestination.DELETE_PROFILE)

        navigation.openToday()

        assertEquals(RootLayer.Today, navigation.layer())
        assertEquals(LayerMotion.Crossfade, navigation.rootMotion)
    }

    @Test
    fun `history from today wins over the settings second level`() {
        val navigation = RootNavigation()
        navigation.openTab(RootTab.SETTINGS)
        navigation.openSection(SettingsDestination.HISTORY)
        navigation.openHistory()

        assertEquals(RootLayer.HistoryFromToday, navigation.layer())
    }
}

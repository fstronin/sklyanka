package ru.stohastika.sklyanka.navigation

sealed interface RootLayer {

    data object Today : RootLayer

    data object Settings : RootLayer

    data object HistoryFromToday : RootLayer

    data class SettingsScreen(val destination: SettingsDestination) : RootLayer

    data class Meds(val entry: MedsDestination) : RootLayer
}

fun RootNavigation.layer(): RootLayer = when {
    historyFromToday -> RootLayer.HistoryFromToday
    section != null -> RootLayer.SettingsScreen(requireNotNull(section))
    meds != null -> RootLayer.Meds(requireNotNull(meds))
    tab == RootTab.SETTINGS -> RootLayer.Settings
    else -> RootLayer.Today
}

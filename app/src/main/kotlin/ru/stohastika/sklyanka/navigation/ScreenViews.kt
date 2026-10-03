package ru.stohastika.sklyanka.navigation

import ru.stohastika.sklyanka.domain.analytics.ScreenName

/**
 * Закрытое отображение экранов приложения на каталог ПМ-26 (SKL-82).
 *
 * Событие `screen_view` отправляется из одной точки — корня приложения и экранов-активностей,
 * — но состояние навигации описывается несколькими типами: слой корня, второй уровень
 * настроек, стек раздела «Лекарства». Таблица соответствий живёт здесь, чтобы значения
 * каталога не разъезжались по вызовам.
 */
object ScreenViews {

    fun of(layer: RootLayer): ScreenName = when (layer) {
        RootLayer.Today -> ScreenName.TODAY
        RootLayer.Settings -> ScreenName.SETTINGS
        RootLayer.HistoryFromToday -> ScreenName.HISTORY
        is RootLayer.SettingsScreen -> of(layer.destination)
        is RootLayer.Meds -> of(layer.entry)
    }

    fun of(destination: SettingsDestination): ScreenName = when (destination) {
        SettingsDestination.HISTORY -> ScreenName.HISTORY
        SettingsDestination.TRANSFER -> ScreenName.DATA_TRANSFER
        SettingsDestination.DELETE_PROFILE -> ScreenName.PROFILES
    }

    fun of(destination: MedsDestination): ScreenName = when (destination) {
        MedsDestination.List -> ScreenName.MEDICATIONS
        MedsDestination.Archive -> ScreenName.ARCHIVE
        is MedsDestination.Card -> ScreenName.MEDICATION_CARD
        is MedsDestination.Edit -> ScreenName.MEDICATION_FORM
        MedsDestination.NewMedication -> ScreenName.MEDICATION_FORM
    }
}

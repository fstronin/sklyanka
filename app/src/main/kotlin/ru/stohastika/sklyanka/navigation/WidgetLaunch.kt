package ru.stohastika.sklyanka.navigation

import ru.stohastika.sklyanka.feature.widgets.open.WidgetPlace

/**
 * Действия открытия приложения с плиты виджета (SKL-118, §5.11).
 *
 * Единственное место, где записаны имена действий: их собирает [WidgetOpenTargetImpl],
 * а разбирает [RootNavigation.openFromLaunch] — и разъехаться эти половины не могут,
 * потому что обе смотрят сюда.
 *
 * Нажатие на плиту вне кнопок отметки открывает приложение там, где живёт содержимое:
 * «Сегодня» — на «Сегодня», «Неделя» — в «Историю», «По лекарству» — в карточку
 * своего лекарства. Приложение — единственное место, где видны день целиком и всё,
 * что сверх отметки.
 */
object WidgetLaunch {

    const val OPEN_TODAY: String = "ru.stohastika.sklyanka.action.WIDGET_TODAY"

    const val OPEN_HISTORY: String = "ru.stohastika.sklyanka.action.WIDGET_HISTORY"

    const val OPEN_MEDICATION: String = "ru.stohastika.sklyanka.action.WIDGET_MEDICATION"

    /** Идентификатор лекарства у действия [OPEN_MEDICATION]: у остальных мест его нет. */
    const val EXTRA_MEDICATION_ID: String = "ru.stohastika.sklyanka.extra.WIDGET_MEDICATION_ID"

    /** Какое действие соответствует месту на экране. */
    fun actionFor(place: WidgetPlace): String = when (place) {
        WidgetPlace.TODAY -> OPEN_TODAY
        WidgetPlace.HISTORY -> OPEN_HISTORY
        WidgetPlace.MEDICATION -> OPEN_MEDICATION
    }
}

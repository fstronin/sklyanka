package ru.stohastika.sklyanka.feature.widgets.open

import android.content.Context
import android.content.Intent

/**
 * Куда ведёт плита виджета (§5.11).
 *
 * Нажатие на плиту вне кнопок отметки открывает приложение там, где живёт содержимое:
 * «Сегодня» — на «Сегодня», «Неделя» — в «Историю», «По лекарству» — в карточку своего
 * лекарства. Приложение — единственное место, где видны день целиком и всё, что сверх
 * отметки, поэтому цель у поверхности есть.
 */
enum class WidgetPlace { TODAY, HISTORY, MEDICATION }

/**
 * Порт открытия приложения из виджета.
 *
 * Реализация живёт в `app`: только он знает свои экраны и правила входа в них (ADR-0007).
 * Модуль виджетов зовёт порт и не знает ни имён активностей, ни состава навигации —
 * иначе разметка домашнего экрана держала бы адрес экрана в себе, и переезд экрана
 * ломал бы плиту.
 */
interface WidgetOpenTarget {

    /**
     * @param medicationId лекарство для [WidgetPlace.MEDICATION]; у остальных мест пусто
     */
    fun intentFor(context: Context, place: WidgetPlace, medicationId: String? = null): Intent
}

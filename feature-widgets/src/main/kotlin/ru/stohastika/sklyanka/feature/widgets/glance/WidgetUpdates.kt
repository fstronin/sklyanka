package ru.stohastika.sklyanka.feature.widgets.glance

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Перерисовка всех плит по событию (SKL-118, `TDD.md §7`).
 *
 * Точки вызова — события, а не таймер-опрос: отметка (из приложения и из виджета),
 * полночь и перевод «ожидается → пропущено» (ПР-1), смена активного профиля, правка
 * лекарства, перенос данных, смена пояса и времени. Опроса сети и фоновых сервисов
 * у виджета нет вовсе (П-6, Р-4).
 *
 * Обновляются все три типа сразу: у виджета нет способа узнать, какие плиты стоят
 * на домашнем экране, а лишняя перерисовка стоит одного запуска сессии и ничего
 * не портит — в отличие от пропущенной, из-за которой плита показывает вчерашний день.
 */
object WidgetUpdates {

    /** Перерисовать все установленные плиты из свежих снимков. */
    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        WeekWidget().updateAll(context)
        MedicationWidget().updateAll(context)
    }
}

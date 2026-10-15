package ru.stohastika.sklyanka.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetRefresher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Перерисовка плит по событиям продукта (SKL-118, `TDD.md §7`).
 *
 * Виджет обязан отставать от продукта ровно до следующего события: смена активного профиля
 * показывает другой день, правка или архивация лекарства — другой состав приёмов, перенос
 * данных — другую базу, отметка и её отмена — другой статус строки. Все они видны одним
 * потоком источников, и второго наблюдателя на каждое событие не заводится: разошлись бы
 * на первой же правке (ПР-24).
 *
 * Перерисовка запрашивается и на первом значении: лишний запуск сессии стоит дешевле, чем
 * пропущенное событие — плита, показавшая вчерашний день, врёт человеку (П-7).
 *
 * Просит перерисовку порт ([WidgetRefresher]), а не статическая сборка: «вызова нет вовсе» —
 * молчаливая регрессия, и её видит только тест, у которого есть шов.
 */
@Singleton
class WidgetRefresh @Inject constructor(private val sources: WidgetSources, private val refresher: WidgetRefresher) {

    /** Начать слежение: живёт столько, сколько живёт процесс. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            sources.changes().collect { refresher.refreshAll() }
        }
    }
}

package ru.stohastika.sklyanka.feature.widgets.glance

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Порт перерисовки плит (SKL-118, `TDD.md §7`).
 *
 * Точки обновления зовут его, а не статическую сборку: иначе «вызова нет вовсе» — молчаливая
 * регрессия, которую ни один тест не видит. Порт даёт точке шов: точка знает только
 * «перерисуй», а чем именно рисуются плиты — дело реализации.
 */
interface WidgetRefresher {

    /** Перерисовать все установленные плиты из свежих снимков. */
    suspend fun refreshAll()
}

/**
 * Реализация порта поверх Glance: тот же [WidgetUpdates], которым живут отметка и приёмники.
 *
 * Контекст — приложения, а не экрана: перерисовку просят фоновая задача, приёмник и
 * наблюдатель процесса, у которых экрана нет.
 */
@Singleton
class GlanceWidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) : WidgetRefresher {

    override suspend fun refreshAll() = WidgetUpdates.updateAll(context)
}

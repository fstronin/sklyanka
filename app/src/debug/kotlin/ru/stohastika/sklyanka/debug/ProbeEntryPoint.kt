package ru.stohastika.sklyanka.debug

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import ru.stohastika.sklyanka.di.ReminderScope
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.platform.reminder.AlarmManagerGateway

/**
 * Доступ пробника к графу зависимостей (SKL-89).
 *
 * Отдельная точка входа, а не расширение продуктовой `ReminderEntryPoint`: пробнику
 * нужны `AlarmManagerGateway` и настройка варианта измерения, которых у продуктовых
 * приёмников нет. Живёт в `src/debug` и в релизный артефакт не попадает.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProbeEntryPoint {

    fun alarmManagerGateway(): AlarmManagerGateway

    fun reminderSettings(): ReminderSettings

    fun timeSource(): TimeSource

    @ReminderScope
    fun reminderScope(): CoroutineScope

    companion object {
        fun of(context: Context): ProbeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ProbeEntryPoint::class.java)
    }
}

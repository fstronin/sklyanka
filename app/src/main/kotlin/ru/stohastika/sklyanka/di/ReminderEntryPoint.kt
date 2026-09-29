package ru.stohastika.sklyanka.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import ru.stohastika.sklyanka.domain.reminder.HandleFiredReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.ShowTestReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.SweepAtDayBoundaryUseCase
import ru.stohastika.sklyanka.reminder.ReminderPlanningTrigger

/**
 * Доступ приёмников к графу зависимостей.
 *
 * Явная точка входа, а не `@AndroidEntryPoint` с внедрением в поля: внедрение в приёмник
 * требует вызова `super.onReceive`, а этот метод у `BroadcastReceiver` абстрактный —
 * в Kotlin такой вызов не компилируется. Ручной доступ к графу решает это без обходных
 * приёмов и заодно делает видимым, что именно нужно приёмнику: его бюджет времени
 * ограничен (НАП-13), и каждая зависимость здесь стоит миллисекунд запуска.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {

    fun handleFiredReminder(): HandleFiredReminderUseCase

    fun showTestReminder(): ShowTestReminderUseCase

    fun reminderPlanning(): ReminderPlanningTrigger

    fun sweepAtDayBoundary(): SweepAtDayBoundaryUseCase

    @ReminderScope
    fun reminderScope(): CoroutineScope

    companion object {
        fun of(context: Context): ReminderEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
    }
}

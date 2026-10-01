package ru.stohastika.sklyanka.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import ru.stohastika.sklyanka.platform.reminder.LockedBootAlarmReplayer
import ru.stohastika.sklyanka.reminder.DirectBootTraceDrain

/**
 * Доступ приёмника Direct Boot к графу зависимостей.
 *
 * Отдельная точка входа, а не расширение [ReminderEntryPoint]: она документирует, что именно
 * допустимо трогать до разблокировки. Восстановитель работает только с DE-снимком и прямыми
 * вызовами `AlarmManager`; перенос следов в журнал (CE) вызывается только в unlocked-ветке,
 * когда журнал уже доступен. Внедрение в поля приёмника невозможно: `onReceive` абстрактный,
 * и `super.onReceive` в Kotlin не компилируется — поэтому ручной доступ к графу.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DirectBootEntryPoint {

    fun lockedBootAlarmReplayer(): LockedBootAlarmReplayer

    fun traceDrain(): DirectBootTraceDrain

    @ReminderScope
    fun reminderScope(): CoroutineScope

    companion object {
        fun of(context: Context): DirectBootEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, DirectBootEntryPoint::class.java)
    }
}

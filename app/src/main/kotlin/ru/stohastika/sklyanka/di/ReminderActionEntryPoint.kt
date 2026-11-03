package ru.stohastika.sklyanka.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.reminder.SnoozeReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings

/**
 * Доступ приёмника действий уведомления к графу зависимостей (RT-4, Р-87 п. 3).
 *
 * Отдельная точка входа, а не расширение [ReminderEntryPoint]: она документирует, что именно
 * нужно отметке из шторки, — те же сценарии отметки и откладывания, что и у карточки
 * напоминания. Внедрение в поля приёмника невозможно: `onReceive` абстрактный, и `super.onReceive`
 * в Kotlin не компилируется (тот же приём, что у [ReminderEntryPoint] и [DirectBootEntryPoint]).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderActionEntryPoint {

    /** Единственная точка отметки приёма — та же, что у карточки и «Сегодня» (ЧАС-24). */
    fun markIntake(): MarkIntakeUseCase

    /** Откладывание напоминания (НАП-30, НАП-31). */
    fun snooze(): SnoozeReminderUseCase

    /** Настройка «Отложить на N минут» — интервал откладывания из шторки тот же, что у карточки. */
    fun reminderSettings(): ReminderSettings

    /**
     * Запись событий действия из шторки (SKL-165).
     *
     * Тот же доменный шов, что у экранов: гейт согласия и очередь живут в нём, а приёмник
     * решает только, какое действие человека записывается.
     */
    fun analytics(): AnalyticsRecorder

    @ReminderScope
    fun reminderScope(): CoroutineScope

    companion object {
        fun of(context: Context): ReminderActionEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ReminderActionEntryPoint::class.java)
    }
}

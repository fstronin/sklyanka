package ru.stohastika.sklyanka.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.await
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.data.room.databaseStateOf
import ru.stohastika.sklyanka.di.ReminderEntryPoint
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetUpdates

/**
 * Системные события, после которых будильников больше нет (НАП-11).
 *
 * Перезагрузка стирает будильники, обновление приложения — снимает, смена пояса и перевод
 * часов ломают соответствие плана реальному времени. Приёмник ничего не решает сам и ничего
 * сам не пересчитывает (НАП-13): он переводит событие в причину и ставит задачу.
 *
 * Постановка задачи дожидается записи в очередь, а не возвращается сразу: запись идёт
 * в фоновом потоке `WorkManager`, и процесс, убитый между вызовом и записью, оставил бы
 * событие без последствий. Дальше работа уже не зависит от жизни процесса — в этом и смысл
 * перехода с работы приёмника на задачу (`ОВ-79`).
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reason = SystemEvents.reasonOf(intent.action) ?: return
        // База закрыта (новее или места нет): перепланировать нечего — план живёт в базе,
        // которой эта сборка работать не может (Р-44, ADR-0012 пп. 3, 7). Человеку
        // называется причина, и ничего не планируется.
        val decision = wakeUpDecision(databaseStateOf(context))
        if (announceBlockedWakeUp(context, decision)) return
        enqueue(context, reason)
    }

    /** Постановка задачи перепланирования: она переживает смерть процесса (НАП-13). */
    private fun enqueue(context: Context, reason: RescheduleReason) {
        // Граф может отсутствовать: в процессе без приложения продукта (инструментальный
        // прогон тестовой сборки) система всё равно доставляет системные broadcast'ы,
        // и приёмник обязан их пережить, а не уронить процесс (SKL-97).
        val graph = runCatching { ReminderEntryPoint.of(context) }.getOrNull() ?: return
        val pending = goAsync()
        graph.reminderScope().launch {
            try {
                ReminderWork.enqueueReschedule(context, reason).await()
                // Смена пояса и перевод часов меняют то, какие сутки сейчас (НФТ-34/37):
                // плита обязана перерисоваться тем же событием, что и план будильников.
                WidgetUpdates.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Системное действие → причина перепланирования (таблица НАП-11).
 *
 * Вынесено из приёмника, чтобы соответствие проверялось тестом на JVM без поднятия
 * `WorkManager` и графа Hilt: забытое действие не ломает ничего сразу — будильники
 * просто не восстановятся после события, и заметить это можно только пропущенным приёмом.
 */
internal object SystemEvents {

    fun reasonOf(action: String?): RescheduleReason? = when (action) {
        Intent.ACTION_BOOT_COMPLETED -> RescheduleReason.BOOT

        // На части прошивок EMUI вместо штатного BOOT_COMPLETED приходит фирменное
        // событие завершения загрузки; подписка на оба — страховка, а не дублирование.
        ACTION_QUICKBOOT_POWERON, ACTION_HTC_QUICKBOOT_POWERON -> RescheduleReason.BOOT

        Intent.ACTION_MY_PACKAGE_REPLACED -> RescheduleReason.PACKAGE_REPLACED
        Intent.ACTION_TIMEZONE_CHANGED -> RescheduleReason.TIMEZONE_CHANGED
        Intent.ACTION_TIME_CHANGED -> RescheduleReason.TIME_CHANGED

        // Выдача права на точные будильники (НАП-16, И-20): до неё системный будильник
        // поставить нельзя, после — план обязан восстановиться сам, без вывода приложения
        // на передний план. Если прошивка событие не доставит, тот же результат даст
        // ближайший запуск (APP_START).
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> RescheduleReason.EXACT_ALARM_GRANTED

        else -> null
    }

    const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
}

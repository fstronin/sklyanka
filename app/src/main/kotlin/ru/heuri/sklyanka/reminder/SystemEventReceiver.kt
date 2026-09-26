package ru.heuri.sklyanka.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.await
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.di.ReminderEntryPoint
import ru.heuri.sklyanka.domain.reminder.RescheduleReason

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
        val reason = reasonOf(intent.action) ?: return
        val graph = ReminderEntryPoint.of(context)
        val pending = goAsync()
        graph.reminderScope().launch {
            try {
                ReminderWork.enqueueReschedule(context, reason).await()
            } finally {
                pending.finish()
            }
        }
    }

    private fun reasonOf(action: String?): RescheduleReason? = when (action) {
        Intent.ACTION_BOOT_COMPLETED -> RescheduleReason.BOOT

        // На части прошивок EMUI вместо штатного BOOT_COMPLETED приходит фирменное
        // событие завершения загрузки; подписка на оба — страховка, а не дублирование.
        ACTION_QUICKBOOT_POWERON, ACTION_HTC_QUICKBOOT_POWERON -> RescheduleReason.BOOT

        Intent.ACTION_MY_PACKAGE_REPLACED -> RescheduleReason.PACKAGE_REPLACED
        Intent.ACTION_TIMEZONE_CHANGED -> RescheduleReason.TIMEZONE_CHANGED
        Intent.ACTION_TIME_CHANGED -> RescheduleReason.TIME_CHANGED
        else -> null
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}

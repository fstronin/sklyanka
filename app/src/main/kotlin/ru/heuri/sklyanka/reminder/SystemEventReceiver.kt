package ru.heuri.sklyanka.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.di.ReminderEntryPoint
import ru.heuri.sklyanka.domain.reminder.RescheduleReason

/**
 * Системные события, после которых будильников больше нет (НАП-11).
 *
 * Перезагрузка стирает будильники, обновление приложения — снимает, смена пояса и перевод
 * часов ломают соответствие плана реальному времени. Приёмник ничего не решает сам: он
 * переводит событие в причину и отдаёт работу планировщику (НАП-13).
 *
 * Приёмник не полагается на то, что приложение запущено: доступ к графу поднимает граф,
 * а граф поднимает базу.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reason = reasonOf(intent.action) ?: return
        val graph = ReminderEntryPoint.of(context)
        val pending = goAsync()
        graph.reminderScope().launch {
            try {
                graph.reminderPlanning().planAll(reason)
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

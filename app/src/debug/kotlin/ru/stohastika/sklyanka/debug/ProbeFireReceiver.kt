package ru.stohastika.sklyanka.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import java.time.Instant

/**
 * Сработавшая проба (SKL-89): строка в logcat и нейтральное уведомление.
 *
 * Срабатыванием считается показ (НАП-2), поэтому приёмник и показывает уведомление,
 * и пишет строку с плановым и фактическим моментом: одно без другого доказательством
 * не является. В журнал напоминаний продукта проба не пишется — иначе измерительные
 * срабатывания попали бы в арифметику гейта НАП-3.
 */
class ProbeFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ProbeProtocol.ACTION_FIRE) return
        val graph = ProbeEntryPoint.of(context)
        val pending = goAsync()
        graph.reminderScope().launch {
            try {
                val index = intent.getIntExtra(ProbeProtocol.EXTRA_INDEX, 0)
                val scheduledAt = Instant.ofEpochMilli(intent.getLongExtra(ProbeProtocol.EXTRA_SCHEDULED_AT, 0))
                val kind = ProbeProtocol.parseStrategy(intent.getStringExtra(ProbeProtocol.EXTRA_STRATEGY))
                    ?: AlarmKind.ALARM_CLOCK
                val firedAt = graph.timeSource().now()
                Log.i(ProbeProtocol.TAG, ProbeProtocol.firedLine(index, scheduledAt, firedAt, kind))
                ProbeNotifications.show(context, index)
            } finally {
                pending.finish()
            }
        }
    }
}

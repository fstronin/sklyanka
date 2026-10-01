package ru.stohastika.sklyanka.debug

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import java.time.Instant

/**
 * Управление измерительным пробником по adb (SKL-89).
 *
 * Действия приходят от `tools/spike/probe.sh` (`am broadcast`): постановка N проб
 * и выбор варианта измерения. Приёмник только переводит команду в вызовы продуктовых
 * объектов: будильник ставит `AlarmManagerGateway`, настройку пишет `ReminderSettings` —
 * собственного пути постановки будильников у пробника нет.
 *
 * Приёмник объявлен `exported` в debug-манифесте: команды приходят из adb-оболочки.
 * В обычной и релизной сборке файла нет вовсе.
 */
class ProbeControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = ProbeEntryPoint.of(context)
        val pending = goAsync()
        graph.reminderScope().launch {
            try {
                when (intent.action) {
                    ProbeProtocol.ACTION_SCHEDULE -> schedule(graph, context, intent)
                    ProbeProtocol.ACTION_SET_STRATEGY -> setStrategy(graph, intent)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun schedule(graph: ProbeEntryPoint, context: Context, intent: Intent) {
        val minutes = intent.getIntArrayExtra(ProbeProtocol.EXTRA_MINUTES)?.toList().orEmpty()
        if (minutes.isEmpty() || minutes.any { it <= 0 }) {
            Log.w(ProbeProtocol.TAG, "probe schedule ignored: minutes must be positive")
            return
        }
        cancelPrevious(context)
        val placements = ProbePlan.place(
            gateway = graph.alarmManagerGateway(),
            now = graph.timeSource().now(),
            minutes = minutes,
            pendingFor = { index, at, kind -> probeIntent(context, index, at, kind) },
        )
        placements.forEach { placement ->
            Log.i(
                ProbeProtocol.TAG,
                ProbeProtocol.scheduledLine(placement.index, placement.at, placement.kind, placement.placed),
            )
        }
    }

    private suspend fun setStrategy(graph: ProbeEntryPoint, intent: Intent) {
        val kind = ProbeProtocol.parseStrategy(intent.getStringExtra(ProbeProtocol.EXTRA_STRATEGY))
        if (kind == null) {
            Log.w(ProbeProtocol.TAG, "strategy change ignored: unknown variant")
            return
        }
        graph.reminderSettings().setAlarmStrategy(kind)
        Log.i(ProbeProtocol.TAG, ProbeProtocol.strategySetLine(kind))
    }

    /** Снять пробы прошлого прогона: иначе их срабатывания попали бы в чужой отчёт. */
    private fun cancelPrevious(context: Context) {
        val alarms = requireNotNull(context.getSystemService(AlarmManager::class.java))
        for (index in 0 until ProbeProtocol.MAX_PROBES) {
            val pending = PendingIntent.getBroadcast(
                context,
                index,
                fireIntent(context, index),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            alarms.cancel(pending)
            pending.cancel()
        }
    }

    private fun probeIntent(context: Context, index: Int, at: Instant, kind: AlarmKind): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            index,
            fireIntent(context, index)
                .putExtra(ProbeProtocol.EXTRA_SCHEDULED_AT, at.toEpochMilli())
                .putExtra(ProbeProtocol.EXTRA_STRATEGY, kind.code),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun fireIntent(context: Context, index: Int): Intent = Intent(context, ProbeFireReceiver::class.java)
        .setAction(ProbeProtocol.ACTION_FIRE)
        .setData(Uri.parse("sklyanka://probe/$index"))
        .putExtra(ProbeProtocol.EXTRA_INDEX, index)
}

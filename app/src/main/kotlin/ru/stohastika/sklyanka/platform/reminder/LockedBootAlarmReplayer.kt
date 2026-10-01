package ru.stohastika.sklyanka.platform.reminder

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.reminder.DirectBootReplayDecision
import ru.stohastika.sklyanka.domain.reminder.DirectBootReplayPolicy
import ru.stohastika.sklyanka.domain.reminder.DirectBootTrace
import ru.stohastika.sklyanka.domain.reminder.DirectBootTraceReason
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootAlarmStore
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootTraceStore
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Восстановление будильников по снимку до разблокировки (SKL-87).
 *
 * Читает DE-снимок и ставит будущие будильники напрямую `AlarmManager`, не касаясь CE:
 * до разблокировки база и журнал закрыты. Адрес будильника — тот же
 * [ReminderAlarms.intake], что у планировщика, поэтому повторная постановка после
 * разблокировки заменяет восстановленный будильник, а не плодит второй (НАП-9).
 * Отказ деградирует явно: исход попытки уходит в очередь следов, а не молча.
 */
@Singleton
class LockedBootAlarmReplayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DirectBootAlarmStore,
    private val traces: DirectBootTraceStore,
    private val time: TimeSource,
) {

    private val alarms: AlarmManager = requireNotNull(context.getSystemService(AlarmManager::class.java))

    suspend fun replay(): DirectBootReplayDecision {
        val now = time.now()
        return when (val decision = DirectBootReplayPolicy.decide(store.load(), now)) {
            is DirectBootReplayDecision.Skip -> {
                traces.append(
                    DirectBootTrace(now, replayed = 0, skipped = 0, failed = 0, reason = decision.reason.code),
                )
                decision
            }

            is DirectBootReplayDecision.Restore -> restore(decision, now)
        }
    }

    private suspend fun restore(decision: DirectBootReplayDecision.Restore, now: Instant): DirectBootReplayDecision {
        if (!canScheduleExactAlarms()) {
            traces.append(
                DirectBootTrace(
                    occurredAt = now,
                    replayed = 0,
                    skipped = decision.skipped,
                    failed = 0,
                    reason = DirectBootTraceReason.EXACT_DENIED.code,
                ),
            )
            return DirectBootReplayDecision.Skip(DirectBootTraceReason.EXACT_DENIED)
        }

        var replayed = 0
        var failed = 0
        decision.alarms.forEach { alarm ->
            val pending = ReminderAlarms.pendingIntent(
                context,
                ReminderAlarms.intake(context, alarm.intakeId, alarm.attempt),
            )
            val placed = try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.at.toEpochMilli(), pending)
                true
            } catch (_: SecurityException) {
                false
            }
            if (placed) replayed++ else failed++
        }
        val reason = if (replayed > 0) DirectBootTraceReason.REPLAYED else DirectBootTraceReason.EMPTY
        traces.append(DirectBootTrace(now, replayed, decision.skipped, failed, reason.code))
        return decision
    }

    /** Право на точные будильники: до Android 12 оно не требуется вовсе (НАП-16). */
    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
}

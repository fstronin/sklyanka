package ru.stohastika.sklyanka.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.work.await
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.data.room.databaseStateOf
import ru.stohastika.sklyanka.di.DirectBootEntryPoint
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason

/**
 * События загрузки до и после первой разблокировки (SKL-87).
 *
 * На EMUI `BOOT_COMPLETED` приходит только после разблокировки, поэтому до неё будильников
 * нет вовсе. `LOCKED_BOOT_COMPLETED` восстанавливает их по DE-снимку, не касаясь CE;
 * `USER_UNLOCKED` переносит след восстановления в журнал и запускает полную пересборку
 * прежним механизмом SKL-13, которая исправляет всё, чего снимок не знал (пояс, прошедшие
 * моменты, изменения расписания).
 *
 * На устройстве без защищённой блокировки locked-фазы нет: `isUserUnlocked` истинно уже
 * на `LOCKED_BOOT_COMPLETED`, и ветка ведёт себя как обычный `BOOT_COMPLETED`.
 */
class DirectBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // База закрыта (новее или места нет): ни пересборки, ни переноса следа — оба читают
        // или пишут базу, которой эта сборка работать не может (Р-44, ADR-0012 пп. 3, 7).
        // Человеку называется причина: обновление или место.
        val decision = wakeUpDecision(databaseStateOf(context))
        if (announceBlockedWakeUp(context, decision)) return
        // Компонент может отсутствовать: систему не интересует, под какой точкой сборки
        // поднят процесс, и broadcast приходит в процесс, графа ещё не имеющий
        // (например, инструментальный процесс тестовой сборки). Восстанавливать
        // напоминания такому процессу нечего, а падение приёмника роняло бы процесс.
        val graph = runCatching { DirectBootEntryPoint.of(context) }.getOrNull() ?: return
        val pending = goAsync()
        graph.reminderScope().launch {
            try {
                handle(context, graph, intent.action)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, graph: DirectBootEntryPoint, action: String?) {
        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                if (isUserUnlocked(context)) {
                    ReminderWork.enqueueReschedule(context, RescheduleReason.BOOT).await()
                } else {
                    graph.lockedBootAlarmReplayer().replay()
                }
            }

            Intent.ACTION_USER_UNLOCKED -> {
                graph.traceDrain().drainIntoJournal()
                ReminderWork.enqueueReschedule(context, RescheduleReason.USER_UNLOCKED).await()
            }
        }
    }

    private fun isUserUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked == true
}

package ru.heuri.sklyanka.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.di.ReminderEntryPoint
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import ru.heuri.sklyanka.platform.reminder.ReminderAlarms
import java.time.Instant

/**
 * Сработавший будильник: показ напоминания, якорь и пробное напоминание (`TDD.md §9.1`).
 *
 * Работу приёмник не выполняет синхронно: бюджет `BroadcastReceiver` не гарантирует
 * ни времени на чтение базы, ни времени на пересборку окна (НАП-13). [goAsync] продлевает
 * жизнь приёмника на время короткой работы и не требует ни `WorkManager`, ни
 * foreground-сервиса. Корутина живёт в области приложения, а не приёмника: объект приёмника
 * система выбрасывает сразу после `onReceive`.
 *
 * Почему не `WorkManager`, как предполагал `TDD.md §5.4`: `androidx.work` объявляет
 * в своём манифесте `ACCESS_NETWORK_STATE`, а разрешения сети в приложении нет и быть
 * не может (ПЛ-3, НФТ-15) — проверка состава собранного артефакта останавливает сборку
 * на любом сетевом разрешении, пришедшем из зависимости. Отказ от библиотеки здесь дешевле
 * вычёркивания её разрешения из слитого манифеста.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = ReminderEntryPoint.of(context)
        val pending = goAsync()
        graph.reminderScope().launch {
            try {
                handle(graph, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(graph: ReminderEntryPoint, intent: Intent) {
        when (intent.action) {
            ReminderAlarms.ACTION_INTAKE -> {
                val intakeId = intent.getStringExtra(ReminderAlarms.EXTRA_INTAKE_ID) ?: return
                graph.handleFiredReminder()(intakeId, intent.getIntExtra(ReminderAlarms.EXTRA_ATTEMPT, 0))
                // Окно сдвигается вперёд после каждого срабатывания: очередь будильников
                // не имеет права опустеть (НФТ-7).
                graph.reminderPlanning().planAll(RescheduleReason.ALARM_FIRED)
            }

            // Якорь — последняя линия обороны: сутки, в которые не сработало ни одного
            // напоминания и приложение не открывалось, окно пересобирать больше некому.
            ReminderAlarms.ACTION_ANCHOR -> graph.reminderPlanning().planAll(RescheduleReason.ANCHOR)

            // Граница суток: неотмеченные приёмы прошедших суток становятся пропущенными,
            // их карточки снимаются, новых уведомлений не появляется (НАП-34).
            ReminderAlarms.ACTION_DAY_BOUNDARY -> graph.sweepAtDayBoundary()()

            ReminderAlarms.ACTION_TEST -> {
                val planned = intent.getLongExtra(ReminderAlarms.EXTRA_PLANNED_AT, 0L)
                graph.showTestReminder()(Instant.ofEpochMilli(planned))
            }
        }
    }
}

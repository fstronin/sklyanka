package ru.stohastika.sklyanka.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.await
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.di.ReminderEntryPoint
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.platform.reminder.ReminderAlarms

/**
 * Сработавший будильник: показ напоминания, якорь и граница суток (`TDD.md §9.1`).
 *
 * Разделение работы здесь не произвольно. **Показ** обязан случиться сейчас и именно в
 * приёмнике: отклонение от планового времени считается от момента предъявления уведомления
 * (НАП-2), и отдавать показ в очередь задач значило бы добавлять к нему чужую задержку.
 * Приёмник продлевает себе жизнь через [goAsync] — работы там на доли секунды: прочитать
 * приём, собрать карточку, показать.
 *
 * Всё остальное — пересборка окна и полуночный проход — уходит задачей `WorkManager`
 * (НАП-13, решение `ОВ-79`): эта работа тяжелее, и она обязана состояться даже если процесс
 * убьют сразу после показа.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = ReminderEntryPoint.of(context)
        val pending = goAsync()
        graph.reminderScope().launch {
            try {
                handle(graph, context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(graph: ReminderEntryPoint, context: Context, intent: Intent) {
        when (intent.action) {
            ReminderAlarms.ACTION_INTAKE -> {
                val intakeId = intent.getStringExtra(ReminderAlarms.EXTRA_INTAKE_ID) ?: return
                graph.handleFiredReminder()(intakeId, intent.getIntExtra(ReminderAlarms.EXTRA_ATTEMPT, 0))
                // Окно сдвигается вперёд после каждого срабатывания: очередь будильников
                // не имеет права опустеть (НФТ-7).
                ReminderWork.enqueueReschedule(context, RescheduleReason.ALARM_FIRED).await()
            }

            // Якорь — последняя линия обороны: сутки, в которые не сработало ни одного
            // напоминания и приложение не открывалось, окно пересобирать больше некому.
            ReminderAlarms.ACTION_ANCHOR ->
                ReminderWork.enqueueReschedule(context, RescheduleReason.ANCHOR).await()

            // Граница суток: неотмеченные приёмы прошедших суток становятся пропущенными,
            // их карточки снимаются, новых уведомлений не появляется (НАП-34).
            ReminderAlarms.ACTION_DAY_BOUNDARY -> ReminderWork.enqueueDayBoundary(context).await()

            // Тестовое срабатывание самопроверки (SKL-86, ОВ-108): показ, отметка
            // предъявления и исход — тонкий путь, не трогающий план. Пересборка окна
            // не запускается: проверка не входит в план напоминаний.
            ReminderAlarms.ACTION_DELIVERY_CHECK -> graph.handleFiredDeliveryCheck()()
        }
    }
}

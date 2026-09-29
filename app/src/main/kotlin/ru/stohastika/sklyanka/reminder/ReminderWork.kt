package ru.stohastika.sklyanka.reminder

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason

/**
 * Постановка задач подсистемы напоминаний (НАП-13, `TDD.md §5.4`, решение `ОВ-79`).
 *
 * Работа выносится из приёмника в `WorkManager` не ради красоты: у приёмника есть около десяти
 * секунд и нет гарантии, что процесс доживёт до конца работы. Перепланирование, не состоявшееся
 * после перезагрузки, — молчаливый отказ, и человек узнаёт о нём пропущенным приёмом.
 * Задача же переживает смерть процесса: она записана в свою базу и будет выполнена системой.
 *
 * Точность самих напоминаний к этому отношения не имеет и остаётся за
 * `AlarmManager.setExactAndAllowWhileIdle` (`ADR-0003`): `WorkManager` отвечает на вопрос
 * «чем выполнить работу после события», а не «откуда взять точное время».
 */
internal object ReminderWork {

    /** Имя очереди пересборки окна. Уникальное: две пересборки подряд не нужны параллельно. */
    const val RESCHEDULE = "sklyanka.reminder.reschedule"

    /** Имя очереди прохода по просроченным приёмам на границе суток (НАП-34). */
    const val DAY_BOUNDARY = "sklyanka.reminder.day-boundary"

    const val KEY_REASON = "reason"

    /** @return операция постановки; её результат стоит дождаться, чтобы запись не потерялась. */
    fun enqueueReschedule(context: Context, reason: RescheduleReason): Operation =
        WorkManager.getInstance(context).enqueueUniqueWork(
            RESCHEDULE,
            // Не REPLACE: снятая посреди работы пересборка оставила бы набор будильников
            // в промежуточном состоянии. Операция идемпотентна (НАП-9), поэтому очередь
            // из двух пересборок безопасна, а порядок событий сохраняется.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            rescheduleRequest(reason),
        )

    fun enqueueDayBoundary(context: Context): Operation = WorkManager.getInstance(context)
        .enqueueUniqueWork(
            DAY_BOUNDARY,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(DayBoundaryWorker::class.java, Data.EMPTY),
        )

    fun rescheduleRequest(reason: RescheduleReason): OneTimeWorkRequest = request(
        worker = RescheduleWorker::class.java,
        data = workDataOf(KEY_REASON to reason.code),
    )

    /** Причина из полезной нагрузки задачи. Незнакомый код означает «делать нечего». */
    fun reasonOf(code: String?): RescheduleReason? = RescheduleReason.entries.firstOrNull {
        it.code == code
    }

    /**
     * Запрос задачи.
     *
     * **Ограничений нет ни одного** (`Constraints.NONE`), и это не значение по умолчанию,
     * а инвариант: сетевое ограничение заставило бы библиотеку обратиться к состоянию сети,
     * разрешение на которое снято из манифеста (ПЛ-3). Инвариант проверяется тестом.
     *
     * `expedited` ставится **только на Android 12 и выше**. Ниже флаг не превращается
     * в ускоренную задачу — такой в системе нет, — а переводит работу в службу переднего
     * плана: `WorkForegroundRunnable` требует у задачи `getForegroundInfo()`, которого
     * у неё нет, и работа падает. То есть на Android 8…11 пометка `expedited` не ускорила бы
     * восстановление, а отменила бы его, да ещё и показала бы человеку служебное уведомление
     * (НФТ-26, НФТ-27). Цена отказа от пометки: на этих версиях пересборка идёт обычной
     * задачей и её момент не гарантирован контрактом — измеряется прогоном SKL-03.
     */
    private fun request(worker: Class<out ListenableWorker>, data: Data): OneTimeWorkRequest {
        val builder = OneTimeWorkRequest.Builder(worker)
            .setInputData(data)
            .setConstraints(Constraints.NONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Если квота ускоренных задач исчерпана, работа выполняется обычной задачей,
            // а не отбрасывается: отброшенная пересборка означает отсутствие будильников.
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        return builder.build()
    }
}

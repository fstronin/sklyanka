package ru.stohastika.sklyanka.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.stohastika.sklyanka.di.ReminderEntryPoint

/**
 * Проход по просроченным приёмам на границе суток фоновой задачей (НАП-34).
 *
 * Отдельная задача от пересборки окна: у них разные поводы и разные очереди, а склеивание
 * означало бы, что полуночный проход не состоится, если в этот момент выполняется пересборка.
 *
 * Уведомлений задача не порождает и порождать не может — этого не умеет ни один её порт:
 * телефон, включённый после суток простоя, не выдаёт очередь карточек (испытание И-10).
 */
class DayBoundaryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = try {
        ReminderEntryPoint.of(applicationContext).sweepAtDayBoundary()()
        Result.success()
    } catch (error: Exception) {
        // Тот же довод, что в RescheduleWorker: причина отказа базы неперечислима, а цена —
        // карточка вчерашнего приёма, висящая в шторке до следующего события. Исчерпав
        // попытки, ошибку пробрасываем: молчаливый отказ разбирать нечем.
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else throw error
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

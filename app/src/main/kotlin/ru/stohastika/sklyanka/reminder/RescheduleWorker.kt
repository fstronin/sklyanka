package ru.stohastika.sklyanka.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.stohastika.sklyanka.data.room.databaseStateOf
import ru.stohastika.sklyanka.di.ReminderEntryPoint
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason

/**
 * Пересборка окна будильников фоновой задачей (НАП-11, НАП-13, `TDD.md §5.4`).
 *
 * Задача, а не работа приёмника: она переживает смерть процесса, и перепланирование после
 * перезагрузки происходит даже если приложение выгрузили посреди работы.
 *
 * Зависимости берутся из графа явной точкой входа: `WorkManager` создаёт задачу сам,
 * фабрикой по умолчанию, и внедрять в неё через конструктор нечем без ещё одной библиотеки.
 */
class RescheduleWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    /**
     * Отказ повторяется с отсрочкой, а исчерпав попытки — не скрывается.
     *
     * Перехват широкого типа здесь осознан: набор причин, по которым не удалось прочитать базу
     * (занятый файл, нет места, миграция соседнего процесса), не перечислим, а цена отказа —
     * отсутствие будильников. Повтор дешевле, чем ждать якорного будильника (НАП-8).
     * Последняя попытка пробрасывает исходную ошибку: `Result.failure()` записал бы в историю
     * задач «не получилось» без причины, и разбирать было бы нечего.
     */
    override suspend fun doWork(): Result {
        val reason = ReminderWork.reasonOf(inputData.getString(ReminderWork.KEY_REASON))
        return when {
            // База новее: пересборка окна читала бы и писала базу, которой эта сборка
            // не знает (Р-44). Задача завершается успешно и ничего не делает: сообщить
            // человеку успел приёмник будильника, а не фоновая задача.
            wakeUpDecision(databaseStateOf(applicationContext)) == WakeUpDecision.AskToUpdate -> Result.success()

            reason == null -> Result.failure()

            else -> replan(reason)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun replan(reason: RescheduleReason): Result = try {
        ReminderEntryPoint.of(applicationContext).reminderPlanning().planAll(reason)
        Result.success()
    } catch (error: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else throw error
    }

    private companion object {
        /**
         * Предел попыток.
         *
         * Бесконечный повтор при устойчивом отказе базы выедал бы батарею, не давая результата;
         * окно всё равно пересоберут якорный будильник и следующий запуск приложения.
         */
        const val MAX_ATTEMPTS = 3
    }
}

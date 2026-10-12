package ru.stohastika.sklyanka.platform

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/**
 * Отказ «забытой» фоновой работы не роняет область и оставляет след (П-1, Р-44, НФТ-19).
 *
 * Проверяется ровно то, ради чего обработчик существует. Первое: падение работы в области
 * не отдаётся системному обработчику потока (он в Android завершает процесс) — область
 * продолжает работать, и следующая задача в ней доходит до конца. Второе: отказ записан
 * с этапом работы и классом отказа, но без данных человека: сообщение отказа в журнал
 * не переносится, потому что в нём бывают значения из запроса к базе.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundWorkFailuresTest {

    @Test
    fun `a failed startup job is recorded and the scope keeps working`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + BackgroundWorkFailures().forStartup())
        val survived = CompletableDeferred<Unit>()

        scope.launch { throw IllegalStateException("no such table: medication") }.join()
        scope.launch { survived.complete(Unit) }
        withTimeout(TIMEOUT_MS) { survived.await() }

        val record = lastRecord()
        assertEquals(Log.ERROR, record.type)
        assertTrue("the stage must be named: ${record.msg}", record.msg.contains("STARTUP"))
        assertTrue(
            "the kind of failure must be named: ${record.msg}",
            record.msg.contains("IllegalStateException"),
        )
        assertFalse(
            "the message of the failure must not be recorded: ${record.msg}",
            record.msg.contains("medication"),
        )
    }

    @Test
    fun `the work of the reminder receivers is recorded as its own stage`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + BackgroundWorkFailures().forReminders())

        scope.launch { throw IllegalStateException("the database is locked") }.join()

        assertTrue("the stage must be named: ${lastRecord().msg}", lastRecord().msg.contains("REMINDERS"))
    }

    /**
     * Отказ не доходит до обработчика потока.
     *
     * Это и есть цена ошибки: обработчик потока в Android завершает процесс. Стенд ловит
     * его подменой обработчика текущего потока — тот же путь, каким отказ ушёл бы системе,
     * если бы в области не стоял `BackgroundWorkFailures`.
     */
    @Test
    fun `the failure does not reach the handler of the thread`() = runBlocking {
        val leaked = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, failure -> leaked += failure }
        try {
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Unconfined + BackgroundWorkFailures().forStartup(),
            )
            scope.launch { throw IllegalStateException("boom") }.join()
        } finally {
            thread.uncaughtExceptionHandler = previous
        }

        assertTrue("the failure must not reach the system handler: $leaked", leaked.isEmpty())
    }

    /** Последняя строка журнала фоновой работы. */
    private fun lastRecord() = ShadowLog.getLogs().last { it.tag == TAG }

    private companion object {
        const val TAG = "SklyankaBackground"
        const val TIMEOUT_MS = 5_000L
    }
}

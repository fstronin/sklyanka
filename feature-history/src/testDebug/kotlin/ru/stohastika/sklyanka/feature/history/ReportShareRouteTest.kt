package ru.stohastika.sklyanka.feature.history

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Нажатие «Передать» через настоящий маршрут истории (SKL-101, дефект 4).
 *
 * Проверяется решение маршрута, а не разметка шторки: запуск открывает системный выбор
 * получателя, файл отдаётся только на чтение, а успех ставится после `RESULT_OK`, не
 * в момент открытия диалога. Настоящего `ChooserActivity` на JVM нет, и стенд
 * `ReportShareStand` представляет его записью запуска и ответом реестра результатов.
 */
@RunWith(AndroidJUnit4::class)
class ReportShareRouteTest {

    @Test
    fun theSendIntentIsAStagedPdfAndSuccessWaitsForTheResult() {
        val stand = ReportShareStand()
        stand.openReport()

        val press = stand.pressShare(andAnswer = Activity.RESULT_OK)

        assertNull("no success may be shown while the chooser is open", press.outcomeWhileOpen)
        val chooser = requireNotNull(press.chooser) { "the chooser must have been launched" }
        val send = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)) {
            "the chooser must carry the send intent"
        }
        assertEquals("application/pdf", send.type)
        assertTrue(
            "the receiver must be granted read access only",
            send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
        assertTrue(
            "the receiver must not be granted write access",
            send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0,
        )
        assertEquals(ReportOutcome.SHARED, press.outcomeAfter)
    }

    @Test
    fun aCancelledChooserReportsCancellationAndNotSuccess() {
        val stand = ReportShareStand()
        stand.openReport()

        val press = stand.pressShare(andAnswer = Activity.RESULT_CANCELED)

        assertNull("no success may be shown while the chooser is open", press.outcomeWhileOpen)
        assertEquals(ReportOutcome.CANCELLED, press.outcomeAfter)
    }
}

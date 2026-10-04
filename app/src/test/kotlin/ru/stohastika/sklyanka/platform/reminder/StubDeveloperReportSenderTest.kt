package ru.stohastika.sklyanka.platform.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.feature.reminder.DeveloperReportOutcome

/**
 * Заглушка отправки отчёта разработчику (SKL-102, ОВ-127, ПМ-31/ПМ-32).
 *
 * Первая версия сети не имеет: заглушка отвечает «отправлено», ничего не собирая и никуда
 * не обращаясь, поэтому проверяется ровно продуктовый контракт — исход `SENT`, независимый
 * от того, что человек написал в отчёте. Сетевой адаптер встанет в этот шов отдельной
 * задачей, и тогда у заглушки появится собственный стенд.
 */
class StubDeveloperReportSenderTest {

    private val sender = StubDeveloperReportSender()

    @Test
    fun `an empty report is reported as sent`() = runTest {
        assertEquals(DeveloperReportOutcome.SENT, sender.send(""))
    }

    @Test
    fun `a report with a comment is reported as sent`() = runTest {
        assertEquals(DeveloperReportOutcome.SENT, sender.send("the reminder did not arrive"))
    }
}

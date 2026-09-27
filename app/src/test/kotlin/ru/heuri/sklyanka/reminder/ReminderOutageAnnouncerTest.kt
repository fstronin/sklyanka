package ru.heuri.sklyanka.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.heuri.sklyanka.domain.reminder.ReminderOutage
import java.time.Instant

/**
 * Сообщение о перерыве при входе в приложение (НАП-38).
 *
 * Проверяется правило объявления, а не сам показ: признак считает домен
 * (`OutageDetectorTest`), рисует баннер экранный модуль, а здесь решается, **когда**
 * человек это сообщение видит и когда перестаёт видеть.
 */
class ReminderOutageAnnouncerTest {

    private val noon: Instant = Instant.parse("2026-09-25T12:00:00Z")
    private val evening: Instant = Instant.parse("2026-09-25T18:00:00Z")

    private var detected: ReminderOutage? = null
    private val announcer = ReminderOutageAnnouncer { detected }

    @Test
    fun `a detected break is announced`() = runTest {
        detected = ReminderOutage(since = noon, missed = 2)

        announcer.refresh()

        assertEquals(ReminderOutage(since = noon, missed = 2), announcer.current.value)
    }

    @Test
    fun `without a break there is nothing to announce`() = runTest {
        announcer.refresh()

        assertNull(announcer.current.value)
    }

    @Test
    fun `the same break is not announced twice`() = runTest {
        detected = ReminderOutage(since = noon, missed = 2)
        announcer.refresh()

        // Человек ушёл по сообщению в диагностику: оно доставлено, и возвращаться при
        // каждом переходе между вкладками ему незачем.
        announcer.acknowledge()
        announcer.refresh()

        assertNull(announcer.current.value)
    }

    @Test
    fun `a later break is announced again`() = runTest {
        detected = ReminderOutage(since = noon, missed = 2)
        announcer.refresh()
        announcer.acknowledge()

        detected = ReminderOutage(since = evening, missed = 1)
        announcer.refresh()

        assertEquals(evening, announcer.current.value?.since)
    }

    @Test
    fun `a break that ended stops being announced`() = runTest {
        detected = ReminderOutage(since = noon, missed = 2)
        announcer.refresh()

        // Напоминания снова пошли: признак вычисляется заново, а не хранится флагом.
        detected = null
        announcer.refresh()

        assertNull(announcer.current.value)
    }
}

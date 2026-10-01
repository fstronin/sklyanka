package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingKey
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Изменение настройки напоминания оставляет след в журнале (НАП-14).
 *
 * Проверяется на реализации порта, а не на экране: настройка меняется из настроек,
 * из онбординга и при загрузке файла переноса, и след обязан быть один и тот же при
 * любом пути. Тест на экране доказывал бы ровно один путь.
 */
class JournalingReminderSettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now: Instant = Instant.parse("2026-09-25T09:00:00Z")
    private val clock = FixedTimeSource(now, ZoneId.of("Europe/Moscow"))
    private val journal = RecordingJournal()

    private fun settings(): JournalingReminderSettings = JournalingReminderSettings(
        store = ReminderSettingsStore(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }),
        journal = journal,
        time = clock,
    )

    @Test
    fun `switching persistent reminders on leaves a trace`() = runTest {
        val settings = settings()

        settings.setPersistentEnabled(true)

        val entry = journal.entries.single()
        assertEquals(ReminderEvent.SETTINGS_CHANGED, entry.event)
        assertEquals(now, entry.occurredAt)
        assertEquals("the key and its new value", "persistent_reminder_enabled=true", entry.reason)
        assertTrue("the value really reached the store", settings.read().persistentEnabled)
    }

    @Test
    fun `changing the snooze interval leaves a trace with the value`() = runTest {
        val settings = settings()

        settings.setSnoozeMinutes(30)

        val entry = journal.entries.single()
        assertEquals(ReminderSettingKey.SNOOZE_MINUTES.trace(30), entry.reason)
        assertEquals(30, entry.minutes)
        assertEquals(30, settings.read().snoozeMinutes)
    }

    @Test
    fun `writing the same value again leaves nothing`() = runTest {
        val settings = settings()

        // Экран настроек перечитывает состояние при каждом возвращении на передний план:
        // запись «на каждое касание» превратила бы журнал срабатываний в журнал открытий.
        settings.setPersistentEnabled(false)
        settings.setSnoozeMinutes(15)
        settings.setAlarmStrategy(AlarmKind.ALARM_CLOCK)

        assertTrue("unchanged values leave no trace", journal.entries.isEmpty())
    }

    /** Смена варианта измерения — граница окна A/B-прогона, и она обязана быть в журнале (SKL-85). */
    @Test
    fun `changing the measurement variant leaves a trace with the new code`() = runTest {
        val settings = settings()

        settings.setAlarmStrategy(AlarmKind.EXACT_IDLE)

        val entry = journal.entries.single()
        assertEquals(ReminderEvent.SETTINGS_CHANGED, entry.event)
        assertEquals(ReminderSettingKey.ALARM_STRATEGY.trace(AlarmKind.EXACT_IDLE.code), entry.reason)
        assertEquals(AlarmKind.EXACT_IDLE, settings.read().alarmStrategy)
    }

    @Test
    fun `a refused interval leaves neither the value nor the trace`() = runTest {
        val settings = settings()

        val refused = runCatching { settings.setSnoozeMinutes(0) }

        assertTrue(refused.isFailure)
        assertTrue("a value that was refused leaves no trace", journal.entries.isEmpty())
        assertEquals(15, settings.read().snoozeMinutes)
    }

    @Test
    fun `the trace carries no medical data at all`() = runTest {
        val settings = settings()

        settings.setPersistentEnabled(true)
        settings.setSnoozeMinutes(45)

        // Запись видит только код настройки и её значение: названий лекарств, дозировок
        // и имён профилей этому классу взять негде (НФТ-19).
        journal.entries.forEach { entry ->
            assertEquals(null, entry.intakeId)
            assertEquals(null, entry.medicationId)
            assertEquals(null, entry.profileId)
            assertFalse(requireNotNull(entry.reason).any { it.isLetter() && it.code > ASCII_LIMIT })
        }
    }

    private companion object {
        /** Кириллица в служебной строке означала бы текст для человека, а не код. */
        const val ASCII_LIMIT = 127
    }
}

/** Журнал в памяти: проверяется и сам след, и отсутствие в нём медицинских данных. */
private class RecordingJournal : ReminderJournal {

    val entries: MutableList<ReminderLogEntry> = mutableListOf()

    override suspend fun record(entry: ReminderLogEntry) {
        entries += entry
    }

    override suspend fun recent(limit: Int): List<ReminderLogEntry> = entries.takeLast(limit).reversed()

    override suspend fun last(event: ReminderEvent): ReminderLogEntry? = entries.lastOrNull { it.event == event }

    override suspend fun firedIntakeIds(since: Instant): Set<String> = emptySet()

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit
}

package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarm
import ru.stohastika.sklyanka.domain.reminder.DirectBootReplayPolicy
import ru.stohastika.sklyanka.domain.reminder.DirectBootTrace
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

/**
 * Хранилище снимка в device-protected storage (SKL-87).
 *
 * Проверяется на JVM с файлом настроек во временном каталоге: `DataStore` для этого не нужен
 * ни `Context`, ни устройство. Коррупция файла переводится в «снимка нет», а не в отказ,
 * тем же обработчиком, что в провайдере `DirectBootModule`.
 */
class DeviceProtectedDirectBootStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now: Instant = Instant.parse("2026-09-27T02:00:00Z")

    private val counter = java.util.concurrent.atomic.AtomicInteger(0)

    private fun newFile(): java.io.File = folder.newFile("direct_boot_${counter.incrementAndGet()}.preferences_pb")

    private fun newStore(at: Instant = now): DeviceProtectedDirectBootStore =
        DeviceProtectedDirectBootStore(dataStoreOver(newFile()), FixedTimeSource(at))

    private fun dataStoreOver(file: java.io.File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    ) { file }

    @Test
    fun `put then load returns the alarm with version and updatedAt`() = runTest {
        val alarm = DirectBootAlarm("intake-1", now.plusSeconds(3600), 0)
        val store = newStore()

        store.put(alarm)

        val loaded = store.load()
        assertEquals(DirectBootReplayPolicy.FORMAT_VERSION, loaded?.version)
        assertEquals(now, loaded?.updatedAt)
        assertEquals(listOf(alarm), loaded?.alarms)
    }

    @Test
    fun `a stored alarm survives a new store instance over the same data`() = runTest {
        val dataStore = dataStoreOver(newFile())
        val alarm = DirectBootAlarm("intake-1", now.plusSeconds(3600), 0)

        DeviceProtectedDirectBootStore(dataStore, FixedTimeSource(now)).put(alarm)
        val reloaded = DeviceProtectedDirectBootStore(dataStore, FixedTimeSource(now)).load()

        assertEquals(DirectBootReplayPolicy.FORMAT_VERSION, reloaded?.version)
        assertEquals(listOf(alarm), reloaded?.alarms)
    }

    @Test
    fun `put replaces the previous record for the same intake`() = runTest {
        val store = newStore()
        store.put(DirectBootAlarm("intake-1", now.plusSeconds(60), 0))
        store.put(DirectBootAlarm("intake-1", now.plusSeconds(120), 1))

        val loaded = store.load()
        assertEquals(1, loaded?.alarms?.size)
        assertEquals(DirectBootAlarm("intake-1", now.plusSeconds(120), 1), loaded?.alarms?.single())
    }

    @Test
    fun `remove clears the record and keeps the snapshot readable`() = runTest {
        val store = newStore()
        store.put(DirectBootAlarm("intake-1", now.plusSeconds(3600), 0))

        store.remove("intake-1")

        assertEquals(emptyList<DirectBootAlarm>(), store.load()?.alarms)
    }

    @Test
    fun `replace writes the whole snapshot and drops records outside it`() = runTest {
        val store = newStore()
        store.put(DirectBootAlarm("intake-old", now.plusSeconds(60), 2))

        store.replace(
            listOf(
                DirectBootAlarm("intake-a", now.plusSeconds(3600), 0),
                DirectBootAlarm("intake-b", now.plusSeconds(7200), 1),
            ),
        )

        val loaded = store.load()
        assertEquals(setOf("intake-a", "intake-b"), loaded?.alarms?.map { it.intakeId }?.toSet())
        assertEquals(DirectBootReplayPolicy.FORMAT_VERSION, loaded?.version)
        assertEquals(now, loaded?.updatedAt)
    }

    @Test
    fun `replace with an empty list leaves a readable empty snapshot`() = runTest {
        val store = newStore()
        store.put(DirectBootAlarm("intake-1", now.plusSeconds(60), 0))

        store.replace(emptyList())

        assertEquals(emptyList<DirectBootAlarm>(), store.load()?.alarms)
    }

    @Test
    fun `an empty file means there is no snapshot`() = runTest {
        assertNull(newStore().load())
    }

    @Test
    fun `a corrupted file reads as no snapshot and writing afterwards works`() = runTest {
        val file = newFile()
        file.writeText("not-a-protobuf")
        val store = DeviceProtectedDirectBootStore(dataStoreOver(file), FixedTimeSource(now))

        assertNull(store.load())

        store.put(DirectBootAlarm("intake-1", now.plusSeconds(3600), 0))
        assertEquals(1, store.load()?.alarms?.size)
    }

    @Test
    fun `a malformed alarm record is dropped without breaking the rest`() = runTest {
        val dataStore = dataStoreOver(newFile())
        val store = DeviceProtectedDirectBootStore(dataStore, FixedTimeSource(now))
        val valid = DirectBootAlarm("intake-1", now.plusSeconds(3600), 0)
        store.put(valid)

        dataStore.edit { prefs ->
            val key = stringSetPreferencesKey("direct_boot_alarms")
            prefs[key] = prefs[key].orEmpty() + "intake-broken@not-a-number@x"
        }

        assertEquals(listOf(valid), store.load()?.alarms)
    }

    @Test
    fun `traces are evicted to the eight newest and drain clears the queue`() = runTest {
        val store = newStore()
        repeat(10) { i ->
            store.append(
                DirectBootTrace(
                    occurredAt = now.plusSeconds(i.toLong()),
                    replayed = 1,
                    skipped = 0,
                    failed = 0,
                    reason = "replayed",
                ),
            )
        }

        val drained = store.drain()

        assertEquals(8, drained.size)
        assertEquals(now.plusSeconds(2), drained.first().occurredAt)
        assertEquals(now.plusSeconds(9), drained.last().occurredAt)
        assertTrue(store.drain().isEmpty())
    }
}

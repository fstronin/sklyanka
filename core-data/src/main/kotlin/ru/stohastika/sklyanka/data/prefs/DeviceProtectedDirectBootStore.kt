package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.data.di.DirectBootPreferences
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarm
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarms
import ru.stohastika.sklyanka.domain.reminder.DirectBootReplayPolicy
import ru.stohastika.sklyanka.domain.reminder.DirectBootTrace
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootAlarmStore
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootTraceStore
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Зеркало поставленных будильников и очередь следов locked-фазы в device-protected storage.
 *
 * Один файл `DataStore<Preferences>` на оба порта: снимок вторичен и восстановим из CE,
 * а трассы нужны только чтобы донести факт восстановления до журнала после разблокировки.
 * Формат — служебные строки, а не JSON: объём крошечный, а разбор не тянет сериализацию
 * в критичный для locked-фазы путь. Неразобранная строка отбрасывается при чтении и не
 * роняет остальные; после разблокировки план всё равно пересобирается из CE целиком.
 */
@Singleton
class DeviceProtectedDirectBootStore @Inject constructor(
    @DirectBootPreferences private val store: DataStore<Preferences>,
    private val time: TimeSource,
) : DirectBootAlarmStore,
    DirectBootTraceStore {

    override suspend fun load(): DirectBootAlarms? {
        val prefs = store.data.first()
        val version = prefs[KEY_VERSION]
        val updatedAt = prefs[KEY_UPDATED_AT]
        return if (version == null || updatedAt == null) {
            null
        } else {
            DirectBootAlarms(
                version = version,
                updatedAt = Instant.ofEpochMilli(updatedAt),
                alarms = prefs[KEY_ALARMS].orEmpty().mapNotNull(::parseAlarm),
            )
        }
    }

    override suspend fun put(alarm: DirectBootAlarm) {
        store.edit { prefs ->
            // Один `intakeId` — одна запись: повторная постановка заменяет прежнюю,
            // как и поставленный будильник заменяет предыдущий (НАП-9).
            val kept = prefs[KEY_ALARMS].orEmpty().filterNot { it.substringBefore(ALARM_SEPARATOR) == alarm.intakeId }
            prefs[KEY_ALARMS] = (kept + encode(alarm)).toSet()
            prefs[KEY_VERSION] = DirectBootReplayPolicy.FORMAT_VERSION
            prefs[KEY_UPDATED_AT] = time.now().toEpochMilli()
        }
    }

    override suspend fun remove(intakeId: String) {
        store.edit { prefs ->
            prefs[KEY_ALARMS] = prefs[KEY_ALARMS].orEmpty()
                .filterNot { it.substringBefore(ALARM_SEPARATOR) == intakeId }
                .toSet()
            prefs[KEY_UPDATED_AT] = time.now().toEpochMilli()
        }
    }

    override suspend fun replace(alarms: List<DirectBootAlarm>) {
        store.edit { prefs ->
            // Один `intakeId` — одна запись и здесь: состав приходит из плана, но инвариант
            // держится хранилищем, а не доброй волей вызывающего.
            prefs[KEY_ALARMS] = alarms.associateBy { it.intakeId }.values.map(::encode).toSet()
            prefs[KEY_VERSION] = DirectBootReplayPolicy.FORMAT_VERSION
            prefs[KEY_UPDATED_AT] = time.now().toEpochMilli()
        }
    }

    override suspend fun append(trace: DirectBootTrace) {
        store.edit { prefs ->
            val records = (prefs[KEY_TRACES].orEmpty() + encode(trace))
                .mapNotNull(::parseTrace)
                .sortedBy { it.occurredAt }
                .takeLast(MAX_TRACES)
                .map(::encode)
                .toSet()
            prefs[KEY_TRACES] = records
        }
    }

    /**
     * Забирает и очищает очередь одной операцией.
     *
     * Чтение и очистка идут в одном [edit], поэтому повторный вызов пуст, а запись, сделанная
     * до вызова, не задваивается и не теряется: журнал недоступен до разблокировки (НАП-14).
     */
    override suspend fun drain(): List<DirectBootTrace> {
        var drained: List<DirectBootTrace> = emptyList()
        store.edit { prefs ->
            drained = prefs[KEY_TRACES].orEmpty().mapNotNull(::parseTrace).sortedBy { it.occurredAt }
            prefs.remove(KEY_TRACES)
        }
        return drained
    }

    private fun encode(alarm: DirectBootAlarm): String =
        listOf(alarm.intakeId, alarm.at.toEpochMilli().toString(), alarm.attempt.toString())
            .joinToString(ALARM_SEPARATOR)

    @Suppress("ReturnCount")
    private fun parseAlarm(record: String): DirectBootAlarm? {
        val parts = record.split(ALARM_SEPARATOR)
        if (parts.size != ALARM_PARTS) return null
        val id = parts[0]
        val millis = parts[1].toLongOrNull() ?: return null
        val attempt = parts[2].toIntOrNull() ?: return null
        if (id.isEmpty()) return null
        return DirectBootAlarm(id, Instant.ofEpochMilli(millis), attempt)
    }

    private fun encode(trace: DirectBootTrace): String = listOf(
        trace.occurredAt.toEpochMilli().toString(),
        trace.replayed.toString(),
        trace.skipped.toString(),
        trace.failed.toString(),
        trace.reason,
    ).joinToString(TRACE_SEPARATOR)

    @Suppress("ReturnCount", "MagicNumber")
    private fun parseTrace(record: String): DirectBootTrace? {
        val parts = record.split(TRACE_SEPARATOR)
        if (parts.size != TRACE_PARTS) return null
        val millis = parts[0].toLongOrNull() ?: return null
        val replayed = parts[1].toIntOrNull() ?: return null
        val skipped = parts[2].toIntOrNull() ?: return null
        val failed = parts[3].toIntOrNull() ?: return null
        if (replayed < 0 || skipped < 0 || failed < 0) return null
        return DirectBootTrace(Instant.ofEpochMilli(millis), replayed, skipped, failed, parts[4])
    }

    private companion object {
        val KEY_VERSION = intPreferencesKey("direct_boot_version")
        val KEY_UPDATED_AT = longPreferencesKey("direct_boot_updated_at")
        val KEY_ALARMS = stringSetPreferencesKey("direct_boot_alarms")
        val KEY_TRACES = stringSetPreferencesKey("direct_boot_traces")

        /** Разделитель компонентов записи будильника. В UUID его нет, разбор однозначен. */
        const val ALARM_SEPARATOR = "@"

        /** Разделитель компонентов трассы; коды исхода его не содержат. */
        const val TRACE_SEPARATOR = "|"

        /** Потолок очереди следов: они нужны только до первой записи в журнал после разблокировки. */
        const val MAX_TRACES = 8

        const val ALARM_PARTS = 3
        const val TRACE_PARTS = 5
    }
}

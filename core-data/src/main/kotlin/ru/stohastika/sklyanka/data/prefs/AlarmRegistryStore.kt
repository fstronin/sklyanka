package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.reminder.port.AlarmRegistry
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Список поставленных будильников в постоянных настройках.
 *
 * Зачем он вообще нужен: Android не даёт перечислить свои будильники. Спросить систему
 * «что у меня стоит» нельзя, можно только проверить существование конкретного
 * `PendingIntent`. Без списка пересборка окна знала бы лишь то, что сама поставила
 * в этом запуске процесса, — а процесс на EMUI живёт недолго, и после его перезапуска
 * все будильники выглядели бы «непоставленными», то есть каждая пересборка снимала бы
 * и ставила всё заново.
 *
 * Список — не источник правды, а подсказка: правду по каждому идентификатору проверяет
 * платформенная реализация, пробуя существование `PendingIntent` (`AlarmManagerGateway`).
 *
 * Хранится в том же файле настроек, что и активный профиль: это не пользовательская
 * настройка, но и не данные — терять её не страшно, восстанавливается пересборкой.
 */
@Singleton
class AlarmRegistryStore @Inject constructor(private val store: DataStore<Preferences>) : AlarmRegistry {

    override suspend fun all(): Map<String, Instant> = store.data.first()[KEY]
        .orEmpty()
        .mapNotNull(::parse)
        .toMap()

    override suspend fun put(intakeId: String, at: Instant) {
        store.edit { prefs ->
            val kept = prefs[KEY].orEmpty().filterNot { it.substringBefore(SEPARATOR) == intakeId }
            prefs[KEY] = (kept + "$intakeId$SEPARATOR${at.toEpochMilli()}").toSet()
        }
    }

    override suspend fun remove(intakeId: String) {
        store.edit { prefs ->
            prefs[KEY] = prefs[KEY].orEmpty().filterNot { it.substringBefore(SEPARATOR) == intakeId }.toSet()
        }
    }

    private fun parse(record: String): Pair<String, Instant>? {
        val id = record.substringBefore(SEPARATOR, missingDelimiterValue = "")
        val millis = record.substringAfter(SEPARATOR, missingDelimiterValue = "").toLongOrNull()
        return if (id.isEmpty() || millis == null) null else id to Instant.ofEpochMilli(millis)
    }

    private companion object {
        val KEY = stringSetPreferencesKey("scheduled_alarms")

        /** Разделитель идентификатора и момента. В UUID его нет, разбор однозначен. */
        const val SEPARATOR = "@"
    }
}

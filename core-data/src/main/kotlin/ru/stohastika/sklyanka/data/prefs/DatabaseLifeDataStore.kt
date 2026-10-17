package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.data.room.DatabaseRecreation
import ru.stohastika.sklyanka.domain.startup.DatabaseLifeStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отметки жизни базы в постоянных настройках (МГ-8, Р-87 п. 2).
 *
 * Живут в `DataStore` приложения, а не в файле базы: повреждённый файл платформа
 * стирает вместе с содержимым, и отметка обязана это пережить, чтобы пересоздание
 * базы можно было отличить от чистой установки. Очистка данных приложения стирает
 * настройки — отметки жизни тоже, и ложного объяснения на чистой установке нет.
 *
 * | Ключ | Что означает |
 * |---|---|
 * | `db_had_life` | база создавалась и приложение с ней работало |
 * | `db_reset_event_at` | момент (UTC мс) записанного события сброса — идентификатор события |
 * | `db_reset_event_acknowledged_at` | момент события, которое человек закрыл на баннере |
 *
 * Признак пересоздания приходит из памяти процесса ([DatabaseRecreation]), а не
 * из настроек: он живёт ровно до первого чтения стартом экрана.
 */
@Singleton
class DatabaseLifeDataStore @Inject constructor(
    private val preferences: DataStore<Preferences>,
    private val recreation: DatabaseRecreation,
) : DatabaseLifeStore {

    override fun consumeDatabaseRecreated(): Boolean = recreation.consumeRecreated()

    override suspend fun hadLife(): Boolean = preferences.data.first()[DB_HAD_LIFE] ?: false

    override suspend fun markLife() {
        preferences.edit { it[DB_HAD_LIFE] = true }
    }

    override suspend fun resetEventAt(): Long? = preferences.data.first()[DB_RESET_EVENT_AT]

    override suspend fun recordResetEventAt(epochMillis: Long) {
        preferences.edit { it[DB_RESET_EVENT_AT] = epochMillis }
    }

    override suspend fun resetEventAcknowledgedAt(): Long? = preferences.data.first()[DB_RESET_ACKNOWLEDGED_AT]

    override suspend fun acknowledgeResetEventAt(epochMillis: Long) {
        preferences.edit { it[DB_RESET_ACKNOWLEDGED_AT] = epochMillis }
    }

    private companion object {
        val DB_HAD_LIFE = booleanPreferencesKey("db_had_life")
        val DB_RESET_EVENT_AT = longPreferencesKey("db_reset_event_at")
        val DB_RESET_ACKNOWLEDGED_AT = longPreferencesKey("db_reset_event_acknowledged_at")
    }
}

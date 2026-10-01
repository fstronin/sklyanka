package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.reminder.port.DeliveryCheckAcknowledgements
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подтверждение объявленного результата самопроверки (SKL-86, ОВ-108).
 *
 * Хранится моментом проверки, а не флагом: флаг «объявлено» запретил бы объявление
 * следующей проверки. В тех же постоянных настройках, что настройки напоминаний:
 * значение атомарно, в выборках не участвует и переживает переустановку процесса.
 */
@Singleton
class DeliveryCheckAcknowledgementsStore @Inject constructor(private val store: DataStore<Preferences>) :
    DeliveryCheckAcknowledgements {

    override suspend fun announced(): Instant? = store.data
        .first()[ANNOUNCED]
        ?.let(Instant::ofEpochMilli)

    override suspend fun remember(dueAt: Instant) {
        store.edit { it[ANNOUNCED] = dueAt.toEpochMilli() }
    }

    private companion object {
        val ANNOUNCED = longPreferencesKey("delivery_check_announced")
    }
}

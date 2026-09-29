package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Активный профиль в постоянных настройках приложения.
 *
 * Не состояние навигации и не поле в памяти процесса (ПРФ-17): приложение обязано
 * открыться в том профиле, который был активен на момент выхода, — в том числе после
 * перезагрузки устройства (ПРФ-18).
 */
@Singleton
class ActiveProfileDataStore @Inject constructor(private val store: DataStore<Preferences>) {

    val storedProfileId: Flow<String?> = store.data.map { it[KEY] }

    suspend fun read(): String? = storedProfileId.first()

    suspend fun write(profileId: String) {
        store.edit { it[KEY] = profileId }
    }

    private companion object {
        val KEY = stringPreferencesKey("active_profile_id")
    }
}

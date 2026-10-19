package ru.stohastika.sklyanka.data.privacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.privacy.PrivacyConsentStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отметка о принятии политики в общем файле постоянных настроек.
 *
 * Ключ один: приложению нужно знать только «предложение было принято», а не когда
 * и на какой версии политики. Дата первого запуска не хранится — она не нужна ни одному
 * экрану, а лишняя запись о человеке противоречит обещанию «данные только на устройстве
 * и только для учёта приёма».
 */
@Singleton
class PrivacyConsentDataStore @Inject constructor(private val preferences: DataStore<Preferences>) :
    PrivacyConsentStore {

    override suspend fun acknowledged(): Boolean = preferences.data.first()[ACKNOWLEDGED] ?: false

    override suspend fun acknowledge() {
        preferences.edit { it[ACKNOWLEDGED] = true }
    }

    companion object {
        /** Ключ отметки; проверяется тестом, чтобы рядом не появилось второго состояния. */
        internal val ACKNOWLEDGED: Preferences.Key<Boolean> = booleanPreferencesKey("privacy_acknowledged")
    }
}

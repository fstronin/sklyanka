package ru.heuri.sklyanka.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import ru.heuri.sklyanka.domain.onboarding.port.OnboardingStateStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище состояния онбординга (`TDD.md §4`).
 *
 * Здесь лежат ровно два булевых значения, и оба — про то, чего в системе нет:
 *
 * | Ключ | Что означает |
 * |---|---|
 * | `onboarding_seen` | человек уже видел экран разрешений — прошёл или пропустил (НАП-21) |
 * | `autostart_confirmed_manually` | человек подтвердил, что настроил автозапуск EMUI (НАП-20) |
 *
 * **Состояний разрешений здесь нет и не появится** (НФТ-52). Сохранённое «разрешение выдано»
 * описывает прошлое и устаревает ровно в тот момент, когда человек отозвал разрешение
 * в системных настройках, — то есть именно тогда, когда правда нужна. Отсутствие таких
 * ключей проверяется тестом, а не обещанием: соблазн «закешировать, чтобы экран не мигал»
 * возникает при первой же правке.
 *
 * Живёт в `core-data`, рядом с остальными постоянными настройками, а не в `app`: настройки —
 * это хранилище, и `DataStore` подключён здесь.
 */
@Singleton
class OnboardingStateDataStore @Inject constructor(private val preferences: DataStore<Preferences>) :
    OnboardingStateStore {

    override suspend fun seen(): Boolean = preferences.data.first()[SEEN] ?: false

    override suspend fun markSeen() {
        preferences.edit { it[SEEN] = true }
    }

    override suspend fun autostartConfirmed(): Boolean = preferences.data.first()[AUTOSTART_CONFIRMED] ?: false

    override suspend fun setAutostartConfirmed(confirmed: Boolean) {
        preferences.edit { it[AUTOSTART_CONFIRMED] = confirmed }
    }

    companion object {
        /** Человек видел экран разрешений. Разницы «прошёл» и «пропустил» здесь нет (НАП-21). */
        val SEEN: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_seen")

        /** Подтверждение автозапуска человеком — единственное, что об этом шаге известно (НАП-20). */
        val AUTOSTART_CONFIRMED: Preferences.Key<Boolean> =
            booleanPreferencesKey("autostart_confirmed_manually")

        /** Оба ключа: опора теста «состояний разрешений в хранилище нет». */
        val KEYS: Set<String> = setOf(SEEN.name, AUTOSTART_CONFIRMED.name)
    }
}

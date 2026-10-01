package ru.stohastika.sklyanka.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище состояния онбординга (`TDD.md §4`).
 *
 * Здесь лежат только булевы значения, и все — про то, чего в системе нет:
 *
 * | Ключ | Что означает |
 * |---|---|
 * | `onboarding_seen` | человек уже видел экран разрешений — прошёл или пропустил (НАП-21) |
 * | `app_startup_confirmed` | человек подтвердил, что настроил «Запуск приложений» (НАП-20) |
 * | `battery_saving_confirmed` | человек подтвердил, что снял ограничения энергосбережения |
 * | `recents_pin_confirmed` | человек подтвердил, что закрепил приложение в недавних |
 * | `autostart_confirmed_manually` | прежнее единственное подтверждение автозапуска |
 *
 * **Состояний разрешений здесь нет и не появится** (НФТ-52). Сохранённое «разрешение выдано»
 * описывает прошлое и устаревает ровно в тот момент, когда человек отозвал разрешение
 * в системных настройках, — то есть именно тогда, когда правда нужна. Отсутствие таких
 * ключей проверяется тестом, а не обещанием: соблазн «закешировать, чтобы экран не мигал»
 * возникает при первой же правке.
 *
 * **Перенос прежнего подтверждения.** До этой задачи существовало одно подтверждение —
 * автозапуска (автозапуск и энергосбережение были слиты в одну карточку). [appStartupConfirmed]
 * читает новый ключ, а если его нет — старый (`contains` отличает «нет» от «снято»);
 * запись идёт только в новый ключ, поэтому после первого касания legacy перестаёт влиять.
 * Старый ключ не удаляется: он — единственный след того, что человек подтверждал раньше,
 * и стоит байтов.
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

    override suspend fun appStartupConfirmed(): Boolean {
        val current = preferences.data.first()
        // Значение нового ключа, если ключ есть; иначе — legacy-подтверждение автозапуска.
        // `false` в новом ключе — это «снято», и legacy его не перебивает.
        return current[APP_STARTUP_CONFIRMED] ?: current[LEGACY_AUTOSTART_CONFIRMED] ?: false
    }

    override suspend fun setAppStartupConfirmed(confirmed: Boolean) {
        preferences.edit { it[APP_STARTUP_CONFIRMED] = confirmed }
    }

    override suspend fun batterySavingConfirmed(): Boolean = preferences.data.first()[BATTERY_SAVING_CONFIRMED] ?: false

    override suspend fun setBatterySavingConfirmed(confirmed: Boolean) {
        preferences.edit { it[BATTERY_SAVING_CONFIRMED] = confirmed }
    }

    override suspend fun recentsPinConfirmed(): Boolean = preferences.data.first()[RECENTS_PIN_CONFIRMED] ?: false

    override suspend fun setRecentsPinConfirmed(confirmed: Boolean) {
        preferences.edit { it[RECENTS_PIN_CONFIRMED] = confirmed }
    }

    companion object {
        /** Человек видел экран разрешений. Разницы «прошёл» и «пропустил» здесь нет (НАП-21). */
        val SEEN: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_seen")

        /** Подтверждение «Запуска приложений» человеком (НАП-20). */
        val APP_STARTUP_CONFIRMED: Preferences.Key<Boolean> = booleanPreferencesKey("app_startup_confirmed")

        /** Подтверждение «Энергосбережения» человеком (НАП-20). */
        val BATTERY_SAVING_CONFIRMED: Preferences.Key<Boolean> = booleanPreferencesKey("battery_saving_confirmed")

        /** Подтверждение «Закрепления в недавних» человеком (НАП-20). */
        val RECENTS_PIN_CONFIRMED: Preferences.Key<Boolean> = booleanPreferencesKey("recents_pin_confirmed")

        /**
         * Прежнее единственное подтверждение автозапуска.
         *
         * Читается как значение [APP_STARTUP_CONFIRMED], пока нового ключа нет; не удаляется,
         * чтобы обновление не «забыло» человека, прошедшего старую настройку.
         */
        val LEGACY_AUTOSTART_CONFIRMED: Preferences.Key<Boolean> =
            booleanPreferencesKey("autostart_confirmed_manually")

        /** Все ключи: опора теста «состояний разрешений в хранилище нет». */
        val KEYS: Set<String> = setOf(
            SEEN.name,
            APP_STARTUP_CONFIRMED.name,
            BATTERY_SAVING_CONFIRMED.name,
            RECENTS_PIN_CONFIRMED.name,
            LEGACY_AUTOSTART_CONFIRMED.name,
        )
    }
}

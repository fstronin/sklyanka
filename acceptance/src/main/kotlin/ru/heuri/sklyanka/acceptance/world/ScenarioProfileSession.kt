package ru.heuri.sklyanka.acceptance.world

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.domain.profile.ProfileContext
import ru.heuri.sklyanka.domain.profile.ProfileContextProvider
import ru.heuri.sklyanka.domain.profile.SwitchOutcome
import ru.heuri.sklyanka.domain.profile.SwitchProfileUseCase
import ru.heuri.sklyanka.domain.profile.SwitchReason
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository

/**
 * Активный профиль сценария как состояние приложения, переживающее перезапуск
 * (`ПРФ-17`, `ПРФ-18`).
 *
 * Настоящее хранилище — `DataStore` в `core-data`, и в модуле приёмки его нет: Android
 * сюда не приезжает (`ADR-0007`). Модель честна в том, что именно проверяется сценарием
 * «После перезапуска активный профиль не меняется молча»: **записанное значение и
 * поставщик контекста — разные вещи**. Переключение идёт настоящим [SwitchProfileUseCase]
 * и кладёт идентификатор в хранилище; [restart] выбрасывает поставщика и собирает нового
 * над тем же записанным значением. Поставщик собственного состояния не держит вовсе,
 * поэтому перезапуск не может вернуть его к `is_self` — ровно тот дефект, от которого
 * защищает сценарий.
 *
 * Хранилище здесь — сам мир сценария: [ScenarioProfiles.active] и есть записанное
 * значение, больше ему лежать негде. Проверка «значение переживает смерть процесса»
 * на настоящем `DataStore` — работа устройства, а не JVM-прогона.
 */
class ScenarioProfileSession(private val profiles: ScenarioProfiles, private val store: ProfileRepository) {

    private val active = ScenarioActiveProfile()
    private var provider: ProfileContextProvider = providerOver(active)

    /** Переключение тем же сценарием домена, что и в продукте; изменяет и мир сценария. */
    fun switchTo(name: String, reason: SwitchReason): SwitchOutcome =
        runBlocking { SwitchProfileUseCase(store, active)(profiles.get(name).id, reason) }

    /** Перезапуск приложения: поставщик контекста собирается заново над тем же хранилищем. */
    fun restart() {
        provider = providerOver(active)
    }

    /** Контекст активного профиля здесь и сейчас — то, что видит корень приложения. */
    fun context(): ProfileContext = runBlocking {
        requireNotNull(provider.snapshot()) { "the scenario has no active profile at all" }
    }

    private fun providerOver(active: ActiveProfileRepository): ProfileContextProvider =
        object : ProfileContextProvider {

            override val current: Flow<ProfileContext> get() = flow { contextOf()?.let { emit(it) } }

            override suspend fun snapshot(): ProfileContext? = contextOf()

            private suspend fun contextOf(): ProfileContext? {
                val id = active.current()
                return profiles.byId(id)?.let(ProfileContext::of)
            }
        }

    /**
     * Хранилище активного профиля в памяти.
     *
     * Записанное значение — [ScenarioProfiles.active]: профиль выбирается доменным
     * сценарием переключения, а не полем поставщика. Значение по умолчанию (первый
     * запуск) — профиль владельца.
     */
    private inner class ScenarioActiveProfile : ActiveProfileRepository {

        override val activeProfileId: Flow<String> get() = flow { emit(current()) }

        override suspend fun current(): String {
            if (profiles.active.isNotEmpty()) return profiles.get(profiles.active).id
            return requireNotNull(profiles.self()) { "the scenario has not created a single profile" }.id
        }

        override suspend fun switchTo(profileId: String) {
            val name = requireNotNull(profiles.byId(profileId)) { "unknown profile: $profileId" }.name
            profiles.use(name)
        }

        override suspend fun switchTo(profileId: String, reason: SwitchReason) = switchTo(profileId)
    }
}

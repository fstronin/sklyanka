package ru.stohastika.sklyanka.data.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище решения по аналитике в отдельном файле `sklyanka_analytics_consent` (TDD §7.1).
 *
 * Отдельный файл, а не общие ключи настроек: согласие принадлежит установке, файлом
 * переноса не переносится и не сбрасывается заменой данных. Отсутствие ключа — «не спрошено»;
 * незнакомое значение — тоже «не спрошено»: безопасное умолчание важнее догадки, а
 * повреждённый файл не должен выглядеть согласием.
 */
@Singleton
class AnalyticsConsentDataStore @Inject constructor(
    @AnalyticsConsentPreferences private val preferences: DataStore<Preferences>,
) : AnalyticsConsentStore {

    override fun observe(): Flow<AnalyticsConsent> = preferences.data.map(::consentOf)

    override suspend fun read(): AnalyticsConsent = consentOf(preferences.data.first())

    override suspend fun record(decision: AnalyticsConsentDecision, materialVersion: Int) {
        preferences.edit {
            it[DECISION] = decision.name
            it[MATERIAL_VERSION] = materialVersion
        }
    }

    private fun consentOf(stored: Preferences): AnalyticsConsent = AnalyticsConsent(
        decision = stored[DECISION]?.let(::decisionOf) ?: AnalyticsConsentDecision.NOT_ASKED,
        materialVersion = stored[MATERIAL_VERSION] ?: 0,
    )

    private fun decisionOf(raw: String): AnalyticsConsentDecision =
        AnalyticsConsentDecision.entries.firstOrNull { it.name == raw } ?: AnalyticsConsentDecision.NOT_ASKED

    companion object {
        /** Решение человека: `ACCEPTED` или `DECLINED`; ключа нет — «не спрошено». */
        val DECISION: Preferences.Key<String> = stringPreferencesKey("analytics_decision")

        /** Версия предмета согласия, при которой принято решение. */
        val MATERIAL_VERSION: Preferences.Key<Int> = intPreferencesKey("analytics_material_version")

        /** Все ключи файла: опора теста «перенос не пишет сюда» и проверки состава. */
        val KEYS: Set<String> = setOf(DECISION.name, MATERIAL_VERSION.name)
    }
}

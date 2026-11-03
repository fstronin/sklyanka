package ru.stohastika.sklyanka.data.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsConsentStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Решение об аналитике — в общем файле постоянных настроек (ПМ-25, TDD §3.1).
 *
 * Хранилище то же, что у политики и оформления (`DataModule.providePreferences`), и по той же
 * причине: согласие — свойство не данных человека, а установки. В Room оно не едет намеренно:
 * файл переноса не должен перевозить согласие с одного устройства на другое (ПЕР-37), а строка
 * в базе означала бы, что однажды её положат и в выгрузку.
 *
 * Ключ один: `allowed` либо `declined`. Отсутствие ключа — «ещё не спрашивали», и это не то же
 * самое, что отказ: первое означает «показать экран», второе — «не показывать и ничего
 * не отправлять». Незнакомая или испорченная запись читается как «ещё не спрашивали»
 * (как тема в [ru.stohastika.sklyanka.data.prefs.AppearancePreferencesDataStore]): порча
 * не имеет права ни включить отправку молча, ни запереть экран вопроса.
 *
 * Версия текста согласия здесь не хранится: задача знает только выбор человека (ПМ-27 вне рамок).
 * Поэтому и обратной записи нет — [`decide`] принимает лишь `allowed` или `declined`, и стереть
 * отказ, вернув установку в «ещё не спрашивали», нельзя.
 */
@Singleton
class AnalyticsConsentDataStore @Inject constructor(private val preferences: DataStore<Preferences>) :
    AnalyticsConsentStore {

    override val consent: Flow<AnalyticsConsentState> = preferences.data.map { read(it[CONSENT]) }

    override suspend fun decide(state: AnalyticsConsentState) {
        require(state != AnalyticsConsentState.NOT_ASKED) {
            "consent is recorded only once the question has been answered"
        }
        preferences.edit { it[CONSENT] = encode(state) }
    }

    private fun read(stored: String?): AnalyticsConsentState = when (stored) {
        ALLOWED -> AnalyticsConsentState.ALLOWED
        DECLINED -> AnalyticsConsentState.DECLINED
        else -> AnalyticsConsentState.NOT_ASKED
    }

    private fun encode(state: AnalyticsConsentState): String = when (state) {
        AnalyticsConsentState.ALLOWED -> ALLOWED
        AnalyticsConsentState.DECLINED -> DECLINED
        AnalyticsConsentState.NOT_ASKED -> error("a missing decision cannot be stored")
    }

    companion object {
        /** Ключ решения; проверяется тестом, чтобы рядом не появилось второго состояния. */
        internal val CONSENT: Preferences.Key<String> = stringPreferencesKey("analytics_consent")

        private const val ALLOWED = "allowed"

        private const val DECLINED = "declined"
    }
}

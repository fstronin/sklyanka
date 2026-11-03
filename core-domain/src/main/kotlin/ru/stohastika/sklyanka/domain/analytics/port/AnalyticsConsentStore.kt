package ru.stohastika.sklyanka.domain.analytics.port

import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState

/**
 * Память о согласии на аналитику (ПМ-25, TDD §3.1).
 *
 * Согласие — состояние настройки, а не отдельная сущность: одно на установку, не на профиль
 * (ПМ-27). Отсутствие решения — [AnalyticsConsentState.NOT_ASKED]; испорченное значение
 * читается так же, потому что порча не имеет права ни включать отправку, ни запирать экран.
 */
interface AnalyticsConsentStore {

    /** Поток — для строки настроек: строка меняется без перезахода на экран. */
    val consent: Flow<AnalyticsConsentState>

    /** Записать решение человека. */
    suspend fun decide(state: AnalyticsConsentState)
}

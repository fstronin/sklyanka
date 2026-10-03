package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.flow.Flow

/**
 * Хранилище решения по аналитике (ПМ-27, TDD §7.1).
 *
 * Отдельно от настроек приложения: согласие принадлежит установке, не участвует
 * в переносе данных и не сбрасывается заменой содержимого. Отсутствие записи означает
 * «не спрошено» — состояние по умолчанию, в том числе после обновления приложения.
 */
interface AnalyticsConsentStore {

    /** Поток решения: настройки и старт приложения читают одно и то же значение. */
    fun observe(): Flow<AnalyticsConsent>

    /** Текущее решение. */
    suspend fun read(): AnalyticsConsent

    /** Записать решение вместе с версией предмета согласия, при которой оно принято. */
    suspend fun record(decision: AnalyticsConsentDecision, materialVersion: Int)
}

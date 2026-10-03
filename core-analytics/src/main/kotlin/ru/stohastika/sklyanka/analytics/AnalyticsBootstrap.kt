package ru.stohastika.sklyanka.analytics

import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import javax.inject.Inject

/**
 * Восстановление состояния контура при старте приложения (TDD §4, §10).
 *
 * Приложение живёт между запусками, а решение и версия согласия — на диске: при каждом
 * старте контур приводится к тому, что записано. Ничего не решает само — только
 * перечитывает решение и передаёт его гейту; после отказа или устаревшей версии
 * активации не происходит.
 */
class AnalyticsBootstrap @Inject constructor(
    private val consent: AnalyticsConsentStore,
    private val analytics: Analytics,
) {

    suspend fun restore() {
        analytics.applyConsent(consent.read())
    }
}

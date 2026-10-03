package ru.stohastika.sklyanka.acceptance.world

import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent

/**
 * Канал аналитики сценария: события остаются в памяти для проверок приёмки.
 *
 * Настоящий гейт согласия (`ConsentedAnalytics`) живёт в `core-domain`, а здесь —
 * только приёмник. Сценарий, которому нужно «до согласия — ничего», проверяет гейт
 * отдельно (SKL-82); этой задаче важно, что событие удаления/отмены уходит, и уходит
 * без доменных свойств.
 */
class ScenarioAnalytics : Analytics {

    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    override suspend fun applyConsent(consent: AnalyticsConsent) = Unit
}

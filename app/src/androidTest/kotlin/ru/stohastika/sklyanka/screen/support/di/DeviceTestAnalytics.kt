package ru.stohastika.sklyanka.screen.support.di

import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.AnalyticsSink
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Записывающий sink аналитики в тестовом графе (SKL-97, TDD §4.3).
 *
 * Гейт согласия остаётся настоящим (`ConsentedAnalytics`), вендорский SDK в проверках
 * не активируется вовсе; записанные события при необходимости видит проверка.
 */
internal object RecordingAnalyticsSink : AnalyticsSink {

    private val recorded = CopyOnWriteArrayList<AnalyticsEvent>()

    val events: List<AnalyticsEvent> get() = recorded.toList()

    override suspend fun activate() = Unit

    override suspend fun deactivate() = Unit

    override fun send(event: AnalyticsEvent) {
        recorded += event
    }
}

/** Тихий журнал контура: проверки не пишут в logcat продуктовыми каналами. */
internal object SilentAnalyticsLog : AnalyticsLog {

    override fun activated() = Unit

    override fun deactivated() = Unit

    override fun unavailable(reason: String) = Unit

    override fun debugSuppressed() = Unit

    override fun sendFailed(reason: String) = Unit
}

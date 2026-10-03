package ru.stohastika.sklyanka.screen.support.di

import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog

/** Тихий журнал контура: проверки не пишут в logcat продуктовыми каналами. */
internal object SilentAnalyticsLog : AnalyticsLog {

    override fun activated() = Unit

    override fun deactivated() = Unit

    override fun unavailable(reason: String) = Unit

    override fun debugSuppressed() = Unit

    override fun sendFailed(reason: String) = Unit
}

package ru.stohastika.sklyanka.domain.analytics

/**
 * Нулевой канал аналитики: событий не отправляет и контур не активирует.
 *
 * Нужен там, где `Analytics` подставляется вручную, — в стендах и юнит-тестах, которым
 * аналитика не предмет проверки; продукт всегда получает адаптер вендора через Hilt
 * (`core-analytics`). Умолчание именно нулевое: забытая подстановка молчит, а не шлёт
 * события мимо гейта согласия.
 */
object NoOpAnalytics : Analytics {

    override fun track(event: AnalyticsEvent) = Unit

    override suspend fun applyConsent(consent: AnalyticsConsent) = Unit
}

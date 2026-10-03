package ru.stohastika.sklyanka.domain.experiment

import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent

/**
 * Нулевой канал экспериментов: всегда базовый вариант, вендор не вызывается.
 *
 * Нужен там, где `Experiments` подставляется вручную, — в стендах и юнит-тестах,
 * которым эксперименты не предмет проверки; продукт всегда получает гейт через Hilt
 * (`core-analytics`). Умолчание именно базовое: забытая подстановка показывает продукт,
 * а не случайный вариант.
 */
object NoOpExperiments : Experiments {

    override fun variantOf(experiment: ExperimentId): VariantKey = experiment.base

    override suspend fun applyConsent(consent: AnalyticsConsent) = Unit

    override suspend fun refresh() = Unit
}

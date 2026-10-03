package ru.stohastika.sklyanka.domain.experiment

import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent

/**
 * Единственный канал A/B-экспериментов (SKL-83, TDD §7).
 *
 * Поверхности читают вариант синхронно ([variantOf]) и никогда не ждут сеть: получение
 * варианта асинхронно и не имеет права задерживать сценарий (НФТ-16а). Разрешённость
 * держит реализация-гейт: до согласия, после отказа, после выключения и в debug она
 * возвращает базовый вариант и не трогает вендора; домен и экраны знают только этот порт.
 */
interface Experiments {

    /** Действующий вариант эксперимента: базовый, если варианта нет или он запрещён. */
    fun variantOf(experiment: ExperimentId): VariantKey

    /** Применить решение согласия: включить/выключить применение и загрузить или сбросить кэш. */
    suspend fun applyConsent(consent: AnalyticsConsent)

    /** Обновить варианты у вендора; при запрете — ничего не делает. */
    suspend fun refresh()
}

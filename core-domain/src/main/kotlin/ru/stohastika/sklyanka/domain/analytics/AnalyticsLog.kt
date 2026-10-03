package ru.stohastika.sklyanka.domain.analytics

/**
 * Локальный журнал аналитического контура (НФТ-19, SKL-82).
 *
 * Отвечает на вопрос «почему нет событий» без сети и без тел событий: только перечисления
 * и классы сбоев. Наружу не уходит — журнал остаётся на устройстве, как и все остальные
 * журналы продукта. Реализация на `android.util.Log` живёт в `core-analytics`.
 */
interface AnalyticsLog {

    fun activated()

    fun deactivated()

    fun unavailable(reason: String)

    fun debugSuppressed()

    fun sendFailed(reason: String)

    /** Обновление вариантов A/B: сколько значений получено (SKL-83). */
    fun experimentsUpdated(count: Int)

    /** A/B-контур недоступен или не обновился: причина классом сбоя, без тел данных. */
    fun experimentsUnavailable(reason: String)
}

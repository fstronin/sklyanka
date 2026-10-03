package ru.stohastika.sklyanka.domain.experiment

/**
 * Технический канал вендора, за которым стоит AGC Remote Configuration (SKL-83, TDD §10).
 *
 * Порт [Experiments] решает, можно ли запрашивать и применять; vendor только исполняет.
 * Разделение даёт проверку «до согласия — ноль вызовов SDK» на JVM с подменённым
 * каналом — и в доменных тестах, и в сценарной приёмке. Реализация (Huawei AppGallery
 * Connect) живёт в `core-analytics`; здесь — только контракт, чтобы правило гейта
 * оставалось доменным и проверяемым без Android.
 */
interface ExperimentVendor {

    /** Назначенные варианты каталога; отсутствие ключа означает базовый вариант. */
    suspend fun fetchVariants(): Map<ExperimentId, VariantKey>
}

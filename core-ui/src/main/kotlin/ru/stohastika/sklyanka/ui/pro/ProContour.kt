package ru.stohastika.sklyanka.ui.pro

/**
 * Контур интерфейса: показывает ли сборка платные признаки (метки «Про», входы покупки).
 *
 * Порт для поверхностей вне композиции приложения — например, экрана конфигурации виджета,
 * который живёт в отдельной активности и не наследует композицию `app`. Реализации —
 * по вариантам сборки: в бесплатной `uiEnabled = false`, в платной — `true` (Р-86, ADR-0016).
 *
 * ```kotlin
 * CompositionLocalProvider(LocalProUiEnabled provides contour.uiEnabled) { … }
 * ```
 */
interface ProContour {
    /** Виден ли в интерфейсе платный контур; по умолчанию — значение `LocalProUiEnabled`. */
    val uiEnabled: Boolean
}

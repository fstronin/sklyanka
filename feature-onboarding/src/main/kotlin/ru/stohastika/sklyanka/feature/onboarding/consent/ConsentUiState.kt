package ru.stohastika.sklyanka.feature.onboarding.consent

import ru.stohastika.sklyanka.domain.analytics.ConsentSource

/**
 * Состояние экрана согласия (`03-screens.md`, ОВ-123).
 *
 * Источник показа решает подписи и исходы: первый запуск и повторное информирование
 * уводят дальше по цепочке первого запуска, вход из настроек — назад в настройки,
 * а отказная кнопка там называется «Оставить отключённой» (ПМ-25 п. 4).
 */
data class ConsentUiState(val source: ConsentSource = ConsentSource.FIRST_LAUNCH) {

    /** Экран открыт включением аналитики из настроек (ПМ-25 п. 4). */
    val fromSettings: Boolean get() = source == ConsentSource.SETTINGS
}

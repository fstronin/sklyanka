package ru.stohastika.sklyanka.feature.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Согласие на аналитику как зависимость экрана настроек (SKL-82).
 *
 * Экран не знает доменных сценариев по отдельности: переключатель читает поток
 * «включено/выключено» и просит выключить; включение — не его работа, оно открывает
 * экран согласия (ПМ-25 п. 4). Нулевое умолчание нужно ручной сборке в тестах и стендах:
 * забытая подстановка молчит и показывает «выключено», а не ложное «включено».
 */
data class AnalyticsConsentControl(
    val observeEnabled: () -> Flow<Boolean>,
    val setEnabled: suspend (Boolean) -> Unit,
) {
    companion object {
        val NONE = AnalyticsConsentControl(observeEnabled = { flowOf(false) }, setEnabled = {})
    }
}

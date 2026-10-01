package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import io.cucumber.java.ru.Тогда
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld

/**
 * Экранные шаги приёмки SKL-85: пояснение системного индикатора под следующим
 * напоминанием (НАП-42, ОВ-110).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране «Проверка напоминаний»:
 * состояние собирает настоящий `DiagnosticsViewModel` на мире сценария, а текст берётся
 * из документа приёмки дословно и сверяется с ресурсом экрана.
 */
@OptIn(ExperimentalTestApi::class)
class IndicatorExplanationSteps(private val world: AcceptanceWorld) {

    @Тогда("^под следующим напоминанием сказано: «(.+)»$")
    fun underTheNextReminder(text: String) {
        // Стенд раскрывает «Для поддержки»: пояснение живёт в техническом блоке рядом
        // со строкой следующего напоминания (DiagnosticsScreen.NextPlanned).
        DiagnosticsStand.ask(world) { onNodeWithText(text).assertIsDisplayed() }
    }
}

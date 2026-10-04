package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import io.cucumber.java.ru.Когда
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS

/**
 * Экранные шаги приёмки диагностики напоминаний (SKL-20).
 *
 * Первый уровень «Проверки напоминаний» переработан задачей SKL-102 (ОВ-126): журнал,
 * счётчики, «Проверить сейчас» и построчные разрешения убраны, и их подача проверяется
 * шагами SKL-102. Здесь остаётся только открытие экрана — общее для устройственных
 * и ручных сценариев соседних задач.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class DiagnosticsScreenSteps(private val world: AcceptanceWorld) {

    @Когда("^человек открывает диагностику напоминаний$")
    fun thePersonOpensTheDiagnostics() {
        DiagnosticsStand.ask(world) { assertTag(TAG_DIAGNOSTICS) }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertTag(tag: String) = onNodeWithTag(tag).assertIsDisplayed()
}

package ru.heuri.sklyanka.acceptance.reminder

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.И
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.profile.ProfileScreen
import ru.heuri.sklyanka.acceptance.profile.TodayFromWorld
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

/**
 * Экранные шаги приёмки деградации напоминаний (SKL-13, группа `@degradation` с `@screen`).
 *
 * Шаги живут в `app`, потому что проверяют сразу все экраны приложения
 * (`SKL-26/TDD.md §4.3`): они живут в разных модулях, и единственное место, где все они
 * существуют одновременно, — `app`. Экраны настоящие, состояния — из мира сценария.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class ReminderDegradationSteps(private val world: AcceptanceWorld) {

    /**
     * Отказ в разрешениях не ломает ни один экран.
     *
     * Каждый из шести экранов собирается на мире сценария и обязан что-то показать:
     * ручной учёт работает независимо от выдачи разрешений, и экрану не из-за чего
     * становиться недоступным.
     */
    @И("^ни один экран приложения не становится недоступным$")
    fun noScreenBecomesUnavailable() {
        val today = TodayFromWorld.state(world)
        val screens = listOf(
            ProfileScreen.Today,
            ProfileScreen.MedicationList,
            ProfileScreen.MedicationCard,
            ProfileScreen.Archive,
            ProfileScreen.History,
            ProfileScreen.MedicationFormScreen,
        )
        screens.forEach { screen ->
            runComposeUiTest {
                setContent { SklyankaTheme { screen.Content(today) } }
                waitForIdle()
                val texts = textsOf(onRoot(useUnmergedTree = true).fetchSemanticsNode()).filter { it.isNotBlank() }
                assertTrue(texts.isNotEmpty(), "the screen ${screen.name} must stay available")
            }
        }
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::textsOf)
}

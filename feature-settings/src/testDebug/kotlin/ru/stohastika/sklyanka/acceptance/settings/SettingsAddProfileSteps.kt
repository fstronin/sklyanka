package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.TAG_ADD_PROFILE

/**
 * Экранные шаги приёмки выключенной строки «Добавить профиль» (SKL-58, группа `@screen`).
 *
 * Шаги говорят об одном экране и потому живут в `feature-settings` (`SKL-26/TDD.md §4.3`).
 * Состояние собирает тот же стенд [SettingsScreenProbe], что у настроек: строка — часть
 * того же экрана, и второй стенд разошёлся бы с первым.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsAddProfileSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Тогда("^в разделе есть строка «Добавить профиль»$")
    fun theSectionHasTheAddProfileRow() {
        SettingsScreenProbe.ask(world) {
            assertTrue(
                onAllNodesWithText(string(R.string.settings_profile_add), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty(),
                "the add-profile row must stay on the screen by the mockup",
            )
        }
    }

    @Но("^строка не нажимается$")
    fun theRowIsNotClickable() {
        SettingsScreenProbe.ask(world) {
            val node = onNodeWithTag(TAG_ADD_PROFILE, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                !node.config.contains(SemanticsActions.OnClick),
                "the disabled add-profile row must carry no click action",
            )
            assertTrue(
                node.config.contains(SemanticsProperties.Disabled),
                "the row must be announced as disabled, not silently dead",
            )
        }
    }

    @И("^под строкой стоит «Будет доступно в ближайшем обновлении»$")
    fun theRowCarriesTheExplanation() {
        SettingsScreenProbe.ask(world) {
            val title = onNodeWithText(string(R.string.settings_profile_add), useUnmergedTree = true)
                .fetchSemanticsNode()
            val explanation = onNodeWithText(string(R.string.settings_profile_add_unavailable), useUnmergedTree = true)
                .fetchSemanticsNode()
            assertTrue(
                explanation.boundsInRoot.top >= title.boundsInRoot.bottom,
                "the explanation must stand under the add-profile row",
            )
        }
    }

    private fun string(id: Int): String = context.getString(id)
}

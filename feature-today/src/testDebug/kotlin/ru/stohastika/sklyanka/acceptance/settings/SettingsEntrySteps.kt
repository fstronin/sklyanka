package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.today.TodayScreenProbe
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR

/**
 * Экранные шаги SKL-20 про единственный вход в настройки (`@defect`).
 *
 * Дефект навигации был в кнопке-шестерёнке шапки «Сегодня», дублировавшей вкладку нижней
 * панели; шестерёнка снята. Проверяется это отсутствием второй цели с именем «Настройки»
 * вне нижней панели: аватар профиля в шапке — индикатор, названный «Профили», и он ведёт
 * в список профилей (ПРФ-19, решение по `ОВ-54`), а не дублирует вкладку.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsEntrySteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Когда("^человек открывает экран «Сегодня»$")
    fun thePersonOpensToday() {
        TodayScreenProbe.ask(world) { test -> test.onNodeWithTag(TAG_TODAY_SCREEN).assertIsDisplayed() }
    }

    @Тогда("^в шапке экрана кнопки-шестерёнки нет$")
    fun theHeaderHasNoGear() {
        TodayScreenProbe.ask(world) { test ->
            val bar = test.onNodeWithTag(TAG_BOTTOM_BAR).fetchSemanticsNode()
            val settingsNamed = test.onAllNodesWithContentDescription(settingsLabel()).fetchSemanticsNodes()
            assertTrue(
                settingsNamed.isNotEmpty(),
                "the bottom-bar tab itself must stay named «${settingsLabel()}»",
            )
            assertTrue(
                settingsNamed.all { it.boundsInRoot.top >= bar.boundsInRoot.top },
                "no «${settingsLabel()}» control may stand above the bottom bar — the gear is gone",
            )
            val avatar = test.onNodeWithTag(ru.stohastika.sklyanka.ui.profile.TAG_PROFILE_AVATAR).fetchSemanticsNode()
            assertTrue(avatar.boundsInRoot.top < bar.boundsInRoot.top, "the header must carry the profile avatar")
            assertEquals(
                listOf(string(CORE_UI_PROFILE_AVATAR)),
                avatar.config.getOrNull(SemanticsProperties.ContentDescription),
                "the header control is the profile indicator, not a second settings button (ОВ-54)",
            )
        }
    }

    @Тогда("^единственный вход в настройки — вкладка «Настройки» нижней панели$")
    fun theOnlyEntryIsTheBottomBarTab() {
        TodayScreenProbe.ask(world) { test ->
            val bar = test.onNodeWithTag(TAG_BOTTOM_BAR).fetchSemanticsNode()
            val named = test.onAllNodesWithContentDescription(settingsLabel()).fetchSemanticsNodes()
            assertEquals(1, named.size, "exactly one control is named «${settingsLabel()}»")
            assertTrue(
                named.single().boundsInRoot.top >= bar.boundsInRoot.top,
                "the only settings entry must be the bottom-bar tab",
            )
        }
        val intents = TodayScreenProbe.act(world) { test ->
            test.onNodeWithContentDescription(settingsLabel()).performClick()
        }
        assertTrue(intents.contains(TodayIntent.OpenSettings), "the tab must open the settings: $intents")
    }

    private fun settingsLabel(): String = context.getString(ru.stohastika.sklyanka.ui.R.string.tab_settings)

    private fun string(id: Int): String = context.getString(id)

    private companion object {
        /** Описание аватара профиля — ресурс дизайн-системы. */
        val CORE_UI_PROFILE_AVATAR: Int = ru.stohastika.sklyanka.ui.R.string.cd_profile_avatar
    }
}

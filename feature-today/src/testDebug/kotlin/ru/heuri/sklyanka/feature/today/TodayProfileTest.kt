package ru.heuri.sklyanka.feature.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.ui.component.TAG_PROFILE_LABEL
import ru.heuri.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.heuri.sklyanka.ui.profile.ProfileChrome
import ru.heuri.sklyanka.ui.profile.ProfileChromeState
import ru.heuri.sklyanka.ui.profile.TAG_PROFILE_AVATAR
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Экран «Сегодня» в контексте профиля (ПРФ-11…ПРФ-16, критерии приёмки 4–8).
 *
 * Отдельно от `TodayScreenTest`: там проверяется наполнение экрана, здесь — то, чей это
 * экран. Вопросы разные, и смешанные в одном классе они перестают читаться списком.
 *
 * Цена ошибки — отметка чужого приёма как своего, поэтому проверяется не «признак есть»,
 * а «признаки есть все сразу» и «в своём профиле нет ни одного»: пара утверждений и есть
 * требование (ПРФ-10, ПРФ-16).
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: снимочный стенд режет его по нижней кромке, и проверка
// состава видела бы не экран, а его верх (SKL-24).
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class TodayProfileTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aForeignProfileShowsEverySignOnTheTodayScreen() {
        showForeign()

        composeRule.onNodeWithTag(TAG_PROFILE_STRIPE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(bannerText()).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(UiR.string.status_marked_by_you)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(UiR.string.action_taken)).assertIsDisplayed()
    }

    @Test
    fun theOwnProfileShowsNoSignAtAll() {
        showOwner()

        composeRule.onAllNodesWithTag(TAG_PROFILE_STRIPE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(bannerText()).assertCountEquals(0)
        composeRule
            .onAllNodesWithText(context.getString(UiR.string.status_marked_by_you), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /** ПРФ-13, критерий приёмки 5: баннер не уезжает вместе со списком приёмов. */
    @Test
    @Config(qualifiers = "w390dp-h844dp-mdpi")
    fun theBannerStaysWhileTheListScrolls() {
        showForeign()
        composeRule.onNodeWithText(bannerText()).assertIsDisplayed()

        composeRule.onNode(hasScrollAction(), useUnmergedTree = true).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(bannerText()).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PROFILE_STRIPE).assertIsDisplayed()
    }

    /**
     * `ОВ-54`: аватар в шапке — индикатор, ведущий в список профилей настроек.
     *
     * Меню в шапке означало бы смену профиля в одно касание — ровно то, что ПРФ-19
     * запрещает: переключение обязано быть осознанным действием.
     */
    @Test
    fun theHeaderAvatarLeadsToTheProfileList() {
        val intents = mutableListOf<TodayIntent>()
        composeRule.setContent {
            ProfileChrome(foreign()) {
                TodayScreen(state = TodayFixtures.plainDay(markedByYou = true), onIntent = intents::add)
            }
        }

        composeRule.onNodeWithTag(TAG_PROFILE_AVATAR).performClick()

        assertEquals(listOf(TodayIntent.OpenSettings), intents)
    }

    @Test
    fun theEmptyScreenCarriesTheIndicationToo() {
        composeRule.setContent {
            ProfileChrome(foreign()) {
                TodayScreen(state = TodayUiState.Empty(TodayFixtures.DATE), onIntent = {})
            }
        }

        composeRule.onNodeWithTag(TAG_PROFILE_STRIPE).assertIsDisplayed()
        composeRule.onNodeWithText(bannerText()).assertIsDisplayed()
    }

    private fun showForeign() {
        composeRule.setContent {
            ProfileChrome(foreign()) {
                TodayScreen(state = TodayFixtures.plainDay(markedByYou = true), onIntent = {})
            }
        }
    }

    private fun showOwner() {
        composeRule.setContent {
            SklyankaTheme {
                TodayScreen(state = TodayFixtures.plainDay(), onIntent = {})
            }
        }
    }

    private fun foreign() = ProfileChromeState.Foreign(
        name = context.getString(UiR.string.preview_profile_name),
        colour = ProfileColor.Terracotta,
    )

    private fun bannerText(): String =
        context.getString(UiR.string.profile_banner, context.getString(UiR.string.preview_profile_name))
}

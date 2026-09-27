package ru.heuri.sklyanka.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.TAG_BANNER
import ru.heuri.sklyanka.ui.component.TAG_PROFILE_LABEL
import ru.heuri.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.ProfilePalette
import ru.heuri.sklyanka.ui.token.SklyankaColors

/**
 * Состав индикации чужого профиля (ПРФ-11…ПРФ-16, критерии приёмки 4–8).
 *
 * Снимки рядом (`ProfileSignsSnapshotTest`) ловят изменение картинки; здесь утверждаются
 * свойства, которые картинкой не выражаются: что признаков ровно шесть и они приходят
 * вместе, что баннер нельзя закрыть, что в своём профиле нет ни одного из них, и что
 * оформление берётся из темы, а не подставляется экраном.
 */
@RunWith(AndroidJUnit4::class)
// Лист с шапкой, баннером, карточкой и кнопкой выше окна телефона: снимочный стенд режет
// его по нижней кромке, и проверка состава видела бы только верх.
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class ProfileChromeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aForeignProfileShowsEverySignAtOnce() {
        showSheet(foreign = true)

        // 1 — цветная полоса вверху.
        composeRule.onNodeWithTag(TAG_PROFILE_STRIPE).assertIsDisplayed()
        // 3 — надпись «Профиль · Имя» над заголовком.
        composeRule.onNodeWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).assertIsDisplayed()
        // 4 — баннер с подставленным именем профиля и названным последствием.
        composeRule.onNodeWithText(bannerText()).assertIsDisplayed()
        // 6 — подпись «отметили вы» под фактическим временем отметки.
        composeRule.onNodeWithText(context.getString(R.string.status_marked_by_you)).assertIsDisplayed()
    }

    @Test
    fun theOwnProfileShowsNoneOfThem() {
        showSheet(foreign = false)

        composeRule.onAllNodesWithTag(TAG_PROFILE_STRIPE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_BANNER).assertCountEquals(0)
        composeRule
            .onAllNodesWithText(context.getString(R.string.status_marked_by_you), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /** ПРФ-13: баннер не скрывается и не сворачивается — закрыть его нечем. */
    @Test
    fun theBannerCarriesNoWayToDismissIt() {
        showSheet(foreign = true)

        val banner = composeRule.onNodeWithText(bannerText()).fetchSemanticsNode()
        assertNull(
            "the profile banner must not be dismissable (PROFILE-13)",
            banner.config.getOrElseNullable(SemanticsActions.OnClick) { null },
        )
    }

    /**
     * ПРФ-11 признак 5: акцент приходит из темы, а не подставляется экраном.
     *
     * Это утверждение о механизме, а не о цвете: пока экран читает акцент из темы, забыть
     * его нельзя — читать неоткуда. Экран, получивший цвет параметром, теряет индикацию
     * на первом же месте, куда параметр не передали.
     */
    @Test
    fun theAccentOfActionsComesFromTheThemeOfTheProfile() {
        val (foreign, owner) = bothProfiles()

        assertEquals(ProfileColor.Terracotta.color, foreign.accent)
        assertEquals(ProfilePalette.ownerAccent, owner.accent)
    }

    /** ПРФ-12, ПРФ-16: подкрашенная шапка — роль темы, у владельца она равна обычному фону. */
    @Test
    fun theTintedHeaderIsARoleOfTheTheme() {
        val (foreign, owner) = bothProfiles()

        assertEquals(owner.background, owner.profileHeader)
        assertNotEquals(foreign.background, foreign.profileHeader)
        assertEquals(Color.Transparent, owner.profileStripe)
        assertEquals(ProfileColor.Terracotta.color, foreign.profileStripe)
    }

    /** Переключение профиля перерисовывает всё оформление разом, а не по частям. */
    @Test
    fun switchingTheProfileRemovesEverySignTogether() {
        var foreign by mutableStateOf(true)
        composeRule.setContent {
            ProfileSignsSheet(if (foreign) foreignProfileChrome() else ProfileChromeState.Owner)
        }

        composeRule.onNodeWithTag(TAG_PROFILE_STRIPE).assertIsDisplayed()
        foreign = false
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(TAG_PROFILE_STRIPE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TAG_BANNER).assertCountEquals(0)
    }

    private fun showSheet(foreign: Boolean) {
        composeRule.setContent {
            ProfileSignsSheet(if (foreign) foreignProfileChrome() else ProfileChromeState.Owner)
        }
    }

    private fun bannerText(): String =
        context.getString(R.string.profile_banner, context.getString(R.string.preview_profile_name))

    /**
     * Роли цвета обоих профилей за один прогон композиции.
     *
     * Один `setContent` на тест — ограничение стенда; здесь оно кстати: пара «чужой
     * и свой» и есть проверяемое утверждение, и брать её из двух разных прогонов значило
     * бы сравнивать не то же самое.
     */
    private fun bothProfiles(): Pair<SklyankaColors, SklyankaColors> {
        var foreign: SklyankaColors? = null
        var owner: SklyankaColors? = null
        composeRule.setContent {
            ProfileChrome(ProfileChromeState.Foreign(NAME, ProfileColor.Terracotta)) {
                Capture { foreign = it }
            }
            ProfileChrome(ProfileChromeState.Owner) { Capture { owner = it } }
        }
        composeRule.waitForIdle()
        return requireNotNull(foreign) { "no colours for a foreign profile" } to
            requireNotNull(owner) { "no colours for the owner profile" }
    }

    @Composable
    private fun Capture(onColors: (SklyankaColors) -> Unit) {
        onColors(SklyankaTheme.colors)
    }

    private companion object {
        /** Имя профиля для темы: на цвета оно не влияет, а пустое имя сняло бы индикацию. */
        const val NAME = "m"
    }
}

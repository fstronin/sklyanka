package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Шапка экрана: возврат шевроном, отсутствие возврата в корне и порог компактного заголовка.
 *
 * Возврат ищется **по доступному имени**, а не по тексту: слова «Назад» на экране нет
 * (§7, Р-30), и проверка, ищущая текст, проверяла бы снятое поведение.
 */
@RunWith(AndroidJUnit4::class)
class ScreenHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theTitleFollowsTheActionRowClosely() {
        show {
            ScreenHeader(
                labels = ScreenHeaderLabels(title = TITLE),
                kind = ScreenHeaderKind.Nested,
                onBack = {},
            )
        }

        val action = composeRule.onNodeWithContentDescription(backName()).fetchSemanticsNode()
        val title = composeRule.onNodeWithText(TITLE).fetchSemanticsNode()
        val gapDp = with(composeRule.density) { (title.boundsInRoot.top - action.boundsInRoot.bottom).toDp() }
        assertTrue("the gap must stay within 4..12 dp but is $gapDp", gapDp >= 3.5.dp && gapDp <= 12.5.dp)
    }

    @Test
    fun theTopPaddingIsAppliedOnce() {
        show {
            ScreenHeader(
                labels = ScreenHeaderLabels(title = TITLE),
                kind = ScreenHeaderKind.Nested,
                onBack = {},
            )
        }

        val action = composeRule.onNodeWithContentDescription(backName()).fetchSemanticsNode()
        val topDp = with(composeRule.density) { action.boundsInRoot.top.toDp() }
        val minimum = SklyankaSizes.HeaderTopPaddingProfile
        assertTrue("the header must keep its top padding, but the row starts at $topDp", topDp >= minimum)
        assertTrue(
            "the top padding must be applied once, but the row starts at $topDp",
            topDp <= SklyankaSizes.HeaderTopPadding,
        )
    }

    /** Р-30: возврат — шеврон с доступным именем, а не надпись; цель не меньше 44 dp. */
    @Test
    fun theNestedHeaderDrawsAChevronWithTheAccessibleName() {
        show {
            ScreenHeader(
                labels = ScreenHeaderLabels(title = TITLE),
                kind = ScreenHeaderKind.Nested,
                onBack = {},
            )
        }

        val back = composeRule.onNodeWithContentDescription(backName()).fetchSemanticsNode()
        val target = with(composeRule.density) { back.size.width.toDp() }
        assertTrue("the return target must be at least 44 dp but is $target", target >= SklyankaSizes.MinTouchTarget)
        composeRule.onNodeWithText(backName()).assertDoesNotExist()
    }

    /** Модальная форма сохраняет слово «Отмена» и шеврона не получает (§7). */
    @Test
    fun theModalHeaderKeepsTheWordCancel() {
        show {
            ScreenHeader(
                labels = ScreenHeaderLabels(title = TITLE),
                kind = ScreenHeaderKind.Modal,
                onBack = {},
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(backName()).assertDoesNotExist()
    }

    /** В корне вкладки видимого возврата нет: возвращаться некуда (§7). */
    @Test
    fun theRootHeaderShowsNoReturn() {
        show {
            ScreenHeader(labels = ScreenHeaderLabels(title = TITLE), kind = ScreenHeaderKind.Root)
        }

        composeRule.onNodeWithContentDescription(backName()).assertDoesNotExist()
        composeRule.onNodeWithText(backName()).assertDoesNotExist()
    }

    /**
     * Порог компактного заголовка — геометрия: пока крупный заголовок ниже ряда, заголовка
     * в ряду нет; как только его нижняя грань ушла под ряд — появляется, и тем же порогом
     * гаснет при прокрутке назад.
     */
    @Test
    fun theCompactTitleAppearsWhenTheLargeOneLeavesTheRow() {
        val header = ScreenHeaderState()
        show { PinnedHeaderBar(labels = ScreenHeaderLabels(title = TITLE), state = header) }

        composeRule.onNodeWithTag(TAG_COMPACT_TITLE).assertDoesNotExist()

        // Крупный заголовок ниже ряда: шапка развёрнута. Нижнюю грань ряда компонент
        // сообщает сам — она равна высоте ряда (56 dp), и подставлять её числом нечем.
        composeRule.runOnIdle { header.reportTitleBottom(TITLE_BELOW_BAR) }
        composeRule.onNodeWithTag(TAG_COMPACT_TITLE).assertDoesNotExist()

        // Крупный заголовок ушёл под ряд целиком.
        composeRule.runOnIdle { header.reportTitleBottom(TITLE_UNDER_BAR) }
        composeRule.onNodeWithTag(TAG_COMPACT_TITLE).assertIsDisplayed()

        // Тем же порогом он гаснет: второго порога у шапки нет.
        composeRule.runOnIdle { header.reportTitleBottom(TITLE_BELOW_BAR) }
        composeRule.onNodeWithTag(TAG_COMPACT_TITLE).assertDoesNotExist()
    }

    /**
     * Компактный заголовок не рисуется, если не помещается целиком: многоточия у него нет,
     * и обрезанный заголовок не сообщал бы, где человек находится (§6.3, §7).
     */
    @Test
    fun aCompactTitleThatDoesNotFitIsNotDrawn() {
        val header = ScreenHeaderState()
        show { PinnedHeaderBar(labels = ScreenHeaderLabels(title = LONG_TITLE), state = header) }

        composeRule.runOnIdle { header.reportTitleBottom(TITLE_UNDER_BAR) }

        composeRule.onNodeWithTag(TAG_COMPACT_TITLE).assertDoesNotExist()
    }

    /**
     * Ряд — ровно 56 dp: поля входят в габарит, и внутри остаётся 44 dp целям нажатия (§7).
     *
     * Проверка «не меньше 56» пропускала ряд в 68 dp — поля, прибавленные к габариту
     * изнутри, росли поверх минимума, и ряд переставал совпадать с макетом. Габарит
     * задаёт дизайн-система, поэтому сверяется точное значение.
     */
    @Test
    fun thePinnedRowIsExactlyItsHeight() {
        val header = ScreenHeaderState()
        show { PinnedHeaderBar(labels = ScreenHeaderLabels(title = TITLE), state = header) }

        val bar = composeRule.onNodeWithTag(TAG_PINNED_HEADER_BAR).fetchSemanticsNode()
        val height = with(composeRule.density) { bar.size.height.toDp() }
        assertEquals("the pinned row must be exactly 56 dp but is $height", SklyankaSizes.PinnedHeaderBar, height)
    }

    @Test
    fun aLeadingSlotReplacesTheBackActionInTheSameRhythm() {
        show {
            ScreenHeader(
                labels = ScreenHeaderLabels(title = TITLE),
                kind = ScreenHeaderKind.Nested,
                leading = { Box(modifier = Modifier.size(SklyankaSizes.MinTouchTarget).testTag(LEADING)) },
                trailing = { Box(modifier = Modifier.size(SklyankaSizes.MinTouchTarget).testTag(TRAILING)) },
            )
        }

        composeRule.onNodeWithTag(LEADING).assertIsDisplayed()
        composeRule.onNodeWithTag(TRAILING).assertIsDisplayed()
        val action = composeRule.onNodeWithTag(LEADING).fetchSemanticsNode()
        val title = composeRule.onNodeWithText(TITLE).fetchSemanticsNode()
        val gapDp = with(composeRule.density) { (title.boundsInRoot.top - action.boundsInRoot.bottom).toDp() }
        assertTrue("the gap must stay within 4..12 dp but is $gapDp", gapDp >= 3.5.dp && gapDp <= 12.5.dp)
    }

    /** Длинный заголовок в развёрнутой шапке не обрезается: он переносится (§6.3). */
    @Test
    fun theLargeTitleWrapsInsteadOfBeingCut() {
        show {
            Column(modifier = Modifier.fillMaxWidth()) {
                ScreenHeader(labels = ScreenHeaderLabels(title = LONG_TITLE))
                Text(text = TITLE, modifier = Modifier.testTag(BELOW))
            }
        }

        val title = composeRule.onNodeWithText(LONG_TITLE).fetchSemanticsNode()
        val below = composeRule.onNodeWithTag(BELOW).fetchSemanticsNode()
        assertTrue(
            "the header must grow instead of cutting the title",
            below.boundsInRoot.top >= title.boundsInRoot.bottom,
        )
    }

    private fun backName(): String = context.getString(R.string.cd_back)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            SklyankaTheme { content() }
        }
    }

    private companion object {
        const val TITLE = "Header"
        const val LONG_TITLE =
            "Benachrichtigungen pruefen und Einstellungen der Erinnerungen vollstaendig anzeigen lassen"
        const val LEADING = "leading"
        const val TRAILING = "trailing"
        const val BELOW = "below"

        /** Нижняя грань крупного заголовка в координатах корня, в пикселях. */
        const val TITLE_BELOW_BAR = 1000
        const val TITLE_UNDER_BAR = 1
    }
}

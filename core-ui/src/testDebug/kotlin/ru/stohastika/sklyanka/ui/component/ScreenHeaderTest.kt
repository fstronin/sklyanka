package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import kotlin.math.abs

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

    /**
     * Р-53: слово действия и название стоят на одной горизонтальной линии, а слово —
     * на боковом поле шапки, там же, где начинается содержимое.
     *
     * До решения строка выравнивалась по верхнему краю, а кнопка центрует подпись внутри
     * своей цели 44 dp: «Отмена» уходила на 11 dp ниже названия. Слот модальной строки
     * гасит собственное поле кнопки (8 dp) смещением, поэтому слово встаёт ровно на поле.
     */
    @Test
    fun theModalActionAndTitleShareOneLineOnTheSideField() {
        show {
            ScreenHeader(
                labels = ScreenHeaderLabels(title = TITLE),
                kind = ScreenHeaderKind.Modal,
                onBack = {},
            )
        }

        // Границы шапки — её поле содержимого (тег стоит внутри собственного поля шапки),
        // поэтому боковое поле 20 dp и есть левый край этого узла.
        val header = composeRule.onNodeWithTag(TAG_SCREEN_HEADER).fetchSemanticsNode()
        val action = composeRule.onNodeWithText(cancelName(), useUnmergedTree = true).fetchSemanticsNode()
        val title = composeRule.onNodeWithText(TITLE, useUnmergedTree = true).fetchSemanticsNode()
        val lineShift = abs(action.boundsInRoot.center.y - title.boundsInRoot.center.y)
        assertTrue(
            "the action and the title must share one horizontal line, but their centres differ by $lineShift px",
            lineShift <= 1f,
        )
        val fieldShift = abs(action.boundsInRoot.left - header.boundsInRoot.left)
        assertTrue(
            "the action word must start on the side field at ${header.boundsInRoot.left} px, " +
                "but starts at ${action.boundsInRoot.left} px",
            fieldShift <= 1f,
        )
    }

    /**
     * Р-53: пустой слот модальной строки не заполняется ничем.
     *
     * Заглушка справа от названия существовала ради поддельной центровки (V-15); центровки
     * в правиле больше нет, и заполнять слот нечем. Наблюдаемо: тихая кнопка в шапке одна
     * (ведущая «Отмена»), а название занимает всю оставшуюся ширину до правого поля.
     */
    @Test
    fun theEmptyModalSlotIsFilledWithNothing() {
        show {
            ScreenHeader(
                labels = ScreenHeaderLabels(title = TITLE),
                kind = ScreenHeaderKind.Modal,
                onBack = {},
            )
        }

        composeRule.onAllNodesWithTag(TAG_QUIET_BUTTON).assertCountEquals(1)
        // Границы шапки — её поле содержимого: правый край узла и есть боковое поле 20 dp,
        // до которого название обязано дотянуться, если слот справа пуст.
        val header = composeRule.onNodeWithTag(TAG_SCREEN_HEADER).fetchSemanticsNode()
        val title = composeRule.onNodeWithText(TITLE, useUnmergedTree = true).fetchSemanticsNode()
        val gap = abs(title.boundsInRoot.right - header.boundsInRoot.right)
        assertTrue(
            "the title must reach the right field at ${header.boundsInRoot.right} px, " +
                "but ends at ${title.boundsInRoot.right} px",
            gap <= 1f,
        )
    }

    /**
     * Р-53: в сжатом ряду модальной шапки ведущее действие — то же слово «Отмена»,
     * а не шеврон; до порога ряда на экране нет вовсе.
     */
    @Test
    fun theCollapsedModalRowCarriesTheCancelWord() {
        val header = ScreenHeaderState()
        show {
            PinnedHeaderBar(
                labels = ScreenHeaderLabels(title = TITLE),
                state = header,
                kind = ScreenHeaderKind.Modal,
                onBack = {},
            )
        }

        composeRule.onNodeWithText(cancelName()).assertDoesNotExist()
        composeRule.runOnIdle { header.reportCollapsibleBottom(TITLE_UNDER_BAR) }

        composeRule.onNodeWithText(cancelName()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(backName()).assertDoesNotExist()
    }

    /**
     * Компактный заголовок в сжатом модальном ряду подчиняется общему правилу: он рисуется,
     * только если помещается рядом с ведущим действием (§7).
     */
    @Test
    fun theCollapsedModalRowKeepsTheCompactTitleRule() {
        val header = ScreenHeaderState()
        show {
            PinnedHeaderBar(
                labels = ScreenHeaderLabels(title = LONG_TITLE),
                state = header,
                kind = ScreenHeaderKind.Modal,
                onBack = {},
            )
        }

        composeRule.runOnIdle { header.reportCollapsibleBottom(TITLE_UNDER_BAR) }

        composeRule.onNodeWithText(cancelName()).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_COMPACT_TITLE).assertDoesNotExist()
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
        composeRule.runOnIdle { header.reportCollapsibleBottom(TITLE_BELOW_BAR) }
        composeRule.onNodeWithTag(TAG_COMPACT_TITLE).assertDoesNotExist()

        // Крупный заголовок ушёл под ряд целиком.
        composeRule.runOnIdle { header.reportCollapsibleBottom(TITLE_UNDER_BAR) }
        composeRule.onNodeWithTag(TAG_COMPACT_TITLE).assertIsDisplayed()

        // Тем же порогом он гаснет: второго порога у шапки нет.
        composeRule.runOnIdle { header.reportCollapsibleBottom(TITLE_BELOW_BAR) }
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

        composeRule.runOnIdle { header.reportCollapsibleBottom(TITLE_UNDER_BAR) }

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

    private fun cancelName(): String = context.getString(R.string.action_cancel)

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

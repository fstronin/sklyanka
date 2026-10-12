package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.profile.LocalProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Композиционный стенд каркаса экрана (SKL-104, `TDD.md §17.1`).
 *
 * Стенд проверяет то, что ловит только композиция: состав ряда в обоих состояниях, его
 * высоту и рост по кнопкам, единственность управления и названия, непрозрачность фона,
 * поведение при крупном системном шрифте и признаки чужого профиля. Арифметика порога —
 * в `HeaderThresholdTest`: граница «равно» на стенде зависит от одного пикселя прокрутки
 * и легко становится недостижимой (§17.3). Правило на одиннадцати экранах проверяет
 * экранный стенд приёмки в `app`.
 *
 * Экран собирается в окне макета 390×300 dp — ниже содержимого, иначе прокрутки не будет
 * и порог не достигнется никогда.
 */
@RunWith(AndroidJUnit4::class)
class ScreenFrameTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // --- Покой -------------------------------------------------------------------------------

    @Test
    fun theHeaderStandsExpandedAtRest() {
        showFrame()

        composeRule.onNodeWithTag(TAG_SCREEN_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HEADER_COMPACT_TITLE).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_HEADER_ROW_LINE).assertDoesNotExist()
    }

    @Test
    fun thePinnedRowOfANestedScreenIsPermanent() {
        // Ряд действия у вложенной шапки и сегодня отдельная строка над заголовком:
        // постоянные 56 dp убирают любое перемещение кнопок (§8.1).
        showFrame(kind = ScreenHeaderKind.Nested)

        composeRule.onNodeWithTag(TAG_HEADER_ROW).assertExists()
        assertRowHeight(SklyankaSizes.HeaderCompactRowHeight)
    }

    @Test
    fun thePinnedRowOfARootScreenIsFlatAtRest() {
        showFrame(kind = ScreenHeaderKind.Root, onBack = null)

        assertRowHeight(0.dp)
        val title = composeRule.onNodeWithTag(TAG_SCREEN_TITLE).fetchSemanticsNode()
        assertDp(
            "the large title of a root screen keeps its distance from the top of the area",
            SklyankaSizes.HeaderTopPadding,
            with(composeRule.density) { title.boundsInRoot.top.toDp() },
        )
    }

    // --- Порог -------------------------------------------------------------------------------

    @Test
    fun theCompactTitleAndTheLineAppearAfterTheThreshold() {
        showFrame()

        scrollUpOnce()

        composeRule.onNodeWithTag(TAG_HEADER_COMPACT_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HEADER_ROW_LINE).assertExists()
        assertRowHeight(SklyankaSizes.HeaderCompactRowHeight + SklyankaSizes.DividerThickness)
    }

    @Test
    fun theRowKeepsItsPlaceAndHeightWhileTheContentKeepsGoing() {
        showFrame()
        val markerBefore = markerTop()
        val rowTop = rowBounds().top
        val rowHeight = rowBounds().height

        scrollUpOnce()
        val topAfterThreshold = rowBounds().top
        scrollUpOnce()

        assertEquals("the row must not jump at the threshold", rowTop, topAfterThreshold, POSITION_TOLERANCE)
        assertEquals(
            "the row must not move while the content keeps scrolling",
            topAfterThreshold,
            rowBounds().top,
            POSITION_TOLERANCE,
        )
        assertEquals("the row must keep its height", rowHeight, rowBounds().height, POSITION_TOLERANCE)
        assertTrue(
            "the content must keep scrolling under the row: the marker started at $markerBefore and never moved",
            markerTop() < markerBefore,
        )
    }

    @Test
    fun theUnscrollableContentKeepsTheHeaderExpanded() {
        // Короткое содержимое: прокрутки нет — порог не достигается — компактного заголовка
        // и нижней линии нет, ряд у корня остаётся плоским (критерий 6).
        showFrame(kind = ScreenHeaderKind.Root, onBack = null, blocks = SHORT_CONTENT, scrollable = false)

        composeRule.onNodeWithTag(TAG_SCREEN_TITLE).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_SCREEN_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HEADER_COMPACT_TITLE).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_HEADER_ROW_LINE).assertDoesNotExist()
        assertRowHeight(0.dp)
    }

    // --- Управление и название ровно один раз --------------------------------------------------

    @Test
    fun theBackActionIsComposedOnceInEitherState() {
        showFrame()
        val back = hasText(context.getString(R.string.action_back))

        composeRule.onAllNodesWithText(context.getString(R.string.action_back)).assertCountEquals(1)
        assertEquals("the back action stands in the permanent row at rest", 1, countInsideRow(back))

        scrollUpOnce()

        composeRule.onAllNodesWithText(context.getString(R.string.action_back)).assertCountEquals(1)
        assertEquals("and the same action stays in the row after the threshold", 1, countInsideRow(back))
    }

    @Test
    fun theRootActionMovesIntoTheRowAndIsNeverDoubled() {
        showFrame(kind = ScreenHeaderKind.Root, onBack = null, trailing = { Action() })

        // В покое действие стоит в развёрнутой шапке, а не в ряду (§8.1).
        composeRule.onNodeWithTag(ACTION).assertIsDisplayed()
        assertEquals("the flat row must not hold the action at rest", 0, countInsideRow(hasTestTag(ACTION)))

        scrollUpOnce()

        assertEquals(
            "the action must be composed exactly once after the threshold",
            1,
            composeRule.onAllNodesWithTag(ACTION).fetchSemanticsNodes().size,
        )
        assertEquals("and it must be the copy inside the pinned row", 1, countInsideRow(hasTestTag(ACTION)))
    }

    @Test
    fun theScreenNameIsSpokenOnceInsideTheHeaderAfterTheThreshold() {
        showFrame()

        scrollUpOnce()

        // Крупный заголовок уходит из дерева доступности целиком, и текстом, и тегом:
        // у экрана с `Column.verticalScroll` его узел остаётся в композиции, и без этого
        // чтения с экрана произнесло бы название дважды (§4.3).
        composeRule.onNodeWithTag(TAG_SCREEN_TITLE).assertDoesNotExist()
        assertEquals("the pinned row must name the screen once", 1, countInsideRow(hasText(TITLE)))
        assertEquals(
            "and the stand must not hold a second copy of the name",
            1,
            composeRule.onAllNodesWithText(TITLE).fetchSemanticsNodes().size,
        )
    }

    // --- Крупный системный шрифт -------------------------------------------------------------

    @Test
    fun theCompactTitleIsHiddenAboveTheFontThresholdAndTheRowGrowsByButtons() {
        showFrame(kind = ScreenHeaderKind.Root, onBack = null, fontScale = LARGE_SCALE, trailing = { Action() })

        scrollUpOnce()

        composeRule.onNodeWithTag(TAG_HEADER_COMPACT_TITLE).assertDoesNotExist()
        val row = rowBounds()
        val button = composeRule.onNodeWithTag(ACTION).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the row must grow by its buttons instead of clipping them: button ${button.height}, row ${row.height}",
            row.height >= button.height,
        )
        assertTrue(
            "the compact row never drops below the token height: ${with(composeRule.density) { row.height.toDp() }}",
            with(composeRule.density) { row.height.toDp() } >= SklyankaSizes.HeaderCompactRowHeight,
        )
        assertEquals(
            "the action stays on the screen exactly once",
            1,
            composeRule.onAllNodesWithTag(ACTION).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theCompactTitleIsAnInterfaceLabelAndStaysOneLineAtOneHundredThirtyPercent() {
        // Компактный заголовок — `BodyLarge` (Golos Text 17 sp/600), а не уменьшенная
        // Literata, и до порога скрытия он умещается в одну строку (§9.2, §9.4, критерий 5).
        showFrame(fontScale = HIDE_EDGE_SCALE)

        scrollUpOnce()

        val layout = requireNotNull(
            InkProbe.textLayout(composeRule.onNodeWithTag(TAG_HEADER_COMPACT_TITLE).fetchSemanticsNode()),
        ) { "the compact title carries no text layout" }
        val style = layout.layoutInput.style
        assertEquals("the compact title is the interface label size", SklyankaType.BodyLarge.fontSize, style.fontSize)
        assertEquals("semi-bold like BodyLarge", SklyankaType.BodyLarge.fontWeight, style.fontWeight)
        assertEquals(
            "the interface family, not the reduced heading",
            SklyankaType.BodyLarge.fontFamily,
            style.fontFamily,
        )
        assertNotEquals(
            "the heading family must not leak into the row",
            SklyankaType.TitleScreen.fontFamily,
            style.fontFamily,
        )
        assertEquals("one line at 130 %", 1, layout.lineCount)
    }

    // --- Признаки профиля ----------------------------------------------------------------------

    @Test
    fun theForeignSignsStayWhileTheContentScrolls() {
        // Закрепляются каркасом только полоса и подкраска; надпись и баннер — содержимое:
        // они уезжают и возвращаются наверх, но не исчезают (§10, ОВ-148).
        showFrame(foreign = true)

        composeRule.onNodeWithTag(TAG_PROFILE_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(bannerText()).assertIsDisplayed()

        scrollUpOnce()

        composeRule.onNodeWithTag(TAG_PROFILE_LABEL).assertIsNotDisplayed()
        composeRule.onNodeWithText(bannerText()).assertIsNotDisplayed()
        composeRule.onNodeWithTag(TAG_HEADER_COMPACT_TITLE).assertIsDisplayed()
    }

    // --- Отклоняемый вид шапки ---------------------------------------------------------------

    @Test
    fun theModalKindIsRejected() {
        val failure = runCatching {
            composeRule.setContent {
                SklyankaTheme {
                    ScreenFrame(labels = ScreenHeaderLabels(title = TITLE), kind = ScreenHeaderKind.Modal) { }
                }
            }
            composeRule.waitForIdle()
        }.exceptionOrNull()

        assertTrue(
            "the modal header is another kind of screen top (§6.1), got $failure",
            generateSequence(failure as Throwable?) { it.cause }.any { it is IllegalArgumentException },
        )
    }

    // --- Стенд -------------------------------------------------------------------------------

    private fun showFrame(
        kind: ScreenHeaderKind = ScreenHeaderKind.Nested,
        blocks: Int = BLOCKS,
        scrollable: Boolean = true,
        fontScale: Float = 1f,
        onBack: (() -> Unit)? = {},
        foreign: Boolean = false,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        composeRule.setContent {
            val state = if (foreign) FOREIGN_PROFILE else ProfileChromeState.Owner
            SklyankaTheme(profile = state.accent) {
                CompositionLocalProvider(LocalProfileChrome provides state) {
                    Scaled(fontScale) {
                        Box(modifier = Modifier.size(width = VIEWPORT_WIDTH.dp, height = VIEWPORT_HEIGHT.dp)) {
                            ScreenFrame(
                                labels = ScreenHeaderLabels(title = TITLE),
                                modifier = Modifier.fillMaxSize(),
                                kind = kind,
                                onBack = onBack,
                                trailing = trailing,
                            ) {
                                Content(blocks = blocks, scrollable = scrollable)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Содержимое экрана: развёрнутая шапка — первый блок прокрутки, затем лента (§13.2). */
    @Composable
    private fun ScreenFrameScope.Content(blocks: Int, scrollable: Boolean) {
        Column(
            modifier = if (scrollable) {
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            ExpandedHeader()
            repeat(blocks) { index ->
                Text(text = "$BLOCK $index", style = SklyankaType.Body, color = SklyankaTheme.colors.text)
            }
        }
    }

    /** Действие экрана: заглушка 44 dp с тегом, как слот `trailing` на живых экранах. */
    @Composable
    private fun Action() {
        Text(
            text = ACTION_LABEL,
            style = SklyankaType.BodyLarge,
            color = SklyankaTheme.colors.text,
            modifier = Modifier
                .testTag(ACTION)
                .size(width = ACTION_WIDTH.dp, height = SklyankaSizes.MinTouchTarget),
        )
    }

    /** Системный масштаб шрифта подменой плотности — приём `LargeFontTest`. */
    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    private fun scrollUpOnce() {
        composeRule.onNode(hasScrollAction(), useUnmergedTree = true).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
    }

    private fun rowBounds() = composeRule.onNodeWithTag(TAG_HEADER_ROW).fetchSemanticsNode().boundsInRoot

    private fun markerTop() = composeRule.onNodeWithText("$BLOCK 3").fetchSemanticsNode().boundsInRoot.top

    private fun countInsideRow(matcher: SemanticsMatcher): Int = composeRule
        .onAllNodes(matcher)
        .filter(hasAnyAncestor(hasTestTag(TAG_HEADER_ROW)))
        .fetchSemanticsNodes()
        .size

    private fun assertRowHeight(expected: Dp) {
        assertDp("the pinned row height", expected, with(composeRule.density) { rowBounds().height.toDp() })
    }

    private fun assertDp(message: String, expected: Dp, actual: Dp) {
        assertEquals("$message: expected $expected, got $actual", expected.value, actual.value, TOLERANCE_DP)
    }

    private fun bannerText(): String = context.getString(R.string.profile_banner, PROFILE_NAME)

    private companion object {
        const val TITLE = "Screen"
        const val BLOCK = "Block"
        const val ACTION = "frame_action"
        const val ACTION_LABEL = "Action"
        const val PROFILE_NAME = "Name"
        const val BLOCKS = 12
        const val SHORT_CONTENT = 1

        /** Окно стенда: ниже содержимого, иначе прокрутки и порога не будет. */
        const val VIEWPORT_WIDTH = 390
        const val VIEWPORT_HEIGHT = 300
        const val ACTION_WIDTH = 48

        /** 200 % системного шрифта: выше порога, при котором компактный заголовок скрыт. */
        const val LARGE_SCALE = 2.0f

        /** Граница скрытия: при 130 % компактный заголовок ещё виден и остаётся в одну строку. */
        const val HIDE_EDGE_SCALE = 1.3f

        /** Полторы точки запаса: Skia смешивает каналы, а плотность округляет. */
        const val TOLERANCE_DP = 1.5f
        const val POSITION_TOLERANCE = 1.5f

        val FOREIGN_PROFILE = ProfileChromeState.Foreign(name = PROFILE_NAME, colour = ProfileColor.Terracotta)
    }
}

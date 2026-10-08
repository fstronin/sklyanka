package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

/**
 * Ряд действий напоминания в шести локалях и трёх масштабах шрифта (ОВ-146, §5.1).
 *
 * Правило дизайнера: ряд держится, пока подпись одной строкой с боковым зазором
 * не меньше 4 dp с каждой стороны влезает в половину, — порог меряет ширину, а не
 * масштаб. На эталонном ряду 342 dp (390 dp минус поля полотна) порог подписи —
 * 157 dp. Проверка гоняет настоящую композицию и фиксирует **фактические** исходы
 * шести языков (JVM, ширина подписи в dp):
 *
 * - 100 % — ряд везде: самая широкая — ru «Отложить 60 мин» 129 dp из 157;
 * - 130 % — ru «Отложить 10/15/30/60 мин» (161–165 dp) уходит в столбик; «Отложить
 *   5 мин» (152 dp) и es «Posponer 60 min» (155 dp) остаются в ряду — они у самого
 *   порога, и любой исход там законен; en/de/kk/zh — ряд;
 * - 200 % — столбик во всех шести языках: zh «稍后提醒 60 分钟» занимает 210 dp, а не
 *   97 dp, как считала таблица дизайна (там измерена укороченная строка).
 *
 * Во всех ряду и столбике подписи целы: в ряду зазор до рамки не меньше 4 dp,
 * в столбике кнопка занимает всю ширину. Стенд SKL-24 не годится для этого списка:
 * его потолок — 110 эталонов, и двадцать два кадра матрицы в него не влезают
 * (см. `ReminderLocaleReviewSheetTest` — кадры для визревью складываются артефактом,
 * а не эталоном).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ReminderActionsLocaleMatrixTest {

    @Test
    fun theActionsOfEveryLocaleAndScaleFollowTheCanon() {
        CASES.forEach { case ->
            runComposeUiTest {
                setContent { PinnedLocale(case.locale) { Show(case.fontScale, case.snoozeMinutes) } }
                assertCase(this, case)
            }
        }
    }

    @Composable
    private fun Show(fontScale: Float, snoozeMinutes: Int) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            SklyankaTheme(dark = true) {
                ReminderScreen(state = state(snoozeMinutes), actions = noActions)
            }
        }
    }

    @Composable
    private fun PinnedLocale(locale: Locale, content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale(locale)
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            content()
        }
    }

    private fun state(snoozeMinutes: Int) = ReminderUiState.Card(
        scheduledAt = LocalDateTime.of(2026, 9, 25, 14, 0),
        items = listOf(
            ReminderRow(
                intakeId = "intake-1",
                name = "med-a",
                form = MedicationForm.TABLET,
                doseAmount = BigDecimal("2"),
                strength = BigDecimal("48"),
                strengthUnit = "mg",
            ),
        ),
        snoozeMinutes = snoozeMinutes,
        foreignProfileName = null,
    )

    private fun assertCase(test: ComposeUiTest, case: Case) {
        val snooze = bounds(test, TAG_ACTION_SNOOZE)
        val skip = bounds(test, TAG_ACTION_SKIP)
        when (case.expectation) {
            Expectation.Stacked -> assertStacked(case, snooze, skip)
            Expectation.Row -> assertOneRow(test, case, snooze, skip)
        }
        assertEveryLabelIsWhole(test, case)
    }

    private fun assertStacked(case: Case, snooze: Rect, skip: Rect) {
        assertTrue(
            "${case.id}: the buttons must stack: snooze=$snooze, skip=$skip",
            skip.top >= snooze.bottom,
        )
        assertEquals("${case.id}: stacked buttons must share one width", snooze.width, skip.width, WIDTH_TOLERANCE)
    }

    private fun assertOneRow(test: ComposeUiTest, case: Case, snooze: Rect, skip: Rect) {
        assertTrue(
            "${case.id}: the buttons must share one row: snooze=$snooze, skip=$skip",
            snooze.top < skip.bottom && skip.top < snooze.bottom,
        )
        assertTrue("${case.id}: «Отложить» must stand before «Пропустить»", snooze.left < skip.left)
        listOf(TAG_ACTION_SNOOZE, TAG_ACTION_SKIP).forEach { tag ->
            val node = test.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val gapPx = with(node.layoutInfo.density) { SklyankaSpacing.Micro.roundToPx() }
            val labelWidthPx = textLayoutsOf(node).first().size.width
            assertTrue(
                "${case.id}: the label of $tag must keep $gapPx px to each border: " +
                    "label=$labelWidthPx, button=${node.size}",
                labelWidthPx + 2 * gapPx <= node.size.width,
            )
        }
    }

    private fun assertEveryLabelIsWhole(test: ComposeUiTest, case: Case) {
        listOf(TAG_ACTION_SNOOZE, TAG_ACTION_SKIP).forEach { tag ->
            val layouts = textLayoutsOf(test.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())
            assertTrue("${case.id}: $tag must carry a label", layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertEquals("${case.id}: the label of $tag must fit one line", 1, layout.lineCount)
                assertTrue("${case.id}: the label of $tag must not be cut", !layout.didOverflowHeight)
            }
        }
    }

    private fun bounds(test: ComposeUiTest, tag: String) =
        test.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun textLayoutsOf(node: SemanticsNode): List<TextLayoutResult> {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        node.children.forEach { child -> layouts += textLayoutsOf(child) }
        return layouts
    }

    private data class Case(
        val locale: Locale,
        val fontScale: Float,
        val snoozeMinutes: Int,
        val expectation: Expectation,
    ) {
        val id: String get() = "${locale.language} ${(fontScale * PERCENT).toInt()}% $snoozeMinutes min"
    }

    private enum class Expectation { Row, Stacked }

    private companion object {
        const val PERCENT = 100f
        const val WIDTH_TOLERANCE = 1f
        const val NORMAL = 1f
        const val LARGE = 1.3f
        const val DOUBLE = 2f

        val RU = Locale.forLanguageTag("ru")
        val EN = Locale.forLanguageTag("en-US")
        val DE = Locale.forLanguageTag("de")
        val ES = Locale.forLanguageTag("es")
        val KK = Locale.forLanguageTag("kk")
        val ZH = Locale.forLanguageTag("zh-CN")

        /**
         * Фактические ширины подписей `max(«Отложить N мин», «Пропустить»)` при 100/130/200 %:
         * ru 129/161–165/226–231, en 106/135/191, de 103/132/187, es 121/155/220,
         * kk 109/136/195 (шире откладывания — «Өткізіп жіберу»), zh 116/148/210.
         * Порог подписи — 157 dp: es на 130 % (155 dp) и ru «5 мин» (152 dp) остаются
         * в ряду с зазором ≥4 dp; ru «10/15/30/60 мин» уходят в столбик.
         */
        val CASES = listOf(
            Case(RU, NORMAL, 60, Expectation.Row),
            Case(RU, LARGE, 5, Expectation.Row),
            Case(RU, LARGE, 10, Expectation.Stacked),
            Case(RU, LARGE, 15, Expectation.Stacked),
            Case(RU, LARGE, 30, Expectation.Stacked),
            Case(RU, LARGE, 60, Expectation.Stacked),
            Case(RU, DOUBLE, 60, Expectation.Stacked),
            Case(EN, NORMAL, 60, Expectation.Row),
            Case(EN, LARGE, 60, Expectation.Row),
            Case(EN, DOUBLE, 60, Expectation.Stacked),
            Case(DE, NORMAL, 60, Expectation.Row),
            Case(DE, LARGE, 60, Expectation.Row),
            Case(DE, DOUBLE, 60, Expectation.Stacked),
            Case(ES, NORMAL, 60, Expectation.Row),
            Case(ES, LARGE, 60, Expectation.Row),
            Case(ES, DOUBLE, 60, Expectation.Stacked),
            Case(KK, NORMAL, 60, Expectation.Row),
            Case(KK, LARGE, 60, Expectation.Row),
            Case(KK, DOUBLE, 60, Expectation.Stacked),
            Case(ZH, NORMAL, 60, Expectation.Row),
            Case(ZH, LARGE, 60, Expectation.Row),
            Case(ZH, DOUBLE, 60, Expectation.Stacked),
        )

        val noActions = ReminderActions(
            onTaken = {},
            onSkipped = {},
            onTakenAll = {},
            onSkippedAll = {},
            onSnooze = {},
        )
    }
}

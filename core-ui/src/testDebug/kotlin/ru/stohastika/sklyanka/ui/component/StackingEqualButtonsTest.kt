package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing

/**
 * Стоп-кран пары равных кнопок: критерий считает ширину подписи (ОВ-146, §5.1).
 *
 * Тёмная форма напоминания не несёт бокового поля подписи, поэтому строка держится,
 * пока каждая подпись одной строкой влезает в половину **с воздухом не меньше 4 dp
 * до рамки** с каждой стороны. На эталонном ряду 342 dp (390 dp минус поля полотна
 * 24 dp) это порог 157 dp: `max(w) + 2 × 4 ≤ (342 − 12) / 2`. Проверка сторожит обе
 * стороны решения: возврат поля 28 dp ломает ряд на эталонной ширине, а светлые формы
 * по-прежнему считают поле — иначе их подпись переносилась бы, а не вставала в столбик
 * (дефект пилота SKL-61).
 */
@RunWith(AndroidJUnit4::class)
class StackingEqualButtonsTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Точный порог проверяется подписью ровно заданной ширины: настоящая строка даёт
     * ширину шрифта и масштаба, а утверждать надо само число 157 dp.
     */
    @Test
    fun `the dark pair keeps one row on a label of exactly one hundred fifty seven dp`() {
        showFixedLabels(first = THRESHOLD_LABEL_WIDTH, second = NARROW_LABEL_WIDTH)

        assertOneRow()
    }

    @Test
    fun `the dark pair stacks one dp above the threshold`() {
        showFixedLabels(first = THRESHOLD_LABEL_WIDTH + 1.dp, second = NARROW_LABEL_WIDTH)

        assertStacked()
    }

    @Test
    fun `the dark pair keeps one row on the reference width`() {
        showPair(fontScale = NORMAL, form = ButtonForm.Dark, width = REFERENCE_ROW_WIDTH)

        assertOneRow()
        assertEveryLabelIsWhole()
        assertEveryLabelKeepsTheSideGap()
    }

    @Test
    fun `the dark pair stacks the long russian label at one hundred thirty percent`() {
        showPair(
            fontScale = LARGE,
            form = ButtonForm.Dark,
            width = REFERENCE_ROW_WIDTH,
            snoozeLabel = LONG_SNOOZE_LABEL,
        )

        assertStackedFullWidth()
        assertEveryLabelIsWhole()
    }

    @Test
    fun `the dark pair stacks at two hundred percent and keeps the labels whole`() {
        showPair(fontScale = DOUBLE, form = ButtonForm.Dark, width = REFERENCE_ROW_WIDTH)

        assertStackedFullWidth()
        assertEveryLabelIsWhole()
    }

    /**
     * Светлая пара — та же раскладка, но с полем 28 dp: на 200 % столбик обязан
     * наступить раньше, чем у тёмной, и подпись не имеет права переноситься.
     * Боковой зазор 4 dp к светлым формам не применяется (§5.1, ОВ-146).
     */
    @Test
    fun `the light pair still counts its label padding at two hundred percent`() {
        showPair(fontScale = DOUBLE, form = ButtonForm.Card, width = CARD_ROW_WIDTH)

        assertStacked()
        assertEveryLabelIsWhole()
    }

    private fun showPair(fontScale: Float, form: ButtonForm, width: Dp, snoozeLabel: String = SNOOZE_LABEL) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                SklyankaTheme(dark = form == ButtonForm.Dark) {
                    Box(modifier = Modifier.width(width)) {
                        StackingEqualButtons(
                            first = {
                                SecondaryButton(
                                    text = snoozeLabel,
                                    onClick = {},
                                    form = form,
                                    modifier = Modifier.testTag(TAG_ACTION_SNOOZE),
                                )
                            },
                            second = {
                                SecondaryButton(
                                    text = SKIP_LABEL,
                                    onClick = {},
                                    form = form,
                                    modifier = Modifier.testTag(TAG_ACTION_SKIP),
                                )
                            },
                            gap = SklyankaSpacing.Medium,
                            labelPadding = form.labelPadding,
                            minLabelSideGap = form.minLabelSideGap,
                        )
                    }
                }
            }
        }
    }

    /** Пара подписей заданной ширины: критерий — арифметика, и порог проверяется числом. */
    private fun showFixedLabels(first: Dp, second: Dp) {
        compose.setContent {
            SklyankaTheme(dark = true) {
                Box(modifier = Modifier.width(REFERENCE_ROW_WIDTH)) {
                    StackingEqualButtons(
                        first = { FixedWidthLabel(first, Modifier.testTag(TAG_ACTION_SNOOZE)) },
                        second = { FixedWidthLabel(second, Modifier.testTag(TAG_ACTION_SKIP)) },
                        gap = SklyankaSpacing.Medium,
                        labelPadding = ButtonForm.Dark.labelPadding,
                        minLabelSideGap = ButtonForm.Dark.minLabelSideGap,
                    )
                }
            }
        }
    }

    private fun assertOneRow() {
        val snooze = bounds(TAG_ACTION_SNOOZE)
        val skip = bounds(TAG_ACTION_SKIP)
        assertTrue(
            "the buttons must share one row: snooze=$snooze, skip=$skip",
            snooze.top < skip.bottom && skip.top < snooze.bottom,
        )
        assertTrue("the first button must stand before the second", snooze.left < skip.left)
    }

    private fun assertStacked() {
        val snooze = bounds(TAG_ACTION_SNOOZE)
        val skip = bounds(TAG_ACTION_SKIP)
        assertTrue(
            "the buttons must stack: snooze=$snooze, skip=$skip",
            skip.top >= snooze.bottom,
        )
    }

    private fun assertStackedFullWidth() {
        assertStacked()
        val snooze = bounds(TAG_ACTION_SNOOZE)
        val skip = bounds(TAG_ACTION_SKIP)
        assertEquals("both stacked buttons must share one width", snooze.width, skip.width, WIDTH_TOLERANCE)
        assertTrue(
            "a stacked button must take the full row: $snooze",
            snooze.width >= REFERENCE_ROW_WIDTH.value - WIDTH_TOLERANCE,
        )
    }

    private fun assertEveryLabelIsWhole() {
        val facts = compose.textFacts()
        assertTrue("the pair must carry both labels", facts.size == LABEL_COUNT)
        facts.forEach { fact ->
            assertEquals("the label must fit one line: ${fact.text}", 1, fact.lines.size)
            assertTrue("the label must not be cut: ${fact.text}", !fact.cut)
            assertTrue("the label must not be broken: ${fact.text}", !fact.wordBroken)
        }
    }

    /**
     * В ряду подпись не прижимается к рамке: воздух до каждой границы кнопки — не
     * меньше `SklyankaSpacing.Micro` (4 dp). Именно этого прежний критерий не требовал,
     * и на 130 % подпись вставала вплотную к рамке (ОВ-146).
     */
    private fun assertEveryLabelKeepsTheSideGap() {
        listOf(TAG_ACTION_SNOOZE, TAG_ACTION_SKIP).forEach { tag ->
            val button = bounds(tag)
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val gapPx = with(node.layoutInfo.density) { SklyankaSpacing.Micro.roundToPx() }
            val labelWidthPx = textWidthPx(node)
            assertTrue(
                "the label of $tag must keep $gapPx px to each border: label=$labelWidthPx, button=$button",
                labelWidthPx + 2 * gapPx <= button.width,
            )
        }
    }

    private fun bounds(tag: String) = compose
        .onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot

    private fun textWidthPx(node: SemanticsNode): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        collectTextLayouts(node, layouts)
        return requireNotNull(layouts.firstOrNull()) { "the button carries no text layout" }.size.width
    }

    private fun collectTextLayouts(node: SemanticsNode, into: MutableList<TextLayoutResult>) {
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(into)
        node.children.forEach { child -> collectTextLayouts(child, into) }
    }

    private companion object {
        const val SNOOZE_LABEL = "Отложить 15 мин"
        const val LONG_SNOOZE_LABEL = "Отложить 60 мин"
        const val SKIP_LABEL = "Пропустить"
        const val TAG_ACTION_SNOOZE = "stacking_snooze"
        const val TAG_ACTION_SKIP = "stacking_skip"

        /** 390 dp экрана минус два поля полотна 24 dp (§5.1). */
        val REFERENCE_ROW_WIDTH = 342.dp

        /** 390 dp минус поля экрана 20 dp и карточки 14 dp — ряд кнопок карточки приёма. */
        val CARD_ROW_WIDTH = 322.dp

        /** Порог подписи на эталонном ряду: `(342 − 12) / 2 − 2 × 4` (ОВ-146). */
        val THRESHOLD_LABEL_WIDTH = 157.dp

        /** Вторая подпись заведомо уже порога: проверяется худшая из двух. */
        val NARROW_LABEL_WIDTH = 100.dp

        const val NORMAL = 1f
        const val LARGE = 1.3f
        const val DOUBLE = 2f
        const val WIDTH_TOLERANCE = 1f
        const val LABEL_COUNT = 2
    }
}

/**
 * Подпись ровно заданной ширины: мера критерия — число, а не метрики шрифта.
 *
 * Настоящая строка даёт ширину шрифта, масштаба и локали, поэтому порог 157 dp
 * ею не утвердить. Замеры фактических подписей — отдельные проверки с настоящими
 * кнопками; здесь проверяется арифметика стоп-крана на её собственной границе.
 */
@Composable
private fun FixedWidthLabel(width: Dp, modifier: Modifier = Modifier) {
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    Layout(content = {}, modifier = modifier, measurePolicy = FixedWidthMeasurePolicy(widthPx))
}

private class FixedWidthMeasurePolicy(private val widthPx: Int) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult =
        layout(widthPx, LABEL_HEIGHT_PX) {}

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        widthPx

    private companion object {
        /** Высота подписи-мерки: на раскладку не влияет, лишь бы помещалась в ряд. */
        const val LABEL_HEIGHT_PX = 10
    }
}

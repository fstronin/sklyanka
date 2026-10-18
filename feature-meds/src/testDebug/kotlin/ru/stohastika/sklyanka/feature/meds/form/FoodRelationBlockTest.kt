package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Блок «Связь с едой»: четыре плитки остаются четырьмя, но при нехватке ширины
 * раскладываются по две, а не рвут слово по буквам (AC-42, SKL-156).
 *
 * Ширина приходит из `Box`, а масштаб — подменой `LocalDensity`: так проверка видит
 * измеренную ширину подписей, а не копию расчёта.
 */
@RunWith(AndroidJUnit4::class)
class FoodRelationBlockTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theFourTilesStandInOneRowAtNormalFontWhenTheyFit() {
        show(width = CONTENT_WIDE, fontScale = 1f)

        assertEquals("the block must keep four tiles", 4, tiles().size)
        assertEquals("four tiles must share one row", 1, rows(tiles()))
    }

    @Test
    fun theTilesGrowDownAndKeepTheWordWholeAtDoubleFont() {
        show(width = CONTENT_300, fontScale = 2f)

        val tiles = tiles()
        assertEquals("the block must keep four tiles", 4, tiles.size)
        assertTrue("at 200 % the tiles must not stay in one row: ${rows(tiles)}", rows(tiles) > 1)

        val word = composeRule.onNodeWithText(ANY_LABEL, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("«$ANY_LABEL» must not be broken by letters", 1, textLayout(word).lineCount)
    }

    private fun show(width: androidx.compose.ui.unit.Dp, fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                SklyankaTheme {
                    Box(modifier = Modifier.width(width)) {
                        FoodRelationBlock(
                            state = MedFormUiState.of(draft(), FormMode.Create, FormInputs()),
                            onIntent = {},
                            anchors = FormAnchors(),
                        )
                    }
                }
            }
        }
    }

    private fun tiles(): List<SemanticsNode> = composeRule.onAllNodesWithTag(TAG_FOOD_RELATION, useUnmergedTree = true)
        .fetchSemanticsNodes()

    /** Число различных верхних границ: плитки одной строки стоят на одной вертикали. */
    private fun rows(tiles: List<SemanticsNode>): Int = tiles.map { it.boundsInRoot.top.roundToInt() }.distinct().size

    private fun textLayout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return requireNotNull(results.firstOrNull()) { "the tile carries no text layout" }
    }

    private fun draft() = MedicationDraft(
        id = null,
        profileId = PROFILE_ID,
        name = "med-a",
        form = MedicationForm.TABLET,
        scheduleMode = ScheduleMode.SCHEDULED,
        times = emptyList(),
        startDate = LocalDate.of(2026, 9, 25),
    )

    private companion object {
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"

        /** Достаточно широкий контент, чтобы четыре плитки помещались даже запасным шрифтом. */
        val CONTENT_WIDE = 400.dp

        /** Ширина контента формы на экране 300 dp: 300 − 2 × 20. */
        val CONTENT_300 = 260.dp

        const val ANY_LABEL = "Неважно"
    }
}

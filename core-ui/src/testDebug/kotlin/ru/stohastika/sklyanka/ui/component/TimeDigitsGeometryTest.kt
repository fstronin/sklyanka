package ru.stohastika.sklyanka.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalTime

/**
 * Геометрия цифр времени в диалоге (ОВ-118/ОВ-119).
 *
 * Проверяется свойство, а не снимок: в слоте 96×80 dp цифра оптически центрирована,
 * а в поле клавиатурного ввода 96×72 dp все чернила внутри и зазоры равны. Чернила
 * измеряются отрисовкой уже посчитанной разметки в `ImageBitmap` (см. `InkProbe`).
 *
 * Свойство обязано держаться на 100 %, 130 % и 200 %: цифры диалога не масштабируются
 * системным шрифтом (потолок 100 %, ОВ-89).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimeDigitsGeometryTest {

    @Test
    fun theSlotDigitsSitCenteredInTheirPlate() = assertSlotCentered(SCALE_100)

    @Test
    fun theSlotDigitsKeepCenteringAtOneHundredThirtyPercent() = assertSlotCentered(SCALE_130)

    @Test
    fun theSlotDigitsKeepCenteringAtTwoHundredPercent() = assertSlotCentered(SCALE_200)

    @Test
    fun theInputDigitsFitInTheFieldWithEqualGaps() = assertInputCentered(SCALE_200)

    private fun assertSlotCentered(scale: Float) {
        composeAt(scale) {
            val digits = digitNodes(TAG_TIME_PICKER)
            assertTrue("the picker must show the slot digits", digits.isNotEmpty())
            digits.forEach { node ->
                val layout = requireNotNull(InkProbe.textLayout(node))
                assertEquals(
                    "the slot line must equal the compensated font line",
                    SLOT_TEXT_HEIGHT,
                    layout.size.height,
                )
                val ink = requireNotNull(InkProbe.inkBounds(layout)) { "the digit must have ink" }
                assertTrue(
                    "the ink must be vertically centered in the line: $ink of ${layout.size}",
                    centreGap(ink.top, ink.bottom, layout.size.height) <= TOLERANCE,
                )
                val plate = requireNotNull(node.parent)
                assertEquals("the slot plate must be 96×80 dp", SLOT_PLATE_SIZE, plate.size)
                val plateTop = plate.boundsInRoot.top
                val textTop = node.boundsInRoot.top
                assertEquals(
                    "the 72 dp line must be centered in the 80 dp plate",
                    ((plate.size.height - layout.size.height) / 2).toFloat(),
                    textTop - plateTop,
                    FLOAT_TOLERANCE,
                )
            }
        }
    }

    private fun assertInputCentered(scale: Float) {
        composeAt(scale) {
            onNodeWithTag(TAG_TIME_PICKER).assertExists()
            onNodeWithText(
                ApplicationProvider.getApplicationContext<android.content.Context>()
                    .getString(R.string.cd_switch_time_input),
            ).performClick()
            onNodeWithTag(TAG_TIME_INPUT).assertExists()

            val fields = digitNodes(TAG_TIME_INPUT)
            assertTrue("the keyboard input must show digit fields", fields.isNotEmpty())
            fields.forEach { node ->
                val layout = requireNotNull(InkProbe.textLayout(node))
                assertEquals(FIELD_HEIGHT, node.size.height)
                val ink = requireNotNull(InkProbe.inkBounds(layout)) { "the input digit must have ink" }
                assertTrue(
                    "the ink must be inside the field: $ink of ${layout.size}",
                    ink.top >= 0 && ink.bottom < FIELD_HEIGHT,
                )
                assertTrue(
                    "the gaps above and below the ink must be equal: $ink of $FIELD_HEIGHT",
                    centreGap(ink.top, ink.bottom, FIELD_HEIGHT) <= TOLERANCE,
                )
            }
        }
    }

    private fun ComposeUiTest.digitNodes(container: String): List<SemanticsNode> =
        onAllNodes(hasAnyAncestor(hasTestTag(container)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter(InkProbe::hasDigits)

    private fun centreGap(top: Int, bottom: Int, box: Int): Int = kotlin.math.abs((top + bottom + 1) / 2 - box / 2)

    private fun composeAt(scale: Float, question: ComposeUiTest.() -> Unit) {
        val previous = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(scale)
        try {
            runComposeUiTest {
                setContent { Content() }
                question(this)
            }
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    @Composable
    private fun Content() {
        SklyankaTheme {
            DateTimePickerDialog(
                title = "Time",
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = {},
                onConfirm = {},
            )
        }
    }

    private companion object {
        const val SCALE_100 = 1f
        const val SCALE_130 = 1.3f
        const val SCALE_200 = 2f
        const val SLOT_TEXT_HEIGHT = 72
        const val FIELD_HEIGHT = 72
        val SLOT_PLATE_SIZE = IntSize(96, 80)
        const val TOLERANCE = 1
        const val FLOAT_TOLERANCE = 1f
    }
}

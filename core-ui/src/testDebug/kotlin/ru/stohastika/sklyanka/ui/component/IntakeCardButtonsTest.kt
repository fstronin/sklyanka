package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Габариты пары «Принято/Пропустить» в карточке приёма (ОВ-90, SKL-70).
 *
 * Проверяется свойство разметки: обе кнопки одного роста и делят строку пополам.
 * Иерархию «заливка/контур» стерегут снимки каталога: одинаковый размер не значит
 * одинаковый вид.
 */
@RunWith(AndroidJUnit4::class)
class IntakeCardButtonsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun thePairSharesTheRowAndTheSameHeight() {
        composeTestRule.setContent {
            SklyankaTheme {
                IntakeCard(
                    state = IntakeCardState.DueNow,
                    labels = IntakeCardLabels(medication = MEDICATION, dose = DOSE, time = TIME),
                    actions = IntakeCardActions(onMark = {}, onSkip = {}),
                )
            }
        }

        val primary = composeTestRule.onNodeWithTag(TAG_PRIMARY_BUTTON).fetchSemanticsNode().boundsInRoot
        val secondary = composeTestRule.onNodeWithTag(TAG_SECONDARY_BUTTON).fetchSemanticsNode().boundsInRoot

        assertEquals("the pair must share the row equally", primary.width, secondary.width, WIDTH_TOLERANCE)
        assertEquals("both buttons must be the same height", primary.height, secondary.height, HEIGHT_TOLERANCE)
    }

    @Test
    fun theCardButtonsCarryTheCardHeight() {
        composeTestRule.setContent {
            SklyankaTheme {
                IntakeCard(
                    state = IntakeCardState.DueNow,
                    labels = IntakeCardLabels(medication = MEDICATION, dose = DOSE, time = TIME),
                    actions = IntakeCardActions(onMark = {}, onSkip = {}),
                )
            }
        }

        val density = composeTestRule.onNodeWithTag(TAG_PRIMARY_BUTTON).fetchSemanticsNode().layoutInfo.density
        val primary = composeTestRule.onNodeWithTag(TAG_PRIMARY_BUTTON).fetchSemanticsNode().size
        val secondary = composeTestRule.onNodeWithTag(TAG_SECONDARY_BUTTON).fetchSemanticsNode().size
        val expected = with(density) { SklyankaSizes.SecondaryButtonHeight.toPx() }

        assertTrue(
            "the primary button is ${with(density) { primary.height.toDp() }}, not the card height",
            primary.height >= expected - HEIGHT_TOLERANCE,
        )
        assertTrue(
            "the secondary button is ${with(density) { secondary.height.toDp() }}, not the card height",
            secondary.height >= expected - HEIGHT_TOLERANCE,
        )
    }

    private companion object {
        const val MEDICATION = "med-a"
        const val DOSE = "1 tablet · 500 mg"
        const val TIME = "08:00"
        const val WIDTH_TOLERANCE = 1f
        const val HEIGHT_TOLERANCE = 1f
    }
}

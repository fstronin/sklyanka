package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing

@RunWith(AndroidJUnit4::class)
class ScreenHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theTitleFollowsTheActionRowClosely() {
        composeRule.setContent {
            SklyankaTheme {
                ScreenHeader(
                    labels = ScreenHeaderLabels(title = TITLE),
                    kind = ScreenHeaderKind.Nested,
                    onBack = {},
                )
            }
        }

        val action = composeRule.onNodeWithText(context.getString(ru.stohastika.sklyanka.ui.R.string.action_back))
            .fetchSemanticsNode()
        val title = composeRule.onNodeWithText(TITLE).fetchSemanticsNode()
        val gapDp = with(composeRule.density) { (title.boundsInRoot.top - action.boundsInRoot.bottom).toDp() }
        assertTrue("the gap must stay within 4..12 dp but is $gapDp", gapDp >= 3.5.dp && gapDp <= 12.5.dp)
    }

    @Test
    fun theTopPaddingIsAppliedOnce() {
        composeRule.setContent {
            SklyankaTheme {
                ScreenHeader(
                    labels = ScreenHeaderLabels(title = TITLE),
                    kind = ScreenHeaderKind.Nested,
                    onBack = {},
                )
            }
        }

        val action = composeRule.onNodeWithText(context.getString(ru.stohastika.sklyanka.ui.R.string.action_back))
            .fetchSemanticsNode()
        val topDp = with(composeRule.density) { action.boundsInRoot.top.toDp() }
        val minimum = SklyankaSizes.HeaderTopPaddingProfile
        assertTrue("the header must keep its top padding, but the row starts at $topDp", topDp >= minimum)
        assertTrue(
            "the top padding must be applied once, but the row starts at $topDp",
            topDp <= SklyankaSizes.HeaderTopPadding,
        )
    }

    @Test
    fun aLeadingSlotReplacesTheBackActionInTheSameRhythm() {
        composeRule.setContent {
            SklyankaTheme {
                ScreenHeader(
                    labels = ScreenHeaderLabels(title = TITLE),
                    kind = ScreenHeaderKind.Nested,
                    leading = { Box(modifier = Modifier.size(SklyankaSizes.MinTouchTarget).testTag(LEADING)) },
                    trailing = { Box(modifier = Modifier.size(SklyankaSizes.MinTouchTarget).testTag(TRAILING)) },
                )
            }
        }

        composeRule.onNodeWithTag(LEADING).assertIsDisplayed()
        composeRule.onNodeWithTag(TRAILING).assertIsDisplayed()
        val action = composeRule.onNodeWithTag(LEADING).fetchSemanticsNode()
        val title = composeRule.onNodeWithText(TITLE).fetchSemanticsNode()
        val gapDp = with(composeRule.density) { (title.boundsInRoot.top - action.boundsInRoot.bottom).toDp() }
        assertTrue("the gap must stay within 4..12 dp but is $gapDp", gapDp >= 3.5.dp && gapDp <= 12.5.dp)
        val minimum = SklyankaSpacing.Micro
        assertTrue("the gap must not collapse: $gapDp", gapDp >= minimum)
    }

    private companion object {
        const val TITLE = "Header"
        const val LEADING = "leading"
        const val TRAILING = "trailing"
    }
}

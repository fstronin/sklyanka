package ru.stohastika.sklyanka.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Системная «Назад» закрывает шторку — и только её (SKL-143, `docs/norm/04-behavior.md` §5.3).
 *
 * Норма шторки: закрывают её «Отмена», системная «Назад» и тап по затемнению — все три
 * означают отмену и зовут **один** обработчик (§4.18). Дефект был в том, что возврата
 * среди них не было: нажатие уходило мимо шторки в раздел и уносило экран под ней.
 *
 * Проверяется наблюдаемое: нажатие системной «Назад» с живой шторкой зовёт обработчик
 * ровно один раз, а содержимое под шторкой остаётся на месте. «Назад» идёт тем же путём,
 * каким приходит человеку, — через диспетчер возврата активности, а не вызовом
 * обработчика напрямую.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class BottomSheetBackTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSystemBackClosesTheSheetAndKeepsWhatIsUnderIt() {
        var dismissals = 0
        var visible by mutableStateOf(true)
        composeRule.setContent {
            SklyankaTheme {
                Column {
                    // Содержимое под шторкой: экран, поверх которого она поднята.
                    Box(modifier = Modifier.testTag(TAG_UNDER_THE_SHEET))
                    SklyankaBottomSheet(
                        visible = visible,
                        onDismiss = {
                            dismissals += 1
                            visible = false
                        },
                    ) {
                        Box(modifier = Modifier.testTag(TAG_SHEET_CONTENT))
                    }
                }
            }
        }
        composeRule.onNodeWithTag(TAG_SHEET_CONTENT).assertExists()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals("the system back must close the sheet exactly once", 1, dismissals)
        assertTrue(
            "closing the sheet must keep what is under it (§5.3)",
            composeRule.onAllNodesWithTag(TAG_UNDER_THE_SHEET).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /** Затухание ухода шторки: обработчик зовётся синхронно, а содержимое уходит анимацией. */
    private companion object {
        const val TAG_UNDER_THE_SHEET = "under_the_sheet"
        const val TAG_SHEET_CONTENT = "sheet_content"
    }
}

package ru.stohastika.sklyanka.acceptance.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferScreen
import ru.stohastika.sklyanka.feature.settings.transfer.TransferScreenFixtures
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.R as UiR

@OptIn(ExperimentalTestApi::class)
class Sk90TransferHeaderScreenSteps {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Допустим("^экран «Перенос данных» собран на стенде SKL-90$")
    fun theTransferScreenIsOnTheStand() = Unit

    @Тогда("^между рядом действия и заголовком «Перенос данных» зазор не больше 12 dp$")
    fun theTitleFollowsTheActionRow() {
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    DataTransferScreen(
                        state = TransferScreenFixtures.overview(),
                        onIntent = {},
                        onSave = {},
                        onPickFile = {},
                        onBack = {},
                    )
                }
            }
            val back = onNodeWithText(context.getString(UiR.string.action_back)).fetchSemanticsNode()
            val title = onNodeWithText(context.getString(R.string.transfer_title)).fetchSemanticsNode()
            val gap = with(density) { (title.boundsInRoot.top - back.boundsInRoot.bottom).toDp() }
            assertTrue("the title must follow the action row closely, but the gap is $gap", gap in MIN_GAP..MAX_GAP)
        }
    }

    private companion object {
        val MIN_GAP = 3.5.dp
        val MAX_GAP = 12.5.dp
    }
}

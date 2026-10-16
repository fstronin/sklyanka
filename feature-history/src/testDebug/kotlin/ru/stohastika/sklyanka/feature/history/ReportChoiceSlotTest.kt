package ru.stohastika.sklyanka.feature.history

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.history.ReportScope
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Слот состояния в выборе состава — один и тот же в выбранном и невыбранном виде (V-19, §5.1 п. 4).
 *
 * Место галочки и место метки «Про» зарезервированы всегда: колонка текста одна и та же, и пояснение
 * состава переносится независимо от того, что выбрано. Дефект был ровно обратным — в базовом кадре
 * пояснение переносилось иначе, чем в расширенном, и человек читал две разные строки об одном выборе.
 * Сторож сравнивает измеренную ширину и высоту пояснения в обоих состояниях.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
@OptIn(ExperimentalTestApi::class)
class ReportChoiceSlotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theNoteKeepsItsColumnWhicheverCompositionIsChosen() {
        val base = noteBox(ReportScope.BASE)
        val extended = noteBox(ReportScope.EXTENDED)
        println("choice note column: base=$base extended=$extended")
        assertEquals("the note column must not depend on the choice (§5.1 п. 4, V-19)", base, extended)
    }

    /** Ширина и высота пояснения выбранного состава: по ним и видно перенос. */
    private fun noteBox(scope: ReportScope): Pair<Int, Int> {
        val note = context.getString(R.string.history_report_composition_extended_note)
        var box = 0 to 0
        runComposeUiTest {
            setContent {
                SklyankaTheme { HistoryScreen(state = HistoryStates.withReport(scope = scope), onIntent = {}) }
            }
            val node = onNodeWithText(note, useUnmergedTree = true).fetchSemanticsNode()
            box = node.size.width to node.size.height
        }
        return box
    }
}

package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_DELIVERY
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_WEEK_SUMMARY

/**
 * Экранные шаги приёмки удаления пробного напоминания в диагностике (SKL-32, группа `@diagnostics`).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране диагностики; состояние собирает
 * тот же стенд [DiagnosticsStand], что у SKL-13 и SKL-20. Проверки отсутствия называют удалённые
 * строки и теги дословно: ресурсов у них больше нет, и сценарий проверяет контракт, а не реализацию.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TestReminderRemovalDiagnosticsSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Тогда("^на «Проверке напоминаний» есть «Проверить доставку» и «Выгрузить журнал»$")
    fun theFirstLevelActionsAreThere() {
        assertTrue(hasTag(TAG_CHECK_DELIVERY), "the delivery check action must be there")
        assertTrue(
            visibleTexts().any { it.contains(context.getString(R.string.diagnostics_journal_export)) },
            "the journal export action must be there",
        )
    }

    @Но("^нет ни кнопки, ни строки о пробном напоминании$")
    fun theTestReminderIsGone() {
        DiagnosticsStand.ask(world) {
            onAllNodesWithTag(TEST_REMINDER_TAG, useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithText(TEST_ACTION_LABEL, useUnmergedTree = true).assertCountEquals(0)
        }
        assertFalse(
            visibleTexts().any { it.contains(TEST_WORD, ignoreCase = true) },
            "the diagnostics must not mention a test reminder at all",
        )
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек открывает журнал срабатываний$")
    fun thePersonOpensTheJournalAt(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        DiagnosticsStand.ask(world) { onNodeWithTag(TAG_DIAGNOSTICS).assertIsDisplayed() }
    }

    @Тогда("^среди записей журнала нет записи вида «пробное напоминание»$")
    fun theJournalKnowsNoTestEvent() {
        val journal = requireNotNull(DiagnosticsStand.state(world).snapshot?.journal)
        assertTrue(journal.isNotEmpty(), "the premise must leave a record about the intake reminder")
        assertTrue(
            journal.none { it.event.code == TEST_EVENT_CODE },
            "the journal must not carry a test reminder record",
        )
        assertFalse(
            visibleTexts().any { it.contains(TEST_WORD, ignoreCase = true) },
            "the journal rows must not name a test reminder",
        )
    }

    @И("^сводка «вовремя за 7 дней» считает только напоминания о приёмах$")
    fun theSummaryCountsIntakeRemindersOnly() {
        val state = DiagnosticsStand.state(world)
        val journal = requireNotNull(state.snapshot?.journal)
        val shown = journal.count { it.event == ReminderEvent.SHOWN }
        val summary = requireNotNull(state.snapshot?.onTime)
        assertEquals(shown, summary.fired, "the summary must count presentations and nothing else")
        assertTrue(hasTag(TAG_WEEK_SUMMARY), "the week summary must be shown on the first level")
    }

    private fun hasTag(tag: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun visibleTexts(): List<String> {
        var texts: List<String> = emptyList()
        DiagnosticsStand.ask(world) {
            texts = onAllNodesWithTag(TAG_DIAGNOSTICS, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
        }
        return texts
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            .orEmpty().map { it.text } + node.children.flatMap(::textsOf)

    private fun hasText(state: DiagnosticsUiState, text: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private companion object {
        const val TEST_REMINDER_TAG = "diagnostics_test_reminder"
        const val TEST_ACTION_LABEL = "Пробное напоминание через минуту"
        const val TEST_WORD = "пробн"
        const val TEST_EVENT_CODE = "test"
    }
}

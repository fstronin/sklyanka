package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderOnTimeRule
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Экранные шаги приёмки SKL-88 — подавленное не выдаётся за показ (ПМ-22, ОВ-109).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране «Проверка напоминаний»:
 * состояние собирает настоящий `DiagnosticsViewModel` на мире сценария ([DiagnosticsStand]),
 * а подписи читаются из ресурсов — дословно те же, что видит человек.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class FiredShowScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locale = context.resources.configuration.locales[0]

    @Когда("^человек открывает «Проверку напоминаний»$")
    fun thePersonOpensTheRemindersCheck() {
        DiagnosticsStand.ask(world) { onNodeWithTag(TAG_DIAGNOSTICS).assertIsDisplayed() }
    }

    /** «Для поддержки» раскрывается стендом: журнал живёт в техническом блоке (ОВ-49). */
    @Когда("^человек раскрывает «Для поддержки» на «Проверке напоминаний»$")
    fun thePersonExpandsSupport() {
        DiagnosticsStand.ask(world) { onNodeWithTag(TAG_DIAGNOSTICS).assertIsDisplayed() }
    }

    @Тогда("^последним напоминанием указано предъявление (.+?), (\\d{2}:\\d{2})$")
    fun theLastReminderIsThatShownOne(date: String, time: String) {
        val state = DiagnosticsStand.state(world)
        val last = requireNotNull(state.snapshot?.lastShown) { "the journal must carry the last shown reminder" }
        val expected = world.clock.momentOf(world.localMoment(date, time))
        assertEquals(ReminderEvent.SHOWN, last.event, "the suppressed firing must not replace the show")
        assertEquals(expected, last.firedAt, "the last reminder is the early show")
        assertTrue(
            hasText(state, string(R.string.diagnostics_last_actual, momentText(expected))),
            "the first level must name the moment of the show",
        )
    }

    @И("^подавленное срабатывание в «Последнее напоминание» не попадает$")
    fun theSuppressedFiringIsNotTheLastReminder() {
        val last = DiagnosticsStand.state(world).snapshot?.lastShown
        assertEquals(ReminderEvent.SHOWN, last?.event, "a suppression is a journal fact, not a delivery (НАП-37)")
        assertNotEquals(ReminderEvent.SUPPRESSED, last?.event)
    }

    @Тогда(
        "^запись подавленного срабатывания подписана «сработало с опозданием на (\\d+) минут — не показано»$",
    )
    fun theSuppressedEntryIsSigned(minutes: Int) {
        val state = DiagnosticsStand.state(world)
        val late = context.resources.getQuantityString(R.plurals.diagnostics_late, minutes, minutes)
        val label = string(R.string.diagnostics_journal_too_late, late)
        assertTrue(hasText(state, label), "the journal must sign the suppressed firing as «$label»")
    }

    @Тогда("^подавленное срабатывание не входит в число сработавших за 7 дней$")
    fun theSuppressedFiringIsNotCountedAsFired() {
        val snapshot = requireNotNull(DiagnosticsStand.state(world).snapshot)
        val since = world.clock.now().minus(ReminderOnTimeRule.WINDOW)
        val shown = snapshot.journal.count { it.event == ReminderEvent.SHOWN && !it.occurredAt.isBefore(since) }
        assertTrue(shown > 0, "the premise records shown reminders")
        assertEquals(shown, snapshot.onTime.fired, "the suppressed firing must not count as fired")
        assertEquals(shown, snapshot.week.fired, "the suppressed firing must not count in the week summary")
    }

    @И("^не входит в число пропущенных приёмов$")
    fun theSuppressedFiringIsNotCountedAsMissed() {
        assertEquals(
            0,
            DiagnosticsStand.state(world).snapshot?.week?.missed,
            "a suppressed firing is not a missed intake",
        )
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun momentText(instant: Instant): String = instant.atZone(world.clock.zone).toLocalDateTime()
        .format(DateTimeFormatter.ofPattern(string(R.string.diagnostics_datetime_pattern), locale))

    private fun hasText(state: DiagnosticsUiState, text: String): Boolean {
        var found = false
        DiagnosticsStand.askRaw(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }
}

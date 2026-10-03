package ru.stohastika.sklyanka.acceptance.reminder

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.ReminderUiState
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SNOOZE
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_TAKEN
import ru.stohastika.sklyanka.feature.reminder.TAG_ROW
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import java.time.LocalTime

/**
 * Экранные шаги приёмки карточки напоминания в объёме SKL-30 (группа `@reminder-screen`).
 *
 * Проверяется то, чего карточка **не** делает: снэкбара на ней нет, частичная отметка
 * строки карточку не закрывает и окна отмены не даёт, «Отложить» отмены не предлагает.
 * Отмена с карточки живёт на «Сегодня» и проверяется прогоном `app` (`TDD.md §12.2`).
 *
 * Имена лекарств: сценарий называет их по-русски, а карточка показывает имя модели
 * (`med-N`, НФТ-19), — [displayName] переводит одно в другое.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class ReminderUndoSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // --- Предыстория ----------------------------------------------------------------------

    @Допустим("^напоминание о нём показано$")
    fun theReminderOfItIsShown() {
        val intake = world.intakes.currentOrScheduled()
        fireAt(intake.scheduledAt)
    }

    @Допустим("^у профиля «([^»]+)» есть лекарства «([^»]+)» и «([^»]+)» с приёмом в (\\d{2}:\\d{2})$")
    fun theProfileHasTwoMedicationsAt(profile: String, first: String, second: String, time: String) {
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        listOf(first, second).forEach { medication ->
            if (RussianNames.resolve(medication, world.medications.names()) == null) {
                world.medications.add(
                    profile = world.profiles,
                    profileName = profile,
                    name = medication,
                    times = listOf(moment.toLocalTime()),
                )
            }
            world.intakes.pending(world.medicationNamed(medication), moment)
        }
    }

    @Допустим("^напоминание с двумя приёмами на (.+?), (\\d{2}:\\d{2}) показано$")
    fun theReminderWithTwoIntakesIsShown(date: String, time: String) {
        val moment = world.localMoment(date, time)
        fireAt(moment)
        assertTrue(
            "the card must carry both intakes",
            (ReminderStand.state(world) as? ReminderUiState.Card)?.items?.size == 2,
        )
    }

    // --- Действия -------------------------------------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Принято» на карточке напоминания$")
    fun thePersonPressesTakenOnTheCard(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        ReminderStand.act(world) { onNodeWithTag(TAG_ACTION_TAKEN).performClick() }
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Принято» в строке «([^»]+)»$")
    fun thePersonPressesTakenInTheRow(date: String, time: String, medication: String) {
        world.clock.moveTo(world.localMoment(date, time))
        val name = displayName(medication)
        val taken = context.getString(R.string.reminder_row_taken)
        ReminderStand.act(world) {
            onNode(
                hasText(taken) and hasAnyAncestor(hasTestTag(TAG_ROW) and hasAnyDescendant(hasText(name))),
            ).performClick()
        }
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Отложить (\\d+) мин»$")
    fun thePersonPressesSnooze(date: String, time: String, minutes: Int) {
        world.clock.moveTo(world.localMoment(date, time))
        ReminderStand.act(world) { onNodeWithTag(TAG_ACTION_SNOOZE).performClick() }
    }

    // --- Проверки -------------------------------------------------------------------------

    @Тогда("^карточка закрывается$")
    fun theCardCloses() {
        assertTrue("the card must close", ReminderStand.state(world) is ReminderUiState.Closed)
    }

    @И("^снэкбар с действием «Отменить» на ней не показывается$")
    fun noSnackbarIsShownOnTheCard() = assertNoSnackbarOnTheCard()

    @И("^снэкбара с действием «Отменить» на карточке нет$")
    fun noSnackbarIsOnTheCard() = assertNoSnackbarOnTheCard()

    @Тогда("^карточка напоминания остаётся открытой$")
    fun theCardStaysOpen() {
        assertTrue("the card must stay open", ReminderStand.state(world) is ReminderUiState.Card)
    }

    @И("^в ней остаётся строка «([^»]+)»$")
    fun theCardKeepsCarryingThatRow(medication: String) {
        val expected = displayName(medication)
        val state = ReminderStand.state(world)
        assertTrue(
            "the card must still carry the row of $expected: $state",
            (state as? ReminderUiState.Card)?.items?.any { it.name == expected } == true,
        )
    }

    @И("^строки «([^»]+)» в карточке больше нет$")
    fun theRowIsGoneFromTheCard(medication: String) {
        val gone = displayName(medication)
        val state = ReminderStand.state(world)
        assertTrue(
            "the row of $gone must leave the card: $state",
            (state as? ReminderUiState.Card)?.items?.none { it.name == gone } == true,
        )
    }

    @И("^экран «Сегодня» не открывается$")
    fun todayDoesNotOpen() {
        // Частичная отметка карточку не закрывает: окно отмены не публикуется, и перейти
        // на «Сегодня» человеку нечем — второго слоя поверх напоминания не появляется.
        assertTrue("no undo window may open from a partial row mark", world.undoActions.live() == null)
    }

    @И("^отмены для этого действия не предлагается$")
    fun noUndoIsOfferedForThatAction() {
        assertNoSnackbarOnTheCard()
        assertTrue("«Отложить» must not open an undo window", world.undoActions.live() == null)
    }

    // --- Обвязка --------------------------------------------------------------------------

    /** Показ карточки боевым путём: перепланирование, срабатывание, открытие активности. */
    private fun fireAt(moment: java.time.LocalDateTime) {
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(moment)
        world.reminders.deliverThrough(moment)
        ReminderStand.open(world)
        assertTrue("the reminder must be shown", ReminderStand.state(world) is ReminderUiState.Card)
    }

    private fun assertNoSnackbarOnTheCard() {
        ReminderStand.ask(world) {
            onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    /** Имя лекарства глазами карточки: сценарий зовёт «Магнием B6», карточка — `med-1`. */
    private fun displayName(scenarioName: String): String =
        world.medications.get(world.medicationNamed(scenarioName)).name
}

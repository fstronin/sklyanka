package ru.heuri.sklyanka.acceptance.reminder

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.steps.dateOf
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import ru.heuri.sklyanka.feature.reminder.R
import ru.heuri.sklyanka.feature.reminder.ReminderUiState
import ru.heuri.sklyanka.feature.reminder.TAG_ACTION_SKIP
import ru.heuri.sklyanka.feature.reminder.TAG_ACTION_SNOOZE
import ru.heuri.sklyanka.feature.reminder.TAG_ACTION_TAKEN
import ru.heuri.sklyanka.feature.reminder.TAG_DOSE
import ru.heuri.sklyanka.feature.reminder.TAG_NAME
import ru.heuri.sklyanka.feature.reminder.TAG_OVERLINE
import ru.heuri.sklyanka.feature.reminder.TAG_REMINDER
import ru.heuri.sklyanka.feature.reminder.TAG_ROW
import ru.heuri.sklyanka.feature.reminder.TAG_TIME
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Экранные шаги приёмки полноэкранного напоминания (SKL-13, группа `@reminder-screen`).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране напоминания
 * (`SKL-26/TDD.md §4.3`). Состояние собирает настоящий `ReminderViewModel` на мире
 * сценария ([ReminderStand]): отметка, откладывание и последствия — те же доменные
 * сценарии, что у продукта.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class ReminderCardSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Дублирование на часы ------------------------------------------------------------------

    @Допустим("^дублирование приёма «([^»]+)» на часы выключено$")
    fun theWatchMirroringIsOff(name: String) {
        world.medications.get(name)
        world.profiles.setMirrorReminders(world.profiles.active, false)
    }

    @Тогда("^на напоминании нет плашки «Продублировано на часы»$")
    fun theWatchChipIsAbsent() {
        ReminderStand.open(world)
        assertTrue(showsTag(TAG_REMINDER), "the reminder screen must be on the stand")
        val offenders = texts().filter { it.contains(WATCH_CHIP_WORD, ignoreCase = true) }
        assertEquals(emptyList<String>(), offenders, "the watch chip must not be on the screen (ADR-0009)")
    }

    @Но("^остальной состав напоминания не меняется$")
    fun theRestOfTheCardIsUnchanged() {
        listOf(TAG_OVERLINE, TAG_TIME, TAG_NAME, TAG_DOSE, TAG_ACTION_TAKEN, TAG_ACTION_SNOOZE, TAG_ACTION_SKIP)
            .forEach { tag -> assertTrue(showsTag(tag), "the card is missing $tag") }
        assertTrue(showsText("14:00"), "the card must keep the time of the intake")
    }

    // --- Действия карточки ---------------------------------------------------------------------

    @Допустим("^напоминание о приёме (.+?), (\\d{2}:\\d{2}) показано$")
    fun theReminderOfThatIntakeIsShown(date: String, time: String) {
        showReminder(date, time)
    }

    @Когда("^в (\\d{2}:\\d{2}) человек нажимает «Принято»$")
    fun thePersonPressesTaken(time: String) {
        pressAt(time) { onNodeWithTag(TAG_ACTION_TAKEN).performClick() }
    }

    @Когда("^в (\\d{2}:\\d{2}) человек нажимает «Пропустить»$")
    fun thePersonPressesSkip(time: String) {
        pressAt(time) { onNodeWithTag(TAG_ACTION_SKIP).performClick() }
    }

    @Когда("^в (\\d{2}:\\d{2}) человек нажимает «Отложить \\d+ мин»$")
    fun thePersonPressesSnooze(time: String) {
        pressAt(time) { onNodeWithTag(TAG_ACTION_SNOOZE).performClick() }
    }

    @Тогда("^напоминание исчезает с экрана и из шторки$")
    fun theReminderLeavesTheScreenAndTheShade() {
        val intake = world.intakes.current()
        assertTrue(ReminderStand.state(world) is ReminderUiState.Closed, "the card must close")
        assertFalse(world.reminders.isNotificationShown(intake.id), "the shade card must be dismissed")
    }

    @И("^парная карточка на часах отзывается$")
    fun theWatchCardIsRevoked() {
        val intake = world.intakes.current()
        assertTrue(
            world.reminders.revokedWatchIntakeIds.contains(intake.id),
            "the watch card of the intake must be revoked (ЧАС-16)",
        )
    }

    @И("^напоминание исчезает с экрана$")
    fun theReminderLeavesTheScreen() {
        assertTrue(ReminderStand.state(world) is ReminderUiState.Closed, "the card must close")
    }

    @И("^в (.+?), (\\d{2}:\\d{2}) напоминание приходит снова$")
    fun theReminderComesAgainAt(date: String, time: String) {
        fireAt(date, time)
        assertTrue(ReminderStand.state(world) is ReminderUiState.Card, "the card must come back")
    }

    // --- Подпись откладывания из настройки -----------------------------------------------------

    @Когда("^наступает (.+?), (\\d{2}:\\d{2}) и напоминание показано$")
    fun thatMomentComesAndTheReminderIsShown(date: String, time: String) {
        fireAt(date, time)
        assertTrue(ReminderStand.state(world) is ReminderUiState.Card, "the card must be shown")
    }

    @Тогда("^вторая кнопка подписана «([^»]+)»$")
    fun theSecondButtonIsLabeled(label: String) {
        assertTrue(showsTag(TAG_ACTION_SNOOZE), "the snooze button must be on the card")
        assertTrue(showsText(label), "the snooze button must say «$label»")
    }

    @И("^нажатие на неё приносит напоминание снова в (.+?), (\\d{2}:\\d{2})$")
    fun pressingItBringsTheReminderBackAt(date: String, time: String) {
        val at = world.localMoment(date, time)
        ReminderStand.act(world) { onNodeWithTag(TAG_ACTION_SNOOZE).performClick() }
        val intake = world.intakes.current()
        assertEquals(world.clock.momentOf(at), world.reminders.alarmOf(intake.id), "the snooze must move the alarm")
        fireAt(date, time)
        assertTrue(ReminderStand.state(world) is ReminderUiState.Card, "the card must come back")
    }

    // --- Несколько приёмов на одно время -------------------------------------------------------

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» с приёмом в (\\d{2}:\\d{2}), доза (\\S+) капсул\\S+$")
    @Suppress("LongParameterList")
    fun theProfileHasACapsuleMedication(profile: String, name: String, time: String, dose: String) {
        addCapsuleMedication(profile, name, time, dose)
        world.intakes.pending(name, world.clock.today().atTime(LocalTime.parse(time)))
        world.reminders.reschedule(RescheduleReason.MANUAL)
    }

    @Тогда("^появляется одно напоминание со списком из двух приёмов$")
    fun oneReminderWithTwoIntakesAppears() {
        ReminderStand.open(world)
        val state = ReminderStand.state(world)
        assertTrue(state is ReminderUiState.Card, "the reminder must be shown")
        assertEquals(2, (state as ReminderUiState.Card).items.size, "both intakes belong to one card")
    }

    @И("^в списке строки «([^»]+)» и «([^»]+)»$")
    fun theListCarriesBothRows(first: String, second: String) {
        assertTrue(showsText(first), "the card must list $first")
        assertTrue(showsText(second), "the card must list $second")
        assertEquals(2, countTag(TAG_ROW), "the list must have exactly two rows")
    }

    @Но("^второго полноэкранного напоминания подряд не появляется$")
    fun noSecondFullScreenReminderAppears() {
        val slots = world.reminders.shownCards.map { it.slot }.distinct()
        assertEquals(1, slots.size, "intakes of one time are one full-screen card")
    }

    @Допустим("^напоминание со списком из приёмов «([^»]+)» и «([^»]+)» показано$")
    fun theReminderWithTheListIsShown(first: String, second: String) {
        val day = world.clock.today()
        addCapsuleMedicationIfMissing(second)
        listOf(first, second).forEach { name -> world.intakes.pending(name, day.atTime(LocalTime.parse(DAILY_TIME))) }
        world.reminders.reschedule(RescheduleReason.MANUAL)
        fireAtMoment(day.atTime(LocalTime.parse(DAILY_TIME)))
        assertTrue(ReminderStand.state(world) is ReminderUiState.Card, "the card must be shown")
    }

    @Когда("^в (\\d{2}:\\d{2}) человек отмечает «([^»]+)» принятым$")
    fun thePersonMarksThatRowTaken(time: String, name: String) {
        world.rememberState()
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        val taken = string(R.string.reminder_row_taken)
        ReminderStand.act(world) {
            onNode(
                hasText(taken) and hasAnyAncestor(hasTestTag(TAG_ROW) and hasAnyDescendant(hasText(name))),
            ).performClick()
        }
    }

    // «Приём «Л» считается принятым» объявлен в общем артефакте (ProfileMarkingSteps):
    // та же строка стоит в сценариях SKL-15, и два объявления погасили бы прогон целиком.

    @И("^приём «([^»]+)» остаётся неотмеченным$")
    fun theIntakeOfThatMedicationStaysPending(name: String) {
        assertEquals(IntakeStatus.PENDING, todayIntakeOf(name).status, "the row of $name must stay unmarked")
    }

    @И("^напоминание продолжает показывать строку «([^»]+)»$")
    fun theReminderKeepsShowingThatRow(name: String) {
        ReminderStand.open(world)
        val state = ReminderStand.state(world)
        assertTrue(state is ReminderUiState.Card, "the card must stay open")
        assertTrue(
            (state as ReminderUiState.Card).items.any { it.name == name },
            "the card must still carry the row of $name",
        )
        assertTrue(showsText(name), "the row of $name must be on the screen")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    /** Показ напоминания в его время: приём, будильник и доставка боевым путём. */
    private fun showReminder(date: String, time: String) {
        val name = world.medications.last
        val day = world.dateOf(date)
        world.intakes.pending(name, day.atTime(LocalTime.parse(time)))
        world.reminders.reschedule(RescheduleReason.MANUAL)
        fireAt(date, time)
    }

    private fun pressAt(time: String, press: ComposeUiTest.() -> Unit) {
        world.rememberState()
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        ReminderStand.act(world) { press() }
    }

    private fun fireAt(date: String, time: String) {
        fireAtMoment(world.localMoment(date, time))
    }

    private fun fireAtMoment(at: LocalDateTime) {
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
        ReminderStand.open(world)
    }

    private fun addCapsuleMedication(profile: String, name: String, time: String, dose: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = name,
            times = listOf(LocalTime.parse(time)),
        )
        world.medications.update(name) {
            it.copy(
                name = name,
                form = MedicationForm.CAPSULE,
                doseAmount = RussianDates.amount(dose),
            )
        }
    }

    private fun addCapsuleMedicationIfMissing(name: String) {
        if (!world.medications.has(name)) {
            addCapsuleMedication(world.profiles.active, name, DAILY_TIME, NO_DOSE)
        }
    }

    private fun todayIntakeOf(name: String) = requireNotNull(
        world.intakes.of(name).firstOrNull { it.scheduledAt.toLocalDate() == world.clock.today() },
    ) { "the scenario must have an intake of $name today" }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun showsText(text: String): Boolean {
        var found = false
        ReminderStand.ask(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun showsTag(tag: String): Boolean = countTag(tag) > 0

    private fun countTag(tag: String): Int {
        var count = 0
        ReminderStand.ask(world) {
            count = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
        }
        return count
    }

    private fun texts(): List<String> {
        var texts: List<String> = emptyList()
        ReminderStand.ask(world) {
            texts = onAllNodesWithTag(TAG_REMINDER, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
        }
        return texts
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            .orEmpty().map { it.text } + node.children.flatMap(::textsOf)

    private companion object {
        const val DAILY_TIME = "14:00"
        const val NO_DOSE = "1"

        /** Как называется плашка в макете: её на экране быть не должно (`ADR-0009`). */
        const val WATCH_CHIP_WORD = "Продублирован"
    }
}

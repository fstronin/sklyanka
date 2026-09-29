package ru.stohastika.sklyanka.acceptance.today

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.feature.reminder.ReminderUiState
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SKIP
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_TAKEN
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import java.time.LocalTime
import ru.stohastika.sklyanka.feature.today.R as TodayR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки композиции «карточка напоминания → Сегодня» (SKL-30, группа
 * `@composition`; заведены SKL-26).
 *
 * Прогон идёт в `app`, потому что сценарию нужны оба экрана сразу: «Сегодня» и карточка
 * напоминания живут в разных модулях, и только здесь они существуют одновременно
 * (`SKL-26/TDD.md §4.3`). Настоящий `ReminderViewModel` и «Сегодня» из мира собирает
 * [UndoAppProbe]; уход с экрана шаг воспроизводит вызовом точки выхода, потому что
 * жизненный цикл композиции между шагами стенд не воспроизводит (`TDD.md §15.5`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class UndoCompositionSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // --- Предыстория ----------------------------------------------------------------------

    @Допустим("^напоминание о нём показано$")
    fun theReminderOfItIsShown() {
        val intake = world.intakes.currentOrScheduled()
        fireAt(intake.scheduledAt)
    }

    @Допустим("^у профиля «([^»]+)» есть лекарства «([^»]+)», «([^»]+)» и «([^»]+)» с приёмом в (\\d{2}:\\d{2})$")
    fun theProfileHasThreeMedicationsAt(profile: String, first: String, second: String, third: String, time: String) {
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        listOf(first, second, third).forEach { medication ->
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

    @Допустим("^напоминание с тремя приёмами на (.+?), (\\d{2}:\\d{2}) показано$")
    fun theReminderWithThreeIntakesIsShown(date: String, time: String) {
        fireAt(world.localMoment(date, time))
        assertTrue(
            "the card must carry all three intakes",
            (UndoAppProbe.reminderState(world) as? ReminderUiState.Card)?.items?.size == 3,
        )
    }

    @Допустим(
        "^у профиля «([^»]+)» есть лекарство «([^»]+)» с приёмом (.+?) " +
            "в (\\d{2}:\\d{2}) и дозой (\\S+) таблет\\S*$",
    )
    fun theProfileHasAMedicationAtTheDateWithDose(
        profile: String,
        medication: String,
        date: String,
        time: String,
        dose: String,
    ) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(LocalTime.parse(time)),
        )
        world.medications.update(world.medicationNamed(medication)) {
            it.copy(doseAmount = RussianDates.amount(dose))
        }
        // Приём создаёт Предыстория, а не планировщик: окно генерации начинается
        // «сейчас», и приём в прошлом (09:00 при моменте 09:05) он бы не породил.
        world.intakes.pending(world.medicationNamed(medication), world.localMoment(date, time))
    }

    @Допустим("^на «Сегодня» показан снэкбар с отменой$")
    fun theUndoSnackbarIsShownOnToday() {
        val intake = world.intakes.current()
        world.undoMarks.publish(
            intakeIds = listOf(intake.id),
            result = MarkResult.of(intake.status) ?: MarkResult.TAKEN,
            medicationNames = listOfNotNull(world.medications.byId(intake.medicationId)?.name),
        )
        assertSnackbarOnToday()
    }

    // --- Действия -------------------------------------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «(Принято|Пропустить)» на карточке напоминания$")
    fun thePersonPressesOnTheReminderCard(date: String, time: String, button: String) {
        world.clock.moveTo(world.localMoment(date, time))
        UndoAppProbe.pressOnCard(
            world,
            if (button == context.getString(UiR.string.action_taken)) TAG_ACTION_TAKEN else TAG_ACTION_SKIP,
        )
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}(?::\\d{2})?) человек нажимает «Отменить» в снэкбаре$")
    fun thePersonPressesUndoInTheSnackbar(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        UndoAppProbe.today(world) { test ->
            test.onNodeWithText(context.getString(TodayR.string.today_undo_action), useUnmergedTree = true)
                .performClick()
        }
    }

    @Когда("^владелец телефона отмечает приём «([^»]+)» принятым$")
    fun theOwnerOfThePhoneMarksThatIntakeTaken(medication: String) {
        val name = world.medicationNamed(medication)
        UndoAppProbe.today(world) { test ->
            test.onNode(
                hasText(context.getString(UiR.string.action_taken)) and hasAnyAncestor(intakeCard(world, name)),
                useUnmergedTree = true,
            ).performClick()
        }
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}:\\d{2}) человек уходит на вкладку «Лекарства»$")
    fun thePersonLeavesToTheMedsTab(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        // Уход с «Сегодня» — конец окна отмены: та же точка выхода, которую зовёт
        // `TodayRoute` при снятии с композиции (`TDD.md §12.2`, §15.5).
        world.undoMarks.clear()
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}:\\d{2}) возвращается на «Сегодня»$")
    fun thePersonComesBackToToday(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
    }

    // --- Проверки -------------------------------------------------------------------------

    @Тогда("^карточка закрывается, и открывается «Сегодня»$")
    fun theCardClosesAndTodayOpens() {
        assertTrue("the card must close", UndoAppProbe.reminderState(world) is ReminderUiState.Closed)
        assertTrue("the undo window must wait on Today", world.undoMarks.live() != null)
    }

    @И("^на «Сегодня» показан снэкбар «([^»]+)»$")
    fun theSnackbarIsShownOnToday(text: String) = assertSnackbarOnToday(text)

    @Тогда("^показан снэкбар «([^»]+)»$")
    fun theSnackbarIsShown(text: String) = assertSnackbarOnToday(text)

    @Тогда("^снэкбара на «Сегодня» нет$")
    fun thereIsNoSnackbarOnToday() {
        assertTrue("the undo window must be over", world.undoMarks.live() == null)
        UndoAppProbe.today(world) { test ->
            test.onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @И("^действие «Отменить» оформлено акцентом профиля «([^»]+)»$")
    fun theUndoActionWearsTheAccentOfThatProfile(profile: String) {
        // Экран собирается в теме чужого профиля, и акцент берётся из неё же: компонент
        // о профиле не знает — механизм доказан `ProfileThemeTest`, а `SnackbarTest`
        // закрывает, что подпись действия читает именно акцент темы (ТDD.md §12.2).
        val accent = ProfileAccent.Foreign(ProfilePalette.of(world.profiles.get(profile).color))
        val captured = UndoAppProbe.todayAccent(world, accent) { test ->
            test.onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(1)
        }
        assertEquals("the undo action must wear the accent of «$profile»", accent.color, captured)
        assertTrue("the accent must differ from the owner's", captured != ProfileAccent.Owner.color)
    }

    // --- Обвязка --------------------------------------------------------------------------

    private fun fireAt(moment: java.time.LocalDateTime) {
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(moment)
        world.reminders.deliverThrough(moment)
        UndoAppProbe.openCard(world)
        assertTrue("the reminder must be shown", UndoAppProbe.reminderState(world) is ReminderUiState.Card)
    }

    private fun assertSnackbarOnToday(expected: String? = null) {
        val texts = mutableListOf<String>()
        UndoAppProbe.today(world) { test ->
            val node = test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode()
            texts += textsOf(node)
        }
        assertTrue("the snackbar must be on Today: $texts", texts.isNotEmpty())
        if (expected != null) {
            val wanted = screenText(expected)
            assertTrue("the snackbar must read «$wanted»: $texts", wanted in texts)
        }
    }

    /** Карточка приёма названного лекарства: имя ищется среди потомков (как в стенде «Сегодня»). */
    private fun intakeCard(world: AcceptanceWorld, medication: String) =
        hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(displayName(world, medication)))

    private fun displayName(world: AcceptanceWorld, medication: String): String =
        world.medications.get(world.medicationNamed(medication)).name

    /**
     * Текст снэкбара в терминах экрана: сценарий называет лекарство по-русски, а модель —
     * `med-N` (НФТ-19); подстановка идёт разрешением падежей, как у остальных шагов.
     */
    private fun screenText(scenarioText: String): String {
        if (" — " !in scenarioText) return scenarioText
        val name = scenarioText.substringBefore(" — ")
        val resolved = runCatching { world.medicationNamed(name) }.getOrNull() ?: return scenarioText
        return scenarioText.replaceFirst(name, displayName(world, resolved))
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }
}

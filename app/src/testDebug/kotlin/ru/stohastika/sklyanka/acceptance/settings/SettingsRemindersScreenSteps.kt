package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.reminder.SnoozeIntervals
import ru.stohastika.sklyanka.feature.reminder.ReminderActions
import ru.stohastika.sklyanka.feature.reminder.ReminderRow
import ru.stohastika.sklyanka.feature.reminder.ReminderScreen
import ru.stohastika.sklyanka.feature.reminder.ReminderUiState
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SNOOZE
import ru.stohastika.sklyanka.ui.component.TAG_LIST_PICKER_OPTION
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDateTime
import ru.stohastika.sklyanka.feature.reminder.R as ReminderR
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * Экранные шаги приёмки настроек «Звук» и «Отложить на» (SKL-78, группа `@screen`).
 *
 * Шаги живут в `app`, потому что сценарий последнего примера говорит и о карточке
 * напоминания (`feature-reminder`), и о настройках (`feature-settings`) — вместе их
 * видит только `app`. Экран настроек собирается настоящим `SettingsRoute` на мире
 * сценария ([SettingsRemindersStand]); подпись кнопки откладывания берётся из того же
 * источника, что у продукта, — настройки, а не константы.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class SettingsRemindersScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Предыстория ---------------------------------------------------------------------------

    @Допустим("^блок «Напоминания» виден$")
    fun theRemindersBlockIsVisible() {
        SettingsRemindersStand.ask(world) {
            onNodeWithText(string(SettingsR.string.settings_sound), useUnmergedTree = true).assertExists()
        }
    }

    // --- Звук ----------------------------------------------------------------------------------

    @Когда("^человек нажимает строку «Звук»$")
    fun thePersonTapsTheSoundRow() {
        SettingsRemindersStand.act(world) {
            onNodeWithText(string(SettingsR.string.settings_sound), useUnmergedTree = true)
                .performScrollTo()
                .performClick()
        }
    }

    @Тогда("^приложение открывает системные настройки уведомлений приложения$")
    fun theAppOpensTheSystemNotificationSettings() {
        assertEquals(
            "the «Звук» row must lead to the system notification settings (ОВ-98)",
            1,
            SettingsRemindersStand.soundOpened,
        )
    }

    @Когда("^человек смотрит на строку «Звук»$")
    fun thePersonLooksAtTheSoundRow() {
        SettingsRemindersStand.ask(world) {
            onNodeWithText(string(SettingsR.string.settings_sound), useUnmergedTree = true).performScrollTo()
        }
    }

    @Тогда("^строка не показывает выдуманного названия мелодии$")
    fun theRowShowsNoFakeMelody() {
        SettingsRemindersStand.ask(world) {
            assertEquals(0, onAllNodesWithText(FAKE_SOUND_VALUE, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @И("^строка не обещает выбора звука внутри приложения$")
    fun theRowDoesNotPromiseAnInAppPicker() {
        val offenders = shownTexts().filter { text -> SOUND_CHOICE_KEYS.any { text.contains(it, ignoreCase = true) } }
        assertEquals("the sound must not be chosen inside the app (ПМ-15)", emptyList<String>(), offenders)
    }

    @И("^строка подписана «Системные настройки уведомлений» и показывает шеврон$")
    fun theRowNamesItsDestination() {
        assertEquals("Системные настройки уведомлений", string(SettingsR.string.settings_sound_caption))
        SettingsRemindersStand.ask(world) {
            // Шеврон — не текст: о переходе говорит нажимаемость строки вместе с подписью.
            onNode(hasText(string(SettingsR.string.settings_sound_caption)) and hasClickAction()).assertExists()
        }
    }

    // --- Индивидуального звука нет -------------------------------------------------------------

    @Когда("^человек проходит по экрану настроек$")
    fun thePersonWalksTheSettingsScreen() {
        shownTexts()
    }

    @Тогда("^выбора звука для отдельного лекарства нигде нет$")
    fun thereIsNoPerMedicationSound() {
        val offenders = shownTexts().filter { text ->
            PER_MEDICATION_SOUND_KEYS.any { text.contains(it, ignoreCase = true) }
        }
        assertEquals("the sound is one per app (НАП-27)", emptyList<String>(), offenders)
    }

    // --- Отложить на ---------------------------------------------------------------------------

    @Допустим("^человек ещё не менял интервал откладывания$")
    fun thePersonHasNotChangedTheSnoozeInterval() {
        assertEquals(
            "the default interval must be untouched",
            SnoozeIntervals.DEFAULT,
            SettingsRemindersStand.state(world).reminders.snoozeMinutes,
        )
    }

    @Когда("^человек смотрит на строку «Отложить на»$")
    fun thePersonLooksAtTheSnoozeRow() {
        SettingsRemindersStand.ask(world) {
            onNodeWithText(string(SettingsR.string.settings_snooze), useUnmergedTree = true).performScrollTo()
        }
    }

    @Тогда("^она показывает «15 минут»$")
    fun itShowsFifteenMinutes() {
        assertSnoozeShows(SnoozeIntervals.DEFAULT)
    }

    @Допустим("^человек открыл выбор интервала откладывания$")
    fun thePersonOpenedTheSnoozePicker() {
        openSnoozePicker()
    }

    @Когда("^человек открывает выбор интервала откладывания$")
    fun thePersonOpensTheSnoozePicker() {
        openSnoozePicker()
    }

    @Когда("^человек выбирает «(.+)»$")
    fun thePersonChoosesOption(label: String) {
        SettingsRemindersStand.act(world) {
            // Вариант ищется по своему тегу и подписи: та же подпись стоит значением
            // строки «Отложить на», и поиск по одному тексту нашёл бы две.
            onNode(hasTestTag(TAG_LIST_PICKER_OPTION) and hasText(label)).performClick()
        }
    }

    @Тогда("^строка «Отложить на» показывает «(.+)» без перезахода на экран$")
    fun theSnoozeRowShowsWithoutReentering(label: String) {
        val minutes = minutesOf(label)
        assertEquals(
            "the interval must be applied without re-entering the screen",
            minutes,
            SettingsRemindersStand.state(world).reminders.snoozeMinutes,
        )
        assertSnoozeShows(minutes)
    }

    @И("^выбранный интервал сохранён$")
    fun theChosenIntervalIsStored() {
        val stored = runBlocking { world.reminders.settingsStore.read().snoozeMinutes }
        assertEquals(SettingsRemindersStand.state(world).reminders.snoozeMinutes, stored)
    }

    @Тогда("^ему доступны ровно 5, 10, 15, 30 и 60 минут$")
    fun exactlyThoseIntervalsAreOffered() {
        assertEquals(listOf(5, 10, 15, 30, 60), SnoozeIntervals.OPTIONS)
        SettingsRemindersStand.ask(world) {
            val options = onAllNodesWithTag(TAG_LIST_PICKER_OPTION, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(5, options.size)
            SnoozeIntervals.OPTIONS.forEach { minutes ->
                onNode(hasTestTag(TAG_LIST_PICKER_OPTION) and hasText(labelOf(minutes))).assertExists()
            }
        }
    }

    @И("^других значений в выборе нет$")
    fun thereAreNoOtherValues() {
        SettingsRemindersStand.ask(world) {
            val texts = onAllNodesWithTag(TAG_LIST_PICKER_OPTION, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
            assertEquals(SnoozeIntervals.OPTIONS.map(::labelOf).toSet(), texts.toSet())
        }
    }

    // --- Подпись кнопки на карточке напоминания -------------------------------------------------

    @Допустим("^человек выбрал интервал «(.+)»$")
    fun thePersonChoseTheInterval(label: String) {
        val minutes = minutesOf(label)
        runBlocking { world.reminders.settingsStore.setSnoozeMinutes(minutes) }
    }

    @Когда("^приходит напоминание об этом приёме$")
    fun theReminderArrives() = Unit

    @Тогда("^кнопка откладывания подписана «(.+)»$")
    fun theSnoozeButtonIsLabelled(label: String) {
        val minutes = world.reminders.settings().snoozeMinutes
        val expected = context.getString(ReminderR.string.reminder_snooze, minutes)
        assertEquals("the scenario must name the caption built from the setting", expected, label)
        runComposeUiTest {
            setContent {
                SklyankaTheme { ReminderScreen(state = reminderCard(minutes), actions = noActions()) }
            }
            waitForIdle()
            val node = onNodeWithTag(TAG_ACTION_SNOOZE, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("the snooze button must be labelled «$label»: ${textsOf(node)}", label in textsOf(node))
        }
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun shownTexts(): List<String> {
        var texts: List<String> = emptyList()
        SettingsRemindersStand.ask(world) { texts = textsOf(onRoot(useUnmergedTree = true).fetchSemanticsNode()) }
        return texts
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::textsOf)

    private fun string(id: Int): String = context.getString(id)

    private fun labelOf(minutes: Int): String =
        context.resources.getQuantityString(SettingsR.plurals.settings_snooze_minutes, minutes, minutes)

    private fun minutesOf(label: String): Int = requireNotNull(
        SnoozeIntervals.OPTIONS.firstOrNull { labelOf(it) == label },
    ) {
        "unknown interval label: $label"
    }

    private fun openSnoozePicker() {
        SettingsRemindersStand.act(world) {
            onNodeWithText(string(SettingsR.string.settings_snooze), useUnmergedTree = true)
                .performScrollTo()
                .performClick()
        }
        assertTrue("the picker must open", SettingsRemindersStand.state(world).snoozePickerVisible)
    }

    private fun assertSnoozeShows(minutes: Int) {
        SettingsRemindersStand.ask(world) {
            onNodeWithText(labelOf(minutes), useUnmergedTree = true).assertExists()
        }
    }

    private fun reminderCard(minutes: Int) = ReminderUiState.Card(
        scheduledAt = LocalDateTime.of(2026, 9, 25, 18, 15),
        items = listOf(
            ReminderRow(
                intakeId = "intake-reminder",
                name = "Магний B6",
                form = MedicationForm.TABLET,
                doseAmount = BigDecimal.ONE,
                strength = null,
                strengthUnit = null,
            ),
        ),
        snoozeMinutes = minutes,
        foreignProfileName = null,
    )

    private fun noActions() = ReminderActions(
        onTaken = {},
        onSkipped = {},
        onTakenAll = {},
        onSkippedAll = {},
        onSnooze = {},
    )

    private companion object {
        /** Прежнее фальшивое значение строки «Звук», снятое ОВ-98. */
        const val FAKE_SOUND_VALUE = "Мягкий сигнал"

        /** Обещание выбора звука внутри приложения. */
        val SOUND_CHOICE_KEYS = listOf("выбрать звук", "выбор звука", "мелодия")

        /** Выбор звука для отдельного лекарства (вторая версия, ADR-0004). */
        val PER_MEDICATION_SOUND_KEYS = listOf("звук для", "звук лекарства")
    }
}

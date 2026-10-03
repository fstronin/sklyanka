package ru.stohastika.sklyanka.acceptance.reminder

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.ReminderActions
import ru.stohastika.sklyanka.feature.reminder.ReminderScreen
import ru.stohastika.sklyanka.feature.reminder.ReminderUiState
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SKIP
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SNOOZE
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_TAKEN
import ru.stohastika.sklyanka.feature.reminder.TAG_REMINDER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Duration
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки подписи действий карточки напоминания (SKL-61, `@screen`).
 *
 * Состояние собирает [ReminderStand] — настоящий `ReminderViewModel` на мире сценария;
 * шаги только нажимают и читают собранную композицию. Системный шрифт задаётся подменой
 * `LocalDensity` — тем же приёмом, что в `ReminderScreenTest` и `Sk93DenseScreenSteps`;
 * «подпись не переносится» читается по `TextLayoutResult`, а не по копии подсчёта.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class ReminderActionsScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var scale: Float = 1f

    /** Нажатие откладывания дошло до приложения: будильник до нажатия и интервал. */
    private var alarmBeforeSnooze: java.time.Instant? = null
    private var snoozeMinutesBeforePress: Int = 0

    // --- Предыстория ---------------------------------------------------------------

    @Допустим("^наступило время приёма в (\\d{2}:\\d{2})$")
    fun theIntakeTimeHasCome(time: String) {
        val name = "med-61"
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            times = listOf(LocalTime.parse(time)),
        )
        val at = world.clock.today().atTime(LocalTime.parse(time))
        world.intakes.pending(name, at)
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
        ReminderStand.open(world)
    }

    @И("^открыто полноэкранное напоминание$")
    fun theFullScreenReminderIsOpen() {
        assertTrue(
            "the reminder card must be shown",
            ReminderStand.state(world) is ReminderUiState.Card,
        )
    }

    @Допустим("^системный шрифт — (\\d+) %$")
    fun theSystemFontIs(percent: Int) {
        scale = percent / PERCENT
    }

    @Допустим("^в настройках выбран интервал (\\d+) минут$")
    fun theSnoozeIntervalIs(minutes: Int) {
        runBlocking { world.reminders.settingsStore.setSnoozeMinutes(minutes) }
        // Карточка перечитывает настройку при открытии — как в продукте.
        ReminderStand.open(world)
        assertEquals(minutes, card().snoozeMinutes)
    }

    // --- Действия ------------------------------------------------------------------

    @Когда("^человек смотрит на действия напоминания$")
    fun thePersonLooksAtTheActions() {
        reminder { }
    }

    @Когда("^человек смотрит на карточку напоминания$")
    fun thePersonLooksAtTheReminderCard() {
        reminder { }
    }

    @Когда("^человек нажимает «Отложить (\\d+) мин»$")
    fun thePersonPressesSnooze(minutes: Int) {
        val intake = world.intakes.current()
        alarmBeforeSnooze = world.reminders.alarmOf(intake.id)
        snoozeMinutesBeforePress = card().snoozeMinutes
        ReminderStand.act(world) { onNodeWithTag(TAG_ACTION_SNOOZE).performClick() }
    }

    // --- Проверки: раскладка ---------------------------------------------------------

    @Тогда("^«Отложить (\\d+) мин» и «Пропустить» стоят в одном ряду$")
    fun theTwoActionsShareOneRow(minutes: Int) {
        reminder { test ->
            val snooze = bounds(test, TAG_ACTION_SNOOZE)
            val skip = bounds(test, TAG_ACTION_SKIP)
            assertTrue(
                "the actions must share one row: snooze=$snooze, skip=$skip",
                snooze.top < skip.bottom && skip.top < snooze.bottom,
            )
            assertTrue("«Отложить» must stand before «Пропустить»", snooze.left < skip.left)
        }
    }

    @И("^каждая подпись — одной строкой$")
    fun everyLabelIsOneLine() {
        reminder { test -> assertLabelsAreOneLine(test) }
    }

    @Тогда("^«Отложить (\\d+) мин» стоит строкой выше «Пропустить»$")
    fun theSnoozeStandsAboveTheSkip(minutes: Int) {
        reminder { test ->
            val snooze = bounds(test, TAG_ACTION_SNOOZE)
            val skip = bounds(test, TAG_ACTION_SKIP)
            assertTrue(
                "«Отложить $minutes мин» must stand above «Пропустить»: snooze=$snooze, skip=$skip",
                skip.top >= snooze.bottom,
            )
        }
    }

    @И("^каждая кнопка занимает всю ширину$")
    fun everyButtonTakesTheFullWidth() {
        reminder { test ->
            val snooze = bounds(test, TAG_ACTION_SNOOZE)
            val skip = bounds(test, TAG_ACTION_SKIP)
            val screen = bounds(test, TAG_REMINDER)
            assertEquals("both stacked buttons must share one width", snooze.width, skip.width, WIDTH_TOLERANCE)
            assertTrue(
                "the buttons must take the full width: snooze=$snooze, screen=$screen",
                snooze.width >= screen.width * FULL_WIDTH_SHARE,
            )
        }
    }

    @И("^ни одна подпись не переносится$")
    fun noLabelWraps() {
        reminder { test -> assertLabelsAreOneLine(test) }
    }

    @Тогда("^подпись говорит «Отложить (\\d+) мин» целиком$")
    fun theLabelSaysTheWholeInterval(minutes: Int) {
        val expected = context.getString(R.string.reminder_snooze, minutes)
        reminder { test ->
            val node = test.onNodeWithTag(TAG_ACTION_SNOOZE, useUnmergedTree = true).fetchSemanticsNode()
            val texts = textsOf(node)
            assertTrue("the snooze button must say «$expected»: $texts", expected in texts)
            val layouts = textLayoutsOf(node)
            assertTrue("the label must have a text layout", layouts.isNotEmpty())
            layouts.forEach { layout -> assertEquals("the label must fit one line", 1, layout.lineCount) }
        }
    }

    @Тогда("^действий ровно три: «Принято», откладывание, «Пропустить»$")
    fun thereAreExactlyThreeActions() {
        reminder { test ->
            listOf(TAG_ACTION_TAKEN, TAG_ACTION_SNOOZE, TAG_ACTION_SKIP).forEach { tag ->
                assertEquals(
                    "the action $tag must be on the card exactly once",
                    1,
                    test.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size,
                )
            }
            val texts = textsOf(
                test.onNodeWithTag(TAG_REMINDER, useUnmergedTree = true).fetchSemanticsNode(),
            )
            assertTrue("«Принято» must be on the card: $texts", context.getString(R.string.reminder_row_taken) in texts)
            assertTrue(
                "«Пропустить» must be on the card: $texts",
                context.getString(UiR.string.action_skip) in texts,
            )
        }
    }

    @И("^«Принято» стоит выше ряда откладывания и пропуска$")
    fun theTakenStandsAboveTheRow() {
        reminder { test ->
            val taken = bounds(test, TAG_ACTION_TAKEN)
            val snooze = bounds(test, TAG_ACTION_SNOOZE)
            assertTrue(
                "«Принято» must stand above the row: taken=$taken, snooze=$snooze",
                taken.bottom <= snooze.top,
            )
        }
    }

    @Тогда("^приложение получает нажатие откладывания$")
    fun theAppReceivesTheSnoozePress() {
        val intake = world.intakes.current()
        val alarm = world.reminders.alarmOf(intake.id)
        val expected = world.clock.momentOf(
            world.clock.nowLocal().plus(Duration.ofMinutes(snoozeMinutesBeforePress.toLong())),
        )
        assertTrue(
            "the app must reschedule the reminder: before=$alarmBeforeSnooze, after=$alarm",
            alarm != null && alarm != alarmBeforeSnooze,
        )
        assertEquals("the snooze must move the alarm by the chosen interval", expected, alarm)
        assertTrue("the card must close after the snooze", ReminderStand.state(world) is ReminderUiState.Closed)
    }

    // --- Обвязка --------------------------------------------------------------------

    private fun card(): ReminderUiState.Card = ReminderStand.state(world) as ReminderUiState.Card

    private fun reminder(question: (ComposeUiTest) -> Unit) {
        val state = card()
        val previous = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(scale)
        try {
            runComposeUiTest {
                setContent {
                    val base = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = base.density, fontScale = scale),
                    ) {
                        SklyankaTheme(dark = true) {
                            ReminderScreen(
                                state = state,
                                actions = ReminderActions(
                                    onTaken = {},
                                    onSkipped = {},
                                    onTakenAll = {},
                                    onSkippedAll = {},
                                    onSnooze = {},
                                ),
                            )
                        }
                    }
                }
                question(this)
            }
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    private fun bounds(test: ComposeUiTest, tag: String) =
        test.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertLabelsAreOneLine(test: ComposeUiTest) {
        listOf(TAG_ACTION_SNOOZE, TAG_ACTION_SKIP).forEach { tag ->
            val layouts = textLayoutsOf(test.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())
            assertTrue("the action $tag must carry text", layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertEquals("the label of $tag must fit one line", 1, layout.lineCount)
                assertTrue("the label of $tag must not be cut", !layout.didOverflowHeight)
            }
        }
    }

    private fun textLayoutsOf(node: SemanticsNode): List<TextLayoutResult> {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        node.children.forEach { child -> layouts += textLayoutsOf(child) }
        return layouts
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::textsOf)

    private companion object {
        const val PERCENT = 100f
        const val WIDTH_TOLERANCE = 0.5f

        /** Стекнутая кнопка шире половины экрана: половина — это не «во всю ширину». */
        const val FULL_WIDTH_SHARE = 0.7f
    }
}

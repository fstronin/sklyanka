package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSION_STATE
import java.time.LocalTime

/**
 * Экранные шаги приёмки диагностики напоминаний (SKL-13).
 *
 * Первый уровень «Проверки напоминаний» переработан задачей SKL-102 (ОВ-126): журнал,
 * счётчики пересборки, «Проверить сейчас» и построчные разрешения убраны, и их подача
 * проверяется шагами SKL-102. Здесь остаётся только то, чем пользуются соседние
 * сценарии: открытие экрана по времени и ручная проверка состояния разрешения
 * «Не беспокоить» (SKL-13, `@manual`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class ReminderDiagnosticsSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Допустим("^напоминание о приёме «([^»]+)» (.+?), (\\d{2}:\\d{2}) пришло в (\\d{2}):(\\d{2}):(\\d{2})$")
    fun theReminderCameLate(name: String, date: String, time: String, hour: String, minute: String, second: String) {
        val day = world.dateOf(date)
        val planned = day.atTime(LocalTime.parse(time))
        world.intakes.pending(name, planned)
        world.intakes.pending(name, day.plusDays(1).atTime(LocalTime.parse(time)))
        world.reminders.reschedule(RescheduleReason.MANUAL)
        val fired = world.localMoment(date, "$hour:$minute").withSecond(second.toInt())
        world.clock.moveTo(fired)
        world.reminders.deliverDue()
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек открывает диагностику напоминаний$")
    fun thePersonOpensTheDiagnosticsAt(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        DiagnosticsStand.ask(world) { assertTag(TAG_DIAGNOSTICS) }
    }

    @И("^состояние доступа к политике «Не беспокоить» показано как невыданное$")
    fun theDndStateIsDenied() {
        assertPermissionRow(ReminderPermission.DND_POLICY_ACCESS, PermissionState.DENIED)
    }

    private fun assertPermissionRow(permission: ReminderPermission, expected: PermissionState) {
        val state = DiagnosticsStand.state(world)
        assertEquals(expected, state.snapshot?.permissions?.get(permission), "the state of $permission")
        assertTrue(hasText(string(permissionLabel(permission))), "the row of $permission must be shown")
        assertTrue(permissionStateWords().contains(string(stateLabel(expected))), "the state must be named in words")
    }

    private fun permissionLabel(permission: ReminderPermission): Int = when (permission) {
        ReminderPermission.NOTIFICATIONS -> R.string.diagnostics_permission_notifications
        ReminderPermission.NOTIFICATION_CHANNEL -> R.string.diagnostics_permission_channel
        ReminderPermission.EXACT_ALARMS -> R.string.diagnostics_permission_exact_alarms
        ReminderPermission.FULL_SCREEN_INTENT -> R.string.diagnostics_permission_full_screen
        ReminderPermission.DND_POLICY_ACCESS -> R.string.diagnostics_permission_dnd
        ReminderPermission.BOOT_RECEIVER -> R.string.diagnostics_permission_boot
        ReminderPermission.AUTOSTART -> R.string.diagnostics_permission_autostart
    }

    private fun stateLabel(state: PermissionState): Int = when (state) {
        PermissionState.GRANTED -> R.string.diagnostics_state_granted
        PermissionState.DENIED -> R.string.diagnostics_state_denied
        PermissionState.UNKNOWN -> R.string.diagnostics_state_unknown
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun hasText(text: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun permissionStateWords(): List<String> {
        var words: List<String> = emptyList()
        DiagnosticsStand.ask(world) {
            words = onAllNodesWithTag(TAG_PERMISSION_STATE, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> textsOf(node) }
        }
        return words
    }

    private fun textsOf(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            .orEmpty().map { it.text } + node.children.flatMap(::textsOf)

    private fun androidx.compose.ui.test.ComposeUiTest.assertTag(tag: String) = onNodeWithTag(tag).assertExists()
}

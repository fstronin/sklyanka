package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.ReminderCheckOutcome
import ru.stohastika.sklyanka.feature.reminder.TAG_ALL_GRANTED
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_EXPORT_JOURNAL
import ru.stohastika.sklyanka.feature.reminder.TAG_FIX
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_ROW
import ru.stohastika.sklyanka.feature.reminder.TAG_LAST_SHORT
import ru.stohastika.sklyanka.feature.reminder.TAG_LAST_SHOWN
import ru.stohastika.sklyanka.feature.reminder.TAG_NEXT_SHORT
import ru.stohastika.sklyanka.feature.reminder.TAG_RELIABLE
import ru.stohastika.sklyanka.feature.reminder.TAG_RESCHEDULE
import ru.stohastika.sklyanka.feature.reminder.TAG_SUPPORT_TOGGLE
import ru.stohastika.sklyanka.feature.reminder.TAG_WEEK_SUMMARY
import java.time.LocalTime

/**
 * Экранные шаги приёмки «Проверки напоминаний» (SKL-79, группы `@screen`/`@support`).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране; состояние собирает
 * [DiagnosticsStand] — настоящий `DiagnosticsViewModel` на мире сценария. Проверки первого
 * уровня идут через `askRaw`: свёрнутость блока «Для поддержки» — часть контракта.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class DiagnosticsCheckSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Предыстория ---------------------------------------------------------------------------

    @Допустим("^человек открыл «Проверку напоминаний» из настроек$")
    fun thePersonOpenedTheCheck() = Unit

    @Допустим("^все разрешения выданы, и напоминания поставлены$")
    fun everythingIsGrantedAndPlanned() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
    }

    @Допустим("^следующее напоминание — о «([^»]+)» (.+?) в (\\d{2}:\\d{2})$")
    fun theNextReminderIs(name: String, date: String, time: String) {
        addMedication(name)
        val moment = world.localMoment(date, time)
        world.intakes.pending(name, moment)
        world.reminders.reschedule(RescheduleReason.MANUAL)
    }

    @Допустим("^последнее напоминание о «([^»]+)» сработало вовремя (.+?) в (\\d{2}:\\d{2})$")
    fun theLastReminderWasOnTime(name: String, date: String, time: String) {
        val medicationId = addMedication(name)
        val planned = world.clock.momentOf(world.localMoment(date, time))
        record(
            ReminderLogEntry(
                occurredAt = planned,
                event = ReminderEvent.SHOWN,
                intakeId = LAST_INTAKE,
                medicationId = medicationId,
                scheduledAt = planned,
                firedAt = planned,
                deviationSeconds = 0,
                channel = ReminderChannel.REGULAR,
                delivered = true,
            ),
        )
    }

    @Допустим("^разрешение «(.+)» не выдано$")
    fun thePermissionIsDenied(label: String) {
        val permission = permissionOf(label)
        world.system.deny(permission)
        assertTrue(permission != ReminderPermission.AUTOSTART, "autostart is not a denial (НАП-20)")
    }

    @Допустим("^уведомления не выданы$")
    fun theNotificationsAreDenied() {
        world.system.deny(ReminderPermission.NOTIFICATIONS)
    }

    @Допустим("^состояние автозапуска и энергосбережения EMUI программно не читается$")
    fun theAutostartIsUnreadable() {
        world.system.set(ReminderPermission.AUTOSTART, PermissionState.UNKNOWN)
    }

    @Допустим("^за последние 7 дней напоминания сработали (\\d+) раз, и (\\d+) приёма пропущены$")
    fun theWeekSummaryPremise(fired: Int, missed: Int) {
        val now = world.clock.now()
        repeat(fired) { index ->
            record(
                ReminderLogEntry(
                    occurredAt = now.minusSeconds((index + 1).toLong()),
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-week-$index",
                    deviationSeconds = 0,
                ),
            )
        }
        repeat(missed) { index ->
            record(
                ReminderLogEntry(
                    occurredAt = now.minusSeconds((index + 1).toLong()),
                    event = ReminderEvent.MISSED_SWEEP,
                    intakeId = "intake-missed-$index",
                ),
            )
        }
    }

    @Допустим("^проверка напоминаний не может завершиться$")
    fun theCheckCannotFinish() {
        world.failReminderCheck = true
    }

    @Допустим("^блок «Для поддержки» свёрнут$")
    fun theSupportBlockIsCollapsed() {
        assertFalse(DiagnosticsStand.state(world).supportExpanded, "the support block must start collapsed")
    }

    @Допустим("^человек раскрыл «Для поддержки»$")
    fun thePersonExpandedTheSupport() {
        DiagnosticsStand.expandSupport(world)
    }

    // --- Действия ------------------------------------------------------------------------------

    @Когда("^человек смотрит на «Проверку напоминаний»$")
    fun thePersonLooksAtTheCheck() {
        DiagnosticsStand.askRaw(world) { onNodeWithTag(TAG_DIAGNOSTICS).assertExists() }
    }

    @Когда("^человек нажимает «Для поддержки»$")
    fun thePersonTapsTheSupport() {
        DiagnosticsStand.actRaw(world) { onNodeWithTag(TAG_SUPPORT_TOGGLE).performScrollTo().performClick() }
    }

    @Когда("^человек выдаёт уведомления в системных настройках и возвращается$")
    fun thePersonGrantsNotificationsAndReturns() {
        world.system.grant(ReminderPermission.NOTIFICATIONS)
        DiagnosticsStand.refresh(world)
    }

    @Когда("^человек нажимает «Исправить» у строки «(.+?)»$")
    fun thePersonFixesTheRow(label: String) {
        val permission = permissionOf(label)
        val index = ReminderPermission.entries
            .filter { it != ReminderPermission.BOOT_RECEIVER }
            .indexOf(permission)
        assertTrue(index >= 0, "the row «$label» must have a fix action")
        DiagnosticsStand.actRaw(world) {
            onAllNodesWithTag(TAG_FIX)[index].performScrollTo().performClick()
        }
    }

    @Когда("^человек нажимает «Выгрузить журнал»$")
    fun thePersonExportsTheJournal() {
        DiagnosticsStand.actRaw(world) { onNodeWithTag(TAG_EXPORT_JOURNAL).performScrollTo().performClick() }
    }

    @Когда("^человек смотрит на строки журнала$")
    fun thePersonLooksAtTheJournalRows() {
        seedAnonymousEntry()
        DiagnosticsStand.refresh(world)
        DiagnosticsStand.expandSupport(world)
        DiagnosticsStand.askRaw(world) { onNodeWithTag(TAG_DIAGNOSTICS).assertExists() }
    }

    @Когда("^человек открывает экран из настроек$")
    fun thePersonOpensTheScreen() {
        DiagnosticsStand.askRaw(world) { onNodeWithTag(TAG_DIAGNOSTICS).assertExists() }
    }

    @Когда("^человек нажимает системную кнопку «Назад»$")
    fun thePersonPressesBack() {
        val back = context.getString(ru.stohastika.sklyanka.ui.R.string.action_back)
        DiagnosticsStand.actRaw(world) { onNodeWithText(back).performClick() }
    }

    // --- Проверки первого уровня ---------------------------------------------------------------

    @Тогда("^показано «Всё в порядке»$")
    fun everythingIsFine() {
        assertTrue(hasTag(TAG_RELIABLE), "the short answer must be shown when everything is fine")
        assertTrue(hasText(string(R.string.diagnostics_reliable)), "the answer must be in words")
    }

    @И("^показано следующее напоминание простыми словами$")
    fun theNextIsInSimpleWords() {
        assertTrue(hasTag(TAG_NEXT_SHORT), "the next reminder must be on the first level")
        assertTrue(hasText(string(R.string.diagnostics_next_plan)), "the time must be labelled as a plan")
    }

    @И("^вместо списка разрешений стоит одна строка «(.+?)»$")
    fun thePermissionsCollapseToOneLine(line: String) {
        assertTrue(hasTag(TAG_ALL_GRANTED), "granted permissions must collapse to a single line")
        assertTrue(hasText(line), "the line must say «$line»")
    }

    @Но("^журнала срабатываний и служебных счётчиков на первом уровне не видно$")
    fun theTechnicalDetailsAreHidden() {
        assertFalse(hasTag(TAG_JOURNAL_ROW), "journal rows must not be on the first level")
        assertFalse(hasTag(TAG_RESCHEDULE), "the rebuild counters must not be on the first level")
    }

    @Тогда("^внизу экрана есть строка «Для поддержки»$")
    fun theSupportRowIsThere() {
        assertTrue(hasTag(TAG_SUPPORT_TOGGLE), "the support row must be at the bottom")
        assertTrue(hasText(string(R.string.diagnostics_support)), "the row must be named «Для поддержки»")
    }

    @И("^содержимое блока свёрнуто$")
    fun theSupportContentIsCollapsed() {
        assertFalse(hasTag(TAG_LAST_SHOWN), "the technical report must be collapsed")
        assertFalse(hasTag(TAG_JOURNAL_ROW), "the journal must be collapsed")
    }

    @Тогда("^у строки «(.+)» сказано, что именно не так$")
    fun theRowSaysWhatIsWrong(label: String) {
        assertTrue(hasText(label), "the row «$label» must be shown")
        assertTrue(hasText(string(R.string.diagnostics_state_denied)), "the row must say what is wrong")
    }

    @И("^у строки есть действие «(.+?)»$")
    fun theRowOffersAFix(action: String) {
        assertTrue(hasTag(TAG_FIX), "the row must offer a fix")
        assertTrue(hasText(action), "the action must be named «$action»")
    }

    @Тогда("^приложение открывает системные настройки уведомлений$")
    fun theAppOpensNotificationSettings() {
        assertEquals(
            ReminderPermission.NOTIFICATIONS,
            DiagnosticsStand.fixed,
            "«Исправить» у уведомлений must lead to the system notification settings",
        )
    }

    @Тогда("^строка «(.+?)» говорит «(.+?)»$")
    fun theRowSaysInWords(label: String, words: String) {
        assertTrue(hasText(label), "the row «$label» must be shown")
        assertTrue(hasTextIgnoringCase(words), "the row must say «$words»")
    }

    @И("^действие «(.+?)» ведёт в системные настройки запуска приложений$")
    fun theFixLeadsToAutostart(action: String) {
        assertTrue(hasText(action), "the action must be named «$action»")
        val index = ReminderPermission.entries
            .filter { it != ReminderPermission.BOOT_RECEIVER }
            .indexOf(ReminderPermission.AUTOSTART)
        DiagnosticsStand.actRaw(world) {
            onAllNodesWithTag(TAG_FIX)[index].performScrollTo().performClick()
        }
        assertEquals(
            ReminderPermission.AUTOSTART,
            DiagnosticsStand.fixed,
            "«Исправить» у автозапуска must lead to the system autostart screen",
        )
    }

    @Когда("^программа чтения с экрана читает строку «(.+?)»$")
    fun theReaderReadsTheRow(label: String) {
        DiagnosticsStand.askRaw(world) {
            assertTrue(
                onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                "the row «$label» must be readable",
            )
        }
    }

    @Тогда("^строка «(.+?)» показывает, что разрешение выдано$")
    fun theRowShowsGranted(label: String) {
        assertTrue(hasText(label), "the row «$label» must be shown")
        assertTrue(hasText(string(R.string.diagnostics_state_granted)), "the row must say the permission is granted")
    }

    @Тогда("^напоминания поставлены заново$")
    fun theRemindersWereRescheduled() {
        assertEquals(
            ReminderCheckOutcome.SUCCESS,
            DiagnosticsStand.state(world).checkOutcome,
            "the manual check must finish successfully",
        )
    }

    @И("^показано «Проверка выполнена — напоминания поставлены»$")
    fun theCheckSucceeded() {
        assertTrue(hasText(string(R.string.diagnostics_check_done)), "the screen must name the successful outcome")
    }

    @Тогда("^показано «Проверить не удалось» с причиной$")
    fun theCheckFailed() {
        assertTrue(hasText(string(R.string.diagnostics_check_failed)), "the screen must name the failed outcome")
    }

    @И("^показана фраза о сработавших и пропущенных напоминаниях$")
    fun theWeekSummaryIsAPhrase() {
        assertTrue(hasTag(TAG_WEEK_SUMMARY), "the week summary must be on the first level")
    }

    @Но("^идентификаторов приёмов и счётчиков пересборки в ней нет$")
    fun theWeekSummaryHasNoIdentifiers() {
        val summary = nodeText(TAG_WEEK_SUMMARY)
        assertFalse(summary.contains("intake"), "the summary must carry no intake identifiers: $summary")
        assertFalse(summary.contains("снято"), "the summary must carry no rebuild counters: $summary")
        assertFalse(summary.contains("поставлено"), "the summary must carry no rebuild counters: $summary")
    }

    @Тогда("^названы лекарство, плановое и фактическое время$")
    fun theMedicationAndTimesAreNamed() {
        assertTrue(hasTag(TAG_LAST_SHORT), "the last reminder must be on the first level")
        assertTrue(hasText(namedMedication()), "the medication must be named")
    }

    @И("^отклонение названо словами$")
    fun theDeviationIsInWords() {
        assertTrue(hasText(string(R.string.diagnostics_on_time_word)), "the deviation must be said in words")
    }

    @Тогда("^названы лекарство и время$")
    fun theMedicationAndTimeAreNamed() {
        assertTrue(hasText(namedMedication()), "the medication must be named")
    }

    @И("^время подписано как план, а не как обещание$")
    fun theTimeIsLabelledAsPlan() {
        assertTrue(hasText(string(R.string.diagnostics_next_plan)), "the time must be labelled as a plan")
    }

    @Тогда("^кнопки пробного напоминания на экране нет$")
    fun thereIsNoTestReminder() {
        assertFalse(visibleTexts().any { it.contains(TEST_WORD, ignoreCase = true) }, "no test reminder on the screen")
    }

    @Тогда("^состояние разрешения не выдано$")
    fun thePermissionIsNotGranted() {
        assertTrue(hasText(string(R.string.diagnostics_state_denied)), "the state must be said in words")
    }

    @И("^состояние произносится словами$")
    fun theStateIsSpoken() {
        assertTrue(hasText(string(R.string.diagnostics_state_denied)), "the state must be pronounced")
    }

    // --- Проверки второго уровня ---------------------------------------------------------------

    @Тогда("^показаны журнал срабатываний, причина и счётчики пересборки и список будильников$")
    fun theSupportShowsTheReport() {
        seedAnonymousEntry()
        DiagnosticsStand.refresh(world)
        assertTrue(expandedHasTag(TAG_JOURNAL_ROW), "the journal must be shown")
        assertTrue(expandedHasTag(TAG_RESCHEDULE), "the rebuild counters must be shown")
        assertTrue(expandedHasTag(TAG_LAST_SHOWN), "the technical report must be shown")
    }

    @Тогда("^журнал сохраняется файлом выбранным человеком способом$")
    fun theJournalIsSavedToAFile() {
        val outcome = DiagnosticsStand.state(world).exportOutcome
        assertTrue(outcome is ReminderJournalExportOutcome.Saved, "the export must succeed: $outcome")
        assertTrue(exportedCsv().isNotEmpty(), "the journal file must be written")
    }

    @Тогда("^в строках стоят идентификаторы приёмов$")
    fun theRowsCarryIntakeIds() {
        val rows = journalTexts()
        assertTrue(rows.isNotEmpty(), "the journal rows must be on the screen")
        assertTrue(rows.any { it.contains("intake") }, "the rows must carry intake identifiers: $rows")
    }

    @Но("^названий лекарств, дозировок и имён профилей в них нет$")
    fun theRowsCarryNoMedicalData() {
        val rows = journalTexts()
        val names = world.medications.all().map { it.name }
        rows.forEach { row ->
            names.forEach { name -> assertFalse(row.contains(name), "the journal must carry no names: $row") }
        }
    }

    // --- Название и навигация ------------------------------------------------------------------

    @Тогда("^в шапке стоит «(.+?)»$")
    fun theHeaderCarries(title: String) {
        assertTrue(hasText(title), "the header must carry «$title»")
    }

    @Но("^слова «(.+?)» в интерфейсе нет$")
    fun theWordIsGone(word: String) {
        assertFalse(
            visibleTexts().any { it.contains(word, ignoreCase = true) },
            "the screen must not carry the word «$word»",
        )
    }

    @Тогда("^снова открыт экран «Настройки»$")
    fun theSettingsScreenIsOpen() {
        // Стенд диагностики не владеет графом навигации: системная «Назад» закрывает экран,
        // и возврат в настройки проверяется на устройстве. Здесь фиксируется сам факт нажатия.
        assertTrue(DiagnosticsStand.backRequested, "the system back must be handled by the screen")
    }

    @Тогда("^она произносит, что разрешение не выдано$")
    fun theReaderPronouncesTheState() {
        assertTrue(hasText(string(R.string.diagnostics_state_denied)), "the state must be pronounced")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun namedMedication(): String = world.medications.all().first().name

    private fun addMedication(name: String): String {
        if (!world.medications.has(name)) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = listOf(LocalTime.parse(DAILY_TIME)),
            )
        }
        return world.medications.get(name).id
    }

    private fun seedAnonymousEntry() {
        if (world.medications.all().isEmpty()) addMedication(JOURNAL_MEDICATION)
        val medication = world.medications.all().first()
        val now = world.clock.now()
        record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.SHOWN,
                intakeId = LAST_INTAKE,
                medicationId = medication.id,
                profileId = medication.profileId,
                scheduledAt = now,
                firedAt = now,
                deviationSeconds = 0,
                channel = ReminderChannel.REGULAR,
                delivered = true,
            ),
        )
    }

    private fun record(entry: ReminderLogEntry) {
        runBlocking { world.reminders.journal.record(entry) }
    }

    private fun exportedCsv(): String =
        requireNotNull(world.transfer.files.documents[DiagnosticsStand.JOURNAL_HANDLE]) {
            "the journal file must be written to the chosen place"
        }

    private fun permissionOf(label: String): ReminderPermission =
        REMINDER_PERMISSION_LABELS.entries.first { (_, id) -> string(id) == label }.key

    /** Поиск подстроки: строки первого уровня — целые фразы («Магний B6 — 25 сентября, 20:00 · план»). */
    private fun hasText(text: String): Boolean {
        var found = false
        DiagnosticsStand.askRaw(world) {
            found = onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun hasTag(tag: String): Boolean {
        var found = false
        DiagnosticsStand.askRaw(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun hasTextIgnoringCase(text: String): Boolean = visibleTexts().any { it.contains(text, ignoreCase = true) }

    private fun expandedHasTag(tag: String): Boolean {
        DiagnosticsStand.expandSupport(world)
        return hasTag(tag)
    }

    private fun nodeText(tag: String): String {
        var text = ""
        DiagnosticsStand.askRaw(world) {
            text = onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
                .joinToString(" ")
        }
        return text
    }

    private fun visibleTexts(): List<String> {
        var texts: List<String> = emptyList()
        DiagnosticsStand.askRaw(world) {
            texts = onAllNodesWithTag(TAG_DIAGNOSTICS, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
        }
        return texts
    }

    private fun journalTexts(): List<String> {
        var texts: List<String> = emptyList()
        DiagnosticsStand.ask(world) {
            texts = onAllNodesWithTag(TAG_JOURNAL_ROW, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
        }
        return texts
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            .orEmpty().map { it.text } + node.children.flatMap(::textsOf)

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val DAILY_TIME = "20:00"
        const val LAST_INTAKE = "intake-last"
        const val JOURNAL_MEDICATION = "Журнальное"
        const val TEST_WORD = "пробн"

        val REMINDER_PERMISSION_LABELS: Map<ReminderPermission, Int> = mapOf(
            ReminderPermission.NOTIFICATIONS to R.string.diagnostics_permission_notifications,
            ReminderPermission.NOTIFICATION_CHANNEL to R.string.diagnostics_permission_channel,
            ReminderPermission.EXACT_ALARMS to R.string.diagnostics_permission_exact_alarms,
            ReminderPermission.FULL_SCREEN_INTENT to R.string.diagnostics_permission_full_screen,
            ReminderPermission.DND_POLICY_ACCESS to R.string.diagnostics_permission_dnd,
            ReminderPermission.BOOT_RECEIVER to R.string.diagnostics_permission_boot,
            ReminderPermission.AUTOSTART to R.string.diagnostics_permission_autostart,
        )
    }
}

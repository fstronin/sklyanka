package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_ALL_GRANTED
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_FIX
import ru.stohastika.sklyanka.feature.reminder.TAG_LAST_SHORT
import ru.stohastika.sklyanka.feature.reminder.TAG_NEXT_SHORT
import ru.stohastika.sklyanka.feature.reminder.TAG_VERDICT
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

    @Допустим("^человек раскрыл «Для поддержки»$")
    fun thePersonExpandedTheSupport() {
        DiagnosticsStand.expandSupport(world)
    }

    // --- Действия ------------------------------------------------------------------------------

    @Когда("^человек смотрит на «Проверку напоминаний»$")
    fun thePersonLooksAtTheCheck() {
        DiagnosticsStand.askRaw(world) { onNodeWithTag(TAG_DIAGNOSTICS).assertExists() }
    }

    @Когда("^человек выдаёт уведомления в системных настройках и возвращается$")
    fun thePersonGrantsNotificationsAndReturns() {
        world.system.grant(ReminderPermission.NOTIFICATIONS)
        DiagnosticsStand.refresh(world)
    }

    @Когда("^человек нажимает «Исправить» у строки «(.+?)»$")
    fun thePersonFixesTheRow(label: String) {
        clickFixFor(permissionOf(label))
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
        // Возврат — шеврон в цели 44 dp; слова «Назад» на экране нет, и цель ищется
        // по доступному имени `cd_back`, а не по тексту (12-design §7).
        val back = context.getString(ru.stohastika.sklyanka.ui.R.string.cd_back)
        DiagnosticsStand.actRaw(world) { onNodeWithContentDescription(back).performClick() }
    }

    // --- Проверки первого уровня ---------------------------------------------------------------

    @Тогда("^показано «Всё в порядке»$")
    fun everythingIsFine() {
        assertTrue(hasTag(TAG_VERDICT), "the short answer must be shown when everything is fine")
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

    @Тогда("^у строки «(.+)» сказано, что именно не так$")
    fun theRowSaysWhatIsWrong(label: String) {
        assertTrue(hasText(label), "the row «$label» must be shown")
        val consequence = requireNotNull(consequenceLabel(permissionOf(label))) {
            "the row «$label» must name a consequence"
        }
        assertTrue(hasText(string(consequence)), "the row must say what is wrong, in words")
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
        clickFixFor(ReminderPermission.AUTOSTART)
        assertEquals(
            ReminderPermission.AUTOSTART,
            DiagnosticsStand.fixed,
            "«Исправить» у автозапуска must lead to the system autostart screen",
        )
    }

    @Тогда("^строка «(.+?)» больше не показана$")
    fun theRowIsGone(label: String) {
        assertFalse(hasText(label), "the granted row «$label» must be gone")
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

    // --- Проверки второго уровня (технический блок убран, см. SKL-102) ------------------------

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
        // Стенд диагностики не владеет графом навигации: системная кнопка возврата закрывает
        // экран, и возврат в настройки проверяется на устройстве. Здесь фиксируется сам факт нажатия.
        assertTrue(DiagnosticsStand.backRequested, "the system back must be handled by the screen")
    }

    @Тогда("^она произносит, что разрешение не выдано$")
    fun theReaderPronouncesTheState() {
        // Состояние названо видимым словом в строке: чтение с экрана произносит его
        // вместе с именем и последствием (НФТ-30, ОВ-134).
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

    private fun permissionOf(label: String): ReminderPermission =
        REMINDER_PERMISSION_LABELS.entries.first { (_, id) -> string(id) == label }.key

    /** Нажать «Исправить» у строки разрешения по порядку карточки, а не по порядку перечисления. */
    private fun clickFixFor(permission: ReminderPermission) {
        val state = DiagnosticsStand.state(world)
        val actionable = ReminderPermission.entries.filter {
            ru.stohastika.sklyanka.domain.reminder.ReminderPermissionGroups.requiresAction(
                it,
                state.snapshot?.permissions?.get(it)
                    ?: ru.stohastika.sklyanka.domain.reminder.port.PermissionState.UNKNOWN,
            )
        }
        val index = actionable.indexOf(permission)
        assertTrue(index >= 0, "the row of $permission must have a fix action")
        DiagnosticsStand.actRaw(world) {
            onAllNodesWithTag(TAG_FIX)[index].performScrollTo().performClick()
        }
    }

    private fun consequenceLabel(permission: ReminderPermission): Int? = when (permission) {
        ReminderPermission.NOTIFICATIONS -> R.string.diagnostics_consequence_notifications
        ReminderPermission.NOTIFICATION_CHANNEL -> R.string.diagnostics_consequence_channel
        ReminderPermission.EXACT_ALARMS -> R.string.diagnostics_consequence_exact_alarms
        ReminderPermission.AUTOSTART -> R.string.diagnostics_consequence_autostart
        ReminderPermission.FULL_SCREEN_INTENT -> R.string.diagnostics_consequence_full_screen
        ReminderPermission.DND_POLICY_ACCESS -> R.string.diagnostics_consequence_dnd
        ReminderPermission.BOOT_RECEIVER -> null
    }

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

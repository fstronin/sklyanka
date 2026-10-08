package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.feature.reminder.JournalExportUi
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_EXPORT
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_EXPORT_PLATE
import java.time.LocalTime

/**
 * Экранные шаги приёмки служебной выгрузки журнала (SKL-106, группы `@screen`).
 *
 * Системный выбор места на стенде играют шаги: `chooseJournalPlace` отдаёт экрану
 * handle вместо SAF-диалога, `cancelJournalPick` — отмену. Файл пишется настоящим
 * `ExportReminderJournalUseCase` на файловом порте мира, поэтому «выгруженных файлов
 * нет» — это про `world.transfer.files`, а не про ожидание.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk106JournalExportSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Предыстория ----------------------------------------------------------------------------

    @Допустим("^в журнале есть срабатывание лекарства «(.+)»$")
    fun theJournalHasAFiringOf(name: String) {
        val medicationId = medication(name)
        val now = world.clock.now()
        runBlocking {
            world.reminders.journal.record(
                ReminderLogEntry(
                    occurredAt = now.minusSeconds(60),
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-export",
                    medicationId = medicationId,
                    scheduledAt = now.minusSeconds(60),
                    firedAt = now.minusSeconds(60),
                    deviationSeconds = 0,
                    channel = ReminderChannel.REGULAR,
                    delivered = true,
                ),
            )
        }
    }

    @Допустим("^на выбранном месте нет места для файла журнала$")
    fun thePlaceIsTooTight() {
        world.transfer.files.freeSpace = 1L
    }

    // --- Действия -------------------------------------------------------------------------------

    @Когда("^человек нажимает «Выгрузить журнал»$")
    fun thePersonTapsTheExport() {
        DiagnosticsStand.actRaw(world) {
            onNodeWithText(string(R.string.diagnostics_journal_export)).performScrollTo().performClick()
        }
    }

    @Когда("^человек выбирает место сохранения$")
    fun thePersonChoosesAPlace() {
        DiagnosticsStand.chooseJournalPlace(world)
    }

    @Когда("^человек отменяет выбор места$")
    fun thePersonCancelsTheChoice() {
        DiagnosticsStand.cancelJournalPick(world)
    }

    // --- Проверки -------------------------------------------------------------------------------

    @Тогда("^внизу есть тихая строка «Выгрузить журнал»$")
    fun theQuietRowSitsAtTheBottom() {
        assertTrue(hasTag(TAG_JOURNAL_EXPORT), "the journal export row must be at the bottom")
        assertTrue(hasText(string(R.string.diagnostics_journal_export)), "the row must name the action")
    }

    @Но("^кнопки «Сообщить о проблеме» на экране нет$")
    fun theLyingReportButtonIsGone() {
        // Литера намеренная: ресурс строки вырезан вместе с заглушкой (П-7), и проверяется
        // именно отсутствие прежней лжи — сослаться на него уже нельзя.
        assertFalse(hasText("Сообщить о проблеме"), "the screen must not promise a send it cannot do")
    }

    @Тогда("^приложение просит систему выбрать место для файла с именем «(.+)»$")
    fun theSystemIsAskedForAPlace(name: String) {
        assertEquals(name, DiagnosticsStand.exportRequested, "the file name must be named before the choice")
    }

    @И("^выгруженных файлов ещё нет$")
    @Но("^выгруженных файлов нет$")
    fun noFilesWereWritten() {
        assertTrue(world.transfer.files.documents.isEmpty(), "no exported file may exist")
    }

    @Тогда("^под строкой выгрузки названо число выгруженных записей$")
    fun thePlateNamesTheCount() {
        assertTrue(hasTag(TAG_JOURNAL_EXPORT_PLATE), "the plate must name the outcome")
        val saved = requireNotNull(DiagnosticsStand.state(world).journalExport as? JournalExportUi.Saved) {
            "the outcome must be a saved export"
        }
        val expected = context.resources.getQuantityString(
            R.plurals.diagnostics_journal_export_saved,
            saved.records,
            saved.records,
        )
        assertTrue(hasText(expected), "the plate must say «$expected»")
    }

    @Но("^в выгруженном файле нет названия лекарства$")
    fun theFileCarriesNoMedicationNames() {
        val content = world.transfer.files.documents.values.joinToString("\n")
        assertTrue(content.isNotEmpty(), "the chosen place must have received the file")
        world.medications.all().forEach { medication ->
            assertFalse(content.contains(medication.name), "the file must not carry «${medication.name}»")
        }
    }

    @Тогда("^никакого числа записей экран не называет$")
    fun noCountIsNamed() {
        assertNull(DiagnosticsStand.state(world).journalExport, "a cancellation is not an outcome")
        assertFalse(hasTag(TAG_JOURNAL_EXPORT_PLATE), "no plate may appear after a cancellation")
    }

    @Тогда("^под строкой выгрузки сказано «(.+)»$")
    fun thePlateSays(text: String) {
        assertTrue(hasTag(TAG_JOURNAL_EXPORT_PLATE), "the plate must name the outcome")
        assertTrue(hasText(text), "the plate must say «$text»")
    }

    // --- Обвязка --------------------------------------------------------------------------------

    /** Лекарству дают имя: мир хранит лекарства без названий, а файлу имя угрожает. */
    private fun medication(name: String): String {
        if (!world.medications.has(name)) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = listOf(LocalTime.of(8, 0)),
            )
        }
        world.medications.update(name) { it.copy(name = name) }
        return world.medications.get(name).id
    }

    private fun hasText(text: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        return found
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

    private fun textsOf(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.Text)
        .orEmpty().map { it.text } + node.children.flatMap(::textsOf)

    private fun string(id: Int): String = context.getString(id)
}

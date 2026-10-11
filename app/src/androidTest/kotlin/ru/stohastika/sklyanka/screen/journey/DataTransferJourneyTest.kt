package ru.stohastika.sklyanka.screen.journey

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.transfer.TransferFormat
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_APPLY_MERGE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_LAST_EXPORT
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_MATCHES
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_MODE_MERGE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_MODE_REPLACE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_NOT_TRANSFERRED
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_PICK_FILE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_REPLACE_CONFIRM
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_REPLACE_CONFIRM_ACTION
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_REPORT
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_SAFETY_COPY
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_SAVE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_SUMMARY
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_SUMMARY_CONTINUE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_TRANSFER_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestGraph
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_FIELD
import java.io.File
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * Поход «Перенос данных файлом» (SKL-140, реестр походов № 7).
 *
 * Человек выгружает всё состояние в файл, загружает этот же файл обратно в режиме
 * «Добавить к текущим» и, наконец, заменяет устройство содержимым файла — со словом
 * подтверждения и страховочной копией. Поход идёт на настоящем хранилище, и файл
 * переноса здесь настоящий: его пишет и читает продуктовый код поверх системного
 * выбора места, а поход читает тот же файл с диска. Иначе «выгрузка создала файл»
 * и «страховочная копия лежит на диске» были бы словами, а не состояниями.
 *
 * **Системный выбор файла подменяется ответом системы, а не продуктом.** На образе
 * прогона (`aosp_atd`) системного выбора файлов нет вовсе, а диалог выбора
 * автоматизации не подлежит — правило набора запрещает его трогать. Поэтому ответ
 * системы на запрос места и файла (`SAF`) подаётся на уровне инструментовки: чужой
 * активности не запускается, а её результат приходит сразу ([answerPickerWith]). Это
 * тот же приём, каким стенд на JVM подменяет владельца реестра результатов
 * («место выбрано» записано в реестр, а не показано диалогом). Всё остальное —
 * настоящее: содержимое файла, его чтение, сводка, разбор совпадений, страховочная
 * копия, замена и отчёт — код продукта на настоящей базе.
 */
@HiltAndroidTest
class DataTransferJourneyTest : JourneyTest() {

    @Test
    fun thePersonMovesHisDataThroughATransferFile() {
        val name = testContext.getString(R.string.device_test_medication_name, "journey-transfer")
        launchApp()
        awaitRoot()
        seedThePersonHistory(name)

        openSettings()
        openTransfer()
        capture("overview")

        val file = exportEverything()
        mergeTheFileBack(file)
        replaceEverythingWithTheFile(file)
    }

    /**
     * Уборка перед походом: файл переноса и страховочная копия не переживают прошлый
     * прогон. Без этого «файл создан» и «копия лежит на диске» были бы верны и без
     * действий человека.
     */
    override fun resetBeforeScenario() {
        super.resetBeforeScenario()
        File(targetContext.filesDir, TRANSFER_DIRECTORY).deleteRecursively()
        safetyCopyFile().delete()
    }

    /**
     * Предыстория: у профиля «Я» есть лекарство с расписанием, план приёмов и отметка
     * «принято» — то, что человек выгружает (лекарства, расписания, история и настройки).
     *
     * Сид идёт доменными путями, как их сеет продукт: иначе файл переноса был бы
     * выгрузкой не того состояния, которое человек видит в приложении.
     */
    private fun seedThePersonHistory(name: String) {
        seedMedication(MEDICATION_ID, name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()
        val planned = dayIntakes().firstOrNull { it.scheduledAt.toLocalTime() == DeviceTestClock.scheduledTime }
        val taken = requireNotNull(planned) { "the seed must plan today's intake at ${DeviceTestClock.scheduledTime}" }
        DeviceTestSeed.markTaken(targetContext, taken.id)
    }

    /** Открыть «Перенос данных» строкой настроек: путь человека, а не внутренний тег строки. */
    private fun openTransfer() {
        val title = ui(SettingsR.string.transfer_title)
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_SETTINGS_SCREEN)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(title))
        compose.onNode(hasClickAction() and hasText(title, substring = true)).performClick()
        waitForTag(TAG_TRANSFER_SCREEN)
    }

    /**
     * Выгрузка: человек нажимает «Сохранить файл», а система отвечает местом на диске.
     *
     * Утверждается не картинка, а файл: он лежит на диске, в нём есть опознаватель
     * продукта и версия формата, а в журнале — след выгрузки. Строка о последней
     * выгрузке до неё отсутствует и появляется после: приложение не ведёт учёта того,
     * куда ушёл файл, и вместо пути называет файл и дату выгрузки.
     */
    private fun exportEverything(): File {
        val file = exportFile()
        assertTrue("there must be no export row before the first export", !exists(TAG_LAST_EXPORT))
        assertTrue(
            "the card must name the file",
            textContains(uiText(SettingsR.string.transfer_export_file, file.name)),
        )
        assertTrue("the place must be the person's choice", textContains(ui(SettingsR.string.transfer_privacy_place)))
        answerPickerWith(file) {
            scrollToAndClick(TAG_SAVE)
            waitUntil(SCREEN_TIMEOUT_MS, "the export row after the export") { exists(TAG_LAST_EXPORT) }
        }
        capture("exported")
        assertTrue("the export must land on disk: ${file.path}", file.isFile && file.length() > 0)
        val text = file.readText()
        assertTrue("the file must carry the product marker", text.contains(TransferFormat.MARKER))
        assertTrue("the file must carry the format version", text.contains(formatVersionLine()))
        assertTrue("the export must leave a trace in the journal", opLogHas(OpEvent.TRANSFER_EXPORTED))
        return file
    }

    /**
     * Загрузка в режиме «Добавить к текущим»: совпадения показаны отдельно, решение
     * принимается по каждому, а отчёт называет добавленное, обновлённое и отброшенное.
     *
     * Файл — выгрузка того же устройства, поэтому совпадение одно, а вся его история
     * уже есть на устройстве: «взять из файла» обновляет запись, дубли истории
     * отбрасываются, и отчёт говорит об этом числами. Набор при этом не удваивается —
     * это утверждается данными, а не только строкой отчёта.
     */
    private fun mergeTheFileBack(file: File) {
        scrollToAndClick(TAG_MODE_MERGE)
        answerPickerWith(file) {
            scrollToAndClick(TAG_PICK_FILE)
            waitForTag(TAG_SUMMARY)
        }
        capture("summary-merge")
        click(TAG_SUMMARY_CONTINUE)
        waitForTag(TAG_MATCHES)
        capture("matches")

        compose.onNodeWithText(uiText(SettingsR.string.transfer_decision_take_from_file))
            .performScrollTo()
            .performClick()
        click(TAG_APPLY_MERGE)
        waitForTag(TAG_REPORT)
        capture("report-merge")

        assertTrue(
            "the report must name the added medications",
            textExists(uiText(SettingsR.string.transfer_report_medications, 0)),
        )
        assertTrue(
            "the report must name the updated medication",
            textExists(uiText(SettingsR.string.transfer_report_medications_updated, 1)),
        )
        assertTrue("the report must name the dropped history", exists(TAG_NOT_TRANSFERRED))
        assertTrue("the report must say how much history was already there", textContains(duplicateHistoryPrefix()))
        assertEquals("the merge must not add a second medication", 1, ownerMedications().size)
        assertEquals("the merge must not double today's intake", 1, todaysIntakes())
        assertTrue("the merge must leave a trace in the journal", opLogHas(OpEvent.TRANSFER_MATCHES_RESOLVED))
    }

    /**
     * Загрузка в режиме «Заменить всё»: страховочная выгрузка обещана до стирания, замена
     * выполняется только после слова, а отчёт называет числа и место копии.
     *
     * Отчёт называет добавленное числом, потому что замена пишет в пустое устройство:
     * обновлять и отбрасывать там нечего. Обновлённое и отброшенное называет отчёт
     * слияния выше — это свойство режимов, а не неполнота отчёта.
     *
     * Двойное нажатие подтверждения («а вдруг не сработало») проверяет враждебный проход
     * по переносу: здесь путь человека — одно слово, одно нажатие, одно применение, и в
     * журнале на подтверждение приходится ровно одна запись.
     */
    private fun replaceEverythingWithTheFile(file: File) {
        pressBack()
        waitForTag(TAG_SETTINGS_SCREEN)
        openTransfer()
        scrollToAndClick(TAG_MODE_REPLACE)
        answerPickerWith(file) {
            scrollToAndClick(TAG_PICK_FILE)
            waitForTag(TAG_SUMMARY)
        }
        click(TAG_SUMMARY_CONTINUE)
        waitForTag(TAG_REPLACE_CONFIRM)
        capture("replace-confirm")

        assertTrue(
            "the safety copy must be promised before the wipe",
            nodeText(TAG_SAFETY_COPY).contains(TransferFormat.SAFETY_COPY_NAME),
        )
        compose.onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION, useUnmergedTree = true).assertIsNotEnabled()
        typeInto(TAG_CONFIRM_WORD_FIELD, uiText(SettingsR.string.transfer_replace_word), scroll = false)
        waitUntil(SCREEN_TIMEOUT_MS, "the confirm action after the word") { isEnabled(TAG_REPLACE_CONFIRM_ACTION) }
        val appliedBefore = opLogCount(OpEvent.TRANSFER_APPLIED)
        click(TAG_REPLACE_CONFIRM_ACTION)
        waitForTag(TAG_REPORT)
        capture("report-replace")

        assertTrue(
            "the report must name the added medications",
            textExists(uiText(SettingsR.string.transfer_report_medications, 1)),
        )
        assertTrue(
            "the report must say where the safety copy is",
            textExists(uiText(SettingsR.string.transfer_report_safety_copy, TransferFormat.SAFETY_COPY_NAME)),
        )
        assertTrue("the safety copy must lie on disk", safetyCopyFile().isFile)
        assertEquals("the replacement must leave the file's medication", 1, ownerMedications().size)
        assertEquals("the replacement must leave the file's intake today", 1, todaysIntakes())
        assertTrue("the replacement must have made the safety copy", opLogHas(OpEvent.TRANSFER_SAFETY_COPY))
        // Подтверждение было одно, и в журнале на него ровно одна запись: путь человека —
        // одно слово, одно нажатие, одно применение. Двойное нажатие («а вдруг не сработало»)
        // проверяет враждебный проход по переносу: там ему и место.
        assertEquals(
            "one confirmed replacement must leave exactly one applied transfer in the journal",
            appliedBefore + 1,
            opLogCount(OpEvent.TRANSFER_APPLIED),
        )
    }

    /**
     * Ответ системы на запрос места или файла — без диалога.
     *
     * Устройство без системного выбора файлов не запускает чужую активность: инструментовка
     * отвечает на запуск сама, и продуктовый `onSave`/`onPickFile` получает ссылку на файл
     * тем же путём, каким получил бы её от системы. Наблюдатель снимается сразу после шага,
     * поэтому на остальные запуски активностей он не влияет.
     */
    private fun answerPickerWith(file: File, block: () -> Unit) {
        val answer = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent?): Instrumentation.ActivityResult? = when (intent?.action) {
                Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT -> answer
                else -> null
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.addMonitor(monitor)
        try {
            block()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** Строка продукта с подстановкой: отчёт называет числа той же строкой, что видит человек. */
    private fun uiText(@StringRes id: Int, value: Any): String = targetContext.getString(id, value)

    /** Строка без подстановки: тот же источник, что и у строки с числом. */
    private fun uiText(@StringRes id: Int): String = targetContext.getString(id)

    /** Есть ли на экране строка с этим куском: числа отчёта заранее неизвестны. */
    private fun textContains(part: String): Boolean =
        compose.onAllNodesWithText(part, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Начало строки о дублях истории: образец берётся из ресурса, число подставляет отчёт. */
    private fun duplicateHistoryPrefix(): String =
        ui(SettingsR.string.transfer_report_intakes_skipped).substringBefore(VALUE_PLACEHOLDER).trim()

    /** Строка версии формата в файле: версия и её ключ — из продуктового описания формата. */
    private fun formatVersionLine(): String = "\"format_version\": ${TransferFormat.CURRENT_VERSION}"

    /** Доступно ли действие: кнопка подтверждения замены ждёт верного слова (ОВ-67). */
    private fun isEnabled(tag: String): Boolean = runCatching {
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsEnabled()
    }.isSuccess

    /** Приёмы профиля владельца на сегодня: их число доказывает, что история не удвоилась. */
    private fun todaysIntakes(): Int =
        dayIntakes().count { it.scheduledAt.toLocalTime() == DeviceTestClock.scheduledTime }

    /** Лекарства профиля владельца: их число доказывает, что набор не удвоился. */
    private fun ownerMedications(): List<Medication> = runBlocking {
        EntryPointAccessors
            .fromApplication(targetContext.applicationContext, DeviceTestGraph::class.java)
            .medications()
            .observeActive(ownerProfile.id)
            .first()
    }

    /** Файл переноса: имя предлагает продукт, а лежит он там, куда его положил человек. */
    private fun exportFile(): File = File(transferDirectory(), TransferFormat.fileName(DeviceTestClock.today))

    /** Каталог файла переноса внутри хранилища приложения: внешнего хранилища приложение не просит. */
    private fun transferDirectory(): File = File(targetContext.filesDir, TRANSFER_DIRECTORY).apply { mkdirs() }

    /** Страховочная копия перед заменой всего: постоянное имя во внутреннем хранилище (ПЕР-36). */
    private fun safetyCopyFile(): File = File(targetContext.filesDir, TransferFormat.SAFETY_COPY_NAME)

    private companion object {
        const val MEDICATION_ID = "journey-transfer-medication"
        const val TRANSFER_DIRECTORY = "journey-transfer"
        const val VALUE_PLACEHOLDER = "%1\$d"
    }
}

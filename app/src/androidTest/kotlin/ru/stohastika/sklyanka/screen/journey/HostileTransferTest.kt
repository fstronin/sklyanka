package ru.stohastika.sklyanka.screen.journey

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.transfer.TransferFormat
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_FAILURE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_FAILURE_REASON
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_LAST_EXPORT
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
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_FIELD
import java.io.File
import java.time.LocalTime
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * Враждебный поход «Перенос данных» (SKL-140, реестр походов № 19).
 *
 * Человек открывает перенос данных, дважды подряд выбирает «Заменить всё», уходит системной
 * «Назад» с середины шагов и возвращается заново. Затем он подаёт испорченный файл,
 * убеждается, что замена не подтверждается неверным словом, и подтверждает её верным словом
 * дважды подряд. Враждебность в том, что после каждого действия утверждается состояние
 * данных, а не только экран: выбор режима и уход не имеют права записать ни строки
 * (ПЕР-15, ПЕР-27), отказ обязан быть назван (ПЕР-28), а подтверждение одно — и замена
 * одна, иначе необратимое действие выполняется дважды.
 *
 * **Системный выбор файла подменяется ответом системы, а не продуктом.** Диалога выбора
 * автоматизации не отдаёт, и трогать его нельзя (TDD задачи §8): поэтому ответ на запрос
 * места и файла подаётся на уровне инструментовки — [Instrumentation.ActivityMonitor]
 * без фильтра. `execStartActivity` спрашивает такого наблюдателя и возвращает его результат
 * раньше, чем запускает чужую активность, а `startActivityForResult` доставляет результат
 * вызывающему. Продукт при этом работает своим кодом на настоящем файле: ни продукт,
 * ни тестовый граф ради похода не менялись.
 *
 * Опорой выбора режима служит не кадр и не слово строки — они от выбора не меняются
 * (НФТ-33), — а семантика `Selected` у строки режима: два режима не выбираются оба (ПЕР-14).
 */
@HiltAndroidTest
class HostileTransferTest : JourneyTest() {

    /**
     * Уборка перед походом: файлы стенда не переживают прошлый прогон.
     *
     * Без неё «выгрузка создала файл» и «страховочная копия лежит на диске» были бы верны
     * и без единого действия человека — то есть перестали бы что-либо утверждать.
     */
    override fun resetBeforeScenario() {
        super.resetBeforeScenario()
        File(targetContext.filesDir, STAND_DIRECTORY).deleteRecursively()
        safetyCopyFile().delete()
    }

    @Test
    fun thePersonBreaksTheTransferAndNothingExtraIsWritten() {
        val name = medicationName()
        launchApp()
        awaitRoot()
        seedMedication(id = MEDICATION_ID, name = name, times = listOf(MORNING))
        planIntakes()
        val dayBefore = dayIntakes()
        assertTrue("the seeded day must hold the morning intake", dayBefore.isNotEmpty())

        leavesTheTransferInTheMiddle(dayBefore)

        rejectABrokenFile(dayBefore)

        replacesEverythingWith(exportEverything(), dayBefore)

        pressBack()
        waitForTag(TAG_SETTINGS_SCREEN)
        openMedications()
        scrollListTo(name)
        assertTrue("the medication must survive the whole walk", textExists(name))
        capture("medications")
    }

    /**
     * Середина шагов и системная «Назад»: человек выбирает «Заменить всё», уходит и возвращается.
     *
     * Утверждается состояние данных, а не смена экрана: выбор режима не имеет права ничего
     * стереть и никого не подтвердить, уход — записать хоть строку (ПЕР-15, ПЕР-27),
     * возвращение — начаться с брошенного выбора (ПЕР-17).
     */
    private fun leavesTheTransferInTheMiddle(dayBefore: List<Intake>) {
        openSettings()
        awaitTransferRow()
        capture("settings")
        openTransfer()
        waitUntil(SCREEN_TIMEOUT_MS, "the composition chip of the seeded medication") {
            textExists(medicationChip())
        }
        capture("transfer")
        assertTrue("the export action must be offered", exists(TAG_SAVE))
        assertTrue("the import action must be offered", exists(TAG_PICK_FILE))
        assertFalse("a fresh install has no export date line", exists(TAG_LAST_EXPORT))
        assertFalse("nothing has failed yet", exists(TAG_FAILURE))
        assertTrue("merge must be the mode by default", modeIsSelected(TAG_MODE_MERGE))
        assertFalse("replace must not be chosen by default", modeIsSelected(TAG_MODE_REPLACE))

        chooseReplaceMode()
        assertFalse("the mode alone must not open the summary", exists(TAG_SUMMARY))
        assertFalse("the mode alone must not open the confirmation", exists(TAG_REPLACE_CONFIRM))
        capture("replace-mode")

        pressBack()
        waitForTag(TAG_SETTINGS_SCREEN)
        waitUntil(SCREEN_TIMEOUT_MS, "the transfer screen to close") { !exists(TAG_TRANSFER_SCREEN) }
        assertTransferRecordedNothing()
        assertEquals("the abandoned transfer must not touch the day", dayBefore, dayIntakes())

        openTransfer()
        waitUntil(SCREEN_TIMEOUT_MS, "the transfer to start over") { modeIsSelected(TAG_MODE_MERGE) }
        assertFalse("the abandoned mode must not survive the leave", modeIsSelected(TAG_MODE_REPLACE))
        assertFalse("no failure must survive the leave", exists(TAG_FAILURE))
        chooseReplaceMode()
    }

    /**
     * Испорченный файл: причина отказа названа, данные не изменились (ПЕР-28).
     *
     * Отказ уходит в журнал кодом причины и ничего не применяет: битый файл не имеет права
     * выглядеть как «просто не получилось» — иначе человек не знает, цела ли его копия.
     */
    private fun rejectABrokenFile(dayBefore: List<Intake>) {
        val broken = standDirectory().resolve(BROKEN_FILE_NAME).apply { writeText(BROKEN_CONTENT) }
        answerPickerWith(broken) {
            scrollToAndClick(TAG_PICK_FILE)
            waitForTag(TAG_FAILURE)
        }
        waitForTag(TAG_FAILURE_REASON)
        capture("broken-file")
        assertTrue(textExists(stringOf(SettingsR.string.transfer_error_not_an_export)))
        assertTrue(textExists(stringOf(SettingsR.string.transfer_reason_not_json)))
        assertTrue("the rejection must leave a trace", opLogHas(OpEvent.TRANSFER_FILE_REJECTED))
        assertTransferRecordedNothing()
        assertEquals("the rejected file must not touch the day", dayBefore, dayIntakes())
    }

    /**
     * Замена всего настоящим файлом: слово, страховочная копия и ровно одно применение.
     *
     * Файл — собственная выгрузка продукта, поэтому замена пишет в пустое устройство то,
     * что в нём и было: добавленное отчёт обязан назвать, а терять при замене нечего.
     * Второе нажатие по той же точке — враждебный приём: подтверждение было одно, и
     * применяться замена обязана один раз.
     */
    private fun replacesEverythingWith(file: File, dayBefore: List<Intake>) {
        answerPickerWith(file) {
            scrollToAndClick(TAG_PICK_FILE)
            waitForTag(TAG_SUMMARY)
        }
        click(TAG_SUMMARY_CONTINUE)
        waitForTag(TAG_REPLACE_CONFIRM)
        waitForTag(TAG_SAFETY_COPY)
        capture("replace-confirm")
        assertTrue(
            "the safety copy must be promised before the wipe",
            nodeText(TAG_SAFETY_COPY).contains(TransferFormat.SAFETY_COPY_NAME),
        )

        setWord(WRONG_WORD)
        assertFalse("a wrong word must not unlock the replacement", confirmIsEnabled())
        capture("wrong-word")

        setWord(stringOf(SettingsR.string.transfer_replace_word))
        waitUntil(SCREEN_TIMEOUT_MS, "the confirm action after the right word") { confirmIsEnabled() }
        val appliedBefore = opLogCount(OpEvent.TRANSFER_APPLIED)
        // Человек подтверждает замену дважды подряд по той же точке: две точки без простоя
        // между ними ([doublePress]) — ожидание простоя мерило бы скорость завершения замены,
        // а не двойное нажатие. Второе нажатие не имеет права применить загрузку ещё раз —
        // иначе необратимое действие выполняется дважды, а человек видел одно подтверждение
        // (ПЕР-16). Пока замена идёт, подтверждающая кнопка занята (`ButtonState.Busy` —
        // недоступна нажатию), тем же способом, каким заняты кнопки удаления профиля
        // и удаления лекарства.
        doublePressByTag(TAG_REPLACE_CONFIRM_ACTION)
        waitForTag(TAG_REPORT)
        capture("report")

        assertTrue(
            "the report must name the added profile",
            textExists(stringOf(SettingsR.string.transfer_report_profiles, 1)),
        )
        assertTrue(
            "the report must name the added medication",
            textExists(stringOf(SettingsR.string.transfer_report_medications, 1)),
        )
        assertTrue("the report must name the added history", textContains(intakesPrefix()))
        assertFalse("the replacement must not drop anything", exists(TAG_NOT_TRANSFERRED))
        assertTrue(
            "the report must say where the safety copy is",
            textExists(stringOf(SettingsR.string.transfer_report_safety_copy, TransferFormat.SAFETY_COPY_NAME)),
        )
        assertTrue("the safety copy must lie on disk", safetyCopyFile().isFile)
        assertTrue("the replacement must have made the safety copy", opLogHas(OpEvent.TRANSFER_SAFETY_COPY))
        assertEquals(
            "one confirmed replacement must apply exactly once",
            appliedBefore + 1,
            opLogCount(OpEvent.TRANSFER_APPLIED),
        )
        assertEquals("the replacement must leave the day as the file had it", dayBefore, dayIntakes())
    }

    /** Выгрузка: человек нажимает «Сохранить файл», а за систему отвечает стенд. */
    private fun exportEverything(): File {
        val file = standDirectory().resolve(TransferFormat.fileName(DeviceTestClock.today))
        answerPickerWith(file) {
            scrollToAndClick(TAG_SAVE)
            waitUntil(SCREEN_TIMEOUT_MS, "the export to land on disk") { file.isFile && file.length() > 0 }
        }
        assertTrue(
            "the export must be the product's own file",
            file.readText().contains(TransferFormat.MARKER),
        )
        return file
    }

    /**
     * Ответ системы на запрос места или файла — без диалога.
     *
     * Чужой активности не запускается: инструментовка отвечает на запуск сама, и продуктовый
     * обработчик получает ссылку на файл тем же путём, каким получил бы её от системы.
     * Наблюдатель снимается сразу после шага и на прочие запуски активностей не влияет.
     */
    private fun answerPickerWith(file: File, block: () -> Unit) {
        val answer = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent?): Instrumentation.ActivityResult? = when (intent?.action) {
                Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_CREATE_DOCUMENT -> answer
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

    /** Выбрать «Заменить всё» дважды подряд: режим ровно один, и выбор ничего не стирает. */
    private fun chooseReplaceMode() {
        scrollToAndClick(TAG_MODE_REPLACE)
        scrollToAndClick(TAG_MODE_REPLACE)
        waitUntil(SCREEN_TIMEOUT_MS, "the replace mode to be chosen") { modeIsSelected(TAG_MODE_REPLACE) }
        assertFalse("the modes must exclude each other", modeIsSelected(TAG_MODE_MERGE))
    }

    /** Заменить слово-подтверждение: полотно подтверждения не прокручивается (прокрутки нет). */
    private fun setWord(value: String) {
        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_CONFIRM_WORD_FIELD)),
            useUnmergedTree = true,
        ).performTextReplacement(value)
        compose.waitForIdle()
    }

    /** Доступно ли подтверждение: кнопка включается только верным словом (ОВ-67). */
    private fun confirmIsEnabled(): Boolean = runCatching {
        compose.onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION, useUnmergedTree = true).assertIsEnabled()
    }.isSuccess

    /** Ни одной записи о переносе до первой выгрузки: уход с экрана не пишет ничего (ПЕР-27). */
    private fun assertTransferRecordedNothing() {
        TRANSFER_WRITES.forEach { event ->
            assertFalse("nothing must record $event yet", opLogHas(event))
        }
    }

    /** Открыть «Перенос данных» со строки настроек: метка строки `internal` чужому модулю не видна. */
    private fun openTransfer() {
        awaitTransferRow()
        compose.onNodeWithText(transferRowTitle(), useUnmergedTree = true).performClick()
        waitForTag(TAG_TRANSFER_SCREEN)
    }

    /** Дождаться, что строка «Перенос данных» доехала до экрана: она живёт ниже первого экрана. */
    private fun awaitTransferRow() {
        waitUntil(SCREEN_TIMEOUT_MS, "the transfer row in settings") { scrollSettingsTo(transferRowTitle()) }
    }

    /**
     * Одна попытка прокрутки настроек до строки.
     *
     * Отказ попытки — не ошибка: ленивый список не компонует строки ниже экрана, и первая
     * прокрутка — то, что их и создаёт. Тот же приём, что у прокрутки списка лекарств.
     */
    private fun scrollSettingsTo(text: String): Boolean = runCatching {
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_SETTINGS_SCREEN)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }.isSuccess

    /**
     * Выбран ли режим загрузки.
     *
     * Выбор режима виден только семантикой `Selected` (НФТ-33): слова строки от выбора
     * не меняются, и подпирать проверку кадром нельзя. Смотрятся оба дерева — слитое
     * и неслитое: свойство живёт на узле строки, а не на её тексте.
     */
    private fun modeIsSelected(tag: String): Boolean = listOf(true, false).any { unmerged ->
        compose.onAllNodes(hasTestTag(tag) and isSelected(), useUnmergedTree = unmerged)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }

    /** Есть ли на экране строка с этим куском: числа отчёта проверке заранее неизвестны. */
    private fun textContains(part: String): Boolean =
        compose.onAllNodesWithText(part, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun transferRowTitle(): String = ui(SettingsR.string.transfer_title)

    /** Имя лекарства — из ресурсов тестового APK, а не строкой в Kotlin (НФТ-42). */
    private fun medicationName(): String =
        testContext.getString(R.string.device_test_medication_name, MEDICATION_SUFFIX)

    /** Чип состава: то же склонение числа, что читает человек (НФТ-44). */
    private fun medicationChip(): String =
        targetContext.resources.getQuantityString(SettingsR.plurals.transfer_chip_medications, 1, 1)

    /** Строка продукта с подстановкой: отчёт называет числа той же строкой, что видит человек. */
    private fun stringOf(@StringRes id: Int, vararg args: Any): String = targetContext.getString(id, *args)

    /** Начало строки отчёта о добавленной истории: число подставляет отчёт, а не проверка. */
    private fun intakesPrefix(): String =
        stringOf(SettingsR.string.transfer_report_intakes, 0).substringBefore(':') + ":"

    /** Каталог стенда внутри хранилища приложения: внешнего хранилища продукт не просит (НФТ-17). */
    private fun standDirectory(): File = File(targetContext.filesDir, STAND_DIRECTORY).apply { mkdirs() }

    /** Страховочная копия перед заменой всего: постоянное имя во внутреннем хранилище (ПЕР-36). */
    private fun safetyCopyFile(): File = File(targetContext.filesDir, TransferFormat.SAFETY_COPY_NAME)

    private companion object {
        const val MEDICATION_ID = "journey-hostile-transfer"
        const val MEDICATION_SUFFIX = "hostile-transfer"
        const val STAND_DIRECTORY = "journey-hostile-transfer"
        const val BROKEN_FILE_NAME = "broken.json"

        /** Не выгрузка и не её подделка: даже опознавателя продукта в файле нет (ПЕР-6). */
        const val BROKEN_CONTENT = "this is not a transfer file"

        /** Неверное слово подтверждения: верное приходит ресурсом — его знает только продукт. */
        const val WRONG_WORD = "wrong"

        /** Время приёма: курс посеян со вчерашнего дня, и утренний приём сегодня уже наступил. */
        val MORNING: LocalTime = LocalTime.of(8, 0)

        /** Записи, которые оставляет перенос: до первой выгрузки в журнале нет ни одной. */
        val TRANSFER_WRITES = listOf(
            OpEvent.TRANSFER_APPLIED,
            OpEvent.TRANSFER_EXPORTED,
            OpEvent.TRANSFER_FILE_ACCEPTED,
            OpEvent.TRANSFER_MATCHES_RESOLVED,
            OpEvent.TRANSFER_SAFETY_COPY,
        )
    }
}

package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransfer
import ru.stohastika.sklyanka.acceptance.world.TransferFileBuilder
import ru.stohastika.sklyanka.data.transfer.JsonTransferCodec
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.transfer.MatchDecision
import ru.stohastika.sklyanka.domain.transfer.ReminderSound
import ru.stohastika.sklyanka.domain.transfer.TransferDecoded
import ru.stohastika.sklyanka.domain.transfer.TransferFormat
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_COUNTS_AFTER
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_COUNTS_NOW
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_DROP_ALL
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_FAILURE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_FAILURE_REASON
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_LAST_EXPORT
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_PICK_FILE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_REPLACE_CONFIRM_ACTION
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_REPLACE_WARNING
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_SAVE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_TRANSFER_SCREEN
import ru.stohastika.sklyanka.feature.settings.transfer.TransferFailure
import ru.stohastika.sklyanka.feature.settings.transfer.TransferStep
import ru.stohastika.sklyanka.feature.settings.transfer.matchTag
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_ERROR
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Экранные шаги приёмки переноса данных (SKL-19, группа `@screen`).
 *
 * Шаги живут в `feature-settings`, потому что говорят об одном разделе — переносе
 * (`SKL-26/TDD.md §4.3`). Состояние собирает настоящий `DataTransferViewModel` на мире
 * сценария ([TransferScreenProbe]), поэтому порядок шагов, отказы и отчёт — продукт,
 * а не пересказ; файл пишет и читает настоящий `JsonTransferCodec` из `core-data`.
 *
 * Предыстория и доменные проверки — в общем артефакте (`TransferSteps`): экранные шаги
 * читают тот же мир и не заводят данных, если сценарий этого не просит.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class TransferScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locale = context.resources.configuration.locales[0]

    /** Состояние устройства перед заменой: им проверяется содержимое страховочной копии. */
    private var profilesBefore: List<Profile> = emptyList()
    private var medicationsBefore: List<Medication> = emptyList()
    private var intakesBefore: List<Intake> = emptyList()

    /** Записи файла, подготовленного для сценария: по ним проверяется «ничего не появилось». */
    private var fileModelNames: List<String> = emptyList()

    // --- Выгрузка ------------------------------------------------------------------------------

    @Когда("^человек нажимает «Сохранить файл» и выбирает место сохранения$")
    fun thePersonSavesTheFile() {
        TransferScreenProbe.click(world, TAG_SAVE)
        assertTrue(TransferScreenProbe.exportRequested, "the save button must be on the screen")
        TransferScreenProbe.export(world)
    }

    @Когда("^человек сохраняет файл$")
    fun thePersonSavesTheFileAgain() {
        TransferScreenProbe.click(world, TAG_SAVE)
        TransferScreenProbe.export(world)
    }

    @Когда("^человек нажимает «Сохранить файл»$")
    fun thePersonPressesSave() {
        TransferScreenProbe.click(world, TAG_SAVE)
        assertTrue(TransferScreenProbe.exportRequested, "the save button must be on the screen")
    }

    @Когда("^человек смотрит на кнопку «Сохранить файл»$")
    fun thePersonLooksAtTheSaveButton() {
        TransferScreenProbe.ask(world) { assertTag(TAG_SAVE) }
    }

    @Когда("^человек открывает экран «Перенос данных»$")
    fun thePersonOpensTheTransferScreen() {
        TransferScreenProbe.ask(world) { assertTag(TAG_TRANSFER_SCREEN) }
    }

    @Допустим("^последняя удачная выгрузка сделана (.+)$")
    fun theLastExportWasOn(date: String) {
        runBlocking { world.transfer.settings.setLastExportDate(world.dateOf(date)) }
        TransferScreenProbe.restart(world)
    }

    @Допустим("^выгрузка не делалась ни разу$")
    fun thereWasNoExportYet() {
        assertNull(world.transfer.settings.lastExport, "the premise says no export happened")
    }

    @Допустим("^запись файла обрывается на середине$")
    fun theWriteIsInterrupted() {
        world.transfer.files.failOnWrite = true
        TransferScreenProbe.export(world)
        world.transfer.files.failOnWrite = false
        assertTrue(world.transfer.files.documents.isEmpty(), "the interrupted write must not leave a ready file")
    }

    @Тогда("^создаётся один файл с именем «([^»]+)»$")
    fun exactlyOneFileIsCreated(name: String) {
        assertEquals(name, TransferScreenProbe.state(world).exportFileName)
        assertEquals(1, world.transfer.files.documents.size, "the export creates exactly one file")
        assertTrue(rawExport().isNotEmpty(), "the exported file must carry the snapshot")
        assertEquals(world.clock.today(), world.transfer.settings.lastExport, "a successful export moves the date")
    }

    @Тогда(
        "^файл содержит оба профиля, все активные лекарства с их расписаниями, двенадцать архивных " +
            "курсов, историю приёмов с 12 февраля 2026 и настройки приложения$",
    )
    fun theFileCarriesEverything() {
        val snapshot = decode(rawExport())
        assertEquals(2, snapshot.profiles.size, "both profiles must be in the file")
        assertEquals(5, snapshot.counts.medications, "five active medications")
        assertEquals(12, snapshot.counts.archived, "twelve archived courses")
        assertEquals(LocalDate.of(2026, 2, 12), snapshot.counts.historyFrom)
        val active = snapshot.medications.filterNot { it.isArchived }
        assertTrue(active.isNotEmpty(), "the file must carry active medications")
        assertTrue(
            active.all { it.times.isNotEmpty() || it.scheduleMode == ScheduleMode.PRN },
            "every active medication must carry its schedule or be on demand",
        )
        assertEquals(ReminderSound.DEFAULT, snapshot.settings.reminderSound, "the settings must be in the file")
    }

    @Тогда("^файл не содержит сведений о выданных разрешениях и автозапуске$")
    fun theFileCarriesNoSystemState() {
        val raw = rawExport()
        val root = JSONObject(raw)
        assertEquals(
            REQUIRED_SECTIONS,
            root.keys().asSequence().toSet(),
            "the file must carry exactly the required sections (ПЕР-5)",
        )
        val settings = root.getJSONObject("settings")
        assertEquals(
            setOf("reminders"),
            settings.keys().asSequence().toSet(),
            "settings carry the reminder block only: permissions, autostart and the watch seam " +
                "are system state or excised from the build (ПЕР-26, Р-3, SKL-106)",
        )
        FORBIDDEN_KEYS.forEach { key ->
            assertFalse(raw.contains(key, ignoreCase = true), "the file must not carry «$key»")
        }
    }

    @Допустим("^на экране показан состав выгрузки: «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)»$")
    fun theScreenShowsTheComposition(
        medications: String,
        archived: String,
        history: String,
        profiles: String,
        settings: String,
    ) {
        val state = TransferScreenProbe.state(world)
        listOf(medications, archived, history, profiles, settings).forEach { chip ->
            assertTrue(TransferScreenProbe.hasText(state, chip), "the screen must show the chip «$chip»")
        }
    }

    @Тогда("^в файле ровно пять активных лекарств, двенадцать архивных курсов и два профиля$")
    fun theFileCountsMatchTheScreen() {
        val snapshot = decode(rawExport())
        assertEquals(5, snapshot.counts.medications)
        assertEquals(12, snapshot.counts.archived)
        assertEquals(2, snapshot.counts.profiles)
    }

    @Тогда("^глубина истории в файле — с (.+)$")
    fun theHistoryInTheFileStartsAt(date: String) {
        assertEquals(world.dateOf(date), decode(rawExport()).counts.historyFrom)
    }

    @Тогда("^рядом сказано, что файл содержит названия лекарств, дозы и историю приёмов$")
    fun theWarningAboutTheContent() {
        val warning = string(R.string.transfer_privacy_content)
        assertTrue(
            TransferScreenProbe.texts(TransferScreenProbe.state(world)).contains(warning),
            "the warning about the content must stand next to the button",
        )
    }

    @Тогда("^сказано, что файл не зашифрован и обращаться с ним следует как с медицинским документом$")
    fun theWarningAboutThePlainFile() {
        val warning = string(R.string.transfer_privacy_content)
        assertTrue(warning.contains("не зашифрован"), "the product warning must name the plain format")
        assertTrue(
            warning.contains("медицинским документом"),
            "the product warning must compare the file to a medical document",
        )
    }

    @Тогда(
        "^сказано, что место сохранения выбирает человек, и папка, синхронизируемая с облаком, унесёт данные в облако$",
    )
    fun theWarningAboutThePlace() {
        assertTrue(
            TransferScreenProbe.hasText(TransferScreenProbe.state(world), string(R.string.transfer_privacy_place)),
            "the warning about the place must be on the screen",
        )
    }

    @Тогда("^на экране есть строка «(.+)»$")
    fun theScreenCarriesTheLine(text: String) {
        assertTrue(TransferScreenProbe.hasText(TransferScreenProbe.state(world), text), "the screen must carry «$text»")
    }

    @Тогда("^он видит строку «Последняя выгрузка — (.+?)»$")
    fun heSeesTheLastExport(date: String) {
        assertTrue(
            TransferScreenProbe.hasText(
                TransferScreenProbe.state(world),
                string(R.string.transfer_last_export, date),
            ),
            "the last export date must be shown as a plain fact",
        )
    }

    @Но("^приложение не предлагает сделать новую выгрузку и не показывает предупреждений$")
    fun theAppDoesNotPushForANewExport() {
        val state = TransferScreenProbe.state(world)
        assertFalse(TransferScreenProbe.hasTag(state, TAG_FAILURE), "no warnings must be shown")
        val pushes = listOf("сделайте", "рекомендуем", "попробуйте", "напоминаем", "давно")
        TransferScreenProbe.texts(state).forEach { text ->
            pushes.forEach { push ->
                assertFalse(text.contains(push, ignoreCase = true), "the screen must not push: «$text»")
            }
        }
    }

    @Тогда("^строки о последней выгрузке на экране нет$")
    fun thereIsNoLastExportLine() {
        assertFalse(TransferScreenProbe.hasTag(TransferScreenProbe.state(world), TAG_LAST_EXPORT))
    }

    @Тогда("^готового файла «([^»]+)» в выбранном месте нет$")
    fun thereIsNoReadyFile(name: String) {
        assertEquals(name, TransferFormat.fileName(world.clock.today()))
        assertTrue(world.transfer.files.documents.isEmpty(), "the chosen place must hold no document")
        assertTrue(world.transfer.files.discarded.isNotEmpty(), "the interrupted target must be discarded")
    }

    @Тогда("^человеку сказано, что выгрузка не состоялась$")
    fun thePersonIsToldTheExportFailed() {
        assertTrue(
            TransferScreenProbe.hasText(
                TransferScreenProbe.state(world),
                string(R.string.transfer_error_export_failed),
            ),
            "the person must be told that the export did not happen",
        )
    }

    @Тогда("^дата последней выгрузки не меняется$")
    fun theLastExportDateDoesNotMove() {
        assertNull(world.transfer.settings.lastExport, "the date of the last export must not move after a failure")
    }

    // --- Разбор совпадений ---------------------------------------------------------------------

    @Допустим("^файл содержит три лекарства, совпадающих по названию и дозировке с лекарствами устройства$")
    fun theFileHasThreeMatches() {
        val owner = world.profiles.all().first { !it.isSelf }
        val devices = world.medications.all().filter { it.profileId == owner.id }
        assertEquals(3, devices.size, "the premise says the device profile carries three medications")
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.profile(owner.name)
        devices.forEachIndexed { index, device ->
            builder.medicationLike(
                key = "совпадающее лекарство ${index + 1}",
                profileKey = owner.name,
                device = device,
                times = listOf(MORNING),
            )
        }
        world.transfer.prepare(builder.build(exportedAt = exportedAt(FILE_DATE)))
        fileModelNames = devices.map { it.name }
    }

    @Когда("^человек открывает список совпадений$")
    fun thePersonOpensTheMatchList() {
        TransferScreenProbe.chooseFileAndContinue(world)
        assertEquals(TransferStep.MATCHES, TransferScreenProbe.state(world).step)
    }

    @Тогда("^в нём три записи, и каждая требует собственного решения$")
    fun thereAreThreeMatches() {
        val state = TransferScreenProbe.state(world)
        assertEquals(3, state.matches.size, "the list must carry one entry per match")
        assertEquals(emptyMap<String, MatchDecision>(), state.decisions, "no decision may be made for the person")
        state.matches.forEach { match ->
            assertTrue(TransferScreenProbe.hasTag(state, matchTag(match.file.id)), "every match needs its own card")
        }
    }

    @Тогда("^кнопки «применить решение ко всем совпадениям» нет$")
    fun thereIsNoBulkAction() {
        val texts = TransferScreenProbe.texts(TransferScreenProbe.state(world))
        val bulk = texts.filter { it.contains("ко всем") }
        assertEquals(emptyList<String>(), bulk, "a bulk decision is forbidden (ПЕР-22)")
    }

    @Но("^человеку доступен выход «не загружать ничего из совпавших»$")
    fun theDropAllExitExists() {
        val state = TransferScreenProbe.state(world)
        assertTrue(TransferScreenProbe.hasTag(state, TAG_DROP_ALL), "the exit from the list must exist")
        assertTrue(
            TransferScreenProbe.hasText(state, string(R.string.transfer_matches_drop_all)),
            "the exit must say what it does",
        )
    }

    @Когда("^человек выбирает этот файл$")
    fun thePersonChoosesTheFile() {
        TransferScreenProbe.chooseFileAndContinue(world)
    }

    @Тогда("^до применения показан список совпадений, и в нём одна запись — «([^»]+)»$")
    fun oneMatchIsShown(title: String) {
        val state = TransferScreenProbe.state(world)
        assertEquals(TransferStep.MATCHES, state.step, "the match list must be shown before anything is applied")
        assertEquals(1, state.matches.size)
        val fileMedication = state.matches.single().file
        val strength = string(
            R.string.transfer_match_dosage_value,
            fileMedication.strength?.stripTrailingZeros()?.toPlainString().orEmpty(),
            string(R.string.transfer_strength_unit_milligram),
        ).trim()
        val expected = string(R.string.transfer_match_dosage, fileMedication.name, strength)
        assertTrue(TransferScreenProbe.hasText(state, expected), "the card «$title» must read «$expected»")
    }

    @Тогда("^в записи видны различия: время приёма на устройстве «(.+)», в файле «(.+)»$")
    fun theDifferencesAreShown(mine: String, theirs: String) {
        assertTrue(
            TransferScreenProbe.hasText(
                TransferScreenProbe.state(world),
                string(R.string.transfer_match_difference_times, mine, theirs),
            ),
            "the card must show the difference in the intake times",
        )
    }

    @Тогда("^человеку предложены четыре решения: «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)»$")
    fun theFourDecisionsAreOffered(first: String, second: String, third: String, fourth: String) {
        val state = TransferScreenProbe.state(world)
        listOf(first, second, third, fourth).forEach { decision ->
            assertTrue(
                TransferScreenProbe.hasText(state, decisionLabel(decision)),
                "the decision «$decision» must be offered",
            )
        }
    }

    @Но("^ничего ещё не применено$")
    fun nothingIsAppliedYet() {
        assertNull(world.transfer.lastOutcome, "nothing may be applied before the confirmation")
        assertTrue(world.transfer.store.applied.isEmpty(), "the store must not be written before the confirmation")
    }

    // --- Отказы --------------------------------------------------------------------------------

    @Допустим("^в файле корректны разделы профилей и лекарств, но повреждён раздел истории$")
    fun theFileHistoryIsBroken() {
        val base = simpleFileSnapshot()
        fileModelNames = base.medications.map { it.name }
        putRaw(brokenLinkRaw(base))
    }

    @Допустим("^файл создан более новой версией формата, чем понимает установленное приложение$")
    fun theFileIsFromTheFuture() {
        val base = simpleFileSnapshot()
        fileModelNames = base.medications.map { it.name }
        val raw = encode(base)
        val patched = raw.replace(
            "\"format_version\": ${TransferFormat.CURRENT_VERSION}",
            "\"format_version\": ${TransferFormat.CURRENT_VERSION + 1}",
        )
        putRaw(patched)
    }

    @Допустим("^в файле у лекарства «(.+?)» указана форма выпуска, незнакомая приложению$")
    fun theFileCarriesAnUnknownForm(medication: String) {
        val modelName = "file-medication-unknown-form"
        val base = simpleFileSnapshot(medicationName = modelName)
        fileModelNames = base.medications.map { it.name }
        world.transfer.fileMedicationNames[medication] = modelName
        putRaw(encode(base).replaceFirst("\"form\": \"$TABLET_CODE\"", "\"form\": \"ALIEN_FORM\""))
        world.transfer.read(ScenarioTransfer.IMPORT_HANDLE)
    }

    @Допустим("^в файле в настройках напоминания указан звук, отсутствующий на этом устройстве$")
    fun theFileCarriesAnUnknownSound() {
        val base = simpleFileSnapshot()
        fileModelNames = base.medications.map { it.name }
        val raw = encode(base)
        putRaw(raw.replace("\"sound\": \"${ReminderSound.DEFAULT.code}\"", "\"sound\": \"alien-signal\""))
        world.transfer.read(ScenarioTransfer.IMPORT_HANDLE)
    }

    @Когда(
        "^человек выбирает (файл, который не читается как JSON|JSON без опознавателя выгрузки Склянки|" +
            "выгрузку Склянки, обрезанную на середине|выгрузку, где запись истории ссылается " +
            "на несуществующее лекарство)$",
    )
    fun thePersonChoosesABrokenFile(description: String) {
        val base = simpleFileSnapshot()
        fileModelNames = base.medications.map { it.name }
        val raw = encode(base)
        val damaged = when (description) {
            "файл, который не читается как JSON" -> "{ this is not json at all"
            "JSON без опознавателя выгрузки Склянки" -> raw.replace(FORMAT_MARKER_LINE.toRegex(), "")
            "выгрузку Склянки, обрезанную на середине" -> raw.substring(0, raw.length / 2)
            else -> brokenLinkRaw(base)
        }
        putRaw(damaged)
        TransferScreenProbe.chooseFile(world)
    }

    @Тогда("^показано сообщение «Файл не похож на выгрузку Склянки» с пояснением одной строкой$")
    fun theNotAnExportMessage() {
        val state = TransferScreenProbe.state(world)
        assertTrue(state.failure is TransferFailure.NotAnExport, "the screen must tell about the refusal")
        assertTrue(
            TransferScreenProbe.hasText(state, string(R.string.transfer_error_not_an_export)),
            "the refusal must be named",
        )
        assertTrue(TransferScreenProbe.hasTag(state, TAG_FAILURE_REASON), "the refusal must carry one line of reason")
    }

    @Тогда("^на устройстве остаются прежние пять лекарств, двенадцать архивных курсов и два профиля$")
    fun theDeviceIsUntouched() {
        assertTrue(world.transfer.store.applied.isEmpty(), "no plan may be applied")
        val counts = world.transfer.counts()
        assertEquals(5, counts.medications)
        assertEquals(12, counts.archived)
        assertEquals(2, counts.profiles)
    }

    @Тогда("^ни одна запись из файла не применена$")
    fun nothingFromTheFileIsApplied() {
        assertNull(world.transfer.lastOutcome, "nothing may be applied")
        assertTrue(world.transfer.store.applied.isEmpty())
    }

    @Тогда("^загрузка отклоняется целиком$")
    fun theImportIsRefusedWhole() {
        val state = TransferScreenProbe.state(world)
        assertTrue(state.failure is TransferFailure.NotAnExport, "the file must be refused as a whole")
        assertTrue(world.transfer.store.applied.isEmpty())
    }

    @Тогда("^ни одного профиля и ни одного лекарства из файла на устройстве не появилось$")
    fun nothingFromTheFileAppeared() {
        assertTrue(
            world.medications.all().none { it.name in fileModelNames },
            "no medication from the refused file may appear",
        )
        assertEquals(2, world.transfer.counts().profiles)
    }

    @Тогда("^человеку не предлагается «загрузить что получится»$")
    fun noPartialRescueIsOffered() {
        val texts = TransferScreenProbe.texts(TransferScreenProbe.state(world))
        val rescue = texts.filter { it.contains("что получится") || it.contains("частичн") }
        assertEquals(emptyList<String>(), rescue, "partial rescue must not be offered")
    }

    @Тогда("^показано сообщение «Файл создан более новой версией Склянки. Обновите приложение»$")
    fun theFutureFormatMessage() {
        val state = TransferScreenProbe.state(world)
        assertTrue(
            state.failure is TransferFailure.FromFuture,
            "the future format must be told apart, not ${state.failure}",
        )
        assertTrue(TransferScreenProbe.hasText(state, string(R.string.transfer_error_from_future)))
    }

    @Тогда("^ничего не применено$")
    fun nothingWasApplied() {
        assertTrue(world.transfer.store.applied.isEmpty())
        assertNull(world.transfer.lastOutcome)
    }

    @Тогда("^приложение не пыталось прочитать знакомые ему разделы файла$")
    fun theAppDidNotReadFamiliarSections() {
        assertTrue(world.transfer.prepared == null, "no snapshot may be assembled from a file from the future")
        assertTrue(
            world.log.events.contains(OpEvent.TRANSFER_FILE_REJECTED),
            "the refusal must leave a trace in the journal",
        )
        assertFalse(
            world.log.events.contains(OpEvent.TRANSFER_FILE_ACCEPTED),
            "a file from the future must not be accepted",
        )
    }

    @Тогда("^загрузка завершается$")
    fun theImportFinishes() {
        assertTrue(world.transfer.lastOutcome is TransferOutcome.Applied, "the import must finish")
    }

    @Тогда("^лекарство «(.+?)» перенесено$")
    fun theMedicationArrived(medication: String) {
        val modelName = fileModelName(medication)
        assertTrue(world.medications.all().any { it.name == modelName }, "«$medication» must arrive from the file")
    }

    @Тогда("^в отчёте сказано, что форма выпуска «(.+?)» не распознана и заменена значением по умолчанию$")
    fun theReportNamesTheUnknownForm(medication: String) {
        val modelName = fileModelName(medication)
        val expected = string(R.string.transfer_report_notice_enum, string(R.string.transfer_field_form), modelName)
        assertTrue(
            TransferScreenProbe.hasText(TransferScreenProbe.reportState(world), expected),
            "the report must carry «$expected»",
        )
    }

    @Тогда("^загрузка завершается успешно$")
    fun theImportFinishesSuccessfully() {
        assertTrue(world.transfer.lastOutcome is TransferOutcome.Applied)
    }

    @Тогда("^звук напоминания принимает значение по умолчанию$")
    fun theSoundFallsBackToDefault() {
        assertEquals(ReminderSound.DEFAULT, world.transfer.settings.values.reminderSound)
    }

    @Тогда("^в отчёте сказано, что звук из файла не найден и заменён$")
    fun theReportNamesTheUnknownSound() {
        assertTrue(
            TransferScreenProbe.hasText(
                TransferScreenProbe.reportState(world),
                string(R.string.transfer_report_notice_sound),
            ),
            "the report must tell about the replaced sound",
        )
    }

    // --- Замена всего --------------------------------------------------------------------------

    @Допустим("^человек выбрал режим «Заменить всё»$")
    fun thePersonChoseReplaceAll() {
        TransferScreenProbe.selectMode(world, TransferMode.REPLACE_ALL)
    }

    @Когда("^человек выбирает файл выгрузки от (.+)$")
    fun thePersonChoosesTheFileOf(date: String) {
        prepareSimpleFile(SIMPLE_FILE_MEDICATION, listOf(EVENING), world.dateOf(date))
        TransferScreenProbe.chooseFileAndContinue(world)
        assertEquals(TransferStep.REPLACE_CONFIRM, TransferScreenProbe.state(world).step)
    }

    @Тогда("^показано предупреждение, что текущие данные на этом устройстве будут стёрты$")
    fun theReplacementIsWarned() {
        val state = TransferScreenProbe.state(world)
        assertTrue(TransferScreenProbe.hasTag(state, TAG_REPLACE_WARNING))
        assertTrue(TransferScreenProbe.hasText(state, string(R.string.transfer_replace_warning)))
    }

    @Тогда(
        "^в предупреждении названы числа: пять лекарств, двенадцать в архиве, история с 12 февраля 2026 и два профиля$",
    )
    fun theWarningNamesTheNumbers() {
        val state = TransferScreenProbe.state(world)
        assertTrue(TransferScreenProbe.hasTag(state, TAG_COUNTS_NOW), "the numbers of the device must be shown")
        val plural = context.resources
        val chips = listOf(
            plural.getQuantityString(R.plurals.transfer_chip_medications, 5, 5),
            plural.getQuantityString(R.plurals.transfer_chip_archived, 12, 12),
            plural.getQuantityString(R.plurals.transfer_chip_profiles, 2, 2),
        )
        chips.forEach { chip ->
            assertTrue(TransferScreenProbe.hasText(state, chip), "the warning must name the number «$chip»")
        }
        assertTrue(
            TransferScreenProbe.hasText(
                state,
                string(R.string.transfer_replace_history_from, fullDate(LocalDate.of(2026, 2, 12))),
            ),
            "the warning must name the depth of the history",
        )
    }

    @Тогда("^подтверждение требует ввода слова «([^»]+)», а не второго нажатия подряд с кнопкой «Выбрать файл»$")
    fun theConfirmationRequiresTheWord(word: String) {
        val state = TransferScreenProbe.state(world)
        assertTrue(
            TransferScreenProbe.hasText(state, string(R.string.transfer_replace_word_label, word)),
            "the confirmation must show the word sample «$word»",
        )
        assertTrue(TransferScreenProbe.hasTag(state, TAG_REPLACE_CONFIRM_ACTION))
        assertTrue(TransferScreenProbe.hasTag(state, TAG_COUNTS_AFTER), "the file side must be shown too")
        assertFalse(
            TransferScreenProbe.hasTag(state, TAG_PICK_FILE),
            "the confirmation screen must not carry the button of the previous step",
        )
    }

    @Тогда("^пока слово не введено верно, кнопка подтверждения недоступна$")
    fun theConfirmationIsUnavailableUntilTheWord() {
        theConfirmationActionIsUnavailable()
    }

    @Тогда("^кнопка подтверждения недоступна$")
    fun theConfirmationActionIsUnavailable() {
        TransferScreenProbe.ask(world) { onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION).assertIsNotEnabled() }
    }

    @Тогда("^кнопка подтверждения доступна$")
    fun theConfirmationActionIsAvailable() {
        TransferScreenProbe.ask(world) { onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION).assertIsEnabled() }
    }

    @Тогда("^поле подтверждения не подсвечено как ошибка$")
    fun theConfirmationFieldIsNotAnError() {
        // ОВ-38 к этому полю не применяется: неверный ввод виден недоступной кнопкой,
        // и подписи ошибки под полем нет.
        assertFalse(
            TransferScreenProbe.hasTag(TransferScreenProbe.state(world), TAG_TEXT_FIELD_ERROR),
            "the word field must not carry an error caption",
        )
    }

    @Когда("^человек вводит в поле подтверждения «(.+)»$")
    fun thePersonTypesTheConfirmationWord(word: String) {
        TransferScreenProbe.typeConfirmationWord(world, word)
    }

    @Когда("^человек нажимает кнопку подтверждения$")
    fun thePersonPressesTheConfirmation() {
        TransferScreenProbe.act(world) {
            onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION).performClick()
        }
    }

    @Тогда("^загрузка завершается отчётом$")
    fun theImportFinishesWithTheReport() {
        assertEquals(
            TransferStep.REPORT,
            TransferScreenProbe.state(world).step,
            "the confirmation must start the replacement and lead to the report",
        )
        assertTrue(world.transfer.store.applied.isNotEmpty(), "the replacement must reach the store")
    }

    @Но("^пока человек не подтвердил, на устройстве ничего не изменилось$")
    fun nothingChangedBeforeTheConfirmation() {
        assertTrue(world.transfer.store.applied.isEmpty())
        val counts = world.transfer.counts()
        assertEquals(5, counts.medications)
        assertEquals(12, counts.archived)
        assertEquals(2, counts.profiles)
    }

    @Допустим("^человек выбрал режим «Заменить всё» и ввёл слово «([^»]+)»$")
    fun thePersonConfirmedTheReplacement(word: String) {
        prepareSimpleFile(SIMPLE_FILE_MEDICATION, listOf(EVENING), FILE_DATE)
        TransferScreenProbe.selectMode(world, TransferMode.REPLACE_ALL)
        TransferScreenProbe.chooseFileAndContinue(world)
        profilesBefore = world.profiles.all()
        medicationsBefore = world.medications.all()
        intakesBefore = world.intakes.all()
        assertEquals(TransferStep.REPLACE_CONFIRM, TransferScreenProbe.state(world).step)
        thePersonTypesTheConfirmationWord(word)
        TransferScreenProbe.ask(world) { onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION).assertIsEnabled() }
    }

    @Когда("^загрузка начинается$")
    fun theImportBegins() {
        TransferScreenProbe.apply(world)
    }

    @Тогда("^до стирания текущих данных создаётся страховочная копия текущего содержимого$")
    fun theSafetyCopyWasMadeBeforeErasing() {
        val content = requireNotNull(world.transfer.files.safetyCopyContent) {
            "the safety copy must be written before the erasure"
        }
        val snapshot = decode(content)
        assertEquals(profilesBefore.map { it.id }.sorted(), snapshot.profiles.map { it.id }.sorted())
        assertEquals(medicationsBefore.map { it.id }.sorted(), snapshot.medications.map { it.id }.sorted())
        assertEquals(intakesBefore.map { it.id }.sorted(), snapshot.intakes.map { it.id }.sorted())
    }

    @Тогда("^человеку сказано, где она лежит$")
    fun thePersonIsToldWhereTheCopyIs() {
        assertTrue(
            TransferScreenProbe.hasText(
                TransferScreenProbe.state(world),
                string(R.string.transfer_report_safety_copy, TransferFormat.SAFETY_COPY_NAME),
            ),
            "the report must say where the safety copy lies",
        )
    }

    @Тогда("^страховочная копия недоступна другим приложениям$")
    fun theSafetyCopyIsInternal() {
        // Копия лежит во внутреннем хранилище приложения, а не в выбранном человеком месте:
        // порт держит её отдельным слотом (ПЕР-36), а рабочая реализация пишет её
        // в `context.filesDir` — и разрешений на внешнее хранилище приложение не просит.
        assertEquals(1, world.transfer.files.internalCopyCount)
        val implementation = File(
            RepositoryLayout.root,
            "app/src/main/kotlin/ru/stohastika/sklyanka/platform/transfer/ContentResolverTransferFiles.kt",
        ).readText()
        assertTrue(
            implementation.contains("File(context.filesDir, TransferFormat.SAFETY_COPY_NAME)"),
            "the working implementation must keep the copy in the internal storage",
        )
        val manifest = File(RepositoryLayout.root, "app/src/main/AndroidManifest.xml").readText()
        listOf("MANAGE_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE").forEach { permission ->
            assertFalse(manifest.contains(permission), "the app must not request $permission (НФТ-17)")
        }
    }

    @Допустим("^на чистом устройстве выполнена загрузка файла от (.+?) в режиме «Заменить всё»$")
    fun theCleanDeviceLoadedTheFile(date: String) {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medication(
            key = IDENTITY_FILE_MEDICATION,
            profileKey = FILE_SELF,
            name = IDENTITY_FILE_MEDICATION,
            times = listOf(MORNING, EVENING),
            startDate = world.dateOf(date),
            stockLeft = BigDecimal.TEN,
        )
        val moment = world.clock.momentOf(world.dateOf(date).atTime(MORNING))
        builder.intake(
            medicationKey = IDENTITY_FILE_MEDICATION,
            at = world.dateOf(date).atTime(MORNING),
            status = IntakeStatus.TAKEN,
            actedAt = moment,
            source = IntakeSource.PHONE,
        )
        builder.intake(
            medicationKey = IDENTITY_FILE_MEDICATION,
            at = world.dateOf(date).atTime(EVENING),
            status = IntakeStatus.MISSED,
            actedAt = world.clock.momentOf(world.dateOf(date).atTime(EVENING)),
            source = IntakeSource.AUTO,
        )
        world.transfer.prepare(builder.build(exportedAt = exportedAt(world.dateOf(date))))
        TransferScreenProbe.selectMode(world, TransferMode.REPLACE_ALL)
        TransferScreenProbe.chooseFile(world)
        TransferScreenProbe.apply(world)
        assertEquals(TransferStep.REPORT, TransferScreenProbe.state(world).step, "the loaded file must be applied")
    }

    @Когда("^человек сразу выгружает данные в новый файл$")
    fun thePersonExportsRightAway() {
        TransferScreenProbe.export(world)
        assertEquals(world.clock.today(), world.transfer.settings.lastExport, "the export must be saved")
        assertTrue(world.transfer.files.documents.containsKey(ScenarioTransfer.EXPORT_HANDLE))
    }

    @Тогда(
        "^новый файл содержит те же профили, лекарства и записи истории, что и загруженный, с теми же идентификаторами$",
    )
    fun theNewFileEqualsTheLoadedOne() {
        val original = decode(raw(ScenarioTransfer.IMPORT_HANDLE))
        val exported = decode(raw(ScenarioTransfer.EXPORT_HANDLE))
        assertEquals(original.profiles, exported.profiles)
        assertEquals(original.medications, exported.medications)
        // История из файла переносится как есть. Кроме неё, снимок содержит и приёмы,
        // порождённые планировщиком уже на устройстве, — их у загруженного файла не было,
        // и это не потеря и не удвоение: каждая запись исходного файла на месте со своим
        // идентификатором и ровно одна.
        original.intakes.forEach { record ->
            assertEquals(
                record,
                exported.intakes.firstOrNull { it.id == record.id },
                "the record ${record.id} must survive with its identifier",
            )
        }
        assertEquals(
            exported.intakes.map { it.id }.distinct().size,
            exported.intakes.size,
            "no record may double",
        )
    }

    @Тогда("^каждая запись истории привязана к тому же лекарству, что и в исходном файле$")
    fun everyIntakeKeepsItsMedication() {
        val original = decode(raw(ScenarioTransfer.IMPORT_HANDLE))
        val exported = decode(raw(ScenarioTransfer.EXPORT_HANDLE))
        val bindings = exported.intakes.associate { it.id to it.medicationId }
        original.intakes.forEach { record ->
            assertEquals(
                record.medicationId,
                bindings[record.id],
                "the record ${record.id} must keep its medication",
            )
        }
    }

    @Когда("^человек снова загружает исходный файл в режиме «Заменить всё»$")
    fun thePersonLoadsTheOriginalAgain() {
        val before = world.medications.all().map { it.id }.sorted()
        TransferScreenProbe.selectMode(world, TransferMode.REPLACE_ALL)
        TransferScreenProbe.chooseFile(world, handle = ScenarioTransfer.IMPORT_HANDLE)
        TransferScreenProbe.apply(world)
        assertEquals(before, world.medications.all().map { it.id }.sorted(), "the composition must not change")
    }

    @Тогда("^состав данных на устройстве не меняется и записи не удваиваются$")
    fun theCompositionDoesNotChange() {
        val original = decode(raw(ScenarioTransfer.IMPORT_HANDLE))
        assertEquals(
            original.profiles.map { it.id }.sorted(),
            world.profiles.all().map { it.id }.sorted(),
            "the profiles must stay as the file brought them",
        )
        assertEquals(
            original.medications.map { it.id }.sorted(),
            world.medications.all().map { it.id }.sorted(),
            "the medications must stay as the file brought them",
        )
        original.intakes.forEach { record ->
            val current = world.intakes.all().filter { it.id == record.id }
            assertEquals(1, current.size, "the record ${record.id} must stay exactly once")
            assertEquals(record, current.single())
        }
    }

    @Допустим("^свободного места на устройстве меньше, чем нужно для страховочной копии и загрузки$")
    fun theDeviceIsAlmostFull() {
        world.transfer.files.freeSpace = 0
    }

    @Когда("^загрузка запускается$")
    fun theImportIsStarted() {
        TransferScreenProbe.apply(world)
    }

    @Тогда("^человеку сказано, что места недостаточно$")
    fun thePersonIsToldAboutSpace() {
        val state = TransferScreenProbe.state(world)
        assertTrue(
            TransferScreenProbe.hasText(state, string(R.string.transfer_error_no_space)),
            "the person must be told about the lack of space; step is ${state.step}, failure ${state.failure}",
        )
    }

    @Тогда("^текущие данные не стёрты$")
    fun theCurrentDataIsNotErased() {
        assertTrue(world.transfer.store.applied.isEmpty(), "nothing may be erased")
        val counts = world.transfer.counts()
        assertEquals(5, counts.medications)
        assertEquals(12, counts.archived)
        assertEquals(2, counts.profiles)
    }

    @Тогда("^страховочная копия не создана наполовину$")
    fun theSafetyCopyIsNotHalfMade() {
        assertNull(world.transfer.files.safetyCopy())
    }

    // --- Прерванная загрузка -------------------------------------------------------------------

    @Когда("^загрузка обрывается на середине применения$")
    fun theImportIsInterrupted() {
        world.transfer.store.failAfterPartial = IllegalStateException("the process was killed in the middle")
        val outcome = world.transfer.apply()
        world.transfer.store.failAfterPartial = null
        assertTrue(outcome is TransferOutcome.NotApplied, "the interrupted import must not be applied")
        // Процесс убит до снятия признака: снять его было некому. JVM-прогон воспроизводит
        // след убийства, а не само убийство, и признак, оставшийся взведённым, — этот след.
        world.transfer.settings.killedDuringImport()
        TransferScreenProbe.restart(world)
    }

    @Тогда("^ни одного лекарства из файла на устройстве нет$")
    fun noFileMedicationLanded() {
        val names = world.transfer.prepared?.medications?.map { it.name }.orEmpty().ifEmpty { fileModelNames }
        assertTrue(names.isNotEmpty(), "the scenario must know what the file carried")
        assertTrue(world.medications.all().none { it.name in names })
    }

    @Тогда("^при следующем запуске приложение сообщает, что загрузка не состоялась$")
    fun theNextLaunchReportsTheFailure() {
        val state = TransferScreenProbe.state(world)
        assertTrue(state.importDidNotHappen, "the next launch must know the import did not happen")
        assertTrue(
            TransferScreenProbe.hasText(state, string(R.string.transfer_error_import_interrupted)),
            "the screen must tell that the import did not happen",
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /**
     * Имя модели лекарства файла по ключу сценария в любом падеже.
     *
     * Сценарий зовёт «Эутирокс», а проверка отчёта — «Эутирокса»: падеж разбирает
     * [RussianNames], как и в общем словаре шагов.
     */
    private fun fileModelName(medication: String): String {
        val key = RussianNames.resolve(medication, world.transfer.fileMedicationNames.keys.toList()) ?: medication
        return requireNotNull(world.transfer.fileMedicationNames[key]) {
            "the scenario named no file medication «$medication»"
        }
    }

    /** Подпись решения из ресурсов: сценарий пишет его со строчной, чип показывает с прописной. */
    private fun decisionLabel(text: String): String = string(
        when (text) {
            "оставить моё" -> R.string.transfer_decision_keep_mine
            "взять из файла" -> R.string.transfer_decision_take_from_file
            "добавить отдельно" -> R.string.transfer_decision_add_separately
            else -> R.string.transfer_decision_do_not_load
        },
    )

    private fun fullDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern(string(R.string.transfer_date_pattern_full), locale))

    private fun raw(handle: String): String =
        requireNotNull(world.transfer.files.documents[handle]) { "no file was written at $handle" }

    private fun rawExport(): String = raw(ScenarioTransfer.EXPORT_HANDLE)

    private fun putRaw(raw: String) {
        world.transfer.codec = JsonTransferCodec(world.clock.time)
        world.transfer.files.put(ScenarioTransfer.IMPORT_HANDLE, raw)
    }

    private fun encode(snapshot: TransferSnapshot): String {
        world.transfer.codec = JsonTransferCodec(world.clock.time)
        return world.transfer.codec.encode(snapshot)
    }

    private fun decode(raw: String): TransferSnapshot {
        world.transfer.codec = JsonTransferCodec(world.clock.time)
        val decoded = world.transfer.codec.decode(raw)
        assertTrue(decoded is TransferDecoded.Loaded, "the file must be readable: $decoded")
        return (decoded as TransferDecoded.Loaded).snapshot
    }

    private fun simpleFileSnapshot(medicationName: String = SIMPLE_FILE_MEDICATION): TransferSnapshot {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medication(
            key = medicationName,
            profileKey = FILE_SELF,
            name = medicationName,
            times = listOf(MORNING),
            startDate = FILE_DATE,
            stockLeft = BigDecimal.TEN,
        )
        fileModelNames = listOf(medicationName)
        return builder.build(exportedAt = exportedAt(FILE_DATE))
    }

    private fun brokenLinkRaw(base: TransferSnapshot): String {
        val broken = TransferSnapshot.of(
            exportedAt = base.exportedAt,
            app = base.app,
            profiles = base.profiles,
            medications = base.medications,
            intakes = listOf(
                Intake(
                    id = "file-intake-broken",
                    medicationId = "missing-medication",
                    scheduledAt = base.exportedAt.atZone(world.clock.zone).toLocalDateTime(),
                    status = IntakeStatus.TAKEN,
                    actedAt = base.exportedAt,
                    source = IntakeSource.PHONE,
                    actorProfileId = null,
                ),
            ),
            settings = base.settings,
        )
        return encode(broken)
    }

    private fun prepareSimpleFile(medicationName: String, times: List<LocalTime>, date: LocalDate) {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medication(
            key = medicationName,
            profileKey = FILE_SELF,
            name = medicationName,
            times = times,
            startDate = date,
        )
        world.transfer.prepare(builder.build(exportedAt = exportedAt(date)))
        fileModelNames = listOf(medicationName)
    }

    private fun exportedAt(date: LocalDate): Instant = world.clock.momentOf(date.atTime(EXPORT_HOUR, EXPORT_MINUTE))

    /** «Экран показывает узел»: узел ищется по тегу, а не по подписи (НФТ-30). */
    private fun ComposeUiTest.assertTag(tag: String) = onNodeWithTag(tag).assertIsDisplayed()

    private companion object {
        const val FILE_SELF = "файловый свой"
        const val SIMPLE_FILE_MEDICATION = "file-medication-simple"
        const val IDENTITY_FILE_MEDICATION = "file-medication-identity"
        const val TABLET_CODE = "TABLET"

        val FILE_DATE: LocalDate = LocalDate.of(2026, 10, 5)
        const val EXPORT_HOUR = 18
        const val EXPORT_MINUTE = 14

        val MORNING: LocalTime = LocalTime.of(9, 0)
        val EVENING: LocalTime = LocalTime.of(21, 0)

        val REQUIRED_SECTIONS = setOf(
            "format",
            "format_version",
            "exported_at",
            "app",
            "counts",
            "profiles",
            "medications",
            "intakes",
            "settings",
        )
        val FORBIDDEN_KEYS = listOf("permission", "autostart", "channel", "connection", "grant", "emui")

        /** Объявление опознавателя в файле: его отсутствие делает файл «чужим» (ПЕР-28). */
        const val FORMAT_MARKER_LINE = "\\s*\"format\":\\s*\"sklyanka-export\","
    }
}

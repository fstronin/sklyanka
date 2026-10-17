package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildExtendedSectionsUseCase
import ru.stohastika.sklyanka.domain.history.BuildHistoryCsvTableUseCase
import ru.stohastika.sklyanka.domain.history.DoctorReportTransfer
import ru.stohastika.sklyanka.domain.history.ExportHistoryCsvUseCase
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.HistoryCsvDocument
import ru.stohastika.sklyanka.domain.history.HistoryCsvSaveOutcome
import ru.stohastika.sklyanka.domain.history.HistoryCsvStatus
import ru.stohastika.sklyanka.domain.history.HistoryCsvTable
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportShareLauncher
import ru.stohastika.sklyanka.domain.history.ReportTarget
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import java.io.File
import java.time.LocalTime

/**
 * Шаги приёмки SKL-119 — состав отчёта и выгрузка истории (Р-85, Р-35, Р-86).
 *
 * Мир отдаёт данные, а правила исполняют **настоящие** сценарии домена: разделы считает
 * `BuildExtendedSectionsUseCase`, таблицу выгрузки — `BuildHistoryCsvTableUseCase`, запись —
 * `ExportHistoryCsvUseCase` на файловом порте мира. Записей правил здесь нет — иначе сценарий
 * проверял бы пересказ продукта.
 *
 * **Чего эти шаги не проверяют.** Слова шапки, колонок и статусов — их держит экранный прогон
 * (`feature-history`, тег `@screen`): в чистом JVM-модуле нет ни каталогов строк, ни ресурсов.
 * Здесь проверяется, что ключ словаря есть и что он непуст: ключ без слов — не слова (П-7).
 * Оплаты в продукте нет (Р-86), поэтому доступность расширенного состава и выгрузки проверяется
 * экранным прогоном, а не подпиской в мире сценария.
 */
@Suppress("TooManyFunctions")
class Sk119Steps(private val world: AcceptanceWorld) {

    /** Расширенные разделы последнего собранного состава. */
    private var sections: ExtendedReportSections = ExtendedReportSections.NONE

    /** Таблица последней выгрузки. */
    private var table: HistoryCsvTable? = null

    /** Содержимое последнего файла журнала срабатываний (НФТ-51). */
    private var journalFile: String? = null

    /** Исход последней записи файла. */
    private var saveOutcome: HistoryCsvSaveOutcome? = null

    // --- Предыстория ---------------------------------------------------------------

    /**
     * Месяц принятых приёмов: так выглядит сравнение периодов у человека, который лечится давно.
     *
     * Приёмы ставятся настоящей отметкой (`world.intakes.markTaken`), а не записью статуса:
     * сценарий, начинающийся с принятых приёмов, обязан начинаться с того же состояния,
     * которое даёт отметка.
     */
    @Допустим("^за (.+?) 2026 «([^»]+)» принимался каждый день$")
    fun theMedicationWasTakenEveryDayIn(monthName: String, medication: String) {
        val month = RussianDates.month("$monthName 2026", world.clock.today())
        val name = world.medicationNamed(medication)
        val times = world.medications.get(name).times.ifEmpty { listOf(MORNING) }
        world.medications.update(name) { it.copy(startDate = month.atDay(1), endDate = month.atEndOfMonth()) }
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            times.forEach { time -> world.intakes.markTaken(name, date.atTime(time)) }
        }
    }

    // Лекарство «по потребности» заводит существующий шаг «у профиля есть лекарство «…»
    // по потребности» (SKL-08): второго объявления той же строки словарь не допускает,
    // а правило у неё то же — расписания нет, и времени плана у приёма не бывает (ПР-4).

    /**
     * Пропуски, отмеченные человеком: последние [missed] приёмов месяца лекарства.
     *
     * Хвост, а не начало: приёмы месяца заводятся по порядку, и «пропущено 1 из 30» значит
     * ровно один пропуск рядом с двадцатью девятью принятыми — так их и увидит врач.
     */
    @Допустим("^в сентябре 2026 у «([^»]+)» пропущено (\\d+) приём\\S* из (\\d+)$")
    fun thatMedicationMissedThatManyOf(medication: String, missed: Int, planned: Int) {
        val name = world.medicationNamed(medication)
        val moments = septemberSlots(name)
        assertEquals(planned, moments.size, "the schedule of «$medication» gives ${moments.size} intakes, not $planned")
        world.intakes.removeOf(world.medications.get(name).id)
        moments.forEach { moment -> world.intakes.pending(name, moment) }
        moments.dropLast(missed).forEach { moment -> world.intakes.markTaken(name, moment) }
        moments.takeLast(missed).forEach { moment -> skipAsPerson(name, moment) }
    }

    @Допустим("^«([^»]+)» принят (.+?) в (\\d{2}):(\\d{2})$")
    fun theIntakeWasTakenOn(medication: String, date: String, hour: String, minute: String) {
        world.intakes.markTaken(world.medicationNamed(medication), world.localMoment(date, "$hour:$minute"))
    }

    /** Места на устройстве нет: файловая проверка выгрузки идёт до начала записи (ПЕР-31). */
    @Допустим("^на устройстве нет свободного места$")
    fun theDeviceHasNoFreeSpace() {
        world.transfer.files.freeSpace = NO_SPACE_BYTES
    }

    // --- Действия -----------------------------------------------------------------

    @Когда("^человек собирает расширенный состав отчёта за (.+)$")
    fun thePersonBuildsTheExtendedReportFor(period: String) {
        sections = runBlocking { extendedSections(windowOf(period)) }
    }

    @Когда("^человек выгружает историю за (.+)$")
    fun thePersonExportsTheHistoryFor(period: String) {
        table = runBlocking { csvTable(windowOf(period)) }
    }

    @Когда("^человек сохраняет выгрузку истории$")
    fun thePersonSavesTheHistoryExport() {
        val document = csvDocument(requireNotNull(table) { "the scenario never exported the history" })
        saveOutcome = runBlocking { export().invoke(document, ExportTarget(TARGET)) }
    }

    // --- Проверки: базовый состав отчёта ------------------------------------------

    /** Базовый состав — сводка и разбивка: то же, что на экране, и то же, что было до второго состава (Р-36). */
    @Тогда("^базовый состав несёт сводку и разбивку по лекарствам$")
    fun theBaseCompositionCarriesTheSummaryAndTheBreakdown() {
        val report = requireNotNull(world.history.buildReport(windowOf(SEPTEMBER))) {
            "the scenario never built the report"
        }
        assertTrue(report.medications.isNotEmpty(), "the base report must name the medications of the period")
        assertTrue(report.plannedTotal > 0, "the base report must count the planned intakes of the period")
    }

    // --- Проверки: расширенные разделы ---------------------------------------------

    @Тогда("^документ несёт сравнение периодов$")
    fun theDocumentCarriesTheComparison() {
        assertNotNull(sections.comparison, "the extended report must carry the period comparison")
    }

    @Тогда("^документ несёт динамику приверженности по месяцам$")
    fun theDocumentCarriesTheDynamics() {
        assertNotNull(sections.dynamics, "the extended report must carry the monthly dynamics")
        assertTrue(
            requireNotNull(sections.dynamics).months.size >= MINIMUM_MONTHS,
            "the dynamics is not a dynamics of one month",
        )
    }

    @Тогда("^документ несёт паттерн пропусков$")
    fun theDocumentCarriesThePattern() {
        val pattern = sections.pattern
        assertNotNull(pattern, "the extended report must carry the missed-dose pattern")
        assertTrue(requireNotNull(pattern).misses > 0, "the pattern names the misses of its time")
    }

    /** Раздела без данных не существует: ни пустой таблицы, ни прочерка (Р-33). */
    @Тогда("^в документе нет ни одного пустого раздела$")
    fun theDocumentCarriesNoEmptySection() {
        assertTrue(sections.isEmpty, "an empty section must not be printed at all: $sections")
    }

    @Тогда("^разбивка по лекарствам остаётся в базовом составе$")
    fun theBreakdownStaysInTheBaseComposition() {
        val report = requireNotNull(world.history.buildReport(windowOf(SEPTEMBER))) {
            "the scenario never built the report"
        }
        assertTrue(
            report.medications.isNotEmpty(),
            "the breakdown by medication stays in the base composition and does not move to the extended one (Р-36)",
        )
    }

    // --- Проверки: таблица выгрузки -------------------------------------------------

    @Тогда("^в выгрузке есть записи «([^»]+)» со статусами «([^»]+)» и «([^»]+)»$")
    fun theExportCarriesRecordsOfThatMedicationWithStatuses(medication: String, first: String, second: String) {
        val name = world.medications.get(world.medicationNamed(medication)).name
        val rows = rowsOf(name)
        assertTrue(rows.isNotEmpty(), "the export must carry the records of «$medication»")
        val statuses = rows.map { it.status }.toSet()
        listOf(first, second).forEach { word ->
            val status = statusOf(word)
            assertTrue(
                status in statuses,
                "the export must carry the record «$word» of «$medication»: ${rows.map { it.status }}",
            )
        }
    }

    @Тогда("^«Вовремя» заполнено только у «принято»$")
    fun theOnTimeWordIsFilledOnlyForTaken() {
        val rows = requireNotNull(table) { "the scenario never exported the history" }.rows
        assertTrue(rows.isNotEmpty(), "the export must carry records")
        rows.forEach { row ->
            if (row.status == HistoryCsvStatus.TAKEN) {
                assertNotNull(row.onTime, "a taken intake must carry the on-time word (Р-21)")
            } else {
                assertNull(row.onTime, "only the taken intake has an on-time word (Р-27): ${row.status}")
            }
        }
    }

    @Тогда("^в выгрузке нет ни «([^»]+)», ни «([^»]+)»$")
    fun theExportCarriesNeitherOfThem(first: String, second: String) {
        val rows = requireNotNull(table) { "the scenario never exported the history" }.rows
        assertTrue(rows.isNotEmpty(), "the export of the own profile must carry records")
        val names = setOf(
            world.medications.get(world.medicationNamed(first)).name,
            world.medications.get(world.medicationNamed(second)).name,
        )
        assertTrue(
            rows.none { it.medication in names },
            "the file is of one active profile and shows no deleted medication (ПРФ-23, ПР-14): $names",
        )
    }

    @Тогда("^в выгрузке есть «([^»]+)» со статусом «([^»]+)»$")
    fun theExportCarriesThatMedicationWithStatus(medication: String, word: String) {
        val rows = rowsOf(world.medications.get(world.medicationNamed(medication)).name)
        assertEquals(listOf(statusOf(word)), rows.map { it.status }, "the record must carry the status «$word»")
    }

    @Тогда("^времени плана у этой записи нет$")
    fun theRecordHasNoPlannedTime() {
        val row = requireNotNull(table) { "the scenario never exported the history" }.rows.single {
            it.status == HistoryCsvStatus.TAKEN_ON_DEMAND
        }
        assertNull(row.plannedTime, "an on-demand record has no planned time (ПР-4)")
    }

    // --- Проверки: исходы и журнал ---------------------------------------------------

    @Тогда("^сказано, что места не хватило$")
    fun theNoSpaceOutcomeIsSaidInWords() {
        assertEquals(HistoryCsvSaveOutcome.NotEnoughSpace, saveOutcome)
        assertEquals(
            NO_SPACE_WORDS,
            historyString("history_export_no_space"),
            "the outcome must be said in the words of the norm (§5.16)",
        )
    }

    @Тогда("^файла нет$")
    fun theFileIsNotThere() {
        assertTrue(world.transfer.files.documents.isEmpty(), "a failed export must leave no file")
    }

    /**
     * Журнал срабатываний — другой документ: его файл собирается доменным путём (НФТ-51, §5.16).
     *
     * Проверяется **сам файл**, а не литералы в исходнике: выгрузка идёт настоящим
     * `ExportReminderJournalUseCase` на файловом порте мира, и её содержимое читается здесь же.
     * Поверхность журнала (доступность без оплаты и отсутствие метки «Про») проверяется
     * сценарием на стенде «Проверки напоминаний»: у чистого JVM-модуля нет ни экрана,
     * ни каталогов строк.
     */
    @Когда("^человек выгружает журнал срабатываний файлом$")
    fun thePersonExportsTheReminderJournal() {
        val export = ExportReminderJournalUseCase(
            journal = world.reminders.journal,
            files = world.transfer.files,
            time = world.clock.time,
        )
        val target = ExportTarget(JOURNAL_TARGET)
        runBlocking {
            export(target)
            journalFile = world.transfer.files.documents[target.handle]
        }
        assertNotNull(journalFile, "the journal export must write the file")
    }

    @Тогда("^в файле журнала есть только идентификаторы записей$")
    fun theJournalFileCarriesIdentifiersOnly() {
        val content = requireNotNull(journalFile) { "the scenario never exported the journal" }
        val header = content.lineSequence().first()
        assertTrue(header.contains(ID_COLUMN), "the journal file keeps identifiers, not names (НФТ-19): $header")
        assertTrue(header.contains(MEDICATION_COLUMN), "the journal file keeps the medication identifier")
        val rows = content.lineSequence().drop(1).filter { it.isNotBlank() }.toList()
        assertEquals(
            rows.size,
            rows.count { row -> row.contains("medication-") },
            "every row of the journal must carry the identifiers of the record",
        )
    }

    @Тогда("^в нём нет ни названий лекарств, ни доз, ни отметок «вовремя»$")
    fun theJournalFileCarriesNoNamesNorDoses() {
        val content = requireNotNull(journalFile) { "the scenario never exported the journal" }
        FORBIDDEN_JOURNAL_WORDS.forEach { word ->
            assertFalse(content.contains(word), "the journal file must not carry «$word» (НФТ-19)")
        }
        // Окно «вовремя» журнала — НАП-2 (опоздание будильника), а не окно отметки человека:
        // слова отметки в служебном файле быть не может, и колонка называется `on_time`.
        assertFalse(content.contains("Вовремя"), "the journal must not name the intake window")
    }

    /** В таблице выгрузки назван ровно один профиль — активный (ПРФ-23). */
    @Тогда("^в таблице выгрузки назван ровно один профиль$")
    fun theExportNamesExactlyOneProfile() {
        val table = requireNotNull(table) { "the scenario never exported the history" }
        assertEquals(world.profiles.active, table.profileName, "the file is of the active profile")
        val own = world.medications.all()
            .filter { it.profileId == world.profiles.get(world.profiles.active).id }
            .mapTo(mutableSetOf()) { it.name }
        assertTrue(
            table.rows.all { it.medication in own },
            "every record of the file must belong to the active profile: ${table.rows.map { it.medication }}",
        )
    }

    // --- Обвязка ---    // --- Обвязка ---------------------------------------------------------------------

    /** Окно периода из слов сценария: месяц, день или диапазон («август—сентябрь 2026»). */
    private fun windowOf(period: String): HistoryWindow {
        val parts = period.split("—").map { it.trim() }
        return when {
            // «август—сентябрь 2026»: начало — первое число первого месяца, конец — последнее
            // второго. Год назван один раз, как его назвал бы человек.
            parts.size == 2 && !parts[0].any { it.isDigit() } -> {
                val last = RussianDates.month(parts[1], world.clock.today())
                val first = RussianDates.month("${parts[0]} ${last.year}", world.clock.today())
                HistoryWindow(first.atDay(1), last.atEndOfMonth())
            }
            parts.size == 2 -> {
                val dates = RussianDates.range(parts[0], parts[1], world.clock.today())
                HistoryWindow(dates.first(), dates.last())
            }
            SINGLE_DAY.containsMatchIn(period) -> {
                val dates = RussianDates.range(period, period, world.clock.today())
                HistoryWindow(dates.first(), dates.last())
            }
            else -> HistoryWindow.of(RussianDates.month(period, world.clock.today()))
        }
    }

    private suspend fun extendedSections(window: HistoryWindow): ExtendedReportSections = BuildExtendedSectionsUseCase(
        activeProfile = world.profileSession.activeStore,
        history = world.history,
        calculator = AdherenceCalculator(OnTimeRule.TwoHours),
        missedPattern = MissedPatternRule(),
        time = world.clock.time,
    ).invoke(window)

    private suspend fun csvTable(window: HistoryWindow): HistoryCsvTable = requireNotNull(
        BuildHistoryCsvTableUseCase(
            activeProfile = world.profileSession.activeStore,
            profiles = world.repositories.profileStore,
            history = world.history,
            onTimeRule = OnTimeRule.TwoHours,
            time = world.clock.time,
        ).invoke(window),
    ) { "the active profile must be in the database" }

    /**
     * Готовый файл выгрузки — для проверки **правил** записи.
     *
     * Тип файла берётся у продукта ([HistoryCsvDocument.MEDIA_TYPE]), а слова шапки и колонок
     * здесь условны и это названо вслух: у чистого JVM-модуля нет ни каталогов строк, ни
     * `HistoryCsvComposer` (НФТ-42). Настоящие имя файла, тип, шапку, колонки и слова статусов
     * проверяет экранный прогон `feature-history` (настоящий композер) и устройство
     * (записанный файл) — эти сценарии говорят только о том, какие записи попали в файл.
     */
    private fun csvDocument(table: HistoryCsvTable) = HistoryCsvDocument(
        fileName = "sklyanka-history-2026-09-01.csv",
        mediaType = HistoryCsvDocument.MEDIA_TYPE,
        header = listOf("profile", "period", "disclaimer", "data"),
        columns = COLUMNS,
        rows = table.rows.map { row ->
            listOf(
                row.date.toString(),
                row.plannedTime?.toString().orEmpty(),
                row.medication,
                row.dose.toPlainString(),
                row.strength?.toPlainString().orEmpty(),
                row.status.name,
                row.actedAt?.toString().orEmpty(),
                row.onTime?.toString().orEmpty(),
            )
        },
    )

    private fun export() = ExportHistoryCsvUseCase(
        files = world.transfer.files,
        transfer = RecordingShare(),
        activeProfile = world.profileSession.activeStore,
        log = world.log,
    )

    /** Приёмы лекарства за сентябрь по его расписанию — тем же шагом, что прочие сценарии истории. */
    private fun septemberSlots(name: String): List<java.time.LocalDateTime> = SEPTEMBER_MONTH
        .let { month -> (1..month.lengthOfMonth()).map(month::atDay) }
        .flatMap { date ->
            val times = world.medications.get(name).times.toList().sorted().ifEmpty { listOf(MORNING) }
            times.map { time -> date.atTime(time) }
        }

    /**
     * Осознанный пропуск: отметка человека настоящим `MarkIntakeUseCase`.
     *
     * Природа пропуска сохраняется: отмеченный человеком в выгрузке называется иначе, чем
     * переведённый приложением (ПР-1), и подмена статуса записью в обход домена стёрла бы
     * ровно то, что проверяет сценарий.
     */
    private fun skipAsPerson(name: String, moment: java.time.LocalDateTime) {
        val intake = world.intakes.pending(name, moment)
        world.mark(intake.id, MarkResult.SKIPPED, IntakeSource.PHONE, world.clock.momentOf(moment))
    }

    private fun rowsOf(modelName: String) = requireNotNull(table) { "the scenario never exported the history" }
        .rows
        .filter { it.medication == modelName }

    /** Слово статуса из каталога `feature-history`: им проверяется, что ключ несёт слова (П-7). */
    private fun statusOf(word: String): HistoryCsvStatus {
        val status = requireNotNull(STATUS_KEYS.entries.firstOrNull { it.value == word }) {
            "the scenario names no status «$word» (known: ${STATUS_KEYS.values})"
        }.key
        assertEquals(word, historyString(STATUS_RESOURCES.getValue(status)), "the status must be said in these words")
        return status
    }

    private fun historyString(key: String): String = requireNotNull(historyCatalog()[key]) {
        "the catalogue of feature-history must carry $key"
    }

    private fun historyCatalog(): Map<String, String> = CATALOG

    private companion object {

        const val SEPTEMBER = "сентябрь 2026"

        /** Месяц сценариев истории: сентябрь 2026 — тот же, о котором говорит Предыстория. */
        val SEPTEMBER_MONTH: java.time.YearMonth = java.time.YearMonth.of(2026, 9)

        const val TARGET = "content://target/history.csv"

        const val NO_SPACE_BYTES = 1L

        const val JOURNAL_TARGET = "content://target/reminder-journal.csv"

        const val ID_COLUMN = "intake_id"
        const val MEDICATION_COLUMN = "medication_id"

        /** Слова, которых служебный файл не несёт: он не пользовательский документ (НФТ-19). */
        val FORBIDDEN_JOURNAL_WORDS = listOf("Лекарство", "Доза", "Время плана")

        /** Слова исхода нехватки места — из нормы дизайна (§5.16). */
        const val NO_SPACE_WORDS = "Места недостаточно. Файл не сохранён"

        const val MINIMUM_MONTHS = 2

        val MORNING: LocalTime = LocalTime.of(8, 0)

        /** Признак одного дня в словах сценария: «12 октября 2026». */
        val SINGLE_DAY = Regex("^\\d{1,2} ")

        /** Колонки таблицы в сценарном прогоне: слова проверяет экранный прогон (`@screen`). */
        val COLUMNS = listOf("date", "planned_time", "medication", "dose", "strength", "status", "acted_at", "on_time")

        /** Слово сценария → исход домена. Слова — из каталога `feature-history` (§10 нормы дизайна). */
        val STATUS_KEYS: Map<HistoryCsvStatus, String> = mapOf(
            HistoryCsvStatus.TAKEN to "принято",
            HistoryCsvStatus.TAKEN_ON_DEMAND to "принято по потребности",
            HistoryCsvStatus.SKIPPED_BY_PERSON to "пропущено — отмечено человеком",
            HistoryCsvStatus.MISSED_WITHOUT_MARK to "пропущено — без отметки",
        )

        val STATUS_RESOURCES: Map<HistoryCsvStatus, String> = mapOf(
            HistoryCsvStatus.TAKEN to "history_export_status_taken",
            HistoryCsvStatus.TAKEN_ON_DEMAND to "history_export_status_on_demand",
            HistoryCsvStatus.SKIPPED_BY_PERSON to "history_export_status_skipped",
            HistoryCsvStatus.MISSED_WITHOUT_MARK to "history_export_status_missed",
        )

        /** Русский каталог `feature-history`: слова, о которых говорит сценарий (§10 нормы дизайна). */
        val CATALOG: Map<String, String> by lazy {
            val file = File(RepositoryLayout.root, "feature-history/src/main/res/values/strings.xml")
            Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
                .findAll(file.readText())
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
        }
    }
}

/**
 * Системная передача в сценарном прогоне домена.
 *
 * Обмен и выбор места — платформенные механизмы, и в чистом JVM-модуле их нет: сценарий здесь
 * говорит о правилах выгрузки (что и каким исходом записано), а передача файла проверяется
 * экранным прогоном и устройством.
 */
private class RecordingShare : DoctorReportTransfer {

    override suspend fun share(document: ReportDocument, launcher: ReportShareLauncher): ReportTransferOutcome =
        ReportTransferOutcome.Shared

    override suspend fun save(document: ReportDocument, target: ReportTarget): ReportTransferOutcome =
        ReportTransferOutcome.Saved
}

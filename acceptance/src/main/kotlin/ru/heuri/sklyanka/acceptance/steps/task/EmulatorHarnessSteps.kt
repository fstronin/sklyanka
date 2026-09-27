package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Шаги приёмки SKL-27 — структурная часть.
 *
 * Эти сценарии говорят не о поведении приложения, а об устройстве оснастки прогона:
 * какие образы в матрице, из чего состоит оснастка, что сказано о каждом испытании
 * выживаемости, какие числа отдаёт разбор результатов. Проверять это чтением репозитория —
 * ровно то, что сделает человек, севший за машину с эмулятором, только автоматически.
 *
 * Часть приёмки задачи требует запущенного эмулятора и помечена `@manual @device`:
 * в среде сборки эмулятора нет, и её шагов здесь нет и быть не может.
 *
 * Общее состояние сценария этим шагам не нужно: они говорят об устройстве репозитория.
 */
@Suppress("TooManyFunctions", "LargeClass")
class EmulatorHarnessSteps {

    private var matrix: String = ""
    private var documents: List<String> = emptyList()
    private var harness: List<File> = emptyList()
    private var syntaxFailures: List<String> = emptyList()
    private var trials: List<Trial> = emptyList()
    private var analysis: String = ""
    private var runbook: String = ""
    private var deviceTestFiles: List<File> = emptyList()
    private var uncovered: List<String> = emptyList()

    /** Строка разбора испытания из таблицы `SKL_TRIALS` скрипта испытаний. */
    private data class Trial(val code: String, val name: String, val verdict: String, val how: String)

    // --- Матрица образов -------------------------------------------------------

    @Когда("разработчик читает матрицу образов эмулятора")
    fun theDeveloperReadsTheImageMatrix() {
        matrix = configFile().readText()
        assertTrue(matrix.contains(API_LEVELS), "config.sh must declare the image matrix")
    }

    @Тогда("в матрице есть образ минимальной поддерживаемой версии")
    fun theMatrixHoldsTheMinimumVersion() = assertMatrixHolds(projectDefault(MIN_SDK))

    @Тогда("в матрице есть образ целевой версии")
    fun theMatrixHoldsTheTargetVersion() = assertMatrixHolds(projectDefault(TARGET_SDK))

    @Тогда("в матрице есть образ границы, с которой существует ускоренная фоновая задача")
    fun theMatrixHoldsTheExpeditedBoundary() {
        // Граница задана самим продуктом: expedited ставится только на Android 12+.
        val reminderWork = File(RepositoryLayout.root, REMINDER_WORK).readText()
        assertTrue(reminderWork.contains(EXPEDITED), "$REMINDER_WORK must be the source of the boundary")
        assertMatrixHolds(EXPEDITED_BOUNDARY_API)
    }

    @Тогда("у каждого образа матрицы названо, что именно он проверяет")
    fun everyImageStatesWhatItChecks() {
        val purposes = section(matrix, PURPOSE_FUNCTION)
        val silent = apiLevels().filterNot { purposes.contains("$it)") }
        assertEquals(emptyList<String>(), silent, "these API levels stand in the matrix without a stated purpose")
    }

    @Тогда("ни один образ не содержит Google APIs и Play Services")
    fun noImageCarriesGoogleServices() {
        val images = section(matrix, CANDIDATES_FUNCTION)
        val withGoogle = images.lines().filter { it.contains(SYSTEM_IMAGE) && it.contains(GOOGLE, ignoreCase = true) }
        assertEquals(emptyList<String>(), withGoogle, "an image with Play Services would check another product")
        assertTrue(
            harnessFiles().single { it.name == AVD_SCRIPT }.readText().contains(GOOGLE),
            "$AVD_SCRIPT must refuse an image with Google services, not only avoid naming one",
        )
    }

    // --- Граница прогона -------------------------------------------------------

    @Когда("разработчик читает документы прогона на эмуляторе")
    fun theDeveloperReadsTheRunDocuments() {
        documents = listOf(File(RepositoryLayout.root, RUNBOOK), File(RepositoryLayout.root, TECH_DESIGN))
            .map { it.readText() }
        assertEquals(2, documents.size, "both the runbook and the technical design must exist")
    }

    @Тогда("в них прямо сказано, что матрица НФТ-40 эмулятором не покрывается")
    fun theDocumentsStateTheDeviceMatrixIsNotCovered() {
        documents.forEach { text ->
            assertTrue(text.contains(DEVICE_MATRIX), "the device matrix requirement is not mentioned")
            assertTrue(NOT_COVERED.containsMatchIn(text), "the documents must say the device matrix stays uncovered")
        }
    }

    @Тогда("в них прямо сказано, что гейт НАП-3 остаётся за живым устройством Huawei")
    fun theDocumentsStateTheGateStaysOnDevice() {
        documents.forEach { text ->
            assertTrue(text.contains(GATE), "the gate requirement is not mentioned")
            assertTrue(text.contains(HUAWEI), "the documents must name the live reference device")
        }
    }

    // --- Оснастка --------------------------------------------------------------

    @Когда("разработчик читает состав оснастки прогона")
    fun theDeveloperReadsTheHarness() {
        harness = harnessFiles()
        assertTrue(harness.isNotEmpty(), "the harness must exist in $HARNESS_DIRECTORY")
    }

    @Тогда("в ней есть шаг создания образа")
    fun theHarnessCreatesTheImage() = assertHarnessStep(AVD_SCRIPT, "avdmanager")

    @Тогда("в ней есть шаг запуска без окна и ожидания готовности")
    fun theHarnessStartsHeadlessAndWaits() {
        assertHarnessStep(START_SCRIPT, "skl_wait_boot")
        assertTrue(matrixText().contains(NO_WINDOW), "the launch flags must start the emulator without a window")
    }

    @Тогда("в ней есть шаг установки сборки")
    fun theHarnessInstallsTheBuild() = assertHarnessStep(INSTALL_SCRIPT, "install -r")

    @Тогда("в ней есть шаг прогона проверок")
    fun theHarnessRunsTheChecks() = assertHarnessStep(TESTS_SCRIPT, "SKL_TEST_TASK")

    @Тогда("в ней есть шаг снятия результатов")
    fun theHarnessCollectsTheResults() = assertHarnessStep(COLLECT_SCRIPT, ANALYZE_SCRIPT)

    @Тогда("в ней есть шаг гашения эмулятора")
    fun theHarnessStopsTheEmulator() = assertHarnessStep(STOP_SCRIPT, "emu kill")

    @Тогда("каждый скрипт оснастки берёт параметры из общего файла настроек")
    fun everyScriptReadsTheSharedSettings() {
        val detached = harness
            .filter { it.name.endsWith(SHELL_SUFFIX) }
            .filterNot { it.name == CONFIG_SCRIPT || it.name == LIBRARY_SCRIPT }
            .filterNot { it.readText().contains(CONFIG_SCRIPT) }
            .map { it.name }
        assertEquals(emptyList<String>(), detached, "these scripts carry their own parameters instead of config.sh")
    }

    @Тогда("уровни API не повторены ни в одном другом скрипте")
    fun theApiLevelsLiveInOnePlace() {
        val repeated = harness
            .filterNot { it.name == CONFIG_SCRIPT }
            .filter { file ->
                file.readText().let { it.contains(SYSTEM_IMAGE) || API_LEVELS_LINE.containsMatchIn(it) }
            }
            .map { it.name }
        assertEquals(emptyList<String>(), repeated, "these scripts declare image versions of their own")
    }

    @Тогда("создание образа проверяет, существует ли он уже")
    fun creatingTheImageChecksForIt() = assertHarnessStep(AVD_SCRIPT, "skl_avd_exists")

    @Тогда("запуск эмулятора проверяет, не запущен ли он уже")
    fun startingTheEmulatorChecksForIt() = assertHarnessStep(START_SCRIPT, "skl_avd_running")

    @Когда("разработчик проверяет синтаксис скриптов оснастки")
    fun theDeveloperChecksTheShellSyntax() {
        syntaxFailures = harnessFiles()
            .filter { it.name.endsWith(SHELL_SUFFIX) }
            .filterNot { shellSyntaxIsValid(it) }
            .map { it.name }
    }

    @Тогда("ни один скрипт не содержит синтаксической ошибки")
    fun noScriptIsBroken() = assertEquals(emptyList<String>(), syntaxFailures, "these scripts do not parse as shell")

    @Тогда("прогон проверок устройства идёт задачей connectedDebugAndroidTest")
    fun theDeviceChecksRunAsAGradleTask() {
        assertTrue(
            configFile().readText().contains(CONNECTED_TASK),
            "the harness must call the standard AGP task, not am instrument by hand",
        )
    }

    // --- Испытания И-1 … И-16 --------------------------------------------------

    @Когда("разработчик читает разбор испытаний выживаемости")
    fun theDeveloperReadsTheTrialBreakdown() {
        trials = readTrials()
        assertEquals(TRIAL_COUNT, trials.size, "the breakdown must cover every survival trial")
    }

    @Тогда("у каждого из шестнадцати испытаний названо, автоматизируется оно или нет")
    fun everyTrialStatesItsAutomation() {
        val silent = trials.filter { it.verdict.isBlank() }.map { it.code }
        assertEquals(emptyList<String>(), silent, "these trials stay without a verdict")
        val expected = (1..TRIAL_COUNT).map { "$TRIAL_PREFIX$it" }
        assertEquals(expected, trials.map { it.code }, "the breakdown must list the trials in order")
    }

    @Тогда("у автоматизируемого испытания названа команда, которой оно воспроизводится")
    fun everyAutomatedTrialNamesItsCommand() {
        val nameless = trials
            .filter { it.verdict.startsWith(AUTOMATED) }
            .filterNot { trial -> COMMANDS.any { trial.how.contains(it) } }
            .map { it.code }
        assertEquals(emptyList<String>(), nameless, "these trials claim automation without naming the command")
    }

    @Тогда("испытания И-12 и И-13 названы невоспроизводимыми на эмуляторе")
    fun theFirmwareTrialsAreDeclaredImpossible() {
        listOf(EMUI_POWER_TRIAL, EMUI_EXTREME_TRIAL).forEach { code ->
            val trial = trials.single { it.code == code }
            assertTrue(trial.verdict.contains(IMPOSSIBLE), "$code must be declared impossible, not merely hard")
        }
    }

    @Тогда("причина названа: это прошивка Huawei, а не Android")
    fun theReasonIsTheFirmware() {
        listOf(EMUI_POWER_TRIAL, EMUI_EXTREME_TRIAL).forEach { code ->
            val trial = trials.single { it.code == code }
            assertTrue(trial.how.contains(HUAWEI) || trial.how.contains(EMUI), "$code must name the firmware")
        }
    }

    @Тогда("испытание И-16 предложено ускоренным прогоном")
    fun theLongRunIsOfferedAccelerated() {
        val trial = trials.single { it.code == LONG_RUN_TRIAL }
        assertTrue(trial.how.contains(ACCELERATED), "the long run must be offered as an accelerated run")
    }

    @Тогда("сказано, что ускоренный прогон не заменяет гейт НАП-3")
    fun theAcceleratedRunIsNotTheGate() {
        val trial = trials.single { it.code == LONG_RUN_TRIAL }
        assertTrue(trial.how.contains(GATE), "the long run must name the gate it does not close")
        assertTrue(NOT_CLOSED.containsMatchIn(trial.how), "the accelerated run must be denied the gate explicitly")
    }

    // --- Инструментальные проверки ---------------------------------------------

    @Допустим("проверки устройства лежат в каталоге androidTest")
    fun theDeviceChecksLiveInAndroidTest() {
        deviceTestFiles = deviceTestSources()
        assertTrue(deviceTestFiles.isNotEmpty(), "src/androidTest must hold the device checks")
    }

    @Когда("разработчик сверяет состав прогона проверок устройства с файлами проверок")
    fun theDeveloperComparesGroupsWithFiles() {
        val configuration = configFile().readText()
        val packages = GROUP_PACKAGES.flatMap { key -> valueOf(configuration, key).split(COMMA) }
        val classes = GROUP_CLASSES.flatMap { key -> valueOf(configuration, key).split(COMMA) }
        uncovered = deviceTestFiles
            .filter { countTests(it) > 0 }
            .filterNot { file ->
                val qualified = qualifiedName(file)
                classes.contains(qualified) || packages.any { it.isNotBlank() && qualified.startsWith("$it.") }
            }
            .map { it.name }
    }

    @Тогда("каждый файл проверок попадает хотя бы в одну группу прогона")
    fun everyCheckFileIsInAGroup() =
        assertEquals(emptyList<String>(), uncovered, "these device checks fall out of every run group")

    @Тогда("группы прогона разделяют быстрые проверки, объёмные и миграции")
    fun theGroupsSeparateFastHeavyAndMigrations() {
        val configuration = configFile().readText()
        (GROUP_PACKAGES + GROUP_CLASSES).forEach { key ->
            assertTrue(valueOf(configuration, key).isNotBlank(), "run group $key is empty")
        }
        assertTrue(
            harnessFiles().single { it.name == TESTS_SCRIPT }.readText().contains(MIGRATION_GROUP),
            "$TESTS_SCRIPT must offer the migration group as a separate run",
        )
    }

    // --- Результаты ------------------------------------------------------------

    @Когда("разработчик читает разбор результатов прогона")
    fun theDeveloperReadsTheAnalysis() {
        analysis = File(RepositoryLayout.root, "$HARNESS_DIRECTORY/$ANALYZE_SCRIPT").readText()
        assertTrue(analysis.isNotBlank(), "$ANALYZE_SCRIPT must exist")
    }

    @Тогда("в результате есть отклонение по каждому напоминанию")
    fun theResultHoldsEveryDeviation() {
        assertTrue(analysis.contains(DEVIATION_FIELD), "the result must carry the deviation of every reminder")
        assertTrue(analysis.contains(SCHEDULED_FIELD), "the deviation is counted from the scheduled moment")
    }

    @Тогда("в результате есть доля срабатываний вовремя")
    fun theResultHoldsTheOnTimeShare() {
        assertTrue(analysis.contains(SHARE_FIELD), "the result must carry the on-time share")
        assertTrue(analysis.contains(ON_TIME_LIMIT), "the on-time window of НАП-2 must be stated as a number")
    }

    @Тогда("в результате есть число напоминаний, исключённых за перерыв прогона")
    fun theResultHoldsTheExcludedCount() =
        assertTrue(analysis.contains(EXCLUDED_FIELD), "reminders lost to an outage must be counted separately")

    @Тогда("в результате есть число тихих отказов показа")
    fun theResultHoldsTheSilentFailures() =
        assertTrue(analysis.contains(SILENT_FIELD), "a fired alarm without a shown notification must be counted")

    @Тогда("результат сохраняется машинно читаемым файлом")
    fun theResultIsMachineReadable() =
        assertTrue(analysis.contains(RESULTS_JSON), "the run must produce a machine readable result")

    @Тогда("рядом сохраняется человеческая сводка")
    fun theResultHasAHumanSummary() =
        assertTrue(analysis.contains(SUMMARY_TEXT), "the run must produce a human summary next to the data")

    @Тогда("перерыв прогона определяется по записям журнала, а не на глаз")
    fun theOutageIsFoundInTheJournal() {
        assertTrue(analysis.contains(OUTAGE_FUNCTION), "the outage windows must be computed, not eyeballed")
        assertTrue(analysis.contains(BOOT_REASON), "device boot is the boundary of an outage window")
    }

    @Тогда("напоминания, пришедшиеся на перерыв, исключаются из знаменателя доли отдельным числом")
    fun theOutageRemindersLeaveTheDenominator() {
        assertTrue(analysis.contains(DURING_OUTAGE_FIELD), "every reminder must say whether it fell into an outage")
        assertTrue(analysis.contains(EXCLUDED_FIELD), "the excluded reminders must be counted")
    }

    @Тогда("разбор не читает из журнала ни названий лекарств, ни дозировок")
    fun theAnalysisReadsNoMedicalData() {
        val columns = section(analysis, COLUMNS_DECLARATION)
        val medical = MEDICAL_FIELDS.filter { columns.contains(it) }
        assertEquals(emptyList<String>(), medical, "these medical fields must never reach the run results")
        assertTrue(analysis.contains(EXPLICIT_COLUMNS), "the analysis must name its columns instead of selecting all")
    }

    // --- Порядок запуска -------------------------------------------------------

    @Когда("разработчик открывает порядок запуска прогона")
    fun theDeveloperOpensTheRunbook() {
        runbook = File(RepositoryLayout.root, RUNBOOK).readText()
        assertTrue(runbook.isNotBlank(), "$RUNBOOK must exist")
    }

    @Тогда("в нём сказано, что поставить на новой машине")
    fun theRunbookNamesWhatToInstall() {
        assertTrue(runbook.contains(WHAT_TO_INSTALL), "the runbook must say what to install")
        assertTrue(runbook.contains(ACCELERATION), "hardware acceleration is the first thing to check")
    }

    @Тогда("в нём названа команда полного прогона")
    fun theRunbookNamesTheFullRun() =
        assertTrue(runbook.contains(RUN_ALL_SCRIPT), "the runbook must name the full run command")

    @Тогда("в нём названа команда быстрого прогона")
    fun theRunbookNamesTheFastRun() =
        assertTrue(runbook.contains(FAST_FLAG), "the runbook must name the fast run command")

    @Тогда("в нём сказано, где смотреть результаты")
    fun theRunbookNamesWhereResultsAre() {
        assertTrue(runbook.contains(RESULTS_JSON), "the runbook must point at the machine readable result")
        assertTrue(runbook.contains(SUMMARY_TEXT), "the runbook must point at the human summary")
    }

    @Тогда("в нём перечислены типовые отказы прогона")
    fun theRunbookListsTypicalFailures() {
        assertTrue(runbook.contains(FAILURES_SECTION), "the runbook must list the typical failures")
        assertTrue(
            runbook.lines().count { it.startsWith(TABLE_ROW) } > FAILURE_ROWS,
            "a single line about failures is not a list",
        )
    }

    // --- Чтение репозитория ----------------------------------------------------

    private fun assertMatrixHolds(api: String) {
        assertTrue(apiLevels().contains(api), "the image matrix must hold API $api")
    }

    private fun matrixText(): String = configFile().readText()

    private fun configFile(): File = File(RepositoryLayout.root, "$HARNESS_DIRECTORY/$CONFIG_SCRIPT")

    private fun harnessFiles(): List<File> = File(RepositoryLayout.root, HARNESS_DIRECTORY).listFiles()
        ?.filter { it.isFile && (it.name.endsWith(SHELL_SUFFIX) || it.name.endsWith(PYTHON_SUFFIX)) }
        ?.sortedBy { it.name }
        .orEmpty()

    private fun assertHarnessStep(script: String, marker: String) {
        val file = harnessFiles().singleOrNull { it.name == script } ?: error("the harness has no $script")
        assertTrue(file.readText().contains(marker), "$script does not do its step: $marker is missing")
    }

    /** Уровни API матрицы: строка `SKL_API_LEVELS=(...)` из config.sh. */
    private fun apiLevels(): List<String> {
        val declaration = API_LEVELS_LINE.find(matrixText())?.groupValues?.get(1).orEmpty()
        return declaration.trim().split(WHITESPACE).filter { it.isNotBlank() }
    }

    /** Значение из ProjectDefaults: minSdk и targetSdk задаёт сборка, а не эта проверка. */
    private fun projectDefault(name: String): String {
        val text = File(RepositoryLayout.root, PROJECT_DEFAULTS).readText()
        return Regex("""$name\s*=\s*(\d+)""").find(text)?.groupValues?.get(1)
            ?: error("$name is not declared in $PROJECT_DEFAULTS")
    }

    /** Тело функции или объявления от заголовка до пустой строки, разделяющей блоки. */
    private fun section(text: String, header: String): String {
        val start = text.indexOf(header)
        if (start < 0) return ""
        val rest = text.substring(start)
        val end = rest.indexOf(SECTION_BREAK)
        return if (end < 0) rest else rest.substring(0, end)
    }

    private fun valueOf(text: String, key: String): String =
        Regex("""$key="([^"]*)"""").find(text)?.groupValues?.get(1).orEmpty()

    private fun readTrials(): List<Trial> {
        val script = File(RepositoryLayout.root, "$HARNESS_DIRECTORY/$TRIALS_SCRIPT").readText()
        return section(script, TRIALS_DECLARATION).lines()
            .mapNotNull { TRIAL_ROW.find(it)?.groupValues }
            .map { Trial(code = it[1], name = it[2], verdict = it[3], how = it[4]) }
    }

    private fun deviceTestSources(): List<File> = RepositoryLayout.root.walkTopDown()
        .filter { it.isFile && it.extension == KOTLIN }
        .filter { it.path.contains(ANDROID_TEST_PATH) && !it.path.contains(BUILD_DIRECTORY) }
        .sortedBy { it.name }
        .toList()

    private fun countTests(file: File): Int = TEST_ANNOTATION.findAll(file.readText()).count()

    private fun qualifiedName(file: File): String {
        val declared = PACKAGE_DECLARATION.find(file.readText())?.groupValues?.get(1).orEmpty()
        return "$declared.${file.nameWithoutExtension}"
    }

    private fun shellSyntaxIsValid(file: File): Boolean {
        val process = ProcessBuilder(BASH, SYNTAX_ONLY, file.absolutePath)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText()
        return process.waitFor() == 0
    }

    private companion object {
        const val HARNESS_DIRECTORY = "tools/emulator"
        const val RUNBOOK = "$HARNESS_DIRECTORY/README.md"
        const val TECH_DESIGN = "docs/tasks/SKL-27/TDD.md"
        const val PROJECT_DEFAULTS =
            "build-logic/convention/src/main/kotlin/ru/heuri/sklyanka/gradle/ProjectDefaults.kt"
        const val REMINDER_WORK = "app/src/main/kotlin/ru/heuri/sklyanka/reminder/ReminderWork.kt"

        const val CONFIG_SCRIPT = "config.sh"
        const val LIBRARY_SCRIPT = "lib.sh"
        const val AVD_SCRIPT = "avd.sh"
        const val START_SCRIPT = "start.sh"
        const val STOP_SCRIPT = "stop.sh"
        const val INSTALL_SCRIPT = "install.sh"
        const val TESTS_SCRIPT = "run-tests.sh"
        const val COLLECT_SCRIPT = "collect.sh"
        const val TRIALS_SCRIPT = "trials.sh"
        const val ANALYZE_SCRIPT = "analyze.py"
        const val RUN_ALL_SCRIPT = "run-all.sh"
        const val SHELL_SUFFIX = ".sh"
        const val PYTHON_SUFFIX = ".py"

        const val API_LEVELS = "SKL_API_LEVELS"
        const val PURPOSE_FUNCTION = "skl_image_purpose"
        const val CANDIDATES_FUNCTION = "skl_image_candidates"
        const val SYSTEM_IMAGE = "system-images"
        const val GOOGLE = "google"
        const val NO_WINDOW = "-no-window"
        const val CONNECTED_TASK = "connectedDebugAndroidTest"
        const val MIGRATION_GROUP = "migration"
        const val EXPEDITED = "expedited"

        /** Граница, с которой ускоренная фоновая задача существует: Android 12. */
        const val EXPEDITED_BOUNDARY_API = "31"
        const val MIN_SDK = "MIN_SDK"
        const val TARGET_SDK = "TARGET_SDK"

        const val DEVICE_MATRIX = "НФТ-40"
        const val GATE = "НАП-3"
        const val HUAWEI = "Huawei"
        const val EMUI = "EMUI"

        const val TRIAL_COUNT = 16
        const val TRIAL_PREFIX = "И-"
        const val TRIALS_DECLARATION = "SKL_TRIALS=("
        const val AUTOMATED = "автоматизируется"
        const val IMPOSSIBLE = "НЕ ВОСПРОИЗВОДИТСЯ"
        const val ACCELERATED = "ускоренный"
        const val EMUI_POWER_TRIAL = "И-12"
        const val EMUI_EXTREME_TRIAL = "И-13"
        const val LONG_RUN_TRIAL = "И-16"

        /** Чем испытание воспроизводится: команда отладочного канала, а не описание. */
        val COMMANDS = listOf("adb", "dumpsys", "am ", "pm ", "cmd ", "appops", "settings", "install", "seed.sh")

        const val DEVIATION_FIELD = "deviation_seconds"
        const val SCHEDULED_FIELD = "scheduled_at"
        const val SHARE_FIELD = "on_time_share"
        const val ON_TIME_LIMIT = "ON_TIME_TO = 120"
        const val EXCLUDED_FIELD = "excluded_for_outage"
        const val SILENT_FIELD = "silent_failure"
        const val RESULTS_JSON = "results.json"
        const val SUMMARY_TEXT = "summary.txt"
        const val OUTAGE_FUNCTION = "outage_windows"
        const val DURING_OUTAGE_FIELD = "during_outage"
        const val BOOT_REASON = "\"boot\""
        const val COLUMNS_DECLARATION = "COLUMNS = ("
        const val EXPLICIT_COLUMNS = "SELECT {columns}"
        val MEDICAL_FIELDS = listOf("\"name\"", "\"dose", "\"strength")

        const val WHAT_TO_INSTALL = "Что поставить"
        const val ACCELERATION = "Аппаратное ускорение"
        const val FAST_FLAG = "--fast"
        const val FAILURES_SECTION = "Типовые отказы"
        const val TABLE_ROW = "| "
        const val FAILURE_ROWS = 8

        const val ANDROID_TEST_PATH = "/src/androidTest/"
        const val BUILD_DIRECTORY = "/build/"
        const val KOTLIN = "kt"
        const val COMMA = ","
        const val SECTION_BREAK = "\n\n"
        const val BASH = "bash"
        const val SYNTAX_ONLY = "-n"

        val GROUP_PACKAGES = listOf("SKL_GROUP_FAST_PACKAGES", "SKL_GROUP_DATA_PACKAGES")
        val GROUP_CLASSES = listOf("SKL_GROUP_HEAVY_CLASSES", "SKL_GROUP_MIGRATION_CLASSES")

        val API_LEVELS_LINE = Regex("""SKL_API_LEVELS=\(([^)]*)\)""")
        val TRIAL_ROW = Regex(""""(И-\d+)\|([^|]*)\|([^|]*)\|([^"]*)"""")
        val TEST_ANNOTATION = Regex("""(?m)^\s*@Test\b""")
        val PACKAGE_DECLARATION = Regex("""(?m)^package\s+([\w.]+)""")
        val WHITESPACE = Regex("""\s+""")
        val NOT_COVERED = Regex("не покрыва\\w*")
        val NOT_CLOSED = Regex("[Нн][Ее] закрыва\\w*|НЕ закрывается")
    }
}

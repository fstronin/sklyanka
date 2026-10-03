package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Шаги приёмки SKL-97 — структурная часть.
 *
 * Сценарии без `@manual @device` читают репозиторий: тестовое приложение и подмены графа,
 * изоляцию и управляемое время, ручной запуск Activity, проход согласия, состав сценариев,
 * журнал и сводку прогона, обновлённые приёмочные правила и границы продуктового кода.
 * Поведенческие сценарии (N/B/C/D/F/H и чек-лист) помечены `@manual @device` и шагов
 * здесь не имеют: в среде сборки эмулятора нет.
 *
 * Проверять это чтением файлов — ровно то, что сделает человек за ревью, только
 * автоматически; утверждения читают реальные исходники и скрипты, а не пересказ.
 */
@Suppress("TooManyFunctions", "LargeClass")
class DeviceTestHarnessSteps {

    private var appChecks: List<File> = emptyList()
    private var catalog: String = ""
    private var hiltConvention: String = ""
    private var appConvention: String = ""
    private var appBuild: String = ""
    private var storesText: String = ""
    private var baseText: String = ""
    private var graphText: String = ""
    private var modulesText: String = ""
    private var clockText: String = ""
    private var guardText: String = ""
    private var activitiesText: String = ""
    private var firstLaunchText: String = ""
    private var runUi: String = ""
    private var runTests: String = ""
    private var runAll: String = ""
    private var start: String = ""
    private var config: String = ""
    private var runbook: String = ""

    // --- Предыстория -----------------------------------------------------------

    @Допустим("оснастка экранного прогона описана задачей SKL-96")
    fun theScreenRunHarnessIsDescribedBySkl96() {
        assertTrue(
            File(RepositoryLayout.root, SKL96_DESIGN).isFile,
            "the SKL-96 design must exist: $SKL96_DESIGN",
        )
        assertTrue(File(RepositoryLayout.root, RUN_UI).isFile, "$RUN_UI must exist")
    }

    @Допустим("экран согласия первым экраном первого запуска описан задачей SKL-82")
    fun theConsentScreenIsDescribedBySkl82() {
        assertTrue(
            File(RepositoryLayout.root, SKL82_DESIGN).isFile,
            "the SKL-82 design must exist: $SKL82_DESIGN",
        )
        assertTrue(
            text(File(RepositoryLayout.root, MAIN_ACTIVITY)).contains("ConsentRoute"),
            "the launch route must know the consent screen ($MAIN_ACTIVITY)",
        )
    }

    // --- Тестовое приложение и граф --------------------------------------------

    @Когда("разработчик читает устройство тестового графа экранного прогона")
    fun theDeveloperReadsTheDeviceTestGraph() {
        appChecks = appCheckFiles()
        assertTrue(appChecks.isNotEmpty(), "$APP_ANDROID_TEST must hold the device checks")
        catalog = text(File(RepositoryLayout.root, VERSION_CATALOG))
        hiltConvention = text(File(RepositoryLayout.root, HILT_CONVENTION))
        appConvention = text(File(RepositoryLayout.root, APP_CONVENTION))
        appBuild = text(RepositoryLayout.moduleBuildFile(APP_MODULE))
    }

    @Тогда("тестовый контур Hilt объявлен каталогом версий и общей конвенцией")
    fun theHiltStandIsDeclaredByCatalogAndConvention() {
        assertTrue(catalog.contains(HILT_ANDROID_TESTING), "the catalog must declare $HILT_ANDROID_TESTING")
        assertTrue(
            hiltConvention.contains(HILT_ANDROID_TESTING),
            "the Hilt convention must put $HILT_ANDROID_TESTING on the androidTest classpath",
        )
        assertTrue(
            hiltConvention.contains(KSP_ANDROID_TEST) && hiltConvention.contains(HILT_COMPILER),
            "the Hilt convention must process test components: $KSP_ANDROID_TEST($HILT_COMPILER)",
        )
        assertTrue(
            appConvention.contains("room-runtime") && appConvention.contains("datastore-preferences"),
            "the application convention must give androidTest the Room and DataStore types",
        )
    }

    @Тогда("тестовое приложение объявляется аннотацией, а не подменой продуктового")
    fun theTestApplicationIsDeclaredByAnnotation() {
        assertTrue(
            appChecks.any { text(it).contains(CUSTOM_TEST_APPLICATION) },
            "a test application must be declared by $CUSTOM_TEST_APPLICATION",
        )
        assertFalse(
            text(File(RepositoryLayout.root, SKLYANKA_APP)).contains(CUSTOM_TEST_APPLICATION),
            "the product application must not be a test application",
        )
    }

    @Тогда("подмены тестового графа заменяют базу, часы и аналитику")
    fun theGraphReplacementsCoverStorageTimeAndAnalytics() {
        modulesText = text(appChecks.single { it.name == "DeviceTestModules.kt" })
        listOf("DataModule", "DirectBootModule", "TimeModule", "AnalyticsModule").forEach { replaced ->
            assertTrue(
                Regex("""replaces\s*=\s*\[[^\]]*${Regex.escape(replaced)}::class""").containsMatchIn(modulesText),
                "the test graph must replace $replaced",
            )
        }
    }

    @Тогда("приложение под проверкой запускается тем же тестовым приложением")
    fun theUnderTestApplicationIsTheTestApplication() {
        val runner = text(appChecks.single { it.name == "DeviceTestRunner.kt" })
        assertTrue(
            runner.contains("DeviceTestApplicationSpec_Application"),
            "the runner must start the generated test application",
        )
        assertTrue(appBuild.contains("DeviceTestRunner"), "$APP_MODULE/build.gradle.kts must name the test runner")
    }

    @Тогда("продуктовое приложение не содержит веток «только для проверок»")
    fun theProductApplicationHasNoTestOnlyBranches() {
        val offenders = mainAppFiles()
            .filter { file ->
                val body = text(file)
                TEST_ONLY_MARKERS.any { body.contains(it) }
            }
            .map { it.name }
        assertEquals(emptyList<String>(), offenders, "the product sources must not know the test stand")
    }

    // --- Изоляция ---------------------------------------------------------------

    @Когда("разработчик читает правила изоляции проверок устройства")
    fun theDeveloperReadsTheIsolationRules() {
        appChecks = appCheckFiles()
        storesText = text(appChecks.single { it.name == "DeviceTestStores.kt" })
        baseText = text(appChecks.single { it.name == "DeviceScreenTest.kt" })
        graphText = text(appChecks.single { it.name == "DeviceTestGraph.kt" })
    }

    @Тогда("база проверок поднимается в памяти на каждую проверку")
    fun theDatabaseIsRaisedInMemory() {
        assertTrue(
            text(appChecks.single { it.name == "DeviceTestModules.kt" }).contains("inMemoryDatabaseBuilder"),
            "the test graph must use an in-memory Room database",
        )
    }

    @Тогда("хранилища настроек и согласия очищаются перед каждой проверкой")
    fun theStoresAreClearedBeforeEveryCheck() {
        assertTrue(storesText.contains("fun clearAll"), "DeviceTestStores must expose clearAll")
        assertTrue(storesText.contains(".clear()"), "clearAll must empty the opened stores")
        assertTrue(
            baseText.contains("DeviceTestStores.clearAll(targetContext)"),
            "the check base must clear the stores before every check",
        )
    }

    @Тогда("отметка о запуске приложения снимается перед каждой проверкой")
    fun theLaunchMarkIsClearedBeforeEveryCheck() {
        assertTrue(baseText.contains("clearLaunchMark()"), "the base must clear the launch mark")
        assertTrue(
            baseText.contains("SystemStartupState.STARTUP_PREFERENCES"),
            "the launch mark must be cleared by the product name, not by a copy",
        )
    }

    @Тогда("ни одна проверка не наследует данные предыдущей")
    fun noCheckInheritsThePreviousData() {
        val setup = baseText
            .substringAfter("fun setUpDeviceStand()")
            .substringBefore("seedDefaultState()")
        val clear = setup.indexOf("DeviceTestStores.clearAll")
        val inject = setup.indexOf("hilt.inject()")
        assertTrue(clear >= 0 && clear < inject, "the stores must be cleared before the graph is built")
        assertTrue(setup.contains("DeviceTestClock.reset()"), "the clock must be reset before every check")
        assertTrue(setup.contains("clearLaunchMark()"), "the launch mark must be cleared before every check")
    }

    // --- Сид состояния ----------------------------------------------------------

    @Тогда("владелец, лекарства и приёмы создаются через доменные сценарии и репозитории")
    fun theStateIsSeededThroughDomainPaths() {
        assertTrue(graphText.contains("ProfileBootstrap"), "the owner must come from the product bootstrap")
        assertTrue(graphText.contains("MedicationRepository"), "medications must go through the repository")
        assertTrue(graphText.contains("PlanIntakesUseCase"), "intakes must be generated by the domain use case")
        assertTrue(baseText.contains("MedicationDraft.new"), "the seed draft must come from the domain")
        assertTrue(baseText.contains("DeviceTestSeed.medication"), "the base must save the seeded medication")
        assertTrue(baseText.contains("DeviceTestSeed.planIntakes"), "the base must plan the seeded intakes")
    }

    @Тогда("согласие и отметка онбординга записываются в тестовые хранилища")
    fun consentAndOnboardingAreSeeded() {
        assertTrue(graphText.contains("OnboardingStateStore"), "the onboarding mark must be seeded")
        assertTrue(graphText.contains("AnalyticsConsentStore"), "the consent decision must be seeded")
        assertTrue(
            baseText.contains("AnalyticsConsentDecision.ACCEPTED"),
            "checks that do not test consent must get the accepted decision by seed",
        )
    }

    @Тогда("каждая проверка утверждает только то, что создала сама")
    fun everyCheckAssertsOnlyWhatItSeeded() {
        val withoutLaunch = scenarioFiles().filterNot { text(it).contains("launchApp()") }.map { it.name }
        assertEquals(emptyList<String>(), withoutLaunch, "every scenario must launch the app on the test graph")
        val directStorage = (scenarioFiles() + appChecks.single { it.name == "DeviceScreenTest.kt" })
            .filter { text(it).contains(ROOM_PACKAGE) }
            .map { it.name }
        assertEquals(emptyList<String>(), directStorage, "checks must seed through the graph, not the database")
    }

    @Тогда("повторный прогон не сталкивается с прежними записями")
    fun theRepeatRunMeetsNoPreviousRecords() {
        val random = appChecks.filter { text(it).contains("randomUUID") }.map { it.name }
        assertEquals(emptyList<String>(), random, "seeded names must be fixed: the base is in memory")
        assertTrue(
            text(File(RepositoryLayout.root, TEST_STRINGS)).contains("device_test_medication_due"),
            "seed names must live in the test APK resources",
        )
    }

    // --- Время ------------------------------------------------------------------

    @Когда("разработчик сверяет управление временем в проверках устройства")
    fun theDeveloperChecksTimeManagement() {
        appChecks = appCheckFiles()
        clockText = text(appChecks.single { it.name == "DeviceTestClock.kt" })
        guardText = text(File(RepositoryLayout.root, GUARD_TEST))
    }

    @Тогда("подменные часы дают проверке заданный момент и часовой пояс")
    fun theTestClockGivesAFixedMomentAndZone() {
        assertTrue(clockText.contains("FixedTimeSource"), "the test clock must be the domain FixedTimeSource")
        assertTrue(clockText.contains("ZoneId.of"), "the test clock must fix the zone")
        assertTrue(clockText.contains("LocalDateTime.of"), "the test clock must fix the moment")
    }

    @Тогда("проверка может перевести часы вперёд внутри сценария")
    fun theTestClockCanAdvanceInsideAScenario() {
        assertTrue(clockText.contains("fun advanceTo("), "the test clock must allow advancing")
        assertTrue(clockText.contains("source.advanceTo"), "advancing must move the shared source")
    }

    @Тогда("ни один файл проверок устройства не читает системные часы напрямую")
    fun noDeviceCheckReadsTheSystemClock() {
        val offenders = appChecks
            .filter { file -> CLOCK_CALLS.any { call -> text(file).contains(call) } }
            .map { it.name }
        assertEquals(emptyList<String>(), offenders, "wall clock must be read through TimeSource only (НФТ-48)")
    }

    @Тогда("страж запрета прямых часов не ослаблен")
    fun theClockGuardIsNotWeakened() {
        assertTrue(guardText.contains("noModuleReadsTheSystemClockDirectly"), "$GUARD_TEST must keep the clock guard")
        assertTrue(guardText.contains("CLOCK_CALLS"), "the guard must check the direct clock calls")
        assertTrue(guardText.contains("\"app\""), "the guard must scan the app module, including androidTest")
    }

    // --- Запуск Activity --------------------------------------------------------

    @Когда("разработчик читает запуск настоящей Activity в проверках устройства")
    fun theDeveloperReadsTheActivityLaunch() {
        appChecks = appCheckFiles()
        baseText = text(appChecks.single { it.name == "DeviceScreenTest.kt" })
        activitiesText = text(appChecks.single { it.name == "DeviceActivities.kt" })
    }

    @Тогда("настоящая MainActivity запускается тестовым кодом, а не правилом ActivityScenario")
    fun theRealMainActivityIsLaunchedManually() {
        assertTrue(
            activitiesText.contains("ActivityLifecycleMonitorRegistry"),
            "the launch must go through $ACTIVITY_REGISTRY",
        )
        assertTrue(activitiesText.contains("MainActivity"), "the launch must address the real MainActivity")
        assertTrue(activitiesText.contains("startActivity"), "the launch must start the activity itself")
        val legacyRule = appChecks
            .filter { text(it).contains("createAndroidComposeRule") || text(it).contains("ActivityScenario.launch") }
            .map { it.name }
        assertEquals(emptyList<String>(), legacyRule, "the ActivityScenario rule must be replaced, not kept")
    }

    @Тогда("готовность экрана ожидается условием с таймаутом, а не сном")
    fun theScreenReadinessIsAwaitedByCondition() {
        assertTrue(baseText.contains("waitUntil("), "the base must wait for conditions")
        val sleeps = appChecks.filter { text(it).contains("Thread.sleep(") }.map { it.name }
        assertEquals(emptyList<String>(), sleeps, "checks must not sleep")
    }

    @Тогда("активности приложения закрываются между проверками")
    fun theActivitiesAreClosedBetweenChecks() {
        assertTrue(baseText.contains("closeAll"), "the check base must close the app")
        assertTrue(baseText.contains("finally"), "closing must happen even after a failure")
        assertTrue(activitiesText.contains("finish"), "closing must finish the activities")
    }

    @Тогда("отказ ожидания оставляет дерево семантики в журнале устройства")
    fun theTimeoutLeavesTheSemanticsTreeInTheLog() {
        assertTrue(baseText.contains("ComposeTimeoutException"), "the timeout must be caught with diagnostics")
        assertTrue(baseText.contains("dumpTree"), "the timeout must dump the semantics tree")
        assertTrue(baseText.contains("printToLog"), "the dump must go to the device log")
        assertTrue(activitiesText.contains("LOG_TAG"), "the dump must have a named log tag")
    }

    // --- Первый запуск и согласие ----------------------------------------------

    @Когда("разработчик сверяет проход первого запуска проверкой")
    fun theDeveloperChecksTheFirstLaunchPass() {
        appChecks = appCheckFiles()
        baseText = text(appChecks.single { it.name == "DeviceScreenTest.kt" })
        graphText = text(appChecks.single { it.name == "DeviceTestGraph.kt" })
        modulesText = text(appChecks.single { it.name == "DeviceTestModules.kt" })
        firstLaunchText = text(appChecks.single { it.name == "FirstLaunchDeviceTest.kt" })
    }

    @Тогда("хранилище согласия остаётся настоящим даже в тестовом графе")
    fun theConsentStorageStaysReal() {
        assertTrue(
            modulesText.contains("AnalyticsConsentPreferences") &&
                modulesText.contains("DeviceTestStores.analyticsConsent"),
            "the test graph must provide the real consent DataStore",
        )
        assertTrue(graphText.contains("AnalyticsConsentStore"), "the consent decision must be seeded through its port")
        val fakeStores = appChecks.filter { text(it).contains(": AnalyticsConsentStore {") }.map { it.name }
        assertEquals(emptyList<String>(), fakeStores, "the consent store must not be faked in tests")
    }

    @Тогда("проверка первого запуска проходит согласие и экран разрешений до «Сегодня»")
    fun theFirstLaunchCheckWalksTheWholeChain() {
        listOf("TAG_CONSENT_SCREEN", "TAG_PERMISSIONS_SCREEN", "TAG_SKIP", "TAG_TODAY_SCREEN").forEach { tag ->
            assertTrue(firstLaunchText.contains(tag), "the first-launch check must walk $tag")
        }
        assertTrue(
            firstLaunchText.contains("override fun seedDefaultState() = Unit"),
            "the first-launch check must not seed the consent decision",
        )
    }

    @Тогда("остальные проверки получают решение согласия сидом, а не нажатием в каждом сценарии")
    fun theOtherChecksGetConsentBySeed() {
        assertTrue(baseText.contains("AnalyticsConsentDecision.ACCEPTED"), "the base must seed the accepted decision")
        val showingConsent = scenarioFiles()
            .filter { text(it).contains("TAG_CONSENT_SCREEN") }
            .map { it.name }
            .sorted()
        assertEquals(
            listOf("FirstLaunchDeviceTest.kt", "SettingsDeviceTest.kt"),
            showingConsent,
            "only the first launch and the settings entry may meet the consent screen",
        )
    }

    @Тогда("экран согласия не подменяется и не пропускается правкой продукта")
    fun theConsentScreenIsNotReplacedOrSkipped() {
        val productSources = mainAppFiles().map(::text).joinToString("\n")
        assertTrue(productSources.contains("ConsentRoute"), "the product route must keep the consent screen")
        assertTrue(
            text(File(RepositoryLayout.root, CONSENT_SCREEN)).contains("TAG_CONSENT_SCREEN"),
            "the product consent screen must keep its tags",
        )
        val replaced = appChecks.filter { text(it).contains("fun ConsentScreen(") }.map { it.name }
        assertEquals(emptyList<String>(), replaced, "the consent screen must not be redefined in tests")
    }

    // --- Состав сценариев волны -------------------------------------------------

    @Когда("разработчик читает состав сценариев волны")
    fun theDeveloperReadsTheWaveScenarios() {
        appChecks = appCheckFiles()
        baseText = text(appChecks.single { it.name == "DeviceScreenTest.kt" })
    }

    // Строка шага дословная из Gherkin: разбить её или сократить нельзя.
    @Suppress("MaxLineLength", "ktlint:standard:max-line-length")
    @Тогда(
        "первый запуск, отметка приёма, групповая отметка, карточка лекарства, правка лекарства " +
            "и настройки названы своими классами проверок",
    )
    fun theWaveScenariosAreNamedByTheirClasses() {
        val expected = setOf(
            "FirstLaunchDeviceTest.kt",
            "IntakeMarkDeviceTest.kt",
            "GroupMarkDeviceTest.kt",
            "MedicationCardDeviceTest.kt",
            "MedicationEditDeviceTest.kt",
            "SettingsDeviceTest.kt",
        )
        val missing = expected - scenarioFiles().map { it.name }.toSet()
        assertEquals(emptySet<String>(), missing, "these wave scenarios have no check class")
    }

    @Тогда("четыре проверки первой волны переработаны под тестовый граф")
    fun theFirstWaveChecksAreReworked() {
        val firstWave = listOf(
            "TodayScreenDeviceTest.kt",
            "MedicationFormDeviceTest.kt",
            "DateTimePickerDeviceTest.kt",
        )
        firstWave.forEach { name ->
            val body = text(appChecks.single { it.name == name })
            assertTrue(body.contains("@HiltAndroidTest"), "$name must run on the Hilt test component")
            assertTrue(body.contains("launchApp()"), "$name must launch the app on the test graph")
        }
    }

    @Тогда("каждый сценарий оставляет кадр, названный состоянием и проверкой")
    fun everyScenarioLeavesANamedFrame() {
        scenarioFiles().forEach { file ->
            assertTrue(text(file).contains("capture("), "${file.name} must leave a frame")
        }
        assertTrue(baseText.contains("testName.methodName"), "the frame name must carry the check")
        assertTrue(
            text(appChecks.single { it.name == "ScreenCapture.kt" }).contains("captureToImage"),
            "the frame must be captured from the real window",
        )
    }

    @Тогда("кадры остаются свидетельством, а не эталоном")
    fun theFramesStayEvidenceNotGoldens() {
        val capture = text(appChecks.single { it.name == "ScreenCapture.kt" })
        assertFalse(capture.contains("assert"), "ScreenCapture must not compare anything")
        val screenshots = text(File(RepositoryLayout.root, RUN_SCREENSHOTS))
        listOf("sha256", "baseline").forEach { word ->
            assertFalse(screenshots.contains(word), "$RUN_SCREENSHOTS must not compare frames with $word")
        }
        assertEquals(emptyList<String>(), appChecks.filter { it.extension == "png" }.map { it.name })
    }

    // --- Журнал и сводка --------------------------------------------------------

    @Когда("разработчик читает сбор журнала и сводки прогона")
    fun theDeveloperReadsTheLogAndSummaryCollection() {
        runUi = text(File(RepositoryLayout.root, RUN_UI))
        runTests = text(File(RepositoryLayout.root, RUN_TESTS))
    }

    @Тогда("порядок запуска очищает журнал устройства перед проверками")
    fun theRunClearsTheDeviceLog() {
        assertTrue(runUi.contains("logcat -c"), "$RUN_UI must clear the device log before the checks")
    }

    @Тогда("журнал устройства пишется в каталог результатов прогона")
    fun theDeviceLogGoesToTheResultsDirectory() {
        assertTrue(runUi.contains("logcat -v time"), "the device log must be taken with timestamps")
        assertTrue(runUi.contains("logcat-ui.txt"), "the device log must have its own file")
        assertTrue(runUi.contains("skl_results_dir"), "the log must live in the harness results directory")
        assertTrue(runTests.contains("androidTest-results"), "the reports must go to the results directory")
    }

    @Тогда("журнал переживает падение проверок и остаётся для разбора")
    fun theDeviceLogSurvivesFailures() {
        assertTrue(runUi.contains("trap stop_logcat EXIT"), "the log writer must be stopped in any outcome")
        assertTrue(runUi.contains("|| tests_status=\$?"), "the run status must be captured, not lost")
        val testsLine = runUi.indexOf("run-tests.sh")
        val stopLine = runUi.indexOf("stop_logcat", testsLine)
        assertTrue(testsLine >= 0 && stopLine > testsLine, "the log must be closed after the checks")
    }

    @Тогда("сводка называет число проверок, падений и пропусков")
    fun theSummaryNamesTheCounts() {
        listOf("проверок:", "падений:", "пропущено:").forEach { word ->
            assertTrue(runUi.contains(word), "the summary must name «$word»")
        }
        assertTrue(runUi.contains("ui-summary.txt"), "the summary must be saved next to the reports")
        assertTrue(runUi.contains("xml.etree.ElementTree"), "the summary must be read from the AGP reports")
    }

    @Тогда("код возврата прогона остаётся кодом проверок, а сбой сбора кадров не прячется")
    fun theExitCodesStayHonest() {
        assertTrue(runUi.contains("shots_status=0"), "the frame collection status must be kept separately")
        assertTrue(runUi.contains("exit \"\$tests_status\""), "the check status must win when non-zero")
        assertTrue(runUi.contains("exit \"\$shots_status\""), "the frame collection failure must not be hidden")
    }

    // --- Окно и отказы ----------------------------------------------------------

    @Когда("разработчик читает порядок запуска экранного прогона после волны")
    fun theDeveloperReadsTheRunOrderAfterTheWave() {
        runUi = text(File(RepositoryLayout.root, RUN_UI))
        start = text(File(RepositoryLayout.root, START_SCRIPT))
        runAll = text(File(RepositoryLayout.root, RUN_ALL))
        config = text(File(RepositoryLayout.root, CONFIG_SCRIPT))
        runbook = text(File(RepositoryLayout.root, RUNBOOK))
    }

    @Тогда("команда прогона по-прежнему поднимает образ с видимым окном")
    fun theRunStillRaisesTheImageWithAWindow() {
        assertTrue(runUi.contains("--window"), "$RUN_UI must keep the visible window")
        assertTrue(runUi.contains("start.sh"), "$RUN_UI must start the image by $START_SCRIPT")
        assertTrue(runbook.contains("run-ui.sh"), "the runbook must name the single command")
    }

    @Тогда("безоконный запуск механических испытаний остаётся поведением по умолчанию")
    fun theMechanicalRunStaysHeadless() {
        assertTrue(config.contains("-no-window"), "the headless flag must stay in $CONFIG_SCRIPT")
        assertTrue(config.contains("SKL_HEADLESS_FLAG") && start.contains("SKL_HEADLESS_FLAG"))
        assertFalse(runAll.contains("--window"), "$RUN_ALL must stay headless")
    }

    @Тогда("без Android SDK команда отказывается и называет, чего не хватает")
    fun theRunRefusesWithoutAnAndroidSdk() {
        val emptySdk = File.createTempFile("skl97-no-sdk", "").apply {
            delete()
            mkdirs()
        }
        val process = ProcessBuilder("bash", File(RepositoryLayout.root, RUN_UI).absolutePath)
            .redirectErrorStream(true)
            .apply {
                environment()["ANDROID_SDK_ROOT"] = emptySdk.absolutePath
                environment().remove("ANDROID_HOME")
                environment()["PATH"] = "/usr/bin:/bin"
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(SANDBOX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
        assertTrue(process.exitValue() != 0, "the run must refuse without an SDK, output was: $output")
        assertTrue(output.contains("SDK"), "the refusal must name the SDK: $output")
    }

    // --- Обновлённые приёмочные правила ----------------------------------------

    @Когда("разработчик сверяет приёмочные правила после волны")
    fun theDeveloperChecksTheAcceptanceRulesAfterTheWave() {
        appChecks = appCheckFiles()
        catalog = text(File(RepositoryLayout.root, VERSION_CATALOG))
        hiltConvention = text(File(RepositoryLayout.root, HILT_CONVENTION))
        appBuild = text(RepositoryLayout.moduleBuildFile(APP_MODULE))
        config = text(File(RepositoryLayout.root, CONFIG_SCRIPT))
        runTests = text(File(RepositoryLayout.root, RUN_TESTS))
    }

    @Тогда("запрет GMS, Firebase и рекламы в каталоге версий сохранён")
    fun theForbiddenGroupsStayOutOfTheCatalog() {
        FORBIDDEN_GROUPS.forEach { group ->
            assertFalse(catalog.contains(group), "the version catalog must not mention $group (ПЛ-4)")
        }
    }

    @Тогда("тестовые зависимости стенда объявлены каталогом и конвенцией, а не обойдены")
    fun theStandDependenciesComeFromTheCatalogAndConvention() {
        assertTrue(catalog.contains(HILT_ANDROID_TESTING), "the catalog must declare $HILT_ANDROID_TESTING")
        assertTrue(hiltConvention.contains(HILT_ANDROID_TESTING), "the Hilt convention must declare it for androidTest")
        assertFalse(
            appBuild.contains("androidTestImplementation"),
            "$APP_MODULE must take its androidTest dependencies from the conventions, not by hand",
        )
    }

    @Тогда("правило запуска настоящей MainActivity называет ручной запуск")
    fun theLaunchRuleNamesTheManualLaunch() {
        val steps = text(File(RepositoryLayout.root, SCREEN_DEVICE_STEPS))
        assertTrue(steps.contains("ActivityLifecycleMonitorRegistry"), "the SKL-96 rule must name the manual launch")
        assertFalse(
            steps.contains("createAndroidComposeRule<MainActivity>"),
            "the old ActivityScenario rule must be gone from the acceptance steps",
        )
    }

    @Тогда("реестр файлов проверок устройства называет каждый новый файл")
    fun theRegistryNamesEveryDeviceCheckFile() {
        val design = text(File(RepositoryLayout.root, SKL24_DESIGN))
        val missing = appCheckFiles().map { it.name }.filterNot { design.contains(it) }
        assertEquals(emptyList<String>(), missing, "every file under $APP_ANDROID_TEST must be named in $SKL24_DESIGN")
    }

    @Тогда("группы прогона сохранили свой состав ключей")
    fun theRunGroupsKeepTheirKeys() {
        GROUP_KEYS.forEach { key ->
            val value = Regex("$key=\"([^\"]*)\"").find(config)?.groupValues?.get(1).orEmpty()
            assertTrue(value.isNotBlank(), "run group $key must stay declared and non-empty")
        }
        assertTrue(runTests.contains("SKL_GROUP_UI_PACKAGES"), "$RUN_TESTS must keep the ui group")
        assertTrue(config.contains("SKL_UI_API_LEVEL"), "the screen run level must stay in $CONFIG_SCRIPT")
    }

    // --- Границы продуктового кода ---------------------------------------------

    @Когда("разработчик сверяет границы продуктового кода после волны")
    fun theDeveloperChecksTheProductBoundaries() {
        appChecks = appCheckFiles()
    }

    @Тогда("домен не знает ни Android, ни Hilt, ни тестовых подмен")
    fun theDomainKnowsNeitherAndroidNorHiltNorTests() {
        val build = text(File(RepositoryLayout.root, CORE_DOMAIN_BUILD))
        assertTrue(build.contains("sklyanka.jvm.library"), "$CORE_DOMAIN_BUILD must stay a pure Kotlin/JVM module")
        assertFalse(build.contains("com.android"), "core-domain must not apply the Android plugin")
        assertFalse(build.contains("dagger.hilt"), "core-domain must not apply Hilt")
        assertFalse(build.contains("project("), "core-domain must not depend on project modules")
        val offenders = coreDomainSources()
            .filter { file ->
                val body = text(file)
                DOMAIN_FORBIDDEN.any { body.contains(it) }
            }
            .map { it.name }
        assertEquals(emptyList<String>(), offenders, "the domain must not see Android, Hilt or the test stand")
    }

    // Строка шага дословная из Gherkin: разбить её или сократить нельзя.
    @Suppress("MaxLineLength", "ktlint:standard:max-line-length")
    @Тогда(
        "изменения продуктового кода ограничены видимостью тестовых тегов, констант " +
            "и устойчивостью приёмников к отсутствию графа",
    )
    fun theProductChangesAreTheNamedSeams() {
        assertTrue(
            text(File(RepositoryLayout.root, SETTINGS_ROWS)).contains("const val TAG_SNOOZE_ROW"),
            "the settings row tags must be public for the device checks",
        )
        assertTrue(
            text(File(RepositoryLayout.root, SETTINGS_SCREEN)).contains("const val TAG_ANALYTICS_ROW"),
            "the analytics row tag must be public for the device checks",
        )
        assertTrue(
            text(File(RepositoryLayout.root, STARTUP_STATE)).contains("internal companion object"),
            "the startup mark names must be visible to the check base",
        )
        RECEIVERS.forEach { path ->
            val body = text(File(RepositoryLayout.root, path))
            assertTrue(
                body.contains("getOrNull() ?: return"),
                "$path must survive the missing Hilt component",
            )
        }
        val testEntryPoints = mainAppFiles()
            .filter { file -> text(file).contains("screen.support") || text(file).contains("androidTest") }
            .map { it.name }
        assertEquals(emptyList<String>(), testEntryPoints, "the product must not call the test stand")
    }

    @Тогда("ни одно правило приёма, расписания или напоминания не переписано")
    fun theIntakeScheduleAndReminderRulesStayInTheDomain() {
        DOMAIN_RULES.forEach { relative ->
            val rule = File(RepositoryLayout.root, "$CORE_DOMAIN_MAIN/$relative")
            assertTrue(rule.isFile, "the rule $relative must stay in the domain")
            assertTrue(rule.length() > 0, "the rule $relative must not be emptied")
        }
        val waveReferences = coreDomainSources().filter { text(it).contains("SKL-97") }.map { it.name }
        assertEquals(emptyList<String>(), waveReferences, "the wave must not rewrite domain rules")
    }

    // --- Чтение репозитория -----------------------------------------------------

    private fun text(file: File): String = file.readText()

    private fun appCheckFiles(): List<File> = kotlinFiles(File(RepositoryLayout.root, APP_ANDROID_TEST))

    private fun scenarioFiles(): List<File> = appCheckFiles()
        .filter { it.name.endsWith("DeviceTest.kt") && it.name != "DeviceScreenTest.kt" }
        .sortedBy { it.name }

    private fun mainAppFiles(): List<File> = kotlinFiles(File(RepositoryLayout.root, APP_MAIN))

    private fun coreDomainSources(): List<File> = kotlinFiles(File(RepositoryLayout.root, CORE_DOMAIN_MAIN))

    private fun kotlinFiles(directory: File): List<File> = if (!directory.isDirectory) {
        emptyList()
    } else {
        directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.name }.toList()
    }

    private companion object {
        const val APP_MODULE = "app"
        const val APP_ANDROID_TEST = "app/src/androidTest"
        const val APP_MAIN = "app/src/main"
        const val CORE_DOMAIN_MAIN = "core-domain/src/main/kotlin"
        const val CORE_DOMAIN_BUILD = "core-domain/build.gradle.kts"
        const val SKLYANKA_APP = "app/src/main/kotlin/ru/stohastika/sklyanka/SklyankaApp.kt"
        const val MAIN_ACTIVITY = "app/src/main/kotlin/ru/stohastika/sklyanka/MainActivity.kt"
        const val CONSENT_SCREEN =
            "feature-onboarding/src/main/kotlin/ru/stohastika/sklyanka/feature/onboarding/consent/ConsentScreen.kt"
        const val TEST_STRINGS = "app/src/androidTest/res/values/strings.xml"
        const val GUARD_TEST = "core-data/src/test/kotlin/ru/stohastika/sklyanka/data/GuardTest.kt"
        const val SKL24_DESIGN = "docs/tasks/SKL-24/TDD.md"
        const val SKL82_DESIGN = "docs/tasks/SKL-82/TDD.md"
        const val SKL96_DESIGN = "docs/tasks/SKL-96/TDD.md"
        const val VERSION_CATALOG = "gradle/libs.versions.toml"
        const val HILT_CONVENTION =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/HiltConventionPlugin.kt"
        const val APP_CONVENTION =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/AndroidApplicationConventionPlugin.kt"
        const val SCREEN_DEVICE_STEPS =
            "acceptance/src/main/kotlin/ru/stohastika/sklyanka/acceptance/steps/task/ScreenDeviceHarnessSteps.kt"
        const val SETTINGS_ROWS =
            "feature-settings/src/main/kotlin/ru/stohastika/sklyanka/feature/settings/SettingsRows.kt"
        const val SETTINGS_SCREEN =
            "feature-settings/src/main/kotlin/ru/stohastika/sklyanka/feature/settings/SettingsScreen.kt"
        const val STARTUP_STATE =
            "app/src/main/kotlin/ru/stohastika/sklyanka/platform/startup/SystemStartupState.kt"

        const val HARNESS = "tools/emulator"
        const val RUN_UI = "$HARNESS/run-ui.sh"
        const val RUN_TESTS = "$HARNESS/run-tests.sh"
        const val RUN_ALL = "$HARNESS/run-all.sh"
        const val START_SCRIPT = "$HARNESS/start.sh"
        const val CONFIG_SCRIPT = "$HARNESS/config.sh"
        const val RUN_SCREENSHOTS = "$HARNESS/screenshots.sh"
        const val RUNBOOK = "$HARNESS/README.md"

        const val HILT_ANDROID_TESTING = "hilt-android-testing"
        const val HILT_COMPILER = "hilt-compiler"
        const val KSP_ANDROID_TEST = "kspAndroidTest"
        const val CUSTOM_TEST_APPLICATION = "@CustomTestApplication"
        const val ACTIVITY_REGISTRY = "ActivityLifecycleMonitorRegistry"
        const val ROOM_PACKAGE = "ru.stohastika.sklyanka.data.room"

        val TEST_ONLY_MARKERS = listOf("ru.stohastika.sklyanka.screen.support", "CustomTestApplication", "DeviceTest")
        val FORBIDDEN_GROUPS = listOf("com.google.android.gms", "com.google.firebase", "com.google.android.play")
        val CLOCK_CALLS = listOf(
            "System.currentTimeMillis(",
            "Instant.now(",
            "LocalDate.now(",
            "LocalTime.now(",
            "LocalDateTime.now(",
            "ZonedDateTime.now(",
            "ZoneId.systemDefault(",
        )
        val DOMAIN_FORBIDDEN = listOf("import android.", "import dagger.", "import javax.inject.", "screen.support")
        val GROUP_KEYS = listOf(
            "SKL_GROUP_FAST_PACKAGES",
            "SKL_GROUP_DATA_PACKAGES",
            "SKL_GROUP_HEAVY_CLASSES",
            "SKL_GROUP_MIGRATION_CLASSES",
        )
        val RECEIVERS = listOf(
            "app/src/main/kotlin/ru/stohastika/sklyanka/reminder/DirectBootReceiver.kt",
            "app/src/main/kotlin/ru/stohastika/sklyanka/reminder/SystemEventReceiver.kt",
            "app/src/main/kotlin/ru/stohastika/sklyanka/reminder/AlarmReceiver.kt",
        )
        val DOMAIN_RULES = listOf(
            "ru/stohastika/sklyanka/domain/schedule/ScheduleExpander.kt",
            "ru/stohastika/sklyanka/domain/intake/MarkIntakeUseCase.kt",
            "ru/stohastika/sklyanka/domain/reminder/RescheduleRemindersUseCase.kt",
        )

        const val SANDBOX_TIMEOUT_SECONDS = 30L
    }
}

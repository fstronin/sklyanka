package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Шаги приёмки SKL-98 — структурная часть.
 *
 * Сценарии без `@manual @device` читают репозиторий: оснастку прямых запусков,
 * состав третьей волны сценариев, границы SAF и UiAutomator, устойчивость сводки
 * к отсутствию `python3`, регистрацию файлов, границы продуктового кода и стабилизацию
 * окна отмены. Поведенческие сценарии (E/I/J/L/M) помечены `@manual @device` и шагов
 * здесь не имеют: в среде сборки эмулятора нет.
 */
@Suppress("TooManyFunctions", "LargeClass")
class DeviceActivityHarnessSteps {

    private var appChecks: List<File> = emptyList()
    private var scenarioFiles: List<File> = emptyList()
    private var activitiesText: String = ""
    private var baseText: String = ""
    private var skl24Design: String = ""
    private var skl97Steps: String = ""
    private var runUi: String = ""
    private var start: String = ""
    private var runAll: String = ""
    private var config: String = ""
    private var catalog: String = ""
    private var appBuild: String = ""

    // --- Предыстория -----------------------------------------------------------

    @Допустим("тестовый граф экранного прогона описан задачей SKL-97")
    fun theDeviceTestGraphIsDescribedBySkl97() {
        assertTrue(File(RepositoryLayout.root, SKL97_DESIGN).isFile, "the SKL-97 design must exist")
        assertTrue(
            File(RepositoryLayout.root, DEVICE_APPLICATION).isFile,
            "the device test application must exist: $DEVICE_APPLICATION",
        )
    }

    // --- Оснастка прямых запусков ----------------------------------------------

    @Когда("разработчик читает оснастку прямых запусков")
    fun theDeveloperReadsTheDirectLaunchHarness() {
        appChecks = appCheckFiles()
        activitiesText = text(File(RepositoryLayout.root, DEVICE_ACTIVITIES))
        baseText = text(File(RepositoryLayout.root, DEVICE_SCREEN_TEST))
    }

    @Тогда("оснастка объявляет запуск прямым намерением")
    fun theHarnessDeclaresADirectLaunch() {
        assertTrue(activitiesText.contains("fun launchIntent("), "$DEVICE_ACTIVITIES must declare launchIntent")
        assertTrue(activitiesText.contains("startActivity"), "the direct launch must start the activity itself")
    }

    @Тогда("оснастка умеет ждать возобновлённую активность любого класса")
    fun theHarnessAwaitsAnyActivityClass() {
        assertTrue(
            activitiesText.contains("fun resumedActivity(): Activity?"),
            "$DEVICE_ACTIVITIES must expose any resumed activity, not only MainActivity",
        )
        assertTrue(baseText.contains("fun awaitActivity("), "$DEVICE_SCREEN_TEST must await an activity class")
    }

    @Тогда("оснастка умеет дождаться закрытия активности")
    fun theHarnessAwaitsTheScreenClosing() {
        assertTrue(baseText.contains("fun awaitScreenClosed("), "the base must await the screen closing")
        assertTrue(activitiesText.contains("fun closeAll("), "closing the app must stay in $DEVICE_ACTIVITIES")
    }

    @Тогда("ни одна проверка волны не пользуется ActivityScenario")
    fun noWaveCheckUsesActivityScenario() {
        val legacy = appChecks
            .filter { text(it).contains("ActivityScenario.launch") || text(it).contains("createAndroidComposeRule") }
            .map { it.name }
        assertEquals(emptyList<String>(), legacy, "the direct launch must replace the ActivityScenario rule")
    }

    @Тогда("закрытие активностей после проверки осталось общим")
    fun closingActivitiesStaysShared() {
        assertTrue(baseText.contains("DeviceActivities.closeAll(compose)"), "the base must close all activities")
        assertTrue(baseText.contains("finally"), "closing must happen even after a failure")
    }

    // --- Состав третьей волны ---------------------------------------------------

    @Когда("разработчик читает состав третьей волны сценариев")
    fun theDeveloperReadsTheThirdWaveComposition() {
        appChecks = appCheckFiles()
        scenarioFiles = scenarioFiles()
    }

    // Строка шага дословная из Gherkin: разбить её или сократить нельзя.
    @Suppress("MaxLineLength", "ktlint:standard:max-line-length")
    @Тогда(
        "архив, диагностика, разрешения, карточка напоминания и самопроверка " +
            "названы своими классами проверок",
    )
    fun theThirdWaveScenariosAreNamed() {
        val expected = setOf(
            "ArchiveDeviceTest.kt",
            "DiagnosticsDeviceTest.kt",
            "PermissionsFromSettingsDeviceTest.kt",
            "ReminderCardDeviceTest.kt",
            "DeliveryCheckDeviceTest.kt",
        )
        val missing = expected - scenarioFiles.map { it.name }.toSet()
        assertEquals(emptySet<String>(), missing, "these wave-3 scenarios have no check class")
    }

    @Тогда("сценарии экранов-активностей запускаются фабриками намерений продукта")
    fun activityScenariosUseProductIntentFactories() {
        val expected = mapOf(
            "DiagnosticsDeviceTest.kt" to "DiagnosticsActivity.intentFor(",
            "PermissionsFromSettingsDeviceTest.kt" to "PermissionsActivity.intentFor(",
            "ReminderCardDeviceTest.kt" to "ReminderActivity.intentFor(",
            "DeliveryCheckDeviceTest.kt" to "DeliveryCheckActivity.intentFor(",
        )
        expected.forEach { (fileName, factory) ->
            val body = text(scenarioFiles.single { it.name == fileName })
            assertTrue(body.contains(factory), "$fileName must start the screen by $factory")
        }
    }

    @Тогда("сценарий архива идёт через карточку лекарства и вкладку архива")
    fun theArchiveScenarioWalksTheProductPath() {
        val body = text(scenarioFiles.single { it.name == "ArchiveDeviceTest.kt" })
        listOf("TAG_MEDICATION_CARD", "TAG_ARCHIVE", "TAG_ARCHIVE_SCREEN", "TAG_ARCHIVE_RESTORE").forEach { tag ->
            assertTrue(body.contains(tag), "the archive scenario must use $tag")
        }
    }

    @Тогда("каждый сценарий оставляет кадр состояния")
    fun everyScenarioLeavesAFrame() {
        scenarioFiles.forEach { file ->
            assertTrue(text(file).contains("capture("), "${file.name} must leave a frame")
        }
    }

    // --- Границы волны ----------------------------------------------------------

    @Когда("разработчик сверяет состав сценариев диагностики")
    fun theDeveloperChecksTheDiagnosticsScopes() {
        scenarioFiles = scenarioFiles()
        catalog = text(File(RepositoryLayout.root, VERSION_CATALOG))
    }

    @Тогда("ни один сценарий волны не открывает системный выбор места")
    fun noWaveScenarioOpensTheSystemPicker() {
        val offenders = scenarioFiles
            .filter { file ->
                val body = text(file)
                body.contains("CreateDocument") ||
                    body.contains("ACTION_CREATE_DOCUMENT") ||
                    body.contains("openDocument")
            }
            .map { it.name }
        assertEquals(emptyList<String>(), offenders, "SAF is out of the wave scope")
        assertFalse(text(File(RepositoryLayout.root, DIAGNOSTICS_SCENARIO_TEST)).contains("TAG_EXPORT_JOURNAL"))
    }

    @Тогда("выгрузка журнала остаётся возможностью продукта")
    fun theJournalExportStaysAProductFeature() {
        assertTrue(
            text(File(RepositoryLayout.root, DIAGNOSTICS_SCREEN)).contains("TAG_EXPORT_JOURNAL"),
            "the product must keep the journal export action",
        )
    }

    @Тогда("системный выбор места назван отложенной задачей")
    fun theSystemPickerIsNamedAsALaterTask() {
        assertTrue(text(File(RepositoryLayout.root, SKL98_TODO)).contains("SAF"), "the TODO must defer SAF")
        assertTrue(
            text(File(RepositoryLayout.root, SKL98_TECH_OPTIONS)).contains("DocumentsUI"),
            "the tech options must name the missing system picker",
        )
    }

    @Когда("разработчик читает каталог версий после волны")
    fun theDeveloperReadsTheCatalogAfterTheWave() {
        catalog = text(File(RepositoryLayout.root, VERSION_CATALOG))
        appBuild = text(RepositoryLayout.moduleBuildFile(APP_MODULE))
    }

    @Тогда("зависимости UiAutomator в каталоге нет")
    fun theCatalogHasNoUiAutomator() {
        assertFalse(catalog.contains("uiautomator"), "UiAutomator is a later-wave dependency")
    }

    @Тогда("системные клавиши, шторка и экран блокировки остаются эталонному устройству")
    fun systemKeysAndLockscreenStayOnTheDevice() {
        val design = text(File(RepositoryLayout.root, SKL98_DESIGN))
        assertTrue(design.contains("UiAutomator"), "the design must name the deferred tool")
        assertTrue(design.contains("Вне рамок"), "the design must keep UiAutomator outside the wave")
    }

    @Тогда("новая зависимость волной не объявлялась")
    fun noNewDependencyWasDeclared() {
        assertFalse(
            appBuild.contains("androidTestImplementation"),
            "$APP_MODULE must take its androidTest dependencies from the conventions, not by hand",
        )
        assertFalse(catalog.contains("uiautomator"), "no UiAutomator coordinate may appear (ПЛ-4)")
    }

    // --- Устойчивость сводки и регистрация --------------------------------------

    @Когда("разработчик читает порядок запуска после волны")
    fun theDeveloperReadsTheRunOrderAfterTheWave() {
        runUi = text(File(RepositoryLayout.root, RUN_UI))
        start = text(File(RepositoryLayout.root, START_SCRIPT))
        runAll = text(File(RepositoryLayout.root, RUN_ALL))
        config = text(File(RepositoryLayout.root, CONFIG_SCRIPT))
    }

    @Тогда("сбор сводки проверяет наличие python3")
    fun theSummaryChecksForPython() {
        assertTrue(runUi.contains("command -v python3"), "$RUN_UI must check for python3 before the summary")
    }

    @Тогда("без python3 прогон продолжается с предупреждением")
    fun theRunContinuesWithoutPython() {
        assertTrue(runUi.contains("python3 не найден"), "the run must warn about the missing python3")
        assertTrue(runUi.contains("сводка недоступна"), "the summary file must carry the named reason")
    }

    @Тогда("образ гасится в любом исходе")
    fun theImageIsStoppedInAnyOutcome() {
        val check = runUi.indexOf("command -v python3")
        val stop = runUi.indexOf("stop.sh")
        assertTrue(check >= 0 && stop > check, "stopping the image must stay after the summary block")
        assertTrue(runUi.contains("--keep"), "the --keep branch must survive the change")
    }

    @Тогда("код возврата остаётся кодом проверок")
    fun theExitCodeStaysTheCheckCode() {
        assertTrue(runUi.contains("exit \"\$tests_status\""), "the check status must win when non-zero")
        assertTrue(runUi.contains("exit \"\$shots_status\""), "the frame collection failure must not be hidden")
    }

    @Когда("разработчик сверяет регистрацию волны 3")
    fun theDeveloperChecksTheThirdWaveRegistration() {
        appChecks = appCheckFiles()
        skl24Design = text(File(RepositoryLayout.root, SKL24_DESIGN))
        skl97Steps = text(File(RepositoryLayout.root, DEVICE_TEST_STEPS))
        config = text(File(RepositoryLayout.root, CONFIG_SCRIPT))
        runAll = text(File(RepositoryLayout.root, RUN_ALL))
    }

    @Тогда("каждый новый файл проверок назван в реестре JVM-стенда")
    fun everyNewFileIsNamedInTheDesign() {
        val missing = appChecks.map { it.name }.filterNot { skl24Design.contains(it) }
        assertEquals(emptyList<String>(), missing, "every file under $APP_ANDROID_TEST must be named in $SKL24_DESIGN")
    }

    @Тогда("приёмочный шаг волны 2 допускает прямой запуск активности")
    fun theSecondWaveStepAllowsADirectLaunch() {
        assertTrue(
            skl97Steps.contains("launchScreen("),
            "the SKL-97 step must accept a direct screen launch: $DEVICE_TEST_STEPS",
        )
    }

    @Тогда("группы прогона сохранили свой состав")
    fun theRunGroupsKeepTheirKeys() {
        GROUP_KEYS.forEach { key ->
            val value = Regex("$key=\"([^\"]*)\"").find(config)?.groupValues?.get(1).orEmpty()
            assertTrue(value.isNotBlank(), "run group $key must stay declared and non-empty")
        }
    }

    @Тогда("запуск механических испытаний остался безоконным")
    fun theMechanicalRunStaysHeadless() {
        assertTrue(config.contains("-no-window") && config.contains("SKL_HEADLESS_FLAG"))
        assertTrue(text(File(RepositoryLayout.root, START_SCRIPT)).contains("SKL_HEADLESS_FLAG"))
        assertFalse(runAll.contains("--window"), "$RUN_ALL must stay headless")
    }

    // --- Границы продуктового кода ---------------------------------------------

    @Когда("разработчик сверяет границы волны 3")
    fun theDeveloperChecksTheProductBoundaries() {
        appChecks = appCheckFiles()
        scenarioFiles = scenarioFiles()
    }

    @Тогда("новые файлы волны лежат только в проверках устройства и оснастке")
    fun theNewFilesStayInTheTestStand() {
        val expected = listOf(
            "ArchiveDeviceTest.kt",
            "DiagnosticsDeviceTest.kt",
            "PermissionsFromSettingsDeviceTest.kt",
            "ReminderCardDeviceTest.kt",
            "DeliveryCheckDeviceTest.kt",
        )
        expected.forEach { name ->
            val file = scenarioFiles.singleOrNull { it.name == name }
            assertTrue(
                file != null && file.path.contains("/$APP_ANDROID_TEST/"),
                "$name must live in $APP_ANDROID_TEST",
            )
        }
        val productLeaks = mainAppFiles()
            .filter { file -> expected.any { name -> text(file).contains(name.removeSuffix(".kt")) } }
            .map { it.name }
        assertEquals(emptyList<String>(), productLeaks, "the product must not reference wave-3 checks")
    }

    @Тогда("домен не получает ни Android, ни Hilt, ни тестовых подмен")
    fun theDomainGetsNoAndroidNoHiltNoTestDoubles() {
        val build = text(File(RepositoryLayout.root, CORE_DOMAIN_BUILD))
        assertTrue(build.contains("sklyanka.jvm.library"), "$CORE_DOMAIN_BUILD must stay a pure Kotlin/JVM module")
        assertFalse(build.contains("com.android"), "core-domain must not apply the Android plugin")
        assertFalse(build.contains("dagger.hilt"), "core-domain must not apply Hilt")
        val offenders = coreDomainSources()
            .filter { file ->
                val body = text(file)
                DOMAIN_FORBIDDEN.any { body.contains(it) }
            }
            .map { it.name }
        assertEquals(emptyList<String>(), offenders, "the domain must not see Android, Hilt or the test stand")
    }

    @Тогда("экраны и правила приёмов продукта не переписаны")
    fun theProductScreensAndRulesStayInPlace() {
        PRODUCT_ACTIVITIES.forEach { path ->
            val body = text(File(RepositoryLayout.root, path))
            assertTrue(body.contains("intentFor("), "$path must keep its product intent factory")
        }
        DOMAIN_RULES.forEach { relative ->
            val rule = File(RepositoryLayout.root, "$CORE_DOMAIN_MAIN/$relative")
            assertTrue(rule.isFile && rule.length() > 0, "the domain rule $relative must stay in place")
        }
        val waveReferences = coreDomainSources().filter { text(it).contains("SKL-98") }.map { it.name }
        assertEquals(emptyList<String>(), waveReferences, "the wave must not rewrite domain rules")
    }

    // --- Стабилизация окна отмены B --------------------------------------------

    @Когда("разработчик читает проверку отметки приёма после волны")
    fun theDeveloperReadsTheIntakeMarkCheckAfterTheWave() {
        scenarioFiles = scenarioFiles()
    }

    @Тогда("кадр отмеченного состояния и отмена — разные проверки")
    fun theFrameAndTheUndoAreSeparateChecks() {
        val body = text(scenarioFiles.single { it.name == "IntakeMarkDeviceTest.kt" })
        assertTrue(body.contains("fun theTakenIntakeShowsTheUndoWindow()"), "the frame check must exist")
        assertTrue(body.contains("fun theUndoReturnsTheIntakeToPending()"), "the undo check must exist")
    }

    @Тогда("проверка отмены не снимает кадр между отметкой и отменой")
    fun theUndoCheckTakesNoFrameBeforeTheUndo() {
        val body = text(scenarioFiles.single { it.name == "IntakeMarkDeviceTest.kt" })
        val undoCheck = body.substringAfter("fun theUndoReturnsTheIntakeToPending()")
        val click = undoCheck.indexOf("performClick()")
        val capture = undoCheck.indexOf("capture(")
        assertTrue(click in 0 until capture, "the undo must be pressed before the frame is taken")
    }

    @Тогда("обе проверки идут на управляемом времени")
    fun bothChecksUseTheManagedClock() {
        val body = text(scenarioFiles.single { it.name == "IntakeMarkDeviceTest.kt" })
        assertTrue(body.contains("DeviceTestClock.scheduledTime"), "the seed must use the managed clock")
        val directClock = CLOCK_CALLS.filter { body.contains(it) }
        assertEquals(emptyList<String>(), directClock, "checks must not read the system clock (НФТ-48)")
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
        const val DEVICE_ACTIVITIES =
            "app/src/androidTest/kotlin/ru/stohastika/sklyanka/screen/support/DeviceActivities.kt"
        const val DEVICE_APPLICATION =
            "app/src/androidTest/kotlin/ru/stohastika/sklyanka/screen/support/DeviceTestApplication.kt"
        const val DEVICE_SCREEN_TEST = "app/src/androidTest/kotlin/ru/stohastika/sklyanka/screen/DeviceScreenTest.kt"
        const val DIAGNOSTICS_SCENARIO_TEST =
            "app/src/androidTest/kotlin/ru/stohastika/sklyanka/screen/DiagnosticsDeviceTest.kt"
        const val DEVICE_TEST_STEPS =
            "acceptance/src/main/kotlin/ru/stohastika/sklyanka/acceptance/steps/task/DeviceTestHarnessSteps.kt"
        const val DIAGNOSTICS_SCREEN =
            "feature-reminder/src/main/kotlin/ru/stohastika/sklyanka/feature/reminder/DiagnosticsScreen.kt"
        const val DIAGNOSTICS_ACTIVITY =
            "feature-reminder/src/main/kotlin/ru/stohastika/sklyanka/feature/reminder/DiagnosticsActivity.kt"
        const val PERMISSIONS_ACTIVITY =
            "feature-onboarding/src/main/kotlin/ru/stohastika/sklyanka/feature/onboarding/PermissionsActivity.kt"
        const val REMINDER_ACTIVITY =
            "feature-reminder/src/main/kotlin/ru/stohastika/sklyanka/feature/reminder/ReminderActivity.kt"
        const val DELIVERY_CHECK_ACTIVITY =
            "feature-reminder/src/main/kotlin/ru/stohastika/sklyanka/feature/reminder/DeliveryCheckActivity.kt"
        const val SKL24_DESIGN = "docs/tasks/SKL-24/TDD.md"
        const val SKL97_DESIGN = "docs/tasks/SKL-97/TDD.md"
        const val SKL98_DESIGN = "docs/tasks/SKL-98/TDD.md"
        const val SKL98_TODO = "docs/tasks/SKL-98/TODO.md"
        const val SKL98_TECH_OPTIONS = "docs/tasks/SKL-98/tech-options.md"
        const val VERSION_CATALOG = "gradle/libs.versions.toml"

        const val HARNESS = "tools/emulator"
        const val RUN_UI = "$HARNESS/run-ui.sh"
        const val RUN_ALL = "$HARNESS/run-all.sh"
        const val START_SCRIPT = "$HARNESS/start.sh"
        const val CONFIG_SCRIPT = "$HARNESS/config.sh"

        val DOMAIN_FORBIDDEN = listOf("import android.", "import dagger.", "import javax.inject.", "screen.support")
        val DOMAIN_RULES = listOf(
            "ru/stohastika/sklyanka/domain/schedule/ScheduleExpander.kt",
            "ru/stohastika/sklyanka/domain/intake/MarkIntakeUseCase.kt",
            "ru/stohastika/sklyanka/domain/reminder/RescheduleRemindersUseCase.kt",
        )
        val GROUP_KEYS = listOf(
            "SKL_GROUP_FAST_PACKAGES",
            "SKL_GROUP_DATA_PACKAGES",
            "SKL_GROUP_HEAVY_CLASSES",
            "SKL_GROUP_MIGRATION_CLASSES",
        )
        val PRODUCT_ACTIVITIES = listOf(
            DIAGNOSTICS_ACTIVITY,
            PERMISSIONS_ACTIVITY,
            REMINDER_ACTIVITY,
            DELIVERY_CHECK_ACTIVITY,
        )
        val CLOCK_CALLS = listOf(
            "System.currentTimeMillis(",
            "Instant.now(",
            "LocalDate.now(",
            "LocalTime.now(",
            "LocalDateTime.now(",
            "ZonedDateTime.now(",
            "ZoneId.systemDefault(",
        )
    }
}

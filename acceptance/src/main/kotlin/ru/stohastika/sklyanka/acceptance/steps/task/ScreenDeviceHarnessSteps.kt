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
 * Шаги приёмки SKL-96 — структурная часть.
 *
 * Эти сценарии говорят не о поведении экрана, а об устройстве оснастки экранного
 * прогона: где живут Compose-проверки, как включается окно, какой группой идут
 * проверки, чем кадры экрана отличаются от эталона, что происходит при отсутствии
 * SDK. Проверять это чтением репозитория — ровно то, что сделает человек, севший
 * за машину с эмулятором, только автоматически.
 *
 * Сценариям с поднятым эмулятором (`@manual @device`) шаги здесь не нужны и не
 * пишутся: в среде сборки эмулятора нет.
 *
 * Общее состояние сценария этим шагам не нужно: они говорят об устройстве репозитория.
 */
@Suppress("TooManyFunctions", "LargeClass")
class ScreenDeviceHarnessSteps {

    private var appChecks: List<File> = emptyList()
    private var deviceChecks: List<File> = emptyList()
    private var config: String = ""
    private var runUi: String = ""
    private var runTests: String = ""
    private var start: String = ""
    private var runAll: String = ""
    private var runbook: String = ""
    private var capture: String = ""
    private var captureCaller: String = ""
    private var screenshots: String = ""
    private var design: String = ""
    private var syntaxFailures: List<String> = emptyList()
    private var sandboxExitCode: Int = -1
    private var sandboxOutput: String = ""

    // --- Предыстория -----------------------------------------------------------

    @Допустим("оснастка прогона на эмуляторе описана задачей SKL-27")
    fun theEmulatorHarnessExists() {
        assertTrue(
            File(RepositoryLayout.root, HARNESS).isDirectory,
            "the SKL-27 harness must exist in $HARNESS",
        )
    }

    // --- Место проверок ---------------------------------------------------------

    @Когда("разработчик читает место экранных проверок на устройстве")
    fun theDeveloperReadsWhereTheScreenChecksLive() {
        appChecks = kotlinFiles(File(RepositoryLayout.root, "$APP_MODULE/src/$ANDROID_TEST"))
        deviceChecks = deviceCheckFiles()
        assertTrue(appChecks.isNotEmpty(), "the app module must hold device screen checks")
    }

    @Тогда("проверки лежат в каталоге androidTest модуля приложения")
    fun theChecksLiveInTheAppModule() {
        val outside = appChecks.filterNot { it.path.contains("/$APP_MODULE/src/$ANDROID_TEST/") }
        assertEquals(emptyList<String>(), outside.map { it.name }, "these files are not in the app module")
    }

    @Тогда("среди них есть проверка, запускающая настоящую MainActivity")
    fun aCheckLaunchesTheRealMainActivity() {
        assertTrue(
            appChecks.any { it.readText().contains(COMPOSE_ACTIVITY_RULE) },
            "a device screen check must launch the real MainActivity: $COMPOSE_ACTIVITY_RULE is missing",
        )
    }

    @Тогда("ни одной новой зависимости для них не объявлено")
    fun noNewDependencyIsDeclared() {
        val appBuild = RepositoryLayout.moduleBuildFile(APP_MODULE).readText()
        assertFalse(
            appBuild.contains("androidTestImplementation"),
            "$APP_MODULE must take its androidTest dependencies from the conventions, not by hand",
        )
        assertTrue(
            File(RepositoryLayout.root, COMPOSE_CONVENTION).readText().contains(UI_TEST_JUNIT4),
            "the Compose convention must keep declaring $UI_TEST_JUNIT4 for androidTest",
        )
        val catalog = File(RepositoryLayout.root, VERSION_CATALOG).readText()
        FORBIDDEN_GROUPS.forEach { group ->
            assertFalse(catalog.contains(group), "the version catalog must not mention $group (ПЛ-4)")
        }
    }

    @Тогда("Robolectric-проверки остаются в каталогах testDebug своих модулей")
    fun theRobolectricChecksStayOnTheJvm() {
        val jvmChecks = RepositoryLayout.root.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { module -> kotlinFiles(File(module, "src/$TEST_DEBUG")) }
            .orEmpty()
        assertTrue(jvmChecks.isNotEmpty(), "the JVM screen checks must stay in src/testDebug")
        assertTrue(
            jvmChecks.any { it.readText().contains(ROBOLECTRIC_QUALIFIERS) },
            "the Robolectric screen checks keep their qualifiers: they are not device tests",
        )
    }

    @Тогда("среди экранных проверок устройства нет ни одной с квалификатором Robolectric")
    fun noDeviceCheckIsRobolectric() {
        val robolectric = appChecks
            .filter { it.readText().contains("org.robolectric") || it.readText().contains(ROBOLECTRIC_QUALIFIERS) }
            .map { it.name }
        assertEquals(emptyList<String>(), robolectric, "a Robolectric qualifier has no place in androidTest")
    }

    @Тогда("снимки JVM-стенда и их число не затрагиваются")
    fun theJvmSnapshotsAreUntouched() {
        assertTrue(
            File(RepositoryLayout.root, SCREEN_CONVENTION).readText().contains(ROBORAZZI_VERIFY),
            "the ordinary JVM run must keep verifying its goldens",
        )
        assertTrue(goldenFiles().isNotEmpty(), "the SKL-24 goldens must stay in place")
        assertEquals(
            emptyList<String>(),
            appChecks.filter { it.extension == PNG }.map { it.name },
            "device screen checks must not carry golden images",
        )
    }

    // --- Прогон ----------------------------------------------------------------

    @Когда("разработчик читает порядок запуска экранного прогона")
    fun theDeveloperReadsTheScreenRunOrder() {
        runbook = File(RepositoryLayout.root, "$HARNESS/README.md").readText()
        runUi = File(RepositoryLayout.root, "$HARNESS/run-ui.sh").readText()
        runTests = File(RepositoryLayout.root, "$HARNESS/run-tests.sh").readText()
    }

    @Тогда("в нём названа одна команда прогона")
    fun theRunbookNamesOneCommand() {
        assertTrue(runbook.contains(RUN_UI_SCRIPT), "the runbook must name $RUN_UI_SCRIPT")
    }

    @Тогда("эта команда поднимает образ с видимым окном")
    fun theCommandStartsTheImageWithAWindow() {
        assertTrue(runUi.contains(WINDOW_ARG), "run-ui.sh must start the image with $WINDOW_ARG")
        assertTrue(runUi.contains(START_SCRIPT), "run-ui.sh must start the image by $START_SCRIPT")
    }

    @Тогда("проверки идут задачей connectedDebugAndroidTest, а не руками через am instrument")
    fun theChecksRunAsTheGradleTask() {
        assertTrue(runUi.contains(TESTS_SCRIPT), "run-ui.sh must call $TESTS_SCRIPT")
        assertTrue(configText().contains(CONNECTED_TASK), "the connected task must be declared in config.sh")
        assertFalse(configText().contains(AM_INSTRUMENT), "the run must not call am instrument by hand")
    }

    @Тогда("отчёты прогона складываются в каталог результатов эмулятора")
    fun theReportsLandInTheResultsDirectory() {
        assertTrue(runTests.contains(TEST_RESULTS_DIR), "$TESTS_SCRIPT must copy the AGP reports")
        assertTrue(configText().contains(RESULTS_DIR_KEY), "the results directory must be declared in config.sh")
    }

    // --- Окно и headless --------------------------------------------------------

    @Когда("разработчик читает параметры запуска эмулятора")
    fun theDeveloperReadsTheLaunchParameters() {
        config = configText()
        start = File(RepositoryLayout.root, "$HARNESS/start.sh").readText()
        runAll = File(RepositoryLayout.root, "$HARNESS/run-all.sh").readText()
    }

    @Тогда("признак запуска без окна остался в общем файле параметров")
    fun theHeadlessFlagStaysInTheConfig() {
        assertTrue(config.contains(NO_WINDOW), "the headless flag $NO_WINDOW must stay in config.sh (SKL-27)")
        assertTrue(config.contains(HEADLESS_FLAG), "the headless flag must be a named parameter")
    }

    @Тогда("безоконный запуск остался поведением по умолчанию")
    fun theHeadlessRunStaysTheDefault() {
        assertTrue(start.contains(HEADLESS_FLAG), "start.sh must add the headless flag by default")
        assertTrue(start.contains(WINDOW_CONDITION), "only the window argument may skip the headless flag")
    }

    @Тогда("видимое окно включается только признаком экранного прогона")
    fun onlyTheScreenRunTurnsTheWindowOn() {
        assertTrue(start.contains(WINDOW_ARG), "start.sh must parse $WINDOW_ARG")
        val carriers = harnessFiles()
            .filter { it.readText().contains(WINDOW_ARG) }
            .map { it.name }
            .sorted()
        assertEquals(
            listOf(RUN_UI_SCRIPT, START_SCRIPT),
            carriers,
            "only the screen run may pass $WINDOW_ARG; every other script stays headless",
        )
    }

    @Тогда("полный прогон механических испытаний остался безоконным")
    fun theFullMechanicsRunStaysHeadless() {
        assertFalse(runAll.contains(WINDOW_ARG), "run-all.sh must not turn the window on")
    }

    // --- Группа ui --------------------------------------------------------------

    @Когда("разработчик читает состав групп прогона на эмуляторе")
    fun theDeveloperReadsTheRunGroups() {
        config = configText()
        runTests = File(RepositoryLayout.root, "$HARNESS/run-tests.sh").readText()
    }

    @Тогда("экранные проверки идут своей группой")
    fun theScreenChecksHaveTheirOwnGroup() {
        assertTrue(runTests.contains(UI_GROUP_CASE), "$TESTS_SCRIPT must know the ui group")
        assertTrue(runTests.contains(UI_GROUP_KEY), "the ui group must take its package from config.sh")
    }

    @Тогда("группа экранных проверок называет пакет своих файлов")
    fun theUiGroupNamesThePackageOfItsFiles() {
        val packageName = valueOf(config, UI_GROUP_KEY)
        assertTrue(packageName.isNotBlank(), "$UI_GROUP_KEY must not be empty")
        val outside = appChecks.filterNot { qualifiedName(it).startsWith("$packageName.") }
        assertEquals(emptyList<String>(), outside.map { it.name }, "these files live outside $packageName")
    }

    @Тогда("группы быстрых, объёмных проверок и миграций сохранили свой состав")
    fun theBaseGroupsKeepTheirComposition() {
        assertTrue(valueOf(config, FAST_GROUP_KEY).contains(REPOSITORY_PACKAGE), "$FAST_GROUP_KEY changed")
        assertTrue(valueOf(config, HEAVY_GROUP_KEY).contains(VOLUME_CLASS), "$HEAVY_GROUP_KEY changed")
        assertTrue(valueOf(config, MIGRATION_GROUP_KEY).contains(MIGRATION_CLASS), "$MIGRATION_GROUP_KEY changed")
    }

    // --- Кадры ------------------------------------------------------------------

    @Когда("разработчик читает правила снятия кадров экрана")
    fun theDeveloperReadsTheFrameRules() {
        capture = File(RepositoryLayout.root, CAPTURE_FILE).readText()
        captureCaller = File(RepositoryLayout.root, CAPTURE_CALLER_FILE).readText()
        screenshots = File(RepositoryLayout.root, "$HARNESS/screenshots.sh").readText()
    }

    @Тогда("кадры снимаются самими проверками на устройстве")
    fun theChecksTakeTheFramesThemselves() {
        assertTrue(capture.contains(CAPTURE_TO_IMAGE), "$CAPTURE_FILE must capture the Compose window")
    }

    @Тогда("собираются с образа одной командой в каталог результатов прогона")
    fun theFramesAreCollectedIntoTheResults() {
        assertTrue(screenshots.contains("adb"), "screenshots.sh must pull the frames from the image")
        assertTrue(screenshots.contains(RESULTS_FUNCTION), "screenshots.sh must use the shared results directory")
        assertTrue(screenshots.contains(SCREENSHOT_DIR_KEY), "the frame directory must come from config.sh")
    }

    @Тогда("имя кадра называет состояние и проверку")
    fun theFrameNameCarriesStateAndCheck() {
        assertTrue(capture.contains("step"), "the frame name must carry the state")
        assertTrue(captureCaller.contains("methodName"), "the frame name must carry the check")
    }

    @Тогда("ни один кадр не сверяется с эталоном")
    fun noFrameIsComparedWithAGolden() {
        assertFalse(screenshots.contains(SHA256), "frames are evidence, not goldens: no hashes")
        assertFalse(screenshots.contains(BASELINE), "frames are evidence, not goldens: no baselines")
    }

    @Тогда("в репозиторий кадры не попадают")
    fun theFramesDoNotReachTheRepository() {
        assertTrue(
            File(RepositoryLayout.root, GITIGNORE).readText().contains("build/"),
            "the results directory under build/ must stay ignored",
        )
        assertEquals(
            emptyList<String>(),
            deviceChecks.filter { it.extension == PNG }.map { it.name },
            "no golden image may appear under src/androidTest",
        )
    }

    // --- Регистрация ------------------------------------------------------------

    @Когда("разработчик сверяет группу экранного прогона с файлами экранных проверок")
    fun theDeveloperComparesTheUiGroupWithTheFiles() {
        config = configText()
        appChecks = kotlinFiles(File(RepositoryLayout.root, "$APP_MODULE/src/$ANDROID_TEST"))
        deviceChecks = deviceCheckFiles()
    }

    @Тогда("каждый файл экранных проверок попадает в группу прогона")
    fun everyScreenCheckFileIsInAGroup() {
        assertCoveredByGroups(appChecks.filter { countTests(it) > 0 })
    }

    @Тогда("ни один файл проверок устройства не остался без своей группы")
    fun noDeviceCheckFileIsLeftOut() {
        assertCoveredByGroups(deviceChecks.filter { countTests(it) > 0 })
    }

    @Когда("разработчик сверяет состав проверок устройства после задачи")
    fun theDeveloperComparesTheDeviceChecksAfterTheTask() {
        design = File(RepositoryLayout.root, SKL24_DESIGN).readText()
        appChecks = kotlinFiles(File(RepositoryLayout.root, "$APP_MODULE/src/$ANDROID_TEST"))
        deviceChecks = deviceCheckFiles()
    }

    @Тогда("^каждый файл экранных проверок назван в docs/tasks/SKL-24/TDD.md$")
    fun everyScreenCheckFileIsNamedInTheDesign() {
        assertEquals(emptyList<String>(), appChecks.map { it.name }.filterNot { design.contains(it) })
    }

    @Тогда("проверка «каждый файл проверок устройства назван» не краснеет")
    fun thePerFileDesignCheckStaysGreen() {
        assertEquals(
            emptyList<String>(),
            deviceChecks.map { it.name }.filterNot { design.contains(it) },
            "these device checks stay without a stated reason in $SKL24_DESIGN",
        )
    }

    // --- Синтаксис и отказы -----------------------------------------------------

    @Когда("разработчик проверяет синтаксис скриптов экранного прогона")
    fun theDeveloperChecksTheScreenRunSyntax() {
        syntaxFailures = SCREEN_SCRIPTS
            .map { File(RepositoryLayout.root, "$HARNESS/$it") }
            .filterNot { shellSyntaxIsValid(it) }
            .map { it.name }
    }

    @Тогда("ни один скрипт экранного прогона не содержит синтаксической ошибки")
    fun noScreenScriptIsBroken() {
        assertEquals(emptyList<String>(), syntaxFailures, "these scripts do not parse as shell")
    }

    @Допустим("в среде сборки нет Android SDK")
    fun thereIsNoAndroidSdk() = Unit

    @Когда("оператор запускает экранный прогон на машине без SDK")
    fun theOperatorRunsWithoutAnSdk() {
        val emptySdk = File.createTempFile("skl96-no-sdk", "").apply {
            delete()
            mkdirs()
        }
        val process = ProcessBuilder(
            "bash",
            File(RepositoryLayout.root, "$HARNESS/$RUN_UI_SCRIPT").absolutePath,
        ).redirectErrorStream(true)
            .apply {
                environment()["ANDROID_SDK_ROOT"] = emptySdk.absolutePath
                environment().remove("ANDROID_HOME")
                environment()["PATH"] = "/usr/bin:/bin"
            }
            .start()
        sandboxOutput = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(SANDBOX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
        sandboxExitCode = process.exitValue()
    }

    @Тогда("команда экранного прогона отказывается")
    fun theScreenRunRefuses() {
        assertTrue(sandboxExitCode != 0, "the run must refuse without an SDK, output was: $sandboxOutput")
    }

    @Тогда("сообщение называет, чего в SDK не хватает, и где про это прочитать")
    fun theRefusalNamesWhatIsMissing() {
        assertTrue(sandboxOutput.contains("SDK"), "the refusal must name the SDK: $sandboxOutput")
        assertTrue(
            sandboxOutput.contains(HARNESS_README) || sandboxOutput.contains(ANDROID_SDK_ROOT),
            "the refusal must say where to read how to set the SDK: $sandboxOutput",
        )
    }

    @Допустим("образ матрицы ещё не создан")
    fun theImageDoesNotExistYet() = Unit

    @Когда("оператор запускает экранный прогон на машине без образа")
    fun theOperatorRunsWithoutAnImage() {
        runUi = File(RepositoryLayout.root, "$HARNESS/$RUN_UI_SCRIPT").readText()
    }

    @Тогда("прогон сначала создаёт образ")
    fun theRunCreatesTheImageFirst() {
        val create = runUi.indexOf("$AVD_SCRIPT\" create")
        val launch = runUi.indexOf("$START_SCRIPT\"")
        assertTrue(create >= 0, "run-ui.sh must create the image by $AVD_SCRIPT")
        assertTrue(launch >= 0, "run-ui.sh must launch the image by $START_SCRIPT")
        assertTrue(create < launch, "the image must be created before the launch")
    }

    @Тогда("при неудаче называет, какого пакета не хватило")
    fun theFailureNamesTheMissingPackage() {
        val avd = File(RepositoryLayout.root, "$HARNESS/$AVD_SCRIPT").readText()
        assertTrue(avd.contains(PACKAGE_FAILURE), "$AVD_SCRIPT must name the package it could not install")
    }

    // --- Граница ----------------------------------------------------------------

    @Тогда("в нём сказано, что эмулятор — AOSP, а не EMUI")
    fun theBoundaryNamesAosp() {
        documents().forEach { text ->
            assertTrue(text.contains(AOSP), "the boundary must name AOSP")
        }
    }

    @Тогда("в нём сказано, что рендер EMUI, звук и экран блокировки остаются эталонному Huawei")
    fun theBoundaryNamesWhatStaysOnTheDevice() {
        val joint = documents().joinToString("\n")
        listOf(EMUI, HUAWEI, SOUND, LOCKSCREEN).forEach { word ->
            assertTrue(joint.contains(word), "the boundary must name «$word»")
        }
    }

    @Тогда("в нём сказано, что матрица НФТ-40 и гейт НАП-3 прогоном не закрываются")
    fun theBoundaryNamesTheOpenGate() {
        val joint = documents().joinToString("\n")
        assertTrue(joint.contains(DEVICE_MATRIX), "the boundary must name $DEVICE_MATRIX")
        assertTrue(joint.contains(GATE), "the boundary must name $GATE")
        assertTrue(
            NOT_CLOSED.containsMatchIn(joint),
            "the boundary must say the device matrix and the gate stay open",
        )
    }

    // --- Чтение репозитория -----------------------------------------------------

    private fun configText(): String = File(RepositoryLayout.root, "$HARNESS/config.sh").readText()

    private fun documents(): List<String> = listOf(
        runbook.ifBlank { File(RepositoryLayout.root, "$HARNESS/README.md").readText() },
        File(RepositoryLayout.root, SKL96_DESIGN).readText(),
    )

    private fun harnessFiles(): List<File> = File(RepositoryLayout.root, HARNESS).listFiles()
        ?.filter { it.isFile && (it.name.endsWith(".sh") || it.name.endsWith(".py")) }
        ?.sortedBy { it.name }
        .orEmpty()

    private fun deviceCheckFiles(): List<File> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.flatMap { module -> kotlinFiles(File(module, "src/$ANDROID_TEST")) }
        .orEmpty()

    private fun kotlinFiles(directory: File): List<File> = if (!directory.isDirectory) {
        emptyList()
    } else {
        directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.name }.toList()
    }

    private fun goldenFiles(): List<File> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.map { File(it, "screenshots") }
        ?.filter { it.isDirectory }
        ?.flatMap { directory -> directory.walkTopDown().filter { it.extension == PNG }.toList() }
        .orEmpty()

    private fun assertCoveredByGroups(files: List<File>) {
        val packages = GROUP_PACKAGE_KEYS.flatMap { key -> valueOf(configText(), key).split(",") }
        val classes = GROUP_CLASS_KEYS.flatMap { key -> valueOf(configText(), key).split(",") }
        val uncovered = files
            .filterNot { file ->
                val qualified = qualifiedName(file)
                classes.contains(qualified) || packages.any { it.isNotBlank() && qualified.startsWith("$it.") }
            }
            .map { it.name }
        assertEquals(emptyList<String>(), uncovered, "these device checks fall out of every run group")
    }

    private fun qualifiedName(file: File): String {
        val declared = PACKAGE_DECLARATION.find(file.readText())?.groupValues?.get(1).orEmpty()
        return "$declared.${file.nameWithoutExtension}"
    }

    private fun countTests(file: File): Int = TEST_ANNOTATION.findAll(file.readText()).count()

    private fun valueOf(text: String, key: String): String =
        Regex("""$key="([^"]*)"""").find(text)?.groupValues?.get(1).orEmpty()

    private fun shellSyntaxIsValid(file: File): Boolean {
        val process = ProcessBuilder("bash", "-n", file.absolutePath)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText()
        return process.waitFor() == 0
    }

    private companion object {
        const val HARNESS = "tools/emulator"
        const val APP_MODULE = "app"
        const val ANDROID_TEST = "androidTest"
        const val TEST_DEBUG = "testDebug"
        const val PNG = "png"
        const val SKL24_DESIGN = "docs/tasks/SKL-24/TDD.md"
        const val SKL96_DESIGN = "docs/tasks/SKL-96/TDD.md"
        const val GITIGNORE = ".gitignore"

        const val RUN_UI_SCRIPT = "run-ui.sh"
        const val START_SCRIPT = "start.sh"
        const val AVD_SCRIPT = "avd.sh"
        const val TESTS_SCRIPT = "run-tests.sh"
        const val HARNESS_README = "README.md"

        const val COMPOSE_ACTIVITY_RULE = "createAndroidComposeRule<MainActivity>"
        const val COMPOSE_CONVENTION =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/AndroidComposeConventionPlugin.kt"
        const val SCREEN_CONVENTION =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/ScreenTestsConventionPlugin.kt"
        const val UI_TEST_JUNIT4 = "androidx-compose-ui-test-junit4"
        const val VERSION_CATALOG = "gradle/libs.versions.toml"
        const val CAPTURE_FILE = "$APP_MODULE/src/androidTest/kotlin/ru/stohastika/sklyanka/screen/ScreenCapture.kt"
        const val CAPTURE_CALLER_FILE =
            "$APP_MODULE/src/androidTest/kotlin/ru/stohastika/sklyanka/screen/DeviceScreenTest.kt"
        const val CAPTURE_TO_IMAGE = "captureToImage"
        const val ROBOLECTRIC_QUALIFIERS = "@Config(qualifiers"
        const val ROBORAZZI_VERIFY = "roborazzi.test.verify"

        const val NO_WINDOW = "-no-window"
        const val HEADLESS_FLAG = "SKL_HEADLESS_FLAG"
        const val WINDOW_ARG = "--window"
        const val WINDOW_CONDITION = "window == 0"
        const val CONNECTED_TASK = "connectedDebugAndroidTest"
        const val AM_INSTRUMENT = "am instrument"
        const val TEST_RESULTS_DIR = "androidTest-results"
        const val RESULTS_DIR_KEY = "SKL_RESULTS_DIR"
        const val RESULTS_FUNCTION = "skl_results_dir"
        const val SCREENSHOT_DIR_KEY = "SKL_UI_SCREENSHOT_DIR"
        const val UI_GROUP_CASE = "ui)"
        const val UI_GROUP_KEY = "SKL_GROUP_UI_PACKAGES"
        const val FAST_GROUP_KEY = "SKL_GROUP_FAST_PACKAGES"
        const val HEAVY_GROUP_KEY = "SKL_GROUP_HEAVY_CLASSES"
        const val MIGRATION_GROUP_KEY = "SKL_GROUP_MIGRATION_CLASSES"
        const val REPOSITORY_PACKAGE = "ru.stohastika.sklyanka.data.repository"
        const val VOLUME_CLASS = "VolumeTest"
        const val MIGRATION_CLASS = "MigrationTest"

        const val SHA256 = "sha256"
        const val BASELINE = "baseline"

        const val AOSP = "AOSP"
        const val EMUI = "EMUI"
        const val HUAWEI = "Huawei"
        const val SOUND = "звук"
        const val LOCKSCREEN = "экран блокировки"
        const val DEVICE_MATRIX = "НФТ-40"
        const val GATE = "НАП-3"
        const val PACKAGE_FAILURE = "не удалось установить"
        const val ANDROID_SDK_ROOT = "ANDROID_SDK_ROOT"

        const val SANDBOX_TIMEOUT_SECONDS = 30L

        val SCREEN_SCRIPTS = listOf("run-ui.sh", "screenshots.sh")
        val FORBIDDEN_GROUPS = listOf(
            "com.google.android.gms",
            "com.google.firebase",
            "com.google.android.play",
        )
        val GROUP_PACKAGE_KEYS = listOf("SKL_GROUP_FAST_PACKAGES", "SKL_GROUP_DATA_PACKAGES")
        val GROUP_CLASS_KEYS = listOf("SKL_GROUP_HEAVY_CLASSES", "SKL_GROUP_MIGRATION_CLASSES")

        val TEST_ANNOTATION = Regex("""(?m)^\s*@Test\b""")
        val PACKAGE_DECLARATION = Regex("""(?m)^package\s+([\w.]+)""")
        val NOT_CLOSED = Regex("[Нн][Ее] закрыва\\w*|НЕ закрывается|не покрыва\\w*")
    }
}

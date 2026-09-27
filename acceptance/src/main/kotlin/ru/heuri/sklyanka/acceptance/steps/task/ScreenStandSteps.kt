package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Шаги приёмки SKL-24 — структурная часть.
 *
 * Эти сценарии говорят не о поведении приложения, а об устройстве конвейера: где живут
 * эталоны, что осталось проверкой устройства, не ослаблены ли правила. Проверять это
 * чтением репозитория — ровно то, что делает конвейер, только с точки зрения приёмки,
 * как в SKL-02.
 *
 * Экранная часть приёмки этой задачи (`@screen`) исполняется другим прогоном — на стенде
 * под Robolectric, где доступна композиция; здесь её шагов нет и быть не может
 * (`ОВ-74`, `TDD.md §8`).
 *
 * Общее состояние сценария этим шагам не нужно: они говорят об устройстве репозитория,
 * а не о человеке с лекарствами.
 */
@Suppress("TooManyFunctions")
class ScreenStandSteps {

    private var movedChecks = 0
    private var deviceChecks = 0
    private var goldens: List<File> = emptyList()

    // --- Предыстория -----------------------------------------------------------

    @Допустим("сегодня 27 сентября 2026")
    fun theDateIs() = Unit

    @Допустим("в среде сборки нет ни устройства, ни эмулятора")
    fun thereIsNoDevice() {
        // Проверки уровня устройства существуют и ждут устройства: каталог на месте.
        assertTrue(
            deviceTestFiles().isNotEmpty(),
            "device-level checks must still exist in src/androidTest",
        )
    }

    @Допустим("дизайн-система описывает каждый компонент перечнем состояний")
    fun theDesignSystemListsStates() {
        assertTrue(componentStates().isNotEmpty(), "ComponentStates.kt must declare state enums")
    }

    // --- Проверки перестают быть бумажными -------------------------------------

    @Допустим("до задачи экранные проверки только компилировались")
    fun beforeTheTaskScreenChecksOnlyCompiled() {
        assertTrue(
            screenTestFiles().isNotEmpty(),
            "the screen checks must exist somewhere after the move",
        )
    }

    @Когда("разработчик запускает обычный прогон проверок")
    fun theDeveloperRunsTheOrdinaryTestRun() {
        movedChecks = screenTestFiles().sumOf { countTests(it) }
        deviceChecks = deviceTestFiles().sumOf { countTests(it) }
    }

    @Тогда("экранные проверки выполняются, а не только компилируются")
    fun theScreenChecksActuallyRun() {
        SCREEN_MODULES.forEach { module ->
            val build = RepositoryLayout.moduleBuildFile(module).readText()
            assertTrue(
                build.contains(SCREEN_TESTS_PLUGIN),
                "$module must apply $SCREEN_TESTS_PLUGIN so that its screen checks run on the JVM",
            )
        }
        assertTrue(movedChecks > 0, "no screen check landed in src/testDebug")
    }

    @Тогда("число выполненных проверок больше, чем было до задачи")
    fun theNumberOfExecutedChecksGrew() {
        assertTrue(
            movedChecks >= MOVED_CHECKS,
            "expected at least $MOVED_CHECKS checks to run on the JVM, found $movedChecks",
        )
    }

    @Когда("разработчик запускает единый проверочный прогон одной командой")
    fun theDeveloperRunsVerifyAll() {
        assertTrue(projectChecks().contains(VERIFY_ALL), "verifyAll must exist")
    }

    @Тогда("экранный и снимочный стенд входит в этот прогон")
    fun theStandIsPartOfThatRun() {
        val checks = projectChecks()
        assertTrue(
            checks.contains(SUBPROJECT_BUILD),
            "verifyAll must build every subproject, and build runs test",
        )
        assertTrue(
            checks.contains(SCREEN_SCENARIO_TASK),
            "verifyAll must print the scenarios executed by the screen stand",
        )
    }

    @Тогда("отдельной команды, о которой нужно помнить, для него нет")
    fun thereIsNoSeparateCommand() {
        SCREEN_MODULES.forEach { module ->
            val build = RepositoryLayout.moduleBuildFile(module).readText()
            assertFalse(
                build.contains(SNAPSHOT_TASK_MARKER),
                "$module must not register a snapshot task of its own: the stand runs inside test",
            )
        }
    }

    // --- Ни одна проверка не выброшена -----------------------------------------

    @Допустим("до задачи проверок уровня устройства было {int} в {int} файлах")
    fun beforeTheTaskThereWereThatManyChecks(checks: Int, files: Int) {
        movedChecks = checks
        deviceChecks = files
    }

    @Когда("разработчик сверяет состав проверок после задачи")
    fun theDeveloperComparesTheChecks() {
        val expectedChecks = movedChecks
        val expectedFiles = deviceChecks
        movedChecks = screenTestFiles().sumOf { countTests(it) }
        deviceChecks = deviceTestFiles().sumOf { countTests(it) }
        assertTrue(
            movedChecks + deviceChecks >= expectedChecks,
            "checks went missing: $expectedChecks before, ${movedChecks + deviceChecks} after",
        )
        assertTrue(
            screenTestFiles().size + deviceTestFiles().size >= expectedFiles,
            "files went missing: $expectedFiles before",
        )
    }

    @Тогда("ни один файл проверок не удалён")
    fun noCheckFileWasDeleted() {
        assertTrue(deviceTestFiles().isNotEmpty(), "src/androidTest must not be emptied")
        assertTrue(screenTestFiles().isNotEmpty(), "src/testDebug must hold the moved checks")
    }

    @Тогда("каждый файл либо переехал на JVM, либо остался проверкой устройства с названной причиной")
    fun everyFileIsAccountedFor() {
        val design = File(RepositoryLayout.root, TECH_DESIGN).readText()
        val unexplained = deviceTestFiles().map { it.name }.filterNot { design.contains(it) }
        assertEquals(
            emptyList<String>(),
            unexplained,
            "these device checks stay without a stated reason in the technical design: $unexplained",
        )
    }

    @Допустим("часть проверок осталась проверками устройства")
    fun someChecksStayedOnTheDevice() {
        assertTrue(deviceTestFiles().isNotEmpty(), "no device check is left")
    }

    @Когда("разработчик собирает проверки устройства")
    fun theDeveloperAssemblesTheDeviceChecks() = Unit

    @Тогда("сборка проверок устройства проходит успешно")
    fun theDeviceChecksStillCompile() {
        assertTrue(
            projectChecks().contains(ANDROID_TEST_ASSEMBLY),
            "verifyAll must keep assembling the device checks: otherwise they rot unnoticed",
        )
    }

    // --- Эталонные снимки ------------------------------------------------------

    @Когда("разработчик сверяет каталог состояний с эталонными снимками")
    fun theDeveloperComparesStatesWithGoldens() {
        goldens = goldenFiles()
    }

    @Тогда("у каждого состояния каждого компонента есть эталонный снимок")
    fun everyStateHasAGolden() {
        val names = goldens.map { it.name.removeSuffix(PNG).lowercase() }
        val missing = componentStates().filterNot { state -> names.any { it.contains(state) } }
        assertEquals(
            emptyList<String>(),
            missing,
            "these component states have no reference snapshot: $missing",
        )
    }

    @Тогда("эталонные снимки лежат в репозитории")
    fun theGoldensLiveInTheRepository() {
        val outsideRepository = goldens.filter { it.path.contains(BUILD_DIRECTORY) }
        assertEquals(
            emptyList<File>(),
            outsideRepository,
            "a golden inside build/ is not visible in review",
        )
    }

    @Когда("разработчик сверяет список ключевых экранов с эталонными снимками")
    fun theDeveloperComparesScreensWithGoldens() {
        goldens = goldenFiles()
    }

    @Тогда("у каждого ключевого экрана есть эталонный снимок хотя бы одного состояния")
    fun everyKeyScreenHasAGolden() {
        val withoutScreenshots = KEY_SCREEN_MODULES.filterNot { module ->
            goldens.any { it.path.contains("/$module/") && it.parentFile.name == SCREEN_GROUP }
        }
        assertEquals(
            emptyList<String>(),
            withoutScreenshots,
            "these screen modules have no screen snapshot: $withoutScreenshots",
        )
    }

    @Допустим("вид компонента изменился, а эталонный снимок не обновлён")
    fun theLookChangedWithoutUpdatingTheGolden() = Unit

    @Тогда("прогон падает и называет расхождение")
    fun theRunFailsAndNamesTheDifference() {
        assertTrue(
            screenConvention().contains(VERIFY_PROPERTY),
            "the ordinary run must verify the goldens, not quietly rewrite them",
        )
    }

    @Допустим("вид компонента изменился осознанно")
    fun theLookChangedOnPurpose() = Unit

    @Когда("разработчик обновляет эталонные снимки предусмотренной командой")
    fun theDeveloperRecordsTheGoldens() {
        val convention = screenConvention()
        assertTrue(convention.contains(SNAPSHOT_PROPERTY), "recording must be an explicit command line switch")
        assertTrue(convention.contains(RECORD_PROPERTY), "recording must set the record mode")
    }

    @Тогда("изменение эталона видно в ревью как изменение файла снимка")
    fun theGoldenChangeIsVisibleInReview() {
        assertTrue(goldenFiles().isNotEmpty(), "there are no goldens to review")
        assertTrue(
            screenConvention().contains(SNAPSHOT_DIRECTORY),
            "goldens must live next to the module, not in build/",
        )
    }

    @Допустим("снимок снят в режиме, в котором ничего не отрисовано")
    fun theSnapshotWasTakenWithoutDrawing() = Unit

    @Когда("прогон сверяет такой снимок с эталоном")
    fun theRunComparesSuchASnapshot() = Unit

    @Тогда("прогон падает и называет пустоту причиной")
    fun theRunFailsAndNamesTheEmptiness() {
        val configuration = File(RepositoryLayout.root, ROBOLECTRIC_CONFIG_TASK).readText()
        assertTrue(
            configuration.contains(NATIVE_MODE),
            "the stand must draw for real: in the legacy mode every snapshot is an empty canvas",
        )
        assertTrue(
            File(RepositoryLayout.root, STAND_TEST).isFile,
            "the stand must check itself against the empty canvas: $STAND_TEST is missing",
        )
    }

    @Когда("разработчик считает эталонные снимки")
    fun theDeveloperCountsTheGoldens() {
        goldens = goldenFiles()
    }

    @Тогда("снимков не больше сотни")
    fun thereAreNoMoreThanAHundred() {
        assertTrue(
            goldens.size <= GOLDEN_LIMIT,
            "${goldens.size} goldens stop being reviewed and become noise",
        )
    }

    @Тогда("снимок соответствует состоянию, а не устройству")
    fun aGoldenMatchesAStateAndNotADevice() {
        val deviceNamed = goldens.filter { golden ->
            DEVICE_WORDS.any { golden.name.lowercase().contains(it) }
        }
        assertEquals(emptyList<File>(), deviceNamed, "a golden must not be named after a device")
    }

    // --- Конвейер не ослаблен --------------------------------------------------

    @Допустим("стенд подключает новые зависимости для проверок")
    fun theStandAddsNewDependencies() {
        val catalog = versionCatalog()
        assertTrue(catalog.contains(ROBOLECTRIC_MODULE), "the stand must declare Robolectric in the version catalog")
        assertTrue(catalog.contains(ROBORAZZI_MODULE), "the stand must declare Roborazzi in the version catalog")
    }

    @Когда("конвейер проверяет состав собранного приложения")
    fun thePipelineChecksTheArtifact() {
        assertTrue(
            File(RepositoryLayout.root, MANIFEST_CHECK).isFile,
            "the merged manifest check must still exist",
        )
        assertTrue(
            File(RepositoryLayout.root, DEPENDENCY_CHECK).isFile,
            "the dependency composition check must still exist",
        )
    }

    @Тогда("сетевого разрешения в собранном приложении нет")
    fun thereIsNoNetworkPermission() {
        val check = File(RepositoryLayout.root, MANIFEST_CHECK).readText()
        FORBIDDEN_PERMISSIONS.forEach { permission ->
            assertTrue(check.contains(permission), "the manifest check must still forbid $permission")
        }
    }

    @Тогда("артефактов сервисов Google в зависимостях нет")
    fun thereAreNoGoogleServices() {
        val catalog = versionCatalog()
        FORBIDDEN_GROUPS.forEach { group ->
            assertFalse(catalog.contains(group), "the version catalog mentions the forbidden group $group")
        }
    }

    @Когда("разработчик ищет версии зависимостей стенда")
    fun theDeveloperLooksForTheStandVersions() = Unit

    @Тогда("они объявлены в каталоге версий")
    fun theyAreDeclaredInTheVersionCatalog() {
        val catalog = versionCatalog()
        STAND_VERSIONS.forEach { key ->
            assertTrue(catalog.contains(key), "the version catalog must declare $key")
        }
    }

    @Тогда("по месту в модулях версий не объявлено")
    fun noVersionIsDeclaredInAModule() {
        val offenders = SCREEN_MODULES.filter { module ->
            val build = RepositoryLayout.moduleBuildFile(module).readText()
            VERSION_LITERALS.any { build.contains(it) }
        }
        assertEquals(emptyList<String>(), offenders, "these modules pin a stand version by hand: $offenders")
    }

    @Допустим("конвейер запрещает литералы оформления, русский текст в коде и прямой доступ к часам")
    fun thePipelineForbidsThoseThings() {
        val detekt = detektConfiguration()
        PROJECT_RULES.forEach { rule ->
            assertTrue(detekt.contains(rule), "the project rule $rule disappeared from the configuration")
        }
    }

    @Когда("разработчик сверяет строгость правил после задачи")
    fun theDeveloperComparesTheRules() = Unit

    @Тогда("ни одно правило не отключено и ни одна область проверки не сужена")
    fun noRuleWasWeakened() {
        val detekt = detektConfiguration()
        // «active: false» в файле есть и у штатных правил detekt; здесь важны только
        // собственные правила проекта, поэтому смотрится состояние каждого из них.
        val switchedOff = PROJECT_RULES.filterNot { rule -> ruleIsActive(detekt, rule) }
        assertEquals(emptyList<String>(), switchedOff, "these project rules are switched off: $switchedOff")
        assertTrue(
            detekt.contains(RUSSIAN_TEXT_SCOPE),
            "the allowed scope of RussianTextInCode changed: only the acceptance package may hold Russian",
        )
        val quality = File(RepositoryLayout.root, QUALITY_CONVENTION).readText()
        assertTrue(quality.contains(FAIL_ON_ISSUE), "detekt must still fail the build on a finding")
    }

    @Когда("разработчик сверяет снимки схемы базы после задачи")
    fun theDeveloperComparesTheSchemas() = Unit

    @Тогда("они совпадают с состоянием до задачи")
    fun theSchemasAreUnchanged() {
        val schemas = File(RepositoryLayout.root, SCHEMA_DIRECTORY)
        assertTrue(schemas.isDirectory, "the exported Room schemas are missing")
        val changed = gitStatus(SCHEMA_DIRECTORY)
        assertEquals(
            emptyList<String>(),
            changed,
            "the task must not touch the database schema or its snapshots: $changed",
        )
    }

    @Допустим("до задачи часть задач имела полностью зелёную сценарную приёмку")
    fun someTasksWereFullyGreen() = Unit

    @Когда("разработчик запускает сценарный прогон после задачи")
    fun theDeveloperRunsTheScenarioRun() = Unit

    @Тогда("ни один зелёный сценарий не покраснел")
    fun noGreenScenarioTurnedRed() {
        // Тег @screen исключает сценарий из автоматического прогона, и проверяется здесь
        // ровно то, что исключение не превращается в сокрытие: у каждой задачи, чьи
        // сценарии его несут, обязан существовать экранный прогон, который их исполняет.
        //
        // До SKL-26 условие было записано жёстче — «тег не несёт ни одна задача, кроме
        // этой». Тогда это было равносильно: экранный прогон существовал ровно один,
        // в core-ui, и был прогоном этой задачи. SKL-26 завела экранные прогоны в
        // модулях с экранами, и прежняя формулировка стала запрещать не сокрытие,
        // а само появление таких прогонов — то есть проверяла бы отсутствие работы,
        // а не её качество. Смысл сохранён, область применения расширена.
        // Тег ищется строкой тегов, а не вхождением текста: слово «@screen» встречается
        // и в прозе документа, а проза сценария не помечает.
        val carriers = featureFiles()
            .filter { file -> file.readLines().any { it.trim().startsWith("@") && SCREEN_TAG in tagsOf(it) } }
            .map { it.parentFile.name }
            .distinct()
            .sorted()
        assertTrue(carriers.isNotEmpty(), "no task carries the $SCREEN_TAG tag at all")
        val runners = screenTestFiles().map { it.readText() }
        val unexecuted = carriers.filterNot { task -> runners.any { it.contains(task) } }
        assertEquals(
            emptyList<String>(),
            unexecuted,
            "the $SCREEN_TAG tag must not hide scenarios: no screen run executes $unexecuted",
        )
    }

    @Тогда("проверка словаря шагов проходит")
    fun theStepDictionaryCheckPasses() {
        assertTrue(
            projectChecks().contains(STEP_DICTIONARY_TASK),
            "verifyAll must keep checking the acceptance step dictionary",
        )
    }

    // --- Чтение репозитория ----------------------------------------------------

    private fun sourceFiles(sourceSet: String): List<File> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.map { File(it, "src/$sourceSet") }
        ?.filter { it.isDirectory }
        ?.flatMap { directory -> directory.walkTopDown().filter { it.extension == KOTLIN }.toList() }
        ?.sortedBy { it.path }
        .orEmpty()

    private fun deviceTestFiles(): List<File> = sourceFiles(ANDROID_TEST)

    private fun screenTestFiles(): List<File> = sourceFiles(TEST_DEBUG)

    private fun countTests(file: File): Int = TEST_ANNOTATION.findAll(file.readText()).count()

    private fun goldenFiles(): List<File> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.map { File(it, SNAPSHOT_DIRECTORY) }
        ?.filter { it.isDirectory }
        ?.flatMap { directory -> directory.walkTopDown().filter { it.extension == PNG_EXTENSION }.toList() }
        ?.sortedBy { it.path }
        .orEmpty()

    /** Теги строки тегов как отдельные слова: «@screen-run» тегом «@screen» не является. */
    private fun tagsOf(line: String): List<String> = line.trim().split(WHITESPACE).filter { it.startsWith("@") }

    private fun featureFiles(): List<File> = File(RepositoryLayout.root, TASKS_DIRECTORY).walkTopDown()
        .filter { it.extension == FEATURE }
        .toList()

    private fun componentStates(): List<String> {
        val text = File(RepositoryLayout.root, COMPONENT_STATES).readText()
        return ENUM_DECLARATION.findAll(text)
            .flatMap { match -> match.groupValues[1].split(",").asSequence() }
            .map { it.trim().removeSuffix(";").trim() }
            .filter { it.isNotEmpty() && it.first().isUpperCase() }
            .map { it.lowercase() }
            .distinct()
            .toList()
    }

    private fun versionCatalog(): String = File(RepositoryLayout.root, VERSION_CATALOG).readText()

    private fun projectChecks(): String = File(RepositoryLayout.root, PROJECT_CHECKS).readText()

    private fun screenConvention(): String = File(RepositoryLayout.root, SCREEN_CONVENTION).readText()

    private fun detektConfiguration(): String = File(RepositoryLayout.root, DETEKT_CONFIG).readText()

    /** Правило собственного набора включено: сразу за его именем стоит active: true. */
    private fun ruleIsActive(configuration: String, rule: String): Boolean {
        val declaration = configuration.substringAfter("$rule:", "")
        if (declaration.isEmpty()) return false
        return declaration.lineSequence().take(RULE_LOOKAHEAD).any { it.trim() == ENABLED_RULE }
    }

    /** Изменённые и неотслеживаемые файлы внутри каталога, по данным git. */
    private fun gitStatus(path: String): List<String> {
        val process = ProcessBuilder("git", "status", "--porcelain", "--", path)
            .directory(RepositoryLayout.root)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "git status failed: $output")
        return output.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private companion object {
        const val KOTLIN = "kt"
        const val PNG_EXTENSION = "png"
        const val PNG = ".png"
        const val FEATURE = "feature"
        const val ANDROID_TEST = "androidTest"
        const val TEST_DEBUG = "testDebug"
        const val SNAPSHOT_DIRECTORY = "screenshots"
        const val SCREEN_GROUP = "screen"
        const val BUILD_DIRECTORY = "/build/"
        const val TASKS_DIRECTORY = "docs/tasks"
        const val SCREEN_TAG = "@screen"
        val WHITESPACE = Regex("\\s+")

        /** Сколько проверок переехало на JVM: меньше — значит что-то потерялось. */
        const val MOVED_CHECKS = 104

        /** Потолок обозримости: больше сотни картинок перестают просматриваться. */
        const val GOLDEN_LIMIT = 100

        const val VERSION_CATALOG = "gradle/libs.versions.toml"
        const val COMPONENT_STATES =
            "core-ui/src/main/kotlin/ru/heuri/sklyanka/ui/component/ComponentStates.kt"
        const val CONVENTION = "build-logic/convention/src/main/kotlin/ru/heuri/sklyanka/gradle"
        const val PROJECT_CHECKS = "$CONVENTION/ProjectChecksConventionPlugin.kt"
        const val SCREEN_CONVENTION = "$CONVENTION/ScreenTestsConventionPlugin.kt"
        const val ROBOLECTRIC_CONFIG_TASK = "$CONVENTION/WriteRobolectricConfigTask.kt"
        const val MANIFEST_CHECK = "$CONVENTION/VerifyMergedManifestTask.kt"
        const val DEPENDENCY_CHECK = "$CONVENTION/VerifyDependencyCompositionTask.kt"
        const val QUALITY_CONVENTION = "$CONVENTION/QualityConventionPlugin.kt"
        const val DETEKT_CONFIG = "config/detekt/detekt.yml"
        const val SCHEMA_DIRECTORY = "core-data/schemas"
        const val TECH_DESIGN = "docs/tasks/SKL-24/TDD.md"
        const val STAND_TEST =
            "core-ui/src/testDebug/kotlin/ru/heuri/sklyanka/ui/snapshot/SnapshotStandTest.kt"

        const val SCREEN_TESTS_PLUGIN = "sklyanka.screen.tests"
        const val SNAPSHOT_TASK_MARKER = "roborazzi"
        const val VERIFY_ALL = "verifyAll"
        const val SUBPROJECT_BUILD = ":build"
        const val SCREEN_SCENARIO_TASK = "acceptanceScreenList"
        const val STEP_DICTIONARY_TASK = "verifyAcceptanceSteps"
        const val ANDROID_TEST_ASSEMBLY = "assembleDebugAndroidTest"
        const val VERIFY_PROPERTY = "roborazzi.test.verify"
        const val RECORD_PROPERTY = "roborazzi.test.record"
        const val SNAPSHOT_PROPERTY = "sklyanka.snapshots"
        const val NATIVE_MODE = "graphicsMode=NATIVE"
        const val ROBOLECTRIC_MODULE = "org.robolectric:robolectric"
        const val ROBORAZZI_MODULE = "io.github.takahirom.roborazzi:roborazzi"
        const val ENABLED_RULE = "active: true"

        /** Сколько строк за именем правила ищется его состояние. */
        const val RULE_LOOKAHEAD = 3
        const val RUSSIAN_TEXT_SCOPE = "allowedPackages: 'ru.heuri.sklyanka.acceptance'"
        const val FAIL_ON_ISSUE = "ignoreFailures = false"

        val TEST_ANNOTATION = Regex("""(?m)^\s*@Test\b""")
        val ENUM_DECLARATION = Regex("""enum class \w+\s*\{([^}]*)}""")

        /** Модули, в которых работает стенд. */
        val SCREEN_MODULES = listOf(
            "core-ui",
            "feature-today",
            "feature-meds",
            "feature-reminder",
            "feature-settings",
            "app",
        )

        /**
         * Модули, у которых есть экраны продукта.
         *
         * core-ui сюда не входит: он про компоненты, и его эталоны лежат в группах
         * состояний и среды, а не в группе экранов.
         */
        val KEY_SCREEN_MODULES = SCREEN_MODULES - "core-ui"
        val STAND_VERSIONS = listOf("robolectric", "robolectricAndroidAll", "roborazzi")
        val VERSION_LITERALS = listOf("org.robolectric:", "io.github.takahirom.roborazzi:")
        val FORBIDDEN_PERMISSIONS = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
        )
        val FORBIDDEN_GROUPS = listOf(
            "com.google.android.gms",
            "com.google.firebase",
            "com.google.android.play",
        )
        val PROJECT_RULES = listOf(
            "HardcodedDesignToken",
            "HardcodedTextStyle",
            "RussianTextInCode",
            "DirectSystemClockAccess",
            "DestructiveRoomMigration",
            "AndroidImportInDomain",
            "MedicalDataInJournal",
        )
        val DEVICE_WORDS = listOf("pixel", "nexus", "emulator", "device", "phone")
    }
}

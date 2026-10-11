package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.tools.DictionaryViolation
import ru.stohastika.sklyanka.acceptance.tools.SourceStepScan
import ru.stohastika.sklyanka.acceptance.tools.StepDeclaration
import ru.stohastika.sklyanka.acceptance.tools.StepDictionary
import ru.stohastika.sklyanka.acceptance.tools.StepDictionaryRules
import ru.stohastika.sklyanka.acceptance.tools.StepScopes
import java.io.File

/**
 * Шаги приёмки SKL-26.
 *
 * Эти сценарии говорят не о поведении приложения, а об устройстве словаря шагов и
 * прогонов, которые его читают. Проверять это чтением репозитория и запуском самого
 * реестра — ровно то, что делает конвейер, только с точки зрения приёмки, как в SKL-02
 * и SKL-24.
 *
 * Общее состояние сценария этим шагам не нужно: они говорят об устройстве репозитория,
 * а не о человеке с лекарствами.
 */
@Suppress("TooManyFunctions", "LargeClass")
class DistributedDictionarySteps {

    private var violations: List<DictionaryViolation> = emptyList()

    /** Выдуманные прогоны сценария; `null` — проверяется настоящий репозиторий. */
    private var invented: List<ru.stohastika.sklyanka.acceptance.tools.StepScope>? = null
    private var probe: List<StepDeclaration> = emptyList()
    private var unparsed: List<String> = emptyList()

    // --- Предыстория -----------------------------------------------------------

    @Допустим("сценарии приёмки лежат в ресурсах модуля приёмки")
    fun theScenariosLiveInModuleResources() {
        assertTrue(featureFiles().isNotEmpty(), "no acceptance document found under $FEATURES")
    }

    @Допустим("словарь шагов проверяется на дубли отдельной проверкой")
    fun theDictionaryIsCheckedByItsOwnTask() {
        assertTrue(
            acceptanceBuild().contains(VERIFY_TASK),
            "the module must declare the $VERIFY_TASK task",
        )
        assertTrue(
            acceptanceBuild().contains("dependsOn($VERIFY_TASK)"),
            "$VERIFY_TASK must be part of the module check",
        )
    }

    // --- Общий мир и словарь как артефакт --------------------------------------

    @Допустим("общий мир сценария объявлен в модуле сценарной приёмки")
    fun theSharedWorldIsDeclaredInTheAcceptanceModule() {
        assertTrue(worldFiles().isNotEmpty(), "the shared world must exist")
    }

    @Когда("разработчик смотрит, из какого набора исходников он собирается")
    fun theDeveloperLooksAtItsSourceSet() = Unit

    @Тогда("общий мир лежит в основном наборе исходников, а не в тестовом")
    fun theWorldIsInTheMainSourceSet() {
        val wrong = worldFiles().filterNot { it.path.contains(MAIN_SOURCES) }
        assertEquals(emptyList<String>(), wrong.map { it.name }, "the world must live in the main source set")
    }

    // Выражение с якорями: в «cucumber expression» косая черта означает выбор
    // из нескольких слов, и шаг «Kotlin/JVM» без якорей Cucumber читает иначе.
    @Тогда("^модуль сценарной приёмки остаётся чистым Kotlin/JVM$")
    fun theAcceptanceModuleStaysPureJvm() {
        assertTrue(acceptanceBuild().contains(JVM_CONVENTION), "the module must apply $JVM_CONVENTION")
        assertTrue(
            ANDROID_PLUGINS.none { acceptanceBuild().contains(it) },
            "the acceptance module must not apply an Android plugin",
        )
    }

    @Когда("разработчик смотрит, где объявлены шаги предметной области")
    fun theDeveloperLooksAtTheStepDeclarations() = Unit

    @Тогда("весь словарь шагов лежит в основном наборе исходников модуля сценарной приёмки")
    fun theWholeDictionaryIsInTheMainSourceSet() {
        val steps = productionSources().filter { it.path.contains(STEP_PACKAGE) }
        assertTrue(steps.isNotEmpty(), "no step declarations found at all")
        val wrong = steps.filterNot { it.path.contains(MAIN_SOURCES) }
        assertEquals(emptyList<String>(), wrong.map { it.name }, "every step must live in the main source set")
    }

    @Тогда("в тестовом наборе модуля остаются только его собственные тесты")
    fun onlyItsOwnTestsStayInTheTestSourceSet() {
        val notTests = acceptanceSources()
            .filter { it.path.contains(TEST_SOURCES) }
            .filterNot { it.name.endsWith(TEST_SUFFIX) }
        assertEquals(emptyList<String>(), notTests.map { it.name }, "the test source set must hold tests only")
    }

    @Когда("разработчик сверяет зависимости модуля сценарной приёмки")
    fun theDeveloperChecksItsDependencies() = Unit

    @Тогда("среди них нет ни одной библиотеки Android")
    fun noneOfThemIsAnAndroidLibrary() {
        val android = dependencyLines().filter { it.contains("androidx") || it.contains("libs.android") }
        assertEquals(emptyList<String>(), android, "the acceptance module must not depend on Android")
    }

    @Тогда("среди них нет имитации Android")
    fun noneOfThemIsAnAndroidSimulation() {
        val simulation = dependencyLines().filter { it.contains("robolectric") || it.contains("roborazzi") }
        assertEquals(emptyList<String>(), simulation, "the acceptance module must not depend on Robolectric")
    }

    // --- Экранный прогон -------------------------------------------------------

    @Допустим("у задачи есть сценарии, помеченные как экранные")
    fun aTaskHasScreenScenarios() {
        assertTrue(tasksTagged(SCREEN_TAG).isNotEmpty(), "no task carries the $SCREEN_TAG tag")
    }

    @Когда("разработчик ищет прогон, который их исполняет")
    fun theDeveloperLooksForTheRunThatExecutesThem() = Unit

    @Тогда("прогон объявлен в модуле, где есть нужные экраны")
    fun theRunIsDeclaredInTheModuleWithTheScreens() {
        val runners = screenRunners()
        assertTrue(runners.isNotEmpty(), "no screen run is declared in any module")
        val unexecuted = tasksTagged(SCREEN_TAG).filterNot { task -> runners.any { it.readText().contains(task) } }
        assertEquals(emptyList<String>(), unexecuted, "no screen run executes $unexecuted")
    }

    @Тогда("он читает тот же файл сценариев, что и сценарный прогон")
    fun itReadsTheSameFeatureFile() {
        val runner = File(RepositoryLayout.root, SCREEN_RUN).readText()
        assertTrue(runner.contains(FEATURE_PATH_TEMPLATE), "the screen run must read $FEATURE_PATH_TEMPLATE")
    }

    @Когда("разработчик ищет копии документов приёмки в тестовых ресурсах")
    fun theDeveloperLooksForCopies() = Unit

    @Тогда("копий нет ни в одном модуле")
    fun thereAreNoCopies() {
        // Канонический дом сценариев — ресурсы модуля приёмки (SKL-111); копии в build/
        // делает processResources, и они производные, а не вторые документы приёмки.
        val copies = RepositoryLayout.root.walkTopDown()
            .filter { it.isFile && it.extension == FEATURE }
            .filterNot { it.path.contains("${File.separator}build${File.separator}") }
            .filterNot { it.path.contains("${File.separator}$FEATURES${File.separator}") }
            .map { it.path }
            .toList()
        assertEquals(emptyList<String>(), copies, "an acceptance document must exist in exactly one place")
    }

    @Допустим("сценарий помечен экранным, а шага к нему не написано")
    fun aScreenScenarioHasNoStep() = Unit

    @Когда("идёт экранный прогон")
    fun theScreenRunGoes() = Unit

    @Тогда("прогон падает")
    fun theRunFails() {
        // Cucumber 7 считает нереализованный шаг отказом, и обвязка возвращает причину,
        // а каждый прогон её проверяет: --dry-run в экранном прогоне нет.
        val runner = File(RepositoryLayout.root, SCREEN_RUN).readText()
        assertTrue(!runner.contains(DRY_RUN), "the screen run must not be a dry run")
        val unchecked = screenRunners().filterNot { it.readText().contains(ASSERTION) }
        assertEquals(emptyList<String>(), unchecked.map { it.name }, "every screen run must assert its outcome")
    }

    @Тогда("экранные прогоны всех модулей входят в этот прогон")
    fun everyScreenRunIsPartOfIt() {
        screenRunners().forEach { runner ->
            val module = moduleOf(runner)
            val build = File(RepositoryLayout.root, "$module/build.gradle.kts").readText()
            assertTrue(build.contains(SCREEN_TESTS_PLUGIN), "$module must apply $SCREEN_TESTS_PLUGIN")
        }
    }

    @Тогда("отдельной команды, о которой нужно помнить, для них нет")
    fun thereIsNoSeparateCommandForThem() {
        // Экранный прогон — обычный модульный тест: он идёт задачей test своего модуля,
        // а она входит в build, который зовёт verifyAll.
        screenRunners().forEach { runner ->
            assertTrue(runner.readText().contains(TEST_ANNOTATION), "${runner.name} must be a plain test")
        }
    }

    // --- Распределённый словарь ------------------------------------------------

    @Допустим("часть шагов объявлена вне модуля сценарной приёмки")
    fun someStepsAreDeclaredOutsideTheModule() {
        assertTrue(
            SourceStepScan.modulesWithSteps(RepositoryLayout.root, ACCEPTANCE).isNotEmpty(),
            "the dictionary must be distributed for this scenario to mean anything",
        )
    }

    @Когда("разработчик запускает проверку словаря шагов")
    fun theDeveloperRunsTheDictionaryCheck() {
        // Правило проверяется ВНУТРИ прогона, а не по общему списку: одно выражение
        // в двух непересекающихся прогонах дублем не является (TDD §6.2).
        val scopes = invented ?: realScopes()
        probe = scopes.flatMap { it.declarations }.distinct()
        violations = scopes.flatMap { StepDictionaryRules.violations(it.declarations, emptyList()) }
    }

    @Тогда("в реестр попадают объявления всех модулей")
    fun theRegistryHoldsEveryModule() {
        val modules = SourceStepScan.modulesWithSteps(RepositoryLayout.root, ACCEPTANCE)
        val seen = probe.map { it.origin.substringBefore('/') }.toSet()
        assertEquals(emptyList<String>(), modules.filterNot { it in seen }, "these modules are missing: $modules")
    }

    @Тогда("у каждого объявления назван модуль, в котором оно написано")
    fun everyDeclarationNamesItsModule() {
        val nameless = probe.filter { it.origin.isBlank() }
        assertEquals(emptyList<String>(), nameless.map { it.expression }, "a declaration without a place")
    }

    @Допустим("общий шаг объявлен в модуле сценарной приёмки")
    fun aSharedStepIsDeclaredInTheModule() {
        val shared =
            StepDeclaration(
                expression = SHARED_EXPRESSION,
                declaredIn = "$ARTIFACT: steps.shared.ProfileSteps",
                method = "theActiveProfileIs",
                origin = ARTIFACT,
            )
        invented = listOf(ru.stohastika.sklyanka.acceptance.tools.StepScope(SCREEN_RUN_NAME, listOf(shared)))
    }

    @Допустим("тот же шаг объявлен повторно в модуле экрана")
    fun theSameStepIsDeclaredAgainInAScreenModule() {
        val again = StepDeclaration(
            expression = SHARED_EXPRESSION,
            declaredIn = "$SCREEN_ORIGIN: ProfileProbe",
            method = "theActiveProfileIs",
            origin = SCREEN_ORIGIN,
        )
        // Второе объявление попадает в ТОТ ЖЕ прогон: модуль экрана видит общий артефакт.
        invented = invented.orEmpty().map { scope ->
            ru.stohastika.sklyanka.acceptance.tools.StepScope(scope.name, scope.declarations + again)
        }
    }

    @Тогда("проверка падает")
    fun theCheckFails() {
        assertTrue(violations.isNotEmpty(), "a duplicate inside one run must be a violation")
    }

    @Тогда("сообщение называет оба места объявления")
    fun theMessageNamesBothPlaces() {
        val message = violations.joinToString("\n") { it.message() }
        assertTrue(message.contains(ARTIFACT), "the message must name the artifact declaration: $message")
        assertTrue(message.contains(SCREEN_ORIGIN), "the message must name the screen declaration: $message")
    }

    @Допустим("два модуля объявляют одинаковый шаг, но их прогоны не пересекаются")
    fun twoModulesShareAnExpressionInSeparateRuns() {
        val first = StepDeclaration(SHARED_EXPRESSION, "FirstProbe", "step", origin = "core-ui/testDebug")
        val second = StepDeclaration(SHARED_EXPRESSION, "SecondProbe", "step", origin = SCREEN_ORIGIN)
        // Два прогона, каждый со своим объявлением: ровно то, как устроены экранный
        // прогон core-ui и остальные — classpath'ы не пересекаются (TDD §6.2).
        invented = listOf(
            ru.stohastika.sklyanka.acceptance.tools.StepScope("первый прогон", listOf(first)),
            ru.stohastika.sklyanka.acceptance.tools.StepScope("второй прогон", listOf(second)),
        )
    }

    @Тогда("проверка проходит")
    fun theCheckPasses() {
        assertEquals(emptyList<String>(), violations.map { it.message() }, "separate runs must not collide")
    }

    @Тогда("реестр называет, в каком прогоне участвует каждое объявление")
    fun theRegistryNamesTheRunOfEveryDeclaration() {
        val scopes = realScopes()
        assertTrue(scopes.size > 1, "there must be more than one run once the dictionary is distributed")
        scopes.forEach { scope ->
            assertTrue(scope.name.isNotBlank(), "a run without a name")
            assertTrue(scope.declarations.isNotEmpty(), "the run «${scope.name}» sees no declaration")
        }
    }

    @Допустим("шаг объявлен так, что проверка не может прочитать его выражение")
    fun aStepIsDeclaredUnreadably() {
        val module = File(RepositoryLayout.root, UNPARSED_PROBE_MODULE)
        val sourceSet = File(module, "src/$UNPARSED_PROBE_SOURCE_SET")
        val directory = File(sourceSet, "kotlin/ru/stohastika/sklyanka/acceptance/probe")
        directory.mkdirs()
        val file = File(directory, "UnparsedProbe.kt")
        file.writeText(UNPARSED_PROBE)
        try {
            unparsed = SourceStepScan.scan(RepositoryLayout.root, UNPARSED_PROBE_MODULE)
                .unparsed
                .map { DictionaryViolation.Unparsed(it).message() }
        } finally {
            // Проба живёт ровно один шаг: оставленный в дереве набор исходников
            // попал бы в следующую сборку — со всем деревом каталогов.
            file.delete()
            sourceSet.deleteRecursively()
        }
    }

    @Тогда("проверка падает и называет это объявление")
    fun theCheckFailsAndNamesThatDeclaration() {
        assertTrue(unparsed.isNotEmpty(), "an unreadable declaration must be a violation, not a skip")
        assertTrue(
            unparsed.any { it.contains("UnparsedProbe.kt") },
            "the message must name the file and the line: $unparsed",
        )
    }

    @Допустим("до задачи в словаре было {int} объявления")
    fun beforeTheTaskTheDictionaryHadThatMany(count: Int) {
        before = count
    }

    @Когда("разработчик сверяет словарь после задачи")
    fun theDeveloperComparesTheDictionary() {
        probe = declarationsOfEveryModule()
    }

    @Тогда("объявлений не меньше, чем было")
    fun thereAreNoFewerDeclarations() {
        assertTrue(probe.size >= before, "the dictionary shrank: ${probe.size} < $before")
    }

    // --- Признаки прогонов -----------------------------------------------------

    @Когда("разработчик читает сводку сценарного прогона")
    fun theDeveloperReadsTheRunSummary() = Unit

    @Тогда("«шаг не написан» отличается от «шагу нужен экран»")
    fun undefinedDiffersFromScreen() {
        assertTrue(automaticTags().contains("not $SCREEN_TAG"), "the automatic run must exclude $SCREEN_TAG")
    }

    @Тогда("«шагу нужен экран» отличается от «шагу нужна платформа»")
    fun screenDiffersFromDevice() {
        assertTrue(automaticTags().contains("not $DEVICE_TAG"), "the automatic run must exclude $DEVICE_TAG")
        assertTrue(acceptanceBuild().contains(SCREEN_LIST_TASK), "the $SCREEN_TAG group needs its own report")
        assertTrue(acceptanceBuild().contains(DEVICE_LIST_TASK), "the $DEVICE_TAG group needs its own report")
    }

    @Тогда("«шагу нужна платформа» отличается от «нужен человек или перезагрузка»")
    fun deviceDiffersFromManual() {
        assertTrue(automaticTags().contains("not $MANUAL_TAG"), "the automatic run must exclude $MANUAL_TAG")
        assertTrue(acceptanceBuild().contains(CHECKLIST_TASK), "the $MANUAL_TAG group needs its own report")
    }

    @Допустим("есть сценарии, которым нужна настоящая платформа без перезагрузки")
    fun thereAreDeviceOnlyScenarios() {
        assertTrue(tasksTagged(DEVICE_TAG).isNotEmpty(), "no task carries the $DEVICE_TAG tag")
    }

    @Когда("идёт сценарный прогон")
    fun theScenarioRunGoes() = Unit

    @Тогда("такие сценарии печатаются отдельным списком")
    fun theyArePrintedAsTheirOwnList() {
        assertTrue(acceptanceBuild().contains(DEVICE_LIST_TASK), "the $DEVICE_TAG group needs its own report")
    }

    @Тогда("нереализованными они не выглядят")
    fun theyDoNotLookUndefined() {
        assertTrue(automaticTags().contains("not $DEVICE_TAG"), "the automatic run must exclude $DEVICE_TAG")
    }

    @Допустим("сценарий исключён из автоматического прогона признаком")
    fun aScenarioIsExcludedByATag() = Unit

    @Тогда("сценарий назван в отчёте своей группы")
    fun theScenarioIsNamedInItsGroupReport() {
        listOf(CHECKLIST_TASK, SCREEN_LIST_TASK, DEVICE_LIST_TASK).forEach { task ->
            assertTrue(acceptanceBuild().contains(task), "$task must exist: exclusion must stay visible")
            assertTrue(projectChecks().contains(task), "$task must be part of verifyAll")
        }
    }

    @Тогда("его не видно только в одном месте — в списке пройденных")
    fun itIsOnlyMissingFromThePassedList() {
        listOf(MANUAL_TAG, SCREEN_TAG, DEVICE_TAG).forEach { tag ->
            assertTrue(automaticTags().contains("not $tag"), "the automatic run must exclude $tag")
        }
    }

    // --- Что стало исполняться -------------------------------------------------

    @Допустим("цена ошибки в профилях — отметка чужого приёма как своего")
    fun theCostOfAMistakeInProfiles() {
        assertTrue(
            File(RepositoryLayout.root, "$FEATURES/SKL-15.feature").isFile,
            "the profiles task must have an acceptance document",
        )
    }

    @Когда("идёт прогон приёмки задачи профилей")
    fun theProfilesAcceptanceRuns() {
        probe = declarationsOfEveryModule()
    }

    @Когда("идёт прогон приёмки задачи главного экрана")
    fun theTodayAcceptanceRuns() {
        probe = declarationsOfEveryModule()
    }

    @Тогда("её сценарии исполняются, а не отмечаются нереализованными")
    fun itsScenariosExecute() {
        val covered = coveredLinesOf(currentTask)
        assertTrue(covered > 0, "not a single step line of $currentTask is caught by a declaration")
    }

    @Допустим("до задачи зелены были SKL-06, SKL-07, SKL-12, SKL-24 и часть SKL-09")
    fun theseTasksWereGreenBeforeTheTask() {
        probe = declarationsOfEveryModule()
    }

    @Когда("разработчик сверяет сводку прогона после задачи")
    fun theDeveloperComparesTheSummary() {
        probe = declarationsOfEveryModule()
    }

    @Тогда("ни одно из этих чисел не уменьшилось")
    fun noneOfThoseNumbersWentDown() {
        // Строки шагов, закрытые объявлением, — то, из чего берётся зелёный прогон.
        // Числа записаны на момент закрытия SKL-26 и могут расти, но не падать. Пол SKL-07
        // снижен SKL-106: сценарии доставки на часы убраны вместе с фичей (Р-3), а не для
        // того, чтобы спрятать красный; остальной контракт зафиксирован на новой высоте.
        COVERED_LINES.forEach { (task, floor) ->
            assertTrue(
                coveredLinesOf(task) >= floor,
                "$task lost coverage: ${coveredLinesOf(task)} < $floor",
            )
        }
    }

    @Когда("разработчик смотрит, чем задача тронула документы приёмки чужих задач")
    fun theDeveloperLooksAtWhatTheTaskTouched() = Unit

    @Тогда("признаки прогона стоят только в строках тегов")
    fun theRunTagsAreOnTagLinesOnly() {
        // Проверяются шаги и заголовки сценариев: проза документа вольна называть тег
        // словами, а шаг и заголовок — это то, что задача обязалась не переписывать.
        val misplaced = featureFiles()
            .filterNot { it.nameWithoutExtension == THIS_TASK }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> SIGNIFICANT_LINE.containsMatchIn(line.trim()) }
                    .filter { (_, line) -> RUN_TAGS.any { it in line } }
                    .map { (number, line) -> "${file.nameWithoutExtension}:${number + 1}: $line" }
            }
        assertEquals(emptyList<String>(), misplaced, "a run tag inside a step or a scenario title")
    }

    @Тогда("ни в одном шаге и ни в одном заголовке сценария их нет")
    fun noStepAndNoTitleCarriesThem() = theRunTagsAreOnTagLinesOnly()

    // --- Чтение репозитория ----------------------------------------------------

    private var before: Int = 0

    /** Задача, о приёмке которой шла речь последним шагом «идёт прогон приёмки…». */
    private var currentTask: String = ""
        get() = field.ifEmpty { "SKL-15" }

    private fun featureFiles(): List<File> = File(RepositoryLayout.root, FEATURES).walkTopDown()
        .filter { it.isFile && it.extension == FEATURE }
        .sortedBy { it.path }
        .toList()

    private fun tasksTagged(tag: String): List<String> = featureFiles()
        .filter { file -> file.readLines().any { line -> line.trim().startsWith("@") && tag in tagsOf(line) } }
        .map { it.nameWithoutExtension }
        .distinct()
        .sorted()

    private fun tagsOf(line: String): List<String> = line.trim().split(WHITESPACE).filter { it.startsWith("@") }

    private fun acceptanceSources(): List<File> = File(RepositoryLayout.root, "$ACCEPTANCE/src").walkTopDown()
        .filter { it.isFile && it.extension == KOTLIN }
        .sortedBy { it.path }
        .toList()

    /** Исходники модуля без его собственных тестов: тесты живут в тестовом наборе законно. */
    private fun productionSources(): List<File> = acceptanceSources().filterNot { it.name.endsWith(TEST_SUFFIX) }

    private fun worldFiles(): List<File> = productionSources().filter { it.path.contains(WORLD_PACKAGE) }

    private fun acceptanceBuild(): String = File(RepositoryLayout.root, "$ACCEPTANCE/build.gradle.kts").readText()

    private fun projectChecks(): String = File(RepositoryLayout.root, PROJECT_CHECKS).readText()

    private fun dependencyLines(): List<String> = acceptanceBuild()
        .substringAfter("dependencies {")
        .substringBefore("\n}")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") }

    /**
     * Строка тегов автоматического прогона с раскрытыми подстановками.
     *
     * В build-файле теги названы переменными (`not ${'$'}screenTag`): проверка обязана
     * читать то же, что читает Gradle, а не текст со знаками доллара.
     */
    private fun automaticTags(): String {
        val build = acceptanceBuild()
        val lines = build.lines()
        val start = lines.indexOfFirst { it.trim().startsWith("val automaticTags") }
        if (start < 0) return ""
        val declaration = StringBuilder(lines[start])
        var index = start
        // Объявление может быть перенесено: SKL-140 добавил шестой тег, и строка перестала
        // помещаться в предел длины. Читаем её вместе с продолжениями — до строки, которая
        // закрывает строковый литерал, иначе проверка «исключение названо» видела бы пустоту.
        while (!lines[index].trimEnd().endsWith("\"")) {
            index++
            if (index >= lines.size) break
            declaration.append('\n').append(lines[index])
        }
        return TAG_VARIABLES.entries.fold(declaration.toString()) { text, (variable, declaration) ->
            val value = build.lines()
                .first { it.trim().startsWith("val $declaration") }
                .substringAfter('"').substringBefore('"')
            text.replace(variable, value)
        }
    }

    /** Обвязки экранных прогонов: тест, зовущий общий запуск сценарной приёмки. */
    private fun screenRunners(): List<File> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.map { File(it, "src/$SCREEN_SOURCE_SET") }
        ?.filter { it.isDirectory }
        ?.flatMap { directory ->
            directory.walkTopDown()
                .filter { it.isFile && it.extension == KOTLIN && it.name.endsWith(RUNNER_SUFFIX) }
                .toList()
        }
        ?.sortedBy { it.path }
        .orEmpty()

    private fun moduleOf(file: File): String =
        file.relativeTo(RepositoryLayout.root).path.substringBefore(File.separatorChar)

    private fun declarationsOfTheArtifact(): List<StepDeclaration> =
        StepDictionary.declarations(listOf(StepDictionary.ownClassesDirectory()))

    private fun declarationsOfEveryModule(): List<StepDeclaration> = realScopes().flatMap { it.declarations }.distinct()

    /** Прогоны репозитория — те же, что видит `:acceptance:verifyAcceptanceSteps`. */
    private fun realScopes(): List<ru.stohastika.sklyanka.acceptance.tools.StepScope> = StepScopes.of(
        reflected = declarationsOfTheArtifact(),
        scanned = SourceStepScan.modulesWithSteps(RepositoryLayout.root, ACCEPTANCE)
            .associateWith { SourceStepScan.scan(RepositoryLayout.root, it).declarations },
    )

    private fun linesOf(task: String) =
        StepDictionary.scenarioLines(File(RepositoryLayout.root, "$FEATURES/$task.feature"))

    private fun coveredLinesOf(task: String): Int = linesOf(task)
        .count { line -> StepDictionaryRules.matching(probe, line.text).isNotEmpty() }

    private companion object {
        const val ACCEPTANCE = "acceptance"
        const val FEATURES = "acceptance/src/main/resources/features"
        const val FEATURE = "feature"
        const val KOTLIN = "kt"
        const val MAIN_SOURCES = "/src/main/"
        const val TEST_SOURCES = "/src/test/"
        const val TEST_SUFFIX = "Test.kt"
        const val RUNNER_SUFFIX = "AcceptanceTest.kt"
        const val SCREEN_SOURCE_SET = "testDebug"
        const val WORLD_PACKAGE = "/acceptance/world/"
        const val STEP_PACKAGE = "/acceptance/steps/"
        const val JVM_CONVENTION = "sklyanka.jvm.library"
        const val SCREEN_TESTS_PLUGIN = "sklyanka.screen.tests"
        const val TEST_ANNOTATION = "@Test"
        const val ASSERTION = "assert"
        const val DRY_RUN = "--dry-run"
        const val SCREEN_RUN =
            "$ACCEPTANCE/src/main/kotlin/ru/stohastika/sklyanka/acceptance/run/ScreenAcceptanceRun.kt"
        const val FEATURE_PATH_TEMPLATE = "acceptance/src/main/resources/features/\$task.feature"
        const val PROJECT_CHECKS =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/ProjectChecksConventionPlugin.kt"
        const val VERIFY_TASK = "verifyAcceptanceSteps"
        const val CHECKLIST_TASK = "acceptanceChecklist"
        const val SCREEN_LIST_TASK = "acceptanceScreenList"
        const val DEVICE_LIST_TASK = "acceptanceDeviceList"
        const val SCREEN_TAG = "@screen"
        const val DEVICE_TAG = "@device"
        const val MANUAL_TAG = "@manual"
        const val ARTIFACT = StepScopes.ARTIFACT_ORIGIN
        const val SCREEN_ORIGIN = "app/testDebug"
        const val SHARED_EXPRESSION = "^активен профиль «(.+)»$"
        const val UNPARSED_PROBE_MODULE = ACCEPTANCE
        const val UNPARSED_PROBE_SOURCE_SET = "probe"

        /** Сколько строк шагов закрыто объявлением у задач, чья приёмка уже зелена. */
        val COVERED_LINES = mapOf(
            "SKL-06" to 179,
            "SKL-07" to 173,
            "SKL-12" to 174,
            "SKL-24" to 69,
            "SKL-09" to 97,
        )

        /** Признаки, которыми задача размечала чужие документы. */
        val RUN_TAGS = listOf(SCREEN_TAG, DEVICE_TAG)

        const val THIS_TASK = "SKL-26"
        const val SCREEN_RUN_NAME = "экранный прогон модуля"

        /** Строки, которые задача обязалась не переписывать: шаги и заголовки сценариев. */
        val SIGNIFICANT_LINE = Regex(
            "^(?:Допустим|Дано|Пусть|Если|Когда|Тогда|То|Затем|И|Но|А|Given|When|Then|And|But|" +
                "Сценарий:|Структура сценария:)\\b",
        )

        val WHITESPACE = Regex("\\s+")

        /** Плагины, появление которых означало бы Android в чистом JVM-модуле. */
        val ANDROID_PLUGINS = listOf("sklyanka.android", "com.android")

        /** Подстановки в строке тегов автоматического прогона и объявления их значений. */
        val TAG_VARIABLES = mapOf("${'$'}screenTag" to "screenTag", "${'$'}deviceTag" to "deviceTag")

        val UNPARSED_PROBE = """
            package ru.stohastika.sklyanka.acceptance.probe

            import io.cucumber.java.en.Given

            class UnparsedProbe {

                @Given(PREFIX + "проба$")
                fun theProbe() = Unit

                private companion object {
                    const val PREFIX = "^"
                }
            }
        """.trimIndent()
    }
}

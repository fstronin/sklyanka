package ru.stohastika.sklyanka.acceptance.tools

import java.io.File

/**
 * Реестр шагов приёмки и проверка «один шаг — одно объявление».
 *
 * Запускается задачей `:acceptance:verifyAcceptanceSteps` тем же способом, каким прогон
 * запускает Cucumber-CLI: тот же classpath, та же кодировка, тот же `sklyanka.repo.root`.
 *
 * Режимы:
 * - `--verify` — падает с ненулевым кодом, если внутри одного прогона одно выражение
 *   объявлено дважды, одну строку сценария ловят два разных объявления или объявление
 *   не удалось прочитать; сообщение называет обе точки объявления;
 * - `--list` — печатает реестр: выражение, место объявления, общий шаг или задачный
 *   и сколько строк каких задач он ловит. Это ответ на вопрос «а такой шаг уже есть?»
 *   до того, как написана первая строка новых шагов.
 *
 * Почему проверка отдельная, а не «Cucumber сам заметит»: Cucumber замечает второе
 * объявление той же строки только когда исполнение до этой строки доходит, а до строк
 * нереализованных сценариев оно не доходит никогда.
 *
 * **Словарь распределён по модулям (SKL-26).** Экранный шаг живёт в модуле своего экрана,
 * потому что только оттуда видны экран и его ресурсы. Поэтому объявления читаются из двух
 * источников — отражением по классам модуля сценарной приёмки и из исходников остальных
 * модулей ([SourceStepScan]) — и проверяются внутри каждого прогона ([StepScopes]).
 */
object StepDictionary {

    private const val GLUE_PACKAGE = "ru.stohastika.sklyanka.acceptance"
    private const val SHARED_PACKAGE = "$GLUE_PACKAGE.steps.shared"
    private const val CUCUMBER_ANNOTATIONS = "io.cucumber.java."
    private const val FEATURES_DIR = "acceptance/src/main/resources/features"

    @JvmStatic
    fun main(args: Array<String>) {
        val root = repositoryRoot()
        val reflected = declarations()
        val scans = SourceStepScan.modulesWithSteps(root, StepScopes.ACCEPTANCE_MODULE)
            .associateWith { module -> SourceStepScan.scan(root, module) }
        val scanned = scans.mapValues { (_, scan) -> scan.declarations }
        val unparsed = scans.values.flatMap { it.unparsed }
        val scopes = StepScopes.of(reflected, scanned)
        val all = (reflected + scanned.values.flatten()).distinct()
        val lines = scenarioLines(File(root, FEATURES_DIR))
        if (all.isEmpty()) {
            println("Объявлений шагов не найдено — проверять нечего, и это само по себе ошибка.")
            kotlin.system.exitProcess(1)
        }
        if (args.contains("--list")) println(registry(scopes, all, lines))

        // Нарушение называет прогон, в котором объявления столкнулись: без этого непонятно,
        // куда идти — словарь распределён по модулям.
        val violations = unparsed.map { DictionaryViolation.Unparsed(it).message() } +
            scopes.flatMap { scope ->
                StepDictionaryRules.violations(scope.declarations, lines)
                    .map { "[${scope.name}] " + it.message() }
            }
        if (violations.isEmpty()) {
            println(
                "Словарь шагов: нарушений нет (${all.size} объявлений в ${scopes.size} прогонах, " +
                    "${lines.size} строк шагов).",
            )
        } else {
            println("Словарь шагов: нарушений ${violations.size}.")
            violations.forEach { println("  " + it) }
        }
        if (args.contains("--verify") && violations.isNotEmpty()) kotlin.system.exitProcess(1)
    }

    /**
     * Объявления шагов из скомпилированных классов тестового набора.
     *
     * Каталоги задаются параметром, чтобы тот же реестр можно было собрать из модульного
     * теста: `java.class.path` в разных запусках устроен по-разному, а каталог классов
     * набора известен всегда.
     */
    fun declarations(roots: List<File> = classpathDirectories()): List<StepDeclaration> {
        val loader = StepDictionary::class.java.classLoader
        return roots.filter { it.isDirectory }.flatMap { dir ->
            val origin = originOf(dir)
            glueClassNames(dir).sorted().mapNotNull { name ->
                runCatching { Class.forName(name, false, loader) }.getOrNull()
            }.flatMap { type -> declarationsOf(type, origin) }
        }.distinct()
    }

    private fun declarationsOf(type: Class<*>, origin: String): List<StepDeclaration> =
        type.declaredMethods.sortedBy { it.name }.flatMap { method ->
            method.annotations.mapNotNull { annotation ->
                val annotationType = annotation.annotationClass.java
                if (!annotationType.name.startsWith(CUCUMBER_ANNOTATIONS)) return@mapNotNull null
                val expression = runCatching {
                    annotationType.getMethod("value").invoke(annotation) as? String
                }.getOrNull() ?: return@mapNotNull null
                StepDeclaration(
                    expression = expression,
                    declaredIn = "$origin: " + type.name.removePrefix("$GLUE_PACKAGE."),
                    method = method.name,
                    keyword = annotationType.simpleName,
                    shared = type.name.startsWith("$SHARED_PACKAGE."),
                    origin = origin,
                )
            }
        }

    /**
     * Модуль и набор исходников по каталогу классов.
     *
     * `…/acceptance/build/classes/kotlin/main` → `acceptance/main`. Общий артефакт
     * отличается от шагов задач именно набором, и прогонам это различие нужно.
     */
    private fun originOf(dir: File): String {
        val parts = dir.absolutePath.split(File.separatorChar)
        val build = parts.indexOf("build")
        val module = parts.getOrNull(build - 1) ?: return dir.name
        return "$module/${parts.last()}"
    }

    /** Строки шагов всех документов приёмки, с раскрытием таблиц примеров. */
    fun scenarioLines(tasks: File): List<ScenarioLine> = tasks.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".feature") }
        .sortedBy { it.path }
        .flatMap { file -> FeatureFile(file).lines() }
        .toList()

    /** Каталог классов тестового набора — тот, из которого загружен сам реестр. */
    fun ownClassesDirectory(): File = File(StepDictionary::class.java.protectionDomain.codeSource.location.toURI())

    private fun classpathDirectories(): List<File> = System.getProperty("java.class.path")
        .split(File.pathSeparatorChar)
        .map(::File)
        .filter { it.isDirectory }

    private fun glueClassNames(dir: File): List<String> = dir.walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .map { it.relativeTo(dir).path.removeSuffix(".class").replace(File.separatorChar, '.') }
        .filter { it.startsWith("$GLUE_PACKAGE.") }
        .toList()
        .distinct()

    private fun repositoryRoot(): File {
        val property = System.getProperty("sklyanka.repo.root")
        require(!property.isNullOrBlank()) { "pass -Dsklyanka.repo.root" }
        return File(property)
    }

    private fun registry(
        scopes: List<StepScope>,
        declarations: List<StepDeclaration>,
        lines: List<ScenarioLine>,
    ): String {
        val caught = declarations.associateWith { declaration ->
            lines.filter { StepDictionaryRules.matches(declaration.expression, it.text) }
        }
        val tasks = lines.map { it.task }.distinct().sorted()
        return buildString {
            appendLine(
                "Словарь шагов приёмки: ${declarations.size} объявлений " +
                    "(${declarations.count { it.shared }} общих, ${declarations.count { !it.shared }} задачных), " +
                    "${lines.size} строк шагов в ${tasks.size} документах приёмки.",
            )
            appendLine()
            appendLine("ПРОГОНЫ — область видимости словаря: дубль опасен внутри прогона")
            scopes.forEach { scope -> appendLine("  ${scope.name}: ${scope.declarations.size} объявлений") }
            appendLine()
            appendLine("ОБЩИЕ ШАГИ — предметная область, доступны сценариям любой задачи")
            declarations.filter { it.shared }.sortedBy { it.at }
                .forEach { append(describe(it, caught.getValue(it))) }
            appendLine()
            appendLine("ШАГИ ЗАДАЧ И ЭКРАНОВ — предмет ровно одной задачи")
            declarations.filterNot { it.shared }.sortedBy { it.at }
                .forEach { append(describe(it, caught.getValue(it))) }
            appendLine()
            appendLine("ПОКРЫТИЕ СТРОК ПО ЗАДАЧАМ — сколько строк шагов не ловит ни одно объявление")
            tasks.forEach { task ->
                val ofTask = lines.filter { it.task == task }
                val uncovered = ofTask.count { StepDictionaryRules.matching(declarations, it.text).isEmpty() }
                appendLine("  $task: ${ofTask.size} строк, без объявления $uncovered")
            }
        }
    }

    private fun describe(declaration: StepDeclaration, caught: List<ScenarioLine>): String {
        val tasks = caught.map { it.task }.distinct().sorted()
        val where = if (declaration.shared) "общий  " else "задачный"
        val coverage = if (tasks.isEmpty()) {
            "не ловит ни одной строки приёмки"
        } else {
            "${caught.size} строк, задачи: ${tasks.joinToString(", ")}"
        }
        return buildString {
            appendLine("  [$where] ${declaration.at} (${declaration.keyword})")
            appendLine("      ${declaration.expression}")
            appendLine("      $coverage")
        }
    }
}

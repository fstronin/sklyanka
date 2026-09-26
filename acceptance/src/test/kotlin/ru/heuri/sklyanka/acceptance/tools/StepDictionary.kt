package ru.heuri.sklyanka.acceptance.tools

import java.io.File

/**
 * Реестр шагов приёмки и проверка «один шаг — одно объявление».
 *
 * Запускается задачей `:acceptance:verifyAcceptanceSteps` тем же способом, каким прогон
 * запускает Cucumber-CLI: тот же classpath, та же кодировка, тот же `sklyanka.repo.root`.
 *
 * Режимы:
 * - `--verify` — падает с ненулевым кодом, если одно выражение объявлено дважды или одну
 *   строку сценария ловят два разных объявления; сообщение называет обе точки объявления;
 * - `--list` — печатает реестр: выражение, место объявления, общий шаг или задачный
 *   и сколько строк каких задач он ловит. Это ответ на вопрос «а такой шаг уже есть?»
 *   до того, как написана первая строка новых шагов.
 *
 * Почему проверка отдельная, а не «Cucumber сам заметит»: Cucumber замечает второе
 * объявление той же строки только когда исполнение до этой строки доходит, а до строк
 * нереализованных сценариев оно не доходит никогда.
 */
object StepDictionary {

    private const val GLUE_PACKAGE = "ru.heuri.sklyanka.acceptance"
    private const val SHARED_PACKAGE = "$GLUE_PACKAGE.steps.shared"
    private const val CUCUMBER_ANNOTATIONS = "io.cucumber.java."
    private const val TASKS_DIR = "docs/tasks"

    @JvmStatic
    fun main(args: Array<String>) {
        val root = repositoryRoot()
        val declarations = declarations()
        val lines = scenarioLines(File(root, TASKS_DIR))
        if (declarations.isEmpty()) {
            println("Объявлений шагов не найдено — проверять нечего, и это само по себе ошибка.")
            kotlin.system.exitProcess(1)
        }
        if (args.contains("--list")) println(registry(declarations, lines))

        val violations = StepDictionaryRules.violations(declarations, lines)
        if (violations.isEmpty()) {
            println("Словарь шагов: нарушений нет (${declarations.size} объявлений, ${lines.size} строк шагов).")
        } else {
            println("Словарь шагов: нарушений ${violations.size}.")
            violations.forEach { println("  " + it.message()) }
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
        return glueClassNames(roots).sorted().mapNotNull { name ->
            runCatching { Class.forName(name, false, loader) }.getOrNull()
        }.flatMap { type ->
            type.declaredMethods.sortedBy { it.name }.flatMap { method ->
                method.annotations.mapNotNull { annotation ->
                    val annotationType = annotation.annotationClass.java
                    if (!annotationType.name.startsWith(CUCUMBER_ANNOTATIONS)) return@mapNotNull null
                    val expression = runCatching {
                        annotationType.getMethod("value").invoke(annotation) as? String
                    }.getOrNull() ?: return@mapNotNull null
                    StepDeclaration(
                        expression = expression,
                        declaredIn = type.name.removePrefix("$GLUE_PACKAGE."),
                        method = method.name,
                        keyword = annotationType.simpleName,
                        shared = type.name.startsWith("$SHARED_PACKAGE."),
                    )
                }
            }
        }
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

    private fun glueClassNames(roots: List<File>): List<String> = roots
        .filter { it.isDirectory }
        .flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map { it.relativeTo(dir).path.removeSuffix(".class").replace(File.separatorChar, '.') }
                .filter { it.startsWith("$GLUE_PACKAGE.") }
                .toList()
        }
        .distinct()

    private fun repositoryRoot(): File {
        val property = System.getProperty("sklyanka.repo.root")
        require(!property.isNullOrBlank()) { "pass -Dsklyanka.repo.root" }
        return File(property)
    }

    private fun registry(declarations: List<StepDeclaration>, lines: List<ScenarioLine>): String {
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
            appendLine("ОБЩИЕ ШАГИ — предметная область, доступны сценариям любой задачи")
            declarations.filter { it.shared }.sortedBy { it.at }
                .forEach { append(describe(it, caught.getValue(it))) }
            appendLine()
            appendLine("ШАГИ ЗАДАЧ — предмет ровно одной задачи")
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

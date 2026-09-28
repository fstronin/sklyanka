package ru.heuri.sklyanka.acceptance

import java.io.File

/**
 * Доступ к файлам репозитория из шагов приёмки.
 *
 * Часть сценариев SKL-02 проверяет не поведение приложения, а устройство проекта:
 * направление зависимостей между модулями. Проверять это по build-файлам —
 * ровно то, что делает конвейер, только с точки зрения приёмки.
 */
object RepositoryLayout {

    val root: File by lazy { locateRoot() }

    fun moduleBuildFile(module: String): File = File(root, "$module/build.gradle.kts")

    /**
     * Модули проекта, на которые ссылается build-файл модуля.
     *
     * @param includeTest считать ли зависимости тестовых наборов. Проверке графа модулей
     *   (SKL-02) нужны только продуктовые зависимости: экранный прогон приёмки подключает
     *   общий артефакт `:acceptance` тестовой конфигурацией, и это разрешено правилом
     *   направления зависимостей (SKL-26, `TDD.md §4.4`). Проверке словаря шагов тестовые
     *   зависимости, наоборот, нужны: экранный прогон видит общий артефакт именно через них.
     */
    fun projectDependenciesOf(module: String, includeTest: Boolean = true): List<String> {
        val text = moduleBuildFile(module).readText()
        val scanned = if (includeTest) text else text.lines().filterNot(::isTestDependencyLine).joinToString("\n")
        return PROJECT_DEPENDENCY.findAll(scanned).map { it.groupValues[1] }.distinct().sorted().toList()
    }

    private fun isTestDependencyLine(line: String): Boolean =
        TEST_CONFIGURATIONS.any { line.trimStart().startsWith(it) }

    fun screenModules(): List<String> = root.listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("feature-") }
        ?.map { it.name }
        ?.sorted()
        .orEmpty()

    private fun locateRoot(): File {
        System.getProperty("sklyanka.repo.root")?.let { return File(it) }
        var candidate: File? = File(".").absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("Repository root not found: pass -Dsklyanka.repo.root")
    }

    private val PROJECT_DEPENDENCY = Regex("""project\("(:[A-Za-z0-9\-_]+)"\)""")

    /** Конфигурации тестовых наборов: их зависимости не входят в продуктовый граф. */
    private val TEST_CONFIGURATIONS =
        listOf("testImplementation", "testDebugImplementation", "androidTestImplementation")
}

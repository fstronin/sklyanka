package ru.heuri.sklyanka.acceptance

import java.io.File

/**
 * Доступ к файлам репозитория из шагов приёмки.
 *
 * Часть сценариев SKL-02 проверяет не поведение приложения, а устройство проекта:
 * направление зависимостей между модулями. Проверять это по build-файлам —
 * ровно то, что делает конвейер, только с точки зрения приёмки.
 */
internal object RepositoryLayout {

    val root: File by lazy { locateRoot() }

    fun moduleBuildFile(module: String): File = File(root, "$module/build.gradle.kts")

    /** Модули проекта, на которые ссылается build-файл модуля. */
    fun projectDependenciesOf(module: String): List<String> {
        val text = moduleBuildFile(module).readText()
        return PROJECT_DEPENDENCY.findAll(text).map { it.groupValues[1] }.distinct().sorted().toList()
    }

    fun screenModules(): List<String> =
        root.listFiles()
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
}

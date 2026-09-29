package ru.stohastika.sklyanka.acceptance

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    /** Значение `const val NAME` из ProjectDefaults.kt: строка (кавычки снимаются) или число. */
    fun projectDefault(name: String): String {
        val text = File(root, PROJECT_DEFAULTS).readText()
        val string = Regex("""const\s+val\s+$name\s*=\s*"([^"]*)"""").find(text)
        if (string != null) return string.groupValues[1]
        val number = Regex("""const\s+val\s+$name\s*=\s*(\d+)""").find(text)
        return number?.groupValues?.get(1) ?: error("$name is not declared in $PROJECT_DEFAULTS")
    }

    /** namespace модуля из `<module>/build.gradle.kts`. */
    fun moduleNamespace(module: String): String {
        val buildFile = moduleBuildFile(module)
        return Regex("""namespace\s*=\s*"([^"]+)"""")
            .find(buildFile.readText())
            ?.groupValues?.get(1)
            ?: error("namespace is not declared in ${buildFile.path}")
    }

    /** Значение `<string name>` из каталогов res/values всех модулей. */
    fun stringResources(name: String): List<ResourceValue> = userVisibleStrings().filter { it.name == name }

    /** Все строковые ресурсы, которые приложение показывает человеку. */
    fun userVisibleStrings(): List<ResourceValue> = root.listFiles()
        ?.filter { it.isDirectory }
        ?.flatMap { module -> stringResourceFiles(module).flatMap { readStrings(module.name, it) } }
        .orEmpty()

    /** Строковый ресурс: модуль, файл, имя и значение. */
    data class ResourceValue(val module: String, val file: File, val name: String, val value: String)

    private fun stringResourceFiles(module: File): List<File> {
        val src = File(module, "src")
        if (!src.isDirectory) return emptyList()
        return src.walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .filter { it.path.replace(File.separatorChar, '/').contains(VALUES_DIRECTORY) }
            .toList()
    }

    private fun readStrings(module: String, file: File): List<ResourceValue> {
        val resources = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        return (0 until resources.childNodes.length)
            .mapNotNull { index -> resources.childNodes.item(index) as? Element }
            .flatMap { element -> stringsOf(module, file, element) }
    }

    private fun stringsOf(module: String, file: File, element: Element): List<ResourceValue> {
        val name = element.getAttribute(NAME_ATTRIBUTE)
        if (name.isEmpty()) return emptyList()
        return when (element.tagName) {
            STRING_TAG -> listOf(ResourceValue(module, file, name, element.textContent.trim()))
            PLURALS_TAG, ARRAY_TAG -> {
                val items = element.getElementsByTagName(ITEM_TAG)
                (0 until items.length).map { index ->
                    ResourceValue(module, file, name, items.item(index).textContent.trim())
                }
            }
            else -> emptyList()
        }
    }

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

    private const val PROJECT_DEFAULTS =
        "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/ProjectDefaults.kt"
    private const val VALUES_DIRECTORY = "/res/values"
    private const val NAME_ATTRIBUTE = "name"
    private const val STRING_TAG = "string"
    private const val PLURALS_TAG = "plurals"
    private const val ARRAY_TAG = "string-array"
    private const val ITEM_TAG = "item"

    /** Конфигурации тестовых наборов: их зависимости не входят в продуктовый граф. */
    private val TEST_CONFIGURATIONS =
        listOf("testImplementation", "testDebugImplementation", "androidTestImplementation")
}

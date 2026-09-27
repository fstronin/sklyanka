package ru.heuri.sklyanka.acceptance.tools

import java.io.File

/**
 * Объявления шагов **других модулей**, прочитанные из исходников (SKL-26, TDD §6.1).
 *
 * Зачем не отражением, как у модуля сценарной приёмки: классы шагов модулей с экранами
 * требуют Android в classpath, и `Class.forName` на них молча вернул бы `null`. Молчание
 * здесь — худший из исходов: пропущенное объявление возвращает ровно тот дубль, от которого
 * защищает вся проверка, а дубль выключает прогон целиком, не окрашивая его в красное.
 *
 * Поэтому разбор узкий и намеренно негибкий: аннотация ключевого слова Gherkin, аргументом
 * которой стоят только строковые литералы (при необходимости склеенные `+`). Всё, что
 * выглядит объявлением шага и под этот вид не подходит, — **нарушение с файлом и строкой**,
 * а не пропуск.
 */
object SourceStepScan {

    /** Каталог исходников, внутри которого объявления шагов вообще ищутся. */
    private const val GLUE_PATH = "ru/heuri/sklyanka/acceptance"

    private const val BUILD_DIRECTORY = "build"

    /**
     * Ключевые слова Gherkin, которыми объявляется шаг.
     *
     * Русские и английские: словарь проекта пишет общие шаги английскими аннотациями
     * с русским регулярным выражением, а шаги задач — русскими (SKL-23).
     */
    private val KEYWORDS = setOf(
        "Допустим", "Дано", "Пусть", "Если", "Когда", "Тогда", "То", "Затем",
        "И", "Ктомуже", "Также", "Но", "А",
        "Given", "When", "Then", "And", "But",
    )

    /** Результат разбора одного файла: найденное и то, что разобрать не удалось. */
    data class Scan(val declarations: List<StepDeclaration>, val unparsed: List<UnparsedDeclaration>)

    /**
     * Модули, у которых есть объявления шагов вне модуля сценарной приёмки.
     *
     * @param root корень репозитория
     * @param except модуль, чьи объявления читаются отражением, а не отсюда
     */
    fun modulesWithSteps(root: File, except: String): List<String> = root.listFiles()
        ?.filter { it.isDirectory && it.name != except }
        ?.filter { glueDirectories(it).isNotEmpty() }
        ?.map { it.name }
        ?.sorted()
        .orEmpty()

    /** Объявления шагов одного модуля. */
    fun scan(root: File, module: String): Scan {
        val declarations = mutableListOf<StepDeclaration>()
        val unparsed = mutableListOf<UnparsedDeclaration>()
        glueDirectories(File(root, module)).forEach { directory ->
            val sourceSet = directory.path.substringAfter("${File.separator}src${File.separator}")
                .substringBefore(File.separatorChar)
            directory.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.path }
                .forEach { file -> read(file, module, sourceSet, declarations, unparsed) }
        }
        return Scan(declarations, unparsed)
    }

    private fun glueDirectories(module: File): List<File> {
        val sources = File(module, "src")
        if (!sources.isDirectory) return emptyList()
        return sources.listFiles()
            ?.filter { it.isDirectory && it.name != BUILD_DIRECTORY }
            ?.map { File(it, "kotlin/$GLUE_PATH") }
            ?.filter { it.isDirectory }
            ?.sortedBy { it.path }
            .orEmpty()
    }

    private fun read(
        file: File,
        module: String,
        sourceSet: String,
        declarations: MutableList<StepDeclaration>,
        unparsed: MutableList<UnparsedDeclaration>,
    ) {
        val lines = file.readLines()
        var type = file.nameWithoutExtension
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            CLASS_DECLARATION.find(trimmed)?.let { type = it.groupValues[1] }
            val keyword = annotationKeyword(trimmed) ?: return@forEachIndexed
            val argument = argumentOf(lines, index)
            val expression = literalsOf(argument)
            val at = "$module/$sourceSet: ${file.name}:${index + 1}"
            if (expression == null) {
                unparsed += UnparsedDeclaration(at, trimmed)
            } else {
                declarations += StepDeclaration(
                    expression = expression,
                    declaredIn = "$module/$sourceSet: $type",
                    method = methodAfter(lines, index),
                    keyword = keyword,
                    shared = false,
                    origin = "$module/$sourceSet",
                )
            }
        }
    }

    /** Ключевое слово аннотации, если эта строка её начинает. */
    private fun annotationKeyword(line: String): String? {
        val match = ANNOTATION.find(line) ?: return null
        val name = match.groupValues[1]
        return if (name.replace(" ", "") in KEYWORDS) name else null
    }

    /**
     * Текст аргумента аннотации: от открывающей скобки до парной ей.
     *
     * Скобки внутри строкового литерала не считаются: выражения шагов — регулярные,
     * и круглых скобок в них больше, чем в самом коде.
     */
    private fun argumentOf(lines: List<String>, from: Int): String {
        val text = StringBuilder()
        var depth = 0
        var started = false
        var inString = false
        var escaped = false
        for (index in from until lines.size) {
            for (character in lines[index]) {
                val structural = !inString
                when {
                    escaped -> escaped = false
                    inString && character == '\\' -> escaped = true
                    character == '"' -> inString = !inString
                    structural && character == '(' -> {
                        depth++
                        started = true
                        if (depth == 1) continue
                    }

                    structural && character == ')' -> {
                        depth--
                        if (depth == 0) return text.toString()
                    }
                }
                if (started && depth >= 1) text.append(character)
            }
            text.append('\n')
            if (started && depth == 0) break
        }
        return text.toString()
    }

    /**
     * Выражение шага из аргумента аннотации.
     *
     * `null` означает «разобрать не удалось» — и это отказ проверки, а не пропуск.
     */
    @Suppress("ReturnCount")
    private fun literalsOf(argument: String): String? {
        if (argument.contains(RAW_STRING)) return null
        val parts = mutableListOf<String>()
        var index = 0
        val rest = StringBuilder()
        while (index < argument.length) {
            val character = argument[index]
            if (character == '"') {
                val literal = StringBuilder()
                index++
                while (index < argument.length && argument[index] != '"') {
                    if (argument[index] == '\\' && index + 1 < argument.length) {
                        literal.append(argument[index]).append(argument[index + 1])
                        index += 2
                        continue
                    }
                    literal.append(argument[index])
                    index++
                }
                if (index >= argument.length) return null
                parts += literal.toString()
                index++
                continue
            }
            rest.append(character)
            index++
        }
        // Между литералами разрешены только склейка, запятые и пробелы: всё остальное —
        // вычисление, а вычисленное выражение проверка прочитать не может и не притворяется.
        if (parts.isEmpty() || rest.any { it !in ALLOWED_BETWEEN }) return null
        return parts.joinToString("").replace("\\\\", "\\").replace("\\\"", "\"")
    }

    /** Имя функции, к которой относится аннотация: первая строка с `fun` после неё. */
    private fun methodAfter(lines: List<String>, from: Int): String {
        for (index in from until lines.size) {
            FUNCTION_DECLARATION.find(lines[index].trim())?.let { return it.groupValues[1] }
        }
        return "?"
    }

    private const val RAW_STRING = "\"\"\""
    private val ALLOWED_BETWEEN = setOf('+', ',', ' ', '\n', '\t', '\r')
    private val ANNOTATION = Regex("""^@([A-Za-zА-Яа-яЁё ]+?)\s*\(""")
    private val CLASS_DECLARATION = Regex("""\bclass\s+([A-Za-z][A-Za-z0-9_]*)""")
    private val FUNCTION_DECLARATION = Regex("""\bfun\s+([A-Za-z][A-Za-z0-9_]*)""")
}

/** Объявление шага, выражение которого проверка прочитать не смогла. */
data class UnparsedDeclaration(val at: String, val line: String)

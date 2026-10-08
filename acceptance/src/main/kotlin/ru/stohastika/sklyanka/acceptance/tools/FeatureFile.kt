package ru.stohastika.sklyanka.acceptance.tools

import java.io.File

/**
 * Чтение строк шагов из документа приёмки.
 *
 * Полноценный разбор Gherkin здесь не нужен и был бы второй копией разбора Cucumber:
 * проверке словаря нужны строки шагов и места, где они написаны. Две вещи всё же
 * обязательны. Таблицы примеров раскрываются — иначе строка «не чаще <лимит> раз в сутки»
 * не совпала бы ни с одним выражением, и дубль под ней остался бы незамеченным. Описание
 * функции и правила пропускается — строка «И …» в прозе для человека шагом не является.
 */
internal class FeatureFile(private val file: File) {

    /** Задача сценария: ресурс назван её именем (`SKL-NN.feature`); прежний `acceptance.feature` — по каталогу. */
    private val task: String =
        if (file.nameWithoutExtension != "acceptance") {
            file.nameWithoutExtension
        } else {
            file.parentFile?.name ?: file.name
        }
    private val path: String = "${file.parentFile?.name}/${file.name}"

    private val found = mutableListOf<ScenarioLine>()
    private val pending = mutableListOf<Pair<Int, String>>()
    private val rows = mutableListOf<List<String>>()
    private var headers: List<String>? = null
    private var inExamples = false
    private var inDocString = false
    private var inSteps = false

    fun lines(): List<ScenarioLine> {
        file.readLines().forEachIndexed { index, raw -> read(index + 1, raw.trim()) }
        flush()
        return found.toList()
    }

    private fun read(number: Int, line: String) {
        when {
            line.startsWith(DOC_STRING) -> inDocString = !inDocString
            inDocString || line.isEmpty() || line.startsWith("#") -> Unit
            BLOCK_HEADER.containsMatchIn(line) -> startBlock(line)
            EXAMPLES_HEADER.containsMatchIn(line) -> inExamples = true
            line.startsWith("|") -> readTableRow(line)
            else -> readStep(number, line)
        }
    }

    private fun startBlock(line: String) {
        flush()
        inExamples = false
        inSteps = !PROSE_HEADER.containsMatchIn(line)
    }

    private fun readTableRow(line: String) {
        if (!inExamples) return
        val cells = line.trim('|').split("|").map { it.trim() }
        if (headers == null) headers = cells else rows += cells
    }

    private fun readStep(number: Int, line: String) {
        if (!inSteps) return
        val step = STEP.find(line) ?: return
        // Таблица примеров закончилась, начался следующий сценарий той же структуры.
        if (inExamples) {
            flush()
            inExamples = false
        }
        pending += number to step.groupValues[2].trim()
    }

    private fun flush() {
        pending.forEach { (number, text) -> found += expand(number, text) }
        pending.clear()
        headers = null
        rows.clear()
    }

    private fun expand(number: Int, text: String): List<ScenarioLine> {
        val outline = headers
        if (!text.contains('<') || outline == null || rows.isEmpty()) {
            return listOf(ScenarioLine(task, path, number, text))
        }
        return rows.map { row ->
            var expanded = text
            outline.forEachIndexed { index, header ->
                expanded = expanded.replace("<$header>", row.getOrElse(index) { "" })
            }
            ScenarioLine(task, path, number, expanded)
        }
    }

    private companion object {
        const val DOC_STRING = "\"\"\""

        /** Заголовки блоков: с них начинается новый сценарий, и таблица примеров закрывается. */
        val BLOCK_HEADER = Regex(
            "^(?:Функция|Функциональность|Функционал|Свойство|Предыстория|Контекст|Сценарий|" +
                "Структура сценария|Шаблон сценария|Сценарий структуры|Правило|" +
                "Feature|Background|Scenario|Scenario Outline|Scenario Template|Rule|Example):",
        )

        /** Заголовки блоков, за которыми идёт проза, а не шаги. */
        val PROSE_HEADER = Regex("^(?:Функция|Функциональность|Функционал|Свойство|Правило|Feature|Rule):")

        val EXAMPLES_HEADER = Regex("^(?:Примеры|Значения|Scenarios|Examples):")

        /**
         * Ключевые слова шага русского и английского диалектов Gherkin.
         *
         * «А» и «И» — тоже ключевые слова: строка «А человек …» это шаг, а не проза.
         */
        val STEP = Regex(
            "^(Допустим|Дано|Пусть|Если|Когда|Тогда|То|Затем|И|К тому же|Также|Но|А|" +
                "Given|When|Then|And|But|\\*)\\s+(.+)$",
        )
    }
}

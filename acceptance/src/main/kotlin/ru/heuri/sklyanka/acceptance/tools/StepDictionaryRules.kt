package ru.heuri.sklyanka.acceptance.tools

/**
 * Объявление шага: выражение и место, где оно написано.
 *
 * [origin] — модуль и набор исходников, откуда объявление прочитано («acceptance/main»,
 * «feature-today/testDebug»). После SKL-26 словарь распределён по модулям, и без места
 * сообщение о дубле не говорит, куда идти.
 */
data class StepDeclaration(
    val expression: String,
    val declaredIn: String,
    val method: String,
    val keyword: String = "",
    val shared: Boolean = false,
    val origin: String = "",
) {
    val at: String get() = "$declaredIn.$method"
}

/** Строка шага из документа приёмки — с точностью до файла и номера строки. */
data class ScenarioLine(val task: String, val file: String, val number: Int, val text: String) {
    val at: String get() = "$file:$number"
}

/**
 * Нарушение словаря шагов.
 *
 * Их два вида, и оба нужны. Одинаковые выражения Cucumber замечает сам при загрузке glue —
 * но сообщает об этом отказом запуска, который прогон до сих пор объяснял поломкой
 * документа задачи. Разные выражения, ловящие одну строку, Cucumber замечает только когда
 * исполнение доходит до этой строки, а до строки нереализованного сценария оно не доходит
 * никогда: сегодня таких сценариев 527 из 630, и дубль в них не увидел бы никто.
 */
sealed interface DictionaryViolation {

    fun message(): String

    /** Одно и то же выражение объявлено дважды. */
    data class SameExpression(val expression: String, val declarations: List<StepDeclaration>) : DictionaryViolation {
        override fun message(): String = "Шаг «$expression» объявлен ${declarations.size} раза: " +
            declarations.joinToString(", ") { it.at }
    }

    /**
     * Объявление шага, выражение которого проверка прочитать не смогла (SKL-26, TDD §6.1).
     *
     * Молчаливый пропуск такого объявления вернул бы дубль, от которого защищает вся
     * проверка: шаг был бы объявлен, а словарь о нём не знал бы.
     */
    data class Unparsed(val declaration: UnparsedDeclaration) : DictionaryViolation {
        override fun message(): String =
            "Объявление шага в ${declaration.at} проверка прочитать не смогла: «${declaration.line}». " +
                "Выражение шага обязано быть строковым литералом, при необходимости склеенным «+»."
    }

    /** Два разных выражения ловят одну и ту же строку сценария. */
    data class SameLine(val line: ScenarioLine, val declarations: List<StepDeclaration>) : DictionaryViolation {
        override fun message(): String = "Строку «${line.text}» (${line.at}) ловят ${declarations.size} объявления: " +
            declarations.joinToString(", ") { "${it.at} → «${it.expression}»" }
    }
}

/**
 * Правило словаря: один шаг — одно объявление.
 *
 * Логика отделена от чтения classpath и файлов приёмки намеренно: правило, которое
 * ни разу не срабатывало, ничего не гарантирует, а проверить его на заведомых нарушениях
 * можно только если ему можно передать выдуманный словарь.
 */
object StepDictionaryRules {

    fun violations(declarations: List<StepDeclaration>, lines: List<ScenarioLine>): List<DictionaryViolation> =
        sameExpression(declarations) + sameLine(declarations, lines)

    /** Какие объявления ловят эту строку сценария. */
    fun matching(declarations: List<StepDeclaration>, text: String): List<StepDeclaration> =
        declarations.filter { matches(it.expression, text) }

    fun matches(expression: String, text: String): Boolean = pattern(expression).containsMatchIn(text)

    private fun sameExpression(declarations: List<StepDeclaration>): List<DictionaryViolation> =
        declarations.groupBy { it.expression }
            .filterValues { it.size > 1 }
            .map { (expression, group) -> DictionaryViolation.SameExpression(expression, group.sortedBy { it.at }) }
            .sortedBy { it.expression }

    private fun sameLine(declarations: List<StepDeclaration>, lines: List<ScenarioLine>): List<DictionaryViolation> {
        val reported = mutableSetOf<List<String>>()
        val found = mutableListOf<DictionaryViolation>()
        lines.forEach { line ->
            val hits = matching(declarations, line.text)
            if (hits.size > 1) {
                // Одна и та же пара объявлений ловит десятки строк: сообщение называет
                // первую из них и не превращается в простыню на весь документ.
                val key = hits.map { it.at }.sorted()
                if (reported.add(key)) {
                    found += DictionaryViolation.SameLine(line, hits.sortedBy { it.at })
                }
            }
        }
        return found
    }

    /**
     * Выражение шага как регулярное.
     *
     * Шаги проекта пишутся регулярными выражениями с якорями — так шаг не ловит чужую
     * строку случайным вхождением. Выражение без якорей Cucumber считает своим
     * «cucumber expression» и сравнивает со строкой целиком; здесь поддержаны его
     * параметры `{int}`, `{word}`, `{string}` и `{float}` — остальное сравнивается
     * дословно, как и в самом Cucumber.
     */
    private fun pattern(expression: String): Regex = CACHE.getOrPut(expression) {
        if (expression.startsWith("^") || expression.endsWith("$")) {
            Regex(expression)
        } else {
            val literal = Regex.escape(expression)
                .replace("{int}", """\E(-?\d+)\Q""")
                .replace("{float}", """\E(-?\d*[.,]?\d+)\Q""")
                .replace("{word}", """\E(\S+)\Q""")
                .replace("{string}", """\E"([^"]*)"\Q""")
            Regex("^$literal$")
        }
    }

    private val CACHE = mutableMapOf<String, Regex>()
}

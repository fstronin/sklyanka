package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Шаги приёмки SKL-146: находки походов закрыты, а не подстроены.
 *
 * Страж читает репозиторий, а не состояние приложения: закрытие находок — обещание
 * в документах, и проверяется оно там же, где дано. Один и тот же список задач обязан
 * быть назван тремя местами — строками реестра `docs/plan.md` §4, условием MVP `docs/plan.md` §3
 * и сценариями этой задачи: иначе строка условия перестаёт быть проверяемой и
 * становится пожеланием.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно (правило RussianTextInCode разрешает пакет
 * `ru.stohastika.sklyanka.acceptance` вместе с подпапками, `config/detekt/detekt.yml`).
 */
class FindingSteps {

    private var registry = ""
    private var feature = ""

    @Когда("участник открывает реестр задач и условие MVP")
    fun theReaderOpensTheRegistryAndTheMvpCondition() {
        registry = text(PLAN)
        feature = text(FEATURE_FILE)
    }

    @Тогда("каждая находка походов названа строкой реестра")
    fun everyFindingIsNamedByItsRegistryRow() {
        assertEquals(
            FINDINGS,
            registryRowsOfFindings(),
            "находки походов обязаны быть строками реестра, каждая со ссылкой на задачу-починку",
        )
    }

    @Тогда("каждая находка названа сценарием приёмки этой задачи")
    fun everyFindingIsNamedByTheAcceptance() {
        assertEquals(
            FINDINGS,
            numbersAfter(feature, SWEPT_MARKER),
            "сценарии этой задачи обязаны называть тот же список находок",
        )
    }

    @Тогда("условие MVP называет те же находки")
    fun theMvpConditionNamesTheSameFindings() {
        assertEquals(
            FINDINGS,
            numbersAfter(mvpSection(), FINDINGS_MARKER),
            "условие MVP обязано называть те же находки, что реестр и сценарии",
        )
    }

    @Тогда("ни одна находка не снята решением вместо починки")
    fun noFindingIsDismissed() {
        val dismissed = registry.lines()
            .filter { it.startsWith(TABLE_ROW) }
            .map { row -> row.split('|').map(String::trim) }
            .filter { cells -> cells.size > STATUS_CELL && cells[1].toIntOrNull() in FINDINGS }
            .filter { cells -> cells[STATUS_CELL] == DISMISSED }
            .map { cells -> cells[1] }
        assertTrue(
            dismissed.isEmpty(),
            "находка не снимается решением: её чинят (сняты: $dismissed)",
        )
    }

    /** Место находок в условии MVP: от заголовка раздела до следующего заголовка. */
    private fun mvpSection(): String {
        val start = registry.indexOf(MVP_HEADING)
        assertTrue(start >= 0, "условие MVP обязано быть отдельным разделом плана")
        val next = registry.indexOf(SECTION_MARK, start + MVP_HEADING.length)
        return if (next < 0) registry.substring(start) else registry.substring(start, next)
    }

    /** Строки реестра, сведённые в задачу-починку: находка обязана быть названа ею. */
    private fun registryRowsOfFindings(): Set<Int> = registry.lines()
        .filter { it.startsWith(TABLE_ROW) && it.contains(FIX_MARKER) }
        .mapNotNull { row -> row.split('|').getOrNull(1)?.trim()?.toIntOrNull() }
        .filter { number -> number != FIX_TASK_NUMBER }
        .toSet()

    /** Числа находок, названные после слова-маркера: «сведены 139–145» и «находки походов (…)». */
    private fun numbersAfter(text: String, marker: String): Set<Int> {
        val at = text.indexOf(marker)
        assertTrue(at >= 0, "«$marker» обязано быть названо")
        val match = RANGE.find(text, at)
        assertTrue(match != null, "после «$marker» обязан стоять список находок")
        val (from, to) = requireNotNull(match).destructured
        return (from.toInt()..to.toInt()).toSet()
    }

    private fun text(relative: String): String = File(RepositoryLayout.root, relative).readText()

    private companion object {
        const val PLAN = "docs/plan.md"
        const val FEATURE_FILE = "acceptance/src/main/resources/features/SKL-146.feature"

        /** Находки походов SKL-140, сведённые в эту задачу (реестр `docs/plan.md` §4). */
        val FINDINGS: Set<Int> = (141..145).toSet()

        const val FIX_TASK_NUMBER = 146
        const val FIX_MARKER = "сведена в SKL-146"
        const val TABLE_ROW = "| "
        const val STATUS_CELL = 4
        const val DISMISSED = "снята"
        const val SECTION_MARK = "\n## "
        const val MVP_HEADING = "## 3. Финишная черта"
        const val FINDINGS_MARKER = "находки походов"
        const val SWEPT_MARKER = "сведены"
        val RANGE = Regex("""(\d{3})\s*[–-]\s*(\d{3})""")
    }
}

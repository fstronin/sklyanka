package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Шаги приёмки SKL-28 — структурная часть.
 *
 * Сценарий говорит не о поведении приложения, а о составе прогона: дефект языка был
 * невидим не потому, что его не искали, а потому, что весь прогон шёл с русским языком
 * телефона. Проверка, идущая с чужим языком, — и есть то, что не даёт дефекту вернуться
 * молча, и её наличие проверяется чтением репозитория.
 *
 * Поведенческая часть приёмки исполняется экранным прогоном (`@screen`) и рукой на
 * устройстве (`@manual`); её шагов здесь нет и быть не может — модуль чистый JVM.
 */
class InterfaceLanguageSteps {

    private var checks: List<String> = emptyList()

    @Когда("^разработчик читает состав прогона проверок$")
    fun theDeveloperReadsTheChecksOfTheRun() {
        checks = RepositoryLayout.root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(KOTLIN) && it.path.contains(TEST_SOURCE) }
            .filterNot { it.path.contains(BUILD) }
            .map(File::readText)
            .toList()
        assertTrue(checks.isNotEmpty(), "no checks found at all: the layout of the repository changed")
    }

    @Тогда("^в нём есть экранная проверка с языком телефона, отличным от русского$")
    fun thereIsAScreenCheckWithAForeignDeviceLanguage() {
        assertTrue(
            checks.any { FOREIGN_QUALIFIER.containsMatchIn(it) },
            "no screen check declares a device language other than Russian: the whole run goes with " +
                "a Russian phone, and that is exactly why the defect was invisible",
        )
    }

    @Тогда("^она сверяет склонения числительных набором требования$")
    fun itChecksThePluralsOfTheRequirement() {
        val numerals = checks.filter { FOREIGN_QUALIFIER.containsMatchIn(it) }
        assertTrue(
            numerals.any { text -> REQUIRED_NUMERALS.all { numeral -> text.contains(numeral) } },
            "the check with a foreign device language does not cover the numerals of NFR-44: " +
                REQUIRED_NUMERALS.joinToString(),
        )
    }

    @Тогда("^она сверяет слова внутри даты$")
    fun itChecksTheWordsInsideTheDate() {
        assertTrue(
            checks.any { FOREIGN_QUALIFIER.containsMatchIn(it) && MONTH.containsMatchIn(it) },
            "no check with a foreign device language compares a date written in Russian words",
        )
    }

    @Тогда("^в нём есть проверка, что хранимые значения не зависят от языка телефона$")
    fun thereIsACheckThatStoredValuesDoNotDependOnTheDeviceLanguage() {
        assertTrue(
            checks.any { it.contains(SET_DEFAULT) && it.contains(STORED) },
            "no check substitutes the default locale and compares a stored value",
        )
    }

    private companion object {
        const val KOTLIN = ".kt"
        const val TEST_SOURCE = "/src/test"
        const val BUILD = "/build/"

        /** Квалификатор прогона с чужим языком телефона: `en-rUS` и любой другой не русский. */
        val FOREIGN_QUALIFIER = Regex("""qualifiers\s*=\s*"(?!ru)[a-z]{2}-r[A-Z]{2}""")

        /** Набор числительных НФТ-44: три русские формы и обе ловушки. */
        val REQUIRED_NUMERALS = listOf("1", "2", "5", "11", "21", "101")

        /** Слово внутри даты: месяц по-русски. */
        val MONTH = Regex("СЕНТЯБРЯ|сентября")

        const val SET_DEFAULT = "Locale.setDefault"
        const val STORED = "SklyankaConverters"
    }
}

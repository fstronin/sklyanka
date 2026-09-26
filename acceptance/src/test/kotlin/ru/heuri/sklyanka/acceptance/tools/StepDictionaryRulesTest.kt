package ru.heuri.sklyanka.acceptance.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Проверка словаря шагов на заведомых нарушениях.
 *
 * Проверка, которая ни разу не падала, ничего не гарантирует — так же рассуждает SKL-02
 * про правила статического анализа (`verifyStaticAnalysisRules` с заведомо нарушающими
 * примерами). Здесь тот же приём: нарушение подсовывается правилу нарочно, и отдельно
 * проверяется, что после его удаления словарь снова чист.
 */
class StepDictionaryRulesTest {

    @Test
    fun `the same expression declared twice is a violation naming both points`() {
        val declarations = listOf(
            StepDeclaration(PROFILE_EXPRESSION, "steps.shared.ProfileSteps", "theActiveProfileIs", shared = true),
            StepDeclaration(PROFILE_EXPRESSION, "steps.task.StockSteps", "theActiveProfileIsAgain"),
        )

        val violations = StepDictionaryRules.violations(declarations, listOf(line(PROFILE_LINE)))

        val duplicate = violations.filterIsInstance<DictionaryViolation.SameExpression>().single()
        assertEquals(PROFILE_EXPRESSION, duplicate.expression)
        assertTrue(duplicate.message().contains("steps.shared.ProfileSteps.theActiveProfileIs"))
        assertTrue(duplicate.message().contains("steps.task.StockSteps.theActiveProfileIsAgain"))
    }

    @Test
    fun `two different expressions catching one line are a violation naming the line`() {
        val declarations = listOf(
            StepDeclaration(PROFILE_EXPRESSION, "steps.shared.ProfileSteps", "theActiveProfileIs", shared = true),
            // Написано иначе, ловит ту же строку: Cucumber заметил бы это только дойдя
            // до неё, а до строки нереализованного сценария он не доходит никогда.
            StepDeclaration("^активен профиль «Я»$", "steps.task.StockSteps", "theOwnerIsActive"),
        )

        val violations = StepDictionaryRules.violations(declarations, listOf(line(PROFILE_LINE)))

        val ambiguous = violations.filterIsInstance<DictionaryViolation.SameLine>().single()
        assertEquals(PROFILE_LINE, ambiguous.line.text)
        assertTrue(ambiguous.message().contains("SKL-07/acceptance.feature:20"), ambiguous.message())
        assertTrue(ambiguous.message().contains("theActiveProfileIs"), ambiguous.message())
        assertTrue(ambiguous.message().contains("theOwnerIsActive"), ambiguous.message())
    }

    @Test
    fun `a line caught by one declaration is not a violation`() {
        val declarations = listOf(
            StepDeclaration(PROFILE_EXPRESSION, "steps.shared.ProfileSteps", "theActiveProfileIs", shared = true),
        )

        assertTrue(StepDictionaryRules.violations(declarations, listOf(line(PROFILE_LINE))).isEmpty())
    }

    @Test
    fun `the real dictionary of the project has no duplicates`() {
        assertTrue(realDeclarations().isNotEmpty(), "the dictionary must not be empty")
        assertTrue(realLines().isNotEmpty(), "the acceptance documents must not be empty")

        val violations = StepDictionaryRules.violations(realDeclarations(), realLines())

        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message() })
    }

    @Test
    fun `a deliberate duplicate added to the real dictionary fires the rule and its removal clears it`() {
        val real = realDeclarations()
        val lines = realLines()
        val shared = real.first { it.shared }
        val deliberate = shared.copy(declaredIn = "steps.task.DeliberateDuplicateSteps", method = "theSameStepAgain")

        val withDuplicate = StepDictionaryRules.violations(real + deliberate, lines)

        assertTrue(
            withDuplicate.any { it is DictionaryViolation.SameExpression },
            "a step declared twice has to fire the rule",
        )
        assertTrue(
            withDuplicate.first().message().contains("DeliberateDuplicateSteps"),
            "the message has to name the second declaration point",
        )
        assertTrue(
            StepDictionaryRules.violations(real, lines).isEmpty(),
            "removing the duplicate has to bring the dictionary back to green",
        )
    }

    @Test
    fun `a step expression without anchors is compared to the whole line`() {
        val declaration = StepDeclaration("сегодня 3 октября 2026", "steps.task.ProjectStructureSteps", "theDateIs")

        assertTrue(StepDictionaryRules.matches(declaration.expression, "сегодня 3 октября 2026"))
        assertTrue(!StepDictionaryRules.matches(declaration.expression, "сегодня 3 октября 2026 года"))
    }

    private fun line(text: String) = ScenarioLine("SKL-07", "SKL-07/acceptance.feature", 20, text)

    private fun realDeclarations(): List<StepDeclaration> =
        StepDictionary.declarations(listOf(StepDictionary.ownClassesDirectory()))

    private fun realLines(): List<ScenarioLine> =
        StepDictionary.scenarioLines(File(RepositoryLayout.root, "docs/tasks"))

    private companion object {
        const val PROFILE_EXPRESSION = "^активен профиль «(.+)»$"
        const val PROFILE_LINE = "активен профиль «Я»"
    }
}

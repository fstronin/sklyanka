package ru.heuri.sklyanka.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * НФТ-42: все тексты интерфейса живут в strings.xml.
 *
 * Правило смотрит на кириллицу в строковом литерале — включая заглушки и превью:
 * строка, оставленная «на время» в коде, не переводится, не склоняется через plurals
 * и не находится при вычитке текстов.
 */
class RussianTextInCode(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "RussianTextInCode",
        severity = Severity.Defect,
        description = "User-facing text must live in strings.xml, not in Kotlin code (NFR-42).",
        debt = Debt.TEN_MINS,
    )

    private val allowedPackages: List<String>
        get() = valueOrDefault("allowedPackages", "").split(",").map { it.trim() }

    private val allowedFiles: List<String>
        get() = valueOrDefault("allowedFiles", "").split(",").map { it.trim() }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        super.visitStringTemplateExpression(expression)
        if (expression.inAllowedScope(allowedPackages, allowedFiles)) return
        val text = expression.text ?: return
        if (CYRILLIC.containsMatchIn(text)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Cyrillic string literal ${text.take(LITERAL_PREVIEW)} found in Kotlin code. " +
                        "Move the text to strings.xml (NFR-42).",
                ),
            )
        }
    }

    private companion object {
        const val LITERAL_PREVIEW = 40
        val CYRILLIC = Regex("[\\u0400-\\u04FF]")
    }
}

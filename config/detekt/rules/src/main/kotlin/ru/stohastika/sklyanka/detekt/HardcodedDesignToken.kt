package ru.stohastika.sklyanka.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * НФТ-47: цвет, размер шрифта, радиус и отступ берутся токеном из дизайн-системы.
 *
 * Литерал по месту — не вкусовщина: он не переезжает в тёмную среду, не масштабируется
 * вместе с остальным интерфейсом и расходится с макетом при первой же правке токена.
 */
class HardcodedDesignToken(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "HardcodedDesignToken",
        severity = Severity.Defect,
        description = "Color, size, corner radius and spacing must come from the core-ui " +
            "design tokens, not from a literal (NFR-47).",
        debt = Debt.TWENTY_MINS,
    )

    private val allowedPackages: List<String>
        get() = valueOrDefault("allowedPackages", DEFAULT_ALLOWED_PACKAGES).split(",").map { it.trim() }

    private val allowedFiles: List<String>
        get() = valueOrDefault("allowedFiles", "").split(",").map { it.trim() }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (expression.inAllowedScope(allowedPackages, allowedFiles)) return

        val selector = expression.selectorExpression?.text ?: return
        val receiver = expression.receiverExpression.text
        if (selector in DIMENSION_SELECTORS && receiver.isNumericLiteral()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Dimension literal '${expression.text}' is forbidden outside the design " +
                        "system package. Use a token from core-ui instead (NFR-47).",
                ),
            )
        }
        if (receiver == "Color" && selector.firstOrNull()?.isUpperCase() == true) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Color constant '${expression.text}' is forbidden outside the design " +
                        "system package. Use a token from core-ui instead (NFR-47).",
                ),
            )
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.inAllowedScope(allowedPackages, allowedFiles)) return

        val callee = expression.calleeExpression?.text ?: return
        if (callee in COLOR_FACTORIES) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Color literal '${expression.text.take(LITERAL_PREVIEW)}' is forbidden " +
                        "outside the design system package. Use a token from core-ui (NFR-47).",
                ),
            )
        }
    }

    private fun String.isNumericLiteral(): Boolean =
        isNotEmpty() && (first().isDigit() || (first() == '-' && length > 1 && this[1].isDigit()))

    private companion object {
        const val DEFAULT_ALLOWED_PACKAGES = "ru.stohastika.sklyanka.ui.token"
        const val LITERAL_PREVIEW = 40
        val DIMENSION_SELECTORS = setOf("dp", "sp", "em", "pt")
        val COLOR_FACTORIES = setOf("Color")
    }
}

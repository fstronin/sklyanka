package ru.heuri.sklyanka.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * НФТ-47: начертание берётся стилем из шкалы, а не собирается по месту.
 *
 * Запрет литералов цвета и размера сам по себе неполон: `TextStyle(fontSize = …)`,
 * собранный на экране, обходит шкалу типографики целиком и вместе с ней — правило
 * пары шрифтов (`docs/spec/08-design-system.md` §2.3). Заголовок, набранный
 * интерфейсным шрифтом, выглядит опечаткой вёрстки, а не ошибкой кода, и на ревью
 * его не видно.
 *
 * Правка готового стиля (`SklyankaType.Body.copy(color = …)`) правилом не
 * затрагивается: она начинается со шкалы и от неё не отрывается.
 */
class HardcodedTextStyle(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "HardcodedTextStyle",
        severity = Severity.Defect,
        description = "Text styles must come from the core-ui type scale, not be built in place (NFR-47).",
        debt = Debt.TWENTY_MINS,
    )

    private val allowedPackages: List<String>
        get() = valueOrDefault("allowedPackages", DEFAULT_ALLOWED_PACKAGES).split(",").map { it.trim() }

    private val allowedFiles: List<String>
        get() = valueOrDefault("allowedFiles", "").split(",").map { it.trim() }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.inAllowedScope(allowedPackages, allowedFiles)) return

        val callee = expression.calleeExpression?.text ?: return
        if (callee in FORBIDDEN_FACTORIES) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "'$callee(...)' builds a text style outside the design system. " +
                        "Use a style from SklyankaType instead (NFR-47).",
                ),
            )
        }
    }

    private companion object {
        const val DEFAULT_ALLOWED_PACKAGES = "ru.heuri.sklyanka.ui.token"
        val FORBIDDEN_FACTORIES = setOf("TextStyle", "SpanStyle", "ParagraphStyle")
    }
}

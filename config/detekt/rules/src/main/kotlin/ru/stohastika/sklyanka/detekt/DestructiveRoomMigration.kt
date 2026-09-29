package ru.stohastika.sklyanka.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * НФТ-9: миграции не теряют историю приёмов.
 *
 * Правило заведено до появления Room (SKL-04) намеренно: правило, заведённое
 * заранее, дешевле правила, заведённого после первого уничтожения данных.
 */
class DestructiveRoomMigration(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "DestructiveRoomMigration",
        severity = Severity.Defect,
        description = "Destructive Room migrations drop the intake history (NFR-9).",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (callee.startsWith("fallbackToDestructiveMigration")) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "'$callee' erases stored intakes. Write an explicit Room migration (NFR-9).",
                ),
            )
        }
    }
}

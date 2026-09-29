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
 * НФТ-19, НФТ-51: в журналы попадают только идентификаторы.
 *
 * Названия лекарств, дозы, дозировки и имена профилей в журнал не попадают. Это не
 * пожелание: журнал показывается на экране диагностики, копируется текстом и может быть
 * выгружен файлом, то есть покидает приложение в руках человека, который не обязан
 * понимать, что он кому-то показывает.
 *
 * Правило смотрит на вызовы журналирования и на сборку записей журнала: если в аргумент
 * пришло поле названия, дозы или имени профиля — это ошибка, а не стилистика. Проверять
 * это ревью бессмысленно: строка `medicationId = medication.name` выглядит правильной
 * ровно до того момента, когда её читает человек в отчёте.
 */
class MedicalDataInJournal(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "MedicalDataInJournal",
        severity = Severity.Defect,
        description = "Journal calls must carry identifiers only, never medication names, " +
            "doses, strengths or profile names (NFR-19).",
        debt = Debt.TWENTY_MINS,
    )

    private val journalCalls: List<String>
        get() = valueOrDefault("journalCalls", DEFAULT_JOURNAL_CALLS).split(",").map { it.trim() }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (callee !in journalCalls) return

        expression.valueArguments.forEach { argument ->
            val text = argument.text?.replace(Regex("\\s+"), "") ?: return@forEach
            val hit = FORBIDDEN_FIELDS.firstOrNull { field -> text.contains(".$field") } ?: qualifiedName(text)
            if (hit != null) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(argument),
                        "Journal call '$callee' receives '$hit': journals carry identifiers only, " +
                            "not medication names, doses or profile names (NFR-19).",
                    ),
                )
            }
        }
    }

    /**
     * `medication.name` и `profile.name` тоже запрещены.
     *
     * Отдельной проверкой, потому что просто `.name` запрещать нельзя: `event.name`
     * и `IntakeSource.AUTO.name` — служебные коды перечислений, и они в журнале законны.
     */
    private fun qualifiedName(text: String): String? = NAMED_OWNERS
        .map { owner -> "$owner.name" }
        .firstOrNull { pattern -> text.contains(pattern, ignoreCase = true) }

    private companion object {
        const val DEFAULT_JOURNAL_CALLS = "record,ReminderLogEntry,OpLogRecord"
        val FORBIDDEN_FIELDS = listOf(
            "medicationName",
            "doseAmount",
            "doseUnit",
            "strengthUnit",
            "strength",
            "profileName",
        )
        val NAMED_OWNERS = listOf("medication", "profile", "med", "drug")
    }
}

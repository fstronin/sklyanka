package ru.heuri.sklyanka.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * НФТ-48: время и часовой пояс берутся из подменяемого источника (TimeSource).
 *
 * Прямое обращение к часам делает непроверяемыми окно приёма, переход
 * pending → missed, полночь, конец курса и смену часового пояса — то есть ровно
 * те места, где ошибка стоит пропущенного приёма.
 */
class DirectSystemClockAccess(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "DirectSystemClockAccess",
        severity = Severity.Defect,
        description = "Wall clock must be read through the injectable TimeSource (NFR-48).",
        debt = Debt.TWENTY_MINS,
    )

    private val allowedPackages: List<String>
        get() = valueOrDefault("allowedPackages", "").split(",").map { it.trim() }

    private val allowedFiles: List<String>
        get() = valueOrDefault("allowedFiles", DEFAULT_ALLOWED_FILES).split(",").map { it.trim() }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (expression.inAllowedScope(allowedPackages, allowedFiles)) return

        val normalized = expression.text.replace(Regex("\\s+"), "")
        // Обращение может быть и полностью квалифицированным (java.time.LocalDate.now()),
        // поэтому ищем совпадение по границам сегментов, а не по началу строки.
        val hit = FORBIDDEN.firstOrNull { forbidden ->
            Regex("(^|\\.)" + Regex.escape(forbidden) + "(\\(|\\.|$)").containsMatchIn(normalized)
        }
        if (hit != null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Direct clock access '$hit' is forbidden. Inject TimeSource instead (NFR-48).",
                ),
            )
        }
    }

    private companion object {
        // По умолчанию файл не разрешён: разрешение выдаётся конфигурацией по пакету
        // (detekt.yml → allowedPackages), а не по имени файла (ОВ-73).
        const val DEFAULT_ALLOWED_FILES = ""
        val FORBIDDEN = listOf(
            "System.currentTimeMillis",
            "System.nanoTime",
            "Instant.now",
            "LocalDate.now",
            "LocalTime.now",
            "LocalDateTime.now",
            "ZonedDateTime.now",
            "OffsetDateTime.now",
            "ZoneId.systemDefault",
            "TimeZone.getDefault",
            "Clock.systemDefaultZone",
            "Clock.systemUTC",
            "Calendar.getInstance",
        )
    }
}

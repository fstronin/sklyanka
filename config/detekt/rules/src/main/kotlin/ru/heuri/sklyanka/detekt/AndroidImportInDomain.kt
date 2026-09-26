package ru.heuri.sklyanka.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * НФТ-46: доменный слой не знает об Android.
 *
 * Сейчас это обеспечено типом модуля (core-domain — чистый Kotlin), и правило
 * выглядит избыточным. Оно заведено на случай, когда модуль попытаются
 * превратить в Android library «ненадолго».
 */
class AndroidImportInDomain(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "AndroidImportInDomain",
        severity = Severity.Defect,
        description = "The domain layer must not depend on the Android platform (NFR-46).",
        debt = Debt.TWENTY_MINS,
    )

    private val domainPackages: List<String>
        get() = valueOrDefault("domainPackages", DEFAULT_DOMAIN_PACKAGES).split(",").map { it.trim() }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val packageName = importDirective.containingKtFile.packageFqName.asString()
        val inDomain = domainPackages.any { prefix ->
            prefix.isNotBlank() && (packageName == prefix || packageName.startsWith("$prefix."))
        }
        if (!inDomain) return

        val imported = importDirective.importedFqName?.asString() ?: return
        if (FORBIDDEN_PREFIXES.any { imported == it || imported.startsWith("$it.") }) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(importDirective),
                    "Domain code must not import '$imported': the domain layer stays free of " +
                        "the Android platform and of Room (NFR-46).",
                ),
            )
        }
    }

    private companion object {
        const val DEFAULT_DOMAIN_PACKAGES = "ru.heuri.sklyanka.domain"
        val FORBIDDEN_PREFIXES = listOf("android", "androidx", "dagger", "javax.inject")
    }
}

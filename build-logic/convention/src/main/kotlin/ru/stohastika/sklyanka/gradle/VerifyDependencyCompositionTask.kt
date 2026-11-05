package ru.stohastika.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * ПЛ-4, НФТ-15/16/39: состав зависимостей варианта (SKL-165, TDD §6.3).
 *
 * Правило одно и живёт в [ArtifactCompositionRules]: запрещённые группы — GMS, Firebase,
 * реклама и чужие SDK — отсутствуют и в разрешённом classpath, и в каталоге версий.
 * Каталог проверяется отдельно: координата, объявленная в нём, однажды станет
 * зависимостью, и заметить это на составе артефакта было бы поздно.
 */
abstract class VerifyDependencyCompositionTask : DefaultTask() {

    @get:Input
    abstract val coordinates: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val versionCatalog: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val resolved = coordinates.get().sorted()

        val problems = ArtifactCompositionRules.validateDependencies(
            coordinates = resolved,
            versionCatalog = versionCatalog.get().asFile.readText(),
        )

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Resolved runtime dependencies (${resolved.size}):")
                resolved.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Forbidden groups checked: ${ArtifactCompositionCatalog.FORBIDDEN_GROUPS.joinToString()}")
                problems.forEach { appendLine("  FAIL $it") }
            },
        )

        logger.lifecycle(
            "  [dependencies] OK   ${resolved.size} artifacts, none from " +
                ArtifactCompositionCatalog.FORBIDDEN_GROUPS.joinToString(),
        )

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Dependency composition check failed:")
                    problems.forEach { appendLine("  - $it") }
                    appendLine("Full report: ${reportFile.path}")
                },
            )
        }
    }
}

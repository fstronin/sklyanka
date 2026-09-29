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
 * ПЛ-4, НФТ-39, НФТ-16: ни Google Play Services, ни Firebase, ни аналитики и крашлитики.
 *
 * Проверяется и каталог версий (одно место, где перечислены координаты), и фактически
 * разрешённый classpath варианта: зависимость способна приехать транзитивно.
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
        val catalogText = versionCatalog.get().asFile.readText()

        val problems = mutableListOf<String>()
        FORBIDDEN_GROUPS.forEach { group ->
            resolved.filter { it.contains(group) }.forEach { hit ->
                problems += "resolved dependency '$hit' belongs to the forbidden group '$group' " +
                    "(PLATFORM-4, NFR-39, NFR-16)"
            }
            if (catalogText.contains(group)) {
                problems += "version catalog mentions the forbidden group '$group'"
            }
        }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Resolved runtime dependencies (${resolved.size}):")
                resolved.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Forbidden groups checked: ${FORBIDDEN_GROUPS.joinToString()}")
                problems.forEach { appendLine("  FAIL $it") }
            },
        )

        logger.lifecycle(
            "  [dependencies] OK   ${resolved.size} artifacts, none from " +
                FORBIDDEN_GROUPS.joinToString(),
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

    private companion object {
        val FORBIDDEN_GROUPS = listOf(
            "com.google.android.gms",
            "com.google.firebase",
            "com.google.android.play",
            "com.crashlytics",
            "io.fabric",
            "com.google.mlkit",
        )
    }
}

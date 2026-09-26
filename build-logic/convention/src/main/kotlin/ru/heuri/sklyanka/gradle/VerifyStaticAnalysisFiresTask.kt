package ru.heuri.sklyanka.gradle

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
 * Проверка того, что правила проекта действительно срабатывают (TDD §5.2).
 *
 * Читает отчёт detekt по заведомо нарушающим фрагментам и требует, чтобы каждое
 * проектное правило нашло хотя бы одно нарушение. Молчащее правило — худший
 * вариант из возможных: оно создаёт видимость контроля.
 */
abstract class VerifyStaticAnalysisFiresTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val detektReport: RegularFileProperty

    @get:Input
    abstract val expectedRules: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val reportText = detektReport.get().asFile.readText()
        val counts = expectedRules.get().associateWith { rule ->
            Regex(Regex.escape(rule)).findAll(reportText).count()
        }
        val silent = counts.filterValues { it == 0 }.keys

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Rule enforcement check over the deliberate violation samples")
                counts.toSortedMap().forEach { (rule, hits) ->
                    appendLine("  ${if (hits > 0) "FIRED " else "SILENT"} $rule ($hits findings)")
                }
            },
        )

        counts.toSortedMap().forEach { (rule, hits) ->
            logger.lifecycle("  [rule] ${if (hits > 0) "fired " else "SILENT"} $rule ($hits findings)")
        }

        if (silent.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("These project rules did not fire on deliberate violations:")
                    silent.forEach { appendLine("  - $it") }
                    appendLine("A rule that never fires is worse than no rule at all (TDD 5.2).")
                    appendLine("Detekt report: ${detektReport.get().asFile.path}")
                },
            )
        }
    }
}

package ru.heuri.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Разбирает фактический граф зависимостей модулей и падает на ребре «наружу». */
abstract class VerifyModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val graph: MapProperty<String, List<String>>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val edges = graph.get()
        val violations = ModuleGraphRules.validate(edges)

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Module dependency direction (app -> feature-* -> core-ui/core-data -> core-domain)")
                edges.toSortedMap().forEach { (module, dependencies) ->
                    appendLine("  $module -> ${dependencies.ifEmpty { listOf("(nothing)") }.joinToString()}")
                }
                violations.forEach { appendLine("  FAIL $it") }
            },
        )

        edges.toSortedMap().forEach { (module, dependencies) ->
            logger.lifecycle("  [modules] $module -> ${dependencies.ifEmpty { listOf("(nothing)") }.joinToString()}")
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Module dependency direction is violated:")
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
    }
}

package ru.heuri.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * Проверка самого правила (TDD §5.2).
 *
 * Без неё невозможно отличить «правило работает и нарушений нет» от «правило
 * не срабатывает». Заведомо нарушающий граф нельзя внести в настоящий проект,
 * не сломав сборку, поэтому он задан здесь как образец.
 */
abstract class VerifyModuleGraphRuleTask : DefaultTask() {

    @TaskAction
    fun verify() {
        val notDetected = VIOLATING_GRAPHS.filter { (_, graph) ->
            ModuleGraphRules.validate(graph).isEmpty()
        }.keys

        VIOLATING_GRAPHS.forEach { (name, graph) ->
            val found = ModuleGraphRules.validate(graph)
            found.forEach { logger.lifecycle("  [module-rule] $name -> $it") }
        }

        if (notDetected.isNotEmpty()) {
            throw GradleException(
                "The module dependency rule did not fire on known violations: " +
                    notDetected.joinToString(),
            )
        }

        val clean = ModuleGraphRules.validate(CLEAN_GRAPH)
        if (clean.isNotEmpty()) {
            throw GradleException(
                "The module dependency rule fires on a valid graph: ${clean.joinToString()}",
            )
        }
    }

    private companion object {
        val VIOLATING_GRAPHS = mapOf(
            "core-domain reaches out to core-data" to
                mapOf(":core-domain" to listOf(":core-data")),
            "core-domain reaches out to app" to
                mapOf(":core-domain" to listOf(":app")),
            "one screen module depends on another" to
                mapOf(":feature-today" to listOf(":feature-meds")),
            "core-ui depends on a screen module" to
                mapOf(":core-ui" to listOf(":feature-today")),
        )

        val CLEAN_GRAPH = mapOf(
            ":core-domain" to emptyList<String>(),
            ":core-ui" to listOf(":core-domain"),
            ":core-data" to listOf(":core-domain"),
            ":feature-today" to listOf(":core-domain", ":core-ui", ":core-data"),
            ":app" to listOf(":core-domain", ":core-ui", ":core-data", ":feature-today"),
        )
    }
}

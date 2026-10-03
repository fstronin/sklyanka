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
 * ПЛ-4, НФТ-15/16/39: состав сетевых зависимостей варианта.
 *
 * Три правила в одном отчёте:
 * 1. запрещённые группы — GMS, Firebase, реклама и чужая аналитика — отсутствуют
 *    и в разрешённом classpath, и в каталоге версий;
 * 2. ни одна координата `com.huawei.*` не проезжает мимо белого списка
 *    (`AnalyticsContourRules`): в релизе без сетевого контура список пуст (ПМ-31);
 * 3. если у контура объявлены обязательные координаты, они присутствуют в classpath.
 *
 * Правила вынесены в общий объект, чтобы проверка манифеста и проверка графа не разошлись.
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
        AnalyticsContourRules.FORBIDDEN_GROUPS.forEach { group ->
            resolved.filter { it.contains(group) }.forEach { hit ->
                problems += "resolved dependency '$hit' belongs to the forbidden group '$group' " +
                    "(PLATFORM-4, NFR-39, NFR-16)"
            }
            if (catalogText.contains(group)) {
                problems += "version catalog mentions the forbidden group '$group'"
            }
        }

        val huawei = resolved.filter { it.startsWith("com.huawei.") }
        huawei.filterNot(AnalyticsContourRules::allows).forEach { hit ->
            problems += "resolved dependency '$hit' is outside the contour white list " +
                "(SKL-100, ADR-0011). Add it to AnalyticsContourRules consciously, or remove it."
        }

        val missing = AnalyticsContourRules.REQUIRED_COORDINATES.filterNot { required ->
            huawei.any { it.startsWith(required) }
        }
        if (missing.isNotEmpty()) {
            problems += "the contour is missing required coordinates ${missing.joinToString()} " +
                "(PLATFORM-3, NFR-15)"
        }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Resolved runtime dependencies (${resolved.size}):")
                resolved.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Forbidden groups checked: ${AnalyticsContourRules.FORBIDDEN_GROUPS.joinToString()}")
                appendLine(
                    "Contour required coordinates: " +
                        AnalyticsContourRules.REQUIRED_COORDINATES.joinToString().ifEmpty { "none" },
                )
                appendLine("Huawei coordinates resolved: ${huawei.joinToString().ifEmpty { "none" }}")
                problems.forEach { appendLine("  FAIL $it") }
            },
        )

        logger.lifecycle(
            "  [dependencies] OK   ${resolved.size} artifacts, none from " +
                AnalyticsContourRules.FORBIDDEN_GROUPS.joinToString() +
                "; huawei coordinates resolved: ${huawei.size}",
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

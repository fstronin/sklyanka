package ru.stohastika.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * ПЛ-4, НФТ-15/16/39: состав сетевых зависимостей варианта (SKL-165, TDD §6.3).
 *
 * Три правила в одном отчёте, все — в [NetworkContourRules]:
 * 1. запрещённые группы — GMS, Firebase, реклама и чужие SDK — отсутствуют
 *    и в разрешённом classpath, и в каталоге версий;
 * 2. ни одна координата охраняемой группы (`com.huawei.*`, `com.my.tracker.*`) не проезжает
 *    мимо белого списка (`NetworkContourCatalog`);
 * 3. обязательные координаты контура присутствуют, только если контур входит в артефакт:
 *    `pro` несёт клиент магазина, поставляемая сборка `free` — нет (Р-86, ADR-0016;
 *    `ADR-0017` п. 3).
 *
 * Правила вынесены в общий объект, чтобы проверка манифеста и проверка графа не разошлись.
 */
abstract class VerifyDependencyCompositionTask : DefaultTask() {

    @get:Input
    abstract val coordinates: ListProperty<String>

    /**
     * Входит ли каждый сетевой контур в артефакт (SKL-165).
     *
     * `free` не несёт сетевых контуров; `pro` несёт платёжный. Требовать обязательные
     * координаты контура, которого в артефакте нет, нельзя: обязательная координата —
     * обещание, что контур работает, а не свойство любого APK.
     */
    @get:Input
    abstract val contoursInArtifact: MapProperty<String, Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val versionCatalog: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val resolved = coordinates.get().sorted()
        val present = contoursInArtifact.get()

        val problems = NetworkContourRules.validateDependencies(
            coordinates = resolved,
            versionCatalog = versionCatalog.get().asFile.readText(),
            contoursInArtifact = present,
        )

        val guarded = resolved.filter(NetworkContourCatalog::guards)
        val contoursPresent = NetworkContourCatalog.NETWORK_CONTOURS.filter { present[it.module] == true }
        val requiredCoordinates = contoursPresent.flatMap { contour ->
            contour.requiredCoordinates.map { "${contour.module}: $it" }
        }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Resolved runtime dependencies (${resolved.size}):")
                resolved.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Forbidden groups checked: ${NetworkContourCatalog.FORBIDDEN_GROUPS.joinToString()}")
                appendLine("Contours in the artifact: ${contoursPresent.joinToString { it.module }.ifEmpty { "none" }}")
                appendLine(
                    "Contour required coordinates: " +
                        requiredCoordinates.joinToString().ifEmpty { "none (no network contour in this artifact)" },
                )
                appendLine("Guarded coordinates resolved: ${guarded.joinToString().ifEmpty { "none" }}")
                problems.forEach { appendLine("  FAIL $it") }
            },
        )

        logger.lifecycle(
            "  [dependencies] OK   ${resolved.size} artifacts, none from " +
                NetworkContourCatalog.FORBIDDEN_GROUPS.joinToString() +
                "; guarded coordinates resolved: ${guarded.size}; " +
                "contours: ${contoursPresent.joinToString { it.module }.ifEmpty { "none" }}",
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

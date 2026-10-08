package ru.stohastika.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Проверка состава СОБРАННОГО артефакта (TDD §5.5).
 *
 * Смотрит слитый манифест, а не исходный: разрешение способна добавить зависимость
 * при слиянии манифестов, и проверка исходника этого не увидит.
 *
 * ПМ-31: первая публикация — без сетевого контура, поэтому сетевые разрешения
 * запрещены (ПЛ-3, НФТ-15). Связка «разрешение ↔ контур» сохранена в запрещённом
 * виде: `verifyDependencies<Config>` валит любую непроверенную координату,
 * а здесь запрещены разрешения.
 */
abstract class VerifyMergedManifestTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val expectedPackageName: Property<String>

    @get:Input
    abstract val expectedMinSdk: Property<Int>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val problems = mutableListOf<String>()
        val notes = mutableListOf<String>()

        // ПЛ-3, НФТ-15, ПМ-31: в релизе сетевых разрешений нет; разрешения состояния
        // сети и рекламный идентификатор запрещены. Правила объявлены одним местом —
        // AnalyticsContourRules.
        AnalyticsContourRules.FORBIDDEN_PERMISSIONS.forEach { permission ->
            if (manifest.contains("\"$permission\"")) {
                problems += "forbidden permission $permission is present in the merged manifest " +
                    "(PLATFORM-3, NFR-15). A dependency may have merged it in; remove it with " +
                    "tools:node=\"remove\" or return it with the contour decision (PM-31)."
            } else {
                notes += "no $permission"
            }
        }

        // ПЛ-2: имя пакета неизменяемо — к нему привязаны записи магазинов.
        val packageName = PACKAGE.find(manifest)?.groupValues?.get(1)
        when {
            packageName == null -> problems += "merged manifest declares no package name"
            packageName != expectedPackageName.get() ->
                problems += "package name is '$packageName', expected '${expectedPackageName.get()}' (PLATFORM-2)"
            else -> notes += "package name $packageName"
        }

        // ПЛ-1, НФТ-38: нижняя граница — Android 8.0.
        val minSdk = MIN_SDK.find(manifest)?.groupValues?.get(1)?.toIntOrNull()
        when {
            minSdk == null -> problems += "merged manifest declares no minSdkVersion"
            minSdk != expectedMinSdk.get() ->
                problems += "minSdkVersion is $minSdk, expected ${expectedMinSdk.get()} (PLATFORM-1, NFR-38)"
            else -> notes += "minSdkVersion $minSdk"
        }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Artifact composition check for ${mergedManifest.get().asFile.path}")
                notes.forEach { appendLine("  OK   $it") }
                problems.forEach { appendLine("  FAIL $it") }
            },
        )

        // Наблюдаемость конвейера (TDD §8): прогон печатает, что именно проверено.
        notes.forEach { logger.lifecycle("  [manifest] OK   $it") }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Artifact composition check failed:")
                    problems.forEach { appendLine("  - $it") }
                },
            )
        }
    }

    private companion object {
        val PACKAGE = Regex("package=\"([^\"]+)\"")
        val MIN_SDK = Regex("android:minSdkVersion=\"(\\d+)\"")
    }
}

package ru.stohastika.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Проверка состава СОБРАННОГО артефакта (SKL-165, TDD §6.2).
 *
 * Смотрит слитый манифест, а не исходный: разрешение способна добавить зависимость
 * при слиянии манифестов, и проверка исходника этого не увидит. Решения о сети принимает
 * [ArtifactCompositionRules]: задача только читает файлы и передаёт текст, поэтому те же
 * правила прогоняются на образцах нарушений отдельной задачей.
 *
 * Что проверяется кроме сети:
 *
 * 1. **Запрещённые разрешения** — состояние сети и рекламный идентификатор — не проходят
 *    никогда, даже если их принесла зависимость (ПЛ-3, НФТ-15). Именно так они и приходят:
 *    библиотеки объявляют их в своих манифестах, и манифест `app` снимает оба через
 *    `tools:node="remove"`.
 * 2. **Сети в продукте нет** (`ADR-0017` п. 2): `INTERNET` и `ACCESS_NETWORK_STATE`
 *    в слитом манифесте — нарушение, откуда бы они ни пришли. Продукт работает офлайн,
 *    и поставляемая сборка не объявляет сетевых разрешений вовсе.
 *
 * Происхождение проверяется по исходникам манифестов **всех** модулей и **всех** наборов
 * исходников (не только `src/main`): разрешение, объявленное нашим модулем, различимо
 * и называется нарушением с именем файла.
 */
abstract class VerifyMergedManifestTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val expectedPackageName: Property<String>

    @get:Input
    abstract val expectedMinSdk: Property<Int>

    /** Исходные манифесты всех модулей сборки. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleManifests: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifestFile = mergedManifest.get().asFile
        val manifest = manifestFile.readText()

        val problems = ArtifactCompositionRules.validateManifest(
            mergedManifest = manifest,
            moduleManifests = moduleManifests.files.associate { it.path to it.readText() },
        ).toMutableList()
        val notes = mutableListOf<String>()

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

        ArtifactCompositionCatalog.NETWORK_PERMISSIONS.forEach { permission ->
            if (!manifest.contains(permission)) notes += "no $permission"
        }
        ArtifactCompositionCatalog.FORBIDDEN_PERMISSIONS.forEach { permission ->
            if (!manifest.contains(permission)) notes += "no $permission"
        }

        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Artifact composition check for ${manifestFile.path}")
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
                    appendLine("Full report: ${reportFile.path}")
                },
            )
        }
    }

    private companion object {
        val PACKAGE = Regex("package=\"([^\"]+)\"")
        val MIN_SDK = Regex("minSdkVersion=\"(\\d+)\"")
    }
}

package ru.stohastika.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
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
 * [NetworkContourRules]: задача только читает файлы и передаёт текст, поэтому те же правила
 * прогоняются на образцах нарушений отдельной задачей.
 *
 * Что проверяется кроме сети:
 *
 * 1. **Запрещённые разрешения** — состояние сети и рекламный идентификатор — не проходят
 *    никогда, даже если их принесла зависимость (ПЛ-3, НФТ-15). Именно так они и приходят:
 *    библиотеки объявляют их в своих манифестах, и манифест `app` снимает оба через
 *    `tools:node="remove"`.
 * 2. **Сеть только вместе с контуром** (`ADR-0017` п. 2): разрешение законно, если его
 *    объявил контур, входящий в артефакт, и никто вне контуров. Обратная сторона тоже
 *    обязательна: контур в артефакте без своего разрешения — нарушение.
 *
 * Происхождение проверяется по исходникам манифестов **всех** модулей и **всех** наборов
 * исходников (не только `src/main`): разрешение, объявленное нашим модулем мимо контура,
 * различимо и называется нарушением.
 *
 * **Чего правило не видит** (названный предел, а не обещание): разрешение, добавленное
 * слиянием из чужого AAR при живом контуре. Это принятая цена решения `ADR-0017` п. 2:
 * библиотека контура объявляет `INTERNET` сама, и отличить её от «случайной» библиотеки
 * можно было бы только разбором отчёта слияния. Гарантия от этого не размывается:
 * **без контура в артефакте сеть не появляется вовсе**, и ни один наш модуль, кроме контуров,
 * сеть не объявляет.
 */
abstract class VerifyMergedManifestTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val expectedPackageName: Property<String>

    @get:Input
    abstract val expectedMinSdk: Property<Int>

    /** Манифесты сетевых контуров: по ним видно, кто объявил разрешение. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contourManifests: ConfigurableFileCollection

    /** Исходные манифесты всех модулей сборки мимо контуров. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleManifests: ConfigurableFileCollection

    /** Входит ли каждый сетевой контур в собираемый артефакт: имя модуля → признак. */
    @get:Input
    abstract val contoursInArtifact: MapProperty<String, Boolean>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifestFile = mergedManifest.get().asFile
        val manifest = manifestFile.readText()
        val present = contoursInArtifact.get()
        val contourTexts = contourManifests.files.mapNotNull { file ->
            contourModuleOf(file)?.let { module -> module to file.readText() }
        }.toMap()
        val contourPaths = contourManifests.files.map { it.canonicalPath }.toSet()

        val problems = NetworkContourRules.validate(
            mergedManifest = manifest,
            contourManifests = contourTexts,
            contoursInArtifact = present,
            moduleManifests = moduleManifests.files
                .filterNot { it.canonicalPath in contourPaths }
                .associate { it.path to it.readText() },
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

        notes += contourNotes(manifest, contourTexts, present)
        NetworkContourCatalog.FORBIDDEN_PERMISSIONS.forEach { permission ->
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

    /** Что именно пришло из какого контура — видно в отчёте, а не только «всё хорошо». */
    private fun contourNotes(
        manifest: String,
        contourTexts: Map<String, String>,
        present: Map<String, Boolean>,
    ): List<String> {
        val notes = mutableListOf<String>()
        NetworkContourCatalog.NETWORK_CONTOURS.forEach { contour ->
            val inArtifact = present[contour.module] == true
            contour.permissions.forEach { permission ->
                when {
                    !inArtifact -> notes += "${contour.module} contour is not part of the artifact"
                    manifest.declares(permission) ->
                        notes += "$permission from the ${contour.module} contour"
                    else -> Unit // нарушение уже названо правилом
                }
            }
            if (inArtifact) {
                notes += "${contour.module} contour is part of the artifact"
            }
            contourTexts[contour.module]?.let { text ->
                if (contour.permissions.any { !text.declares(it) }) {
                    notes += "${contour.module} contour manifest declares no network permission of its own"
                }
            }
        }
        return notes
    }

    /**
     * Имя контура по пути его манифеста.
     *
     * Модуль называется так же, как его каталог: другого способа связать файл с контуром
     * задача не имеет — путь и есть адрес модуля в дереве.
     */
    private fun contourModuleOf(file: java.io.File): String? {
        val path = file.invariantSeparatorsPath
        return NetworkContourCatalog.NETWORK_CONTOURS
            .map { it.module }
            .filter { path.contains("/$it/") }
            .maxByOrNull { it.length }
    }

    private companion object {
        val PACKAGE = Regex("package=\"([^\"]+)\"")
        val MIN_SDK = Regex("minSdkVersion=\"(\\d+)\"")
    }
}

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
 * Проверка состава СОБРАННОГО артефакта (TDD §5.4).
 *
 * Смотрит слитый манифест, а не исходный: разрешение способна добавить зависимость
 * при слиянии манифестов, и проверка исходника этого не увидит.
 *
 * Два правила:
 *
 * 1. **Запрещённые разрешения** — состояние сети и рекламный идентификатор — не проходят
 *    никогда, даже если их принесла зависимость (ПЛ-3, НФТ-15).
 * 2. **Разрешения платного контура** (`INTERNET`) проходят, только если контур есть
 *    в артефакте, объявляет их сам и не объявляет никто другой (`ADR-0010` п. 1 и п. 5, ИИ-24).
 *    Раньше здесь стоял безусловный запрет; он перестал быть правдой с появлением контура,
 *    и правило заменено на адресное — «сеть только вместе с контуром», а не «сеть ни при
 *    каких условиях».
 *
 * Происхождение проверяется по исходникам манифестов **всех** модулей и **всех** наборов
 * (все наборы исходников, а не только `src/main`): разрешение, объявленное нашим модулем мимо контура,
 * различимо и называется нарушением.
 *
 * **Чего правило не видит** (названный предел, а не обещание): разрешение, добавленное
 * слиянием из чужого AAR при живом контуре. Это принятая цена решения `ADR-0010` п. 5:
 * сама вендорская библиотека объявляет `INTERNET`, и отличить её от «случайной» библиотеки
 * можно было бы только разбором отчёта слияния. Гарантия от этого не размывается:
 * **без контура в артефакте сеть не появляется вовсе**, и ни один наш модуль, кроме
 * контура, сеть не объявляет.
 */
abstract class VerifyMergedManifestTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val expectedPackageName: Property<String>

    @get:Input
    abstract val expectedMinSdk: Property<Int>

    /** Манифест модуля платного контура: единственный источник сетевых разрешений. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contourManifest: RegularFileProperty

    /** Исходные манифесты всех модулей сборки: по ним видно, кто ещё объявил сетевое разрешение. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleManifests: ConfigurableFileCollection

    /** Входит ли платный контур в собираемый артефакт. */
    @get:Input
    abstract val paidContourPresent: Property<Boolean>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val problems = mutableListOf<String>()
        val notes = mutableListOf<String>()

        // ПЛ-3, НФТ-15: разрешения состояния сети и рекламный идентификатор запрещены всегда.
        // Правила объявлены одним местом — AnalyticsContourRules.
        AnalyticsContourRules.FORBIDDEN_PERMISSIONS.forEach { permission ->
            if (manifest.contains("\"$permission\"")) {
                problems += "forbidden permission $permission is present in the merged manifest " +
                    "(PLATFORM-3, NFR-15). A dependency may have merged it in; remove it with " +
                    "tools:node=\"remove\" or return it with the contour decision (PM-31)."
            } else {
                notes += "no $permission"
            }
        }

        verifyContourPermissions(manifest, problems, notes)

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

    /**
     * Сеть только вместе с контуром и только из него (`ADR-0010` п. 1 и п. 5).
     *
     * Разрешение контура законно, когда выполняются все три условия: контур входит
     * в артефакт, объявляет разрешение его собственный манифест и ни один другой модуль
     * его не объявляет. Обратная сторона проверяется тоже и целиком: если контур в артефакте
     * есть, а разрешения нет **в слитом манифесте или в манифесте самого контура** — сетевой
     * путь пропал, и это нарушение, а не «повезло».
     */
    private fun verifyContourPermissions(
        manifest: String,
        problems: MutableList<String>,
        notes: MutableList<String>,
    ) {
        val contour = contourManifest.get().asFile
        val contourText = contour.readText()
        val contourPresent = paidContourPresent.get()

        AnalyticsContourRules.PAID_CONTOUR_PERMISSIONS.forEach { permission ->
            val inMerged = manifest.contains("\"$permission\"")
            val inContour = contourText.contains("\"$permission\"")
            val elsewhere = moduleManifests.files
                .filter { it != contour && it.readText().contains("\"$permission\"") }
                .map { it.path }

            when {
                // Контур в артефакте — разрешение обязано быть и в слитом манифесте,
                // и в манифесте контура: исчезновение любой из двух половин пути — нарушение.
                !inMerged && contourPresent -> problems +=
                    "the paid contour is part of the artifact but $permission is missing from the " +
                        "merged manifest: the network point disappeared (ADR-0010 §1)"

                !inMerged -> notes += "no $permission"

                !contourPresent -> problems +=
                    "$permission is in the merged manifest while the paid contour " +
                        "(${AnalyticsContourRules.PAID_CONTOUR_MODULE}) is not part of the artifact " +
                        "(PLATFORM-3, NFR-15, ADR-0010 §5)"

                !inContour -> problems +=
                    "$permission is in the merged manifest but the contour manifest " +
                        "(${contour.path}) does not declare it: the network point must name itself " +
                        "(ADR-0010 §1)"

                elsewhere.isNotEmpty() -> problems +=
                    "$permission is declared by ${elsewhere.joinToString()} — the network point " +
                        "belongs to the paid contour alone (ADR-0010 §1)"

                else -> notes += "$permission from the paid contour"
            }
        }
    }

    private companion object {
        val PACKAGE = Regex("package=\"([^\"]+)\"")
        val MIN_SDK = Regex("android:minSdkVersion=\"(\\d+)\"")
    }
}

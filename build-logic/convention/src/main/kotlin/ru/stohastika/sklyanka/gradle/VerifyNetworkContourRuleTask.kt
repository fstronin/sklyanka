package ru.stohastika.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * Проверка самих правил сетевых контуров (TDD §6.4, §11.6, `ADR-0017` п. 4).
 *
 * Без неё невозможно отличить «правило работает и нарушений нет» от «правило не срабатывает».
 * Заведомо нарушающий манифест нельзя внести в настоящий проект, не сломав сборку, поэтому
 * образцы заданы здесь: слитый манифест, набор контуров и состав зависимостей описаны
 * как данные, а [NetworkContourRules] судит их той же функцией, что и настоящий артефакт.
 *
 * Задача входит в `check` и в `verifyAll` (`ProjectChecksConventionPlugin`): гейт, который
 * есть только для того, кто о нём помнит, — это обещание, а не гейт.
 */
abstract class VerifyNetworkContourRuleTask : DefaultTask() {

    @TaskAction
    fun verify() {
        val undetected = MUTATIONS.filter { (_, sample) ->
            NetworkContourRules.validate(
                mergedManifest = sample.merged,
                contourManifests = sample.contours,
                contoursInArtifact = sample.present,
                moduleManifests = sample.modules,
            ).isEmpty()
        }.keys

        MUTATIONS.forEach { (name, sample) ->
            NetworkContourRules.validate(
                mergedManifest = sample.merged,
                contourManifests = sample.contours,
                contoursInArtifact = sample.present,
                moduleManifests = sample.modules,
            ).forEach { logger.lifecycle("  [contour-rule] $name -> $it") }
        }

        val brokenClean = COHERENT.filter { (_, sample) ->
            NetworkContourRules.validate(
                mergedManifest = sample.merged,
                contourManifests = sample.contours,
                contoursInArtifact = sample.present,
                moduleManifests = sample.modules,
            ).isNotEmpty()
        }.keys

        val undetectedDependencies = MUTATIONS_DEPENDENCIES.filter { (_, sample) ->
            NetworkContourRules.validateDependencies(
                coordinates = sample.coordinates,
                versionCatalog = sample.catalog,
                contoursInArtifact = sample.present,
            ).isEmpty()
        }.keys

        MUTATIONS_DEPENDENCIES.forEach { (name, sample) ->
            NetworkContourRules.validateDependencies(
                coordinates = sample.coordinates,
                versionCatalog = sample.catalog,
                contoursInArtifact = sample.present,
            ).forEach { logger.lifecycle("  [contour-rule] $name -> $it") }
        }

        val brokenCleanDependencies = COHERENT_DEPENDENCIES.filter { (_, sample) ->
            NetworkContourRules.validateDependencies(
                coordinates = sample.coordinates,
                versionCatalog = sample.catalog,
                contoursInArtifact = sample.present,
            ).isNotEmpty()
        }.keys

        val failures = buildList {
            if (undetected.isNotEmpty()) {
                add("the manifest rule did not fire on known violations: ${undetected.joinToString()}")
            }
            if (brokenClean.isNotEmpty()) {
                add("the manifest rule fires on a coherent artifact: ${brokenClean.joinToString()}")
            }
            if (undetectedDependencies.isNotEmpty()) {
                add("the dependency rule did not fire on known violations: ${undetectedDependencies.joinToString()}")
            }
            if (brokenCleanDependencies.isNotEmpty()) {
                add("the dependency rule fires on a coherent artifact: ${brokenCleanDependencies.joinToString()}")
            }
        }

        logger.lifecycle(
            "  [contour-rule] OK   ${MUTATIONS.size} manifest violations and " +
                "${MUTATIONS_DEPENDENCIES.size} composition violations are caught; " +
                "${COHERENT.size + COHERENT_DEPENDENCIES.size} coherent samples pass",
        )

        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(System.lineSeparator()))
        }
    }

    /** Образец слитого манифеста вместе с набором контуров и исходников модулей. */
    private data class ManifestSample(
        val merged: String,
        val contours: Map<String, String>,
        val present: Map<String, Boolean>,
        val modules: Map<String, String> = emptyMap(),
    )

    /** Образец состава зависимостей варианта. */
    private data class CompositionSample(
        val coordinates: List<String>,
        val present: Map<String, Boolean>,
        val catalog: String = CATALOG,
    )

    private companion object {
        const val INTERNET = "android.permission.INTERNET"
        const val ANALYTICS = "core-analytics"
        const val PAID = "billing-appgallery"

        /** Манифест с названным разрешением и без ничего лишнего. */
        fun manifest(vararg permissions: String): String = buildString {
            appendLine("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">")
            permissions.forEach {
                appendLine("    <uses-permission android:name=\"$it\" />")
            }
            appendLine("</manifest>")
        }

        /** Разрешения контура аналитики — берём из правил, а не переписываем руками. */
        val ANALYTICS_PERMISSIONS = AnalyticsContourRules.ANALYTICS_CONTOUR.permissions.toTypedArray()

        val ANALYTICS_MANIFEST = manifest(*ANALYTICS_PERMISSIONS)
        val PAID_MANIFEST = manifest(INTERNET)
        val EMPTY_MANIFEST = manifest()

        /** Состав свободной сборки: контур аналитики есть, платёжного нет. */
        val FREE_PRESENT = mapOf(ANALYTICS to true, PAID to false)

        /** Состав сборки `pro`: оба сетевых контура. */
        val PRO_PRESENT = mapOf(ANALYTICS to true, PAID to true)

        val NO_CONTOURS = mapOf(ANALYTICS to false, PAID to false)

        val MUTATIONS = linkedMapOf(
            "network without any contour" to ManifestSample(
                merged = manifest(INTERNET, "android.permission.POST_NOTIFICATIONS"),
                contours = mapOf(ANALYTICS to EMPTY_MANIFEST, PAID to EMPTY_MANIFEST),
                present = NO_CONTOURS,
            ),
            "network declared by a module outside the contours" to ManifestSample(
                merged = manifest(INTERNET),
                contours = mapOf(ANALYTICS to ANALYTICS_MANIFEST, PAID to PAID_MANIFEST),
                present = FREE_PRESENT,
                modules = mapOf("feature-today/src/main/AndroidManifest.xml" to manifest(INTERNET)),
            ),
            "contour in the artifact but its permission missing" to ManifestSample(
                merged = manifest("android.permission.POST_NOTIFICATIONS"),
                contours = mapOf(ANALYTICS to ANALYTICS_MANIFEST, PAID to PAID_MANIFEST),
                present = FREE_PRESENT,
            ),
            "permission without a declaring contour in the artifact" to ManifestSample(
                merged = manifest(INTERNET),
                contours = mapOf(ANALYTICS to EMPTY_MANIFEST, PAID to PAID_MANIFEST),
                present = FREE_PRESENT,
            ),
            "contour present, its own manifest silent, the neighbour declares" to ManifestSample(
                merged = manifest(INTERNET),
                contours = mapOf(ANALYTICS to ANALYTICS_MANIFEST, PAID to EMPTY_MANIFEST),
                present = PRO_PRESENT,
            ),
            "forbidden wi-fi state permission merged in" to ManifestSample(
                merged = manifest(INTERNET, "android.permission.ACCESS_WIFI_STATE"),
                contours = mapOf(ANALYTICS to ANALYTICS_MANIFEST, PAID to PAID_MANIFEST),
                present = FREE_PRESENT,
            ),
            "advertising identifier merged in" to ManifestSample(
                merged = manifest(INTERNET, "com.google.android.gms.permission.AD_ID"),
                contours = mapOf(ANALYTICS to ANALYTICS_MANIFEST, PAID to PAID_MANIFEST),
                present = FREE_PRESENT,
            ),
        )

        val COHERENT = linkedMapOf(
            // Поставляемая сборка: сеть объявлена контуром аналитики и только им.
            "free build" to ManifestSample(
                merged = manifest(*ANALYTICS_PERMISSIONS),
                contours = mapOf(ANALYTICS to ANALYTICS_MANIFEST, PAID to PAID_MANIFEST),
                present = FREE_PRESENT,
            ),
            // `pro`: оба контура в артефакте и оба объявили сеть — законный случай.
            "pro build" to ManifestSample(
                merged = manifest(*ANALYTICS_PERMISSIONS),
                contours = mapOf(ANALYTICS to ANALYTICS_MANIFEST, PAID to PAID_MANIFEST),
                present = PRO_PRESENT,
            ),
            // Сборка без сетевых контуров сети не объявляет вовсе.
            "no contours" to ManifestSample(
                merged = manifest("android.permission.POST_NOTIFICATIONS"),
                contours = mapOf(ANALYTICS to ANALYTICS_MANIFEST, PAID to PAID_MANIFEST),
                present = NO_CONTOURS,
            ),
        )

        val FREE_COORDINATES = listOf("com.my.tracker:mytracker-sdk:3.6.0")

        val PRO_COORDINATES = FREE_COORDINATES + listOf("com.huawei.hms:iap:6.16.6.305")

        const val CATALOG = """
            [versions]
            mytracker = "3.6.0"

            [libraries]
            mytracker-sdk = { module = "com.my.tracker:mytracker-sdk", version.ref = "mytracker" }
        """

        val MUTATIONS_DEPENDENCIES = linkedMapOf(
            "forbidden group in the classpath" to CompositionSample(
                coordinates = FREE_COORDINATES + "com.google.android.gms:play-services-appset:16.1.0",
                present = FREE_PRESENT,
            ),
            "forbidden group in the version catalog" to CompositionSample(
                coordinates = FREE_COORDINATES,
                present = FREE_PRESENT,
                catalog = CATALOG + "com.google.firebase:firebase-encoders",
            ),
            "guarded coordinate outside the white list" to CompositionSample(
                coordinates = FREE_COORDINATES + "com.my.tracker:mytracker-plugins:1.0",
                present = FREE_PRESENT,
            ),
            "contour in the artifact without its client" to CompositionSample(
                coordinates = emptyList(),
                present = FREE_PRESENT,
            ),
        )

        val COHERENT_DEPENDENCIES = linkedMapOf(
            "free build" to CompositionSample(coordinates = FREE_COORDINATES, present = FREE_PRESENT),
            "pro build" to CompositionSample(coordinates = PRO_COORDINATES, present = PRO_PRESENT),
            "no contours" to CompositionSample(coordinates = emptyList(), present = NO_CONTOURS),
        )
    }
}

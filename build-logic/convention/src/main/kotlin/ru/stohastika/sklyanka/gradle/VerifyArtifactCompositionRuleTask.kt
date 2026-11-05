package ru.stohastika.sklyanka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * Проверка самих правил состава артефакта (TDD §6.4, §11.6, `ADR-0017` п. 4).
 *
 * Без неё невозможно отличить «правило работает и нарушений нет» от «правило не срабатывает».
 * Заведомо нарушающий манифест нельзя внести в настоящий проект, не сломав сборку, поэтому
 * образцы заданы здесь: слитый манифест, исходные манифесты модулей и состав зависимостей
 * описаны как данные, а [ArtifactCompositionRules] судит их той же функцией, что и настоящий
 * артефакт.
 *
 * Задача входит в `check` и в `verifyAll` (`ProjectChecksConventionPlugin`): гейт, который
 * есть только для того, кто о нём помнит, — это обещание, а не гейт.
 */
abstract class VerifyArtifactCompositionRuleTask : DefaultTask() {

    @TaskAction
    fun verify() {
        val undetected = MUTATIONS.filter { (_, sample) ->
            ArtifactCompositionRules.validateManifest(
                mergedManifest = sample.merged,
                moduleManifests = sample.modules,
            ).isEmpty()
        }.keys

        MUTATIONS.forEach { (name, sample) ->
            ArtifactCompositionRules.validateManifest(
                mergedManifest = sample.merged,
                moduleManifests = sample.modules,
            ).forEach { logger.lifecycle("  [composition-rule] $name -> $it") }
        }

        val brokenClean = COHERENT.filter { (_, sample) ->
            ArtifactCompositionRules.validateManifest(
                mergedManifest = sample.merged,
                moduleManifests = sample.modules,
            ).isNotEmpty()
        }.keys

        val undetectedDependencies = MUTATIONS_DEPENDENCIES.filter { (_, sample) ->
            ArtifactCompositionRules.validateDependencies(
                coordinates = sample.coordinates,
                versionCatalog = sample.catalog,
            ).isEmpty()
        }.keys

        MUTATIONS_DEPENDENCIES.forEach { (name, sample) ->
            ArtifactCompositionRules.validateDependencies(
                coordinates = sample.coordinates,
                versionCatalog = sample.catalog,
            ).forEach { logger.lifecycle("  [composition-rule] $name -> $it") }
        }

        val brokenCleanDependencies = COHERENT_DEPENDENCIES.filter { (_, sample) ->
            ArtifactCompositionRules.validateDependencies(
                coordinates = sample.coordinates,
                versionCatalog = sample.catalog,
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
            "  [composition-rule] OK   ${MUTATIONS.size} manifest violations and " +
                "${MUTATIONS_DEPENDENCIES.size} composition violations are caught; " +
                "${COHERENT.size + COHERENT_DEPENDENCIES.size} coherent samples pass",
        )

        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(System.lineSeparator()))
        }
    }

    /** Образец слитого манифеста вместе с исходниками модулей. */
    private data class ManifestSample(
        val merged: String,
        val modules: Map<String, String> = emptyMap(),
    )

    /** Образец состава зависимостей варианта. */
    private data class CompositionSample(
        val coordinates: List<String>,
        val catalog: String = CATALOG,
    )

    private companion object {
        const val INTERNET = "android.permission.INTERNET"
        const val NETWORK_STATE = "android.permission.ACCESS_NETWORK_STATE"

        /** Манифест с названным разрешением и без ничего лишнего. */
        fun manifest(vararg permissions: String): String = buildString {
            appendLine("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">")
            permissions.forEach {
                appendLine("    <uses-permission android:name=\"$it\" />")
            }
            appendLine("</manifest>")
        }

        val MUTATIONS = linkedMapOf(
            "internet merged in" to ManifestSample(
                merged = manifest(INTERNET, "android.permission.POST_NOTIFICATIONS"),
                modules = mapOf("feature-today/src/main/AndroidManifest.xml" to manifest()),
            ),
            "network state merged in" to ManifestSample(
                merged = manifest(NETWORK_STATE),
            ),
            "network declared by a module manifest" to ManifestSample(
                merged = manifest(),
                modules = mapOf("feature-today/src/main/AndroidManifest.xml" to manifest(INTERNET)),
            ),
            "forbidden wi-fi state permission merged in" to ManifestSample(
                merged = manifest("android.permission.ACCESS_WIFI_STATE"),
            ),
            "advertising identifier merged in" to ManifestSample(
                merged = manifest("com.google.android.gms.permission.AD_ID"),
            ),
        )

        val COHERENT = linkedMapOf(
            "shipped build" to ManifestSample(
                merged = manifest(
                    "android.permission.POST_NOTIFICATIONS",
                    "android.permission.USE_EXACT_ALARM",
                ),
                modules = mapOf("feature-today/src/main/AndroidManifest.xml" to manifest()),
            ),
        )

        const val CATALOG = """
            [versions]
            work = "2.9.1"

            [libraries]
            androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "work" }
        """

        val COHERENT_COORDINATES = listOf(
            "androidx.work:work-runtime:2.9.1",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0",
        )

        val MUTATIONS_DEPENDENCIES = linkedMapOf(
            "forbidden group in the classpath" to CompositionSample(
                coordinates = COHERENT_COORDINATES + "com.google.android.gms:play-services-appset:16.1.0",
            ),
            "forbidden group in the version catalog" to CompositionSample(
                coordinates = COHERENT_COORDINATES,
                catalog = CATALOG + "com.google.firebase:firebase-encoders",
            ),
        )

        val COHERENT_DEPENDENCIES = linkedMapOf(
            "shipped build" to CompositionSample(coordinates = COHERENT_COORDINATES),
        )
    }
}

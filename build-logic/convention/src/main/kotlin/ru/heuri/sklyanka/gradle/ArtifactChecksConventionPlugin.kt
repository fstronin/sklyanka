package ru.heuri.sklyanka.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * Проверки, которые нельзя сделать по исходникам, — только по собранному приложению
 * (TDD §5.5). Регистрируются на каждый включённый вариант; отладочный вариант
 * входит в общий прогон check, релизный — в сборку релиза.
 */
class ArtifactChecksConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                val suffix = variant.name.replaceFirstChar { it.uppercase() }

                val manifestCheck = tasks.register<VerifyMergedManifestTask>("verifyManifest$suffix") {
                    group = VERIFICATION_GROUP
                    description = "Checks the merged manifest of the $suffix artifact: " +
                        "no network permission, package name and minSdk (PLATFORM-1..3, NFR-41)."
                    mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    expectedPackageName.set(ProjectDefaults.APPLICATION_ID)
                    packageNameLimit.set(ProjectDefaults.PACKAGE_NAME_LIMIT)
                    expectedMinSdk.set(ProjectDefaults.MIN_SDK)
                    report.set(layout.buildDirectory.file("reports/composition/manifest-${variant.name}.txt"))
                }

                val runtimeConfiguration = variant.runtimeConfiguration
                val dependencyCheck =
                    tasks.register<VerifyDependencyCompositionTask>("verifyDependencies$suffix") {
                        group = VERIFICATION_GROUP
                        description = "Checks that the $suffix runtime classpath carries no Google " +
                            "Play Services, Firebase, analytics or crash reporting (PLATFORM-4)."
                        coordinates.set(
                            provider {
                                runtimeConfiguration.incoming.resolutionResult.allComponents
                                    .map(ResolvedComponentResult::getId)
                                    .map { it.displayName }
                                    .filterNot { it.startsWith("project ") }
                            },
                        )
                        versionCatalog.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
                        report.set(
                            layout.buildDirectory.file("reports/composition/dependencies-${variant.name}.txt"),
                        )
                    }

                // Проверка состава — часть сборки варианта, а не отдельный ритуал,
                // про который вспоминают перед выпуском (TDD §5.5).
                tasks.matching { it.name == "assemble$suffix" }.configureEach {
                    dependsOn(manifestCheck, dependencyCheck)
                }
                if (variant.name == "debug") {
                    tasks.matching { it.name == "check" }.configureEach {
                        dependsOn(manifestCheck, dependencyCheck)
                    }
                }
            }
        }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"
    }
}

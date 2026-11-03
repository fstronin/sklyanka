package ru.stohastika.sklyanka.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * Проверки, которые нельзя сделать по исходникам, — только по собранному приложению
 * (TDD §5.4, §6.2). Регистрируются на каждый включённый вариант; отладочный вариант
 * входит в общий прогон check, релизный — в сборку релиза.
 */
class ArtifactChecksConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                val suffix = variant.name.replaceFirstChar { it.uppercase() }
                // Один признак на контур, и он общий для обеих проверок: манифест и граф
                // зависимостей обязаны судить о контурах одинаково (SKL-165). Без этого
                // free-вариант требовал бы вендорскую координату, которой у него нет,
                // или, наоборот, пропускал `INTERNET` без своего контура.
                val contoursInArtifactProvider = provider {
                    val modules = variant.runtimeConfiguration.incoming.resolutionResult.allComponents
                        .map(ResolvedComponentResult::getId)
                        .map { it.displayName }
                        .toSet()
                    AnalyticsContourRules.NETWORK_CONTOURS.associate { contour ->
                        contour.module to modules.any { it.startsWith("project ${contour.project}") }
                    }
                }

                val manifestCheck = tasks.register<VerifyMergedManifestTask>("verifyManifest$suffix") {
                    group = VERIFICATION_GROUP
                    description = "Checks the merged manifest of the $suffix artifact: network " +
                        "permissions only together with their network contours and only from them, " +
                        "network state forbidden always, package name and minSdk " +
                        "(PLATFORM-1..3, NFR-15/38, ADR-0017 §2)."
                    mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    expectedPackageName.set(ProjectDefaults.APPLICATION_ID)
                    expectedMinSdk.set(ProjectDefaults.MIN_SDK)
                    contourManifests.from(
                        provider {
                            AnalyticsContourRules.NETWORK_CONTOURS.map { contour ->
                                rootProject.layout.projectDirectory
                                    .file("${contour.module}/src/main/AndroidManifest.xml")
                            }
                        },
                    )
                    moduleManifests.from(
                        provider {
                            rootProject.allprojects.flatMap { project ->
                                project.file("src")
                                    .walkTopDown()
                                    .filter { it.isFile && it.name == "AndroidManifest.xml" }
                                    .toList()
                            }
                        },
                    )
                    contoursInArtifact.set(contoursInArtifactProvider)
                    report.set(layout.buildDirectory.file("reports/composition/manifest-${variant.name}.txt"))
                }

                val runtimeConfiguration = variant.runtimeConfiguration
                val dependencyCheck =
                    tasks.register<VerifyDependencyCompositionTask>("verifyDependencies$suffix") {
                        group = VERIFICATION_GROUP
                        description = "Checks that the $suffix runtime classpath carries no Google " +
                            "Play Services, Firebase, ads or third-party analytics; no guarded " +
                            "coordinate rides outside the white list, and each network contour " +
                            "present carries its client (PLATFORM-4, NFR-15/16/39, ADR-0017 §3)."
                        coordinates.set(
                            provider {
                                runtimeConfiguration.incoming.resolutionResult.allComponents
                                    .map(ResolvedComponentResult::getId)
                                    .map { it.displayName }
                                    .filterNot { it.startsWith("project ") }
                            },
                        )
                        versionCatalog.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
                        contoursInArtifact.set(contoursInArtifactProvider)
                        report.set(
                            layout.buildDirectory.file("reports/composition/dependencies-${variant.name}.txt"),
                        )
                    }

                // Проверка состава — часть сборки варианта, а не отдельный ритуал,
                // про который вспоминают перед выпуском (TDD §5.4).
                tasks.matching { it.name == "assemble$suffix" }.configureEach {
                    dependsOn(manifestCheck, dependencyCheck)
                }
                // `check` обязан видеть гейт у отладочного варианта и после введения контуров:
                // вариант называется `freeDebug`/`proDebug`, поэтому сравнение идёт
                // по типу сборки, а не по полному имени (SKL-153).
                if (variant.buildType == "debug") {
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

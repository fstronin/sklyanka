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
 * (TDD §5.4). Регистрируются на каждый включённый вариант; отладочный вариант
 * входит в общий прогон check, релизный — в сборку релиза.
 */
class ArtifactChecksConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                val suffix = variant.name.replaceFirstChar { it.uppercase() }
                // Один признак на обе проверки: манифест и граф зависимостей обязаны судить
                // о контуре одинаково (SKL-153, free/pro). Без этого free-вариант требовал бы
                // вендорскую координату, которой у него нет.
                val contourInArtifact = provider {
                    variant.runtimeConfiguration.incoming.resolutionResult.allComponents.any {
                        it.id.displayName.startsWith("project ${AnalyticsContourRules.PAID_CONTOUR_PROJECT}")
                    }
                }

                val manifestCheck = tasks.register<VerifyMergedManifestTask>("verifyManifest$suffix") {
                    group = VERIFICATION_GROUP
                    description = "Checks the merged manifest of the $suffix artifact: network " +
                        "permissions only together with the paid contour and only from it, network " +
                        "state forbidden always, package name and minSdk (PLATFORM-1..3, NFR-15/38, " +
                        "ADR-0010 §1/§5)."
                    mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    expectedPackageName.set(ProjectDefaults.APPLICATION_ID)
                    expectedMinSdk.set(ProjectDefaults.MIN_SDK)
                    contourManifest.set(
                        rootProject.layout.projectDirectory.file(
                            "${AnalyticsContourRules.PAID_CONTOUR_MODULE}/src/main/AndroidManifest.xml",
                        ),
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
                    paidContourPresent.set(contourInArtifact)
                    report.set(layout.buildDirectory.file("reports/composition/manifest-${variant.name}.txt"))
                }

                val runtimeConfiguration = variant.runtimeConfiguration
                val dependencyCheck =
                    tasks.register<VerifyDependencyCompositionTask>("verifyDependencies$suffix") {
                        group = VERIFICATION_GROUP
                        description = "Checks that the $suffix runtime classpath carries no Google " +
                            "Play Services, Firebase, ads or third-party analytics; no vendor " +
                            "dependency rides outside the contour white list, and the contour " +
                            "client is present (PLATFORM-4, NFR-15/16/39, ADR-0010 §5)."
                        coordinates.set(
                            provider {
                                runtimeConfiguration.incoming.resolutionResult.allComponents
                                    .map(ResolvedComponentResult::getId)
                                    .map { it.displayName }
                                    .filterNot { it.startsWith("project ") }
                            },
                        )
                        versionCatalog.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
                        paidContourPresent.set(contourInArtifact)
                        report.set(
                            layout.buildDirectory.file("reports/composition/dependencies-${variant.name}.txt"),
                        )
                    }

                // Проверка состава — часть сборки варианта, а не отдельный ритуал,
                // про который вспоминают перед выпуском (TDD §5.4).
                tasks.matching { it.name == "assemble$suffix" }.configureEach {
                    dependsOn(manifestCheck, dependencyCheck)
                }
                // `check` обязан видеть гейт у отладочного варианта и после введения контура:
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

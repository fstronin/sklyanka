package ru.heuri.sklyanka.gradle

import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Конвенция Android-библиотеки: core-ui, core-data, feature-*. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("sklyanka.quality")

        extensions.configure<LibraryExtension> {
            compileSdk = ProjectDefaults.COMPILE_SDK
            defaultConfig {
                minSdk = ProjectDefaults.MIN_SDK
                testInstrumentationRunner = ProjectDefaults.ANDROID_TEST_RUNNER
                consumerProguardFiles("consumer-rules.pro")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            lint {
                configureSklyankaLint(rootProject)
            }
        }
        configureKotlinJvmTarget()

        dependencies {
            add("androidTestImplementation", libs.findLibrary("junit4").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-core").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
        }
    }
}

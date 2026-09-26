package ru.heuri.sklyanka.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Конвенция чистого Kotlin/JVM-модуля — core-domain и acceptance.
 *
 * Android в classpath отсутствует физически, поэтому НФТ-46 выполняется
 * конструкцией, а не дисциплиной (TDD §3.2).
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("sklyanka.quality")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlinJvmTarget()

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }

        dependencies {
            add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
            add("testImplementation", libs.findBundle("domain-test").get())
            add("testRuntimeOnly", libs.findLibrary("junit5-engine").get())
            add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
        }
    }
}

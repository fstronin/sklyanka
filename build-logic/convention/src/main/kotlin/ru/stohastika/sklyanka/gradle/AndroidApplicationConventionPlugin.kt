package ru.stohastika.sklyanka.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Конвенция приложения: единственный модуль типа application — app. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("sklyanka.quality")

        extensions.configure<ApplicationExtension> {
            compileSdk = ProjectDefaults.COMPILE_SDK
            defaultConfig {
                applicationId = ProjectDefaults.APPLICATION_ID
                minSdk = ProjectDefaults.MIN_SDK
                targetSdk = ProjectDefaults.TARGET_SDK
                versionCode = 1
                versionName = "0.1.0"
                testInstrumentationRunner = ProjectDefaults.ANDROID_TEST_RUNNER
                // Приложение говорит на одном языке (НФТ-43), и это записано здесь, а не
                // только в строках: иначе в артефакт едут чужие переводы библиотек, а
                // система не знает, на каких языках приложение вообще разговаривает.
                // Механизмом закрепления языка это НЕ является — им служит конфигурация
                // контекста (ADR-0008): форму множественного числа выбирает система
                // по локали конфигурации, а не по составу ресурсов.
                resourceConfigurations += ProjectDefaults.INTERFACE_LOCALE
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            lint {
                configureSklyankaLint(rootProject)
            }
            configureSklyankaInstrumentation()
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

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
                // Версии по Р-26: versionCode монотонный (+1 на загрузку в магазин),
                // versionName — calver ГГГГ.ММ[.N], N — повторный выпуск в том же месяце.
                versionCode = 3
                versionName = "2026.10.2"
                testInstrumentationRunner = ProjectDefaults.ANDROID_TEST_RUNNER
                // Объявленные языки (НФТ-43, ПМ-14) записаны здесь, а не только
                // в строках: иначе в артефакт едут чужие переводы библиотек, а система
                // не знает, на каких языках приложение вообще разговаривает.
                // Механизмом выбора языка это НЕ является — им служит конфигурация
                // контекста (ADR-0008): форму множественного числа выбирает система
                // по локали конфигурации, а не по составу ресурсов.
                //
                // BCP-47 («en-US») переводится в квалификатор AAPT («en-rUS»):
                // дефис перед регионом AAPT не понимает и валит линковку ресурсов.
                resourceConfigurations += ProjectDefaults.INTERFACE_LOCALES.map(::resourceQualifier)
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
        skipDuplicateReleaseUnitTest()
        skipLintOnTestComponents()

        dependencies {
            add("androidTestImplementation", libs.findLibrary("junit4").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-core").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
            // Стенд устройства (SKL-97): androidTest-исходникам приложения и KSP тестового
            // графа нужны типы Room и DataStore. В core-data они объявлены implementation
            // и наружу не экспортируются, поэтому тестовое ребро объявляется здесь —
            // одним местом на приложение, а не руками в его build-файле.
            add("androidTestImplementation", libs.findLibrary("room-runtime").get())
            add("androidTestImplementation", libs.findLibrary("androidx-datastore-preferences").get())
        }
    }
}

/** BCP-47 в квалификатор AAPT: `en-US` → `en-rUS`, `zh-CN` → `zh-rCN`, `es` → `es`. */
private fun resourceQualifier(language: String): String {
    val parts = language.split('-', limit = 2)
    return if (parts.size == 2) "${parts[0]}-r${parts[1]}" else language
}

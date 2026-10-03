package ru.stohastika.sklyanka.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Hilt подключается в app, core-data и feature-*.
 *
 * core-domain Hilt не подключает: аннотации внедрения в чистом домене создают
 * зависимость от инструмента, от которого домен обязан быть свободен (TDD §7).
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-compiler").get())
            // Стенд устройства (SKL-97): приложение тестов из androidTest обязано быть
            // обработано Hilt — иначе @TestInstallIn и @CustomTestApplication не сгенерируются.
            add("androidTestImplementation", libs.findLibrary("hilt-android-testing").get())
            add("kspAndroidTest", libs.findLibrary("hilt-compiler").get())
        }
    }
}

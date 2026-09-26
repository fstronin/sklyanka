// Собственные правила detekt проекта «Склянка».
//
// Отдельная сборка, а не модуль проекта: её jar нужен detekt'у как плагин,
// и она не должна попадать в граф зависимостей приложения.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "ru.heuri.sklyanka"
version = "1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

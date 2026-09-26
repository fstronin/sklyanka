plugins {
    `kotlin-dsl`
}

group = "ru.heuri.sklyanka.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.build.gradle.plugin.android)
    compileOnly(libs.build.gradle.plugin.kotlin)
    compileOnly(libs.build.gradle.plugin.ksp)
    compileOnly(libs.build.gradle.plugin.detekt)
    compileOnly(libs.build.gradle.plugin.ktlint)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "sklyanka.android.application"
            implementationClass = "ru.heuri.sklyanka.gradle.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "sklyanka.android.library"
            implementationClass = "ru.heuri.sklyanka.gradle.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "sklyanka.android.compose"
            implementationClass = "ru.heuri.sklyanka.gradle.AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "sklyanka.jvm.library"
            implementationClass = "ru.heuri.sklyanka.gradle.JvmLibraryConventionPlugin"
        }
        register("quality") {
            id = "sklyanka.quality"
            implementationClass = "ru.heuri.sklyanka.gradle.QualityConventionPlugin"
        }
        register("releaseSigning") {
            id = "sklyanka.release.signing"
            implementationClass = "ru.heuri.sklyanka.gradle.ReleaseSigningConventionPlugin"
        }
        register("artifactChecks") {
            id = "sklyanka.artifact.checks"
            implementationClass = "ru.heuri.sklyanka.gradle.ArtifactChecksConventionPlugin"
        }
        register("projectChecks") {
            id = "sklyanka.project.checks"
            implementationClass = "ru.heuri.sklyanka.gradle.ProjectChecksConventionPlugin"
        }
        register("hilt") {
            id = "sklyanka.hilt"
            implementationClass = "ru.heuri.sklyanka.gradle.HiltConventionPlugin"
        }
    }
}

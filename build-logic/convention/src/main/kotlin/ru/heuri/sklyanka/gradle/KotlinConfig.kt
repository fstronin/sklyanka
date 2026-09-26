package ru.heuri.sklyanka.gradle

import com.android.build.api.dsl.Lint
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Единый JVM target по всем модулям — иначе классы из разных модулей не стыкуются. */
internal fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(ProjectDefaults.JVM_TARGET))
            // Предупреждения компилятора — не повод падать: содержательные запреты
            // живут в detekt, оформление — в ktlint (TDD §5.1).
            allWarningsAsErrors.set(false)
        }
    }
}

/**
 * Android Lint отвечает за платформенное: NewApi (ПЛ-1) и HardcodedText (НФТ-42).
 * Находка — ошибка, а не предупреждение: предупреждение, которое никто не читает,
 * создаёт видимость контроля (TDD §5.2).
 */
internal fun Lint.configureSklyankaLint(rootProject: Project) {
    lintConfig = rootProject.file("config/lint/lint.xml")
    abortOnError = true
    warningsAsErrors = false
    checkAllWarnings = false
    htmlReport = true
    xmlReport = true
    sarifReport = false
    error += setOf("NewApi", "HardcodedText", "InlinedApi")
}

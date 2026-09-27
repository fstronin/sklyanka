package ru.heuri.sklyanka.gradle

import com.android.build.api.dsl.CommonExtension
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

/**
 * Параметры инструментального прогона на эмуляторе (SKL-27).
 *
 * До этой задачи ни одна инструментальная проверка не выполнялась ни разу, и требований
 * к `connectedDebugAndroidTest` не было нигде: задача существует по умолчанию, а её
 * поведение по умолчанию рассчитано на быстрое устройство с включённой отрисовкой.
 *
 * Анимации выключаются на уровне сборки, а не руками оператора: проверка, падающая
 * потому, что кто-то не выполнил подготовительную команду, обвиняет продукт вместо стенда.
 * Таймаут установки поднят: отладочный артефакт ставится на программно отрисованный образ
 * без аппаратного ускорения дольше, чем отводит AGP по умолчанию, и его отказ выглядит
 * как отказ проверок.
 */
internal fun CommonExtension<*, *, *, *, *, *>.configureSklyankaInstrumentation() {
    testOptions {
        animationsDisabled = true
    }
    installation {
        timeOutInMs = INSTALL_TIMEOUT_MS
    }
}

/** Пять минут: установка на образ без аппаратного ускорения занимает минуты, а не секунды. */
private const val INSTALL_TIMEOUT_MS = 300_000

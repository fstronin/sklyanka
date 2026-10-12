package ru.stohastika.sklyanka.gradle

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
 * Юнит-тесты исполняются один раз на модуль — отладочным вариантом.
 *
 * Android заводит unit-тестовую задачу на каждый вариант, и `check` тянет обе:
 * `testDebugUnitTest` и `testReleaseUnitTest`. Предмета у второй проверки нет:
 * ни в одном модуле нет исходников `src/release` или `src/testRelease`, поэтому
 * релизный прогон исполняет те же классы `src/test` **второй раз** — ради другого
 * классного пути. Это не вторая проверка, а вторая оплата той же: около минуты
 * на прогон (`testReleaseUnitTest` 8 модулей) плюс сборка этих же классов в релизном
 * варианте (компиляция и KSP).
 *
 * Отладочный прогон строго шире: в нём, кроме `src/test`, участвует `src/testDebug`
 * стенда, которого у релизного варианта нет вовсе. Релизный вариант при этом
 * собирается и проверяется как прежде: `assembleRelease`, `verifyManifestRelease`,
 * `verifyDependenciesRelease`, `lintRelease` остаются на месте — снимается только
 * повторный прогон тех же тестов.
 *
 * Задача не удаляется и не прячется: она остаётся в графе и печатается как SKIPPED,
 * поэтому «тестов нет» и «тесты не гоняются» по-прежнему разные вещи.
 */
internal fun Project.skipDuplicateReleaseUnitTest() {
    tasks.matching { it.name == DUPLICATE_UNIT_TEST }.configureEach { enabled = false }
}

/** Релизный прогон юнит-тестов: та же проверка, что отладочная, второй раз. */
private const val DUPLICATE_UNIT_TEST = "testReleaseUnitTest"

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

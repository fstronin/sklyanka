// acceptance — сценарный уровень тестов (НФТ-45).
//
// Модуль чистый Kotlin/JVM: сценарии, которым не нужен эмулятор, не должны его тянуть.
// Файлы .feature читаются прямо из docs/tasks/SKL-NN/ — копия в тестовых ресурсах
// неизбежно разойдётся с оригиналом (TDD §6.2).

plugins {
    id("sklyanka.jvm.library")
}

dependencies {
    testImplementation(project(":core-domain"))
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
}

/** Каталог, из которого прогон берёт сценарии. По умолчанию — все задачи процесса. */
val featuresPath: String =
    (project.findProperty("sklyanka.acceptance.features") as String?) ?: "docs/tasks"

/** Строгий режим: нереализованный шаг валит прогон. По умолчанию выключен. */
val strict: Boolean =
    (project.findProperty("sklyanka.acceptance.strict") as String?)?.toBoolean() ?: false

val acceptanceReports = layout.buildDirectory.dir("reports/acceptance")

/**
 * Автоматический прогон: русские ключевые слова, теги @manual и @slow исключены.
 *
 * Реализованных шагов на этом этапе почти нет — это ожидаемо и правильно.
 * Сценарий без реализации отмечается нереализованным и НЕ засчитывается как пройденный:
 * иначе зелёный прогон означал бы «шагов нет», а не «поведение верно» (TDD §6.2).
 */
val acceptanceRun by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the Gherkin acceptance scenarios from $featuresPath (Cucumber-JVM, ru locale)."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.cucumber.core.cli.Main")
    outputs.dir(acceptanceReports)
    systemProperty("sklyanka.repo.root", rootProject.projectDir.absolutePath)
    doFirst {
        acceptanceReports.get().asFile.mkdirs()
    }
    args = listOf(
        "--glue", "ru.heuri.sklyanka.acceptance",
        "--tags", "not @manual and not @slow",
        "--plugin", "progress",
        "--plugin", "html:${acceptanceReports.get().asFile}/report.html",
        "--snippets", "camelcase",
        rootProject.file(featuresPath).absolutePath,
    )
    isIgnoreExitValue = !strict
}

/**
 * Чек-лист ручной проверки (TODO §22).
 *
 * Сценарии с @manual исключены из автоматического прогона и без отдельного отчёта
 * просто исчезают из виду. Сухой прогон печатает их списком.
 */
val acceptanceChecklist by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Prints the @manual scenarios as a device check-list."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.cucumber.core.cli.Main")
    outputs.dir(acceptanceReports)
    systemProperty("sklyanka.repo.root", rootProject.projectDir.absolutePath)
    doFirst {
        acceptanceReports.get().asFile.mkdirs()
    }
    args = listOf(
        "--glue", "ru.heuri.sklyanka.acceptance",
        "--tags", "@manual",
        "--dry-run",
        "--plugin", "pretty",
        "--plugin", "html:${acceptanceReports.get().asFile}/manual-checklist.html",
        rootProject.file(featuresPath).absolutePath,
    )
    isIgnoreExitValue = true
}

// Штатная задача test оставлена для обычных JUnit-тестов модуля: движок Cucumber
// из неё исключён, чтобы сценарный прогон был отдельной, управляемой командой.
tasks.test {
    useJUnitPlatform {
        excludeEngines("cucumber")
    }
}

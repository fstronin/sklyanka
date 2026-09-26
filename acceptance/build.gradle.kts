// acceptance — сценарный уровень тестов (НФТ-45).
//
// Модуль чистый Kotlin/JVM: сценарии, которым не нужен эмулятор, не должны его тянуть.
// Файлы .feature читаются прямо из docs/tasks/SKL-NN/ — копия в тестовых ресурсах
// неизбежно разойдётся с оригиналом (TDD §6.2).

import java.io.ByteArrayOutputStream

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
val strictUndefined: Boolean =
    (project.findProperty("sklyanka.acceptance.strict") as String?)?.toBoolean() ?: false

/**
 * Карантин: задачи, чьи .feature сейчас не разбираются Gherkin.
 *
 * Список существует не для того, чтобы прятать поломку, а чтобы её было видно:
 * каждый прогон печатает карантин отдельной строкой, и любой НОВЫЙ неразбираемый
 * файл валит прогон. Правка чужих артефактов — дело их задач.
 *
 * Сейчас список пуст: все .feature разбираются. Механизм оставлен на случай,
 * когда файл снова сломается и починка не может быть сделана сразу; заполняется
 * он только через -Psklyanka.acceptance.quarantine=SKL-NN,... и обязан
 * сокращаться, а не расти.
 *
 * Типовая причина поломки одна: шаг перенесён на вторую строку. Gherkin переносов
 * внутри шага не понимает — шаг должен быть одной строкой.
 */
val quarantine: Set<String> =
    ((project.findProperty("sklyanka.acceptance.quarantine") as String?) ?: "")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

val acceptanceReports = layout.buildDirectory.dir("reports/acceptance")

/** Каталоги задач, в которых лежат .feature; если их нет — сам указанный путь. */
fun featureTargets(): List<File> {
    val root = rootProject.file(featuresPath)
    if (!root.isDirectory) return emptyList()
    val nested = root.listFiles()
        ?.filter { dir -> dir.isDirectory && dir.listFiles { f -> f.name.endsWith(".feature") }?.isNotEmpty() == true }
        ?.sortedBy { it.name }
        .orEmpty()
    return nested.ifEmpty { listOf(root) }
}

/**
 * Один запуск Cucumber-CLI на каталог задачи.
 *
 * Отдельными запусками, а не одним общим: неразбираемый файл одной задачи иначе
 * обрывает весь прогон, и остальные задачи молча исчезают из отчёта.
 */
fun runCucumber(target: File, extraArgs: List<String>): String {
    val captured = ByteArrayOutputStream()
    project.javaexec {
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("io.cucumber.core.cli.Main")
        // Сценарии и шаги — на русском: без явной кодировки вывод превращается в «?????».
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
        systemProperty("sklyanka.repo.root", rootProject.projectDir.absolutePath)
        args = extraArgs + target.absolutePath
        standardOutput = captured
        errorOutput = captured
        isIgnoreExitValue = true
    }
    // Cucumber раскрашивает вывод; в отчёт и в чек-лист коды не нужны.
    return ansiCodes.replace(captured.toString(Charsets.UTF_8.name()), "")
}

/**
 * Автоматический прогон: русские ключевые слова, теги @manual и @slow исключены.
 *
 * Реализованных шагов на этом этапе почти нет — это ожидаемо и правильно.
 * Сценарий без реализации отмечается нереализованным и НЕ засчитывается как пройденный:
 * иначе зелёный прогон означал бы «шагов нет», а не «поведение верно» (TDD §6.2).
 */
val acceptanceRun by tasks.registering {
    group = "verification"
    description = "Runs the Gherkin acceptance scenarios from $featuresPath (Cucumber-JVM, ru locale)."
    dependsOn(tasks.named("testClasses"))
    inputs.dir(rootProject.file(featuresPath))
    inputs.files(sourceSets.test.get().output)
    outputs.dir(acceptanceReports)

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val lines = mutableListOf<String>()
        val broken = mutableListOf<String>()
        val quarantined = mutableListOf<String>()
        var undefinedSeen = false

        featureTargets().forEach { target ->
            val output = runCucumber(
                target,
                listOf(
                    "--glue",
                    "ru.heuri.sklyanka.acceptance",
                    "--tags",
                    "not @manual and not @slow",
                    "--plugin",
                    "progress",
                    "--snippets",
                    "camelcase",
                ),
            )
            reportDir.resolve("${target.name}.txt").writeText(output)

            val summary = cucumberSummary.find(output)?.value
            when {
                summary == null && target.name in quarantine -> {
                    quarantined += target.name
                    lines += "  QUARANTINE ${target.name}: feature file does not parse, see ${target.name}.txt"
                }
                summary == null -> {
                    broken += target.name
                    lines += "  BROKEN     ${target.name}: Cucumber could not run, see ${target.name}.txt"
                }
                else -> {
                    if (output.contains("undefined")) undefinedSeen = true
                    lines +=
                        "  ${if (output.contains("undefined")) "UNDEFINED " else "PASSED    "}${target.name}: $summary"
                }
            }
        }

        val report = buildString {
            appendLine("Acceptance run over $featuresPath (tags: not @manual and not @slow)")
            lines.forEach { appendLine(it) }
        }
        reportDir.resolve("summary.txt").writeText(report)
        report.trim().lines().forEach { logger.lifecycle(it) }

        if (broken.isNotEmpty()) {
            throw GradleException(
                "Cucumber could not parse or run these feature files: ${broken.joinToString()}. " +
                    "Fix the file, or quarantine it explicitly with " +
                    "-Psklyanka.acceptance.quarantine=... and open a task for it.",
            )
        }
        if (quarantined.isNotEmpty()) {
            logger.warn(
                "Acceptance: ${quarantined.joinToString()} stay in quarantine — their .feature does " +
                    "not parse. Their scenarios are NOT covered by this run.",
            )
        }
        if (undefinedSeen && strictUndefined) {
            throw GradleException(
                "Undefined scenarios found and strict mode is on " +
                    "(-Psklyanka.acceptance.strict=true). See ${reportDir.path}.",
            )
        }
    }
}

/**
 * Чек-лист ручной проверки (TODO §22).
 *
 * Сценарии с @manual исключены из автоматического прогона и без отдельного отчёта
 * просто исчезают из виду. Сухой прогон печатает их списком.
 */
val acceptanceChecklist by tasks.registering {
    group = "verification"
    description = "Prints the @manual scenarios as a device check-list."
    dependsOn(tasks.named("testClasses"))
    inputs.dir(rootProject.file(featuresPath))
    inputs.files(sourceSets.test.get().output)
    outputs.dir(acceptanceReports)

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val checklist = StringBuilder("Manual acceptance check-list (@manual) over $featuresPath\n")
        featureTargets().forEach { target ->
            val output = runCucumber(
                target,
                listOf(
                    "--glue",
                    "ru.heuri.sklyanka.acceptance",
                    "--tags",
                    "@manual",
                    "--dry-run",
                    "--plugin",
                    "pretty",
                ),
            )
            val scenarios = scenarioLine.findAll(output).map { it.groupValues[1].trim() }.toList()
            if (scenarios.isNotEmpty()) {
                checklist.appendLine("  ${target.name}:")
                scenarios.forEach { checklist.appendLine("    - $it") }
            }
        }
        reportDir.resolve("manual-checklist.txt").writeText(checklist.toString())
        checklist.toString().trim().lines().forEach { logger.lifecycle(it) }
    }
}

// Штатная задача test оставлена для обычных JUnit-тестов модуля: движок Cucumber
// из неё исключён, чтобы сценарный прогон был отдельной, управляемой командой.
tasks.test {
    useJUnitPlatform {
        excludeEngines("cucumber")
    }
}

val ansiCodes = Regex("\u001B\\[[;\\d]*m")
val cucumberSummary = Regex("""\d+ Scenarios?( \([^)]*\))?""")
val scenarioLine = Regex("""(?m)^\s*(?:Сценарий|Структура сценария|Scenario|Scenario Outline):\s*(.+?)\s*(?:#.*)?$""")

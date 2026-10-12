// acceptance — сценарный уровень тестов (НФТ-45).
//
// Модуль чистый Kotlin/JVM: сценарии, которым не нужен эмулятор, не должны его тянуть.
// Основной набор исходников — общий артефакт приёмки, видимый тестам других модулей
// (SKL-26, TDD §4.1); тестовый набор — шаги задач самого модуля и проверки мира.
// Файлы .feature — ресурсы этого модуля (acceptance/src/main/resources/features/SKL-NN.feature,
// SKL-111): сценарии принадлежат приёмке, а не слою документации; второй копии нет, расходиться нечему.

import java.io.ByteArrayOutputStream

plugins {
    id("sklyanka.jvm.library")
}

dependencies {
    // Основной набор исходников — общий артефакт приёмки (SKL-26, TDD §4.1): общий мир
    // сценария, общий словарь шагов, разбор слов и инструменты словаря. Его подключают
    // тестовые наборы модулей с экранами, поэтому зависимости, видимые в объявлениях
    // шагов и в типе мира, объявлены api, а не implementation (TDD §4.2).
    //
    // Android здесь нет и быть не может: модуль остаётся чистым Kotlin/JVM (ADR-0007),
    // и сценарий приёмки SKL-26 это проверяет составом зависимостей.
    api(project(":core-domain"))
    api(libs.cucumber.java)
    // Cucumber-CLI: экранный прогон модуля запускает его изнутри модульного теста
    // (SKL-24, ОВ-74), а обвязка запуска живёт здесь — одна на все модули.
    api(libs.cucumber.core)
    // Утверждения общих шагов. Api, потому что шаг модуля пишется теми же.
    api(libs.junit5.api)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.cucumber.junit.platform.engine)
    // Фабрика объектов Cucumber: состояние сценария приезжает в шаги параметром
    // конструктора, и экземпляр у сценария один на все классы шагов (TDD SKL-23 §5.1).
    testImplementation(libs.cucumber.picocontainer)
    testImplementation(libs.junit.platform.suite)
}

/** Каталог, из которого прогон берёт сценарии. По умолчанию — ресурсы этого модуля. */
val featuresPath: String =
    (project.findProperty("sklyanka.acceptance.features") as String?)
        ?: "acceptance/src/main/resources/features"

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

/**
 * Исходники шагов, объявленных вне этого модуля (SKL-26, TDD §6.1).
 *
 * Словарь шагов распределён по модулям: экранный шаг живёт в модуле своего экрана.
 * Проверка словаря читает эти файлы, значит они — её вход. Без этого правка шага
 * в модуле экрана не перезапускала бы проверку, и дубль проезжал бы на кеше — то есть
 * проверка существовала бы, но не срабатывала.
 */
val distributedStepSources: List<File> = rootProject.projectDir.listFiles()
    ?.filter { it.isDirectory && it.name != project.name }
    ?.flatMap { module ->
        File(module, "src").listFiles()
            ?.map { sourceSet -> File(sourceSet, "kotlin/ru/stohastika/sklyanka/acceptance") }
            .orEmpty()
    }
    ?.filter { it.isDirectory }
    ?.sortedBy { it.path }
    .orEmpty()

/**
 * Цели отдельных запусков Cucumber.
 *
 * Штатная раскладка плоская: один файл — одна задача (SKL-NN.feature), и целью бывает
 * сам файл, чтобы неразбираемый файл одной задачи не обрывал прогон остальных. Для
 * переопределённого пути поддерживается и прежняя раскладка каталогами задач; если ни
 * того ни другого нет — целью становится сам указанный путь.
 */
fun featureTargets(): List<File> {
    val root = rootProject.file(featuresPath)
    if (!root.isDirectory) return emptyList()
    val nested = root.listFiles()
        ?.filter { dir -> dir.isDirectory && dir.listFiles { f -> f.name.endsWith(".feature") }?.isNotEmpty() == true }
        ?.sortedBy { it.name }
        .orEmpty()
    if (nested.isNotEmpty()) return nested
    val flat = root.listFiles { f -> f.isFile && f.name.endsWith(".feature") }
        ?.sortedBy { it.name }
        .orEmpty()
    return flat.ifEmpty { listOf(root) }
}

/** Метка цели в отчёте: имя каталога задачи или имя файла сценариев без расширения. */
fun targetLabel(target: File): String = if (target.isDirectory) target.name else target.nameWithoutExtension

/**
 * Прогон всех целей одной задачей — **один запуск JVM на задачу**, а не на цель.
 *
 * Целей сто одна (по файлу сценариев на задачу), и отдельный `javaexec` на каждую означал
 * пять сотен запусков JVM в едином прогоне: проект `:acceptance` переставал быть тем,
 * чем он является (исполнением сценариев), и становился тем, чем измеряется время запуска
 * JVM. Разбор — в `CucumberBatch.kt`, который цели и исполняет.
 *
 * Изоляция целей осталась прежней: цели исполняются по одной, и исключение одной цели
 * не доходит до следующей — за это отвечает `CucumberBatch` (SKL-24, ОВ-78).
 *
 * @return вывод Cucumber по каждой цели, в порядке переданных целей
 */
fun runCucumber(targets: List<File>, extraArgs: List<String>): List<Pair<File, String>> {
    if (targets.isEmpty()) return emptyList()
    val captured = ByteArrayOutputStream()
    project.javaexec {
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("ru.stohastika.sklyanka.acceptance.run.CucumberBatch")
        // Сценарии и шаги — на русском: без явной кодировки вывод превращается в «?????».
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
        systemProperty("sklyanka.repo.root", rootProject.projectDir.absolutePath)
        args = extraArgs + batchTargets + targets.map { it.absolutePath }
        standardOutput = captured
        errorOutput = captured
        isIgnoreExitValue = true
    }
    return readBatch(captured.toString(Charsets.UTF_8.name()), targets)
}

/**
 * Разбор протокола `CucumberBatch`: отбивка, путь цели, длина вывода, вывод, код возврата.
 *
 * Длина, а не строка-разделитель: вывод Cucumber — чужой текст, и он вправе содержать
 * любую строку, какую мы выбрали бы разделителем. Отбивки — служебные знаки \u0001 и
 * \u0002: их в отчётах не печатает никто, и склейка двух целей в один блок невозможна.
 *
 * Разбор идёт до снятия цветовых кодов: длину считает `CucumberBatch` на том же тексте,
 * который печатает, — снять коды раньше значило бы сдвинуть все длины.
 */
fun readBatch(raw: String, targets: List<File>): List<Pair<File, String>> {
    val outputs = mutableListOf<Pair<File, String>>()
    var cursor = 0
    while (cursor < raw.length) {
        val open = raw.indexOf(batchOpen, cursor)
        if (open < 0) break
        val pathEnd = raw.indexOf(batchOpen, open + 1)
        val lengthEnd = raw.indexOf(batchOpen, pathEnd + 1)
        val close = raw.indexOf(batchClose, lengthEnd + 1)
        check(pathEnd > 0 && lengthEnd > pathEnd && close > 0) {
            "the batch protocol mark is broken: CucumberBatch.kt prints it, this script reads it — " +
                "the two must stay in step"
        }
        val path = raw.substring(open + 1, pathEnd)
        val length = raw.substring(pathEnd + 1, lengthEnd).toInt()
        val bodyStart = lengthEnd + 1
        check(bodyStart + length <= raw.length) {
            "the batch printed a shorter output than it declared for ${File(path).name}: " +
                "$length characters promised, ${raw.length - bodyStart} available"
        }
        val output = raw.substring(bodyStart, bodyStart + length)
        // Cucumber раскрашивает вывод; в отчёт и в чек-лист коды не нужны.
        outputs += File(path) to ansiCodes.replace(output, "")
        cursor = close
    }
    check(outputs.size == targets.size) {
        "the batch printed ${outputs.size} of ${targets.size} target outputs: " +
            outputs.joinToString { it.first.name }
    }
    return outputs
}

/**
 * Автоматический прогон: русские ключевые слова, теги @manual и @slow исключены.
 *
 * Реализованных шагов на этом этапе почти нет — это ожидаемо и правильно.
 * Сценарий без реализации отмечается нереализованным и НЕ засчитывается как пройденный:
 * иначе зелёный прогон означал бы «шагов нет», а не «поведение верно» (TDD §6.2).
 *
 * Упавший сценарий и нереализованный — разные вещи, и обходятся по-разному.
 * «Шаг не написан» — нормальное состояние задачи, до которой ещё не дошли: прогон печатает
 * UNDEFINED и сборку не валит (строгий режим для этого есть отдельным флагом).
 * «Проверка не прошла» — либо поведение неверно, либо неверно ожидание, и работой
 * в процессе это не бывает: прогон печатает FAILED и валит сборку немедленно.
 */
val acceptanceRun by tasks.registering {
    group = "verification"
    description = "Runs the Gherkin acceptance scenarios from $featuresPath (Cucumber-JVM, ru locale)."
    dependsOn(tasks.named("testClasses"))
    inputs.dir(rootProject.file(featuresPath))
    // И основной набор, и тестовый: словарь шагов живёт в основном (SKL-26, TDD §4.1),
    // и без него правка шага не перезапускала бы прогон — проверка проезжала бы на кеше.
    inputs.files(sourceSets.main.get().output, sourceSets.test.get().output)
    outputs.dir(acceptanceReports)

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val lines = mutableListOf<String>()
        val broken = mutableListOf<String>()
        val brokenGlue = mutableListOf<String>()
        val quarantined = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var undefinedSeen = false

        runCucumber(
            featureTargets(),
            listOf(
                "--glue",
                "ru.stohastika.sklyanka.acceptance",
                "--tags",
                automaticTags,
                "--plugin",
                "progress",
                "--snippets",
                "camelcase",
            ),
        ).forEach { (target, output) ->
            val label = targetLabel(target)
            reportDir.resolve("$label.txt").writeText(output)

            val summary = cucumberSummary.find(output)?.value
            when {
                summary == null && label in quarantine -> {
                    quarantined += label
                    lines += "  QUARANTINE $label: feature file does not parse, see $label.txt"
                }
                // Отказ Cucumber загрузить шаги — это сломанный словарь шагов, а не
                // сломанный документ задачи: карантин здесь не поможет, а диагноз обязан
                // называть настоящую причину (SKL-23, TDD §8.1).
                //
                // Проверяется до итога, а не вместо него: Cucumber при повторном объявлении
                // шага не отказывается запускаться, а печатает исключение в лог и выдаёт
                // «0 Scenarios» — то есть прогон без этой ветки становится зелёным и пустым.
                duplicateGlue.containsMatchIn(output) -> {
                    brokenGlue += label
                    lines += "  BROKEN-GLUE $label: a step is declared twice, " +
                        "run :acceptance:verifyAcceptanceSteps, see $label.txt"
                }
                // Любая другая поломка прогона: исключение вместо исполнения сценария —
                // это не «ни одного сценария», а не состоявшийся прогон.
                runtimeFailure.containsMatchIn(output) -> {
                    broken += label
                    lines += "  BROKEN     $label: Cucumber failed while running, " +
                        "see $label.txt"
                }
                summary == null -> {
                    broken += label
                    lines += "  BROKEN     $label: Cucumber could not run, see $label.txt"
                }
                else -> {
                    // Число падений берётся из строки со сценариями, а не из строки с шагами:
                    // ниже Cucumber печатает второй такой же итог по шагам, и он не в счёт.
                    val failures = scenarioFailures.find(summary)?.groupValues?.get(1)?.toInt() ?: 0
                    val undefined = output.contains("undefined")
                    if (undefined) undefinedSeen = true
                    if (failures > 0) failed += "$label ($failures)"
                    val status = when {
                        failures > 0 -> "FAILED    "
                        undefined -> "UNDEFINED "
                        else -> "PASSED    "
                    }
                    lines += "  $status$label: $summary"
                }
            }
        }

        val report = buildString {
            appendLine("Acceptance run over $featuresPath (tags: $automaticTags)")
            lines.forEach { appendLine(it) }
        }
        reportDir.resolve("summary.txt").writeText(report)
        report.trim().lines().forEach { logger.lifecycle(it) }

        if (brokenGlue.isNotEmpty()) {
            throw GradleException(
                "The acceptance step dictionary is broken: a step is declared twice, so Cucumber " +
                    "executed no scenario at all for ${brokenGlue.joinToString()} — it reported " +
                    "\"0 Scenarios\" instead of failing. This is not a document problem and quarantine " +
                    "does not apply: run :acceptance:verifyAcceptanceSteps — it names both declaration " +
                    "points.",
            )
        }
        if (broken.isNotEmpty()) {
            throw GradleException(
                "Cucumber could not parse or run these feature files: ${broken.joinToString()}. " +
                    "Fix the file, or quarantine it explicitly with " +
                    "-Psklyanka.acceptance.quarantine=... and open a task for it.",
            )
        }
        if (failed.isNotEmpty()) {
            throw GradleException(
                "Acceptance scenarios failed: ${failed.joinToString()}. A failing scenario is not work " +
                    "in progress: either the behaviour is wrong or the expectation is wrong, and both " +
                    "have to be resolved before the build goes green. See ${reportDir.path}.",
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
    // И основной набор, и тестовый: словарь шагов живёт в основном (SKL-26, TDD §4.1),
    // и без него правка шага не перезапускала бы прогон — проверка проезжала бы на кеше.
    inputs.files(sourceSets.main.get().output, sourceSets.test.get().output)
    outputs.dir(acceptanceReports)

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val checklist = StringBuilder("Manual acceptance check-list (@manual) over $featuresPath\n")
        runCucumber(
            featureTargets(),
            listOf(
                "--glue",
                "ru.stohastika.sklyanka.acceptance",
                "--tags",
                "@manual and not $postponedTag",
                "--dry-run",
                "--plugin",
                "pretty",
            ),
        ).forEach { (target, output) ->
            val scenarios = scenarioLine.findAll(output).map { it.groupValues[1].trim() }.toList()
            if (scenarios.isNotEmpty()) {
                checklist.appendLine("  ${targetLabel(target)}:")
                scenarios.forEach { checklist.appendLine("    - $it") }
            }
        }
        reportDir.resolve("manual-checklist.txt").writeText(checklist.toString())
        checklist.toString().trim().lines().forEach { logger.lifecycle(it) }
    }
}

/**
 * Сценарии, исполняемые экранным прогоном на JVM, а не этим (SKL-24, ОВ-74, ОВ-78).
 *
 * Модуль `acceptance` — чистый Kotlin/JVM по ADR-0007, и шага, обращающегося к
 * собранному экрану, в нём написать нечем. Раньше такие сценарии были неотличимы от
 * «шаг ещё не написан»: и то и другое печаталось как `undefined`. Тег `@screen`
 * различает их, а эта задача печатает их списком — чтобы исключение из прогона
 * оставалось видимым, а не превращалось в способ спрятать сценарий.
 *
 * Сами они исполняются в модульных тестах модуля с экранами (см. `ScreenAcceptanceTest`).
 */
val acceptanceScreenList by tasks.registering {
    group = "verification"
    description = "Prints the @screen scenarios: they run on the screen stand, not in this run."
    dependsOn(tasks.named("testClasses"))
    inputs.dir(rootProject.file(featuresPath))
    // И основной набор, и тестовый: словарь шагов живёт в основном (SKL-26, TDD §4.1),
    // и без него правка шага не перезапускала бы прогон — проверка проезжала бы на кеше.
    inputs.files(sourceSets.main.get().output, sourceSets.test.get().output)
    outputs.dir(acceptanceReports)

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val listing = StringBuilder(
            "Scenarios executed by the screen stand on the JVM (@screen), not by this run\n",
        )
        runCucumber(
            featureTargets(),
            listOf(
                "--glue",
                "ru.stohastika.sklyanka.acceptance",
                "--tags",
                "$screenTag and not $postponedTag",
                "--dry-run",
                "--plugin",
                "pretty",
            ),
        ).forEach { (target, output) ->
            val scenarios = scenarioLine.findAll(output).map { it.groupValues[1].trim() }.toList()
            if (scenarios.isNotEmpty()) {
                listing.appendLine("  ${targetLabel(target)}:")
                scenarios.forEach { listing.appendLine("    - $it") }
            }
        }
        reportDir.resolve("screen-scenarios.txt").writeText(listing.toString())
        listing.toString().trim().lines().forEach { logger.lifecycle(it) }
    }
}

/**
 * Сценарии, проверяемые только на устройстве (SKL-26, вторая половина `ОВ-78`).
 *
 * От `@manual` отличаются тем, что человек для них не нужен: нужна платформа. Прогон
 * на JVM их исключает и печатает списком — иначе они неотличимы от «шаг ещё не написан»,
 * и это ровно то, на что указывает `ОВ-78`.
 */
val acceptanceDeviceList by tasks.registering {
    group = "verification"
    description = "Prints the @device scenarios: they are checkable on a device only."
    dependsOn(tasks.named("testClasses"))
    inputs.dir(rootProject.file(featuresPath))
    // И основной набор, и тестовый: словарь шагов живёт в основном (SKL-26, TDD §4.1),
    // и без него правка шага не перезапускала бы прогон — проверка проезжала бы на кеше.
    inputs.files(sourceSets.main.get().output, sourceSets.test.get().output)
    outputs.dir(acceptanceReports)

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val listing = StringBuilder(
            "Scenarios checkable on a device only ($deviceTag), not by the JVM runs\n",
        )
        runCucumber(
            featureTargets(),
            listOf(
                "--glue",
                "ru.stohastika.sklyanka.acceptance",
                "--tags",
                "$deviceTag and not $postponedTag",
                "--dry-run",
                "--plugin",
                "pretty",
            ),
        ).forEach { (target, output) ->
            val scenarios = scenarioLine.findAll(output).map { it.groupValues[1].trim() }.toList()
            if (scenarios.isNotEmpty()) {
                listing.appendLine("  ${targetLabel(target)}:")
                scenarios.forEach { listing.appendLine("    - $it") }
            }
        }
        reportDir.resolve("device-scenarios.txt").writeText(listing.toString())
        listing.toString().trim().lines().forEach { logger.lifecycle(it) }
    }
}

/**
 * Сценарии, исполняемые походами на устройстве (SKL-140, Р-58, ADR 0014).
 *
 * От `@device` отличаются тем, что исполняются автоматом — группой `journey` оснастки
 * эмулятора, на настоящем файловом хранилище. Прогон на JVM их исключает и печатает
 * списком: исключение из прогона не должно становиться способом спрятать сценарий.
 *
 * Список называет походы отдельно от стражей реестра: первые помечены `@journey`,
 * вторые исполняет сам автоматический прогон.
 */
val acceptanceJourneyList by tasks.registering {
    group = "verification"
    description = "Prints the @journey scenarios: they run in the emulator group journey."
    dependsOn(tasks.named("testClasses"))
    inputs.dir(rootProject.file(featuresPath))
    // И основной набор, и тестовый: словарь шагов живёт в основном (SKL-26, TDD §4.1),
    // и без него правка шага не перезапускала бы прогон — проверка проезжала бы на кеше.
    inputs.files(sourceSets.main.get().output, sourceSets.test.get().output)
    outputs.dir(acceptanceReports)

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val listing = StringBuilder(
            "Scenarios executed by the journeys on a device ($journeyTag), not by the JVM runs\n",
        )
        runCucumber(
            featureTargets(),
            listOf(
                "--glue",
                "ru.stohastika.sklyanka.acceptance",
                "--tags",
                "$journeyTag and not $postponedTag",
                "--dry-run",
                "--plugin",
                "pretty",
            ),
        ).forEach { (target, output) ->
            val scenarios = scenarioLine.findAll(output).map { it.groupValues[1].trim() }.toList()
            if (scenarios.isNotEmpty()) {
                listing.appendLine("  ${targetLabel(target)}:")
                scenarios.forEach { listing.appendLine("    - $it") }
            }
        }
        reportDir.resolve("journey-scenarios.txt").writeText(listing.toString())
        listing.toString().trim().lines().forEach { logger.lifecycle(it) }
    }
}

/**
 * Запуск реестра шагов (SKL-23).
 *
 * Тем же способом, что и Cucumber-CLI: тот же classpath, та же кодировка, тот же
 * `sklyanka.repo.root` — иначе проверка словаря видела бы не то, что видит прогон.
 */
fun runStepDictionary(modes: List<String>): Pair<Int, String> {
    val captured = ByteArrayOutputStream()
    val result = project.javaexec {
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("ru.stohastika.sklyanka.acceptance.tools.StepDictionary")
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
        systemProperty("sklyanka.repo.root", rootProject.projectDir.absolutePath)
        args = modes
        standardOutput = captured
        errorOutput = captured
        isIgnoreExitValue = true
    }
    return result.exitValue to ansiCodes.replace(captured.toString(Charsets.UTF_8.name()), "")
}

/**
 * Проверка «один шаг — одно объявление» (SKL-23, НФТ-49).
 *
 * Ловит два нарушения: одинаковые выражения и два разных выражения, ловящих одну строку
 * сценария. Второе Cucumber сам замечает только когда исполнение доходит до этой строки,
 * а до строк нереализованных сценариев оно не доходит никогда — то есть в прогоне такой
 * дубль не видит никто.
 *
 * Проверка падает, а не предупреждает: она входит в check модуля и в verifyAll.
 */
val verifyAcceptanceSteps by tasks.registering {
    group = "verification"
    description = "Fails when an acceptance step is declared twice (same expression or same scenario line)."
    dependsOn(tasks.named("testClasses"))
    inputs.dir(rootProject.file(featuresPath))
    // И основной набор, и тестовый: словарь шагов живёт в основном (SKL-26, TDD §4.1),
    // и без него правка шага не перезапускала бы прогон — проверка проезжала бы на кеше.
    inputs.files(sourceSets.main.get().output, sourceSets.test.get().output)
    inputs.files(distributedStepSources).withPropertyName("distributedSteps").optional()
    outputs.file(acceptanceReports.map { it.file("step-dictionary.txt") })

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val (exitValue, output) = runStepDictionary(listOf("--list", "--verify"))
        val report = reportDir.resolve("step-dictionary.txt")
        report.writeText(output)
        // В лог — итог и нарушения; реестр целиком лежит в отчёте и печатается
        // задачей acceptanceSteps.
        output.lines().dropWhile { !it.startsWith(dictionarySummary) }.forEach { logger.lifecycle(it) }
        if (exitValue != 0) {
            throw GradleException(
                "The acceptance step dictionary has duplicates: a step is declared twice, or two " +
                    "declarations catch one scenario line. Both declaration points are named above " +
                    "and in ${report.path}. A domain step belongs to steps/shared and is declared once.",
            )
        }
    }
}

/**
 * Реестр шагов приёмки одной командой (критерий приёмки 14 SKL-23).
 *
 * Отвечает на вопрос «а такой шаг уже есть?» до того, как написана первая строка новых
 * шагов: раньше ответ получали, написав своё объявление и получив отказ прогона.
 */
val acceptanceSteps by tasks.registering {
    group = "verification"
    description = "Prints every acceptance step: its expression, where it is declared, shared or task-owned."
    dependsOn(tasks.named("testClasses"))
    inputs.dir(rootProject.file(featuresPath))
    // И основной набор, и тестовый: словарь шагов живёт в основном (SKL-26, TDD §4.1),
    // и без него правка шага не перезапускала бы прогон — проверка проезжала бы на кеше.
    inputs.files(sourceSets.main.get().output, sourceSets.test.get().output)
    inputs.files(distributedStepSources).withPropertyName("distributedSteps").optional()

    doLast {
        val reportDir = acceptanceReports.get().asFile.also { it.mkdirs() }
        val (_, output) = runStepDictionary(listOf("--list"))
        reportDir.resolve("step-dictionary.txt").writeText(output)
        output.trim().lines().forEach { logger.lifecycle(it) }
    }
}

tasks.named("check") {
    dependsOn(verifyAcceptanceSteps)
}

// Штатная задача test оставлена для обычных JUnit-тестов модуля: движок Cucumber
// из неё исключён, чтобы сценарный прогон был отдельной, управляемой командой.
tasks.test {
    useJUnitPlatform {
        excludeEngines("cucumber")
    }
}

val ansiCodes = Regex("\u001B\\[[;\\d]*m")

/**
 * Протокол обмена с `CucumberBatch`: аргумент-разделитель и отбивки блока цели.
 *
 * Значения обязаны совпадать с `CucumberBatch.kt`: там задача-исполнитель, здесь —
 * читатель. Разъедутся — прогон упадёт разбором, а не покажет пустой отчёт.
 */
val batchTargets = "--sklyanka-targets"
val batchOpen = '\u0001'
val batchClose = '\u0002'

/**
 * Тег сценариев, исполняемых экранным прогоном на JVM (SKL-24, ОВ-74).
 *
 * Исключение из автоматического прогона, а не сокрытие: их печатает
 * задача acceptanceScreenList, и их исполняет ScreenAcceptanceTest.
 */
val screenTag = "@screen"

/**
 * Тег сценариев, проверяемых только на устройстве (SKL-26, вторая половина `ОВ-78`).
 *
 * `@manual` означает «нужен человек или перезагрузка». Но есть сценарии, которым нужна
 * настоящая платформа без всякой перезагрузки: полноэкранная карточка поверх экрана
 * блокировки, различимость сигналов каналов уведомлений, путь до часов и обратно.
 * Автоматизировать их можно — но только на устройстве, и в прогоне на JVM они выглядели
 * как обычные нереализованные. Тег их различает, а задача acceptanceDeviceList печатает
 * списком: исключение из прогона не должно становиться способом спрятать сценарий.
 */
val deviceTag = "@device"

/**
 * Тег сценариев, исполняемых походами на устройстве (SKL-140, Р-58, ADR 0014).
 *
 * Поход — сквозной проход человека по приложению на настоящей платформе с настоящим
 * хранилищем; в модуле приёмки (чистый Kotlin/JVM по ADR-0007) ему делать нечего.
 * Исключение из прогона, а не сокрытие: список печатает задача acceptanceJourneyList,
 * исполняет — группа `journey` оснастки эмулятора (`tools/emulator/run-journeys.sh`).
 */
val journeyTag = "@journey"

/**
 * Тег отложенных сценариев (SKL-100).
 *
 * Сценарии удалённых контуров убираются вместе с кодом (П-7, Р-4): SKL-82 и SKL-83
 * вырезаны вместе с вендорным откатом и аналитическим контуром. Тег остаётся механизмом
 * для будущих отложек: исключение видно в отчёте задачи, а не молчит.
 */
val postponedTag = "@postponed"

/** Что берёт автоматический прогон: всё, кроме ручного, медленного, отложенного, экранного, устройственного и походного. */
val automaticTags =
    "not @manual and not @slow and not $postponedTag and not $screenTag and not $deviceTag and not $journeyTag"

/** Строка итога реестра шагов: с неё начинается то, что стоит печатать в лог. */
val dictionarySummary = "Словарь шагов:"

/** Отказ Cucumber загрузить шаги из-за второго объявления того же выражения. */
val duplicateGlue = Regex("DuplicateStepDefinitionException|[Dd]uplicate step definition")

/**
 * Прогон не состоялся: вместо исполнения сценария — исключение.
 *
 * Упавшая проверка выглядит иначе (FAILED и число упавших сценариев); здесь ловится
 * поломка самого прогона, при которой Cucumber честно печатает «0 Scenarios».
 */
val runtimeFailure = Regex("Exception while executing pickle|io\\.cucumber\\.core\\.exception\\.CucumberException")
val cucumberSummary = Regex("""\d+ Scenarios?( \([^)]*\))?""")

/** «32 Scenarios (2 failed, 30 passed)» — сколько сценариев не прошло. */
val scenarioFailures = Regex("""(\d+) failed""")
val scenarioLine = Regex("""(?m)^\s*(?:Сценарий|Структура сценария|Scenario|Scenario Outline):\s*(.+?)\s*(?:#.*)?$""")

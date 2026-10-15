package ru.stohastika.sklyanka.gradle

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register

/**
 * Проверки уровня всего проекта: направление зависимостей между модулями (§5.4)
 * и работоспособность самих правил статического анализа (§5.2).
 *
 * Применяется к корневому проекту.
 */
class ProjectChecksConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
        pluginManager.apply("base")
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        dependencies {
            add("detektPlugins", "ru.stohastika.sklyanka:detekt-rules:1.0")
        }

        val moduleGraph = tasks.register<VerifyModuleGraphTask>("verifyModuleDependencies") {
            group = VERIFICATION_GROUP
            description = "Fails on any module dependency pointing outwards (TDD 5.4)."
            graph.set(
                provider {
                    subprojects.associate { sub ->
                        sub.path to sub.configurations
                            .flatMap { configuration ->
                                configuration.dependencies.withType(ProjectDependency::class.java)
                            }
                            .map { it.path }
                            // AGP заводит ссылку модуля на самого себя в тестовых
                            // конфигурациях — это не ребро графа модулей.
                            .filterNot { it == sub.path }
                            .distinct()
                            .sorted()
                    }
                },
            )
            report.set(layout.buildDirectory.file("reports/modules/dependency-direction.txt"))
        }

        val moduleGraphRule = tasks.register<VerifyModuleGraphRuleTask>("verifyModuleDependencyRule") {
            group = VERIFICATION_GROUP
            description = "Proves the module dependency rule fires on deliberate violations (TDD 5.2)."
        }

        // Заведомо нарушающие фрагменты живут вне модулей и не входят в основную
        // сборку: они не компилируются и не попадают ни в один артефакт.
        val samples = tasks.register<Detekt>("detektViolationSamples") {
            group = VERIFICATION_GROUP
            description = "Runs detekt over the deliberate violation samples (TDD 5.2)."
            setSource(files(VIOLATION_SAMPLES))
            config.setFrom(file("config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            // Здесь падение ожидаемо: судит о нём verifyStaticAnalysisRules.
            ignoreFailures = true
            jvmTarget = ProjectDefaults.JVM_TARGET
            reports {
                xml.required.set(true)
                txt.required.set(true)
                html.required.set(false)
                sarif.required.set(false)
                md.required.set(false)
            }
            reports.xml.outputLocation.set(
                layout.buildDirectory.file("reports/detekt/violation-samples.xml"),
            )
            reports.txt.outputLocation.set(
                layout.buildDirectory.file("reports/detekt/violation-samples.txt"),
            )
            // Тот же ключ кеша, что и у обычных задач detekt: правила меняются чаще,
            // чем их координаты, и кеш обязан это замечать.
            inputs.files(configurations.named("detektPlugins"))
                .withPropertyName("sklyankaDetektRules")
                .withNormalizer(org.gradle.api.tasks.ClasspathNormalizer::class.java)
        }

        val rulesFire = tasks.register<VerifyStaticAnalysisFiresTask>("verifyStaticAnalysisRules") {
            group = VERIFICATION_GROUP
            description = "Requires every project rule to fire on its deliberate violation (TDD 5.2)."
            dependsOn(samples)
            detektReport.set(layout.buildDirectory.file("reports/detekt/violation-samples.xml"))
            expectedRules.set(PROJECT_RULES)
            report.set(layout.buildDirectory.file("reports/detekt/rule-enforcement.txt"))
        }

        tasks.named("check") {
            dependsOn(moduleGraph, moduleGraphRule, rulesFire)
        }

        // Единый проверочный прогон одной командой (НФТ-49, TODO §25).
        tasks.register("verifyAll") {
            group = VERIFICATION_GROUP
            description = "Build, static analysis, artifact composition checks and all test levels."
            // build корневого проекта собирает только корень — модули нужно назвать явно.
            dependsOn(subprojects.map { "${it.path}:build" })
            // Инструментальные и экранные тесты запустить негде, но они обязаны
            // компилироваться: иначе сломанный androidTest обнаружится на устройстве.
            dependsOn(ANDROID_MODULES.map { "$it:assembleDebugAndroidTest" })
            dependsOn(moduleGraph, moduleGraphRule, rulesFire)
            dependsOn(gradle.includedBuild("detekt-rules").task(":test"))
            // Проверка словаря шагов приёмки — часть единого прогона, а не отдельная
            // команда, которую никто не запускает (SKL-23, НФТ-49).
            dependsOn(
                ":acceptance:acceptanceRun",
                ":acceptance:acceptanceChecklist",
                ":acceptance:verifyAcceptanceSteps",
                // Сценарии, исполняемые экранным прогоном на JVM, печатаются списком:
                // исключение из сценарного прогона обязано оставаться видимым (SKL-24, ОВ-78).
                ":acceptance:acceptanceScreenList",
                // То же для сценариев, проверяемых только на устройстве (SKL-26, ОВ-78):
                // «шагу нужна платформа» перестаёт выглядеть как «шаг не написан».
                ":acceptance:acceptanceDeviceList",
                // И для походов (SKL-140, Р-58): они исполняются группой journey оснастки
                // эмулятора, и список обязан оставаться видимым в едином прогоне.
                ":acceptance:acceptanceJourneyList",
            )
        }
        }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"
        const val VIOLATION_SAMPLES = "tools/violations/kotlin"
        val ANDROID_MODULES = listOf(
            ":app",
            ":core-ui",
            ":core-data",
            ":feature-today",
            ":feature-meds",
            ":feature-reminder",
            ":feature-onboarding",
            ":feature-history",
            ":feature-settings",
            // Витрина «Про» и платный контур (SKL-122): их инструментальные наборы
            // и походы обязаны хотя бы компилироваться этим же прогоном.
            ":feature-pro",
            ":billing-appgallery",
            // Виджеты домашнего экрана (SKL-118): провайдеры и экран настройки виджета
            // проверяются устройством, и их инструментальный набор обязан компилироваться.
            ":feature-widgets",
        )
        val PROJECT_RULES = listOf(
            "HardcodedDesignToken",
            "HardcodedTextStyle",
            "RussianTextInCode",
            "DirectSystemClockAccess",
            "DestructiveRoomMigration",
            "AndroidImportInDomain",
            "MedicalDataInJournal",
        )
    }
}

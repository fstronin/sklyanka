package ru.heuri.sklyanka.gradle

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Статический анализ модуля.
 *
 * Роли разделены намеренно (TDD §5.1): ktlint говорит «переформатируй»,
 * detekt говорит «так делать нельзя». Смешивание приводит к тому, что игнорируют оба.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        extensions.configure<DetektExtension> {
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            parallel = true
            // Находка валит сборку: режим предупреждений не используется (TDD §5.2).
            ignoreFailures = false
            basePath = rootProject.projectDir.absolutePath
        }

        extensions.configure<KtlintExtension> {
            version.set("1.3.1")
            android.set(true)
            ignoreFailures.set(false)
            filter {
                exclude { element -> element.file.path.contains("/build/") }
            }
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget = ProjectDefaults.JVM_TARGET
            // Без этого jar с собственными правилами не входит в ключ кеша задачи:
            // правило можно поправить, а detekt отдаст результат прошлого прогона
            // и промолчит. Ошибка ровно того сорта, от которого защищает §5.2.
            inputs.files(configurations.named(DETEKT_PLUGINS))
                .withPropertyName("sklyankaDetektRules")
                .withNormalizer(ClasspathNormalizer::class.java)
            reports {
                xml.required.set(true)
                html.required.set(true)
                txt.required.set(true)
                sarif.required.set(false)
                md.required.set(false)
            }
        }

        dependencies {
            // Собственные правила проекта подключаются к detekt отдельной сборкой
            // config/detekt/rules — см. TDD §5.3.
            add(DETEKT_PLUGINS, "ru.heuri.sklyanka:detekt-rules:1.0")
        }
    }

    private companion object {
        const val DETEKT_PLUGINS = "detektPlugins"
    }
}

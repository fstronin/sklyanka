package ru.heuri.sklyanka.gradle

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Экранный и снимочный стенд на JVM (SKL-24).
 *
 * Подключается конвенцией, а не копипастой по модулям: пять модулей с экранами разойдутся
 * в настройках стенда на третьей правке, и разойдутся молча — снимки в одном из них
 * начнут сниматься в другом графическом режиме или в другой плотности, и сверка перестанет
 * что-либо означать.
 *
 * Чего стенд НЕ доказывает, написано в `docs/tasks/SKL-24/task.md` и `TDD.md §2`:
 * Robolectric — имитация Android, а не EMUI. Проверки уровня устройства остаются
 * в `src/androidTest` и остаются истиной про устройство.
 */
class ScreenTestsConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        @Suppress("UNCHECKED_CAST")
        val android = extensions.getByName("android") as CommonExtension<*, *, *, *, *, *>

        // Без ресурсов Robolectric поднимает пустой пакет: `stringResource` вернёт отказ,
        // а снимок — пустое полотно. Это главный переключатель стенда.
        android.testOptions.unitTests.isIncludeAndroidResources = true

        // Снимок платформы для Robolectric приезжает зависимостью (см. каталог версий),
        // а не скачиванием из самого Robolectric в обход управления зависимостями.
        val androidAll = configurations.create(ANDROID_ALL_CONFIGURATION) {
            isCanBeConsumed = false
            isCanBeResolved = true
            description = "Robolectric platform snapshot, resolved by Gradle instead of Robolectric."
        }
        val platformDir = layout.buildDirectory.dir("robolectric/platform")
        val syncPlatform = tasks.register<Sync>("syncRobolectricPlatform") {
            group = "build setup"
            description = "Puts the Robolectric platform snapshot where offline mode expects it."
            from(androidAll)
            into(platformDir)
        }

        // Конфигурация стенда: единственное место, где записано, чем именно рисуется снимок.
        val robolectricConfigDir = layout.buildDirectory.dir("generated/robolectric/test/resources")
        val writeRobolectricConfig = tasks.register<WriteRobolectricConfigTask>("writeRobolectricConfig") {
            group = "build setup"
            description = "Writes robolectric.properties shared by every module with screens (SKL-24)."
            sdk.set(ProjectDefaults.SCREEN_TEST_SDK)
            qualifiers.set(ProjectDefaults.SCREEN_TEST_QUALIFIERS)
            outputDirectory.set(robolectricConfigDir)
        }
        android.sourceSets.getByName("test").resources.srcDir(robolectricConfigDir)
        // Ресурсы тестов собираются отдельной задачей на вариант, и провайдер каталога
        // сам зависимости не приносит: без явного dependsOn файл появляется через прогон.
        tasks.matching { it.name.startsWith("process") && it.name.endsWith("UnitTestJavaRes") }
            .configureEach { dependsOn(writeRobolectricConfig) }

        val record = providers.gradleProperty(SNAPSHOT_MODE_PROPERTY).orNull == RECORD_MODE

        tasks.withType<Test>().configureEach {
            dependsOn(syncPlatform, writeRobolectricConfig)

            // Офлайн-режим: Robolectric берёт снимок платформы из каталога, а не из сети.
            systemProperty("robolectric.offline", "true")
            systemProperty("robolectric.dependency.dir", platformDir.get().asFile.absolutePath)
            // Сообщения Robolectric о недостающих тенях полезны ровно тогда, когда снимок
            // вышел пустым, — то есть тогда, когда их некому включить.
            systemProperty("robolectric.logging", "stdout")
            systemProperty("sklyanka.repo.root", rootProject.projectDir.absolutePath)

            // Эталоны лежат в репозитории рядом с модулем: снимок — такой же исходник,
            // как строка в strings.xml, и в ревью он обязан быть виден файлом.
            //
            // Свойство читают и Roborazzi (для отчётов), и сами тесты: путь эталона
            // собирается от него, а не от рабочего каталога прогона. Относительный путь
            // разрешается по рабочему каталогу, и это ровно тот способ получить эталоны
            // в неожиданном месте, который потом не объяснить.
            systemProperty("roborazzi.output.dir", file(SNAPSHOT_DIR).absolutePath)
            if (record) {
                systemProperty("roborazzi.test.record", "true")
            } else {
                // По умолчанию — сверка: прогон обязан падать на расхождении и на
                // отсутствующем эталоне, иначе снимки не проверка, а побочный продукт.
                systemProperty("roborazzi.test.verify", "true")
            }

            // Compose под Robolectric разворачивает настоящую композицию; штатных 512 МБ
            // на это не хватает, и падение выглядит как «тест упал без причины».
            maxHeapSize = TEST_HEAP
            testLogging {
                events("passed", "skipped", "failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }

        dependencies {
            add(ANDROID_ALL_CONFIGURATION, libs.findLibrary("robolectric-android-all").get())

            // Стенд живёт в отладочном варианте: манифест `ui-test-manifest`,
            // без которого Compose не поднимет активность, подключён как debug-зависимость,
            // а каталог компонентов `Showcase` лежит в `src/debug` (TDD §4.2).
            val debugTest = "testDebugImplementation"
            add(debugTest, platform(libs.findLibrary("androidx-compose-bom").get()))
            add(debugTest, libs.findLibrary("junit4").get())
            add(debugTest, libs.findLibrary("robolectric").get())
            add(debugTest, libs.findLibrary("androidx-test-core").get())
            add(debugTest, libs.findLibrary("androidx-test-junit").get())
            add(debugTest, libs.findLibrary("androidx-compose-ui-test-junit4").get())
            add(debugTest, libs.findLibrary("roborazzi").get())
            add(debugTest, libs.findLibrary("roborazzi-compose").get())
        }
    }

    private companion object {
        const val ANDROID_ALL_CONFIGURATION = "robolectricPlatform"
        const val SNAPSHOT_DIR = "screenshots"
        const val SNAPSHOT_MODE_PROPERTY = "sklyanka.snapshots"
        const val RECORD_MODE = "record"
        const val TEST_HEAP = "2g"
    }
}

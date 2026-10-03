// app — сборка графа Hilt, навигация, манифест, приёмники, уведомления, планировщик.
//
// Варианты сборки и подпись — sklyanka.release.signing (TDD §4.3).
// Проверки состава собранного артефакта — sklyanka.artifact.checks (TDD §5.5).

import java.util.Properties

plugins {
    id("sklyanka.android.application")
    id("sklyanka.android.compose")
    // Экранная проверка и эталонный снимок заставки идут на JVM (SKL-24).
    id("sklyanka.screen.tests")
    id("sklyanka.hilt")
    id("sklyanka.release.signing")
    id("sklyanka.artifact.checks")
}

/**
 * Режим измерения пилота (SKL-85, ПМ-20).
 *
 * Включается только явной Gradle-опцией `-Psklyanka.reminderMeasurement=true` для сборки
 * пилота; обычная и релизная сборки опцию не передают, и `BuildConfig.REMINDER_MEASUREMENT`
 * остаётся `false`. Аналитический A/B-контур сюда не подключается (ADR-0011).
 */
val reminderMeasurement: Boolean =
    (project.findProperty("sklyanka.reminderMeasurement") as String?)?.toBoolean() ?: false

/**
 * Конфигурация вендора аналитики (SKL-82, TDD §12.6, ПМ-18).
 *
 * `agconnect-services.json` — идентификаторы проекта AppGallery Connect; файл лежит вне git
 * и подкладывается извне. Путь задаётся Gradle-свойством
 * `sklyanka.agconnect.servicesDir` или ключом `agconnect.servicesDir` в `local.properties`.
 * Отсутствие файла — не отказ сборки: контур честно молчит (аналитический SDK не найдёт
 * конфигурацию и события дропнутся), а наличие — предусловие публикации SKL-22.
 */
val agconnectServicesDir: File? = resolveAgconnectServicesDir()
val agconnectServicesFile: File? = agconnectServicesDir?.resolve("agconnect-services.json")

fun resolveAgconnectServicesDir(): File? {
    val raw = providers.gradleProperty("sklyanka.agconnect.servicesDir").orNull
        ?: rootProject.file("local.properties")
            .takeIf { it.isFile }
            ?.let { file ->
                Properties().apply { file.inputStream().use { load(it) } }
                    .getProperty("agconnect.servicesDir")
            }
        ?: return null
    return rootProject.file(raw)
}

if (agconnectServicesFile?.isFile != true) {
    logger.lifecycle(
        "analytics vendor config is absent: the analytics contour stays silent " +
            "(provide sklyanka.agconnect.servicesDir to enable it; see SKL-82 TDD 12.6)",
    )
}

android {
    namespace = "ru.stohastika.sklyanka"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("boolean", "REMINDER_MEASUREMENT", reminderMeasurement.toString())
        // Стенд устройства (SKL-97): приложение тестов и Hilt-граф на проверку.
        // Конвенция оставляет стандартный раннер модулям core-data/feature-history.
        testInstrumentationRunner = "ru.stohastika.sklyanka.screen.support.DeviceTestRunner"
    }
    sourceSets {
        getByName("main") {
            // Каталог вендора становится assets только когда файл действительно есть:
            // пустой srcDir ломал бы сборку на чистой машине.
            agconnectServicesDir?.takeIf { agconnectServicesFile?.isFile == true }?.let { assets.srcDir(it) }
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))
    // Аналитический контур (SKL-82): порт объявлен доменом, адаптер вендора живёт здесь,
    // чтобы граф Hilt приложения поднял его и гейты проверяли один и тот же состав.
    implementation(project(":core-analytics"))
    implementation(project(":feature-today"))
    // Список, форма, карточка лекарства и архив курсов (SKL-09, SKL-10, SKL-11).
    implementation(project(":feature-meds"))
    // Перенос данных выгрузкой и загрузкой файлом (SKL-19). Модуль подключён, чтобы граф
    // Hilt проверялся на сборке: иначе связки переноса остались бы непроверенными до
    // первого запуска экрана.
    implementation(project(":feature-settings"))
    // Полноэкранное напоминание и диагностика напоминаний (SKL-13).
    implementation(project(":feature-reminder"))
    // История приёмов, приверженность за месяц и отчёт врачу (SKL-18).
    implementation(project(":feature-history"))
    // Экран разрешений EMUI (SKL-14).
    implementation(project(":feature-onboarding"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    // Заставка — штатным механизмом платформы, а не собственной Activity (TDD §6).
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Перепланирование будильников задачей, переживающей смерть процесса (SKL-13, ОВ-79).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)

    // Правило выбора экрана после заставки — арифметика по трём признакам,
    // и проверяется она на JVM: эмулятор для неё не нужен и недоступен.
    testImplementation(libs.junit4)
    // Передача отчёта врачу — suspend-функция с главным дисплеем (SKL-33):
    // тест подменяет Main тестовым диспетчером, иначе `runBlocking` на главном
    // потоке Robolectric ждёт сам себя.
    testImplementation(libs.kotlinx.coroutines.test)
    // Регрессия расположения DE-файла снимка (SKL-87): core-data не экспортирует DataStore
    // наружу, а тесту нужно записать через него и проверить, что файл лёг в DE.
    testDebugImplementation(libs.androidx.datastore.preferences)

    // Экранный прогон сценарной приёмки профилей (SKL-26). Модуль выбран не по удобству:
    // «Признаки чужого профиля есть на всех экранах профиля» (SKL-15) обходит шесть
    // экранов трёх разных модулей, и единственное место, где они существуют
    // одновременно, — app.
    //
    // Общий артефакт приёмки — мир сценария и весь словарь шагов; фабрика объектов
    // Cucumber раздаёт один мир всем классам шагов сценария (SKL-23, TDD §5.1).
    testDebugImplementation(project(":acceptance"))
    testDebugImplementation(libs.cucumber.picocontainer)
}

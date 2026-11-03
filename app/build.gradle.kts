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
 * Конфигурация вендора для биллингового контура (Р-37, Р-38, SKL-122).
 *
 * `agconnect-services.json` — идентификаторы проекта AppGallery Connect; файл лежит вне git
 * и подкладывается извне. Путь задаётся Gradle-свойством
 * `sklyanka.agconnect.servicesDir` или ключом `agconnect.servicesDir` в `local.properties`.
 * В первой публикации сетевого контура нет: файл сохранён для биллинга,
 * его отсутствие — не отказ сборки.
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

/**
 * Один файл конфигурации вендора как источник assets.
 *
 * Задача копирует **только** `agconnect-services.json`: содержимое его каталога в артефакт
 * не попадает. Соседние файлы называются в журнале сборки вслух — если человек положил
 * конфигурацию в папку с ключами, он узнает об этом до выпуска, а не после (SKL-122).
 */
val vendorAssets: TaskProvider<Copy>? = agconnectServicesFile
    ?.takeIf { it.isFile }
    ?.let { config ->
        val strays = agconnectServicesDir?.listFiles()
            ?.filter { it.name != config.name }
            ?: emptyList()
        if (strays.isNotEmpty()) {
            logger.lifecycle(
                "vendor config dir has ${strays.size} other file(s); they will NOT ship in the APK: " +
                    strays.joinToString { it.name },
            )
        }
        tasks.register<Copy>("vendorAssets") {
            from(config)
            into(layout.buildDirectory.dir("vendor-assets"))
        }
    }

if (agconnectServicesFile?.isFile != true) {
    logger.lifecycle(
        "vendor config is absent: kept for the billing contour only " +
            "(provide sklyanka.agconnect.servicesDir when billing ships; see SKL-122)",
    )
}

android {
    namespace = "ru.stohastika.sklyanka"
    buildFeatures {
        buildConfig = true
    }
    // Контур продукта (Р-86, ADR-0016): `free` — единственная поставляемая сборка,
    // `pro` — запасной вариант для будущего возврата платной модели. Измерение одно,
    // поэтому варианты называются `freeDebug`/`proDebug`, `freeRelease`/`proRelease`.
    // Подписанного `proRelease` нет: платная модель не поставляется (release-вариант
    // pro выключает sklyanka.release.signing), `pro` живёт отладочной сборкой и тестами.
    //
    // Платёжные модули подключены только к `pro` (§2 TDD): в `free` их нет ни в графе
    // зависимостей, ни в слитом манифесте. `INTERNET` в бесплатной сборке есть, но его
    // объявляет единственный контур — аналитика (SKL-165, ADR-0017 п. 1); платёжный контур
    // добавляет второй источник сети только в `pro`. Приёмка и эталоны платных поверхностей
    // удалены (Р-86 п. 3): `pro` держится компиляцией, DI-графом и unit-тестами.
    flavorDimensions += "contour"
    productFlavors {
        create("free") {
            dimension = "contour"
            isDefault = true
        }
        create("pro") {
            dimension = "contour"
        }
    }
    defaultConfig {
        buildConfigField("boolean", "REMINDER_MEASUREMENT", reminderMeasurement.toString())
        // Стенд устройства (SKL-97): приложение тестов и Hilt-граф на проверку.
        // Конвенция оставляет стандартный раннер модулям core-data/feature-history.
        testInstrumentationRunner = "ru.stohastika.sklyanka.screen.support.DeviceTestRunner"
    }
    sourceSets {
        getByName("main") {
            // В assets едет ровно файл конфигурации: каталог утащил бы в публикуемый APK
            // всё, что лежит рядом с ним, — включая ключ подписи, если конфигурация лежит
            // в папке с ключами (SKL-122, критика F12). Поэтому файл сначала копируется
            // в отдельный каталог сборки, а в assets объявляется уже он.
            vendorAssets?.let { assets.srcDir(it) }
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))
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
    // Витрина «Склянки Про»: одна модальная поверхность покупки (SKL-122, Р-67).
    // Только для контура `pro` (Р-86, ADR-0016 п. 1–2): в `free` витрины нет, и модуль
    // не попадает ни в граф зависимостей, ни в артефакт.
    "proImplementation"(project(":feature-pro"))
    // Платный сетевой контур: реализация порта магазина и единственное место сборки,
    // которому разрешена сеть (SKL-122, ADR-0010 п. 1–2). Подключён только к `pro`,
    // поэтому `INTERNET` объявляется исключительно в про-варианте.
    "proImplementation"(project(":billing-appgallery"))
    // Виджеты домашнего экрана (SKL-118): провайдеры и экран настройки приезжают слиянием
    // манифеста модуля, а обновление виджетов по событиям — точками графа в app.
    implementation(project(":feature-widgets"))

    // Сетевой контур аналитики (SKL-165, Р-93 п. 2): реализация порта `AnalyticsTransport`
    // поверх MyTracker и единственный манифест, объявляющий `INTERNET` в free-сборке.
    // Подключён к обоим вариантам: аналитика — часть бесплатного продукта, а `pro`
    // добавляет к ней ещё и платёжный контур.
    implementation(project(":core-analytics"))
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

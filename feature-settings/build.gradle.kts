// feature-settings — экраны раздела «Данные» настроек: перенос данных (SKL-19).
//
// Зависимостей на другие feature-* нет и быть не может: общее уезжает в core-*
// (проверяется задачей verifyModuleDependencies). Сам экран настроек — SKL-20;
// отсюда наружу ведут только события навигации.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    // Экранные проверки и эталонные снимки экрана переноса данных идут на JVM (SKL-24).
    id("sklyanka.screen.tests")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.heuri.sklyanka.feature.settings"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))

    // Домен отдаёт наружу Flow, и состояние экрана собирается из них.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Системный выбор места и файла (SAF, НФТ-17) — контракт результата активности:
    // собственного файлового менеджера продукт не рисует.
    implementation(libs.androidx.activity.compose)

    // Состояние экрана переноса проверяется на JVM: эмулятора в среде задачи нет,
    // а порядок шагов загрузки в нём и не нуждается.
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    // Экранный прогон сценарной приёмки переноса данных (SKL-26): Cucumber запускается
    // изнутри модульного теста под Robolectric, поэтому шаги видят собранный экран,
    // а настоящий кодек формата приезжает из core-data. Общий мир сценария и весь
    // словарь шагов приезжают общим артефактом приёмки; фабрика объектов Cucumber
    // раздаёт один мир всем классам шагов сценария.
    testDebugImplementation(project(":acceptance"))
    testDebugImplementation(libs.cucumber.picocontainer)
}

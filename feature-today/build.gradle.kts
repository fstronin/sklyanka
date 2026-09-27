// feature-today — единственный экранный модуль на этом этапе.
//
// Остальные feature-* заводятся задачами этапа 4 по мере появления экранов:
// пустой модуль ничего не проверяет и замедляет сборку (TDD §3.1).
// Зависимости на другие feature-* запрещены и проверяются verifyModuleDependencies.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    // Экранные проверки и эталонные снимки экрана «Сегодня» идут на JVM (SKL-24).
    id("sklyanka.screen.tests")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.heuri.sklyanka.feature.today"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))

    // Домен отдаёт сутки потоком, состояние экрана собирается из него.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Состояние экрана и разбор намерений проверяются на JVM: эмулятора в среде задачи
    // нет, а поведению ViewModel он и не нужен.
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Экранный прогон сценарной приёмки «Сегодня» (SKL-26): Cucumber запускается изнутри
    // модульного теста под Robolectric, поэтому шаги видят собранный экран. Общий мир
    // сценария и весь словарь шагов приезжают общим артефактом приёмки; фабрика объектов
    // Cucumber раздаёт один мир всем классам шагов сценария.
    testDebugImplementation(project(":acceptance"))
    testDebugImplementation(libs.cucumber.picocontainer)
}

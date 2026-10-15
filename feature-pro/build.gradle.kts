// feature-pro — витрина «Склянки Про» (SKL-122, Р-67).
//
// Одна модальная поверхность покупки: состояния «не оформлена», «активна», «магазин
// недоступен» — это одно состояние одной шторки, а не три экрана. Правила переходов живут
// в `core-domain` (`ProSheetSession`), здесь — сборка поверхности из компонентов `core-ui`
// и её `ViewModel`; ни цены, ни решения о доступности покупки модуль не считает.
//
// Зависимостей на другие feature-* нет и быть не может (правило verifyModuleDependencies):
// точка входа из настроек идёт событием навигации, а не вызовом экрана соседа.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    // Экранные проверки витрины и её эталонный снимок идут на JVM (SKL-24).
    id("sklyanka.screen.tests")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.stohastika.sklyanka.feature.pro"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))

    // Сессия шторки отдаёт состояние потоком, и экран собирает его в `ViewModel`.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    // Экранный прогон сценарной приёмки витрины: Cucumber запускается изнутри модульного
    // теста под Robolectric, и шаги видят собранный экран.
    testDebugImplementation(project(":acceptance"))
    testDebugImplementation(libs.cucumber.picocontainer)
}

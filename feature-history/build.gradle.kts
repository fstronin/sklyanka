// feature-history — экран истории приёмов, приверженность за месяц и отчёт врачу (SKL-18).
//
// Зависимостей на другие feature-* нет и быть не может: общее уезжает в core-*
// (проверяется задачей verifyModuleDependencies). Индикация чужого профиля берётся из
// core-ui как есть — собранная заново, она однажды исчезнет с этого экрана (ПРФ-12).

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    // Экранные проверки и эталонные снимки экрана истории идут на JVM (SKL-24).
    id("sklyanka.screen.tests")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.heuri.sklyanka.feature.history"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))

    // Домен отдаёт наружу Flow, и состояние экрана собирается из них.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // Системный выбор места для файла отчёта: результат чужой активности приходит
    // в composable, а не в ViewModel (НФТ-17).
    implementation(libs.androidx.activity.compose)

    // Состояние экрана и переключение месяца проверяются на JVM: устройства в среде
    // задачи нет, а арифметике по месяцу оно и не нужно.
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

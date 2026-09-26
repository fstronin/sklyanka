// feature-today — единственный экранный модуль на этом этапе.
//
// Остальные feature-* заводятся задачами этапа 4 по мере появления экранов:
// пустой модуль ничего не проверяет и замедляет сборку (TDD §3.1).
// Зависимости на другие feature-* запрещены и проверяются verifyModuleDependencies.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.heuri.sklyanka.feature.today"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))
}

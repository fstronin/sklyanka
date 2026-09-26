// app — сборка графа Hilt, навигация, манифест, приёмники, уведомления, планировщик.
//
// Варианты сборки и подпись — sklyanka.release.signing (TDD §4.3).
// Проверки состава собранного артефакта — sklyanka.artifact.checks (TDD §5.5).

plugins {
    id("sklyanka.android.application")
    id("sklyanka.android.compose")
    id("sklyanka.hilt")
    id("sklyanka.release.signing")
    id("sklyanka.artifact.checks")
}

android {
    namespace = "ru.heuri.sklyanka"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))
    implementation(project(":feature-today"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    // Заставка — штатным механизмом платформы, а не собственной Activity (TDD §6).
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Правило выбора экрана после заставки — арифметика по трём признакам,
    // и проверяется она на JVM: эмулятор для неё не нужен и недоступен.
    testImplementation(libs.junit4)
}

// feature-onboarding — экран разрешений EMUI и вызов пробного напоминания (SKL-14).
//
// Отдельный экранный модуль, а не часть feature-reminder: тот показывает напоминание
// и его диагностику, а этот проводит человека по цепочке разрешений до первого напоминания.
// Смысл разный, и общего кода у них нет — только порты домена (`TDD.md §3`).
// Зависимости на другие feature-* запрещены и проверяются verifyModuleDependencies.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    // Экранные проверки и эталонные снимки экрана разрешений идут на JVM (SKL-24).
    id("sklyanka.screen.tests")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.heuri.sklyanka.feature.onboarding"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Системный запрос разрешения на уведомления (ПЛ-13) — контракт результата активности:
    // своего диалога разрешений приложение не рисует и нарисовать не может.
    implementation(libs.androidx.activity.compose)

    // Состояние экрана проверяется на JVM: устройства в среде задачи нет, а расчёту
    // состояния карточек оно и не нужно.
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

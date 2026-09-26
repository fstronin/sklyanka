// feature-reminder — полноэкранное напоминание и экран диагностики (SKL-13).
//
// Отдельный экранный модуль, а не часть app: карточка напоминания — экран, и живёт она
// там же, где остальные экраны (`TDD.md §3`). В app остаются только точки входа системы:
// приёмники, каналы уведомлений и постановка будильников.
// Зависимости на другие feature-* запрещены и проверяются verifyModuleDependencies.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.heuri.sklyanka.feature.reminder"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    // Состояние карточки и подпись кнопки откладывания проверяются на JVM: эмулятора
    // в среде задачи нет, а поведению ViewModel он и не нужен.
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

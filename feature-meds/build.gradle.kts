// feature-meds — список лекарств и форма заведения и правки лекарства (SKL-09).
//
// Зависимостей на другие feature-* нет и быть не может: общее уезжает в core-*
// (проверяется задачей verifyModuleDependencies). Карточка лекарства (SKL-10) и архив
// (SKL-11) живут своими задачами; отсюда к ним ведут только события навигации.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    // Экранные проверки и эталонные снимки экранов лекарств идут на JVM (SKL-24).
    id("sklyanka.screen.tests")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.stohastika.sklyanka.feature.meds"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))

    // Домен отдаёт наружу Flow, и состояние экрана собирается из них.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Состояние экранов и правила формы проверяются на JVM: эмулятора в среде задачи нет,
    // а арифметика по черновику в нём и не нуждается.
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    // Экранный прогон сценарной приёмки списка и формы лекарств (SKL-26): Cucumber
    // запускается изнутри модульного теста под Robolectric, поэтому шаги видят собранный
    // экран. Общий мир сценария и весь словарь шагов приезжают общим артефактом приёмки;
    // фабрика объектов Cucumber раздаёт один мир всем классам шагов сценария.
    testDebugImplementation(project(":acceptance"))
    testDebugImplementation(libs.cucumber.picocontainer)
}

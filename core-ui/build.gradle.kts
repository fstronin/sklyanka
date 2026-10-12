// core-ui — дизайн-система: токены, типографика, базовые компоненты, общие строки.
//
// Единственный модуль, в пакете токенов которого литералы цвета и размера законны
// (НФТ-47). Значения токенов, тема, компоненты и каталог — SKL-05.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    // Экранные проверки и эталонные снимки идут на JVM (SKL-24): каталог состояний
    // дизайн-системы — первое, что обязано быть защищено снимком.
    id("sklyanka.screen.tests")
}

android {
    namespace = "ru.stohastika.sklyanka.ui"
}

dependencies {
    implementation(project(":core-domain"))

    // Обработчик системного возврата шторки: `BackHandler` живёт в activity-compose,
    // и правило «возврат закрывает шторку» обязано стоять в самой шторке, а не в каждом
    // её вызывающем по месту (SKL-143, `10-behavior.md` §5.3).
    implementation(libs.androidx.activity.compose)

    // Расчёт контраста, тонирование и полнота наборов проверяются на JVM: эмулятор
    // для арифметики по токенам не нужен, а без эмулятора он и недоступен (TDD §11.1).
    testImplementation(libs.junit4)
    // Экранный прогон сценарной приёмки (SKL-24, ОВ-74): Cucumber запускается изнутри
    // модульного теста под Robolectric, поэтому шаги видят собранный компонент.
    testDebugImplementation(libs.cucumber.java)
    testDebugImplementation(libs.cucumber.core)
}

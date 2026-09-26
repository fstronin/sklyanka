// Корневой build-файл: только агрегирующие проверки, никакой сборочной логики модулей —
// она в build-logic (TDD §11).

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // Плагины анализа объявлены здесь, чтобы их классы были на сборочном classpath
    // и конвенция sklyanka.quality могла их применить.
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    // Проверки уровня всего проекта: направление зависимостей и работоспособность правил.
    id("sklyanka.project.checks")
}

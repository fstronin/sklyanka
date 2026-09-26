// Состав сборки «Склянки».
//
// Направление зависимостей — внутрь: app → feature-* → core-ui/core-data → core-domain.
// Правило проверяется задачей verifyModuleDependencies, а не ревью (TDD §5.4).

pluginManagement {
    // Общая сборочная логика вынесена в отдельную сборку: параметры модулей
    // не должны расползаться по пяти build-файлам.
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Собственные правила detekt живут отдельной сборкой: их jar нужен detekt'у
// как плагин, а не проекту как зависимость.
includeBuild("config/detekt/rules") {
    // Иначе сборка зовётся по имени каталога («rules»), и ссылки на её задачи ломаются.
    name = "detekt-rules"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sklyanka"

include(":app")
include(":feature-today")
include(":core-ui")
include(":core-data")
include(":core-domain")
include(":acceptance")

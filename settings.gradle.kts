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
        // Репозиторий вендора сохранён для будущих сетевых контуров (SKL-50, H2, ПМ-31);
        // в первой публикации сетевого контура нет. Репозиторий ограничен его группами,
        // чтобы «случайная» библиотека не приехала отсюда молча.
        maven {
            url = uri("https://developer.huawei.com/repo/")
            content {
                includeGroup("com.huawei.hms")
                includeGroup("com.huawei.agconnect")
                includeGroup("com.huawei.android.hms")
                includeGroup("com.huawei.hmf")
            }
        }
    }
}

rootProject.name = "sklyanka"

include(":app")
include(":feature-today")
include(":feature-meds")
include(":feature-reminder")
include(":feature-onboarding")
include(":feature-history")
include(":feature-settings")
include(":core-ui")
include(":core-data")
include(":core-analytics")
include(":core-domain")
include(":acceptance")

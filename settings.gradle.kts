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
        // Репозиторий вендора сохранён для биллингового контура «Склянки Про» (Р-37, Р-38,
        // SKL-122); в первой публикации сетевого контура нет (Р-4). Репозиторий ограничен
        // его группами, чтобы «случайная» библиотека не приехала отсюда молча.
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
// Витрина «Склянки Про» (SKL-122): одна модальная поверхность покупки (Р-67).
// Проект остаётся в сборке ради контура `pro` (Р-86, ADR-0016): free-вариант `app`
// на него не ссылается, но `verifyAll` собирает модуль и его unit-тесты отдельно.
include(":feature-pro")
// Виджеты домашнего экрана: «Сегодня», неделя и по лекарству (SKL-118, Р-34).
// Модуль объявляет провайдеров виджетов и экран настройки — точку гейта «Про» (Р-32).
include(":feature-widgets")
// Платный контур: реализация порта магазина поверх AppGallery IAP (ADR-0010 п. 2).
// Один из двух сетевых контуров: объявляет `INTERNET` своим манифестом и подключается
// только к `pro`-варианту `app` (Р-86, ADR-0016 п. 1), поэтому free-манифест этой сети
// не получает; сам проект остаётся в сборке, чтобы его граф Hilt и unit-тесты
// проверялись `verifyAll`. Второй контур — `:core-analytics`.
include(":billing-appgallery")
// Аналитика по согласию (SKL-165, Р-93 п. 2, ADR-0017): реализация порта
// `AnalyticsTransport` поверх MyTracker и единственный манифест, объявляющий `INTERNET`
// в поставляемой сборке. Зависит только от `core-domain`; подключается к обоим вариантам
// `app`, потому что аналитика — часть бесплатной сборки.
include(":core-analytics")
include(":core-ui")
include(":core-data")
include(":core-domain")
include(":acceptance")

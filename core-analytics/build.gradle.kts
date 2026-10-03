// core-analytics — дом аналитического контура: сейчас заглушка, провайдер подключается
// новым решением (ПМ-31).
//
// Домен объявляет порт Analytics, порт AnalyticsSink и каталог событий; SDK вендора
// в релизе нет. Модуль остаётся местом будущего адаптера: подключение провайдера —
// замена заглушки в `di/AnalyticsModule` и возврат `AnalyticsBoundaryTest` к стражу SDK.
//
// Зависимость на core-domain — единственная разрешённая: контур наружу не течёт.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.stohastika.sklyanka.analytics"
}

dependencies {
    implementation(project(":core-domain"))

    // Домен отдаёт наружу Flow и suspend, и адаптер обязан видеть корутины на своём
    // classpath: в core-domain они подключены как implementation и транзитивно не приходят.
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

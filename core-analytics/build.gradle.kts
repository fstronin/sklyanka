// core-analytics — адаптер Huawei Analytics Kit: единственное место вызова SDK вендора.
//
// Домен объявляет порт Analytics и каталог событий; здесь — гейт согласия, debug-выключение
// и честная деградация без HMS Core (НФТ-39). Импорт com.huawei.* вне этого модуля
// запрещён проверкой AnalyticsBoundaryTest и правилом графа ModuleGraphRules.
//
// Зависимость на core-domain — единственная разрешённая: SDK наружу не течёт.

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

    implementation(libs.huawei.hianalytics)

    // A/B-контур (SKL-83): Remote Configuration — единственный канал вариантов;
    // agconnect-abtest приходит транзитивно, но объявлен явно, чтобы состав контура
    // был виден в каталоге версий и отчёте гейтов.
    implementation(libs.huawei.agconnect.remoteconfig)
    implementation(libs.huawei.agconnect.abtest)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

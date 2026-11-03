// billing-appgallery — платный сетевой контур «Склянки Про» (SKL-122, ADR-0010).
//
// Модуль знает вендора и не знает продукта: он исполняет порт `ProStore` из `core-domain`
// (товары, покупка, восстановление, состояние) поверх AppGallery IAP. Ни экраны, ни домен
// Huawei не видят — иначе витрину нельзя было бы проверить приёмкой без магазина,
// а песочница перестала бы быть ручной проверкой (docs/tasks/SKL-122/task.md).
//
// Один из двух сетевых контуров сборки (SKL-165, ADR-0017 п. 1): `INTERNET` объявляют
// этот модуль и `core-analytics`, и только они. `core-domain` и `core-data` о контуре
// не знают вовсе.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.stohastika.sklyanka.billing"
}

dependencies {
    implementation(project(":core-domain"))

    // Домен отдаёт исходы через корутины, и контур видит их на своём classpath.
    implementation(libs.kotlinx.coroutines.core)

    // Клиент магазина: цены, покупка, восстановление, экран управления подписками.
    implementation(libs.huawei.iap)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

// billing-appgallery holds the paid network contour of the pro variant (ADR-0010).
//
// The module knows the vendor and not the product: it implements the `ProStore` port of
// `core-domain` (products, purchase, restore, state) over AppGallery IAP. Neither the screens
// nor the domain see Huawei; otherwise the storefront could not be verified by acceptance
// without the store, and the sandbox would stop being a manual check.
//
// The only network contour of the build (ADR-0017 item 1): `INTERNET` is declared by this
// module and by nothing else. `core-domain` and `core-data` do not know the contour at all.

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

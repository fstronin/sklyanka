// core-data — Room, DAO, миграции, репозитории, мапперы, файл переноса.
//
// Схема и сущности появляются в SKL-04. Здесь подключается Room и проверяется,
// что тест с базой в памяти поднимается.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.heuri.sklyanka.data"
}

dependencies {
    implementation(project(":core-domain"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.room.testing)
}

// core-data — Room, DAO, миграции, репозитории, мапперы, файл переноса.
//
// Схема данных «Склянки» (SKL-04): профили, лекарства, приёмы и журнал критичных операций.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.hilt")
    // Формат файла переноса данных (SKL-19) описан объявлениями, а не ручной сборкой
    // строки: ручной разбор JSON — это второй парсер, который расходится с первым.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.heuri.sklyanka.data"

    sourceSets {
        // Экспортированные схемы Room нужны инструментальному тесту миграций как ресурсы:
        // MigrationTestHelper поднимает базу предыдущей версии именно из этих файлов (МГ-3).
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// МГ-1: JSON-схемы лежат в репозитории и входят в ревью. Изменение сущности
// без нового файла схемы — не принимается.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core-domain"))

    // Домен отдаёт наружу Flow, и core-data обязан видеть корутины на своём classpath:
    // в core-domain они подключены как implementation и транзитивно не приходят.
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // Файл переноса данных: разбор строгий по обязательным полям и терпимый
    // к незнакомым ключам при совпадающей версии формата (ПЕР-5, TDD §4 SKL-19).
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

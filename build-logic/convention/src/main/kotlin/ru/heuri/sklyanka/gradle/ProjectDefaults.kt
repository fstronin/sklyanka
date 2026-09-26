package ru.heuri.sklyanka.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Единые параметры сборки всех модулей.
 *
 * Расползшиеся по пяти build-файлам значения расходятся: в одном модуле minSdk
 * поднимают «на время», и требование ПЛ-1 перестаёт быть правдой незаметно.
 */
object ProjectDefaults {
    /** ПЛ-1, НФТ-38: Android 8.0. */
    const val MIN_SDK = 26

    /** НФТ-38: целевая версия — актуальная на момент задачи. */
    const val COMPILE_SDK = 35
    const val TARGET_SDK = 35

    /** ПЛ-2: имя пакета неизменяемо — к нему привязана заявка Wear Engine. */
    const val APPLICATION_ID = "ru.heuri.sklyanka"

    /** НФТ-41, ЧАС-7: шаблонные уведомления Wear Engine не принимают пакет длиннее. */
    const val PACKAGE_NAME_LIMIT = 28

    const val JVM_TARGET = "17"

    const val ANDROID_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

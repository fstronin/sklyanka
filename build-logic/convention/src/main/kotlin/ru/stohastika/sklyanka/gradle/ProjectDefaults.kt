package ru.stohastika.sklyanka.gradle

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

    /** ПЛ-2: имя пакета неизменяемо — к нему привязаны записи магазинов. */
    const val APPLICATION_ID = "ru.stohastika.sklyanka"

    const val JVM_TARGET = "17"

    const val ANDROID_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

    /**
     * Уровень API имитации Android на стенде экранных проверок (SKL-24).
     *
     * Тот же, на который собирается приложение: стенд, имитирующий другую версию
     * платформы, отвечает на вопрос, которого никто не задавал.
     */
    const val SCREEN_TEST_SDK = COMPILE_SDK

    /**
     * Среда снимка: русская локаль, ширина макетов 390 точек, плотность 1.
     *
     * Плотность 1 выбрана намеренно: точка равна пикселю, и снимок состояния весит
     * единицы килобайт. Снимок сравнивается с эталоном, а не разглядывается вместо
     * устройства, поэтому разрешение здесь — цена хранения, а не качество проверки.
     */
    const val SCREEN_TEST_QUALIFIERS = "ru-rRU-w390dp-h844dp-mdpi"

    /**
     * Объявленные языки интерфейса (НФТ-43, ПМ-14, ПМ-23). Состав живёт здесь, потому что
     * его знают и сборка, и манифест, и код: разойдясь, они разойдутся молча.
     *
     * Русский — язык набора по умолчанию и запасной для незнакомых языков телефона.
     */
    val INTERFACE_LOCALES: Set<String> = linkedSetOf("ru", "en-US", "es", "de", "zh-CN", "kk")
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

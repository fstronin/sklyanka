package ru.stohastika.sklyanka.screen.support

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Раннер проверок устройства (SKL-97, TDD §4.1).
 *
 * Поднимает приложение тестов вместо продуктового: Hilt-граф проверки обязан быть
 * тестовым, а `SklyankaApp` — не подниматься. Объявлен только в `app/build.gradle.kts`;
 * модули `core-data` и `feature-history` остаются на стандартном раннере конвенции.
 */
class DeviceTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, DeviceTestApplicationSpec_Application::class.java.name, context)
}

package ru.stohastika.sklyanka.screen.support

import android.app.Application
import dagger.hilt.android.testing.CustomTestApplication

/**
 * Приложение тестов экранного прогона (SKL-97, TDD §4.2).
 *
 * Продуктовый `SklyankaApp` не поднимается: его стартовые подписки — профиль владельца,
 * планирование приёмов, будильники, журналы и признак перерыва — неуправляемы, а состояние
 * проверки готовит сид (`DeviceTestSeed`). `@CustomTestApplication` генерирует подкласс
 * `DeviceTestApplicationSpec_Application`, который поднимает `DeviceTestRunner`.
 */
@CustomTestApplication(DeviceTestApplication::class)
interface DeviceTestApplicationSpec

abstract class DeviceTestApplication : Application()

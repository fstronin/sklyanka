package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission

/**
 * Предыстория самопроверки доставки (SKL-86, ПМ-21, ОВ-108).
 *
 * Разрешения выдаются в мире сценария — так же, как это делают другие задачи: состояния
 * читаются портами при каждом показе (НФТ-52), а не хранятся флагом. Шаги живут в общем
 * артефакте, потому что нужны и сценарному прогону, и обоим стендам (диагностика,
 * приложение через онбординг).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class DeliveryCheckContextSteps(private val world: AcceptanceWorld) {

    @Допустим("^все разрешения выданы$")
    fun allPermissionsAreGranted() {
        ReminderPermission.entries.forEach { permission -> world.system.grant(permission) }
    }

    /**
     * Вход в «Настроить надёжность» из плашки «Всё настроено» (SKL-84, SKL-86).
     *
     * Достаточно для всех входов: телефон Huawei, разрешения выданы, вендорские шаги
     * подтверждены человеком. Состояние читается чеклистом при каждом показе (НФТ-52).
     */
    @Допустим("^разрешения выданы, и все шаги надёжности подтверждены$")
    fun permissionsAndReliabilityStepsAreConfirmed() {
        world.onboarding.vendor = VendorProfile.EMUI
        allPermissionsAreGranted()
        runBlocking {
            world.onboarding.setAppStartupConfirmed(true)
            world.onboarding.setBatterySavingConfirmed(true)
            world.onboarding.setRecentsPinConfirmed(true)
        }
    }
}

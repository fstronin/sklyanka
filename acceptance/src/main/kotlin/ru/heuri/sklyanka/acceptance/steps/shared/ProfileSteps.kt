package ru.heuri.sklyanka.acceptance.steps.shared

import io.cucumber.java.en.Given
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld

/**
 * Общий словарь: профиль сценария.
 *
 * Строка «активен профиль «Я»» написана в Предысториях четырнадцати задач. Объявлена она
 * ровно здесь и ставит один и тот же профиль всем шагам сценария — и общим, и
 * принадлежащим задаче.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно, иначе прогон перестаёт быть контрактом задачи. Правило
 * RussianTextInCode разрешает пакет ru.heuri.sklyanka.acceptance вместе с подпакетами
 * именно поэтому (config/detekt/detekt.yml).
 */
class ProfileSteps(private val world: AcceptanceWorld) {

    @Given("^активен профиль «(.+)»$")
    fun theActiveProfileIs(name: String) {
        world.profiles.use(name)
    }
}

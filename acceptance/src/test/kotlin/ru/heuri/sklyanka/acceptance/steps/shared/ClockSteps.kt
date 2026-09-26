package ru.heuri.sklyanka.acceptance.steps.shared

import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld

/**
 * Общий словарь: момент сценария и его часовой пояс.
 *
 * Строка «сейчас …» написана в Предысториях двадцати задач и ставит **единственные** часы
 * сценария: все последующие шаги — и общие, и принадлежащие задаче — видят этот момент
 * (НФТ-37, НФТ-48).
 *
 * Перелёт объявлен здесь же. Раньше он менял пояс в состоянии SKL-06, а сценарий SKL-07
 * «Перелёт не меняет уже сделанную отметку» проверял своё состояние: сценарий был зелёным,
 * перелёта в нём не происходило. Теперь пояс меняется там же, где его читают проверки.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class ClockSteps(private val world: AcceptanceWorld) {

    @Given("^сейчас (.+), (\\d{2}:\\d{2})$")
    fun theCurrentMomentIs(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) устройство переходит в пояс на (\\S+) часа (восточнее|западнее)$")
    fun theDeviceChangesItsTimeZone(date: String, time: String, hours: String, direction: String) {
        world.rememberState()
        world.clock.moveTo(world.localMoment(date, time))
        val shift = RussianDates.count(hours).toLong()
        // Перелёт меняет пояс, а не момент: расписание остаётся местным временем человека,
        // а момент отметки — фактом в UTC (НФТ-34).
        world.clock.shiftZone(if (direction == EASTWARD) shift else -shift)
    }

    private companion object {
        const val EASTWARD = "восточнее"
    }
}

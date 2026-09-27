package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.steps.dateOf
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.model.ScheduleMode
import java.time.LocalTime

/**
 * Предыстория SKL-08: лекарства суток и уже сделанные отметки.
 *
 * Объявлена в общем артефакте, а не в модуле экрана (SKL-26, TDD §4.5): эти строки
 * доменные, и читает их не только экранный прогон «Сегодня».
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class TodayContextSteps(private val world: AcceptanceWorld) {

    @Given("^активен профиль «([^»]+)», принадлежащий владельцу телефона$")
    fun theActiveProfileBelongsToTheOwner(name: String) {
        world.profiles.use(name)
        assertTrue(world.profiles.get(name).isSelf, "«$name» must be the phone owner")
    }

    /**
     * Лекарства суток таблицей: название, доза с дозировкой, времена приёма.
     *
     * Из колонки «доза и дозировка» берётся только число дозы: единицы и дозировка —
     * предмет строки карточки, и проверяют их сценарии состава карточки, а не эта
     * Предыстория. Выдумывать разбор «2 впрыска в каждый носовой ход» здесь незачем.
     */
    @Given("^у профиля есть лекарства по расписанию:$")
    fun theProfileHasTheseScheduledMedications(table: DataTable) {
        table.entries().forEach { row ->
            val name = requireNotNull(row[MEDICATION_COLUMN]) { "the table must have a «лекарство» column" }
            val times = RussianDates.times(requireNotNull(row[TIME_COLUMN]) { "no «время приёма» in $row" })
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = times,
            )
            row[DOSE_COLUMN]?.let { dose ->
                world.medications.update(name) { it.copy(doseAmount = RussianDates.amount(leadingNumber(dose))) }
            }
        }
        // Приёмы порождаются здесь, в момент Предыстории, а не при сборке экрана.
        // Окно генерации начинается «сейчас» (SKL-06), и планировщик, запущенный после
        // того как сценарий перевёл часы на 14:05, приёма на 14:00 уже не создаёт —
        // сутки на экране оказались бы без него.
        world.plan()
    }

    @Given("^у профиля есть лекарство «([^»]+)» по потребности, не чаще (\\d+) раз в сутки$")
    fun theProfileHasAnOnDemandMedication(name: String, limit: Int) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            mode = ScheduleMode.PRN,
            maxPerDay = limit,
        )
    }

    /** Приёмы суток по расписанию Предыстории: порождает их планировщик, а не шаг. */
    @Given("^приёмы суток запланированы$")
    fun theIntakesOfTheDayArePlanned() {
        world.plan()
    }

    @Given("^приёмы на (\\d{2}:\\d{2}) отмечены принятыми$")
    fun theIntakesOfThatTimeAreTaken(time: String) {
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        val scheduled = world.medications.all()
            .filter { it.scheduleMode == ScheduleMode.SCHEDULED && moment.toLocalTime() in it.times }
        assertTrue(scheduled.isNotEmpty(), "no medication is scheduled at $time")
        scheduled.forEach { medication ->
            world.intakes.markTaken(nameOf(medication.id), moment)
        }
    }

    @Given("^(.+?) приёмы «([^»]+)» и «([^»]+)» на (\\d{2}:\\d{2}) отмечены принятыми$")
    fun theseTwoIntakesAreTaken(date: String, first: String, second: String, time: String) {
        val moment = world.dateOf(date).atTime(LocalTime.parse(time))
        listOf(first, second).forEach { name -> world.intakes.markTaken(world.medicationNamed(name), moment) }
    }

    @Given("^«([^»]+)» в этот день принят один раз$")
    fun thatOnDemandMedicationIsTakenOnce(name: String) {
        // Приём по потребности рождается уже принятым: он случился в момент нажатия (ПР-4).
        val resolved = world.medicationNamed(name)
        assertEquals(
            ScheduleMode.PRN,
            world.medications.get(resolved).scheduleMode,
            "«$resolved» must be an on-demand medication",
        )
        world.pressTakeOnDemand(resolved)
        world.intakes.outcomes.clear()
    }

    private fun nameOf(medicationId: String): String =
        requireNotNull(world.medications.names().firstOrNull { world.medications.get(it).id == medicationId })

    /** Ведущее число строки «1 таблетка · 50 мкг» или «2 впрыска в каждый носовой ход». */
    private fun leadingNumber(text: String): String =
        requireNotNull(LEADING_NUMBER.find(text.trim())) { "no dose amount in «$text»" }.value

    private companion object {
        const val MEDICATION_COLUMN = "лекарство"
        const val DOSE_COLUMN = "доза и дозировка"
        const val TIME_COLUMN = "время приёма"
        val LEADING_NUMBER = Regex("""^\d+(?:[.,]\d+)?""")
    }
}

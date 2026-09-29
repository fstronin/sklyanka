package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.model.ScheduleMode
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
    /**
     * Та же Предыстория, но профиль назван сценарием (SKL-31).
     *
     * Строка говорит о профиле, который сценарий только что объявил активным: заводить
     * лекарства чужому профилю она не имеет права, и промах виден отказом.
     */
    @Given("^у профиля «([^»]+)» есть лекарства по расписанию:$")
    fun theNamedProfileHasTheseScheduledMedications(profile: String, table: DataTable) {
        assertTrue(
            world.profiles.active == profile,
            "the table must speak about the active profile «${world.profiles.active}»",
        )
        theProfileHasTheseScheduledMedications(table)
    }

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

    // --- Предыстория: профиль без расписания и лекарств -------------------------

    @Given("^у активного профиля нет ни одного лекарства$")
    fun theActiveProfileHasNoMedications() {
        // Данность Предыстории, а не действие продукта: сценарию нужен первый запуск,
        // и лекарства убираются, а не архивируются — у архива остался бы список.
        val owner = world.profiles.get(world.profiles.active).id
        world.medications.all().filter { it.profileId == owner }.forEach { world.medications.remove(nameOf(it.id)) }
        assertTrue(
            world.medications.all().none { it.profileId == owner },
            "the active profile must have no medications at all",
        )
    }

    @Given("^у профиля нет приёмов по расписанию на (.+)$")
    fun theProfileHasNoScheduledIntakesOn(date: String) {
        // Лекарства по расписанию убираются из мира: планировать по ним нечего, и в сутки
        // сценария они не попадают. Приёмы по потребности остаются — они и есть предмет.
        val owner = world.profiles.get(world.profiles.active).id
        world.medications.all()
            .filter { it.profileId == owner && it.scheduleMode == ScheduleMode.SCHEDULED }
            .forEach { world.medications.remove(nameOf(it.id)) }
        assertTrue(
            world.medications.all().none { it.profileId == owner && it.scheduleMode == ScheduleMode.SCHEDULED },
            "the profile must have no scheduled medications on $date",
        )
    }

    @Given("^у профиля есть лекарство «([^»]+)» по потребности$")
    fun theProfileHasThatOnDemandMedication(name: String) {
        val resolved = RussianNames.resolve(name, world.medications.names())
        if (resolved == null) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                mode = ScheduleMode.PRN,
            )
        } else {
            assertEquals(
                ScheduleMode.PRN,
                world.medications.get(resolved).scheduleMode,
                "«$name» must be an on-demand medication",
            )
        }
    }

    @Given("^у профиля есть лекарство «([^»]+)» с приёмом в (\\d{2}:\\d{2})$")
    fun theProfileHasThatMedicationAt(medication: String, time: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            times = listOf(LocalTime.parse(time)),
        )
        // Приёмы рождает планировщик, а не шаг (SKL-06).
        world.plan()
    }

    /**
     * Ежедневный курс с дозой — Предыстория сценариев предупреждения на «Сегодня» (SKL-29).
     *
     * От [theProfileHasThatMedicationAt] отличается дозой: приёмы суток те же, но карточка
     * называет дозу, и сценарий проверяет её наравне с предупреждением. Приёмы порождает
     * планировщик, а не шаг (SKL-06).
     */
    @Given("^у профиля есть лекарство «([^»]+)» с приёмом в (\\d{2}:\\d{2}) ежедневно и дозой (\\S+) таблетк\\S*$")
    fun theProfileHasADailyMedicationWithDose(medication: String, time: String, dose: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            times = listOf(LocalTime.parse(time)),
        )
        world.medications.update(medication) { it.copy(doseAmount = RussianDates.amount(dose)) }
        world.plan()
    }

    @Given(
        "^(.+?) приём «([^»]+)» на (\\d{2}:\\d{2}) принят, " +
            "а приём «([^»]+)» на (\\d{2}:\\d{2}) пропущен без отметки$",
    )
    fun oneIntakeIsTakenAndTheOtherIsMissed(
        date: String,
        firstMedication: String,
        firstTime: String,
        secondMedication: String,
        secondTime: String,
    ) {
        val at = world.clock.nowLocal()
        val day = world.dateOf(date)
        val first = LocalTime.parse(firstTime)
        world.markExisting(
            medication = world.medicationNamed(firstMedication),
            at = day.atTime(first),
            markedAt = first.plusMinutes(MINUTES_AFTER),
            result = MarkResult.TAKEN,
        )
        world.intakes.pending(world.medicationNamed(secondMedication), day.atTime(LocalTime.parse(secondTime)))
        // Пропуск без отметки создаёт проход по просроченным, а не запись статуса
        // в обход правила (ПР-1).
        world.clock.moveTo(day.plusDays(1).atStartOfDay())
        world.sweep(SweepReason.TIMER)
        world.clock.moveTo(at)
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

        /** На сколько минут отметка позже назначенного времени: «принят в 08:05». */
        const val MINUTES_AFTER = 5L
        val LEADING_NUMBER = Regex("""^\d+(?:[.,]\d+)?""")
    }
}

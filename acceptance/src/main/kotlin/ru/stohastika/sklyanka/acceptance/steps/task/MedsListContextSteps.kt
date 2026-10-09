package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.MedicationUnits
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ArchiveOutcome
import ru.stohastika.sklyanka.domain.medication.MedicationListRules
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Предыстория SKL-09: лекарства списка — состав, счётчики, архив и «по потребности».
 *
 * Строки живут в общем артефакте, потому что они доменные: они заводят лекарства, приёмы
 * и архив в мире сценария, а экранный прогон читает тот же мир. Экранных утверждений
 * здесь нет вовсе — они в `feature-meds` (`SKL-26/TDD.md §4.3`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class MedsListContextSteps(private val world: AcceptanceWorld) {

    // --- Состав списка -----------------------------------------------------------

    /**
     * Таблица карточек: лекарство, описание и расписание.
     *
     * Из описания берётся форма выпуска и — если оно называет дозировку или количество —
     * сама величина: «50 мкг» — дозировка препарата, «2 шт» — доза одного приёма.
     * Без этого Предыстория заводила бы лекарства, отличные от названных в таблице.
     */
    @Given("^у профиля есть активные лекарства по расписанию:$")
    fun theProfileHasTheseActiveMedications(table: DataTable) {
        table.entries().forEach { row ->
            val name = requireNotNull(row[MEDICATION_COLUMN]) { "the table must have a «лекарство» column" }
            // SKL-68 называет столбец «времена» и добавляет расчётный «следующий приём»;
            // для Предыстории важно расписание — под ним и понимаются времена.
            val schedule = row[SCHEDULE_COLUMN] ?: row[TIMES_COLUMN]
            val times = RussianDates.times(requireNotNull(schedule) { "no «расписание»/«времена» in $row" })
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = times,
            )
            row[DESCRIPTION_COLUMN]?.let { description -> applyDescription(name, description) }
        }
    }

    /**
     * Пять активных курсов: четыре безымянных и «Магний B6».
     *
     * «Магний B6» назван намеренно: сценарии архива называют его, чтобы убрать один курс
     * из пяти и проверить счётчики («Активные · 4», «Архив · 13»). Пять при этом остаётся
     * пятью, и счётчик сценария 41 SKL-09 не меняется.
     */
    @Given("^у профиля пять активных лекарств по расписанию$")
    fun theProfileHasFiveScheduledMedications() {
        repeat(FIVE - 1) { index ->
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = "плановое лекарство ${index + 1}",
                times = listOf(DEFAULT_TIME),
            )
        }
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = "Магний B6",
            times = listOf(DEFAULT_TIME),
        )
    }

    @Given("^в архиве двенадцать лекарств$")
    fun theArchiveHoldsTwelveMedications() {
        repeat(TWELVE) { index ->
            val name = "архивное лекарство ${index + 1}"
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = listOf(DEFAULT_TIME),
            )
            world.medications.archive(name, world.clock.now())
        }
    }

    @Given("^у профиля есть активное лекарство «([^»]+)» по потребности$")
    fun theProfileHasThatActiveOnDemandMedication(medication: String) {
        // Ограничение сценарий не называет, но лекарство по потребности без него нарушало
        // бы ИНВ-17: берётся первое допустимое — один приём в сутки.
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            mode = ScheduleMode.PRN,
            maxPerDay = 1,
        )
    }

    @Given("^у профиля есть активное лекарство «([^»]+)»$")
    fun theProfileHasThatActiveMedication(medication: String) {
        val resolved = RussianNames.resolve(medication, world.medications.names()) ?: run {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                times = listOf(DEFAULT_TIME),
            )
            medication
        }
        assertFalse(world.medications.get(resolved).isArchived, "«$medication» must be active")
    }

    // --- Карточка «по потребности» -----------------------------------------------

    @Given("^последний раз его принимали (.+)$")
    fun theLastIntakeOfThatMedicationWasOn(date: String) {
        val name = requireNotNull(world.medications.last.takeIf { it.isNotEmpty() }) {
            "no medication was mentioned before the last intake"
        }
        val day = world.dateOf(date)
        // Принятое 19 сентября лекарство началось не позже этого дня: иначе история
        // приёма лежала бы за границей курса, и экран её не показал бы.
        world.medications.update(name) { if (it.startDate.isAfter(day)) it.copy(startDate = day) else it }
        world.intakes.markTaken(name, day.atTime(LAST_INTAKE_TIME))
    }

    @Given("^сегодня, (.+?), его ещё не принимали$")
    fun theOnDemandMedicationHasNotBeenTakenToday(date: String) {
        val today = world.dateOf(date)
        assertEquals(world.clock.today(), today, "the scenario moment is not $date")
        val name = requireNotNull(world.medications.last.takeIf { it.isNotEmpty() }) {
            "no medication was mentioned before the intake check"
        }
        assertTrue(
            world.intakes.of(name).none { it.scheduledAt.toLocalDate() == today && it.status == IntakeStatus.TAKEN },
            "«$name» must not have been taken on $date yet",
        )
    }

    @Given(
        "^(.+?) «([^»]+)» уже принят в (\\d{2}:\\d{2}), (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) " +
            "при ограничении (\\d+) раза в сутки$",
    )
    fun theOnDemandMedicationWasTakenThreeTimes(
        date: String,
        medication: String,
        first: String,
        second: String,
        third: String,
        limit: Int,
    ) {
        val name = RussianNames.resolve(medication, world.medications.names()) ?: medication.also {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                mode = ScheduleMode.PRN,
                maxPerDay = limit,
            )
        }
        world.medications.update(name) { it.copy(scheduleMode = ScheduleMode.PRN, maxPerDay = limit) }
        listOf(first, second, third).forEach { time ->
            world.intakes.markTaken(name, world.dateOf(date).atTime(LocalTime.parse(time)))
        }
    }

    // --- Остаток и период --------------------------------------------------------

    @Given("^у «([^»]+)» остаток (\\d+) таблеток, и порог напоминания о покупке сработал$")
    fun theStockThresholdOfThatMedicationHasFired(medication: String, amount: Int) {
        val name = scheduled(medication)
        world.medications.update(name) {
            it.copy(stockLeft = BigDecimal(amount), stockThresholdDays = THRESHOLD_DAYS)
        }
        assertTrue(
            MedicationListRules.stockThresholdReached(world.medications.get(name)),
            "the premise requires the stock threshold of «$medication» to have fired",
        )
    }

    @Given("^у «([^»]+)» остаток не ведётся, а курс бессрочный с (.+)$")
    fun theStockIsNotTrackedAndTheCourseIsOpenEnded(medication: String, date: String) {
        val name = scheduled(medication)
        world.medications.update(name) {
            it.copy(
                stockLeft = null,
                stockThresholdDays = null,
                startDate = world.dateOf(date),
                endDate = null,
            )
        }
    }

    // --- Архив -------------------------------------------------------------------

    @When("^лекарство «([^»]+)» отправлено в архив$")
    fun theMedicationIsArchived(medication: String) {
        // Счётчики снимаются до действия: проверка «уменьшился на единицу» живёт
        // в экранном прогоне, а сюда за счётчиками ей не дотянуться.
        world.medicationCountsBefore = world.medicationCounts()
        val id = world.medications.get(world.medicationNamed(medication)).id
        val useCase = ArchiveMedicationUseCase(world.repositories.medicationStore, world.planner())
        val outcome = runBlocking { useCase(id, ArchiveReason.MANUAL) }
        assertTrue(
            outcome is ArchiveOutcome.Archived,
            "«$medication» must land in the archive, not $outcome",
        )
    }

    // --- Вспомогательное ---------------------------------------------------------

    /**
     * Лекарство по имени с расписанием, если Предыстория его ещё не завела.
     *
     * Сценарии остатка и периода называют лекарство, не создавая его: строка «у «Магния B6»
     * остаток 9 таблеток» предполагает лекарство существующим. Заводится оно с тем же
     * расписанием, какое даёт ему таблица первого сценария задачи, — иначе предпосылка
     * «порог сработал» (нужен расход не меньше двух в сутки) не выполнялась бы.
     */
    private fun scheduled(medication: String): String =
        RussianNames.resolve(medication, world.medications.names()) ?: medication.also {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                times = DEFAULT_SCHEDULE,
            )
        }

    private fun applyDescription(medication: String, description: String) {
        val parts = description.split(SEPARATOR).map { it.trim() }
        world.medications.update(medication) { it.copy(form = releaseForm(parts.first())) }
        val middle = parts.getOrNull(1)?.takeIf { parts.size > 2 } ?: return
        val unit = middle.substringAfterLast(SPACE)
        val amount = RussianDates.amount(middle.substringBeforeLast(SPACE))
        if (unit in STRENGTH_UNITS) {
            world.medications.update(medication) { it.copy(strength = amount, strengthUnit = strengthCode(unit)) }
        } else {
            world.medications.update(medication) { it.copy(doseAmount = amount) }
        }
    }

    private fun releaseForm(label: String): MedicationForm = when (label) {
        "Таблетка" -> MedicationForm.TABLET
        "Капсула" -> MedicationForm.CAPSULE
        "Спрей" -> MedicationForm.SPRAY
        "Сироп" -> MedicationForm.SYRUP
        "Капли" -> MedicationForm.DROPS
        else -> MedicationForm.INJECTION
    }

    /** Слово единицы дозировки → код домена: закрытый список Р-45 (SKL-126). */
    private fun strengthCode(unit: String): String = MedicationUnits.strengthCode(unit)

    private companion object {
        const val MEDICATION_COLUMN = "лекарство"
        const val DESCRIPTION_COLUMN = "описание"
        const val SCHEDULE_COLUMN = "расписание"

        /** Столбец времён приёма у задач, которые не называют его «расписанием» (SKL-68). */
        const val TIMES_COLUMN = "времена"

        const val FIVE = 5
        const val TWELVE = 12
        const val THRESHOLD_DAYS = 5

        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)

        /** Расписание «Магния B6» из таблицы первого сценария задачи. */
        val DEFAULT_SCHEDULE: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))

        /** Время приёма, которого сценарий не называет: значение показывает только месяц. */
        val LAST_INTAKE_TIME: LocalTime = LocalTime.of(10, 0)

        const val SEPARATOR = " · "
        const val SPACE = " "

        /** Единицы препарата из таблицы сценария; всё остальное — количество за приём. */
        val STRENGTH_UNITS = MedicationUnits.STRENGTH_WORDS
    }
}

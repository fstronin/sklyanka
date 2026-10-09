package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Шаги SKL-09: следствия сохранения — группа `@save` (`SKL-26/TDD.md §8.1`).
 *
 * Группа доменная: «появились ли приёмы», «пересобралось ли окно напоминаний» и «в каком
 * профиле курс» решают домен и хранилище, а не разметка. Экранные следствия (приёмы
 * на «Сегодня», шапка формы правки) живут в модулях своих экранов.
 *
 * Сохранение идёт настоящим `AcceptanceWorld.save` — `SaveMedicationUseCase`, проверка,
 * запись и планирование тем же портом, что в продукте. Перепланирование напоминаний
 * приезжает следом за приёмами из этого же порта (`MedicationCardModule`): копии связки
 * здесь нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class MedicationSaveSteps(private val world: AcceptanceWorld) {

    /** Черновик, заполненный Предысторией: его сохраняет нажатие «Сохранить лекарство». */
    private var draft: MedicationDraft? = null

    private var saved: SaveResult? = null

    /** Название последнего сохранённого сценарием лекарства: «его» следующих проверок. */
    private var savedMedication: String = ""

    // --- Сохранение расписания перепланирует напоминания (НАП-11) ----------------

    /**
     * Курс заведён настоящим сохранением: окно приёмов и будильники в мире те же, какие
     * дало бы создание лекарства человеком, — иначе предпосылка «напоминание было» держалась
     * бы на честном слове.
     */
    @Допустим("^у лекарства «([^»]+)» было расписание «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})»$")
    fun theMedicationHadThatSchedule(medication: String, first: String, second: String) {
        val result = world.save(
            MedicationDraft(
                id = null,
                profileId = world.profiles.get(world.profiles.active).id,
                name = medication,
                form = MedicationForm.TABLET,
                doseAmount = BigDecimal.ONE,
                scheduleMode = ScheduleMode.SCHEDULED,
                times = listOf(LocalTime.parse(first), LocalTime.parse(second)),
                startDate = world.clock.today(),
            ),
        )
        assertTrue(result is SaveResult.Saved, "the medication must be created: $result")
        val intake = world.intakes.at(medication, world.clock.today().atTime(LocalTime.parse(second)))
        assertNotNull(
            world.reminders.alarmOf(intake.id),
            "the reminder at $second must already be scheduled before the schedule is changed",
        )
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек меняет расписание на «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})» и сохраняет$")
    fun thePersonChangesTheScheduleAndSaves(date: String, time: String, first: String, second: String) {
        world.clock.moveTo(world.localMoment(date, time))
        world.reminders.rememberScheduled()
        val name = world.medications.last
        savedMedication = name
        // Правка — тот же черновик, что и создание: форма правки заполняется из лекарства.
        val edited = MedicationDraft.of(world.medications.get(name)).copy(
            times = listOf(LocalTime.parse(first), LocalTime.parse(second)),
        )
        val result = world.save(edited)
        assertTrue(result is SaveResult.Saved, "the edited schedule must be saved: $result")
    }

    @Тогда("^ближайшее окно напоминаний перепланируется$")
    fun theReminderWindowIsRebuilt() {
        val before = requireNotNull(world.reminders.scheduledBefore()) {
            "the scenario never remembered the window of alarms"
        }
        assertNotEquals(
            before,
            world.reminders.scheduled(),
            "the window of alarms must be rebuilt after the schedule edit (НАП-11)",
        )
    }

    @Тогда("^ближайшее окно напоминаний перепланировано$")
    fun theReminderWindowWasRebuilt() {
        val before = requireNotNull(world.reminders.scheduledBefore()) {
            "the scenario never remembered the window of alarms"
        }
        assertNotEquals(
            before,
            world.reminders.scheduled(),
            "the window of alarms must be rebuilt after the schedule edit (НАП-11)",
        )
    }

    /**
     * Правка, не тронувшая расписание, будильники не трогает (НАП-11).
     *
     * Предпосылка — у курса уже есть будильники: без неё сравнение «не перепланировано»
     * шло бы между двумя пустыми наборами и ничего не доказывало. Набор собирает тот же
     * порт, что у сохранения в продукте.
     */
    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек меняет только связь с едой на «(.+?)» и сохраняет$")
    fun thePersonChangesOnlyTheFoodRelation(date: String, time: String, food: String) {
        world.clock.moveTo(world.localMoment(date, time))
        val name = world.medications.last
        savedMedication = name
        runBlocking { world.planner().plan(world.medications.get(name).id, PlanReason.MEDICATION_ADDED) }
        world.reminders.rememberScheduled()
        val edited = MedicationDraft.of(world.medications.get(name)).copy(foodRelation = foodRelation(food))
        val result = world.save(edited)
        assertTrue(result is SaveResult.Saved, "the edited medication must be saved: $result")
    }

    @Тогда("^ближайшее окно напоминаний не перепланируется$")
    fun theReminderWindowIsUntouched() {
        val before = requireNotNull(world.reminders.scheduledBefore()) {
            "the scenario never remembered the window of alarms"
        }
        assertEquals(
            before,
            world.reminders.scheduled(),
            "an edit that did not touch the schedule must not rebuild the window (НАП-11)",
        )
    }

    @Тогда("^напоминание на (.+?), (\\d{2}:\\d{2}) поставлено$")
    fun thatReminderIsScheduled(date: String, time: String) {
        // Лекарство называет либо шаг сохранения этого класса, либо последняя строка
        // Предыстории: возврат из архива (SKL-11) планирует будильники тем же окном.
        val medication = savedMedication.takeIf { it.isNotEmpty() } ?: world.medications.last
        val intake = world.intakes.at(medication, world.localMoment(date, time))
        assertNotNull(
            world.reminders.alarmOf(intake.id),
            "the reminder of «$medication» at $date, $time must be scheduled",
        )
    }

    @Тогда("^напоминания на (.+?), (\\d{2}:\\d{2}) больше нет$")
    fun thatReminderIsGone(date: String, time: String) {
        val moment = world.clock.momentOf(world.localMoment(date, time))
        assertTrue(
            world.reminders.scheduled().values.none { it == moment },
            "the reminder at $date, $time must be gone: ${world.reminders.scheduled().values}",
        )
    }

    // --- Лекарство по потребности не порождает приёмов (ПР-4, ПР-8) --------------

    @Допустим(
        "^человек заполнил форму: название «([^»]+)», форма выпуска «([^»]+)», доза (\\d+) \\S+, " +
            "дозировка (\\d+) \\S+, режим «По потребности», ограничение (\\d+) раза в сутки$",
    )
    fun thePersonFilledTheOnDemandForm(medication: String, form: String, dose: Int, strength: Int, limit: Int) {
        draft = MedicationDraft(
            id = null,
            profileId = world.profiles.get(world.profiles.active).id,
            name = medication,
            form = releaseForm(form),
            doseAmount = BigDecimal(dose),
            strength = BigDecimal(strength),
            strengthUnit = StrengthUnit.MILLIGRAM,
            scheduleMode = ScheduleMode.PRN,
            maxPerDay = limit,
            times = emptyList(),
            startDate = world.clock.today(),
        )
    }

    @Допустим("^человек заполнил форму нового лекарства: название «([^»]+)», форма «([^»]+)», вид не выбран$")
    fun thePersonFilledTheFormWithoutShape(medication: String, form: String) {
        // Вид не выбран намеренно: сценарий проверяет, что необязательное уточнение
        // не мешает сохранению (ОВ-88).
        draft = MedicationDraft(
            id = null,
            profileId = world.profiles.get(world.profiles.active).id,
            name = medication,
            form = releaseForm(form),
            doseAmount = BigDecimal.ONE,
            scheduleMode = ScheduleMode.SCHEDULED,
            times = listOf(DEFAULT_TIME),
            startDate = world.clock.today(),
        )
    }

    @Когда("^он нажимает «Сохранить лекарство»$")
    fun thePersonPressesSaveTheMedication() {
        saved = world.save(requireNotNull(draft) { "the scenario never filled the form" })
    }

    @Когда("^он сохраняет лекарство$")
    fun heSavesTheMedication() {
        saved = world.save(requireNotNull(draft) { "the scenario never filled the form" })
    }

    @Тогда("^лекарство сохраняется$")
    fun theMedicationIsSaved() {
        val result = requireNotNull(saved) { "the scenario never pressed «Сохранить лекарство»" }
        assertTrue(result is SaveResult.Saved, "the medication must be saved: $result")
        savedMedication = requireNotNull(draft).name
    }

    @Тогда("^у сохранённого лекарства вида таблетки нет$")
    fun theSavedMedicationHasNoShape() {
        val result = requireNotNull(saved) { "the scenario never saved the medication" }
        assertTrue(result is SaveResult.Saved, "the medication must be saved: $result")
        assertNull(world.medications.get(requireNotNull(draft).name).shape)
    }

    @Тогда("^ни одного ожидающего приёма «([^»]+)» не создаётся$")
    fun noPendingIntakeIsCreated(medication: String) {
        val name = world.medicationNamed(medication)
        assertTrue(
            world.intakes.of(name).none { it.isPending },
            "an on-demand medication must not produce pending intakes (ПР-4, ПР-8)",
        )
    }

    @Тогда("^ни одного напоминания для «([^»]+)» не планируется$")
    fun noReminderIsScheduled(medication: String) {
        val name = world.medicationNamed(medication)
        val ownIntakes = world.intakes.of(name).map { it.id }.toSet()
        assertTrue(
            world.reminders.scheduled().keys.none { it in ownIntakes },
            "nothing of «$name» must be in the window of alarms (НАП-10)",
        )
    }

    // --- Лекарство заводится в активном профиле (ИНВ-3, ПР-10) -------------------

    @Когда("^человек сохраняет в форме лекарство «([^»]+)»$")
    fun thePersonSavesThatMedication(medication: String) {
        val result = world.save(
            MedicationDraft(
                id = null,
                profileId = world.profiles.get(world.profiles.active).id,
                name = medication,
                form = MedicationForm.TABLET,
                doseAmount = BigDecimal.ONE,
                scheduleMode = ScheduleMode.SCHEDULED,
                times = listOf(DEFAULT_TIME),
                startDate = world.clock.today(),
            ),
        )
        assertTrue(result is SaveResult.Saved, "the medication must be saved: $result")
        savedMedication = medication
    }

    @Тогда("^«([^»]+)» появляется в списке лекарств профиля «([^»]+)»$")
    fun theMedicationAppearsInTheListOfThatProfile(medication: String, profile: String) {
        val active = activeMedicationsOf(profile)
        assertTrue(
            active.any { it.name == medication },
            "«$medication» must be in the active list of «$profile»: ${active.map { it.name }}",
        )
    }

    @Тогда("^в списке лекарств профиля «([^»]+)» его нет$")
    fun theMedicationIsAbsentFromTheListOfThatProfile(profile: String) {
        val active = activeMedicationsOf(profile)
        assertTrue(
            active.none { it.name == savedMedication },
            "«$savedMedication» must not be in the active list of «$profile» (ПР-10): ${active.map { it.name }}",
        )
    }

    // --- Правка существующего лекарства идёт той же формой -----------------------

    @Допустим("^существует лекарство «([^»]+)»$")
    fun theMedicationExists(medication: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            times = listOf(DEFAULT_TIME, SECOND_TIME),
        )
    }

    // --- Обвязка ------------------------------------------------------------------

    /** Срез списка активных лекарств профиля — тем же запросом, каким его читает экран (ПР-10). */
    private fun activeMedicationsOf(profile: String): List<Medication> {
        val profileId = world.profiles.get(profile).id
        return runBlocking { world.repositories.medicationStore.observeActive(profileId).first() }
    }

    private fun releaseForm(label: String): MedicationForm = when (label) {
        "Таблетка" -> MedicationForm.TABLET
        "Капсула" -> MedicationForm.CAPSULE
        "Спрей" -> MedicationForm.SPRAY
        "Сироп" -> MedicationForm.SYRUP
        "Капли" -> MedicationForm.DROPS
        else -> MedicationForm.INJECTION
    }

    /** Связь с едой в сценарии карточки: «после еды», «до еды», «во время еды». */
    private fun foodRelation(label: String): FoodRelation = when (label.lowercase()) {
        "до еды", "до" -> FoodRelation.BEFORE_MEAL
        "во время", "во время еды" -> FoodRelation.WITH_MEAL
        "после", "после еды" -> FoodRelation.AFTER_MEAL
        else -> FoodRelation.ANY
    }

    private companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
        val SECOND_TIME: LocalTime = LocalTime.of(20, 0)
    }
}

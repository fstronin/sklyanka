package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.InvariantViolationException
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.schedule.PlannedIntake
import ru.stohastika.sklyanka.domain.transfer.TransferRejectReason
import ru.stohastika.sklyanka.domain.transfer.ValidateSnapshot
import java.time.LocalTime

/**
 * Шаги приёмки SKL-107: инварианты модели на границах записи.
 *
 * Шаги идут настоящими хранилищами мира (`medicationStore`, `intakeStore`), которые
 * повторяют границы боевого `core-data` — мир приёмки и база ссылаются на одну
 * реализацию правил (`Invariants`), коды в отказах те же.
 */
class Integrity107Steps(private val world: AcceptanceWorld) {

    private var refusal: Throwable? = null
    private var intakesBefore: Map<String, List<Intake>> = emptyMap()

    // --- ИНВ-3: неизменяемость владельца -------------------------------------------------------

    @Допустим("^на устройстве есть профиль «(.+?)»$")
    fun thereIsAnotherProfileOnTheDevice(profile: String) {
        world.profiles.get(profile)
    }

    @Когда("^лекарство «(.+?)» сохраняют с владельцем «(.+?)»$")
    fun theMedicationIsSavedIntoAnotherProfile(medication: String, owner: String) {
        rememberIntakes(medication)
        val current = world.medications.get(medication)
        val takeover = current.copy(profileId = world.profiles.get(owner).id)
        refusal = runCatching {
            runBlocking { world.repositories.medicationStore.save(takeover) }
        }.exceptionOrNull()
    }

    @Тогда("^сохранение отвергнуто инвариантом «(INV-\\d+)»$")
    fun theSaveWasRefusedByInvariant(code: String) {
        assertRefusedBy(code)
    }

    @Тогда("^владельцем лекарства «(.+?)» остаётся профиль «(.+?)»$")
    fun theMedicationKeepsItsOwner(medication: String, owner: String) {
        val stored = world.medications.get(medication)
        assertEquals(world.profiles.get(owner).id, stored.profileId, "ИНВ-3: the owner must stay put")
    }

    // --- ИНВ-3 глазами переноса: совпавшее лекарство остаётся своим -----------------------------

    @Тогда("^владельцем лекарства на устройстве остаётся профиль «(.+?)»$")
    fun theDeviceMedicationsKeepTheirOwner(profile: String) {
        val owner = world.profiles.get(profile).id
        world.medications.all().forEach { medication ->
            assertEquals(owner, medication.profileId, "ИНВ-3: the file must not reassign a device medication")
        }
    }

    // --- ИНВ-10, ИНВ-11, ИНВ-14: согласованность полей отметки ----------------------------------

    @Тогда(
        "^приём «(.+?)» записан с назначенным временем (\\d{2}:\\d{2}) и отметкой в тот же момент$",
    )
    fun thePrnIntakeIsBornMarkedAtItsOwnMinute(medication: String, time: String) {
        val intake = world.intakes.of(medication).single()
        val expected = LocalTime.parse(time)
        assertEquals(IntakeStatus.TAKEN, intake.status, "ПР-4: a new on-demand intake is never pending")
        assertEquals(expected, intake.scheduledAt.toLocalTime(), "ПР-4: scheduled time is the press itself")

        val actedLocal = requireNotNull(intake.actedAt).atZone(world.clock.zone).toLocalDateTime()
        assertEquals(
            intake.scheduledAt.withSecond(0).withNano(0),
            actedLocal.withSecond(0).withNano(0),
            "ИНВ-14: scheduled time must equal the moment of marking",
        )
    }

    @Тогда("^все записанные приёмы согласованы со своими статусами$")
    fun everyStoredIntakeAgreesWithItsStatus() {
        world.intakes.all().forEach { intake ->
            assertTrue(
                Invariants.violations(intake).isEmpty(),
                "ИНВ-10, ИНВ-11: stored rows must be self-consistent, broke ${intake.id}: " +
                    Invariants.violations(intake),
            )
        }
    }

    @Когда("^хранилище записывает ожидающийся приём «(.+?)» с моментом отметки (\\d{2}:\\d{2})$")
    fun theStorageTriesToWriteAPendingRowWithMarkingFields(medication: String, markedAt: String) {
        rememberIntakes(medication)
        val stored = world.medications.get(medication)
        val poisoned = Intake(
            id = "poisoned-intake",
            medicationId = stored.id,
            scheduledAt = world.clock.today().atTime(stored.times.first()),
            status = IntakeStatus.PENDING,
            actedAt = world.clock.momentOf(world.clock.today().atTime(LocalTime.parse(markedAt))),
            source = IntakeSource.PHONE,
            actorProfileId = null,
        )
        refusal = runCatching {
            runBlocking { world.repositories.intakeStore.insertMissing(listOf(poisoned)) }
        }.exceptionOrNull()
    }

    @Тогда("^запись приёма отвергнута инвариантом «(INV-\\d+)»$")
    fun theIntakeWriteWasRefusedByInvariant(code: String) {
        assertRefusedBy(code)
    }

    @Тогда("^приёмы «(.+?)» не изменились$")
    fun theIntakesAreUnchanged(medication: String) {
        val before = requireNotNull(intakesBefore[medication]) { "capture the intakes before the attempt" }
        assertEquals(before, world.intakes.of(medication), "the refused write must leave the history untouched")
    }

    // --- ИНВ-15: приём рождается внутри курса ----------------------------------------------------

    @Тогда("^все приёмы «(.+?)» лежат в периоде курса$")
    fun everyPlannedIntakeLiesInsideTheCourse(medication: String) {
        val stored = world.medications.get(medication)
        val rows = world.intakes.of(medication)
        assertTrue(rows.isNotEmpty(), "the plan must have generated the course days")
        rows.forEach { intake ->
            val day = intake.scheduledAt.toLocalDate()
            assertTrue(
                !day.isBefore(stored.startDate) && (stored.endDate == null || !day.isAfter(stored.endDate)),
                "ИНВ-15: ${intake.scheduledAt} outside ${stored.startDate}..${stored.endDate}",
            )
        }
    }

    @Когда("^хранилище дописывает в план «(.+?)» приём на (.+?) в (\\d{2}:\\d{2})$")
    fun theStorageTriesToAppendAMomentOutsideThePlan(medication: String, date: String, time: String) {
        rememberIntakes(medication)
        val stored = world.medications.get(medication)
        refusal = runCatching {
            runBlocking {
                world.repositories.intakeStore.applyPlan(
                    profileId = stored.profileId,
                    medicationId = stored.id,
                    diff = IntakePlanDiff(
                        // Доза дня плану не важна: строка рождается ежедневным курсом,
                        // у которого снимка дозы нет.
                        toCreate = listOf(
                            PlannedIntake(scheduledAt = world.localMoment(date, time), doseAmount = null),
                        ),
                        toDelete = emptyList(),
                        unchanged = 0,
                    ),
                )
            }
        }.exceptionOrNull()
    }

    @Тогда("^применение плана отвергнуто инвариантом «(INV-\\d+)»$")
    fun thePlanApplyWasRefusedByInvariant(code: String) {
        assertRefusedBy(code)
    }

    // --- ИНВ-18: подпись отметившего глазами файла ------------------------------------------------

    @Когда("^в выгруженном файле приём «(.+?)» за (.+?), (\\d{2}:\\d{2}) подписан профилем этого же лекарства$")
    fun theFileIntakeIsSignedByItsOwnMedicationProfile(medication: String, date: String, time: String) {
        val base = world.transfer.snapshot()
        val stored = world.medications.get(medication)
        val moment = world.localMoment(date, time)
        val target = base.intakes.first { it.medicationId == stored.id && it.scheduledAt == moment }
        val signed = target.copy(actorProfileId = stored.profileId)
        world.transfer.prepare(base.copy(intakes = base.intakes.map { if (it.id == target.id) signed else it }))
    }

    @Тогда("^проверка файла отклоняет его как «битую запись» с кодом (INV-\\d+)$")
    fun theFileCheckRejectsTheSignedIntake(code: String) {
        val snapshot = requireNotNull(world.transfer.prepared) { "tamper the file before checking it" }
        val reject = ValidateSnapshot()(snapshot)
        assertEquals(TransferRejectReason.INVALID_RECORD, reject?.reason)
        assertEquals("intakes:$code", reject?.detail)
    }

    // --- Вспомогательное ---------------------------------------------------------------------------

    private fun rememberIntakes(medication: String) {
        intakesBefore = intakesBefore + (medication to world.intakes.of(medication))
    }

    private fun assertRefusedBy(code: String) {
        val violation = refusal as? InvariantViolationException
        assertTrue(
            violation != null,
            "the write must be refused by the invariant $code, got: $refusal",
        )
        assertTrue(
            violation!!.violations.contains(code),
            "the refusal must name $code, named ${violation.violations}",
        )
    }
}

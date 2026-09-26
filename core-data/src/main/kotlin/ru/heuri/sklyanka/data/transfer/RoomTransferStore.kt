package ru.heuri.sklyanka.data.transfer

import ru.heuri.sklyanka.data.room.SklyankaConverters
import ru.heuri.sklyanka.data.room.dao.IntakeDao
import ru.heuri.sklyanka.data.room.dao.MedicationDao
import ru.heuri.sklyanka.data.room.dao.ProfileDao
import ru.heuri.sklyanka.data.room.mapper.Decoded
import ru.heuri.sklyanka.data.room.mapper.decode
import ru.heuri.sklyanka.data.room.mapper.toDomain
import ru.heuri.sklyanka.data.room.mapper.toEntity
import ru.heuri.sklyanka.domain.Transactor
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.Invariants
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.transfer.DeviceState
import ru.heuri.sklyanka.domain.transfer.TransferApplied
import ru.heuri.sklyanka.domain.transfer.TransferPlan
import ru.heuri.sklyanka.domain.transfer.TransferStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Исполнение плана переноса поверх Room (`TDD.md §6`).
 *
 * **Одна транзакция на всю загрузку** (ПЕР-27). Внутри неё не принимается ни одного решения:
 * совпадения найдены, идентификаторы перевыданы и история разрешена ещё в домене, а здесь
 * применяется готовый список. Ручного «откатим, что успели» нет и не будет — это второй
 * путь записи, который никогда не проверяется по-настоящему.
 *
 * Порядок внутри транзакции задан внешними ключами с RESTRICT: стирание идёт
 * приёмы → лекарства → профили. Обратный порядок упёрся бы в ограничение целостности,
 * и это ровно то, чего от него ждут (`ReplaceAllDataTest` закрепляет порядок тестом).
 */
@Singleton
class RoomTransferStore @Inject constructor(
    private val profiles: ProfileDao,
    private val medications: MedicationDao,
    private val intakes: IntakeDao,
    private val transfer: TransferDao,
    private val transactor: Transactor,
) : TransferStore {

    override suspend fun readAll(): DeviceState = transactor.inTransaction { readState() }

    override suspend fun apply(plan: TransferPlan): TransferApplied = transactor.inTransaction {
        val before = counts()

        if (plan.erase) {
            // Порядок обязателен: у лекарства есть дети-приёмы, у профиля — дети-лекарства,
            // и внешние ключи стоят на RESTRICT, а не на CASCADE (НФТ-13).
            intakes.deleteAll()
            medications.deleteAll()
            profiles.deleteAll()
        }

        plan.profilesToInsert.forEach { profile ->
            Invariants.requireValid(Invariants.violations(profile))
            profiles.insert(profile.toEntity())
        }
        (plan.medicationsToInsert + plan.medicationsToUpdate).forEach { medication ->
            Invariants.requireValid(Invariants.violations(medication))
            medications.upsert(medication.toEntity())
        }

        val modes = scheduleModes(plan.intakesToInsert + plan.intakesToUpdate)
        if (plan.intakesToInsert.isNotEmpty()) {
            intakes.insertAll(plan.intakesToInsert.map { it.toEntity(mode(modes, it)) })
        }
        if (plan.intakesToUpdate.isNotEmpty()) {
            transfer.updateIntakes(plan.intakesToUpdate.map { it.toEntity(mode(modes, it)) })
        }

        requireSelfProfile()
        val after = counts()
        TransferApplied(
            profilesBefore = before.profiles,
            medicationsBefore = before.medications,
            intakesBefore = before.intakes,
            profilesAfter = after.profiles,
            medicationsAfter = after.medications,
            intakesAfter = after.intakes,
        )
    }

    /**
     * Состояние целиком: профили, их лекарства (активные и архивные) и вся история.
     *
     * Читается профиль за профилем, а не одним запросом без фильтра: даже здесь, где
     * нужно всё, выборка записей остаётся в контексте профиля (ПР-10).
     */
    private suspend fun readState(): DeviceState {
        val allProfiles = transfer.selectAllProfiles().map { it.toDomain() }
        val allMedications = allProfiles.flatMap { profile ->
            transfer.selectMedicationsInProfile(profile.id).mapNotNull { entity ->
                (entity.decode() as? Decoded.Value)?.model
            }
        }
        val allIntakes = allProfiles.flatMap { profile ->
            transfer.selectIntakesInProfile(profile.id).mapNotNull { entity ->
                (entity.decode() as? Decoded.Value)?.model
            }
        }
        return DeviceState(allProfiles, allMedications, allIntakes)
    }

    private suspend fun counts(): Counts = Counts(
        profiles = transfer.countProfiles(),
        medications = transfer.countMedications(),
        intakes = transfer.countIntakes(),
    )

    /**
     * Режим приёма его лекарства.
     *
     * Нужен колонке-слоту: приём по расписанию занимает слот и потому уникален на момент
     * времени (ИНВ-13), приём по потребности слота не занимает (ИНВ-14).
     */
    private suspend fun scheduleModes(rows: List<Intake>): Map<String, ScheduleMode> =
        rows.map { it.medicationId }.distinct().associateWith { id ->
            val stored = medications.getById(id)
            requireNotNull(stored) { "The transfer plan refers to a medication that does not exist: $id" }
            SklyankaConverters.textToEnum<ScheduleMode>(stored.scheduleMode) ?: ScheduleMode.SCHEDULED
        }

    private fun mode(modes: Map<String, ScheduleMode>, intake: Intake): ScheduleMode =
        requireNotNull(modes[intake.medicationId])

    /**
     * ИНВ-1 в конце транзакции (`TDD.md §6`).
     *
     * Нарушение — исключение, то есть откат. Проверка стоит здесь, а не перед записью,
     * потому что «ровно один свой профиль» — свойство базы после применения, а не плана.
     * Вторую половину инварианта держит база: уникальный индекс по колонке-слоту.
     */
    private suspend fun requireSelfProfile() {
        requireNotNull(profiles.getSelf()) { "INV-1: exactly one self profile must exist after a transfer" }
    }

    private data class Counts(val profiles: Int, val medications: Int, val intakes: Int)
}

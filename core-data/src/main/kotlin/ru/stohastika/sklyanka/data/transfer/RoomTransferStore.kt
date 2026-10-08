package ru.stohastika.sklyanka.data.transfer

import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.data.room.entity.MedicationEntity
import ru.stohastika.sklyanka.data.room.mapper.Decoded
import ru.stohastika.sklyanka.data.room.mapper.decode
import ru.stohastika.sklyanka.data.room.mapper.toDomain
import ru.stohastika.sklyanka.data.room.mapper.toEntity
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.transfer.DeviceState
import ru.stohastika.sklyanka.domain.transfer.TransferApplied
import ru.stohastika.sklyanka.domain.transfer.TransferPlan
import ru.stohastika.sklyanka.domain.transfer.TransferStore
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
            // Граница ИНВ-3: план merged-режима переносит владельца устройства сам
            // (`BuildTransferPlan`), перезапись чужого идентификатора с другим профилем
            // означает обход правила — отказ откатывает всю транзакцию (SKL-107).
            medications.getById(medication.id)?.let { stored ->
                Invariants.requireValid(Invariants.ownershipViolations(stored.profileId, medication))
            }
            medications.upsert(medication.toEntity())
        }

        val parents = parentsOf(plan.intakesToInsert + plan.intakesToUpdate)
        // Строки файла проходят те же границы, что обычная запись: поля отметки (ИНВ-10,
        // ИНВ-11) и подпись (ИНВ-18). Курс и режим историю не судят — обоснование в
        // `ValidateSnapshot` и `TDD.md §2`.
        (plan.intakesToInsert + plan.intakesToUpdate).forEach { intake ->
            val owner = requireNotNull(parents[intake.medicationId])
            Invariants.requireValid(
                Invariants.violations(intake) +
                    Invariants.actorOwnershipViolations(intake.actorProfileId, owner.profileId),
            )
        }
        if (plan.intakesToInsert.isNotEmpty()) {
            intakes.insertAll(plan.intakesToInsert.map { it.toEntity(mode(parents, it)) })
        }
        if (plan.intakesToUpdate.isNotEmpty()) {
            transfer.updateIntakes(plan.intakesToUpdate.map { it.toEntity(mode(parents, it)) })
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
     * Лекарство каждого упомянутого приёма: для колонки-слота и парных проверок.
     *
     * Режим нужен слоту: приём по расписанию занимает слот и потому уникален на момент
     * времени (ИНВ-13), приём по потребности слота не занимает (ИНВ-14); профиль нужен
     * проверке подписи (ИНВ-18).
     */
    private suspend fun parentsOf(rows: List<Intake>): Map<String, MedicationEntity> {
        val medicationIds = rows.map { it.medicationId }.distinct()
        return medicationIds.associateWith { id ->
            val stored = medications.getById(id)
            requireNotNull(stored) { "The transfer plan refers to a medication that does not exist: $id" }
        }
    }

    private fun mode(parents: Map<String, MedicationEntity>, intake: Intake): ScheduleMode =
        SklyankaConverters.textToEnum<ScheduleMode>(requireNotNull(parents[intake.medicationId]).scheduleMode)
            ?: ScheduleMode.SCHEDULED

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

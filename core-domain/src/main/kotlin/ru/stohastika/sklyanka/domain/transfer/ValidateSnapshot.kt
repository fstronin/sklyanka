package ru.stohastika.sklyanka.domain.transfer

import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.Medication

/**
 * Проверка разобранного снимка до всякой записи в базу (ПЕР-28, ИНВ-1, ИНВ-12).
 *
 * Второй из двух этапов проверки файла. Первый — опознание (опознаватель и версия
 * формата), он живёт в кодеке и отсекает чужие файлы и файлы из будущего. Здесь
 * проверяется то, что можно проверить только на собранной структуре: уникальность
 * идентификаторов внутри раздела, ссылки между разделами, правила предметной области.
 *
 * Оба этапа выполняются до единой транзакции. Поэтому «битый файл» и «файл из будущего»
 * гарантированно не оставляют следа: применять просто нечего (ПЕР-27, ПЕР-28).
 *
 * Правила записи лекарства не переписываются здесь заново — берутся те же
 * [ValidateMedicationDraft], что у формы (SKL-09). Форма у загрузки отсутствует, а правила
 * те же: запись, которую форма не дала бы сохранить, не должна въезжать в базу через файл.
 *
 * Приёмы проверяются на согласованность своих полей ([Invariants.violations] от
 * одного приёма) и на подпись пары: ИНВ-18 отвергает файл, где отметку поставил
 * «сам владелец профиля» — продукта такого состояния не бывает ни в одной выпущенной
 * сборке (по образцу отказа ИНВ-22). Полная пара «приём и его лекарство» здесь
 * не проверяется намеренно. ИНВ-15 требует, чтобы назначенное время лежало внутри
 * курса, а курс, у которого человек сдвинул даты уже после приёмов, — обычное дело,
 * как и смена режима «по расписанию» на «по потребности» (ИНВ-14); отказ от всего
 * файла из-за такой записи потерял бы единственную резервную копию истории.
 */
class ValidateSnapshot(private val validateMedication: ValidateMedicationDraft = ValidateMedicationDraft()) {

    /** @return причина отказа или null, если снимок применим целиком. */
    operator fun invoke(snapshot: TransferSnapshot): TransferReject? = selfProfileReject(snapshot)
        ?: duplicateIdReject(snapshot)
        ?: brokenLinkReject(snapshot)
        ?: deletedWithTakenReject(snapshot)
        ?: recordReject(snapshot)

    /** ИНВ-1: своего профиля ровно один. Ноль своих профилей — файл, из которого нельзя собрать базу. */
    private fun selfProfileReject(snapshot: TransferSnapshot): TransferReject? {
        val selfCount = snapshot.profiles.count { it.isSelf }
        return if (selfCount == 1) null else TransferReject(TransferRejectReason.SELF_PROFILE, "count=$selfCount")
    }

    private fun duplicateIdReject(snapshot: TransferSnapshot): TransferReject? = when {
        hasDuplicates(snapshot.profiles.map { it.id }) -> reject(TransferRejectReason.DUPLICATE_ID, "profiles.id")
        hasDuplicates(snapshot.medications.map { it.id }) -> reject(TransferRejectReason.DUPLICATE_ID, "medications.id")
        hasDuplicates(snapshot.intakes.map { it.id }) -> reject(TransferRejectReason.DUPLICATE_ID, "intakes.id")
        else -> null
    }

    /** ИНВ-12: у каждого приёма есть лекарство, у каждого лекарства — профиль. */
    private fun brokenLinkReject(snapshot: TransferSnapshot): TransferReject? {
        val profileIds = snapshot.profiles.map { it.id }.toSet()
        val medicationIds = snapshot.medications.map { it.id }.toSet()
        return when {
            snapshot.medications.any { it.profileId !in profileIds } ->
                reject(TransferRejectReason.BROKEN_LINK, "medications.profile_id")

            snapshot.intakes.any { it.medicationId !in medicationIds } ->
                reject(TransferRejectReason.BROKEN_LINK, "intakes.medication_id")

            snapshot.intakes.any { it.actorProfileId != null && it.actorProfileId !in profileIds } ->
                reject(TransferRejectReason.BROKEN_LINK, "intakes.actor_profile_id")

            else -> null
        }
    }

    /**
     * ИНВ-22: у мягко удалённого лекарства не бывает отметки `TAKEN`.
     *
     * Продукт такого состояния не создаёт (удаление условно по отсутствию отметок),
     * но файл — единственный вход, который может прийти из рук. В отличие от ИНВ-15,
     * проверка не отвергает ни одного законного состояния: «удалено» и «принято»
     * не сосуществуют ни в одной выпущенной сборке.
     */
    private fun deletedWithTakenReject(snapshot: TransferSnapshot): TransferReject? {
        val deletedIds = snapshot.medications.filter { it.isDeleted }.map { it.id }.toSet()
        val offending = snapshot.intakes.any { it.medicationId in deletedIds && it.status == IntakeStatus.TAKEN }
        return if (offending) reject(TransferRejectReason.INVALID_RECORD, "intakes:INV-22") else null
    }

    /**
     * Правила предметной области по каждой записи.
     *
     * Пояснение к отказу называет раздел и код нарушенного правила — и ничего больше:
     * ни названия лекарства, ни значений полей, потому что отказ уходит и в журнал (НФТ-19).
     */
    @Suppress("ReturnCount")
    private fun recordReject(snapshot: TransferSnapshot): TransferReject? {
        snapshot.profiles.forEach { profile ->
            val violations = Invariants.violations(profile)
            if (violations.isNotEmpty()) {
                return reject(TransferRejectReason.INVALID_RECORD, "profiles:${violations.first()}")
            }
        }
        snapshot.medications.forEach { medication ->
            val violations = medicationViolations(medication)
            if (violations.isNotEmpty()) {
                return reject(TransferRejectReason.INVALID_RECORD, "medications:${violations.first()}")
            }
        }
        val profileByMedication = snapshot.medications.associate { it.id to it.profileId }
        snapshot.intakes.forEach { intake ->
            val violations = Invariants.violations(intake) +
                Invariants.actorOwnershipViolations(
                    actorProfileId = intake.actorProfileId,
                    medicationProfileId = profileByMedication.getValue(intake.medicationId),
                )
            if (violations.isNotEmpty()) {
                return reject(TransferRejectReason.INVALID_RECORD, "intakes:${violations.first()}")
            }
        }
        return null
    }

    /**
     * Нарушения записи лекарства кодами.
     *
     * Два набора правил рядом не дублирование: правила формы (SKL-09) говорят человеку
     * и указывают на поле, инварианты модели — последняя линия обороны перед записью.
     * У файла формы нет, поэтому здесь работают оба.
     */
    private fun medicationViolations(medication: Medication): List<String> =
        validateMedication(MedicationDraft.of(medication)).map { it::class.simpleName.orEmpty() } +
            Invariants.violations(medication)

    private fun hasDuplicates(ids: List<String>): Boolean = ids.size != ids.distinct().size

    private fun reject(reason: TransferRejectReason, detail: String) = TransferReject(reason, detail)
}

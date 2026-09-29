package ru.stohastika.sklyanka.data.room.mapper

import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.entity.MedicationEntity
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape

/**
 * Чтение лекарства. Незнакомое значение любого перечисления останавливает чтение строки (МГ-6).
 *
 * Ранний выход на каждом поле — здесь самая честная форма: она называет поле, на котором
 * чтение споткнулось, а накопление ошибок в список сделало бы отказ безадресным.
 */
@Suppress("ReturnCount")
fun MedicationEntity.decode(): Decoded<Medication> {
    val decodedForm = SklyankaConverters.textToEnum<MedicationForm>(form)
        ?: return Decoded.UnknownEnum("form")
    val decodedMode = SklyankaConverters.textToEnum<ScheduleMode>(scheduleMode)
        ?: return Decoded.UnknownEnum("schedule_mode")
    val decodedRelation = SklyankaConverters.textToEnum<FoodRelation>(foodRelation)
        ?: return Decoded.UnknownEnum("food_relation")
    val decodedShape = shape?.let {
        SklyankaConverters.textToEnum<TabletShape>(it) ?: return Decoded.UnknownEnum("shape")
    }
    val decodedReason = archiveReason?.let {
        SklyankaConverters.textToEnum<ArchiveReason>(it) ?: return Decoded.UnknownEnum("archive_reason")
    }

    return Decoded.Value(
        Medication(
            id = id,
            profileId = profileId,
            name = name,
            form = decodedForm,
            shape = decodedShape,
            doseAmount = requireNotNull(SklyankaConverters.textToDecimal(doseAmount)),
            doseUnit = doseUnit,
            strength = SklyankaConverters.textToDecimal(strength),
            strengthUnit = strengthUnit,
            foodRelation = decodedRelation,
            scheduleMode = decodedMode,
            maxPerDay = maxPerDay,
            times = SklyankaConverters.textToTimes(times),
            startDate = SklyankaConverters.textToDate(startDate),
            endDate = endDate?.let(SklyankaConverters::textToDate),
            stockLeft = SklyankaConverters.textToDecimal(stockLeft),
            packageSize = SklyankaConverters.textToDecimal(packageSize),
            stockThresholdDays = stockThresholdDays,
            archivedAt = SklyankaConverters.millisToInstant(archivedAt),
            archiveReason = decodedReason,
            mirrorToWatch = mirrorToWatch,
        ),
    )
}

fun Medication.toEntity(): MedicationEntity = MedicationEntity(
    id = id,
    profileId = profileId,
    name = name,
    form = form.name,
    shape = shape?.name,
    doseAmount = doseAmount.toPlainString(),
    doseUnit = doseUnit,
    strength = SklyankaConverters.decimalToText(strength),
    strengthUnit = strengthUnit,
    foodRelation = foodRelation.name,
    scheduleMode = scheduleMode.name,
    maxPerDay = maxPerDay,
    times = SklyankaConverters.timesToText(times),
    startDate = SklyankaConverters.dateToText(startDate),
    endDate = endDate?.let(SklyankaConverters::dateToText),
    stockLeft = SklyankaConverters.decimalToText(stockLeft),
    packageSize = SklyankaConverters.decimalToText(packageSize),
    stockThresholdDays = stockThresholdDays,
    archivedAt = SklyankaConverters.instantToMillis(archivedAt),
    archiveReason = archiveReason?.name,
    mirrorToWatch = mirrorToWatch,
)

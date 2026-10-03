package ru.stohastika.sklyanka.acceptance.today

import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.CourseProgress
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.MedicationSubtitle
import ru.stohastika.sklyanka.domain.medication.WeekAdherenceStrip
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.stock.StockPanel
import ru.stohastika.sklyanka.feature.meds.card.MedCardUiState
import ru.stohastika.sklyanka.feature.meds.card.ScheduleBlockUi
import ru.stohastika.sklyanka.feature.meds.card.WatchMirrorUi

/**
 * Состояние карточки лекарства из мира сценария приёмки.
 *
 * Собирается теми же правилами домена, что `MedicationCardViewModel`: блок расписания,
 * курс, остаток и единица — предметные величины, и выдуманное состояние проверяло бы
 * разметку, а не продукт. Пустой полосой недели состояние не обедняется: сценарий
 * говорит о блоке остатка, а история семи дней — предмет SKL-10.
 */
internal object MedicationCardFromWorld {

    fun state(world: AcceptanceWorld, medication: Medication, deleteConfirmation: Boolean = false): MedCardUiState =
        MedCardUiState(
            medicationId = medication.id,
            title = medication.name,
            form = medication.form,
            subtitle = MedicationSubtitle.of(medication),
            schedule = scheduleOf(medication),
            course = CourseProgress.of(medication, world.clock.today()),
            stock = StockPanel.of(medication),
            stockUnit = doseUnitOf(medication),
            week = WeekAdherenceStrip(days = emptyList(), taken = 0, planned = 0),
            watchMirror = WatchMirrorUi(checked = medication.mirrorToWatch, enabled = !medication.isArchived),
            isArchived = medication.isArchived,
            courseFinishedOffer = false,
            canDelete = !kotlinx.coroutines.runBlocking {
                world.repositories.intakeStore.hasTaken(medication.profileId, medication.id)
            },
            deleteConfirmation = deleteConfirmation,
            refill = null,
            notice = null,
        )

    private fun scheduleOf(medication: Medication): ScheduleBlockUi =
        if (medication.scheduleMode == ScheduleMode.SCHEDULED) {
            ScheduleBlockUi.Scheduled(
                timesPerDay = medication.times.size,
                doseAmount = medication.doseAmount,
                doseUnit = doseUnitOf(medication),
                times = medication.times.sorted(),
            )
        } else {
            ScheduleBlockUi.AsNeeded(maxPerDay = medication.maxPerDay ?: 0)
        }

    private fun doseUnitOf(medication: Medication): DoseUnit =
        DoseUnit.ofCode(medication.doseUnit) ?: medication.form.doseUnit()
}

package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.ScheduleMode

/**
 * Какие блоки формы существуют при текущем черновике (`TDD.md §5.2`).
 *
 * Чистая функция от черновика, а не состояние экрана: те же правила применимости
 * действуют при загрузке файла переноса данных (SKL-19), где формы нет вовсе.
 */
data class FieldApplicability(
    /** «Вид таблетки»: только для «таблетки» и «капсулы» (ПР-9, ИНВ-16). */
    val shapeVisible: Boolean,
    /** «Приёмов в сутки» и «Время приёма»: только при «по расписанию» (ПР-8). */
    val timesVisible: Boolean,
    /** «Не чаще N раз в сутки»: только при «по потребности» (ПР-7, ИНВ-17). */
    val maxPerDayVisible: Boolean,
    /** Строка «Окончание» активна, когда «Принимать бессрочно» выключено. */
    val endDateEnabled: Boolean,
    /** Порог остатка действует, когда «Напомнить о покупке» включено. */
    val stockThresholdEnabled: Boolean,
    /** Единица дозы и остатка — производная формы выпуска. */
    val doseUnit: DoseUnit,
)

/** Применимость блоков для этого черновика. */
fun MedicationDraft.applicability(): FieldApplicability = FieldApplicability(
    shapeVisible = form.allowsShape,
    timesVisible = scheduleMode == ScheduleMode.SCHEDULED,
    maxPerDayVisible = scheduleMode == ScheduleMode.PRN,
    endDateEnabled = endDate != null,
    stockThresholdEnabled = stockThresholdDays != null,
    doseUnit = doseUnit,
)

/**
 * Черновик без неприменимых значений.
 *
 * Неприменимое значение именно **сбрасывается**, а не прячется вместе с блоком:
 * спрятанный, но сохранённый вид таблетки уедет в базу и нарушит ИНВ-16 — причём
 * незаметно для человека, который видел, как блок исчез, и решил, что значения больше нет.
 *
 * Вызывается после каждого изменения черновика, а не только перед сохранением:
 * иначе между сменой формы выпуска и нажатием «Сохранить» состояние формы и состояние
 * черновика расходятся, и экран показывает одно, а сохраняет другое.
 */
fun MedicationDraft.normalized(): MedicationDraft = copy(
    shape = if (form.allowsShape) shape else null,
    times = if (scheduleMode == ScheduleMode.SCHEDULED) times else emptyList(),
    maxPerDay = if (scheduleMode == ScheduleMode.PRN) maxPerDay else null,
)

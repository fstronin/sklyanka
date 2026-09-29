package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Заготовки черновиков.
 *
 * Корректный черновик один, и каждый тест отличается от него ровно одним полем: иначе
 * непонятно, какое правило проверяет тест. Настоящих названий лекарств здесь нет —
 * тестовые данные попадают в отчёты (НФТ-19).
 */
internal object Drafts {

    const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
    const val NEW_ID = "55555555-5555-4555-8555-555555555555"

    val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val EVENING: LocalTime = LocalTime.of(20, 0)

    /** Черновик, проходящий все девять правил формы. */
    fun valid(): MedicationDraft = MedicationDraft(
        id = null,
        profileId = PROFILE_ID,
        name = "med-a",
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal("2"),
        strength = BigDecimal("48"),
        strengthUnit = "mg",
        scheduleMode = ScheduleMode.SCHEDULED,
        times = listOf(MORNING, EVENING),
        startDate = TODAY,
        endDate = null,
        stockLeft = BigDecimal("60"),
        stockThresholdDays = 5,
    )

    /** Корректный черновик «по потребности»: расписания нет, ограничение задано. */
    fun validPrn(): MedicationDraft = valid().copy(
        scheduleMode = ScheduleMode.PRN,
        times = emptyList(),
        maxPerDay = 3,
    )
}

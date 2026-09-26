package ru.heuri.sklyanka.domain.medication

import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.model.TabletShape
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Состояние заполняемой формы лекарства (`TDD.md §5.1`).
 *
 * Зачем отдельный тип рядом с [Medication]. `Medication` — запись, удовлетворяющая
 * инвариантам модели данных; черновик — то, что человек набрал и что инвариантам ещё
 * не удовлетворяет. Смешение этих двух ролей — способ протащить невалидную запись
 * в базу, потому что «поле же необязательное».
 *
 * Счётчика «приёмов в сутки» здесь нет намеренно: он производная от длины [times]
 * (`TDD.md §9.2`). Двусторонняя связь счётчика и списка времён, нарисованная
 * в макете, — источник рассогласования: хранимое значение одно, и инвариант
 * «список непуст и без повторов» (ИНВ-6) проверяется в одном месте.
 *
 * Состояния двух переключателей формы тоже не хранятся отдельными флагами:
 * «Принимать бессрочно» — это [endDate] `== null` (ПР-6), «Напомнить о покупке» —
 * это [stockThresholdDays] `== null`. Отдельный флаг рядом со значением позволяет
 * собрать «бессрочно, но с датой окончания», а отсутствие значения — нет.
 */
@Suppress("LongParameterList")
data class MedicationDraft(
    /** `null` — новое лекарство; иначе правится существующее. */
    val id: String?,
    /** Владелец. Неизменяем после создания (ИНВ-3): форма правки профиль не предлагает. */
    val profileId: String,
    val name: String = "",
    val form: MedicationForm = MedicationForm.TABLET,
    val shape: TabletShape? = null,
    val doseAmount: BigDecimal? = null,
    val strength: BigDecimal? = null,
    val strengthUnit: String? = null,
    val foodRelation: FoodRelation = FoodRelation.ANY,
    val scheduleMode: ScheduleMode = ScheduleMode.SCHEDULED,
    /** Ограничение «не чаще N раз в сутки»; применимо только к «по потребности» (ПР-7). */
    val maxPerDay: Int? = null,
    val times: List<LocalTime> = emptyList(),
    val startDate: LocalDate,
    /** Пустое значение означает бессрочный приём (ПР-6), а не неполные данные. */
    val endDate: LocalDate? = null,
    val stockLeft: BigDecimal? = null,
    val stockThresholdDays: Int? = null,
) {

    /** Единица дозы и остатка — производная формы выпуска, человеком не правится (ОВ-16). */
    val doseUnit: DoseUnit get() = form.doseUnit()

    /** Число приёмов в сутки: производная длины списка времён, а не хранимое значение. */
    val intakesPerDay: Int get() = times.size

    /**
     * Готовое лекарство из черновика.
     *
     * Вызывается только после успешной проверки [ValidateMedicationDraft]: непроверенный
     * черновик даёт запись, которую отвергнут инварианты уровня данных, и человек увидит
     * отказ записи вместо подсказки.
     */
    fun toMedication(id: String): Medication = Medication(
        id = id,
        profileId = profileId,
        name = name.trim(),
        form = form,
        shape = shape,
        doseAmount = requireNotNull(doseAmount) { "Dose amount must be validated before saving" },
        doseUnit = doseUnit.code,
        strength = strength,
        strengthUnit = strengthUnit?.trim()?.ifBlank { null },
        foodRelation = foodRelation,
        scheduleMode = scheduleMode,
        maxPerDay = maxPerDay,
        times = times,
        startDate = startDate,
        endDate = endDate,
        stockLeft = stockLeft,
        stockThresholdDays = stockThresholdDays,
        // Архивация черновиком не управляется: в архив и обратно курс уходит с карточки
        // лекарства (SKL-10) и из архива (SKL-11), а не правкой формы.
        archivedAt = null,
        archiveReason = null,
    )

    companion object {

        /** Черновик нового лекарства: начало курса — сегодняшняя дата из часов домена (НФТ-48). */
        fun new(profileId: String, today: LocalDate): MedicationDraft =
            MedicationDraft(id = null, profileId = profileId, startDate = today)

        /**
         * Черновик правки: форма правки — это черновик, заполненный из существующего курса.
         *
         * Архивация и причина архивации не переносятся: форма их не показывает и не меняет,
         * а перенос значения в черновик означал бы, что сохранение способно вернуть курс
         * из архива незаметно для человека.
         */
        fun of(medication: Medication): MedicationDraft = MedicationDraft(
            id = medication.id,
            profileId = medication.profileId,
            name = medication.name,
            form = medication.form,
            shape = medication.shape,
            doseAmount = medication.doseAmount,
            strength = medication.strength,
            strengthUnit = medication.strengthUnit,
            foodRelation = medication.foodRelation,
            scheduleMode = medication.scheduleMode,
            maxPerDay = medication.maxPerDay,
            times = medication.times,
            startDate = medication.startDate,
            endDate = medication.endDate,
            stockLeft = medication.stockLeft,
            stockThresholdDays = medication.stockThresholdDays,
        )
    }
}

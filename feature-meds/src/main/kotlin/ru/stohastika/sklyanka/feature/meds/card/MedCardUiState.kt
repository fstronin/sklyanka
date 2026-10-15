package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.runtime.Immutable
import ru.stohastika.sklyanka.domain.medication.CourseProgress
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.MedicationSubtitle
import ru.stohastika.sklyanka.domain.medication.WeekAdherenceStrip
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.stock.StockPanel
import ru.stohastika.sklyanka.feature.meds.form.ScheduleKind
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Состояние карточки лекарства (`design/screens/MedCard.dc.html`).
 *
 * Состояний «загрузка» и «ошибка» здесь нет по тем же причинам, что на экране «Сегодня»:
 * данные локальные, а отказ чтения базы — аварийная ситуация, а не экранное состояние.
 * До первого значения потока состояние равно `null`, и экран ещё не рисуется.
 *
 * Готовых русских строк в состоянии нет: склонения и разделители живут в ресурсах
 * (НФТ-42, НФТ-44), а правила — в домене.
 */
@Immutable
data class MedCardUiState(
    val medicationId: String,
    val title: String,
    val form: MedicationForm,
    val subtitle: MedicationSubtitle,
    val schedule: ScheduleBlockUi,
    /** `null` у лекарства «по потребности»: отрезка курса у него нет (ПР-8). */
    val course: CourseProgress?,
    /** [StockPanel.Hidden] — учёт остатка не ведётся, и блока на карточке нет вовсе (ПР-13). */
    val stock: StockPanel,
    val stockUnit: DoseUnit,
    val week: WeekAdherenceStrip,
    val isArchived: Boolean,
    /**
     * Курс дошёл до конца и всё ещё активен: карточка предлагает убрать его в архив
     * с причиной «курс завершён» (ОВ-42).
     */
    val courseFinishedOffer: Boolean,
    /** Открытый ввод нового остатка; `null` — кнопка «Пополнить» ещё не нажата. */
    val refill: RefillUi?,
    /**
     * Удаление доступно, пока у лекарства нет ни одной отметки `TAKEN` (ПР-14, ОВ-124).
     *
     * `false` — действия «Удалить» на карточке нет вовсе: ни серой кнопки, ни пояснения.
     */
    val canDelete: Boolean = false,
    /** Открыта шторка подтверждения удаления (ОВ-124). */
    val deleteConfirmation: Boolean = false,
    /** Объяснение, которое нельзя проглотить: возврат курса с прошедшей датой окончания. */
    val notice: MedCardNotice?,
    /** Архивация возвращает человека в список лекарств; экран закрывается маршрутом. */
    val closed: Boolean = false,
)

/**
 * Блок «Расписание» либо ограничение, его заменяющее.
 *
 * Запечатанный тип, а не расписание с необязательным ограничением: у лекарства
 * «по потребности» расписания не существует (ПР-8), а не «оно пустое».
 */
@Immutable
sealed interface ScheduleBlockUi {

    /** «2 приёма в сутки · по 2 таблетки» и чипы времени «08:00», «20:00». */
    @Immutable
    data class Scheduled(
        val timesPerDay: Int,
        val doseAmount: BigDecimal,
        val doseUnit: DoseUnit,
        val times: List<LocalTime>,
        /**
         * Вид расписания: у сложных видов в слоте частоты стоит его имя (§5.10, 5.6).
         * У ежедневного вида частота считается по числу приёмов.
         */
        val scheduleKind: ScheduleKind = ScheduleKind.DAILY,
    ) : ScheduleBlockUi

    /** «не чаще 3 раз в сутки» — вместо блока расписания (ПР-7). */
    @Immutable
    data class AsNeeded(val maxPerDay: Int) : ScheduleBlockUi
}

/** Ввод нового значения остатка: «Пополнить» заменяет значение (ОВ-13, решено в SKL-12). */
@Immutable
data class RefillUi(val text: String, val invalid: Boolean = false)

/** То, что карточка обязана сказать человеку вслух. */
@Immutable
sealed interface MedCardNotice {

    /**
     * Возврат из архива не состоялся: дата окончания курса в прошлом.
     *
     * Молчаливого возврата, не порождающего ни одного приёма, не происходит (ОВ-14):
     * человеку сказано, что период приёма нужно продлить.
     */
    @Immutable
    data object PeriodNeedsUpdate : MedCardNotice

    /** Остаток не может быть отрицательным (ИНВ-8). */
    @Immutable
    data object StockNegative : MedCardNotice
}

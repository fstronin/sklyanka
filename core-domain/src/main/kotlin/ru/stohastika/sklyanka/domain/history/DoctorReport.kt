package ru.stohastika.sklyanka.domain.history

import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Состав отчёта врачу (`TDD.md §5.6`).
 *
 * **Состав, а не документ.** Формат отчёта спецификацией не зафиксирован (ОВ-15), и
 * разделение ровно по этой границе: здесь — числа и факты, проверяемые тестами на JVM;
 * отрисовка живёт в экранном модуле и меняется вместе с ответом заказчика, не задевая
 * расчёт.
 *
 * **Выводов и рекомендаций в отчёте нет** — по той же причине, по которой ограничена
 * строка-вывод: приложение не медицинское изделие (`CLAUDE.md`). Поэтому в составе нет
 * ни одного поля «оценка», «риск» или «совет», и появиться оно здесь не может незаметно.
 *
 * Готовых русских строк в составе нет вовсе (НФТ-42): даты, склонения и разделители
 * собирает отрисовка из ресурсов.
 */
data class DoctorReport(
    /** Период и все посчитанные по нему числа — те же, что человек видит на экране. */
    val period: AdherencePeriod,
    /** Имя профиля: отчёт всегда об одном профиле (ПР-10). */
    val profileName: String,
    val generatedAt: Instant,
    /** Строки по лекарствам, включая архивированные курсы (ПР-11). */
    val medications: List<ReportMedicationRow>,
) {

    /** Сколько записей приёма легло в основу отчёта. Число уходит в журнал (НФТ-19). */
    val intakeCount: Int get() = plannedTotal

    /**
     * Сводка «Принято: 86 из 100 приёмов (86 %)» — сумма по строкам лекарств, а не второй
     * расчёт: строки уже посчитаны из тех же приёмов, и отдельная формула однажды
     * разошлась бы с экраном истории (решение дизайна по ОВ-15).
     */
    val takenTotal: Int get() = medications.sumOf { it.taken }

    val plannedTotal: Int get() = medications.sumOf { it.planned }

    val takenPercent: Int
        get() = if (plannedTotal == 0) 0 else Math.round(takenTotal.toDouble() / plannedTotal * PERCENT).toInt()

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * Строка отчёта по одному лекарству.
 *
 * Пропуск, отмеченный человеком, и пропуск, проставленный приложением, здесь **не
 * сливаются**, в отличие от календаря: первое говорит о поведении человека, второе может
 * говорить о том, что телефон был выключен, и врач вправе видеть разницу (`TDD.md §5.3`).
 */
data class ReportMedicationRow(
    val medicationId: String,
    val name: String,
    val form: MedicationForm,
    /**
     * Дозы приёмов, записанные за период, — по возрастанию и без повторов (ПР-19).
     *
     * У ступенчатого курса доза меняется от приёма к приёму, и одно число курса — доза
     * **первого** этапа — сказало бы врачу неправду о принятом: в отчёте стоят те дозы,
     * которыми приёмы прожиты и по которым списан остаток (SKL-117, критика F1).
     * У ежедневного курса и у курса до появления видов расписания список — из одного
     * элемента: дозы приёмов равны дозе лекарства.
     */
    val doses: List<BigDecimal>,
    val doseUnit: String,
    val strength: BigDecimal?,
    val strengthUnit: String?,
    val foodRelation: FoodRelation,
    /** Времена приёма по расписанию; пусто — лекарство «по потребности» (ПР-4). */
    val times: List<LocalTime>,
    val courseStart: LocalDate,
    /** `null` — приём бессрочный (ПР-6), а не данные неполные. */
    val courseEnd: LocalDate?,
    val taken: Int,
    val planned: Int,
    /** Пропуски, отмеченные человеком: он был и решил не принимать. */
    val skippedByPerson: Int,
    /** Пропуски без отметки: приложение перевело их по истечении окна (ПР-1). */
    val missedWithoutMark: Int,
    /** Курс в архиве: признак печатается в отчёте (ПР-11). */
    val archived: Boolean,
    /** Даты пропусков этого лекарства — перечень, а не число (`TDD.md §8.2`). */
    val missedDates: List<LocalDate>,
) {

    val misses: Int get() = skippedByPerson + missedWithoutMark

    val takenPercent: Int
        get() = if (planned == 0) 0 else Math.round(taken.toDouble() / planned * PERCENT).toInt()

    private companion object {
        const val PERCENT = 100
    }
}

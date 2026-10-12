package ru.stohastika.sklyanka.domain.stock

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import java.math.BigDecimal

/** Чем закончилось пополнение остатка. */
sealed interface RefillOutcome {

    /** Значение записано. [before] пуст, если до пополнения учёт остатка не вёлся. */
    data class Applied(val before: BigDecimal?, val after: BigDecimal) : RefillOutcome

    /** Отрицательный остаток не существует (ИНВ-8): форма обязана сказать об этом человеку. */
    data object NegativeValue : RefillOutcome

    /** Лекарства с таким идентификатором нет: карточку открыли, пока курс удаляли переносом данных. */
    data object UnknownMedication : RefillOutcome
}

/**
 * Пополнение остатка человеком — действие «Пополнить» в карточке лекарства.
 *
 * **Решение временное (ОВ-13).** «Пополнить» здесь *заменяет* значение остатка вслед за
 * `docs/norm/04-behavior.md` («запрашивает новое значение `stock_left`»). Прибавление
 * упаковки к текущему остатку и ввод новой упаковки — два других поведения, и выбор
 * за заказчиком. Вынесено в отдельный сценарий именно поэтому: при смене решения
 * правится тело этого класса, а не каждый вызывающий.
 *
 * Истории приёмов пополнение не касается: остаток разошёлся с упаковкой — это про
 * упаковку, а не про то, что человек принимал.
 */
class RefillStockUseCase(private val medications: MedicationRepository, private val log: OperationLog) {

    suspend operator fun invoke(medicationId: String, newValue: BigDecimal): RefillOutcome {
        val medication = medications.get(medicationId)
        return when {
            newValue < BigDecimal.ZERO -> RefillOutcome.NegativeValue
            medication == null -> RefillOutcome.UnknownMedication
            else -> replace(medication, newValue)
        }
    }

    private suspend fun replace(medication: Medication, newValue: BigDecimal): RefillOutcome {
        medications.setStock(medication.id, newValue)
        log.record(
            event = OpEvent.STOCK_REFILLED,
            profileId = medication.profileId,
            medicationId = medication.id,
            before = medication.stockLeft?.toPlainString(),
            after = newValue.toPlainString(),
        )
        return RefillOutcome.Applied(before = medication.stockLeft, after = newValue)
    }
}

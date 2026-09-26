package ru.heuri.sklyanka.domain.stock

import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.repository.MedicationRepository
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
 * `docs/spec/03-screens.md` («запрашивает новое значение `stock_left`»). Прибавление
 * упаковки к текущему остатку и ввод новой упаковки — два других поведения, и выбор
 * за заказчиком. Вынесено в отдельный сценарий именно поэтому: при смене решения
 * правится тело этого класса, а не каждый вызывающий.
 *
 * Истории приёмов пополнение не касается: остаток разошёлся с упаковкой — это про
 * упаковку, а не про то, что человек принимал.
 */
class RefillStockUseCase(
    private val medications: MedicationRepository,
    private val log: OperationLog,
) {

    suspend operator fun invoke(medicationId: String, newValue: BigDecimal): RefillOutcome {
        if (newValue < BigDecimal.ZERO) return RefillOutcome.NegativeValue
        val medication = medications.get(medicationId) ?: return RefillOutcome.UnknownMedication
        medications.setStock(medicationId, newValue)
        log.record(
            event = OpEvent.STOCK_REFILLED,
            profileId = medication.profileId,
            medicationId = medicationId,
            before = medication.stockLeft?.toPlainString(),
            after = newValue.toPlainString(),
        )
        return RefillOutcome.Applied(before = medication.stockLeft, after = newValue)
    }
}

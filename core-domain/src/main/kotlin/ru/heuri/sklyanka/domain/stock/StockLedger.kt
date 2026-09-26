package ru.heuri.sklyanka.domain.stock

import ru.heuri.sklyanka.domain.intake.StockEffect
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.math.BigDecimal

/**
 * Насколько изменился остаток упаковки.
 *
 * [clamped] отличает «списали дозу целиком» от «списали, сколько было»: разница между
 * счётчиком и упаковкой возникает именно здесь, и без признака её потом не найти
 * (`TDD.md §8`).
 */
data class StockChange(val before: BigDecimal, val after: BigDecimal, val clamped: Boolean)

/**
 * Правило списания остатка (ПР-5, ИНВ-8).
 *
 * Чистая функция без хранилища: остаток приводится к результату отметки внутри той же
 * транзакции, что и сама отметка (ЧАС-23, ЧАС-24), и правило обязано проверяться на JVM,
 * а не на устройстве с эмулятором.
 *
 * Второго механизма списания в приложении нет: и транзакция отметки в `core-data`,
 * и хранилище сценариев приёмки считают остаток этой функцией. Копия правила рано или
 * поздно разойдётся с оригиналом, а разойтись ей нельзя — остаток истории не имеет.
 */
object StockLedger {

    /**
     * Что делать с остатком при таком исходе приёма (ПР-5).
     *
     * `SKIPPED` и `MISSED` перечислены вместе и оба остаток не трогают. Автоматический
     * пропуск не списывает намеренно: приложение не знает, принял человек лекарство или
     * нет, и списание по догадке рано или поздно даёт ложное «пора покупать»
     * (`task.md`, «Что списывается и когда»).
     */
    fun effectOf(result: IntakeStatus): StockEffect = when (result) {
        IntakeStatus.TAKEN -> StockEffect.DECREMENT
        IntakeStatus.PENDING, IntakeStatus.SKIPPED, IntakeStatus.MISSED -> StockEffect.NONE
    }

    /**
     * Новое значение остатка или `null`, если остаток не меняется.
     *
     * `null` возвращается в двух случаях: учёт остатка не ведётся (`stockLeft == null` —
     * валидное состояние, а не пропущенные данные) и исход приёма остатка не касается.
     * Ниже нуля остаток не опускается (ИНВ-8): доза больше остатка — это ноль и признак
     * [StockChange.clamped], а не отказ записать отметку.
     */
    fun apply(stockLeft: BigDecimal?, doseAmount: BigDecimal, effect: StockEffect): StockChange? {
        if (stockLeft == null || effect == StockEffect.NONE) return null
        val raw = when (effect) {
            StockEffect.DECREMENT -> stockLeft - doseAmount
            StockEffect.RESTORE -> stockLeft + doseAmount
            StockEffect.NONE -> stockLeft
        }
        return StockChange(before = stockLeft, after = raw.max(BigDecimal.ZERO), clamped = raw < BigDecimal.ZERO)
    }

    /** То же правило в терминах исхода приёма — как его формулирует ПР-5. */
    fun applyIntakeResult(stockLeft: BigDecimal?, doseAmount: BigDecimal, result: IntakeStatus): StockChange? =
        apply(stockLeft, doseAmount, effectOf(result))
}

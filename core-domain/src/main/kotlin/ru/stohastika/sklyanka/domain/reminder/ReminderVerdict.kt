package ru.stohastika.sklyanka.domain.reminder

/**
 * Один верхний элемент «Проверки напоминаний» (ОВ-130).
 *
 * Старшинство отвечает на вопрос «придут ли напоминания вообще»: причина ненадёжности
 * важнее разового результата самопроверки, потому что её действие живёт в «Разрешениях».
 * Перерыв в вердикт не входит: он остаётся отдельным баннером ниже (AC-5) и рисуется
 * даже под баннером старшей причины.
 */
sealed interface ReminderVerdict {

    /** Строка «Всё в порядке»; [hasUpcoming] — есть ли будущие приёмы (AC-2, AC-28). */
    data class Ok(val hasUpcoming: Boolean) : ReminderVerdict

    /** Баннер «Предупреждение»: одна старшая причина ненадёжности (AC-3, AC-7). */
    data class Cause(val warning: ReminderWarning) : ReminderVerdict

    /** Баннер «Последняя проверка доставки не прошла.» (АС-20, ОВ-131). */
    data object DeliveryFailed : ReminderVerdict
}

/**
 * Правило вердикта (ОВ-130).
 *
 * Порядок причин уже задан надёжностью (ОВ-107), поэтому правило только выбирает между
 * баннером причины, баннером неподтверждённого провала доставки и строкой «Всё в порядке».
 * Подтверждённый результат проверки вердикт не трогает: его уже показали и закрыли
 * (AC-18, AC-22).
 */
object ReminderVerdictRule {

    fun of(
        reliability: ReminderReliability,
        deliveryCheck: DeliveryCheckState,
        deliveryCheckAcknowledged: Boolean,
        hasUpcoming: Boolean,
    ): ReminderVerdict {
        reliability.warning?.let { return ReminderVerdict.Cause(it) }
        return if (isUnacknowledgedFailure(deliveryCheck, deliveryCheckAcknowledged)) {
            ReminderVerdict.DeliveryFailed
        } else {
            ReminderVerdict.Ok(hasUpcoming)
        }
    }

    /**
     * Неудачный исход самопроверки, который человеку ещё не назвали.
     *
     * «Проверка не состоялась» сюда не входит: она показывается прямо в блоке и баннера
     * не даёт (AC-21).
     */
    private fun isUnacknowledgedFailure(state: DeliveryCheckState, acknowledged: Boolean): Boolean = !acknowledged &&
        state is DeliveryCheckState.Finished &&
        (state.outcome == DeliveryCheckOutcome.LATE || state.outcome == DeliveryCheckOutcome.NOT_DELIVERED)
}

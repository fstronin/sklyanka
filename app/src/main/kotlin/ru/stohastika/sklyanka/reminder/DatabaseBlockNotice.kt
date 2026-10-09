package ru.stohastika.sklyanka.reminder

import android.content.Context
import ru.stohastika.sklyanka.platform.notification.FreeSpaceRequiredNotifier
import ru.stohastika.sklyanka.platform.notification.UpdateRequiredNotifier

/**
 * Сообщение человеку, когда напоминание не может отработать честно (П-7).
 *
 * Решение [wakeUpDecision] говорит «работать нельзя», но человеку нужно знать **почему**:
 * «обновите приложение» и «освободите место» — разные действия, и сообщение не по причине
 * человек выполнить не может. Соответствие решения и сообщения живёт здесь одним местом:
 * у пяти приёмников и задач эта ветка была бы пятью копиями одного правила, и одна из них
 * однажды не получила бы новую причину.
 *
 * @return `true`, если работа приостановлена и вызывающий обязан выйти, ничего не сделав
 */
fun announceBlockedWakeUp(context: Context, decision: WakeUpDecision): Boolean = when (decision) {
    WakeUpDecision.Handle -> false

    WakeUpDecision.AskToUpdate -> {
        UpdateRequiredNotifier.show(context)
        true
    }

    WakeUpDecision.AskToFreeSpace -> {
        FreeSpaceRequiredNotifier.show(context)
        true
    }
}

package ru.heuri.sklyanka.domain.transfer

import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeStatus

/**
 * Итог слияния истории.
 *
 * @property inserted приёмы файла, которых на устройстве не было
 * @property updated записи устройства, которые обязаны принять значения из файла;
 *   идентификатор остаётся идентификатором устройства — это правка строки, а не вставка
 * @property skipped сколько записей файла отброшено как дубли (для отчёта человеку)
 */
data class IntakeMergeResult(val inserted: List<Intake>, val updated: List<Intake>, val skipped: Int)

/**
 * Разрешение дублей истории при слиянии (ПЕР-23).
 *
 * Ключ — «лекарство + назначенное время». Не идентификатор: идентификаторы двух устройств
 * не сравнимы (ПЕР-9), а один и тот же приём, отмеченный на телефоне и попавший в файл
 * с другого устройства, обязан остаться одной записью — иначе после каждой загрузки
 * история удваивается, и отчёт врачу перестаёт что-либо значить.
 *
 * Правило первенства то же, что при отметке приёма (ЧАС-22): сначала определённый исход
 * над неопределённым, затем более ранняя отметка. Запись без отметки вовсе проигрывает
 * записи с отметкой: «ожидается» несёт меньше сведений, чем «пропущено в 23:59».
 *
 * Остаток упаковки по истории не пересчитывается (ПЕР-23): он берётся из принятой записи
 * лекарства. Пересчёт остатка по слитой истории дал бы отрицательный остаток на первом же
 * файле, где приёмов больше, чем таблеток в упаковке.
 */
class MergeIntakeHistory {

    operator fun invoke(existing: List<Intake>, incoming: List<Intake>): IntakeMergeResult {
        val deviceByKey = existing.associateBy(::keyOf)
        val inserted = linkedMapOf<Pair<String, String>, Intake>()
        val updated = linkedMapOf<Pair<String, String>, Intake>()
        var skipped = 0

        incoming.forEach { candidate ->
            val key = keyOf(candidate)
            val device = deviceByKey[key]
            when {
                // Дубль внутри самого файла: между собой записи разрешаются тем же правилом.
                inserted.containsKey(key) -> {
                    val winner = better(requireNotNull(inserted[key]), candidate)
                    // Идентификатор остаётся у первой записи: на него уже ничто не ссылается,
                    // но два разных идентификатора на одну вставку — источник путаницы в отчёте.
                    inserted[key] = winner.copy(id = requireNotNull(inserted[key]).id)
                    skipped++
                }

                device == null -> inserted[key] = candidate

                else -> {
                    val winner = better(device, candidate)
                    // Победила запись устройства — файл не приносит ничего нового.
                    if (winner === device) {
                        skipped++
                    } else {
                        updated[key] = winner.copy(id = device.id)
                    }
                }
            }
        }
        return IntakeMergeResult(
            inserted = inserted.values.toList(),
            updated = updated.values.toList(),
            skipped = skipped,
        )
    }

    /**
     * Победитель пары: определённый исход, затем более ранняя отметка.
     *
     * При полном равенстве побеждает первая — [left]: это запись устройства либо запись,
     * пришедшая в файле раньше, и менять её на равнозначную незачем.
     */
    private fun better(left: Intake, right: Intake): Intake {
        val byOutcome = rank(right.status) - rank(left.status)
        if (byOutcome != 0) return if (byOutcome > 0) right else left
        val mine = left.actedAt
        val theirs = right.actedAt
        return when {
            theirs == null -> left
            mine == null -> right
            theirs < mine -> right
            else -> left
        }
    }

    /** Определённый исход — решение человека или подтверждённый приём; остальное — его отсутствие. */
    private fun rank(status: IntakeStatus): Int = when (status) {
        IntakeStatus.TAKEN, IntakeStatus.SKIPPED -> 1
        IntakeStatus.PENDING, IntakeStatus.MISSED -> 0
    }

    private fun keyOf(intake: Intake): Pair<String, String> = intake.medicationId to intake.scheduledAt.toString()
}

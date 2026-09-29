package ru.stohastika.sklyanka.domain.today

import java.time.LocalTime

/** Начало ночи: приём в 00:05 относится к тем же суткам, что и приём в 06:30. */
private const val NIGHT_FROM_HOUR = 0

/** Границы взяты из подписей плотного дня: «Утро · 07:00 — 11:00» (`MainDense.dc.html`). */
private const val MORNING_FROM_HOUR = 7
private const val DAY_FROM_HOUR = 12
private const val EVENING_FROM_HOUR = 18

/** Последний час каждого времени суток: час начала следующего минус один. */
private const val NIGHT_LAST_HOUR = 6
private const val MORNING_LAST_HOUR = 11
private const val DAY_LAST_HOUR = 17
private const val EVENING_LAST_HOUR = 23

/**
 * Время суток — метка справа от группы приёмов и заголовок секции плотного дня.
 *
 * Границы фиксированы, а не выводятся из самих приёмов: подпись «Утро · 07:00 — 11:00»
 * обязана оставаться той же, сколько бы приёмов в это время ни стояло, иначе один
 * отменённый приём переименовывает секцию и человек решает, что утро «сдвинулось».
 *
 * Конец диапазона хранится последним **часом**, а не последней минутой: макет пишет
 * «07:00 — 11:00», а не «07:00 — 11:59», и приём в 11:30 всё равно утренний —
 * принадлежность считается по [of], а не по [range].
 */
enum class PartOfDay(fromHour: Int, lastHour: Int) {
    NIGHT(NIGHT_FROM_HOUR, NIGHT_LAST_HOUR),
    MORNING(MORNING_FROM_HOUR, MORNING_LAST_HOUR),
    DAY(DAY_FROM_HOUR, DAY_LAST_HOUR),
    EVENING(EVENING_FROM_HOUR, EVENING_LAST_HOUR),
    ;

    /** Начало времени суток — левая часть подписи секции. */
    val from: LocalTime = LocalTime.of(fromHour, 0)

    /** Правая часть подписи секции: «11:00», а не «11:59». */
    val toInclusive: LocalTime = LocalTime.of(lastHour, 0)

    companion object {

        /**
         * Время суток, которому принадлежит момент.
         *
         * По часу, а не по сравнению с [toInclusive]: 11:30 — это утро, хотя подпись
         * секции кончается на 11:00.
         */
        fun of(time: LocalTime): PartOfDay = when (time.hour) {
            in NIGHT_FROM_HOUR..NIGHT_LAST_HOUR -> NIGHT
            in MORNING_FROM_HOUR..MORNING_LAST_HOUR -> MORNING
            in DAY_FROM_HOUR..DAY_LAST_HOUR -> DAY
            else -> EVENING
        }
    }
}

package ru.heuri.sklyanka.acceptance.world

import ru.heuri.sklyanka.domain.time.FixedTimeSource
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Часы сценария — единственный источник времени приёмки (НФТ-48).
 *
 * Момент по умолчанию записан здесь и больше нигде: до общего мира каждое состояние
 * сценария держало свою копию момента из Предыстории, и две копии одного значения
 * расходятся не «если», а «когда».
 *
 * Пояс отделён от момента намеренно (НФТ-34): перелёт сдвигает местные часы, но не
 * момент времени, а граница суток считается по местной полуночи текущего пояса (НФТ-37).
 */
class ScenarioClock {

    private val source = FixedTimeSource(
        DEFAULT_MOMENT.atZone(DEFAULT_ZONE).toInstant(),
        DEFAULT_ZONE,
    )

    /** Часы для доменных сценариев: подменяемый источник времени, а не системные часы. */
    val time: TimeSource get() = source

    val zone: ZoneId get() = source.zone()

    fun now(): Instant = source.now()

    fun nowLocal(): LocalDateTime = source.nowLocal()

    fun today(): LocalDate = source.today()

    /** Момент местного времени как факт в UTC: отметки и журнал хранятся в UTC. */
    fun momentOf(moment: LocalDateTime): Instant = moment.atZone(source.zone()).toInstant()

    fun moveTo(moment: LocalDateTime) {
        source.advanceTo(momentOf(moment))
    }

    /** Другая дата при том же времени суток: «приложение планирует приёмы 1 октября». */
    fun moveToDate(date: LocalDate) {
        moveTo(date.atTime(nowLocal().toLocalTime()))
    }

    /** Перелёт: момент остаётся прежним, местные часы сдвигаются вместе с поясом. */
    fun shiftZone(hours: Long) {
        source.moveTo(ZoneId.ofOffset("", ZoneOffset.ofHours(MOSCOW_OFFSET_HOURS + hours.toInt())))
    }

    private companion object {
        val DEFAULT_ZONE: ZoneId = ZoneId.of("Europe/Moscow")

        /**
         * Момент по умолчанию — 25 сентября 2026, 07:00.
         *
         * Предыстории задач называют момент сами строкой «сейчас …»; значение нужно лишь
         * сценарию, который о времени не говорит вовсе.
         */
        val DEFAULT_MOMENT: LocalDateTime = LocalDateTime.of(2026, 9, 25, 7, 0)

        const val MOSCOW_OFFSET_HOURS = 3
    }
}

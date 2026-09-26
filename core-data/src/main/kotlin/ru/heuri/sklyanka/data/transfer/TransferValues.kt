package ru.heuri.sklyanka.data.transfer

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.heuri.sklyanka.domain.transfer.TransferReject
import ru.heuri.sklyanka.domain.transfer.TransferRejectReason
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Отказ разбора, выраженный исключением.
 *
 * Исключение здесь — способ выйти из середины разбора вложенной структуры, а не аварийный
 * исход: наружу кодека оно не выходит, а превращается в [TransferReject]. Альтернатива —
 * протаскивать результат-либо-ошибку через двадцать полей — делает мапперы нечитаемыми,
 * и первое же новое поле окажется разобранным без проверки.
 */
internal class DecodeFailure(val reject: TransferReject) : IllegalArgumentException(reject.logDetail)

/**
 * Разбор и запись примитивов файла переноса.
 *
 * Вынесено из маппера отдельным объектом: это переводы значений, не зависящие ни от часового
 * пояса, ни от предметной области, и держать их рядом с правилами МГ-6 значит однажды
 * обнаружить разбор даты внутри разбора перечисления.
 *
 * Все форматы — из ПЕР-8: календарная дата `ГГГГ-ММ-ДД`, время суток `HH:mm`,
 * момент времени — ISO 8601 со смещением.
 */
internal object TransferValues {

    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val OFFSET: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun requireDecimal(element: JsonElement?, fieldName: String): BigDecimal =
        decimal(element, fieldName) ?: throw DecodeFailure(
            TransferReject(TransferRejectReason.MISSING_SECTION, fieldName),
        )

    fun decimal(element: JsonElement?, fieldName: String): BigDecimal? {
        val text = element?.primitiveOrNull()?.contentOrNull ?: return null
        return runCatching { BigDecimal(text) }.getOrElse { invalid(fieldName) }
    }

    fun date(raw: String, fieldName: String): LocalDate =
        runCatching { LocalDate.parse(raw, DATE) }.getOrElse { invalid(fieldName) }

    fun time(raw: String, fieldName: String): LocalTime =
        runCatching { LocalTime.parse(raw, TIME) }.getOrElse { invalid(fieldName) }

    fun moment(raw: String, fieldName: String): Instant =
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrElse { invalid(fieldName) }

    /**
     * Локальные дата и время: смещение, если оно есть, **отбрасывается**.
     *
     * Именно отбрасывается, а не применяется. Приём «в 08:00» остаётся приёмом в 08:00
     * и на приёмнике в другом поясе (НФТ-34, `TDD.md §8`): расписание — правило человека,
     * а не момент времени. Пересчёт означал бы, что перелёт переписывает медицинскую историю.
     */
    fun localDateTime(raw: String, fieldName: String): LocalDateTime = runCatching {
        runCatching { OffsetDateTime.parse(raw).toLocalDateTime() }
            .getOrElse { LocalDateTime.parse(raw) }
    }.getOrElse { invalid(fieldName) }

    /**
     * Цвет профиля: `#AARRGGBB`, `#RRGGBB` или целое.
     *
     * Читаются все три формы, пишется восьмизначная. Шестизначная не несёт прозрачности,
     * и цикл «выгрузка → загрузка → выгрузка» переставал бы быть точным там, где НФТ-12
     * требует совпадения без расхождений. Это единственное расхождение с примером
     * `docs/spec/07-data-transfer.md` §2 — сам пример помечен там как черновик (ОВ-65).
     */
    @Suppress("ReturnCount")
    fun color(element: JsonElement?): Int {
        val primitive = element?.primitiveOrNull() ?: return 0
        primitive.intOrNull?.let { return it }
        val text = primitive.contentOrNull?.removePrefix(HASH) ?: return 0
        return runCatching { text.toLong(HEX_RADIX).toInt() }.getOrElse { invalid("profiles.color") }
    }

    fun colorText(color: Int): String = HASH + COLOR_FORMAT.format(color)

    /** Дробная величина пишется числом, а не строкой: так её видит и читает любой инструмент. */
    fun number(value: BigDecimal?): JsonElement? = value?.let { JsonPrimitive(it) }

    fun invalid(fieldName: String): Nothing =
        throw DecodeFailure(TransferReject(TransferRejectReason.INVALID_VALUE, fieldName))

    /** Значение не примитив (например, объект на месте числа) — это битый файл, а не ноль. */
    private fun JsonElement.primitiveOrNull() = runCatching { jsonPrimitive }.getOrNull()

    private const val HASH = "#"
    private const val HEX_RADIX = 16
    private const val COLOR_FORMAT = "%08X"
}

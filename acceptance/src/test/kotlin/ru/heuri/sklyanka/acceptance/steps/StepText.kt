package ru.heuri.sklyanka.acceptance.steps

import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Разбор слов сценария, общий для всех наборов шагов.
 *
 * Дата без месяца («26») или без года достраивается по текущему дню сценария, а состояние
 * приёма, исход отметки и её источник читаются из русских слов документа. Раньше каждый
 * набор шагов держал свою копию этих функций — и своё представление о том, что значит
 * «пропущено без отметки».
 */
internal fun AcceptanceWorld.dateOf(text: String): LocalDate = RussianDates.date(text, clock.today())

internal fun AcceptanceWorld.localMoment(date: String, time: String): LocalDateTime =
    dateOf(date).atTime(LocalTime.parse(time))

internal fun resultOf(text: String): MarkResult =
    if (text.startsWith(TAKEN_WORD)) MarkResult.TAKEN else MarkResult.SKIPPED

internal fun statusOf(text: String): IntakeStatus = when {
    text.startsWith(TAKEN_WORD) || text.startsWith(TAKEN_NEUTER) -> IntakeStatus.TAKEN
    text.endsWith(WITHOUT_A_MARK) -> IntakeStatus.MISSED
    text.startsWith(PENDING_WORD) -> IntakeStatus.PENDING
    else -> IntakeStatus.SKIPPED
}

internal fun sourceOf(text: String): IntakeSource = when (text) {
    PHONE_WORD -> IntakeSource.PHONE
    WATCH_WORD -> IntakeSource.WATCH
    else -> IntakeSource.AUTO
}

internal const val TAKEN_WORD = "принят"
internal const val TAKEN_NEUTER = "принято"
internal const val PENDING_WORD = "ожидающ"
internal const val WITHOUT_A_MARK = "без отметки"
internal const val PHONE_WORD = "телефон"
internal const val WATCH_WORD = "часы"
internal const val TAKE_BUTTON = "Принято"

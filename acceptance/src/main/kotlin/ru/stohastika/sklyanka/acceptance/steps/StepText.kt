package ru.stohastika.sklyanka.acceptance.steps

import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
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
fun AcceptanceWorld.dateOf(text: String): LocalDate = RussianDates.date(text, clock.today())

fun AcceptanceWorld.localMoment(date: String, time: String): LocalDateTime = dateOf(date).atTime(LocalTime.parse(time))

/**
 * Имя лекарства из текста шага в том падеже, в каком его поставила фраза.
 *
 * Предыстория заводит «Магний B6», а проверка говорит «остаток упаковки «Магния B6»» —
 * и это одно лекарство. Разбор падежа — [RussianNames]; неизвестное имя остаётся ошибкой
 * шага, а не молчаливым созданием лекарства.
 */
fun AcceptanceWorld.medicationNamed(text: String): String = requireNotNull(
    RussianNames.resolve(text, medications.names()),
) { "unknown medication: $text (known: ${medications.names()})" }

fun resultOf(text: String): MarkResult = if (text.startsWith(TAKEN_WORD)) MarkResult.TAKEN else MarkResult.SKIPPED

fun statusOf(text: String): IntakeStatus = when {
    text.startsWith(TAKEN_WORD) || text.startsWith(TAKEN_NEUTER) -> IntakeStatus.TAKEN
    text.endsWith(WITHOUT_A_MARK) -> IntakeStatus.MISSED
    text.startsWith(PENDING_WORD) || text.startsWith(NOT_MARKED_WORD) -> IntakeStatus.PENDING
    else -> IntakeStatus.SKIPPED
}

fun sourceOf(text: String): IntakeSource = if (text == PHONE_WORD) IntakeSource.PHONE else IntakeSource.AUTO

const val TAKEN_WORD = "принят"
const val TAKEN_NEUTER = "принято"
const val PENDING_WORD = "ожидающ"
const val NOT_MARKED_WORD = "неотмечен"
const val WITHOUT_A_MARK = "без отметки"
const val PHONE_WORD = "телефон"
const val TAKE_BUTTON = "Принято"

package ru.stohastika.sklyanka.feature.widgets

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.intake.IgnoreReason
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.feature.widgets.receiver.WidgetMarking
import java.time.Instant
import java.time.LocalDateTime

/**
 * Нажатие кнопки отметки на плите (ПР-22, `TDD.md §7`).
 *
 * Проверяется то, что до этого подтверждала сама приёмка: какая просьба уходит в домен
 * (источник — `PHONE`, результат — «принято»). Отказ домена (`Ignored`) не глотается:
 * штатная проигравшая отметка видна вызывающему тем же исходом, что у кнопки в приложении
 * (ПР-2). Перерисовку плит делает сам сценарий отметки своими последствиями — нажатие
 * второго вызова не делает (иначе два `updateAll` на одно событие).
 */
class WidgetMarkingTest {

    @Test
    fun `the widget asks the domain for a phone mark`() {
        val request = WidgetMarking.request("intake-1")

        assertEquals("intake-1", request.intakeId)
        assertEquals(MarkResult.TAKEN, request.result)
        assertEquals(
            "the widget mark must take the same path as the screen one (PR-22)",
            IntakeSource.PHONE,
            request.source,
        )
    }

    @Test
    fun `marking sends the request through the single mark path`() = runTest {
        val sent = mutableListOf<MarkRequest>()

        val outcome = WidgetMarking.mark(
            intakeId = "intake-1",
            mark = { request ->
                sent += request
                MarkOutcome.Applied(taken("intake-1"))
            },
        )

        assertEquals(listOf("intake-1"), sent.map { it.intakeId })
        assertEquals(IntakeSource.PHONE, sent.single().source)
        assertTrue("the domain outcome must come back to the caller", outcome is MarkOutcome.Applied)
    }

    @Test
    fun `a second press keeps the ignored outcome`() = runTest {
        val outcome = WidgetMarking.mark(
            intakeId = "intake-1",
            mark = { MarkOutcome.Ignored(taken("intake-1"), IgnoreReason.ALREADY_MARKED) },
        )

        assertEquals(IgnoreReason.ALREADY_MARKED, (outcome as MarkOutcome.Ignored).reason)
    }

    private fun taken(id: String) = Intake(
        id = id,
        medicationId = "med-1",
        scheduledAt = LocalDateTime.of(2026, 10, 13, 8, 0),
        status = IntakeStatus.TAKEN,
        actedAt = Instant.parse("2026-10-13T05:00:00Z"),
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )
}

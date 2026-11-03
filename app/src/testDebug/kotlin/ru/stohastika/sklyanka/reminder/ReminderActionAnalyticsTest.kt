package ru.stohastika.sklyanka.reminder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.acceptance.world.ScenarioAnalytics
import ru.stohastika.sklyanka.acceptance.world.ScenarioClock
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.intake.IgnoreReason
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.Instant
import java.time.LocalDateTime

/**
 * Действия шторки уведомления в аналитике (SKL-165, RT-4).
 *
 * Проверяется выполнение команды, а не разбор намерения: приёмник собирает
 * [ReminderActions] теми же сценариями, что и карточка, а здесь видно, что одно нажатие
 * даёт ровно одно событие и что без согласия очередь остаётся пустой.
 */
class ReminderActionAnalyticsTest {

    @Test
    fun `taking the slot from the shade is one intake action`() = runTest {
        val world = analytics()
        val actions = actions(world)

        actions.markAll(listOf("i-1", "i-2"), MarkResult.TAKEN)

        val record = world.records().single()
        assertEquals(AnalyticsEventName.INTAKE_ACTION, record.name)
        assertEquals(
            mapOf(
                AnalyticsProperty.ACTION to "taken",
                AnalyticsProperty.SOURCE to "phone",
            ),
            record.properties,
        )
    }

    @Test
    fun `skipping the slot from the shade is a skipped intake action`() = runTest {
        val world = analytics()
        val actions = actions(world)

        actions.markAll(listOf("i-1"), MarkResult.SKIPPED)

        assertEquals(
            mapOf(
                AnalyticsProperty.ACTION to "skipped",
                AnalyticsProperty.SOURCE to "phone",
            ),
            world.records().single().properties,
        )
    }

    @Test
    fun `a mark that lost to the watch is not an action`() = runTest {
        val world = analytics()
        val actions = actions(world) { request ->
            MarkOutcome.Ignored(intake(request.intakeId), IgnoreReason.ALREADY_MARKED)
        }

        actions.markAll(listOf("i-1"), MarkResult.TAKEN)

        assertTrue(world.records().isEmpty())
    }

    @Test
    fun `snoozing from the shade is a reminder event with the setting interval`() = runTest {
        val world = analytics()
        val snoozed = mutableListOf<Pair<String, Int>>()
        val actions = ReminderActions(
            mark = { request -> MarkOutcome.Applied(intake(request.intakeId)) },
            snooze = { intakeId, minutes -> snoozed += intakeId to minutes },
            snoozeMinutes = { 15 },
            recorder = world.recorder(CoroutineScope(Dispatchers.Unconfined)),
        )

        actions.snoozeAll(listOf("i-1", "i-2"))

        assertEquals(listOf("i-1" to 15, "i-2" to 15), snoozed)
        val record = world.records().single()
        assertEquals(AnalyticsEventName.REMINDER_SNOOZED, record.name)
        assertEquals(mapOf(AnalyticsProperty.SOURCE to "phone"), record.properties)
    }

    @Test
    fun `a declined consent leaves the queue empty`() = runTest {
        val world = analytics()
        world.consent = AnalyticsConsentState.DECLINED
        val actions = actions(world)

        actions.markAll(listOf("i-1"), MarkResult.TAKEN)
        actions.snoozeAll(listOf("i-1"))

        assertTrue(world.records().isEmpty())
    }

    private fun actions(
        world: ScenarioAnalytics,
        mark: suspend (MarkRequest) -> MarkOutcome = { request -> MarkOutcome.Applied(intake(request.intakeId)) },
    ) = ReminderActions(
        mark = mark,
        snooze = { _, _ -> },
        snoozeMinutes = { 15 },
        recorder = world.recorder(CoroutineScope(Dispatchers.Unconfined)),
    )

    /** Мир аналитики теста: согласие дано заранее — проверяется запись, а не запрос согласия. */
    private fun analytics(): ScenarioAnalytics = ScenarioAnalytics(ScenarioClock()).also {
        it.consent = AnalyticsConsentState.ALLOWED
        it.consentGranted()
    }

    private fun intake(id: String) = Intake(
        id = id,
        medicationId = "med-1",
        scheduledAt = LocalDateTime.of(2026, 10, 13, 8, 0),
        status = IntakeStatus.TAKEN,
        actedAt = Instant.parse("2026-10-13T05:00:00Z"),
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )
}

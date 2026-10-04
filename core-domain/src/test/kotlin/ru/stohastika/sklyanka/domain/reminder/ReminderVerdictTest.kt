package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("The verdict picks exactly one top element by seniority")
class ReminderVerdictTest {

    @Test
    fun `a reliability cause outranks an unacknowledged delivery failure`() {
        val verdict = ReminderVerdictRule.of(
            reliability = reliability(notifications = false, channel = true, exact = true, fullScreen = true),
            deliveryCheck = finished(DeliveryCheckOutcome.NOT_DELIVERED),
            deliveryCheckAcknowledged = false,
            hasUpcoming = true,
        )

        assertEquals(ReminderVerdict.Cause(ReminderWarning.NOTIFICATIONS_OFF), verdict)
    }

    @Test
    fun `an unacknowledged failure is shown as the delivery verdict`() {
        val verdict = ReminderVerdictRule.of(
            reliability = reliable(),
            deliveryCheck = finished(DeliveryCheckOutcome.LATE, lateMinutes = 12),
            deliveryCheckAcknowledged = false,
            hasUpcoming = true,
        )

        assertEquals(ReminderVerdict.DeliveryFailed, verdict)
    }

    @Test
    fun `an acknowledged failure does not block the ok line`() {
        val verdict = ReminderVerdictRule.of(
            reliability = reliable(),
            deliveryCheck = finished(DeliveryCheckOutcome.NOT_DELIVERED),
            deliveryCheckAcknowledged = true,
            hasUpcoming = true,
        )

        assertEquals(ReminderVerdict.Ok(hasUpcoming = true), verdict)
    }

    @Test
    fun `a successful or unfinished check leaves the ok line`() {
        val onTime = ReminderVerdictRule.of(
            reliability = reliable(),
            deliveryCheck = finished(DeliveryCheckOutcome.ON_TIME),
            deliveryCheckAcknowledged = false,
            hasUpcoming = true,
        )
        val unfinished = ReminderVerdictRule.of(
            reliability = reliable(),
            deliveryCheck = finished(DeliveryCheckOutcome.NOT_COMPLETED),
            deliveryCheckAcknowledged = false,
            hasUpcoming = true,
        )

        assertEquals(ReminderVerdict.Ok(hasUpcoming = true), onTime)
        assertEquals(ReminderVerdict.Ok(hasUpcoming = true), unfinished)
    }

    @Test
    fun `no upcoming reminder keeps the ok verdict and tells about it`() {
        val verdict = ReminderVerdictRule.of(
            reliability = reliable(),
            deliveryCheck = DeliveryCheckState.None,
            deliveryCheckAcknowledged = false,
            hasUpcoming = false,
        )

        assertEquals(ReminderVerdict.Ok(hasUpcoming = false), verdict)
    }

    @Test
    fun `the senior reliability cause is the banner text`() {
        val verdict = ReminderVerdictRule.of(
            reliability = ReminderReliability(
                notificationsEnabled = true,
                channelEnabled = false,
                exactAlarmsAllowed = true,
                fullScreenIntentAllowed = true,
            ),
            deliveryCheck = DeliveryCheckState.None,
            deliveryCheckAcknowledged = false,
            hasUpcoming = true,
        )

        assertEquals(ReminderVerdict.Cause(ReminderWarning.NOTIFICATION_CHANNEL_OFF), verdict)
    }

    private fun reliable() = ReminderReliability(
        notificationsEnabled = true,
        channelEnabled = true,
        exactAlarmsAllowed = true,
        fullScreenIntentAllowed = true,
    )

    private fun reliability(notifications: Boolean, channel: Boolean, exact: Boolean, fullScreen: Boolean) =
        ReminderReliability(
            notificationsEnabled = notifications,
            channelEnabled = channel,
            exactAlarmsAllowed = exact,
            fullScreenIntentAllowed = fullScreen,
        )

    private fun finished(outcome: DeliveryCheckOutcome, lateMinutes: Long? = null) = DeliveryCheckState.Finished(
        outcome = outcome,
        dueAt = Instant.parse("2026-09-25T07:01:30Z"),
        resolvedAt = Instant.parse("2026-09-25T07:02:00Z"),
        lateMinutes = lateMinutes,
    )
}

package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("Five conditions decide whether an intake gets an alarm")
class WindowIntakeSelectorTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val now = LocalDateTime.of(2026, 9, 25, 9, 0).atZone(zone).toInstant()
    private val window = ReminderWindow.startingAt(now)

    /**
     * Кандидат по умолчанию: свой профиль, расписание, ожидается.
     *
     * Остальные признаки меняются через `copy` в самих проверках — так видно, чем именно
     * тест отличается от соседнего, а не какой из семи аргументов переставлен.
     */
    private fun candidate(
        id: String = "intake-1",
        at: LocalDateTime = LocalDateTime.of(2026, 9, 25, 14, 0),
        status: IntakeStatus = IntakeStatus.PENDING,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
    ) = ReminderIntake(
        intakeId = id,
        medicationId = "med-1",
        profileId = "profile-1",
        scheduledAt = at,
        status = status,
        scheduleMode = mode,
        medicationArchived = false,
        medicationDeleted = false,
        profileIsSelf = true,
        profileMirrorsReminders = false,
    )

    private fun select(vararg candidates: ReminderIntake) =
        WindowIntakeSelector.select(candidates.toList(), window, zone)

    @Test
    fun `a pending scheduled intake of the own profile is planned`() {
        assertEquals(listOf("intake-1"), select(candidate()).map { it.intakeId })
    }

    @Test
    fun `an as-needed medication produces no alarm at all`() {
        assertTrue(select(candidate(mode = ScheduleMode.PRN)).isEmpty())
    }

    @Test
    fun `an archived medication produces no alarm`() {
        assertTrue(select(candidate().copy(medicationArchived = true)).isEmpty())
    }

    @Test
    fun `a deleted medication produces no alarm`() {
        // ПР-14: удалённое лекарство уходит из плана — будильники питаются им же.
        assertTrue(select(candidate().copy(medicationDeleted = true)).isEmpty())
        assertTrue(WindowIntakeSelector.expected(listOf(candidate().copy(medicationDeleted = true))).isEmpty())
    }

    @Test
    fun `a marked intake is not reminded of again`() {
        IntakeStatus.entries.filter { it != IntakeStatus.PENDING }.forEach { status ->
            assertTrue(select(candidate(status = status)).isEmpty(), "status $status must not be planned")
        }
    }

    @Test
    fun `a foreign profile without mirrored reminders is skipped and with them is planned`() {
        val foreign = candidate().copy(profileIsSelf = false)
        assertTrue(select(foreign).isEmpty())
        assertEquals(1, select(foreign.copy(profileMirrorsReminders = true)).size)
    }

    @Test
    fun `only the intakes inside the window are planned`() {
        val inside = candidate(id = "today", at = LocalDateTime.of(2026, 9, 25, 14, 0))
        val alsoInside = candidate(id = "tomorrow", at = LocalDateTime.of(2026, 9, 26, 14, 0))
        val outside = candidate(id = "third-day", at = LocalDateTime.of(2026, 9, 27, 14, 0))
        val farOutside = candidate(id = "fourth-day", at = LocalDateTime.of(2026, 9, 28, 14, 0))

        val planned = select(inside, alsoInside, outside, farOutside).map { it.intakeId }

        assertEquals(listOf("today", "tomorrow"), planned)
    }

    @Test
    fun `the local time of the intake is resolved with the current zone`() {
        val planned = select(candidate(at = LocalDateTime.of(2026, 9, 25, 14, 0))).single()

        assertEquals(LocalDateTime.of(2026, 9, 25, 14, 0).atZone(zone).toInstant(), planned.at)
    }

    @Test
    fun `the order is by moment and stable for the same moment`() {
        val second = candidate(id = "b", at = LocalDateTime.of(2026, 9, 25, 14, 0))
        val first = candidate(id = "a", at = LocalDateTime.of(2026, 9, 25, 14, 0))
        val later = candidate(id = "c", at = LocalDateTime.of(2026, 9, 25, 20, 0))

        assertEquals(listOf("a", "b", "c"), select(later, second, first).map { it.intakeId })
    }
}

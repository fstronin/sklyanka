package ru.stohastika.sklyanka.feature.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.ReminderCardItem
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Перевод карточки домена в состояние экрана.
 *
 * Проверяется на JVM: эмулятора в среде задачи нет, а правилу он и не нужен. Тексты
 * и вёрстка проверяются экранным тестом, который здесь только компилируется.
 */
class ReminderUiStateTest {

    private val at: LocalDateTime = LocalDateTime.of(2026, 9, 25, 14, 0)
    private val slot = ReminderSlot(profileId = "profile-1", scheduledAt = at)

    private fun item(id: String = "intake-1", name: String = "magnesium") = ReminderCardItem(
        intakeId = id,
        medicationId = "med-$id",
        medicationName = name,
        form = MedicationForm.TABLET,
        doseAmount = BigDecimal("2"),
        doseUnit = "tablet",
        strength = BigDecimal("48"),
        strengthUnit = "mg",
    )

    private fun card(
        items: List<ReminderCardItem> = listOf(item()),
        isSelf: Boolean = true,
        profileName: String = "self",
    ) = ReminderCard(
        slot = slot,
        scheduledAt = at,
        items = items,
        profileIsSelf = isSelf,
        profileName = profileName,
    )

    @Test
    fun `the snooze interval comes from the setting and is not a constant`() {
        assertEquals(15, card().toUiState(snoozeMinutes = 15).snoozeMinutes)
        assertEquals(30, card().toUiState(snoozeMinutes = 30).snoozeMinutes)
        assertEquals(60, card().toUiState(snoozeMinutes = 60).snoozeMinutes)
    }

    @Test
    fun `a single intake becomes a single row with its dose and strength`() {
        val state = card().toUiState(snoozeMinutes = 15)

        val row = state.items.single()
        assertEquals("magnesium", row.name)
        assertEquals(BigDecimal("2"), row.doseAmount)
        assertEquals(BigDecimal("48"), row.strength)
        assertEquals("mg", row.strengthUnit)
        assertEquals(at, state.scheduledAt)
    }

    @Test
    fun `two intakes on one time become two rows of one card`() {
        val state = card(items = listOf(item(), item(id = "intake-2", name = "vitamin"))).toUiState(15)

        assertEquals(listOf("magnesium", "vitamin"), state.items.map { it.name })
    }

    @Test
    fun `the own profile carries no profile indication and a foreign one does`() {
        assertNull(card().toUiState(15).foreignProfileName)
        assertEquals("mother", card(isSelf = false, profileName = "mother").toUiState(15).foreignProfileName)
    }
}

package ru.stohastika.sklyanka.domain.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Событие — закрытая форма, а не переносчик данных.
 *
 * Показ экрана несёт ровно одно свойство, режим лекарства есть только у добавления,
 * а запись очереди содержит только проводные имена закрытых списков: свободному тексту
 * неоткуда взяться (АН-3, ПМ-26).
 */
class AnalyticsScreenViewTest {

    private val occurredAt: Instant = Instant.parse("2026-05-01T08:00:00Z")

    @Test
    fun `a screen view carries exactly one property`() {
        val event = ScreenView(AnalyticsScreen.MEDICATION_FORM)

        assertEquals(AnalyticsEventName.SCREEN_VIEW, event.name)
        assertEquals(mapOf(AnalyticsProperty.SCREEN to "medication_form"), event.properties)
    }

    @Test
    fun `a screen view record keeps the moment and the product scope`() {
        val record = ScreenView(AnalyticsScreen.HISTORY).toRecord(occurredAt)

        assertEquals(AnalyticsEventName.SCREEN_VIEW, record.name)
        assertEquals(occurredAt, record.occurredAt)
        assertEquals(AnalyticsScope.PRODUCT, record.scope)
        assertEquals(mapOf(AnalyticsProperty.SCREEN to "history"), record.properties)
    }

    @Test
    fun `adding a medication demands the mode`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MedicationAction(MedicationActionKind.ADDED)
        }

        assertEquals("medication mode is set exactly for an added medication", failure.message)
    }

    @Test
    fun `adding a medication keeps its mode`() {
        val event = MedicationAction(MedicationActionKind.ADDED, MedicationMode.PRN)

        assertEquals(
            mapOf(
                AnalyticsProperty.ACTION to "added",
                AnalyticsProperty.MODE to "prn",
            ),
            event.properties,
        )
    }

    @Test
    fun `archiving a medication has no mode`() {
        val event = MedicationAction(MedicationActionKind.ARCHIVED)

        assertEquals(mapOf(AnalyticsProperty.ACTION to "archived"), event.properties)
    }

    @Test
    fun `a mode on a non-adding action is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            MedicationAction(MedicationActionKind.DELETED, MedicationMode.SCHEDULED)
        }
    }

    @Test
    fun `a record holds nothing but wire names of closed lists`() {
        val declared = listOf(
            ConsentShownSource.entries.map { it.wire },
            ConsentAnswer.entries.map { it.wire },
            AnalyticsEnabledSource.entries.map { it.wire },
            OnboardingEntry.entries.map { it.wire },
            OnboardingStep.entries.map { it.wire },
            StepResult.entries.map { it.wire },
            OnboardingOutcome.entries.map { it.wire },
            AnalyticsScreen.entries.map { it.wire },
            MedicationActionKind.entries.map { it.wire },
            MedicationMode.entries.map { it.wire },
            IntakeActionKind.entries.map { it.wire },
            ActionSource.entries.map { it.wire },
            OperationResult.entries.map { it.wire },
            TransferDirection.entries.map { it.wire },
            DeliveryCheckOutcome.entries.map { it.wire },
        ).flatten().toSet()

        val record = MedicationAction(MedicationActionKind.ADDED, MedicationMode.SCHEDULED).toRecord(occurredAt)

        record.properties.forEach { (key, value) ->
            assertTrue(value in declared, "${key.wire} = '$value' is a closed-list value")
        }
    }
}

package ru.stohastika.sklyanka.domain.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Каталог событий ПМ-26: тринадцать имён, десять свойств и только закрытые значения.
 *
 * Тест — доказательство, а не пересказ: проводные имена сверяются с таблицей продукта,
 * а каждое событие обязано нести ровно те свойства, что разрешены ему каталогом.
 */
class AnalyticsCatalogTest {

    /** События каталога: по одному экземпляру на каждое имя. */
    private val samples: List<AnalyticsEvent> = listOf(
        ConsentShown(ConsentShownSource.FIRST_LAUNCH),
        ConsentChoice(ConsentAnswer.ACCEPTED),
        AnalyticsEnabled,
        OnboardingStarted(OnboardingEntry.FIRST_LAUNCH),
        OnboardingStepResult(OnboardingStep.NOTIFICATIONS, StepResult.GRANTED),
        OnboardingFinished(OnboardingEntry.FIRST_LAUNCH, OnboardingOutcome.COMPLETED),
        ScreenView(AnalyticsScreen.TODAY),
        MedicationAction(MedicationActionKind.ADDED, MedicationMode.SCHEDULED),
        IntakeAction(IntakeActionKind.TAKEN, ActionSource.PHONE),
        ReminderSnoozed(ActionSource.PHONE),
        ReportExported(OperationResult.SUCCESS),
        TransferResult(TransferDirection.EXPORT, OperationResult.SUCCESS),
        DeliveryCheckResult(DeliveryCheckOutcome.PASSED),
    )

    /** Допустимые свойства каждого события — дословно из таблицы ПМ-26. */
    private val allowedProperties: Map<AnalyticsEventName, Set<AnalyticsProperty>> = mapOf(
        AnalyticsEventName.CONSENT_SHOWN to setOf(AnalyticsProperty.SOURCE),
        AnalyticsEventName.CONSENT_CHOICE to setOf(AnalyticsProperty.CHOICE),
        AnalyticsEventName.ANALYTICS_ENABLED to setOf(AnalyticsProperty.SOURCE),
        AnalyticsEventName.ONBOARDING_STARTED to setOf(AnalyticsProperty.ENTRY),
        AnalyticsEventName.ONBOARDING_STEP_RESULT to
            setOf(AnalyticsProperty.STEP, AnalyticsProperty.RESULT),
        AnalyticsEventName.ONBOARDING_FINISHED to
            setOf(AnalyticsProperty.ENTRY, AnalyticsProperty.OUTCOME),
        AnalyticsEventName.SCREEN_VIEW to setOf(AnalyticsProperty.SCREEN),
        AnalyticsEventName.MEDICATION_ACTION to setOf(AnalyticsProperty.ACTION, AnalyticsProperty.MODE),
        AnalyticsEventName.INTAKE_ACTION to setOf(AnalyticsProperty.ACTION, AnalyticsProperty.SOURCE),
        AnalyticsEventName.REMINDER_SNOOZED to setOf(AnalyticsProperty.SOURCE),
        AnalyticsEventName.REPORT_EXPORTED to setOf(AnalyticsProperty.RESULT),
        AnalyticsEventName.TRANSFER_RESULT to
            setOf(AnalyticsProperty.DIRECTION, AnalyticsProperty.RESULT),
        AnalyticsEventName.DELIVERY_CHECK_RESULT to setOf(AnalyticsProperty.RESULT),
    )

    @Test
    fun `the catalog holds exactly the thirteen base events`() {
        assertEquals(13, AnalyticsEventName.entries.size)
        assertEquals(AnalyticsEventName.entries.toSet(), samples.map { it.name }.toSet())
    }

    @Test
    fun `wire names match the product table`() {
        assertEquals(
            setOf(
                "consent_shown",
                "consent_choice",
                "analytics_enabled",
                "onboarding_started",
                "onboarding_step_result",
                "onboarding_finished",
                "screen_view",
                "medication_action",
                "intake_action",
                "reminder_snoozed",
                "report_exported",
                "transfer_result",
                "delivery_check_result",
            ),
            AnalyticsEventName.entries.map { it.wire }.toSet(),
        )
    }

    @Test
    fun `the paid funnel is reserved and absent from the catalog`() {
        val wires = AnalyticsEventName.entries.map { it.wire }

        assertFalse(wires.any { it.startsWith("paywall_") }, "the paid zone does not exist (R-94)")
        assertFalse(wires.contains("purchase_result"), "there are no purchases, so the event has no source")
    }

    @Test
    fun `each event carries only the properties the product allows`() {
        samples.forEach { event ->
            assertEquals(
                allowedProperties.getValue(event.name),
                event.properties.keys,
                "properties of ${event.name.wire}",
            )
        }
        assertEquals(AnalyticsEventName.entries.toSet(), allowedProperties.keys)
    }

    @Test
    fun `there are exactly ten property keys`() {
        assertEquals(10, AnalyticsProperty.entries.size)
        assertEquals(
            setOf(
                "source",
                "choice",
                "entry",
                "step",
                "result",
                "screen",
                "action",
                "mode",
                "outcome",
                "direction",
            ),
            AnalyticsProperty.entries.map { it.wire }.toSet(),
        )
    }

    @Test
    fun `every closed list carries a wire name`() {
        allWireGroups().forEach { wires ->
            assertEquals(wires.size, wires.toSet().size, "values inside a closed list do not repeat")
            wires.forEach { wire ->
                assertTrue(
                    WIRE_NAME.matches(wire),
                    "wire name '$wire' is a closed value, not free text",
                )
            }
        }
    }

    @Test
    fun `every property value of every event is a declared wire name`() {
        val declared = allWireGroups().flatten().toSet()

        samples.forEach { event ->
            event.properties.forEach { (property, value) ->
                assertTrue(value in declared, "${event.name.wire}:${property.wire} = '$value' is declared")
            }
        }
    }

    @Test
    fun `consent shown accepts three sources including the settings return`() {
        assertEquals(
            listOf("first_launch", "policy_update", "settings"),
            ConsentShownSource.entries.map { it.wire },
        )
    }

    @Test
    fun `consent events live in the consent scope and product events do not`() {
        samples.forEach { event ->
            val consent = event.name in setOf(
                AnalyticsEventName.CONSENT_SHOWN,
                AnalyticsEventName.CONSENT_CHOICE,
                AnalyticsEventName.ANALYTICS_ENABLED,
            )
            assertEquals(consent, event.scope == AnalyticsScope.CONSENT, event.name.wire)
        }
    }

    @Test
    fun `the screen list is closed and includes the unreachable ones`() {
        assertEquals(
            setOf(
                "today",
                "medications",
                "archive",
                "medication_card",
                "medication_form",
                "history",
                "report",
                "settings",
                "data_transfer",
                "reminders_check",
                "profiles",
                "watch_setup",
                "about",
            ),
            AnalyticsScreen.entries.map { it.wire }.toSet(),
        )
    }

    private fun allWireGroups(): List<List<String>> = listOf(
        AnalyticsEventName.entries.map { it.wire },
        AnalyticsProperty.entries.map { it.wire },
        AnalyticsScope.entries.map { it.wire },
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
    )

    private companion object {
        val WIRE_NAME = Regex("[a-z][a-z_]*")
    }
}

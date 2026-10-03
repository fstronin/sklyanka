package ru.stohastika.sklyanka.domain.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Каталог событий (ПМ-26): состав и закрытые значения свойств.
 *
 * Тест перечисляет события явно, а не отражением: `when` по sealed-типу делает разбор
 * исчерпывающим на компиляции, поэтому новое событие обязано прийти сюда — иначе тест
 * не соберётся. Так «новое событие — только решением продакт-менеджера» становится
 * проверкой, а не пожеланием.
 */
@DisplayName("The analytics catalog stays closed: thirteen base events, three reserved")
class AnalyticsEventTest {

    private val baseEvents = listOf(
        AnalyticsEvent.ConsentShown(ConsentSource.FIRST_LAUNCH),
        AnalyticsEvent.ConsentChoice(ConsentOutcome.ACCEPTED),
        AnalyticsEvent.AnalyticsEnabled(AnalyticsEnabledSource.SETTINGS),
        AnalyticsEvent.OnboardingStarted(OnboardingEntry.FIRST_LAUNCH),
        AnalyticsEvent.OnboardingStepResult(OnboardingStep.NOTIFICATIONS, StepResult.GRANTED),
        AnalyticsEvent.OnboardingFinished(OnboardingEntry.FIRST_LAUNCH, OnboardingOutcome.COMPLETED),
        AnalyticsEvent.ScreenView(ScreenName.TODAY),
        AnalyticsEvent.MedicationAction(MedicationActionType.ADDED, MedicationMode.SCHEDULED),
        AnalyticsEvent.IntakeAction(IntakeActionType.TAKEN, ActionSource.PHONE),
        AnalyticsEvent.ReminderSnoozed(ActionSource.PHONE),
        AnalyticsEvent.ReportExported(OperationResult.SUCCESS),
        AnalyticsEvent.TransferResult(TransferDirection.EXPORT, OperationResult.SUCCESS),
        AnalyticsEvent.DeliveryCheckResult(DeliveryCheckOutcome.PASSED),
    )

    private val reservedEvents = listOf(
        AnalyticsEvent.PaywallShown(PaywallEntry.SETTINGS),
        AnalyticsEvent.PaywallPlanSelected(SubscriptionPlan.MONTHLY),
        AnalyticsEvent.PurchaseResult(PurchaseOutcome.SUCCESS, SubscriptionPlan.YEARLY),
    )

    @Test
    fun `thirteen base events are declared`() {
        assertEquals(13, baseEvents.size)
    }

    @Test
    fun `three paywall events are reserved for the paid zone and stay out of the base`() {
        assertEquals(3, reservedEvents.size)
    }

    @Test
    fun `every event maps to exactly one product name`() {
        val names = (baseEvents + reservedEvents).map(::productName)

        assertEquals(
            listOf(
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
                "paywall_shown",
                "paywall_plan_selected",
                "purchase_result",
            ),
            names,
        )
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `screen names are a closed product list`() {
        assertEquals(
            setOf(
                "today", "medications", "archive", "medication_card", "medication_form",
                "history", "report", "settings", "data_transfer", "reminders_check",
                "profiles", "watch_setup", "about", "consent", "onboarding", "reminder",
                "delivery_check",
            ),
            ScreenName.entries.map { it.name.lowercase() }.toSet(),
        )
    }

    @Test
    fun `property values match the product catalog`() {
        assertEquals(setOf("FIRST_LAUNCH", "POLICY_UPDATE", "SETTINGS"), enumNames<ConsentSource>())
        assertEquals(setOf("ACCEPTED", "NOT_NOW"), enumNames<ConsentOutcome>())
        assertEquals(setOf("SETTINGS"), enumNames<AnalyticsEnabledSource>())
        assertEquals(
            setOf("FIRST_LAUNCH", "TODAY_BANNER", "BREAK_BANNER", "SETTINGS", "DELIVERY_CHECK"),
            enumNames<OnboardingEntry>(),
        )
        assertEquals(
            setOf("NOTIFICATIONS", "EXACT_ALARMS", "APP_LAUNCH", "BATTERY", "RECENTS"),
            enumNames<OnboardingStep>(),
        )
        assertEquals(setOf("GRANTED", "CONFIRMED", "SKIPPED", "NOT_APPLICABLE"), enumNames<StepResult>())
        assertEquals(setOf("COMPLETED", "SKIPPED"), enumNames<OnboardingOutcome>())
        assertEquals(setOf("ADDED", "ARCHIVED", "DELETED", "UNDO"), enumNames<MedicationActionType>())
        assertEquals(setOf("SCHEDULED", "PRN"), enumNames<MedicationMode>())
        assertEquals(setOf("TAKEN", "SKIPPED", "UNDO"), enumNames<IntakeActionType>())
        assertEquals(setOf("PHONE", "WATCH"), enumNames<ActionSource>())
        assertEquals(setOf("SUCCESS", "CANCELLED", "ERROR"), enumNames<OperationResult>())
        assertEquals(setOf("EXPORT", "IMPORT"), enumNames<TransferDirection>())
        assertEquals(setOf("PASSED", "LATE", "FAILED"), enumNames<DeliveryCheckOutcome>())
        assertEquals(setOf("SETTINGS", "FEATURE_GATE"), enumNames<PaywallEntry>())
        assertEquals(setOf("MONTHLY", "YEARLY"), enumNames<SubscriptionPlan>())
        assertEquals(setOf("SUCCESS", "RESTORED", "CANCELLED", "FAILED"), enumNames<PurchaseOutcome>())
    }

    private inline fun <reified T : Enum<T>> enumNames(): Set<String> = enumValues<T>().map { it.name }.toSet()

    /** Технические имена вендора: адаптер повторяет это отображение ровно в одном месте. */
    @Suppress("CyclomaticComplexMethod")
    private fun productName(event: AnalyticsEvent): String = when (event) {
        is AnalyticsEvent.ConsentShown -> "consent_shown"
        is AnalyticsEvent.ConsentChoice -> "consent_choice"
        is AnalyticsEvent.AnalyticsEnabled -> "analytics_enabled"
        is AnalyticsEvent.OnboardingStarted -> "onboarding_started"
        is AnalyticsEvent.OnboardingStepResult -> "onboarding_step_result"
        is AnalyticsEvent.OnboardingFinished -> "onboarding_finished"
        is AnalyticsEvent.ScreenView -> "screen_view"
        is AnalyticsEvent.MedicationAction -> "medication_action"
        is AnalyticsEvent.IntakeAction -> "intake_action"
        is AnalyticsEvent.ReminderSnoozed -> "reminder_snoozed"
        is AnalyticsEvent.ReportExported -> "report_exported"
        is AnalyticsEvent.TransferResult -> "transfer_result"
        is AnalyticsEvent.DeliveryCheckResult -> "delivery_check_result"
        is AnalyticsEvent.PaywallShown -> "paywall_shown"
        is AnalyticsEvent.PaywallPlanSelected -> "paywall_plan_selected"
        is AnalyticsEvent.PurchaseResult -> "purchase_result"
    }
}

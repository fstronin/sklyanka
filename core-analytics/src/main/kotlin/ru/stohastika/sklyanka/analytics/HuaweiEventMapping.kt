package ru.stohastika.sklyanka.analytics

import android.os.Bundle
import ru.stohastika.sklyanka.domain.analytics.ActionSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEnabledSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.ConsentOutcome
import ru.stohastika.sklyanka.domain.analytics.ConsentSource
import ru.stohastika.sklyanka.domain.analytics.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.analytics.IntakeActionType
import ru.stohastika.sklyanka.domain.analytics.MedicationActionType
import ru.stohastika.sklyanka.domain.analytics.MedicationMode
import ru.stohastika.sklyanka.domain.analytics.OnboardingEntry
import ru.stohastika.sklyanka.domain.analytics.OnboardingOutcome
import ru.stohastika.sklyanka.domain.analytics.OnboardingStep
import ru.stohastika.sklyanka.domain.analytics.OperationResult
import ru.stohastika.sklyanka.domain.analytics.PaywallEntry
import ru.stohastika.sklyanka.domain.analytics.PurchaseOutcome
import ru.stohastika.sklyanka.domain.analytics.ScreenName
import ru.stohastika.sklyanka.domain.analytics.StepResult
import ru.stohastika.sklyanka.domain.analytics.SubscriptionPlan
import ru.stohastika.sklyanka.domain.analytics.TransferDirection

/**
 * Отображение события каталога на технический формат вендора (TDD §5.3).
 *
 * Единственное место, где продуктовое событие превращается в строку SDK: ни один другой
 * модуль не знает имён вендора. Значения свойств — только закрытые перечисления домена,
 * никакого свободного текста и идентификаторов.
 */
@Suppress("CyclomaticComplexMethod")
internal fun AnalyticsEvent.toVendorEvent(): VendorEvent = when (this) {
    is AnalyticsEvent.ConsentShown -> event("consent_shown") { putString("source", source.code) }
    is AnalyticsEvent.ConsentChoice -> event("consent_choice") { putString("choice", choice.code) }
    is AnalyticsEvent.AnalyticsEnabled -> event("analytics_enabled") { putString("source", source.code) }
    is AnalyticsEvent.OnboardingStarted -> event("onboarding_started") { putString("entry", entry.code) }
    is AnalyticsEvent.OnboardingStepResult -> event("onboarding_step_result") {
        putString("step", step.code)
        putString("result", result.code)
    }
    is AnalyticsEvent.OnboardingFinished -> event("onboarding_finished") {
        putString("entry", entry.code)
        putString("outcome", outcome.code)
    }
    is AnalyticsEvent.ScreenView -> event("screen_view") { putString("screen", screen.code) }
    is AnalyticsEvent.MedicationAction -> event("medication_action") {
        putString("action", action.code)
        mode?.let { putString("mode", it.code) }
    }
    is AnalyticsEvent.IntakeAction -> event("intake_action") {
        putString("action", action.code)
        putString("source", source.code)
    }
    is AnalyticsEvent.ReminderSnoozed -> event("reminder_snoozed") { putString("source", source.code) }
    is AnalyticsEvent.ReportExported -> event("report_exported") { putString("result", result.code) }
    is AnalyticsEvent.TransferResult -> event("transfer_result") {
        putString("direction", direction.code)
        putString("result", result.code)
    }
    is AnalyticsEvent.DeliveryCheckResult -> event("delivery_check_result") { putString("result", result.code) }
    is AnalyticsEvent.PaywallShown -> event("paywall_shown") { putString("entry", entry.code) }
    is AnalyticsEvent.PaywallPlanSelected -> event("paywall_plan_selected") { putString("plan", plan.code) }
    is AnalyticsEvent.PurchaseResult -> event("purchase_result") {
        putString("result", result.code)
        putString("plan", plan.code)
    }
}

/** Имя события вендора и его свойства — без доменных полей по построению. */
internal data class VendorEvent(val name: String, val params: Bundle)

private fun event(name: String, params: Bundle.() -> Unit = {}): VendorEvent = VendorEvent(name, Bundle().apply(params))

private val ConsentSource.code: String
    get() = when (this) {
        ConsentSource.FIRST_LAUNCH -> "first_launch"
        ConsentSource.POLICY_UPDATE -> "policy_update"
        ConsentSource.SETTINGS -> "settings"
    }

private val ConsentOutcome.code: String
    get() = when (this) {
        ConsentOutcome.ACCEPTED -> "accepted"
        ConsentOutcome.NOT_NOW -> "not_now"
    }

private val AnalyticsEnabledSource.code: String
    get() = when (this) {
        AnalyticsEnabledSource.SETTINGS -> "settings"
    }

private val OnboardingEntry.code: String
    get() = when (this) {
        OnboardingEntry.FIRST_LAUNCH -> "first_launch"
        OnboardingEntry.TODAY_BANNER -> "today_banner"
        OnboardingEntry.BREAK_BANNER -> "break_banner"
        OnboardingEntry.SETTINGS -> "settings"
        OnboardingEntry.DELIVERY_CHECK -> "delivery_check"
    }

private val OnboardingStep.code: String
    get() = when (this) {
        OnboardingStep.NOTIFICATIONS -> "notifications"
        OnboardingStep.EXACT_ALARMS -> "exact_alarms"
        OnboardingStep.APP_LAUNCH -> "app_launch"
        OnboardingStep.BATTERY -> "battery"
        OnboardingStep.RECENTS -> "recents"
    }

private val StepResult.code: String
    get() = when (this) {
        StepResult.GRANTED -> "granted"
        StepResult.CONFIRMED -> "confirmed"
        StepResult.SKIPPED -> "skipped"
        StepResult.NOT_APPLICABLE -> "not_applicable"
    }

private val OnboardingOutcome.code: String
    get() = when (this) {
        OnboardingOutcome.COMPLETED -> "completed"
        OnboardingOutcome.SKIPPED -> "skipped"
    }

private val ScreenName.code: String
    get() = when (this) {
        ScreenName.TODAY -> "today"
        ScreenName.MEDICATIONS -> "medications"
        ScreenName.ARCHIVE -> "archive"
        ScreenName.MEDICATION_CARD -> "medication_card"
        ScreenName.MEDICATION_FORM -> "medication_form"
        ScreenName.HISTORY -> "history"
        ScreenName.REPORT -> "report"
        ScreenName.SETTINGS -> "settings"
        ScreenName.DATA_TRANSFER -> "data_transfer"
        ScreenName.REMINDERS_CHECK -> "reminders_check"
        ScreenName.PROFILES -> "profiles"
        ScreenName.WATCH_SETUP -> "watch_setup"
        ScreenName.ABOUT -> "about"
        ScreenName.CONSENT -> "consent"
        ScreenName.ONBOARDING -> "onboarding"
        ScreenName.REMINDER -> "reminder"
        ScreenName.DELIVERY_CHECK -> "delivery_check"
    }

private val MedicationActionType.code: String
    get() = when (this) {
        MedicationActionType.ADDED -> "added"
        MedicationActionType.ARCHIVED -> "archived"
        MedicationActionType.DELETED -> "deleted"
        MedicationActionType.UNDO -> "undo"
    }

private val MedicationMode.code: String
    get() = when (this) {
        MedicationMode.SCHEDULED -> "scheduled"
        MedicationMode.PRN -> "prn"
    }

private val IntakeActionType.code: String
    get() = when (this) {
        IntakeActionType.TAKEN -> "taken"
        IntakeActionType.SKIPPED -> "skipped"
        IntakeActionType.UNDO -> "undo"
    }

private val ActionSource.code: String
    get() = when (this) {
        ActionSource.PHONE -> "phone"
        ActionSource.WATCH -> "watch"
    }

private val OperationResult.code: String
    get() = when (this) {
        OperationResult.SUCCESS -> "success"
        OperationResult.CANCELLED -> "cancelled"
        OperationResult.ERROR -> "error"
    }

private val TransferDirection.code: String
    get() = when (this) {
        TransferDirection.EXPORT -> "export"
        TransferDirection.IMPORT -> "import"
    }

private val DeliveryCheckOutcome.code: String
    get() = when (this) {
        DeliveryCheckOutcome.PASSED -> "passed"
        DeliveryCheckOutcome.LATE -> "late"
        DeliveryCheckOutcome.FAILED -> "failed"
    }

private val PaywallEntry.code: String
    get() = when (this) {
        PaywallEntry.SETTINGS -> "settings"
        PaywallEntry.FEATURE_GATE -> "feature_gate"
    }

private val SubscriptionPlan.code: String
    get() = when (this) {
        SubscriptionPlan.MONTHLY -> "monthly"
        SubscriptionPlan.YEARLY -> "yearly"
    }

private val PurchaseOutcome.code: String
    get() = when (this) {
        PurchaseOutcome.SUCCESS -> "success"
        PurchaseOutcome.RESTORED -> "restored"
        PurchaseOutcome.CANCELLED -> "cancelled"
        PurchaseOutcome.FAILED -> "failed"
    }

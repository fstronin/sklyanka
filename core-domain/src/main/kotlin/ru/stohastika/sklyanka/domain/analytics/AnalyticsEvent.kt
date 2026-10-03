package ru.stohastika.sklyanka.domain.analytics

/**
 * Каталог событий аналитики (ПМ-26).
 *
 * Свойства — только закрытые перечисления, утверждённые продуктом; ни строки, ни числа,
 * ни времени, ни идентификатора в событие передать нельзя. Медицинские данные не
 * маскируются «обезличиванием»: связи события с лекарством, приёмом или профилем
 * не существует конструктивно.
 *
 * Имя события как строка вендора живёт в адаптере (`core-analytics), а не здесь:
 * домен знает продуктовое событие, а не технический код SDK. Новое событие или новое
 * значение свойства — только решением продакт-менеджера (ПМ-26).
 */
sealed interface AnalyticsEvent {

    /** Показ экрана согласия: охват первого запуска и повторный показ после смены версии. */
    data class ConsentShown(val source: ConsentSource) : AnalyticsEvent

    /**
     * Исход решения по согласию.
     *
     * `accepted` уходит при разрешении; `not_now` — только при повторном решении, когда
     * контур уже был активен: первичный отказ не отправляет ничего (до согласия
     * SDK не активируется, ПМ-19).
     */
    data class ConsentChoice(val choice: ConsentOutcome) : AnalyticsEvent

    /** Включение аналитики позже в настройках; выключение события не отправляет (ПМ-26). */
    data class AnalyticsEnabled(val source: AnalyticsEnabledSource) : AnalyticsEvent

    /** Вход в «Настроить надёжность»: воронка по входам. */
    data class OnboardingStarted(val entry: OnboardingEntry) : AnalyticsEvent

    /** Шаг цепочки надёжности: где рвётся настройка. */
    data class OnboardingStepResult(val step: OnboardingStep, val result: StepResult) : AnalyticsEvent

    /** Исход онбординга: дошли или пропустили. */
    data class OnboardingFinished(val entry: OnboardingEntry, val outcome: OnboardingOutcome) : AnalyticsEvent

    /** Использование экранов, удержание, шаги сценариев. */
    data class ScreenView(val screen: ScreenName) : AnalyticsEvent

    /** Жизненный цикл записи лекарства, включая мягкое удаление и отмену (ПМ-24). */
    data class MedicationAction(val action: MedicationActionType, val mode: MedicationMode?) : AnalyticsEvent

    /** Базовый цикл отметок: вовлечённость и удержание. */
    data class IntakeAction(val action: IntakeActionType, val source: ActionSource) : AnalyticsEvent

    /** Использование «Отложить» (ПМ-15). */
    data class ReminderSnoozed(val source: ActionSource) : AnalyticsEvent

    /** Использование базового PDF врачу. */
    data class ReportExported(val result: OperationResult) : AnalyticsEvent

    /** Перенос данных: пользуются ли и где сбоит. */
    data class TransferResult(val direction: TransferDirection, val result: OperationResult) : AnalyticsEvent

    /** Самопроверка доставки (ПМ-21): доля устройств с проблемами. */
    data class DeliveryCheckResult(val result: DeliveryCheckOutcome) : AnalyticsEvent

    // Три события платной зоны зарезервированы каталогом (H1, SKL-50/SKL-51).
    // Точек вызова до появления платной зоны нет.

    /** Воронка paywall → подписка: точки касания. */
    data class PaywallShown(val entry: PaywallEntry) : AnalyticsEvent

    /** Выбор тарифа. */
    data class PaywallPlanSelected(val plan: SubscriptionPlan) : AnalyticsEvent

    /** Конверсия покупки и восстановления, сбои. */
    data class PurchaseResult(val result: PurchaseOutcome, val plan: SubscriptionPlan) : AnalyticsEvent
}

/** Источник показа экрана согласия. */
enum class ConsentSource {
    FIRST_LAUNCH,
    POLICY_UPDATE,

    /** Включение аналитики из настроек: тот же экран, другое решение (ПМ-25 п. 4). */
    SETTINGS,
}

/** Исход решения человека на экране согласия. */
enum class ConsentOutcome {
    ACCEPTED,
    NOT_NOW,
}

/** Источник включения аналитики из настроек. */
enum class AnalyticsEnabledSource {
    SETTINGS,
}

/** Вход в цепочку «Настроить надёжность». */
enum class OnboardingEntry {
    FIRST_LAUNCH,
    TODAY_BANNER,
    BREAK_BANNER,
    SETTINGS,
    DELIVERY_CHECK,
}

/** Шаг цепочки надёжности. */
enum class OnboardingStep {
    NOTIFICATIONS,
    EXACT_ALARMS,
    APP_LAUNCH,
    BATTERY,
    RECENTS,
}

/** Результат шага цепочки надёжности. */
enum class StepResult {
    GRANTED,
    CONFIRMED,
    SKIPPED,
    NOT_APPLICABLE,
}

/** Исход онбординга. */
enum class OnboardingOutcome {
    COMPLETED,
    SKIPPED,
}

/** Закрытый список экранов, по которым считается использование и удержание. */
enum class ScreenName {
    TODAY,
    MEDICATIONS,
    ARCHIVE,
    MEDICATION_CARD,
    MEDICATION_FORM,
    HISTORY,
    REPORT,
    SETTINGS,
    DATA_TRANSFER,
    REMINDERS_CHECK,
    PROFILES,
    WATCH_SETUP,
    ABOUT,
    CONSENT,
    ONBOARDING,
    REMINDER,
    DELIVERY_CHECK,
}

/** Действие жизненного цикла лекарства. */
enum class MedicationActionType {
    ADDED,
    ARCHIVED,
    DELETED,
    UNDO,
}

/** Режим приёма для события добавления. */
enum class MedicationMode {
    SCHEDULED,
    PRN,
}

/** Действие отметки приёма. */
enum class IntakeActionType {
    TAKEN,
    SKIPPED,
    UNDO,
}

/** Источник действия: телефон или часы. */
enum class ActionSource {
    PHONE,
    WATCH,
}

/** Исход операции, у которой человек отменял или ошибался. */
enum class OperationResult {
    SUCCESS,
    CANCELLED,
    ERROR,
}

/** Направление переноса данных. */
enum class TransferDirection {
    EXPORT,
    IMPORT,
}

/** Исход самопроверки доставки. */
enum class DeliveryCheckOutcome {
    PASSED,
    LATE,
    FAILED,
}

/** Закрытый список нелечебных входов в paywall (H1). */
enum class PaywallEntry {
    SETTINGS,
    FEATURE_GATE,
}

/** Тариф подписки (H1). */
enum class SubscriptionPlan {
    MONTHLY,
    YEARLY,
}

/** Исход покупки или восстановления подписки (H1). */
enum class PurchaseOutcome {
    SUCCESS,
    RESTORED,
    CANCELLED,
    FAILED,
}

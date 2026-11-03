package ru.stohastika.sklyanka.domain.analytics

import java.time.Instant

/**
 * Событие аналитики из каталога [AnalyticsEventName].
 *
 * Реализации — по одной на событие ПМ-26. Конструктор каждой принимает только значения
 * закрытых перечислений из [AnalyticsCatalog], поэтому свободный текст в событие не попадает
 * даже случайно. Имя события и свойства задаёт только сам класс: точка вызова не может
 * их подменить (АН-3).
 */
sealed interface AnalyticsEvent {

    /** Имя события каталога. */
    val name: AnalyticsEventName

    /** Свойства события: ключ из [AnalyticsProperty], значение — проводное имя закрытого списка. */
    val properties: Map<AnalyticsProperty, String>

    /** Контур очереди: события о согласии пишутся до согласия и отправляются только с ним. */
    val scope: AnalyticsScope
        get() = AnalyticsScope.PRODUCT

    /** Снимок для очереди: время появления записи — только порядок и срок хранения (TDD §8). */
    fun toRecord(occurredAt: Instant): AnalyticsRecord = AnalyticsRecord(
        name = name,
        properties = properties,
        occurredAt = occurredAt,
        scope = scope,
    )
}

/** Запись очереди аналитики. Время в ней — только порядок и TTL, поставщику оно не уходит. */
data class AnalyticsRecord(
    val name: AnalyticsEventName,
    val properties: Map<AnalyticsProperty, String>,
    val occurredAt: Instant,
    val scope: AnalyticsScope,
)

/** Запись очереди с её идентификатором: по нему отправленное удаляется. */
data class QueuedAnalyticsRecord(val id: Long, val record: AnalyticsRecord)

/** Показ экрана согласия: первый запуск, изменение политики или повторный вход из настроек. */
data class ConsentShown(val source: ConsentShownSource) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.CONSENT_SHOWN
    override val scope: AnalyticsScope = AnalyticsScope.CONSENT
    override val properties: Map<AnalyticsProperty, String> =
        mapOf(AnalyticsProperty.SOURCE to source.wire)
}

/** Ответ человека на вопрос о согласии. Значение `NOT_NOW` никуда не отправляется. */
data class ConsentChoice(val choice: ConsentAnswer) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.CONSENT_CHOICE
    override val scope: AnalyticsScope = AnalyticsScope.CONSENT
    override val properties: Map<AnalyticsProperty, String> =
        mapOf(AnalyticsProperty.CHOICE to choice.wire)
}

/** Включение аналитики позже, из настроек. Выключение события не имеет. */
data object AnalyticsEnabled : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.ANALYTICS_ENABLED
    override val scope: AnalyticsScope = AnalyticsScope.CONSENT
    override val properties: Map<AnalyticsProperty, String> =
        mapOf(AnalyticsProperty.SOURCE to AnalyticsEnabledSource.SETTINGS.wire)
}

/** Вход в онбординг разрешений. */
data class OnboardingStarted(val entry: OnboardingEntry) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.ONBOARDING_STARTED
    override val properties: Map<AnalyticsProperty, String> =
        mapOf(AnalyticsProperty.ENTRY to entry.wire)
}

/** Результат шага цепочки разрешений. */
data class OnboardingStepResult(val step: OnboardingStep, val result: StepResult) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.ONBOARDING_STEP_RESULT
    override val properties: Map<AnalyticsProperty, String> = mapOf(
        AnalyticsProperty.STEP to step.wire,
        AnalyticsProperty.RESULT to result.wire,
    )
}

/** Исход онбординга: дошли или пропустили. */
data class OnboardingFinished(val entry: OnboardingEntry, val outcome: OnboardingOutcome) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.ONBOARDING_FINISHED
    override val properties: Map<AnalyticsProperty, String> = mapOf(
        AnalyticsProperty.ENTRY to entry.wire,
        AnalyticsProperty.OUTCOME to outcome.wire,
    )
}

/** Показ экрана: несёт ровно одно свойство `screen`. */
data class ScreenView(val screen: AnalyticsScreen) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.SCREEN_VIEW
    override val properties: Map<AnalyticsProperty, String> =
        mapOf(AnalyticsProperty.SCREEN to screen.wire)
}

/**
 * Действие с записью лекарства.
 *
 * Режим есть только у добавления: у архива, удаления и отмены его нет. Требование
 * проверяется при создании события, а не в момент показа: событие без режима у `added`
 * было бы неразбираемо в воронке «по расписанию или по потребности».
 */
data class MedicationAction(val action: MedicationActionKind, val mode: MedicationMode? = null) : AnalyticsEvent {

    init {
        require((action == MedicationActionKind.ADDED) == (mode != null)) {
            "medication mode is set exactly for an added medication"
        }
    }

    override val name: AnalyticsEventName = AnalyticsEventName.MEDICATION_ACTION
    override val properties: Map<AnalyticsProperty, String> = buildMap {
        put(AnalyticsProperty.ACTION, action.wire)
        mode?.let { put(AnalyticsProperty.MODE, it.wire) }
    }
}

/** Отметка приёма, пропуск или отмена с источником действия. */
data class IntakeAction(val action: IntakeActionKind, val source: ActionSource) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.INTAKE_ACTION
    override val properties: Map<AnalyticsProperty, String> = mapOf(
        AnalyticsProperty.ACTION to action.wire,
        AnalyticsProperty.SOURCE to source.wire,
    )
}

/** «Отложить» и его источник. */
data class ReminderSnoozed(val source: ActionSource) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.REMINDER_SNOOZED
    override val properties: Map<AnalyticsProperty, String> =
        mapOf(AnalyticsProperty.SOURCE to source.wire)
}

/** Исход выгрузки отчёта врачу. */
data class ReportExported(val result: OperationResult) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.REPORT_EXPORTED
    override val properties: Map<AnalyticsProperty, String> =
        mapOf(AnalyticsProperty.RESULT to result.wire)
}

/** Направление и исход переноса данных. */
data class TransferResult(val direction: TransferDirection, val result: OperationResult) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.TRANSFER_RESULT
    override val properties: Map<AnalyticsProperty, String> = mapOf(
        AnalyticsProperty.DIRECTION to direction.wire,
        AnalyticsProperty.RESULT to result.wire,
    )
}

/** Исход самопроверки доставки. */
data class DeliveryCheckResult(val result: DeliveryCheckOutcome) : AnalyticsEvent {

    override val name: AnalyticsEventName = AnalyticsEventName.DELIVERY_CHECK_RESULT
    override val properties: Map<AnalyticsProperty, String> =
        mapOf(AnalyticsProperty.RESULT to result.wire)
}

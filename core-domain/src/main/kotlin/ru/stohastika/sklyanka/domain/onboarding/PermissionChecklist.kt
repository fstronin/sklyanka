package ru.stohastika.sklyanka.domain.onboarding

/**
 * Состояние цепочки шагов надёжности (НАП-19, ПЛ-18, ОВ-106).
 *
 * Поля типизированы поимённо, а не собраны в `Map<PermissionStep, StepState>`, ровно для
 * того, чтобы вендорский шаг нельзя было объявить выданным: у [ManualStepState] варианта
 * «выдано» нет, и подставить на его место [PermissionGrant.Granted] не получится
 * (`TDD.md §5.6`). Карта, собранная из шагов, отдаётся наружу [steps] — но собирает её
 * этот класс, а не экран.
 *
 * Состав шагов зависит от [vendor]: у каждого профиля свой набор вендорских шагов
 * ([VendorSteps.of]), а на незнакомой прошивке вендорских настроек нет и шагов остаётся
 * два. Число шагов не хранится константой: экран берёт его из [steps].
 */
data class PermissionChecklist(
    val vendor: VendorProfile,
    val notifications: NotificationAccessState,
    val exactAlarms: PermissionGrant,
    val appStartup: ManualStepState,
    val batterySaving: ManualStepState,
    val recentsPin: ManualStepState,
) {

    /** Шаги в фиксированном порядке ОВ-106: экран не решает, в каком порядке их показать. */
    val steps: List<Pair<PermissionStep, StepState>> = buildList {
        add(PermissionStep.NOTIFICATIONS to notifications)
        add(PermissionStep.EXACT_ALARMS to exactAlarms)
        VendorSteps.of(vendor).forEach { add(it to state(it)) }
    }

    /** Состояние конкретного шага. */
    fun state(step: PermissionStep): StepState = when (step) {
        PermissionStep.NOTIFICATIONS -> notifications
        PermissionStep.EXACT_ALARMS -> exactAlarms
        PermissionStep.APP_STARTUP -> appStartup
        PermissionStep.BATTERY_SAVING -> batterySaving
        PermissionStep.RECENTS_PIN -> recentsPin
    }

    /**
     * Цепочка пройдена целиком: «Дальше» ведёт на следующий шаг онбординга (критерий приёмки 7).
     *
     * Вендорские шаги входят в счёт: цепочка, пройденная на двух из пяти, — это ровно та
     * ложная зелень, которой задача не допускает (НАП-20). На не-Huawei в счёт входят
     * только два общих шага: вендорских настроек там нет вовсе.
     */
    val allResolved: Boolean = steps.all { (_, state) -> state.isResolved }

    /** Сколько шагов не пройдено — сводное состояние строки настроек. */
    val unresolvedCount: Int = steps.count { (_, state) -> !state.isResolved }

    /** Непройденные шаги в том же фиксированном порядке: что предложить сделать первым. */
    val unresolved: List<PermissionStep> = steps.filterNot { (_, state) -> state.isResolved }.map { it.first }

    /** Первый незакрытый шаг — фокус при входе из баннера (ОВ-107). */
    val firstUnresolved: PermissionStep? get() = unresolved.firstOrNull()

    /**
     * Напоминания ненадёжны в смысле НАП-18 — предупреждение на «Сегодня» и в диагностике.
     *
     * Совпадает с [allResolved] намеренно: пока не пройден любой из шагов, приложение
     * не вправе утверждать, что напоминания настроены.
     */
    val remindersUnreliable: Boolean = !allResolved
}

package ru.heuri.sklyanka.domain.onboarding

/**
 * Состояние цепочки из трёх шагов (НАП-19, ПЛ-18).
 *
 * Поля типизированы поимённо, а не собраны в `Map<PermissionStep, StepState>`, ровно для
 * того, чтобы [autostart] нельзя было объявить выданным: тип [AutostartState] варианта
 * «выдано» не имеет, и подставить на его место [PermissionGrant.Granted] не получится
 * (`TDD.md §5.1`). Карта, собранная из шагов, отдаётся наружу [steps] — но собирает её
 * этот класс, а не экран.
 */
data class PermissionChecklist(
    val notifications: PermissionGrant,
    val exactAlarms: PermissionGrant,
    val autostart: AutostartState,
) {

    /** Шаги в фиксированном порядке НАП-19: экран не решает, в каком порядке их показать. */
    val steps: List<Pair<PermissionStep, StepState>> = listOf(
        PermissionStep.NOTIFICATIONS to notifications,
        PermissionStep.EXACT_ALARMS to exactAlarms,
        PermissionStep.AUTOSTART to autostart,
    )

    /** Состояние конкретного шага. */
    fun state(step: PermissionStep): StepState = when (step) {
        PermissionStep.NOTIFICATIONS -> notifications
        PermissionStep.EXACT_ALARMS -> exactAlarms
        PermissionStep.AUTOSTART -> autostart
    }

    /**
     * Цепочка пройдена целиком: «Дальше» ведёт на экран подключения часов (критерий приёмки 20).
     *
     * Автозапуск входит в счёт: цепочка из трёх шагов, пройденная на двух, — это ровно та
     * ложная зелень, которой задача не допускает (НАП-20).
     */
    val allResolved: Boolean = steps.all { (_, state) -> state.isResolved }

    /** Сколько шагов не пройдено — сводное состояние строки настроек (критерий приёмки 14). */
    val unresolvedCount: Int = steps.count { (_, state) -> !state.isResolved }

    /** Непройденные шаги в том же фиксированном порядке: что предложить сделать первым. */
    val unresolved: List<PermissionStep> = steps.filterNot { (_, state) -> state.isResolved }.map { it.first }

    /**
     * Напоминания ненадёжны в смысле НАП-18 — предупреждение на «Сегодня» и в диагностике.
     *
     * Совпадает с [allResolved] намеренно: пока не пройден любой из трёх шагов, приложение
     * не вправе утверждать, что напоминания настроены (критерий приёмки 17).
     */
    val remindersUnreliable: Boolean = !allResolved

    companion object {
        /** Сколько шагов в цепочке — три по НАП-19, и число берётся из перечисления, а не из головы. */
        val STEP_COUNT: Int = PermissionStep.entries.size
    }
}

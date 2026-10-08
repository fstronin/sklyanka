package ru.stohastika.sklyanka.feature.onboarding

import ru.stohastika.sklyanka.domain.onboarding.OnboardingFlow
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepState
import java.time.Instant

/**
 * Что показывает экран «Настроить надёжность».
 *
 * Состояние цепочки не хранится в поле «прошёл онбординг»: [checklist] пересобирается
 * из системы при каждом показе и при каждом возврате на передний план (НФТ-52).
 * До первого чтения оно `null` — это не «всё не выдано», а «ещё не спросили систему»,
 * и экран в этот момент не имеет права утверждать ни то, ни другое.
 */
data class OnboardingUiState(
    val source: OnboardingSource,
    val checklist: PermissionChecklist? = null,
    /**
     * Шаг, к которому прокручивается список при входе из баннера (ОВ-107).
     *
     * Вычисляется после первого чтения состояния, а не принимается от вызывающего:
     * передай тот устаревший шаг — фокус однажды указал бы на уже закрытый (НФТ-52).
     */
    val focusStep: PermissionStep? = null,
    /** Перерыв, из-за которого человек пришёл с баннера: плашка предупреждения (ОВ-107). */
    val outageSince: Instant? = null,
    /**
     * Шаги, для которых показан запасной путь.
     *
     * Набор, а не один флаг: запасной путь возможен у «Запуска приложений»
     * и «Энергосбережения», и стираться должен по шагам (TDD §5.11).
     */
    val instructions: Set<PermissionStep> = emptySet(),
    /** Шаги, разрешение на которые было и пропало между двумя чтениями. */
    val revoked: List<PermissionStep> = emptyList(),
) {

    /** Индикатор шагов принадлежит онбордингу: при входе из настроек его нет (критерий 13). */
    val showStepIndicator: Boolean get() = source.showsStepIndicator

    val totalSteps: Int get() = OnboardingFlow.TOTAL_STEPS

    val currentStep: Int get() = OnboardingFlow.PERMISSIONS_STEP

    /** Шаги в фиксированном порядке ОВ-106; до первого чтения показывать нечего. */
    val steps: List<Pair<PermissionStep, StepState>> get() = checklist?.steps.orEmpty()

    /**
     * Цепочка закрыта — над списком встаёт плашка «Всё настроено» (критерий приёмки 7).
     *
     * Вычисляемое свойство, а не сохраняемое поле: оно обязано меняться от чтения системы,
     * а не от памяти экрана.
     */
    val allConfigured: Boolean get() = checklist?.allResolved == true
}

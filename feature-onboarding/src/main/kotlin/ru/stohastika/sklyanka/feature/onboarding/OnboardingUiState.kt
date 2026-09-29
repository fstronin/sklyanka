package ru.stohastika.sklyanka.feature.onboarding

import ru.stohastika.sklyanka.domain.onboarding.AutostartOpenResult
import ru.stohastika.sklyanka.domain.onboarding.OnboardingFlow
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepState

/**
 * Что показывает экран разрешений.
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
     * Показывать ли текстовую инструкцию вместо обещания открыть системный экран.
     *
     * Включается только исходом [AutostartOpenResult.FALLBACK_TO_INSTRUCTIONS]: на прошивке,
     * где экран автозапуска не открывается, названия пунктов меню — единственное, что
     * приложение может человеку дать (критерий приёмки 6).
     */
    val showAutostartInstructions: Boolean = false,
    /** Шаги, разрешение на которые было и пропало между двумя чтениями (критерий приёмки 17). */
    val revoked: List<PermissionStep> = emptyList(),
) {

    /** Индикатор шагов принадлежит онбордингу: при входе из настроек его нет (критерий 15). */
    val showStepIndicator: Boolean get() = source.showsStepIndicator

    val totalSteps: Int get() = OnboardingFlow.TOTAL_STEPS

    val currentStep: Int get() = OnboardingFlow.PERMISSIONS_STEP

    /** Шаги в фиксированном порядке НАП-19; до первого чтения показывать нечего. */
    val steps: List<Pair<PermissionStep, StepState>> get() = checklist?.steps.orEmpty()

    /**
     * Ведёт ли «Дальше» дальше по онбордингу.
     *
     * Кнопка есть всегда — она нарисована в макете и не исчезает от непройденной цепочки,
     * иначе экран превратился бы в тупик (НАП-18). Но пока цепочка не пройдена, «Дальше»
     * равносильно пропуску, и приложение об этом не врёт.
     */
    val chainComplete: Boolean get() = checklist?.allResolved == true
}

/** Исход перехода с экрана — решает его хозяин навигации, а не сам экран. */
enum class OnboardingExit {
    /** Цепочка пройдена: следующий шаг онбординга — подключение часов (SKL-17). */
    WATCH_SETUP,

    /** «Пропустить, настрою позже» или «Дальше» при непройденной цепочке: «Сегодня». */
    TODAY,
}

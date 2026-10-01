package ru.stohastika.sklyanka.platform.onboarding

import android.util.Log
import ru.stohastika.sklyanka.domain.onboarding.OnboardingOutcome
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * След онбординга (`TDD.md §11`).
 *
 * Локальный журнал и ничего кроме: сети у приложения нет (ПЛ-3), аналитики и крашлитики
 * в релизной сборке нет (НФТ-16), и ни одно событие отсюда не уходит за пределы устройства —
 * в том числе «человек отказал в разрешении» (НАП-39).
 *
 * В записях только перечисления, числа и моменты. Названий лекарств, дозировок и имён
 * профилей здесь нет ни в одном поле — в этой задаче доменных данных нет вовсе (НФТ-19).
 */
@Singleton
class OnboardingLogcat @Inject constructor() : OnboardingLog {

    override fun screenOpened(source: OnboardingSource, checklist: PermissionChecklist) {
        Log.i(TAG, "permissions screen opened (source=$source, ${describe(checklist)})")
    }

    override fun permissionRequested(step: PermissionStep) {
        Log.i(TAG, "system permission request shown (step=$step)")
    }

    override fun permissionResult(step: PermissionStep, granted: Boolean) {
        Log.i(TAG, "system permission result (step=$step, granted=$granted)")
    }

    override fun vendorStepOpened(step: PermissionStep, result: StepOpenResult) {
        Log.i(TAG, "vendor step opened (step=$step, result=$result)")
    }

    override fun manualStepConfirmed(step: PermissionStep, confirmed: Boolean) {
        Log.i(TAG, "vendor step confirmed by the person (step=$step, confirmed=$confirmed)")
    }

    override fun reliabilityRouteTaken(source: OnboardingSource, firstUnresolved: PermissionStep?) {
        Log.i(TAG, "reliability route taken (source=$source, firstUnresolved=$firstUnresolved)")
    }

    override fun onboardingLeft(outcome: OnboardingOutcome, checklist: PermissionChecklist) {
        Log.i(TAG, "permissions screen left (outcome=$outcome, ${describe(checklist)})")
    }

    override fun revocationDetected(steps: List<PermissionStep>) {
        Log.w(TAG, "a previously granted step is gone (steps=$steps)")
    }

    /** Состояние каждого шага на момент записи: без него «пропустил» не отличить от «прошёл». */
    private fun describe(checklist: PermissionChecklist): String = checklist.steps
        .joinToString(prefix = "steps=[", postfix = "]") { (step, state) -> "$step:${state.isResolved}" }

    private companion object {
        const val TAG = "SklyankaOnboarding"
    }
}

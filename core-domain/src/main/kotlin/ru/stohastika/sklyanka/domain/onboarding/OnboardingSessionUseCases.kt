package ru.stohastika.sklyanka.domain.onboarding

import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore

/**
 * Уход с экрана разрешений (НАП-21, `TDD.md §9.3`).
 *
 * В обоих исходах сохраняется одно и то же: человек видел экран. Разницы «прошёл»
 * и «пропустил» в хранимом состоянии нет намеренно — она вычисляется из текущего состояния
 * разрешений, а не из памяти о намерении. Запомненное «прошёл» пережило бы отзыв разрешения
 * и стало бы утверждением, что напоминания настроены, тогда как они уже нет (НФТ-52).
 */
class LeaveOnboardingUseCase(private val store: OnboardingStateStore, private val log: OnboardingLog) {

    suspend operator fun invoke(outcome: OnboardingOutcome, checklist: PermissionChecklist) {
        store.markSeen()
        log.onboardingLeft(outcome, checklist)
    }
}

/**
 * Ручное подтверждение вендорского шага (НАП-20, ОВ-53).
 *
 * Единственное, что человек может сообщить приложению о настройках EMUI, и единственное,
 * что о них вообще сохраняется. Подтверждение снимаемо: человек, нажавший его по ошибке,
 * иначе остался бы с зелёным шагом, который сам себе и выдал, — а весь смысл вендорских
 * шагов в том, чтобы зелень означала что-то настоящее.
 *
 * Шаг-разрешение подтверждению не подлежит: у уведомлений и точных будильников состояние
 * читает система, и запись «человек подтвердил» была бы ложью о факте, которого не было.
 * Домен защищает от неё исключением, а не молчаливым no-op.
 */
class ConfirmReliabilityStepUseCase(private val store: OnboardingStateStore, private val log: OnboardingLog) {

    /** @throws IllegalArgumentException если шаг читается системой — подтверждать нечего. */
    suspend operator fun invoke(step: PermissionStep, confirmed: Boolean) {
        require(!step.verifiedBySystem) { "step $step is read from the system and cannot be confirmed" }
        when (step) {
            PermissionStep.APP_STARTUP -> store.setAppStartupConfirmed(confirmed)
            PermissionStep.BATTERY_SAVING -> store.setBatterySavingConfirmed(confirmed)
            PermissionStep.RECENTS_PIN -> store.setRecentsPinConfirmed(confirmed)
            // Недостижимо после require, но перечисление закрыто не зря: новый вендорский
            // шаг компилятор заставит добавить сюда, а не молча подтвердит не то.
            PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> Unit
        }
        log.manualStepConfirmed(step, confirmed)
    }
}

/**
 * Показывать ли онбординг при запуске.
 *
 * Правило ухода с заставки живёт в `SplashRoute` (SKL-13) и опирается на состояние
 * разрешений, прочитанное из системы. Здесь — единственная часть решения, которой в системе
 * нет: видел ли человек экран разрешений хоть раз (НАП-21).
 */
class ShouldShowOnboardingUseCase(private val store: OnboardingStateStore) {

    suspend operator fun invoke(): Boolean = !store.seen()
}

/**
 * Шаги, разрешение на которые было и пропало (`TDD.md §11.1`).
 *
 * Чистая функция от двух снимков цепочки: отзыв разрешения обнаруживается сравнением,
 * а не сохранённым флагом «было выдано». Сравниваются два чтения системы, между которыми
 * человек уходил в системные настройки.
 */
object RevokedPermissions {

    fun between(before: PermissionChecklist, after: PermissionChecklist): List<PermissionStep> =
        PermissionStep.entries.filter { step ->
            before.state(step).isResolved && !after.state(step).isResolved
        }
}

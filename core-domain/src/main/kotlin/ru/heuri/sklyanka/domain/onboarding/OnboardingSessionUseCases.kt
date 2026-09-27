package ru.heuri.sklyanka.domain.onboarding

import ru.heuri.sklyanka.domain.onboarding.port.OnboardingLog
import ru.heuri.sklyanka.domain.onboarding.port.OnboardingStateStore

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
 * Ручное подтверждение автозапуска (НАП-20, ОВ-53).
 *
 * Единственное, что человек может сообщить приложению об этом шаге, и единственное, что
 * о нём вообще сохраняется. Подтверждение снимаемо: человек, нажавший его по ошибке, иначе
 * остался бы с зелёным шагом, который сам себе и выдал, — а весь смысл третьего шага в том,
 * чтобы зелень означала что-то настоящее.
 */
class ConfirmAutostartUseCase(private val store: OnboardingStateStore, private val log: OnboardingLog) {

    suspend operator fun invoke(confirmed: Boolean) {
        store.setAutostartConfirmed(confirmed)
        log.autostartConfirmedByUser(confirmed)
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

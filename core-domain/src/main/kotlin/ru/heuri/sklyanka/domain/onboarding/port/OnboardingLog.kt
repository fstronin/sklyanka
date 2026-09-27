package ru.heuri.sklyanka.domain.onboarding.port

import ru.heuri.sklyanka.domain.onboarding.AutostartOpenResult
import ru.heuri.sklyanka.domain.onboarding.OnboardingOutcome
import ru.heuri.sklyanka.domain.onboarding.OnboardingSource
import ru.heuri.sklyanka.domain.onboarding.PermissionChecklist
import ru.heuri.sklyanka.domain.onboarding.PermissionStep
import java.time.Instant

/**
 * След онбординга (`TDD.md §11`).
 *
 * Онбординг — часть цепочки надёжности, и без его записей неразбираема жалоба «напоминания
 * не приходили с самого начала»: неизвестно, показывали ли человеку экран, что он ответил
 * системному запросу и открылся ли у него экран автозапуска.
 *
 * Отдельный журнал, а не записи в журнале срабатываний (`ReminderJournal`): тот отвечает
 * на вопрос «почему не пришло это напоминание» и вытесняется по кольцу (НФТ-51), а здесь
 * события установки, которых за всю жизнь приложения десяток.
 *
 * Доменных данных в записях нет вовсе: шаги, моменты и результаты. Ни названий лекарств,
 * ни дозировок, ни имён профилей (НФТ-19). Наружу не уходит ничего, включая «человек
 * отказал в разрешении» — именно такие события первыми просятся в продуктовую аналитику,
 * которой у приложения нет (НАП-39, ПЛ-3, НФТ-16).
 */
interface OnboardingLog {

    /** Экран разрешений открыт: момент, источник и состояние каждого шага на этот момент. */
    fun screenOpened(source: OnboardingSource, checklist: PermissionChecklist)

    /** Системный запрос разрешения показан. */
    fun permissionRequested(step: PermissionStep)

    /** Результат системного запроса: выдано или отказано. */
    fun permissionResult(step: PermissionStep, granted: Boolean)

    /** Открытие экрана автозапуска и его исход — открылся или сработал запасной путь. */
    fun autostartSettingsOpened(result: AutostartOpenResult)

    /** Ручное подтверждение автозапуска человеком — или снятие подтверждения. */
    fun autostartConfirmedByUser(confirmed: Boolean)

    /** Пробное напоминание поставлено: момент постановки и источник. */
    fun testReminderStarted(source: OnboardingSource, at: Instant)

    /** Уход с экрана: «Дальше» или «Пропустить», и состояние шагов на этот момент. */
    fun onboardingLeft(outcome: OnboardingOutcome, checklist: PermissionChecklist)

    /**
     * Обнаружен отзыв ранее выданного разрешения.
     *
     * Событие, за которым приходят чаще всего: напоминания работали, потом перестали,
     * и человек уверен, что в приложении ничего не менял. Он прав — менялось в системе.
     */
    fun revocationDetected(steps: List<PermissionStep>)
}

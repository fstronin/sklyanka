package ru.stohastika.sklyanka.domain.onboarding

import ru.stohastika.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.onboarding.port.PermissionRequestSupport
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration

/**
 * Сборка состояния цепочки (НАП-19, НФТ-52, `TDD.md §9.1`).
 *
 * Вызывается при каждом показе экрана и при каждом возврате приложения на передний план:
 * человек уходит выдавать разрешение в системные настройки и возвращается — экран обязан
 * показать новое состояние без перезахода (критерий приёмки 4 и 16).
 *
 * Состояние автозапуска не читается ни здесь, ни где-либо ещё: оно не выставлено в публичный
 * API (НАП-20). Собирается только подтверждение человека и список косвенных признаков.
 */
@Suppress("LongParameterList")
class ReadPermissionChecklistUseCase(
    private val permissions: PermissionStateReader,
    private val store: OnboardingStateStore,
    private val hints: AutostartHintReader,
    private val requests: PermissionRequestSupport,
    private val journal: ReminderJournal,
    private val time: TimeSource,
) {

    suspend operator fun invoke(): PermissionChecklist = PermissionChecklist(
        notifications = grant(PermissionStep.NOTIFICATIONS, ReminderPermission.NOTIFICATIONS),
        // Право на точные будильники диалогом не запрашивается ни на одной версии Android:
        // его выдают на экране особого доступа (ПЛ-14), и приложение не может «спросить» само.
        exactAlarms = grant(PermissionStep.EXACT_ALARMS, ReminderPermission.EXACT_ALARMS),
        autostart = AutostartState(
            confirmedByUser = store.autostartConfirmed(),
            hints = collectHints(),
        ),
    )

    /**
     * Состояние разрешения Android.
     *
     * [PermissionState.UNKNOWN] здесь означало бы, что приложение не знает состояния
     * разрешения Android, — такого не бывает: `UNKNOWN` заведён для автозапуска, который
     * через этот путь не проходит. На всякий случай он трактуется как «не выдано»:
     * ошибка в сторону «попросим лишний раз» дешевле ошибки в сторону молчащих напоминаний.
     */
    private fun grant(step: PermissionStep, permission: ReminderPermission): PermissionGrant =
        if (permissions.state(permission) == PermissionState.GRANTED) {
            PermissionGrant.Granted(readAt = time.now())
        } else {
            PermissionGrant.Denied(canAsk = requests.canRequestFromCode(step))
        }

    private suspend fun collectHints(): Set<AutostartHint> = buildSet {
        if (hints.batteryOptimizationIgnored()) add(AutostartHint.BATTERY_OPTIMIZATION_IGNORED)
        if (testReminderDeliveredRecently()) add(AutostartHint.TEST_REMINDER_DELIVERED)
    }

    /**
     * Дошло ли пробное напоминание — и дошло ли недавно (`TDD.md §10`).
     *
     * Срок давности обязателен: пробное напоминание, доставленное неделю назад, ничего
     * не говорит о состоянии устройства сегодня — прошивку могли обновить, а приложение
     * выкинуть из списка ручного управления запуском.
     */
    private suspend fun testReminderDeliveredRecently(): Boolean {
        // Недоставленное пробное напоминание признаком не является: оно говорит ровно то же,
        // что его отсутствие, — цепочка до человека не дошла.
        val last = journal.last(ReminderEvent.TEST)?.takeIf { it.delivered == true } ?: return false
        val age = Duration.between(last.occurredAt, time.now())
        return !age.isNegative && age <= TEST_REMINDER_HINT_MAX_AGE
    }

    companion object {
        /**
         * Давность, после которой дошедшее пробное напоминание перестаёт быть признаком.
         *
         * Одна именованная константа на весь расчёт: срок давности, разложенный по двум
         * местам, разойдётся, и признак начнёт то появляться, то исчезать без причины.
         */
        val TEST_REMINDER_HINT_MAX_AGE: Duration = Duration.ofDays(1)
    }
}

/**
 * Прохождение шага: что приложение делает по нажатию на карточке (`TDD.md §9.2`).
 *
 * Решение живёт в домене, а не в экране: «уведомления запрашиваются диалогом, точные
 * будильники — экраном особого доступа, автозапуск — чужим экраном производителя» —
 * это требование ПЛ-13…ПЛ-18, а не деталь вёрстки.
 */
enum class StepAction {
    /** Системный диалог запроса разрешения (ПЛ-13). */
    REQUEST_PERMISSION,

    /** Экран особого доступа или настроек приложения (ПЛ-14). */
    OPEN_SYSTEM_SETTINGS,

    /** Экран автозапуска производителя с запасным путём (ПЛ-18). */
    OPEN_AUTOSTART_SETTINGS,
}

/** Какое действие предлагает шаг в своём текущем состоянии. */
fun actionFor(step: PermissionStep, state: StepState): StepAction? = when (step) {
    PermissionStep.AUTOSTART -> StepAction.OPEN_AUTOSTART_SETTINGS
    PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> when (state) {
        is PermissionGrant.Granted -> null
        is PermissionGrant.Denied ->
            if (state.canAsk) StepAction.REQUEST_PERMISSION else StepAction.OPEN_SYSTEM_SETTINGS
        // Автозапуск сюда не попадает: у него свой шаг и своё действие.
        is AutostartState -> StepAction.OPEN_AUTOSTART_SETTINGS
    }
}

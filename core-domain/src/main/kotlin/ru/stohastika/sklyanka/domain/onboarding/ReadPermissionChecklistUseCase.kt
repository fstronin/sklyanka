package ru.stohastika.sklyanka.domain.onboarding

import ru.stohastika.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.onboarding.port.PermissionRequestSupport
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.time.TimeSource

/**
 * Сборка состояния цепочки (НАП-19, НФТ-52, `TDD.md §9.1`).
 *
 * Вызывается при каждом показе экрана и при каждом возврате приложения на передний план:
 * человек уходит выдавать разрешение в системные настройки и возвращается — экран обязан
 * показать новое состояние без перезахода (критерии приёмки 12).
 *
 * Состояние вендорских настроек не читается ни здесь, ни где-либо ещё: оно не выставлено
 * в публичный API (НАП-20). Собираются только подтверждения человека и косвенный признак
 * энергосбережения; вендор приходит с платформенного порта, потому что домен не знает
 * `Build.MANUFACTURER`.
 */
class ReadPermissionChecklistUseCase(
    private val permissions: PermissionStateReader,
    private val store: OnboardingStateStore,
    private val hints: AutostartHintReader,
    private val requests: PermissionRequestSupport,
    private val time: TimeSource,
    private val vendor: VendorProfileReader,
) {

    suspend operator fun invoke(): PermissionChecklist = PermissionChecklist(
        vendor = vendor.profile(),
        notifications = NotificationAccessState(
            permission = grant(PermissionStep.NOTIFICATIONS, ReminderPermission.NOTIFICATIONS),
            channel = gate(ReminderPermission.NOTIFICATION_CHANNEL),
            fullScreenIntent = gate(ReminderPermission.FULL_SCREEN_INTENT),
        ),
        // Право на точные будильники диалогом не запрашивается ни на одной версии Android:
        // его выдают на экране особого доступа (ПЛ-14), и приложение не может «спросить» само.
        exactAlarms = grant(PermissionStep.EXACT_ALARMS, ReminderPermission.EXACT_ALARMS),
        appStartup = ManualStepState(confirmedByUser = store.appStartupConfirmed()),
        // Косвенный признак относится к предмету этого шага — оптимизации батареи (НАП-20).
        batterySaving = ManualStepState(
            confirmedByUser = store.batterySavingConfirmed(),
            hints = collectHints(),
        ),
        recentsPin = ManualStepState(confirmedByUser = store.recentsPinConfirmed()),
    )

    /**
     * Состояние разрешения Android.
     *
     * [PermissionState.UNKNOWN] здесь означало бы, что приложение не знает состояния
     * разрешения Android, — такого не бывает: `UNKNOWN` заведён для вендорских настроек,
     * которые через этот путь не проходят. На всякий случай он трактуется как «не выдано»:
     * ошибка в сторону «попросим лишний раз» дешевле ошибки в сторону молчащих напоминаний.
     */
    private fun grant(step: PermissionStep, permission: ReminderPermission): PermissionGrant =
        if (permissions.state(permission) == PermissionState.GRANTED) {
            PermissionGrant.Granted(readAt = time.now())
        } else {
            PermissionGrant.Denied(canAsk = requests.canRequestFromCode(step))
        }

    /** Настройка без права запроса: система отвечает «включено» или «выключено», `UNKNOWN` — «нет». */
    private fun gate(permission: ReminderPermission): PermissionGate =
        if (permissions.state(permission) == PermissionState.GRANTED) PermissionGate.GRANTED else PermissionGate.DENIED

    private fun collectHints(): Set<ManualStepHint> = buildSet {
        if (hints.batteryOptimizationIgnored()) add(ManualStepHint.BATTERY_OPTIMIZATION_IGNORED)
    }
}

/**
 * Прохождение шага: что приложение делает по нажатию на карточке (`TDD.md §9.2`).
 *
 * Решение живёт в домене, а не в экране: «уведомления запрашиваются диалогом, точные
 * будильники — экраном особого доступа, вендорские настройки — чужим экраном производителя» —
 * это требование ПЛ-13…ПЛ-18, а не деталь вёрстки.
 */
enum class StepAction {
    /** Системный диалог запроса разрешения (ПЛ-13). */
    REQUEST_PERMISSION,

    /** Экран особого доступа, канала или настроек приложения (ПЛ-14, ОВ-107). */
    OPEN_SYSTEM_SETTINGS,

    /** Вендорский экран производителя с запасной инструкцией (ПЛ-18). */
    OPEN_VENDOR_STEP,
}

/**
 * Какое действие предлагает шаг в своём текущем состоянии.
 *
 * У закрепления в недавних системного экрана нет вовсе: только инструкция и переключатель,
 * поэтому действия у него нет ни в каком состоянии.
 */
fun actionFor(step: PermissionStep, state: StepState): StepAction? = when (step) {
    PermissionStep.APP_STARTUP, PermissionStep.BATTERY_SAVING -> StepAction.OPEN_VENDOR_STEP
    PermissionStep.RECENTS_PIN -> null
    PermissionStep.EXACT_ALARMS -> exactAlarmAction(state)
    PermissionStep.NOTIFICATIONS -> notificationAction(state)
}

/**
 * Действие шага «Точные будильники».
 *
 * Диалогом это право не запрашивается ни на одной версии Android (ПЛ-14): система выдаёт
 * его только на своём экране особого доступа. Поэтому «допустимо ли спросить кодом» здесь
 * не читается вовсе — шаг ведёт на системный экран, и платформенное значение не превратит
 * его в запрос, которого система не покажет.
 */
private fun exactAlarmAction(state: StepState): StepAction? = when (state) {
    is PermissionGrant.Granted -> null
    is PermissionGrant.Denied -> StepAction.OPEN_SYSTEM_SETTINGS
    is NotificationAccessState, is ManualStepState -> null
}

/**
 * Действие шага «Уведомления».
 *
 * Пока не выдано разрешение и система ещё показывает диалог — спрашиваем диалогом;
 * канал и полноэкранное право исправляются только на системном экране уведомлений.
 */
private fun notificationAction(state: StepState): StepAction? = when (state) {
    is NotificationAccessState -> when {
        state.isResolved -> null
        state.permission is PermissionGrant.Denied && state.permission.canAsk -> StepAction.REQUEST_PERMISSION
        else -> StepAction.OPEN_SYSTEM_SETTINGS
    }
    // Состояние другого типа у шага «Уведомления» — ошибка сборки цепочки, и действия
    // у него нет: подставлять чужой путь к настройкам нельзя.
    else -> null
}

package ru.stohastika.sklyanka.feature.onboarding

import ru.stohastika.sklyanka.domain.onboarding.ManualStepHint
import ru.stohastika.sklyanka.domain.onboarding.ManualStepState
import ru.stohastika.sklyanka.domain.onboarding.NotificationAccessState
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionGate
import ru.stohastika.sklyanka.domain.onboarding.PermissionGrant
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import java.time.Instant

/**
 * Заготовки состояний экрана «Настроить надёжность».
 *
 * Общие для проверок состава и для снимков: два набора заготовок разошлись бы, и снимок
 * перестал бы изображать то, что проверяет тест.
 */
internal object PermissionsScreenFixtures {

    val READ_AT: Instant = Instant.parse("2026-09-25T07:00:00Z")

    val OUTAGE_SINCE: Instant = Instant.parse("2026-10-01T05:30:00Z")

    /** Состояние макета: уведомления выданы, точные будильники нет, вендорские шаги не подтверждены. */
    fun asDrawn(): OnboardingUiState = state(
        notifications = grantedNotifications(),
        exactAlarms = PermissionGrant.Denied(canAsk = false),
    )

    /** Первый запуск на чистой установке: не выдано ничего. */
    fun nothingGranted(): OnboardingUiState = state(
        notifications = grantedNotifications(permission = PermissionGrant.Denied(canAsk = true)),
        exactAlarms = PermissionGrant.Denied(canAsk = false),
    )

    /** Цепочка пройдена целиком: разрешения выданы, вендорские шаги подтверждены. */
    fun chainComplete(): OnboardingUiState = state(
        appStartup = ManualStepState(confirmedByUser = true),
        batterySaving = ManualStepState(
            confirmedByUser = true,
            hints = setOf(ManualStepHint.BATTERY_OPTIMIZATION_IGNORED),
        ),
        recentsPin = ManualStepState(confirmedByUser = true),
    )

    /** Оба разрешения выданы, косвенный признак есть — вендорский шаг всё равно не пройден. */
    fun hintsOnly(): OnboardingUiState = state(
        batterySaving = ManualStepState(
            confirmedByUser = false,
            hints = setOf(ManualStepHint.BATTERY_OPTIMIZATION_IGNORED),
        ),
    )

    /** Уведомления выданы, канал выключен: карточка называет причину «канал». */
    fun channelOff(): OnboardingUiState = state(
        notifications = grantedNotifications(channel = PermissionGate.DENIED),
    )

    /** Уведомления выданы, полноэкранное право не выдано. */
    fun fullScreenOff(): OnboardingUiState = state(
        notifications = grantedNotifications(fullScreenIntent = PermissionGate.DENIED),
    )

    /** Закрытая цепочка: плашка «Всё настроено» (SKL-86 добавит действие проверки). */
    fun allConfigured(): OnboardingUiState = chainComplete()

    /** Закрытая цепочка, но напоминания не приходили: плашка предупреждения о перерыве (ОВ-107). */
    fun allConfiguredWithOutage(): OnboardingUiState = allConfigured().copy(outageSince = OUTAGE_SINCE)

    /** Телефон другого производителя: вендорских шагов нет вовсе. */
    fun nonHuawei(): OnboardingUiState = asDrawn().copy(
        source = OnboardingSource.SETTINGS,
        checklist = asDrawn().checklist?.copy(vendor = VendorProfile.GENERIC),
    )

    /** Xiaomi: та же цепочка из пяти шагов, но автозапуск зовётся своим словом (SKL-112). */
    fun xiaomi(): OnboardingUiState = state(
        vendor = VendorProfile.XIAOMI,
        notifications = grantedNotifications(permission = PermissionGrant.Denied(canAsk = true)),
        exactAlarms = PermissionGrant.Denied(canAsk = false),
    )

    /** Samsung: четыре карточки — отдельного автозапуска у One UI в цепочке нет (SKL-112). */
    fun samsung(): OnboardingUiState = state(
        vendor = VendorProfile.SAMSUNG,
        notifications = grantedNotifications(permission = PermissionGrant.Denied(canAsk = true)),
        exactAlarms = PermissionGrant.Denied(canAsk = false),
    )

    /** Незнакомая прошивка: чужих вендорских слов на экране нет, остаются два общих шага. */
    fun unknownFirmware(): OnboardingUiState = state(
        vendor = VendorProfile.GENERIC,
        notifications = grantedNotifications(permission = PermissionGrant.Denied(canAsk = true)),
        exactAlarms = PermissionGrant.Denied(canAsk = false),
    )

    /** Повторный вход из настроек: экран тот же, индикатора шагов нет. */
    fun fromSettings(): OnboardingUiState = asDrawn().copy(source = OnboardingSource.SETTINGS)

    /**
     * Xiaomi, раздел автозапуска не открылся: запасная инструкция пунктами меню HyperOS/MIUI.
     *
     * Инструкция — своя у профиля: общий путь EMUI здесь был бы рассказом про чужое меню.
     */
    fun withInstructions(): OnboardingUiState = state(vendor = VendorProfile.XIAOMI)
        .copy(instructions = setOf(PermissionStep.APP_STARTUP))

    /** Вход из баннера перерыва: фокус на первом незакрытом шаге. */
    fun focusedFromOutage(): OnboardingUiState = asDrawn().copy(
        source = OnboardingSource.OUTAGE_BANNER,
        focusStep = PermissionStep.EXACT_ALARMS,
        outageSince = OUTAGE_SINCE,
    )

    /** Разрешение, выданное ранее, отозвано в системных настройках. */
    fun revoked(): OnboardingUiState =
        state(notifications = grantedNotifications(permission = PermissionGrant.Denied(canAsk = true)))
            .copy(revoked = listOf(PermissionStep.NOTIFICATIONS))

    fun state(
        vendor: VendorProfile = VendorProfile.EMUI,
        notifications: NotificationAccessState = grantedNotifications(),
        exactAlarms: PermissionGrant = PermissionGrant.Granted(READ_AT),
        appStartup: ManualStepState = ManualStepState(confirmedByUser = false),
        batterySaving: ManualStepState = ManualStepState(confirmedByUser = false),
        recentsPin: ManualStepState = ManualStepState(confirmedByUser = false),
        source: OnboardingSource = OnboardingSource.FIRST_LAUNCH,
    ): OnboardingUiState = OnboardingUiState(
        source = source,
        checklist = PermissionChecklist(vendor, notifications, exactAlarms, appStartup, batterySaving, recentsPin),
    )

    fun grantedNotifications(
        permission: PermissionGrant = PermissionGrant.Granted(READ_AT),
        channel: PermissionGate = PermissionGate.GRANTED,
        fullScreenIntent: PermissionGate = PermissionGate.GRANTED,
    ): NotificationAccessState = NotificationAccessState(permission, channel, fullScreenIntent)

    /** Действия-заглушки: состав экрана проверяется отдельно от переходов. */
    fun actions(
        onStepAction: (PermissionStep) -> Unit = {},
        onManualConfirmed: (PermissionStep, Boolean) -> Unit = { _, _ -> },
        onNext: () -> Unit = {},
        onSkip: () -> Unit = {},
        onDone: () -> Unit = {},
        onCheckDelivery: (() -> Unit)? = null,
    ): PermissionsActions = PermissionsActions(
        onStepAction = onStepAction,
        onManualConfirmed = onManualConfirmed,
        onNext = onNext,
        onSkip = onSkip,
        onDone = onDone,
        onCheckDelivery = onCheckDelivery,
    )
}

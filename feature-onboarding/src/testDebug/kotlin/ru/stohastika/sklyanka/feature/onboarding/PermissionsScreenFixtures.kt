package ru.stohastika.sklyanka.feature.onboarding

import ru.stohastika.sklyanka.domain.onboarding.AutostartHint
import ru.stohastika.sklyanka.domain.onboarding.AutostartState
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionGrant
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Заготовки состояний экрана разрешений.
 *
 * Общие для проверок состава и для снимков: два набора заготовок разошлись бы, и снимок
 * перестал бы изображать то, что проверяет тест.
 */
internal object PermissionsScreenFixtures {

    val READ_AT: Instant = Instant.parse("2026-09-25T07:00:00Z")
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

    /** Состояние макета: уведомления выданы, точные будильники нет, автозапуск не подтверждён. */
    fun asDrawn(): OnboardingUiState = state(
        notifications = PermissionGrant.Granted(READ_AT),
        exactAlarms = PermissionGrant.Denied(canAsk = false),
    )

    /** Первый запуск на чистой установке: не выдано ничего. */
    fun nothingGranted(): OnboardingUiState = state(
        notifications = PermissionGrant.Denied(canAsk = true),
        exactAlarms = PermissionGrant.Denied(canAsk = false),
    )

    /** Цепочка пройдена целиком: оба разрешения выданы, автозапуск подтверждён человеком. */
    fun chainComplete(): OnboardingUiState = state(
        autostart = AutostartState(confirmedByUser = true, hints = AutostartHint.entries.toSet()),
    )

    /** Оба разрешения выданы, косвенные признаки есть — третий шаг всё равно не пройден. */
    fun hintsOnly(): OnboardingUiState = state(
        autostart = AutostartState(
            confirmedByUser = false,
            hints = setOf(AutostartHint.BATTERY_OPTIMIZATION_IGNORED),
        ),
    )

    /** Повторный вход из настроек: экран тот же, индикатора шагов нет. */
    fun fromSettings(): OnboardingUiState = asDrawn().copy(source = OnboardingSource.SETTINGS)

    /** Прошивка, на которой системный раздел автозапуска не открылся. */
    fun withInstructions(): OnboardingUiState = state().copy(showAutostartInstructions = true)

    /** Пробное напоминание поставлено: человек видит, когда оно придёт. */
    fun testReminderScheduled(): OnboardingUiState = state().copy(testReminderAt = READ_AT.plusSeconds(SIXTY))

    /** Разрешение, выданное ранее, отозвано в системных настройках. */
    fun revoked(): OnboardingUiState = state(notifications = PermissionGrant.Denied(canAsk = true))
        .copy(revoked = listOf(PermissionStep.NOTIFICATIONS))

    fun state(
        notifications: PermissionGrant = PermissionGrant.Granted(READ_AT),
        exactAlarms: PermissionGrant = PermissionGrant.Granted(READ_AT),
        autostart: AutostartState = AutostartState(confirmedByUser = false),
        source: OnboardingSource = OnboardingSource.FIRST_LAUNCH,
    ): OnboardingUiState = OnboardingUiState(
        source = source,
        checklist = PermissionChecklist(notifications, exactAlarms, autostart),
    )

    fun toLocal(instant: Instant): LocalDateTime = instant.atZone(ZONE).toLocalDateTime()

    /** Действия-заглушки: состав экрана проверяется отдельно от переходов. */
    fun actions(
        onStepAction: (PermissionStep) -> Unit = {},
        onAutostartConfirmed: (Boolean) -> Unit = {},
        onTestReminder: () -> Unit = {},
        onNext: () -> Unit = {},
        onSkip: () -> Unit = {},
    ): PermissionsActions = PermissionsActions(
        onStepAction = onStepAction,
        onAutostartConfirmed = onAutostartConfirmed,
        onTestReminder = onTestReminder,
        onNext = onNext,
        onSkip = onSkip,
    )

    private const val SIXTY = 60L
}

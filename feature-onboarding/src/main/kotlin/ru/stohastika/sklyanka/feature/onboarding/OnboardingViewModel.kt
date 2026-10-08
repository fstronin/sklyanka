package ru.stohastika.sklyanka.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.onboarding.ConfirmReliabilityStepUseCase
import ru.stohastika.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.OnboardingOutcome
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
import ru.stohastika.sklyanka.domain.onboarding.RevokedPermissions
import ru.stohastika.sklyanka.domain.onboarding.StepAction
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.domain.onboarding.actionFor
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Экран «Настроить надёжность» (НАП-19 … НАП-21, ОВ-106, ОВ-107).
 *
 * Главное, что эта модель делает и что важнее остального кода задачи.
 *
 * **Состояние читается, а не помнится.** [refresh] спрашивает систему при открытии экрана
 * и при каждом возврате приложения на передний план: человек уходит выдавать разрешение
 * в системные настройки и возвращается, и карточка обязана измениться без перезахода
 * (критерий приёмки 12). Разрешение, отозванное через месяц, замечается тем же способом.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val readChecklist: ReadPermissionChecklistUseCase,
    private val confirmStep: ConfirmReliabilityStepUseCase,
    private val leaveOnboarding: LeaveOnboardingUseCase,
    private val navigator: SystemSettingsNavigator,
    private val log: OnboardingLog,
    time: TimeSource,
) : ViewModel() {

    private val zone = time.zone()

    private val _state = MutableStateFlow(OnboardingUiState(source = OnboardingSource.FIRST_LAUNCH))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Куда уходить с экрана; `null` — человек всё ещё на нём. */
    private val _exit = MutableStateFlow<Unit?>(null)
    val exit: StateFlow<Unit?> = _exit.asStateFlow()

    private var opened = false

    /**
     * Откуда пришёл человек и из-за какого перерыва.
     *
     * Задаётся хозяином экрана один раз: источник решает, показывать ли индикатор шагов
     * (критерий 13), куда прокрутить список (ОВ-107) и попадает в журнал. Фокус сбрасывается
     * и вычисляется заново после чтения системы — переданный вызывающим шаг устарел бы.
     */
    fun start(source: OnboardingSource, outageSince: Instant? = null) {
        // Исход — событие ухода, а не данные шага: при входе он обязан быть пустым. Без сброса
        // повторный вход с тем же исходом не закрывает экран — StateFlow не эмитит равное
        // значение, и LaunchedEffect(exit) не перезапускается (ОВ-86).
        _exit.value = null
        _state.update { it.copy(source = source, outageSince = outageSince, focusStep = null) }
        refresh(logOpening = true, focus = source.focusesFirstUnresolved)
    }

    /**
     * Перечитать состояние цепочки из системы (НФТ-52).
     *
     * Вызывается хозяином экрана в `onResume`, а не подпиской на поток: состояние разрешений
     * потоком не приходит — система об их изменении не уведомляет вовсе, и единственный
     * момент, когда его стоит спросить, — когда человек смотрит на экран.
     *
     * @param focus вычислить ли шаг фокуса после этого чтения (только при входе из баннера).
     */
    fun refresh(logOpening: Boolean = false, focus: Boolean = false) {
        viewModelScope.launch {
            val before = _state.value.checklist
            val after = readChecklist()
            val revoked = before?.let { RevokedPermissions.between(it, after) }.orEmpty()
            if (revoked.isNotEmpty()) log.revocationDetected(revoked)
            _state.update { current ->
                current.copy(
                    checklist = after,
                    // Запасная инструкция держится, пока шаг не закрыт; после подтверждения
                    // показывать путь тому, кто по нему уже прошёл, незачем.
                    instructions = current.instructions.filterTo(mutableSetOf()) { it in after.unresolved },
                    focusStep = if (focus) after.firstUnresolved else current.focusStep,
                    revoked = revoked,
                )
            }
            if (logOpening && !opened) {
                opened = true
                log.screenOpened(_state.value.source, after)
                if (_state.value.source.focusesFirstUnresolved) {
                    log.reliabilityRouteTaken(_state.value.source, _state.value.focusStep)
                }
            }
        }
    }

    /**
     * Действие карточки шага (`TDD.md §9.2`).
     *
     * Запрос разрешения на уведомления возвращается наружу: системный диалог показывает
     * Activity, а не модель. Всё остальное — переходы в системные настройки; неудача
     * открытия не молчит, а оставляет запасную инструкцию под карточкой (НФТ-8).
     *
     * @return `true`, если хозяин экрана обязан показать системный диалог разрешения.
     */
    fun onStepAction(step: PermissionStep): Boolean {
        val state = _state.value.checklist?.state(step) ?: return false
        return when (actionFor(step, state)) {
            null -> false

            StepAction.REQUEST_PERMISSION -> {
                log.permissionRequested(step)
                true
            }

            StepAction.OPEN_SYSTEM_SETTINGS -> {
                val result = when (step) {
                    PermissionStep.EXACT_ALARMS -> navigator.openExactAlarmSettings()
                    PermissionStep.NOTIFICATIONS -> navigator.openNotificationSettings()
                    PermissionStep.APP_STARTUP,
                    PermissionStep.BATTERY_SAVING,
                    PermissionStep.RECENTS_PIN,
                    -> null
                }
                applyOpenResult(step, result)
                false
            }

            StepAction.OPEN_VENDOR_STEP -> {
                val result = when (step) {
                    PermissionStep.APP_STARTUP -> navigator.openAppStartupSettings()
                    PermissionStep.BATTERY_SAVING -> navigator.openBatterySavingSettings()
                    PermissionStep.RECENTS_PIN -> null
                    PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> null
                }
                applyOpenResult(step, result)
                false
            }
        }
    }

    /** Результат системного диалога разрешения: состояние перечитывается у системы, а не берётся из ответа. */
    fun onPermissionResult(step: PermissionStep, granted: Boolean) {
        log.permissionResult(step, granted)
        // Отказ не закрывает экран и не мешает пройти остальные шаги (НАП-18).
        refresh()
    }

    /** Ручное подтверждение вендорского шага человеком (НАП-20, ОВ-53). */
    fun onManualConfirmed(step: PermissionStep, confirmed: Boolean) {
        viewModelScope.launch {
            confirmStep(step, confirmed)
            refresh()
        }
    }

    /** «Дальше» при пройденной цепочке: онбординг закончен, следующий экран — «Сегодня». */
    fun onNext() {
        leave(OnboardingOutcome.NEXT)
    }

    /** «Пропустить, настрою позже» — легальный путь, а не ошибка (НАП-21). */
    fun onSkip() {
        leave(OnboardingOutcome.SKIPPED)
    }

    /** «Готово» на повторном входе: тихий выход к точке входа (ОВ-106, `TDD.md §9.5`). */
    fun onDone() {
        leave(OnboardingOutcome.SKIPPED)
    }

    private fun leave(outcome: OnboardingOutcome) {
        viewModelScope.launch {
            val checklist = _state.value.checklist ?: readChecklist()
            leaveOnboarding(outcome, checklist)
            _exit.value = Unit
        }
    }

    /** Местное время момента: плашка перерыва читает момент в поясе человека (НФТ-48). */
    fun local(instant: Instant): LocalDateTime = instant.atZone(zone).toLocalDateTime()

    /** Записать исход открытия: неудача показывает запасную инструкцию по этому шагу. */
    private fun applyOpenResult(step: PermissionStep, result: StepOpenResult?) {
        if (result == null) return
        log.vendorStepOpened(step, result)
        if (result == StepOpenResult.FALLBACK_TO_INSTRUCTIONS) {
            _state.update { it.copy(instructions = it.instructions + step) }
        }
    }
}

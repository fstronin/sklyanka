package ru.stohastika.sklyanka.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.onboarding.AutostartOpenResult
import ru.stohastika.sklyanka.domain.onboarding.ConfirmAutostartUseCase
import ru.stohastika.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.OnboardingOutcome
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
import ru.stohastika.sklyanka.domain.onboarding.RevokedPermissions
import ru.stohastika.sklyanka.domain.onboarding.StepAction
import ru.stohastika.sklyanka.domain.onboarding.actionFor
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import ru.stohastika.sklyanka.domain.reminder.TestReminderUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Экран разрешений (НАП-19 … НАП-22).
 *
 * Две вещи, которые эта модель делает и которые важнее остального кода задачи.
 *
 * **Состояние читается, а не помнится.** [refresh] спрашивает систему при открытии экрана
 * и при каждом возврате приложения на передний план: человек уходит выдавать разрешение
 * в системные настройки и возвращается, и карточка обязана измениться без перезахода
 * (критерии приёмки 4 и 16). Разрешение, отозванное через месяц, замечается тем же способом.
 *
 * **Пробное напоминание не переписывается.** Операция принадлежит SKL-13
 * ([TestReminderUseCase]); здесь только место вызова и обещание «придёт через минуту»,
 * данное человеку **до** постановки (НАП-22, ПЛ-19).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val readChecklist: ReadPermissionChecklistUseCase,
    private val confirmAutostart: ConfirmAutostartUseCase,
    private val leaveOnboarding: LeaveOnboardingUseCase,
    private val testReminder: TestReminderUseCase,
    private val navigator: SystemSettingsNavigator,
    private val log: OnboardingLog,
    private val time: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(source = OnboardingSource.FIRST_LAUNCH))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Куда уходить с экрана; `null` — человек всё ещё на нём. */
    private val _exit = MutableStateFlow<OnboardingExit?>(null)
    val exit: StateFlow<OnboardingExit?> = _exit.asStateFlow()

    private var opened = false

    /**
     * Откуда пришёл человек.
     *
     * Задаётся хозяином экрана один раз: источник решает, показывать ли индикатор шагов
     * (критерий 15), и попадает в журнал (`TDD.md §11.1`).
     */
    fun start(source: OnboardingSource) {
        _state.update { it.copy(source = source) }
        refresh(logOpening = true)
    }

    /**
     * Перечитать состояние цепочки из системы (НФТ-52).
     *
     * Вызывается хозяином экрана в `onResume`, а не подпиской на поток: состояние разрешений
     * потоком не приходит — система об их изменении не уведомляет вовсе, и единственный
     * момент, когда его стоит спросить, — когда человек смотрит на экран.
     */
    fun refresh(logOpening: Boolean = false) {
        viewModelScope.launch {
            val before = _state.value.checklist
            val after = readChecklist()
            val revoked = before?.let { RevokedPermissions.between(it, after) }.orEmpty()
            if (revoked.isNotEmpty()) log.revocationDetected(revoked)
            _state.update { current ->
                current.copy(
                    checklist = after,
                    // Инструкция снимается, как только автозапуск подтверждён: показывать
                    // путь в настройки тому, кто по нему уже прошёл, незачем.
                    showAutostartInstructions = current.showAutostartInstructions && !after.autostart.confirmedByUser,
                    revoked = revoked,
                )
            }
            if (logOpening && !opened) {
                opened = true
                log.screenOpened(_state.value.source, after)
            }
        }
    }

    /**
     * Действие карточки шага (`TDD.md §9.2`).
     *
     * Запрос разрешения на уведомления возвращается наружу: системный диалог показывает
     * Activity, а не модель. Всё остальное — переходы в системные настройки.
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
                when (step) {
                    PermissionStep.EXACT_ALARMS -> navigator.openExactAlarmSettings()
                    PermissionStep.NOTIFICATIONS -> navigator.openNotificationSettings()
                    PermissionStep.AUTOSTART -> openAutostart()
                }
                false
            }

            StepAction.OPEN_AUTOSTART_SETTINGS -> {
                openAutostart()
                false
            }
        }
    }

    /** Результат системного диалога разрешения: состояние перечитывается у системы, а не берётся из ответа. */
    fun onPermissionResult(step: PermissionStep, granted: Boolean) {
        log.permissionResult(step, granted)
        // Отказ не закрывает экран и не мешает пройти остальные шаги (НАП-18, критерий 5).
        refresh()
    }

    /** Ручное подтверждение третьего шага человеком (НАП-20, ОВ-53). */
    fun onAutostartConfirmed(confirmed: Boolean) {
        viewModelScope.launch {
            confirmAutostart(confirmed)
            refresh()
        }
    }

    /**
     * Пробное напоминание через минуту (НАП-22, ПЛ-19).
     *
     * Обещание «придёт через минуту» стоит на экране до нажатия — плашкой из макета, —
     * поэтому человек знает, что телефон сейчас зазвонит (критерий приёмки 8).
     */
    fun onTestReminder() {
        viewModelScope.launch {
            val at = testReminder()
            log.testReminderStarted(_state.value.source, at)
            _state.update { it.copy(testReminderAt = at) }
        }
    }

    /** «Дальше»: при пройденной цепочке — подключение часов, иначе — «Сегодня». */
    fun onNext() {
        leave(OnboardingOutcome.NEXT)
    }

    /** «Пропустить, настрою позже» — легальный путь, а не ошибка (НАП-21). */
    fun onSkip() {
        leave(OnboardingOutcome.SKIPPED)
    }

    /** Местное время момента: пробное напоминание человек читает в своём поясе. */
    fun local(instant: Instant): LocalDateTime = instant.atZone(time.zone()).toLocalDateTime()

    private fun leave(outcome: OnboardingOutcome) {
        viewModelScope.launch {
            val checklist = _state.value.checklist ?: readChecklist()
            leaveOnboarding(outcome, checklist)
            _exit.value = destinationOf(outcome, checklist)
        }
    }

    private fun openAutostart() {
        val result = navigator.openAutostartSettings()
        log.autostartSettingsOpened(result)
        _state.update {
            it.copy(showAutostartInstructions = result == AutostartOpenResult.FALLBACK_TO_INSTRUCTIONS)
        }
    }

    private companion object {

        /**
         * Куда ведёт уход с экрана.
         *
         * «Дальше» при непройденной цепочке ведёт туда же, куда «Пропустить»: обещать
         * подключение часов тому, у кого напоминание ещё не дойдёт до телефона, значит
         * увести его от единственной причины, по которой он на этом экране.
         */
        fun destinationOf(outcome: OnboardingOutcome, checklist: PermissionChecklist): OnboardingExit = when {
            outcome == OnboardingOutcome.NEXT && checklist.allResolved -> OnboardingExit.WATCH_SETUP
            else -> OnboardingExit.TODAY
        }
    }
}

package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Что показывает экран проверки напоминаний (НАП-37, ОВ-126). */
data class DiagnosticsUiState(
    val snapshot: ReminderDiagnostics? = null,
    val zone: ZoneId,
    /**
     * Сегодняшняя дата в поясе человека (AC-26/AC-27).
     *
     * Нужна, чтобы назвать следующее напоминание «завтра» словами. Обновляется при
     * перечитывании снимка: открытый через полночь экран не считает «завтра» вчерашним.
     */
    val today: LocalDate,
    /**
     * Разрешение, для которого системный экран не открылся (НФТ-8, ОВ-106).
     *
     * Под строкой этого разрешения показывается запасная инструкция; снимок состояния
     * её не стирает, пока шаг не исправлен, — инструкция обязана остаться после возврата.
     */
    val fixFallback: ReminderPermission? = null,
    /** Открыта ли шторка запуска самопроверки доставки (SKL-86, ОВ-108). */
    val checkSheetOpen: Boolean = false,
    /** Открыта шторка отчёта разработчику (AC-30). */
    val reportOpen: Boolean = false,
    /** Текст комментария; живёт в состоянии, чтобы пережить пересборку композиции. */
    val reportComment: String = "",
    /** Исход последней попытки отправки; null — ещё не отправляли (AC-32/AC-33). */
    val reportOutcome: DeveloperReportOutcome? = null,
)

/**
 * Диагностика напоминаний (НАП-37, НАП-39).
 *
 * Первый уровень не выполняет технических операций: он читает снимок, ведёт самопроверку
 * доставки и отчёт разработчику. Пересборка плана идёт автоматически, а не по кнопке
 * (ОВ-128); наружу экран ничего не отправляет без явного нажатия «Отправить отчёт»
 * (НАП-39).
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: BuildReminderDiagnosticsUseCase,
    /**
     * Самопроверка доставки (SKL-86).
     *
     * Точка входа одна: экран открывает шторку, запускает и отменяет проверку, а закрытие
     * истёкшей проверки и подтверждение результата идут тем же путём. Сценарии собирает
     * `app`: `feature-reminder` не знает ни будильников, ни их адресов (`ADR-0007`).
     */
    private val deliveryCheck: DeliveryCheckController,
    /**
     * Отправка отчёта разработчику (ОВ-127, ПМ-32).
     *
     * Шов без сети: реализацию подставляет `app`. В первой версии — заглушка, всегда
     * возвращающая [DeveloperReportOutcome.SENT]; сетевая — отдельная задача.
     */
    private val reportSender: DeveloperReportSender,
    private val time: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState(zone = time.zone(), today = today()))
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Истёкшая проверка закрывается до сборки снимка: «не дошло» обязано
            // появиться без перезапуска приложения (НФТ-52). Снимок — чтение, и менять
            // журнал ему нельзя, поэтому закрытие идёт здесь.
            deliveryCheck.finish()
            val snapshot = diagnostics()
            _state.value = _state.value.copy(
                snapshot = snapshot,
                today = today(),
                // Инструкция снимается только тогда, когда разрешение действительно выдано:
                // возврат без исправления оставляет её на месте (критерий приёмки 6).
                fixFallback = _state.value.fixFallback?.takeIf {
                    snapshot.permissions[it] != PermissionState.GRANTED
                },
            )
        }
    }

    /** Сегодняшняя дата в поясе человека из подменяемых часов (НФТ-48). */
    private fun today(): LocalDate = time.now().atZone(time.zone()).toLocalDate()

    /** «Проверить доставку»: открыть шторку с инструкцией, ничего не ставя (ОВ-108). */
    fun onCheckDelivery() {
        _state.value = _state.value.copy(checkSheetOpen = true)
    }

    /** «Отмена» в шторке: закрыть её и не ставить тестовое срабатывание. */
    fun onDismissCheckSheet() {
        _state.value = _state.value.copy(checkSheetOpen = false)
    }

    /** «Начать проверку»: назначить тестовое срабатывание и показать ход проверки. */
    fun onStartDeliveryCheck() {
        viewModelScope.launch {
            deliveryCheck.start()
            _state.value = _state.value.copy(checkSheetOpen = false)
            refresh()
        }
    }

    /** «Отменить проверку»: снять тестовое срабатывание и вернуть блок в исходное состояние. */
    fun onCancelDeliveryCheck() {
        viewModelScope.launch {
            deliveryCheck.cancel()
            refresh()
        }
    }

    /**
     * Результат проверки прочитан человеком на экране.
     *
     * Подтверждение постоянное: без него результат, уже показанный в блоке, вернулся бы
     * баннером при следующем входе (ОВ-108).
     */
    fun onAcknowledgeDeliveryCheck() {
        val finished = _state.value.snapshot?.deliveryCheck as? DeliveryCheckState.Finished ?: return
        viewModelScope.launch { deliveryCheck.acknowledge(finished.dueAt) }
    }

    /**
     * Исход «Исправить» приходит от хозяина экрана (ОВ-106, `TDD.md §9.4`).
     *
     * Системный `Intent` собирает `app` (`ADR-0007`), а состояние экрана живёт здесь:
     * неудача открытия превращается в запасную инструкцию под строкой разрешения.
     */
    fun onFixResult(permission: ReminderPermission, result: ReminderFixResult) {
        _state.value = _state.value.copy(
            fixFallback = permission.takeIf { result == ReminderFixResult.FALLBACK_TO_INSTRUCTIONS },
        )
    }

    /** «Сообщить о проблеме»: открыть шторку с пустым полем и без исхода (AC-30, AC-40). */
    fun onReportOpen() {
        _state.value = _state.value.copy(reportOpen = true, reportComment = "", reportOutcome = null)
    }

    /** Правка комментария: поле необязательное, ошибок у него нет (AC-31). */
    fun onReportCommentChange(text: String) {
        _state.value = _state.value.copy(reportComment = text)
    }

    /**
     * «Отправить отчёт»: вызвать шов отправки и назвать исход (AC-32/AC-33).
     *
     * Отказ не глотается: комментарий сохраняется, а состояние ошибки покажет «Повторить».
     * `CancellationException` перебрасывается — отмена корутины не является отказом отправки.
     */
    @Suppress("TooGenericExceptionCaught")
    fun onReportSend() {
        val comment = _state.value.reportComment
        viewModelScope.launch {
            val outcome = try {
                reportSender.send(comment)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DeveloperReportOutcome.FAILED
            }
            _state.value = _state.value.copy(reportOutcome = outcome)
        }
    }

    /** Закрыть шторку отчёта и очистить состояние: повтор открывается с пустым полем (AC-40). */
    fun onReportDismiss() {
        _state.value = _state.value.copy(reportOpen = false, reportComment = "", reportOutcome = null)
    }

    /** Местное время момента: журнал хранит моменты в UTC, человек читает их в своём поясе. */
    fun local(instant: Instant): LocalDateTime = instant.atZone(_state.value.zone).toLocalDateTime()
}

/** Исход отправки отчёта; `FAILED` показывает «Повторить» (AC-32/AC-33). */
enum class DeveloperReportOutcome { SENT, FAILED }

/**
 * Отправка отчёта разработчику как зависимость экрана (ОВ-127, ПМ-32).
 *
 * Функциональный интерфейс, а не сетевой порт: в первой версии `app` подставляет заглушку,
 * ничего не собирающую и никуда не отправляющую (сети нет). Сетевой адаптер встанет в тот же
 * шов отдельной задачей; тогда же появится автосборка состава без медицинских данных.
 */
fun interface DeveloperReportSender {
    suspend fun send(comment: String): DeveloperReportOutcome
}

/**
 * Вход в настройку разрешений заново — как зависимость экрана (НАП-21).
 *
 * Экран разрешений принадлежит SKL-14 и живёт в `feature-onboarding`, а экранные модули
 * друг от друга не зависят: решать, куда ведёт «Пройти настройку заново», обязан `app`
 * (`ADR-0007`). Точка расширения задана типом, поэтому подключение не потребует правок
 * экрана диагностики.
 */
fun interface ReminderSetupEntry {
    fun intentFor(context: Context): Intent
}

/**
 * Чем закончился переход «Исправить» (НФТ-8, ОВ-106).
 *
 * Неудача открытия системного экрана — не исключение: на части прошивок раздела нет,
 * и экран обязан показать запасную инструкцию. [NO_SCREEN] — «человеку здесь исправлять
 * нечего» (приёмник загрузки), это не ошибка.
 */
enum class ReminderFixResult {
    OPENED,
    FALLBACK_TO_INSTRUCTIONS,
    NO_SCREEN,
}

/**
 * Переход «Исправить» к системному экрану разрешения (ОВ-49, НАП-15).
 *
 * Экран не собирает системный `Intent` сам: он не знает ни `android.provider.Settings`,
 * ни того, какой экран соответствует разрешению. Реализация живёт в `app`, потому что
 * `feature-reminder` не должен зависеть от онбординга (`ADR-0007`).
 *
 * Исход возвращается наружу: неудача не глотается, а превращается в запасную инструкцию
 * под строкой разрешения.
 */
fun interface ReminderSettingsFixer {
    fun fix(permission: ReminderPermission): ReminderFixResult
}

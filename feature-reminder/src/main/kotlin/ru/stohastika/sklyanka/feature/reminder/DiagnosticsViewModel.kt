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
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Исход нажатия «Проверить сейчас»: экран не отвечает молчанием ни в одном исходе (НФТ-8). */
enum class ReminderCheckOutcome { SUCCESS, FAILED }

/** Что показывает экран проверки напоминаний (НАП-37, ОВ-49). */
data class DiagnosticsUiState(
    val snapshot: ReminderDiagnostics? = null,
    val zone: ZoneId,
    /**
     * Имя предлагаемого файла выгрузки журнала (НФТ-51).
     *
     * Нужно **до** системного выбора места: имя предлагается системе, а решение о месте
     * остаётся за человеком, и приложение выбранный путь не запоминает (НФТ-17).
     */
    val exportFileName: String = "",
    /** Исход последней выгрузки журнала: человеку нельзя отвечать молчанием ни в одном исходе. */
    val exportOutcome: ReminderJournalExportOutcome? = null,
    val busy: Boolean = false,
    /**
     * Раскрыт ли технический блок «Для поддержки» (ОВ-49).
     *
     * Живёт в состоянии экрана, а не в `remember` композиции: стенд приёмки пересобирает
     * композицию на каждый шаг, и `remember` терял бы раскрытие между «нажал» и «проверил».
     */
    val supportExpanded: Boolean = false,
    /** Исход последней ручной проверки; null — ещё не проверяли. */
    val checkOutcome: ReminderCheckOutcome? = null,
    /**
     * Разрешение, для которого системный экран не открылся (НФТ-8, ОВ-106).
     *
     * Под строкой этого разрешения показывается запасная инструкция; снимок состояния
     * её не стирает, пока шаг не исправлен, — инструкция обязана остаться после возврата.
     */
    val fixFallback: ReminderPermission? = null,
)

/**
 * Диагностика напоминаний (НАП-37, НАП-39).
 *
 * Пересборку плана экран не делает сам: он зовёт ту же операцию, что зовут приёмники,
 * с причиной «ручное действие человека» (НАП-11). Наружу экран не отправляет ничего —
 * порта сети в проекте нет вовсе (ПЛ-3, НФТ-14).
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: BuildReminderDiagnosticsUseCase,
    private val check: ReminderCheckNow,
    private val exportJournal: ExportReminderJournalUseCase,
    /**
     * Вариант измерения пилота (SKL-85).
     *
     * Нужен только измерительной сборке: в обычной значение не читается, а переключателя
     * в блоке «Для поддержки» нет вовсе (ПМ-20).
     */
    private val settings: ReminderSettings,
    time: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DiagnosticsUiState(zone = time.zone(), exportFileName = exportJournal.fileName()),
    )
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = diagnostics()
            _state.value = _state.value.copy(
                snapshot = snapshot,
                busy = false,
                // Инструкция снимается только тогда, когда разрешение действительно выдано:
                // возврат без исправления оставляет её на месте (критерий приёмки 6).
                fixFallback = _state.value.fixFallback?.takeIf {
                    snapshot.permissions[it] != PermissionState.GRANTED
                },
            )
        }
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

    /** Раскрыть или свернуть технический блок «Для поддержки» (ОВ-49). */
    fun onToggleSupport() {
        _state.value = _state.value.copy(supportExpanded = !_state.value.supportExpanded)
    }

    /**
     * «Проверить сейчас»: пересобрать окно и обновить показанное состояние (НАП-37).
     *
     * Отказ перепланирования не молчит: экран называет исход словами (НФТ-8). Побочный
     * факт отказа остаётся в журнале силами планировщика, а `CancellationException`
     * перебрасывается — отмена корутины не является отказом проверки.
     */
    @Suppress("TooGenericExceptionCaught")
    fun onCheckNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, checkOutcome = null)
            val outcome = try {
                check(RescheduleReason.MANUAL)
                ReminderCheckOutcome.SUCCESS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReminderCheckOutcome.FAILED
            }
            _state.value = _state.value.copy(checkOutcome = outcome)
            refresh()
        }
    }

    /**
     * Смена варианта измерения пилота (SKL-85, `TDD.md §9.4`).
     *
     * Порядок обязателен: сначала настройка, потом пересборка. `AlarmManagerGateway` читает
     * настройку в момент постановки будильника, и пересборка до записи поставила бы
     * будильники прежним механизмом. Пересборка идёт существующим путём «Проверить сейчас»,
     * поэтому след причины, счётчики и обновление снимка — те же.
     */
    fun onAlarmKindChange(kind: AlarmKind) {
        viewModelScope.launch {
            settings.setAlarmStrategy(kind)
            onCheckNow()
        }
    }

    /**
     * «Выгрузить журнал»: запись в документ, выбранный человеком (НФТ-51).
     *
     * Ссылка на документ приходит строкой: о `Uri` домен не знает (НФТ-46), а само
     * место выгрузки нигде не сохраняется — приложение не ведёт учёта того, куда ушёл файл.
     */
    fun onExportTo(handle: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, exportOutcome = null)
            val outcome = exportJournal(ExportTarget(handle))
            _state.value = _state.value.copy(busy = false, exportOutcome = outcome)
        }
    }

    /** Местное время момента: журнал хранит моменты в UTC, человек читает их в своём поясе. */
    fun local(instant: Instant): LocalDateTime = instant.atZone(_state.value.zone).toLocalDateTime()
}

/**
 * «Проверить сейчас» как зависимость экрана.
 *
 * Функциональный интерфейс, а не прямая зависимость на планировщик: полная пересборка
 * состоит из генерации приёмов и постановки будильников в строгом порядке, и знать этот
 * порядок обязан один класс в `app`, а не каждый экран (SKL-06, НАП-11).
 */
fun interface ReminderCheckNow {
    suspend operator fun invoke(reason: RescheduleReason)
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

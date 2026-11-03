package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.DeliveryCheckResult
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import ru.stohastika.sklyanka.domain.analytics.DeliveryCheckOutcome as AnalyticsCheckOutcome

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
    /**
     * Исход последней служебной выгрузки журнала; `null` — выгрузки не было или она
     * отменена (отмена не исход, SKL-106).
     */
    val journalExport: JournalExportUi? = null,
)

/**
 * Диагностика напоминаний (НАП-37, НАП-39).
 *
 * Первый уровень не выполняет технических операций: он читает снимок и ведёт
 * самопроверку доставки. Пересборка плана идёт автоматически, а не по кнопке (ОВ-128);
 * наружу экран ничего не отправляет (сети у ядра нет, НАП-39, ПЛ-3). Единственный
 * служебный вход — выгрузка журнала файлом через системный выбор места (НФТ-51):
 * файл остаётся на устройстве человека и в сеть не уходит.
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
     * Служебная выгрузка журнала срабатываний (НФТ-51, SKL-106).
     *
     * Тот же доменный сценарий, что проверяется тестом `ExportReminderJournalUseCaseTest`:
     * имя файла экран знает **до** выбора места, запись идёт настоящим файловым портом
     * `app`, отмена системного диалога в исход не попадает.
     */
    private val exportJournal: ExportReminderJournalUseCase,
    private val time: TimeSource,
    /**
     * Запись исхода самопроверки (SKL-165, АН-1).
     *
     * Событие ставится там, где проверка **признана завершённой и объявлена человеку**,
     * а не при входе на экран: повторный вход на ту же проверку дубля не даёт —
     * завершённость опознаётся по моменту срабатывания (`dueAt`).
     */
    private val analytics: AnalyticsRecorder,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState(zone = time.zone(), today = today()))
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    /** Проверка, исход которой уже записан: «одна проверка — одно событие». */
    private var reportedCheck: Instant? = null

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
            reportDeliveryCheck(snapshot.deliveryCheck, snapshot.deliveryCheckAcknowledged)
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

    /**
     * Исход завершённой самопроверки — ровно один раз на проверку.
     *
     * Проверка опознаётся моментом срабатывания: перечитывание снимка, повторный вход
     * и поворот экрана видят ту же завершённую проверку и второго события не дают.
     * Подтверждённая проверка не пишется ещё и после перезапуска экрана: человек её
     * уже видел, и признак подтверждения переживает смерть процесса.
     */
    private fun reportDeliveryCheck(state: DeliveryCheckState, acknowledged: Boolean) {
        val finished = state as? DeliveryCheckState.Finished ?: return
        if (acknowledged || reportedCheck == finished.dueAt) return
        reportedCheck = finished.dueAt
        analytics.record(DeliveryCheckResult(finished.outcome.toAnalytics()))
    }

    /**
     * Исход проверки в каталоге событий: у продукта четыре исхода, у события три (ПМ-26).
     *
     * «Не дошло» и «не завершена» сводятся к `failed`: для воронки обе — проверка,
     * которая не подтвердила надёжность доставки, а различает их экран, не аналитика.
     */
    private fun DeliveryCheckOutcome.toAnalytics(): AnalyticsCheckOutcome = when (this) {
        DeliveryCheckOutcome.ON_TIME -> AnalyticsCheckOutcome.PASSED
        DeliveryCheckOutcome.LATE -> AnalyticsCheckOutcome.LATE
        DeliveryCheckOutcome.NOT_DELIVERED, DeliveryCheckOutcome.NOT_COMPLETED -> AnalyticsCheckOutcome.FAILED
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

    /**
     * Имя файла для системного выбора места: экран знает его **до** диалога, и система
     * предлагает именно его (НФТ-51). Окончательное решение, куда попадёт файл, — за
     * человеком; приложение путь не запоминает (НФТ-17).
     */
    fun journalExportFileName(): String = exportJournal.fileName()

    /** Системного выбора места нет (обработчик исчез или не объявлен): отказ, а не молчание. */
    fun onJournalExportUnavailable() {
        _state.value = _state.value.copy(journalExport = JournalExportUi.Failed)
    }

    /**
     * Место выбрано (или выбор отменён): выгрузить журнал и назвать исход числом записей.
     *
     * `handle == null` — отмена системного диалога: она не исход, состояние плашки
     * очищается, никакого «не сохранено» человек не видит (тот же порядок, что ПЕР-31).
     * `CancellationException` перебрасывается — отмена корутины не является отказом.
     */
    @Suppress("TooGenericExceptionCaught")
    fun onJournalExportResult(handle: String?) {
        if (handle == null) {
            _state.value = _state.value.copy(journalExport = null)
            return
        }
        val target = ExportTarget(handle)
        viewModelScope.launch {
            val outcome = try {
                exportJournal(target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReminderJournalExportOutcome.Failed
            }
            _state.value = _state.value.copy(
                journalExport = when (outcome) {
                    is ReminderJournalExportOutcome.Saved -> JournalExportUi.Saved(outcome.records)
                    ReminderJournalExportOutcome.NotEnoughSpace -> JournalExportUi.NoSpace
                    ReminderJournalExportOutcome.Failed -> JournalExportUi.Failed
                },
            )
        }
    }

    /** Местное время момента: журнал хранит моменты в UTC, человек читает их в своём поясе. */
    fun local(instant: Instant): LocalDateTime = instant.atZone(_state.value.zone).toLocalDateTime()
}

/** Чем закончилась служебная выгрузка журнала; отмены здесь нет по определению. */
sealed interface JournalExportUi {

    data class Saved(val records: Int) : JournalExportUi

    data object NoSpace : JournalExportUi

    data object Failed : JournalExportUi
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

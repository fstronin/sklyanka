@file:Suppress("TooManyFunctions")

package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.reminder.ReminderDeviationWords
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderOnTimeSummary
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.reminder.ReminderWeekSummary
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.Instant
import java.time.LocalDateTime

/**
 * Проверка напоминаний (НАП-37, ОВ-49, SKL-79).
 *
 * Двухуровневый экран. Первый уровень отвечает человеку, открывшему его в худший момент —
 * когда напоминания не приходят: что не так, что сделать, всё ли в порядке, когда было
 * последнее и когда следующее. Технические подробности — журнал, причина и счётчики
 * пересборки, общее число будильников — уходят в свёрнутый блок «Для поддержки»: знание
 * нужно поддержке, а не человеку.
 *
 * Пробного напоминания здесь нет (НАП-22/23, ОВ-50/ОВ-53): кнопка удалена целиком.
 * Наружу экран не отправляет ничего (НАП-39).
 */
@Composable
@Suppress("LongParameterList")
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onCheckNow: () -> Unit,
    onExportJournal: () -> Unit,
    /**
     * «Пройти настройку заново» (НАП-21). `null` означает «экрана настройки нет»: кнопки
     * тогда нет вовсе — кнопка, ведущая в никуда, обещает больше, чем продукт умеет.
     */
    onRestartSetup: (() -> Unit)?,
    onToggleSupport: () -> Unit,
    onFixPermission: (ReminderPermission) -> Unit,
    onBack: () -> Unit,
    toLocal: (Instant) -> LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val snapshot = state.snapshot
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag(TAG_DIAGNOSTICS),
    ) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = stringResource(R.string.diagnostics_title)),
            kind = ScreenHeaderKind.Nested,
            onBack = onBack,
        )
        if (snapshot == null) return@Column
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SklyankaSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            item { Warnings(snapshot, toLocal) }
            item { ReliableLine(snapshot) }
            item { Permissions(snapshot, onFixPermission) }
            item { Actions(state, onCheckNow) }
            item { WeekSummary(snapshot.week) }
            item { LastSimple(snapshot, toLocal) }
            item { NextPlan(snapshot, toLocal) }
            item { SupportToggle(onToggleSupport) }
            if (state.supportExpanded) {
                item { LastShown(snapshot, toLocal) }
                item { NextPlanned(snapshot, toLocal) }
                item { OnTime(snapshot.onTime) }
                item { LastReschedule(snapshot, toLocal) }
                item { SectionTitle(stringResource(R.string.diagnostics_section_journal)) }
                items(snapshot.journal) { entry -> JournalRow(entry, toLocal) }
                item {
                    SupportActions(
                        state = state,
                        onExportJournal = onExportJournal,
                        onRestartSetup = onRestartSetup,
                    )
                }
            } else {
                item { ExportOutcome(state) }
            }
        }
    }
}

/**
 * Предупреждения о ненадёжности (НАП-18) и о перерыве (НАП-38).
 *
 * Стоят первыми: человек, открывший проверку, пришёл именно за этим ответом.
 */
@Composable
private fun Warnings(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime) {
    val warning = snapshot.reliability.warning
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        if (warning != null) {
            Banner(
                text = stringResource(
                    when (warning) {
                        ReminderWarning.NOTIFICATIONS_OFF -> R.string.diagnostics_warning_notifications
                        ReminderWarning.EXACT_ALARMS_OFF -> R.string.diagnostics_warning_exact_alarms
                        ReminderWarning.BOTH_OFF -> R.string.diagnostics_warning_both
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_WARNING),
            )
        }
        snapshot.outage?.let { outage ->
            Banner(
                text = stringResource(R.string.diagnostics_outage, toLocal(outage.since).journalMoment()),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_OUTAGE),
            )
        }
    }
}

/** Короткий ответ при исправности: «Всё в порядке» (ОВ-49). */
@Composable
private fun ReliableLine(snapshot: ReminderDiagnostics) {
    if (!snapshot.reliability.isReliable) return
    Text(
        text = stringResource(R.string.diagnostics_reliable),
        style = SklyankaType.BodyDefaultStrong,
        color = SklyankaTheme.colors.taken,
        modifier = Modifier.testTag(TAG_RELIABLE),
    )
}

/**
 * Состояние разрешений (НАП-37, ОВ-49).
 *
 * При всех выданных — одна строка «Разрешения выданы». Иначе каждое разрешение своей
 * строкой со словами состояния и действием «Исправить»; непроверяемый автозапуск EMUI
 * честно говорит «проверьте вручную» (НАП-20).
 */
@Composable
private fun Permissions(snapshot: ReminderDiagnostics, onFixPermission: (ReminderPermission) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.surface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_PERMISSIONS),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        if (snapshot.permissions.values.all { it == PermissionState.GRANTED }) {
            Text(
                text = stringResource(R.string.diagnostics_all_granted),
                style = SklyankaType.BodyDefault,
                color = SklyankaTheme.colors.text,
                modifier = Modifier.testTag(TAG_ALL_GRANTED),
            )
            return@Column
        }
        ReminderPermission.entries.forEach { permission ->
            val state = snapshot.permissions[permission] ?: PermissionState.UNKNOWN
            PermissionRow(permission, state, onFixPermission)
        }
    }
}

@Composable
private fun PermissionRow(
    permission: ReminderPermission,
    state: PermissionState,
    onFixPermission: (ReminderPermission) -> Unit,
) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_PERMISSION_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Text(
            text = permission.label(),
            style = SklyankaType.Body,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        Text(text = state.label(), style = SklyankaType.Caption, color = colors.textMuted)
        // BOOT_RECEIVER человеку исправлять нечего: состояние объявлено манифестом (ПЛ-9).
        if (permission != ReminderPermission.BOOT_RECEIVER) {
            QuietButton(
                text = stringResource(R.string.diagnostics_fix),
                onClick = { onFixPermission(permission) },
                modifier = Modifier.testTag(TAG_FIX),
            )
        }
    }
    // Отказ в доступе к политике «Не беспокоить» — ограничение, а не ошибка (НАП-17).
    if (permission == ReminderPermission.DND_POLICY_ACCESS && state != PermissionState.GRANTED) {
        Value(stringResource(R.string.diagnostics_dnd_hint))
    }
}

/** «Проверить сейчас» тихой кнопкой и исход словами (НФТ-8, ОВ-49). */
@Composable
private fun Actions(state: DiagnosticsUiState, onCheckNow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        SecondaryButton(
            text = stringResource(R.string.diagnostics_action_check),
            onClick = onCheckNow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_CHECK_NOW),
        )
        state.checkOutcome?.let { outcome ->
            Text(
                text = stringResource(
                    when (outcome) {
                        ReminderCheckOutcome.SUCCESS -> R.string.diagnostics_check_done
                        ReminderCheckOutcome.FAILED -> R.string.diagnostics_check_failed
                    },
                ),
                style = SklyankaType.Caption,
                color = SklyankaTheme.colors.textMuted,
                modifier = Modifier.testTag(TAG_CHECK_OUTCOME),
            )
        }
    }
}

/** Сводка за 7 дней фразой: сколько сработало и сколько приёмов пропущено (ОВ-49). */
@Composable
private fun WeekSummary(summary: ReminderWeekSummary) {
    Text(
        text = if (summary.hasData) {
            stringResource(R.string.diagnostics_week_summary, summary.fired, summary.missed)
        } else {
            stringResource(R.string.diagnostics_week_summary_none)
        },
        style = SklyankaType.Body,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier.testTag(TAG_WEEK_SUMMARY),
    )
}

/** Последнее напоминание простыми словами: лекарство, план, факт, отклонение словом (ОВ-49). */
@Composable
private fun LastSimple(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime) {
    val entry = snapshot.lastShown
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.surface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_LAST_SHORT),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        SectionTitle(stringResource(R.string.diagnostics_section_last))
        if (entry == null) {
            Value(stringResource(R.string.diagnostics_last_none))
            return@Column
        }
        val name = snapshot.lastMedicationName ?: stringResource(R.string.diagnostics_none)
        entry.scheduledAt?.let { planned ->
            Value(stringResource(R.string.diagnostics_last_line, name, toLocal(planned).journalMoment()))
        }
        entry.firedAt?.let { actual ->
            Value(stringResource(R.string.diagnostics_last_actual, toLocal(actual).journalMoment()))
        }
        entry.deviationSeconds?.let { deviation ->
            Value(deviationWord(deviation))
        }
    }
}

/** Следующее напоминание планом, а не обещанием (ОВ-49). */
@Composable
private fun NextPlan(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime) {
    val next = snapshot.nextAt
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.surface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_NEXT_SHORT),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        SectionTitle(stringResource(R.string.diagnostics_section_next))
        if (next == null) {
            Value(stringResource(R.string.diagnostics_next_none))
            return@Column
        }
        val name = snapshot.nextMedicationName ?: stringResource(R.string.diagnostics_none)
        Value(
            stringResource(
                R.string.diagnostics_next_line,
                name,
                toLocal(next).journalMoment(),
                stringResource(R.string.diagnostics_next_plan),
            ),
        )
    }
}

/** Строка-раскрытие технического блока «Для поддержки» (ОВ-49). */
@Composable
private fun SupportToggle(onToggle: () -> Unit) {
    SecondaryButton(
        text = stringResource(R.string.diagnostics_support),
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_SUPPORT_TOGGLE),
    )
}

@Composable
private fun SupportActions(state: DiagnosticsUiState, onExportJournal: () -> Unit, onRestartSetup: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        SecondaryButton(
            text = stringResource(R.string.diagnostics_action_export),
            onClick = onExportJournal,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_EXPORT_JOURNAL),
        )
        if (onRestartSetup != null) {
            SecondaryButton(
                text = stringResource(R.string.diagnostics_action_setup),
                onClick = onRestartSetup,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_RESTART_SETUP),
            )
        }
        ExportOutcome(state)
    }
}

/** Исход выгрузки журнала словами: отмолчаться нельзя ни при удаче, ни при отказе (НФТ-8). */
@Composable
private fun ExportOutcome(state: DiagnosticsUiState) {
    state.exportOutcome?.let { outcome ->
        Text(
            text = outcome.label(),
            style = SklyankaType.Caption,
            color = SklyankaTheme.colors.textMuted,
            modifier = Modifier.testTag(TAG_EXPORT_OUTCOME),
        )
    }
}

/**
 * Сводка «вовремя за 7 дней» (НФТ-51) — техническая, под раскрытием.
 *
 * Строкой, а не полосой: сводка отвечает на вопрос «работает ли оно вообще», и число
 * срабатываний рядом с долей нужно затем, чтобы «100 %» на одном срабатывании нельзя
 * было принять за доказательство надёжности.
 */
@Composable
private fun OnTime(summary: ReminderOnTimeSummary) {
    Section(stringResource(R.string.diagnostics_section_on_time), TAG_ON_TIME) {
        val percent = summary.percent
        if (percent == null) {
            Value(stringResource(R.string.diagnostics_on_time_none))
        } else {
            Value(stringResource(R.string.diagnostics_on_time, summary.onTime, summary.fired, percent))
        }
        Value(stringResource(R.string.diagnostics_on_time_rule))
    }
}

/** Последнее сработавшее напоминание в техническом виде: план, факт и отклонение в секундах. */
@Composable
private fun LastShown(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime) {
    val entry = snapshot.lastShown
    Section(stringResource(R.string.diagnostics_section_last), TAG_LAST_SHOWN) {
        if (entry == null) {
            Value(stringResource(R.string.diagnostics_none))
        } else {
            entry.scheduledAt?.let { Value(stringResource(R.string.diagnostics_planned, toLocal(it).journalMoment())) }
            entry.firedAt?.let { Value(stringResource(R.string.diagnostics_actual, toLocal(it).journalMoment())) }
            entry.deviationSeconds?.let { Value(stringResource(R.string.diagnostics_deviation, it)) }
        }
    }
}

/** Следующее запланированное напоминание и общее число поставленных будильников (НАП-37). */
@Composable
private fun NextPlanned(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime) {
    Section(stringResource(R.string.diagnostics_section_next), TAG_NEXT) {
        val next = snapshot.nextAt
        Value(if (next == null) stringResource(R.string.diagnostics_none) else toLocal(next).journalMoment())
        Value(stringResource(R.string.diagnostics_scheduled_total, snapshot.totalScheduled))
    }
}

/** Время и причина последней пересборки плана (НАП-14, НАП-37). */
@Composable
private fun LastReschedule(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime) {
    val entry = snapshot.lastReschedule
    Section(stringResource(R.string.diagnostics_section_reschedule), TAG_RESCHEDULE) {
        if (entry == null) {
            Value(stringResource(R.string.diagnostics_none))
        } else {
            Value(toLocal(entry.occurredAt).journalMoment())
            reasonOf(entry)?.let { Value(it.label()) }
            Value(
                stringResource(
                    R.string.diagnostics_alarm_counters,
                    entry.alarmsCancelled ?: 0,
                    entry.alarmsScheduled ?: 0,
                ),
            )
        }
    }
}

@Composable
private fun JournalRow(entry: ReminderLogEntry, toLocal: (Instant) -> LocalDateTime) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.surface, SklyankaShapes.Compact)
            .padding(SklyankaSpacing.Medium)
            .testTag(TAG_JOURNAL_ROW),
    ) {
        Text(
            text = toLocal(entry.occurredAt).journalMoment(),
            style = SklyankaType.Caption,
            color = SklyankaTheme.colors.textMuted,
        )
        Text(text = entry.event.label(), style = SklyankaType.Body, color = SklyankaTheme.colors.text)
        entry.deviationSeconds?.let {
            Value(stringResource(R.string.diagnostics_deviation, it))
        }
        // Идентификатор приёма, а не название лекарства: журнал показывается на экране
        // и копируется текстом, поэтому медицинских данных в нём нет (НФТ-19).
        entry.intakeId?.let { Value(it) }
    }
}

@Composable
private fun Section(title: String, tag: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.surface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = SklyankaType.BodyStrong, color = SklyankaTheme.colors.text)
}

@Composable
private fun Value(text: String) {
    Text(text = text, style = SklyankaType.Body, color = SklyankaTheme.colors.textMuted)
}

/** Отклонение словами: «вовремя» или «с опозданием на N мин» (ОВ-49). */
@Composable
private fun deviationWord(deviationSeconds: Long): String = if (ReminderDeviationWords.isOnTime(deviationSeconds)) {
    stringResource(R.string.diagnostics_on_time_word)
} else {
    val minutes = ReminderDeviationWords.lateMinutes(deviationSeconds).toInt()
    pluralStringResource(R.plurals.diagnostics_late, minutes, minutes)
}

/** Причина пересборки из служебного кода записи журнала. */
private fun reasonOf(entry: ReminderLogEntry): RescheduleReason? =
    RescheduleReason.entries.firstOrNull { it.code == entry.reason }

const val TAG_DIAGNOSTICS = "diagnostics"
const val TAG_WARNING = "diagnostics_warning"
const val TAG_OUTAGE = "diagnostics_outage"
const val TAG_RELIABLE = "diagnostics_reliable"
const val TAG_ALL_GRANTED = "diagnostics_all_granted"
const val TAG_PERMISSION_ROW = "diagnostics_permission_row"
const val TAG_FIX = "diagnostics_fix"
const val TAG_CHECK_NOW = "diagnostics_check_now"
const val TAG_CHECK_OUTCOME = "diagnostics_check_outcome"
const val TAG_WEEK_SUMMARY = "diagnostics_week_summary"
const val TAG_LAST_SHORT = "diagnostics_last_short"
const val TAG_NEXT_SHORT = "diagnostics_next_short"
const val TAG_SUPPORT_TOGGLE = "diagnostics_support_toggle"
const val TAG_LAST_SHOWN = "diagnostics_last_shown"
const val TAG_NEXT = "diagnostics_next"
const val TAG_RESCHEDULE = "diagnostics_reschedule"
const val TAG_ON_TIME = "diagnostics_on_time"
const val TAG_EXPORT_JOURNAL = "diagnostics_export_journal"
const val TAG_EXPORT_OUTCOME = "diagnostics_export_outcome"
const val TAG_RESTART_SETUP = "diagnostics_restart_setup"
const val TAG_PERMISSIONS = "diagnostics_permissions"
const val TAG_JOURNAL_ROW = "diagnostics_journal_row"

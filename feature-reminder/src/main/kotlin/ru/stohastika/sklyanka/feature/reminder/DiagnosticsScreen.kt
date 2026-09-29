package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderOnTimeSummary
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.PrimaryButton
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
 * Диагностика напоминаний (НАП-37).
 *
 * Экран существует, чтобы человек мог отличить «приложение сломалось» от «система
 * запретила», а разработчик — принять внятный отчёт. Поэтому здесь нет ни одной сводной
 * оценки вида «всё в порядке»: каждое разрешение своей строкой, каждое срабатывание
 * с отклонением, причина последней пересборки словами (НФТ-8).
 *
 * Макета у экрана нет (ОВ-49): состав задан требованием НАП-37, вёрстка собрана из
 * компонентов дизайн-системы. Наружу экран не отправляет ничего (НАП-39).
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
            item {
                Actions(
                    state = state,
                    onCheckNow = onCheckNow,
                    onExportJournal = onExportJournal,
                    onRestartSetup = onRestartSetup,
                )
            }
            item { LastShown(snapshot, toLocal) }
            item { NextPlanned(snapshot, toLocal) }
            item { OnTime(snapshot.onTime) }
            item { LastReschedule(snapshot, toLocal) }
            item { Permissions(snapshot) }
            item { SectionTitle(stringResource(R.string.diagnostics_section_journal)) }
            items(snapshot.journal) { entry -> JournalRow(entry, toLocal) }
        }
    }
}

/**
 * Предупреждения о ненадёжности (НАП-18) и о перерыве (НАП-38).
 *
 * Стоят первыми: человек, открывший диагностику, пришёл именно за этим ответом.
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

@Composable
@Suppress("LongParameterList")
private fun Actions(
    state: DiagnosticsUiState,
    onCheckNow: () -> Unit,
    onExportJournal: () -> Unit,
    onRestartSetup: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        PrimaryButton(
            text = stringResource(R.string.diagnostics_action_check),
            onClick = onCheckNow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_CHECK_NOW),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium)) {
            SecondaryButton(
                text = stringResource(R.string.diagnostics_action_export),
                onClick = onExportJournal,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_EXPORT_JOURNAL),
            )
            // Кнопки нет, когда некуда вести: экран настройки разрешений подключает `app`.
            if (onRestartSetup != null) {
                SecondaryButton(
                    text = stringResource(R.string.diagnostics_action_setup),
                    onClick = onRestartSetup,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TAG_RESTART_SETUP),
                )
            }
        }
        // Исход выгрузки словами: отмолчаться нельзя ни при удаче, ни при отказе (НФТ-8).
        state.exportOutcome?.let { outcome ->
            Text(
                text = outcome.label(),
                style = SklyankaType.Caption,
                color = SklyankaTheme.colors.textMuted,
                modifier = Modifier.testTag(TAG_EXPORT_OUTCOME),
            )
        }
    }
}

/**
 * Сводка «вовремя за 7 дней» (НФТ-51).
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

/** Последнее сработавшее напоминание: план, факт и отклонение в секундах (НАП-37). */
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

/** Состояние каждого разрешения отдельной строкой (НАП-37, НФТ-8). */
@Composable
private fun Permissions(snapshot: ReminderDiagnostics) {
    Section(stringResource(R.string.diagnostics_section_permissions), TAG_PERMISSIONS) {
        ReminderPermission.entries.forEach { permission ->
            val state = snapshot.permissions[permission] ?: PermissionState.UNKNOWN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = permission.label(), style = SklyankaType.Body, color = SklyankaTheme.colors.text)
                Text(text = state.label(), style = SklyankaType.Body, color = SklyankaTheme.colors.textMuted)
            }
            // Отказ в доступе к политике «Не беспокоить» — ограничение, а не ошибка (НАП-17).
            if (permission == ReminderPermission.DND_POLICY_ACCESS && state != PermissionState.GRANTED) {
                Value(stringResource(R.string.diagnostics_dnd_hint))
            }
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

/** Причина пересборки из служебного кода записи журнала. */
private fun reasonOf(entry: ReminderLogEntry): RescheduleReason? =
    RescheduleReason.entries.firstOrNull { it.code == entry.reason }

const val TAG_DIAGNOSTICS = "diagnostics"
const val TAG_WARNING = "diagnostics_warning"
const val TAG_OUTAGE = "diagnostics_outage"
const val TAG_CHECK_NOW = "diagnostics_check_now"
const val TAG_LAST_SHOWN = "diagnostics_last_shown"
const val TAG_NEXT = "diagnostics_next"
const val TAG_RESCHEDULE = "diagnostics_reschedule"
const val TAG_ON_TIME = "diagnostics_on_time"
const val TAG_EXPORT_JOURNAL = "diagnostics_export_journal"
const val TAG_EXPORT_OUTCOME = "diagnostics_export_outcome"
const val TAG_RESTART_SETUP = "diagnostics_restart_setup"
const val TAG_PERMISSIONS = "diagnostics_permissions"
const val TAG_JOURNAL_ROW = "diagnostics_journal_row"

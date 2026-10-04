@file:Suppress("TooManyFunctions")

package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.PermissionGroup
import ru.stohastika.sklyanka.domain.reminder.PermissionMarker
import ru.stohastika.sklyanka.domain.reminder.ReminderDeviationWords
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderOutage
import ru.stohastika.sklyanka.domain.reminder.ReminderPermissionGroups
import ru.stohastika.sklyanka.domain.reminder.ReminderVerdict
import ru.stohastika.sklyanka.domain.reminder.ReminderVerdictRule
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.reminder.ReminderWeekSummary
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.ButtonForm
import ru.stohastika.sklyanka.ui.component.ButtonLabel
import ru.stohastika.sklyanka.ui.component.ChatGlyph
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.ChevronGlyph
import ru.stohastika.sklyanka.ui.component.InfoGlyph
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.StackingRow
import ru.stohastika.sklyanka.ui.component.WarningGlyph
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Проверка напоминаний — финальный первый уровень (НАП-37, ОВ-126).
 *
 * Порядок секций один во всех состояниях: вердикт → «Разрешения» → «Проверка доставки» →
 * «Напоминания» → «Сообщить о проблеме». Технические сведения (журнал, счётчики пересборки,
 * список будильников) сюда не выходят: они уезжают в отчёт разработчику. Кнопок «Проверить
 * сейчас» и «Повторить проверку» нет (ОВ-128); перепланирование автоматическое.
 */
@Composable
@Suppress("LongParameterList")
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    /**
     * «Пройти настройку заново» (НАП-21). `null` означает «экрана настройки нет»: кнопки
     * тогда нет вовсе — кнопка, ведущая в никуда, обещает больше, чем продукт умеет.
     */
    onRestartSetup: (() -> Unit)?,
    onFixPermission: (ReminderPermission) -> Unit,
    /** «Проверить доставку»: открыть шторку запуска самопроверки (SKL-86, ОВ-108). */
    onCheckDelivery: () -> Unit,
    /** «Отмена» в шторке: закрыть её, ничего не ставя. */
    onDismissCheckSheet: () -> Unit,
    /** «Начать проверку»: назначить тестовое срабатывание. */
    onStartDeliveryCheck: () -> Unit,
    /** «Отменить проверку»: снять тестовое срабатывание. */
    onCancelDeliveryCheck: () -> Unit,
    /** «Понятно»: исход прочитан и скрыт (AC-18, AC-22). */
    onAcknowledgeDeliveryCheck: () -> Unit,
    /** «Исправить» у неудачного исхода: прочитать и вести на «Настроить надёжность». */
    onFixDeliveryCheck: () -> Unit,
    onReportOpen: () -> Unit,
    onReportCommentChange: (String) -> Unit,
    onReportSend: () -> Unit,
    onReportDismiss: () -> Unit,
    onBack: () -> Unit,
    toLocal: (Instant) -> LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val snapshot = state.snapshot
    if (state.checkSheetOpen) {
        CheckSheetLayer(
            onStart = onStartDeliveryCheck,
            onDismiss = onDismissCheckSheet,
            modifier = modifier,
        )
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
            DiagnosticsList(
                state = state,
                snapshot = snapshot,
                toLocal = toLocal,
                onRestartSetup = onRestartSetup,
                onFixPermission = onFixPermission,
                onCheckDelivery = onCheckDelivery,
                onCancelDeliveryCheck = onCancelDeliveryCheck,
                onAcknowledgeDeliveryCheck = onAcknowledgeDeliveryCheck,
                onFixDeliveryCheck = onFixDeliveryCheck,
                onReportOpen = onReportOpen,
            )
        }
        DeveloperReportSheet(
            state = state,
            onCommentChange = onReportCommentChange,
            onSend = onReportSend,
            onDismiss = onReportDismiss,
        )
    }
}

/**
 * Содержимое экрана проверки списком (ОВ-126).
 *
 * Вынесено из [DiagnosticsScreen] ради обозримости: у функции-корня остаётся выбор
 * между шторкой и содержимым, а состав списка читается отдельно.
 */
@Composable
@Suppress("LongParameterList")
private fun DiagnosticsList(
    state: DiagnosticsUiState,
    snapshot: ReminderDiagnostics,
    toLocal: (Instant) -> LocalDateTime,
    onRestartSetup: (() -> Unit)?,
    onFixPermission: (ReminderPermission) -> Unit,
    onCheckDelivery: () -> Unit,
    onCancelDeliveryCheck: () -> Unit,
    onAcknowledgeDeliveryCheck: () -> Unit,
    onFixDeliveryCheck: () -> Unit,
    onReportOpen: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Section),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        item { Verdict(snapshot, toLocal, onRestartSetup) }
        item {
            Section(R.string.diagnostics_section_permissions) {
                PermissionsCard(snapshot, state.fixFallback, onFixPermission, onRestartSetup)
            }
        }
        item {
            Section(R.string.diagnostics_section_delivery) {
                DeliveryCheckBlock(
                    state = snapshot.deliveryCheck,
                    acknowledged = snapshot.deliveryCheckAcknowledged,
                    onCheckDelivery = onCheckDelivery,
                    onCancelDeliveryCheck = onCancelDeliveryCheck,
                    onAcknowledgeDeliveryCheck = onAcknowledgeDeliveryCheck,
                    onFixDeliveryCheck = onFixDeliveryCheck,
                )
            }
        }
        item {
            Section(R.string.diagnostics_section_reminders) {
                RemindersSection(snapshot, toLocal, state.today)
            }
        }
        item { ReportFooter(onReportOpen) }
    }
}

/**
 * Шторка запуска поверх экрана проверки (SKL-86, ОВ-108).
 *
 * Шторка — часть экрана, а не второй экран: её накрывает та же Activity, и возврат
 * из неё обязан вернуть человека на «Проверку напоминаний». Тег экрана остаётся
 * на обёртке, чтобы «экран открыт» было верно в обоих состояниях.
 */
@Composable
private fun CheckSheetLayer(onStart: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_DIAGNOSTICS),
    ) {
        DeliveryCheckSheet(onStart = onStart, onDismiss = onDismiss)
    }
}

/** Надстрочник секции: «Разрешения», «Проверка доставки», «Напоминания» (AC-1). */
@Composable
private fun Section(titleRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large)) {
        Text(
            text = stringResource(titleRes).uppercase(interfaceLocale()),
            style = SklyankaType.Overline,
            color = SklyankaTheme.colors.textMuted,
            modifier = Modifier.padding(start = SklyankaSpacing.Micro),
        )
        content()
    }
}

/**
 * Вердикт — ровно один верхний элемент (ОВ-130).
 *
 * Перерыв всегда рисуется отдельным баннером ниже: и под баннером старшей причины,
 * и вместо строки «Всё в порядке». Строка «Всё в порядке» несёт галочку и слова
 * пояснения, чтобы смысл не держался одним цветом (НФТ-30).
 */
@Composable
private fun Verdict(
    snapshot: ReminderDiagnostics,
    toLocal: (Instant) -> LocalDateTime,
    onRestartSetup: (() -> Unit)?,
) {
    val verdict = ReminderVerdictRule.of(
        reliability = snapshot.reliability,
        deliveryCheck = snapshot.deliveryCheck,
        deliveryCheckAcknowledged = snapshot.deliveryCheckAcknowledged,
        hasUpcoming = snapshot.nextAt != null,
    )
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        when (verdict) {
            is ReminderVerdict.Cause -> CauseBanner(verdict.warning)
            ReminderVerdict.DeliveryFailed -> Banner(
                text = stringResource(R.string.diagnostics_check_failed_verdict),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_WARNING),
            )

            is ReminderVerdict.Ok -> {
                if (snapshot.outage == null) VerdictOk(verdict.hasUpcoming)
            }
        }
        snapshot.outage?.let { outage -> OutageBanner(outage, toLocal, onRestartSetup) }
    }
}

/** Баннер старшей причины ненадёжности — теми же словами, что на «Сегодня» (ОВ-82, ОВ-107). */
@Composable
private fun CauseBanner(warning: ReminderWarning) {
    Banner(
        text = stringResource(
            when (warning) {
                ReminderWarning.NOTIFICATIONS_OFF -> R.string.diagnostics_warning_notifications
                ReminderWarning.NOTIFICATION_CHANNEL_OFF -> R.string.diagnostics_warning_channel
                ReminderWarning.EXACT_ALARMS_OFF -> R.string.diagnostics_warning_exact_alarms
                ReminderWarning.FULL_SCREEN_INTENT_OFF -> R.string.diagnostics_warning_full_screen
                ReminderWarning.BOTH_OFF -> R.string.diagnostics_warning_both
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_WARNING),
    )
}

/** Перерыв — отдельный баннер с датой и действием «Исправить» (AC-5, ОВ-132). */
@Composable
private fun OutageBanner(outage: ReminderOutage, toLocal: (Instant) -> LocalDateTime, onRestartSetup: (() -> Unit)?) {
    Banner(
        text = stringResource(R.string.diagnostics_outage, toLocal(outage.since).outageMoment()),
        actionLabel = onRestartSetup?.let { stringResource(R.string.diagnostics_fix) },
        onAction = onRestartSetup,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_OUTAGE),
    )
}

/**
 * Строка «Всё в порядке»: галочка на мягкой плашке, заголовок и пояснение словами.
 *
 * При отсутствии будущих приёмов пояснение называет это (AC-28): молчание вердикта
 * читалось бы как «неизвестно».
 */
@Composable
private fun VerdictOk(hasUpcoming: Boolean) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_VERDICT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.MarkerTile)
                .background(colors.takenSoft, SklyankaShapes.Medium),
            contentAlignment = Alignment.Center,
        ) {
            CheckGlyph(color = colors.taken, size = SklyankaSizes.IconPlateGlyph)
        }
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
            Text(
                text = stringResource(R.string.diagnostics_reliable),
                style = SklyankaType.BodyLarge,
                color = colors.taken,
            )
            // Пояснение «приходят вовремя» стоит всегда; отсутствие будущих приёмов
            // уточняется второй строкой, а не подменяет первую (AC-2, AC-28, макет 8).
            Text(
                text = stringResource(R.string.diagnostics_verdict_ok_sub),
                style = SklyankaType.Caption,
                color = colors.textMuted,
            )
            if (!hasUpcoming) {
                Text(
                    text = stringResource(R.string.diagnostics_verdict_ok_no_next),
                    style = SklyankaType.Caption,
                    color = colors.textMuted,
                )
            }
        }
    }
}

/**
 * Карточка «Разрешения»: только требующие действия, двумя группами по последствию (AC-8, AC-9).
 *
 * Строка несёт маркер-плитку, последствие словами и единое тихое «Исправить ›» в колонке
 * фиксированной ширины. Выданные не разворачиваются: под карточкой футер «Остальные
 * разрешения выданы», а когда проблем нет — одна строка «Разрешения выданы».
 */
@Composable
private fun PermissionsCard(
    snapshot: ReminderDiagnostics,
    fixFallback: ReminderPermission?,
    onFixPermission: (ReminderPermission) -> Unit,
    onRestartSetup: (() -> Unit)?,
) {
    val colors = SklyankaTheme.colors
    val actionable = ReminderPermission.entries.filter { permission ->
        ReminderPermissionGroups.requiresAction(permission, snapshot.permissions[permission] ?: PermissionState.UNKNOWN)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            // `.permgroups{padding:2px 16px}`: вертикальное поле 2 dp, боковое — в строках.
            .padding(vertical = SklyankaSpacing.Hair)
            .testTag(TAG_PERMISSIONS),
    ) {
        if (actionable.isEmpty()) {
            GrantedLine(R.string.diagnostics_all_granted, TAG_ALL_GRANTED)
        } else {
            PermissionGroupsList(actionable, snapshot, fixFallback, onFixPermission)
            GrantedLine(R.string.diagnostics_permission_rest_granted, TAG_PERMISSION_REST_GRANTED)
        }
        if (onRestartSetup != null) {
            PermissionsSeparator()
            RestartSetupRow(onRestartSetup)
        }
    }
}

/**
 * Волосяная линия между элементами карточки «Разрешения» (`colors.lineOnSurface`).
 *
 * Внутри бокового поля карточки, как `.pitem + .pitem`, `.pgroup + .pgroup` и
 * `.pfoot-action{border-top}` макета: линия не выходит за поля строк.
 */
@Composable
private fun PermissionsSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .height(SklyankaSizes.BorderThin)
            .background(SklyankaTheme.colors.lineOnSurface)
            .testTag(TAG_PERMISSION_SEPARATOR),
    )
}

@Composable
private fun PermissionGroupsList(
    actionable: List<ReminderPermission>,
    snapshot: ReminderDiagnostics,
    fixFallback: ReminderPermission?,
    onFixPermission: (ReminderPermission) -> Unit,
) {
    val groups = PermissionGroup.entries.mapNotNull { group ->
        val rows = actionable.filter { ReminderPermissionGroups.groupOf(it) == group }
        if (rows.isEmpty()) null else group to rows
    }
    groups.forEachIndexed { index, (group, rows) ->
        if (index > 0) PermissionsSeparator()
        PermissionGroupBlock(group, rows, snapshot, fixFallback, onFixPermission)
    }
}

@Composable
private fun PermissionGroupBlock(
    group: PermissionGroup,
    rows: List<ReminderPermission>,
    snapshot: ReminderDiagnostics,
    fixFallback: ReminderPermission?,
    onFixPermission: (ReminderPermission) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // `.pgroup{padding:13px 0 3px}`: низ группы отделяет её от следующей строки.
            .padding(bottom = SklyankaSizes.PermissionGroupPaddingBottom)
            .testTag(TAG_PERMISSION_GROUP),
    ) {
        Text(
            text = stringResource(
                when (group) {
                    PermissionGroup.FIX_FIRST -> R.string.diagnostics_permission_group_fix_first
                    PermissionGroup.CAN_DEFER -> R.string.diagnostics_permission_group_can_defer
                },
            ),
            style = SklyankaType.CaptionStrong,
            color = SklyankaTheme.colors.textMuted,
            modifier = Modifier.padding(
                start = SklyankaSpacing.Block,
                end = SklyankaSpacing.Block,
                top = SklyankaSizes.PermissionGroupLabelPaddingTop,
                // `.pgroup-label{padding-bottom:2px}`: зазор до первой строки — 2 + 12 строки.
                bottom = SklyankaSpacing.Hair,
            ),
        )
        rows.forEachIndexed { index, permission ->
            if (index > 0) PermissionsSeparator()
            val state = snapshot.permissions[permission] ?: PermissionState.UNKNOWN
            PermissionRow(permission, state, fixFallback == permission, onFixPermission)
        }
    }
}

@Composable
private fun PermissionRow(
    permission: ReminderPermission,
    state: PermissionState,
    fixFailed: Boolean,
    onFixPermission: (ReminderPermission) -> Unit,
) {
    val colors = SklyankaTheme.colors
    // Переведённая подпись разрешения длиннее русской («Auto-launch and power saving»):
    // ряд переносит действие под текст, а текст не сжимается ниже самого длинного слова (§6.3).
    Column(
        modifier = Modifier
            // `.pitem{padding:12px 0}`: линия-разделитель получает 12 dp воздуха сверху и снизу.
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Medium),
    ) {
        StackingRow(
            leading = {
                PermissionMarkerTile(ReminderPermissionGroups.markerOf(permission, state))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
                    Text(text = permission.label(), style = SklyankaType.BodyDefaultStrong, color = colors.text)
                    Text(
                        text = ReminderPermissionGroups.stateShown(permission, state).label(),
                        style = SklyankaType.CaptionStrong,
                        color = colors.textMuted,
                        modifier = Modifier.testTag(TAG_PERMISSION_STATE),
                    )
                    permission.consequence()?.let { consequence ->
                        Text(
                            text = consequence,
                            style = SklyankaType.CaptionSmall,
                            color = colors.textMuted,
                            modifier = Modifier.testTag(TAG_PERMISSION_CONSEQUENCE),
                        )
                    }
                }
            },
            action = {
                FixAction(onClick = { onFixPermission(permission) })
            },
            gap = SklyankaSpacing.Medium,
            minTextWidth = SklyankaSizes.MinTextColumn,
            modifier = Modifier.testTag(TAG_PERMISSION_ROW),
            // При крупном шрифте действие уезжает под текст во всю ширину строки, и его
            // фиксированная колонка снимается: иначе «Исправить» рвётся по буквам (AC-42, §6.3).
            stackedActionFullWidth = true,
        )
        // Системный экран не открылся — не молчим: под строкой остаётся ручной путь (НФТ-8, ОВ-106).
        if (fixFailed) {
            Text(
                text = stringResource(R.string.diagnostics_fallback_app_startup),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
                modifier = Modifier
                    .padding(top = SklyankaSpacing.Small)
                    .testTag(TAG_FIX_FALLBACK),
            )
        }
    }
}

/** Плитка-маркер строки: фон и глиф различают вид, но смысл несут видимое слово и последствие (ОВ-134). */
@Composable
private fun PermissionMarkerTile(marker: PermissionMarker) {
    val colors = SklyankaTheme.colors
    val background = when (marker) {
        PermissionMarker.WARNING -> colors.accentSoft
        PermissionMarker.NEUTRAL -> colors.neutralSoft
    }
    Box(
        modifier = Modifier
            .size(SklyankaSizes.MarkerTile)
            .background(background, SklyankaShapes.Medium)
            .testTag(TAG_PERMISSION_MARKER),
        contentAlignment = Alignment.Center,
    ) {
        when (marker) {
            PermissionMarker.WARNING -> WarningGlyph(color = colors.warning, size = SklyankaSizes.IconPlateGlyph)
            PermissionMarker.NEUTRAL -> InfoGlyph(color = colors.textMuted, size = SklyankaSizes.IconPlateGlyph)
        }
    }
}

/** Тихое «Исправить ›» в колонке фиксированной ширины: подпись слева, шеврон справа (AC-11). */
@Composable
private fun FixAction(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            // Минимум колонки — под «Исправить» при 100 %; в стековом режиме ряд
            // расширяет действие до всей строки, и слово не переносится по буквам (AC-42).
            .widthIn(min = SklyankaSizes.PermissionActionColumn)
            .heightIn(min = SklyankaSizes.MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        QuietButton(
            text = stringResource(R.string.diagnostics_fix),
            onClick = onClick,
            kind = QuietButtonKind.Accent,
            modifier = Modifier.testTag(TAG_FIX),
        )
        ChevronGlyph(color = SklyankaTheme.colors.iconFaint, size = SklyankaSizes.Chevron)
    }
}

@Composable
private fun GrantedLine(titleRes: Int, tag: String) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // «Остальные разрешения выданы» — та же строка `.pitem`, что и требующие действия.
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Medium)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.MarkerTile)
                .background(colors.takenSoft, SklyankaShapes.Medium),
            contentAlignment = Alignment.Center,
        ) {
            CheckGlyph(color = colors.taken, size = SklyankaSizes.IconPlateGlyph)
        }
        Text(
            text = stringResource(titleRes),
            style = SklyankaType.LabelStrong,
            color = colors.textMuted,
        )
    }
}

/** Тихая «Пройти настройку заново» под карточкой: подпись слева, шеврон справа (AC-12). */
@Composable
private fun RestartSetupRow(onRestartSetup: () -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SklyankaSizes.MinTouchTarget)
            .clickable(onClick = onRestartSetup)
            .testTag(TAG_RESTART_SETUP),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.diagnostics_action_setup),
            style = SklyankaType.LabelStrong,
            color = colors.accent,
            modifier = Modifier.padding(horizontal = SklyankaSpacing.Block),
        )
        ChevronGlyph(
            color = colors.iconFaint,
            size = SklyankaSizes.Chevron,
            modifier = Modifier.padding(end = SklyankaSpacing.Block),
        )
    }
}

/**
 * Блок самопроверки доставки без повтора (ОВ-126, ОВ-131).
 *
 * Подтверждённый исход возвращает блок к пояснению и кнопке; неподтверждённый —
 * показывает исход один раз: успех и «не состоялась» закрываются «Понятно», опоздание
 * и недолёт ведут «Исправить» на «Настроить надёжность».
 */
@Composable
private fun DeliveryCheckBlock(
    state: DeliveryCheckState,
    acknowledged: Boolean,
    onCheckDelivery: () -> Unit,
    onCancelDeliveryCheck: () -> Unit,
    onAcknowledgeDeliveryCheck: () -> Unit,
    onFixDeliveryCheck: () -> Unit,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_CHECK_BLOCK),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        when {
            state is DeliveryCheckState.Running -> RunningCheck(onCancelDeliveryCheck)
            state is DeliveryCheckState.Finished && !acknowledged -> FinishedCheck(
                state = state,
                onAcknowledgeDeliveryCheck = onAcknowledgeDeliveryCheck,
                onFixDeliveryCheck = onFixDeliveryCheck,
            )

            else -> CheckStart(onCheckDelivery)
        }
    }
}

/** Пояснение и кнопка — обычное состояние блока до и после проверки (AC-15). */
@Composable
private fun CheckStart(onCheckDelivery: () -> Unit) {
    val colors = SklyankaTheme.colors
    Text(
        text = stringResource(R.string.diagnostics_delivery_explanation),
        style = SklyankaType.CaptionSmall,
        color = colors.textMuted,
    )
    SecondaryButton(
        text = stringResource(R.string.diagnostics_action_check_delivery),
        onClick = onCheckDelivery,
        form = ButtonForm.Screen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_CHECK_DELIVERY),
    )
}

/** Ход проверки: нейтральная плашка «идёт» и тихая отмена (ОВ-108, AC-16). */
@Composable
private fun RunningCheck(onCancelDeliveryCheck: () -> Unit) {
    Banner(
        text = stringResource(R.string.diagnostics_check_running),
        kind = BannerKind.Neutral,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_CHECK_RUNNING),
    )
    QuietButton(
        text = stringResource(R.string.diagnostics_check_cancel),
        onClick = onCancelDeliveryCheck,
        kind = QuietButtonKind.Muted,
        modifier = Modifier.testTag(TAG_CHECK_CANCEL),
    )
}

/** Исход проверки: успех и «не состоялась» — «Понятно», опоздание и недолёт — «Исправить» (AC-18…AC-21). */
@Composable
private fun FinishedCheck(
    state: DeliveryCheckState.Finished,
    onAcknowledgeDeliveryCheck: () -> Unit,
    onFixDeliveryCheck: () -> Unit,
) {
    CardValue(
        text = state.resultText(),
        modifier = Modifier.testTag(TAG_CHECK_RESULT),
    )
    when (state.outcome) {
        DeliveryCheckOutcome.ON_TIME,
        DeliveryCheckOutcome.NOT_COMPLETED,
        -> QuietButton(
            text = stringResource(R.string.diagnostics_check_acknowledge),
            onClick = onAcknowledgeDeliveryCheck,
            kind = QuietButtonKind.Accent,
            modifier = Modifier.testTag(TAG_CHECK_RESULT_ACKNOWLEDGE),
        )

        DeliveryCheckOutcome.LATE,
        DeliveryCheckOutcome.NOT_DELIVERED,
        -> SecondaryButton(
            text = stringResource(R.string.diagnostics_fix),
            onClick = onFixDeliveryCheck,
            form = ButtonForm.Screen,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_CHECK_FIX),
        )
    }
}

/** «Напоминания»: сводка 7 дней, последнее и следующее напоминание (AC-25…AC-28). */
@Composable
private fun RemindersSection(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large)) {
        WeekSummary(snapshot.week)
        LastSimple(snapshot, toLocal)
        NextPlan(snapshot, toLocal, today)
    }
}

/**
 * Сводка 7 дней одной фразой (AC-25).
 *
 * Оба числа склоняются независимыми `plurals`, а «0 из 0» и «0 %» не показываются:
 * домен отдаёт числа, экран выбирает одну из четырёх формулировок.
 */
@Composable
private fun WeekSummary(summary: ReminderWeekSummary) {
    val fired = summary.fired
    val missed = summary.missed
    val text = when {
        fired > 0 && missed > 0 -> prefix() +
            pluralStringResource(R.plurals.diagnostics_week_fired, fired, fired) +
            separator() +
            pluralStringResource(R.plurals.diagnostics_week_missed, missed, missed)

        fired > 0 -> prefix() +
            pluralStringResource(R.plurals.diagnostics_week_fired, fired, fired) +
            separator() +
            stringResource(R.string.diagnostics_week_missed_none)

        missed > 0 -> prefix() +
            stringResource(R.string.diagnostics_week_fired_none) +
            separator() +
            pluralStringResource(R.plurals.diagnostics_week_missed, missed, missed)

        else -> stringResource(R.string.diagnostics_week_none)
    }
    Text(
        text = text,
        style = SklyankaType.Caption,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier
            .padding(start = SklyankaSpacing.Micro)
            .testTag(TAG_WEEK_SUMMARY),
    )
}

@Composable
private fun prefix(): String = stringResource(R.string.diagnostics_week_prefix)

@Composable
private fun separator(): String = stringResource(R.string.diagnostics_week_separator)

/** Последнее напоминание простыми словами: лекарство, плановое время, факт и отклонение (AC-26). */
@Composable
private fun LastSimple(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime) {
    val entry = snapshot.lastShown
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, SklyankaTheme.colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_LAST_SHORT),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
    ) {
        CardTitle(stringResource(R.string.diagnostics_section_last))
        if (entry == null) {
            CardValue(stringResource(R.string.diagnostics_last_none))
            return@Column
        }
        val name = snapshot.lastMedicationName ?: stringResource(R.string.diagnostics_none)
        entry.scheduledAt?.let { planned ->
            CardValue(stringResource(R.string.diagnostics_last_line, name, toLocal(planned).diagnosticsClock()))
        }
        val firedAt = entry.firedAt
        if (firedAt != null) {
            val clock = toLocal(firedAt).diagnosticsClock()
            val deviation = entry.deviationSeconds
            CardSubtitle(
                text = if (deviation == null) {
                    stringResource(R.string.diagnostics_last_actual, clock)
                } else {
                    stringResource(R.string.diagnostics_last_actual_with_deviation, clock, deviationWord(deviation))
                },
            )
        }
    }
}

/** Следующее напоминание планом, а не обещанием (AC-27, AC-28). */
@Composable
private fun NextPlan(snapshot: ReminderDiagnostics, toLocal: (Instant) -> LocalDateTime, today: LocalDate) {
    val next = snapshot.nextAt
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, SklyankaTheme.colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_NEXT_SHORT),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
    ) {
        CardTitle(stringResource(R.string.diagnostics_section_next))
        if (next == null) {
            CardValue(stringResource(R.string.diagnostics_next_none))
            CardSubtitle(stringResource(R.string.diagnostics_next_none_explanation))
            return@Column
        }
        val name = snapshot.nextMedicationName ?: stringResource(R.string.diagnostics_none)
        CardValue(
            stringResource(
                R.string.diagnostics_next_line,
                name,
                toLocal(next).nextReminderMoment(today),
                stringResource(R.string.diagnostics_next_plan),
            ),
        )
        // Пояснение системного индикатора будильника стоит там, где человек смотрит
        // на следующее напоминание (НАП-42, ОВ-110): без него признак в статус-баре
        // выглядит дефектом, а не выбранным механизмом.
        CardSubtitle(stringResource(R.string.diagnostics_next_alarm_indicator))
    }
}

/** Постоянный низ: «Сообщить о проблеме» с подписью, виден в каждом базовом состоянии (AC-29). */
@Composable
private fun ReportFooter(onReportOpen: () -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_REPORT_FOOTER),
    ) {
        // Верхняя линия постоянного низа (`.report{border-top}`, ОВ-144).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SklyankaSizes.BorderThin)
                .background(colors.line),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SklyankaSpacing.Block),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
        ) {
            QuietButton(
                label = ButtonLabel(
                    text = stringResource(R.string.diagnostics_report_open),
                    // Декоративный ведущий глиф: смысл несёт подпись, чтением с экрана
                    // глиф не объявляется (ОВ-144).
                    leadingGlyph = {
                        ChatGlyph(
                            color = colors.accent,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    },
                ),
                onClick = onReportOpen,
            )
            Text(
                text = stringResource(R.string.diagnostics_report_footer_note),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
                // Подпись низа — единственная центрируемая по строкам (`.report .bs{text-align:center}`):
                // на широком экране и при крупном шрифте перенос выравнивается по центру (ОВ-144).
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CardTitle(title: String) {
    Text(text = title, style = SklyankaType.BodyDefaultStrong, color = SklyankaTheme.colors.text)
}

@Composable
private fun CardValue(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = SklyankaType.BodyDefault, color = SklyankaTheme.colors.text, modifier = modifier)
}

@Composable
private fun CardSubtitle(text: String) {
    Text(text = text, style = SklyankaType.CaptionSmall, color = SklyankaTheme.colors.textMuted)
}

/** Отклонение словами: «вовремя» или «с опозданием на N мин» (ОВ-49). */
@Composable
private fun deviationWord(deviationSeconds: Long): String = if (ReminderDeviationWords.isOnTime(deviationSeconds)) {
    stringResource(R.string.diagnostics_on_time_word)
} else {
    val minutes = ReminderDeviationWords.lateMinutes(deviationSeconds).toInt()
    pluralStringResource(R.plurals.diagnostics_late, minutes, minutes)
}

const val TAG_DIAGNOSTICS = "diagnostics"
const val TAG_VERDICT = "diagnostics_verdict"
const val TAG_WARNING = "diagnostics_warning"
const val TAG_OUTAGE = "diagnostics_outage"
const val TAG_PERMISSIONS = "diagnostics_permissions"
const val TAG_PERMISSION_GROUP = "diagnostics_permission_group"
const val TAG_PERMISSION_ROW = "diagnostics_permission_row"
const val TAG_PERMISSION_SEPARATOR = "diagnostics_permission_separator"
const val TAG_PERMISSION_MARKER = "diagnostics_permission_marker"
const val TAG_PERMISSION_STATE = "diagnostics_permission_state"
const val TAG_PERMISSION_CONSEQUENCE = "diagnostics_permission_consequence"
const val TAG_ALL_GRANTED = "diagnostics_all_granted"
const val TAG_PERMISSION_REST_GRANTED = "diagnostics_permission_rest_granted"
const val TAG_FIX = "diagnostics_fix"
const val TAG_FIX_FALLBACK = "diagnostics_fix_fallback"
const val TAG_RESTART_SETUP = "diagnostics_restart_setup"
const val TAG_CHECK_BLOCK = "diagnostics_check_block"
const val TAG_CHECK_DELIVERY = "diagnostics_check_delivery"
const val TAG_CHECK_RUNNING = "diagnostics_check_running"
const val TAG_CHECK_CANCEL = "diagnostics_check_cancel"
const val TAG_CHECK_RESULT = "diagnostics_check_result"
const val TAG_CHECK_RESULT_ACKNOWLEDGE = "diagnostics_check_acknowledge"
const val TAG_CHECK_FIX = "diagnostics_check_fix"
const val TAG_WEEK_SUMMARY = "diagnostics_week_summary"
const val TAG_LAST_SHORT = "diagnostics_last_short"
const val TAG_NEXT_SHORT = "diagnostics_next_short"
const val TAG_REPORT_FOOTER = "diagnostics_report_footer"
const val TAG_REPORT_SHEET = "diagnostics_report_sheet"
const val TAG_REPORT_COMMENT = "diagnostics_report_comment"
const val TAG_REPORT_SEND = "diagnostics_report_send"
const val TAG_REPORT_SENT = "diagnostics_report_sent"
const val TAG_REPORT_FAILED = "diagnostics_report_failed"
const val TAG_REPORT_RETRY = "diagnostics_report_retry"
const val TAG_REPORT_DISMISS = "diagnostics_report_dismiss"

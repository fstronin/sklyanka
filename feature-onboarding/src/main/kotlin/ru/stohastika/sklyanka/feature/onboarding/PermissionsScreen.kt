// Файл экрана и его небольших функций-переводчиков: «слишком много функций» здесь
// означало бы дробление карточек по файлам, а не связанность.
@file:Suppress("TooManyFunctions")

package ru.stohastika.sklyanka.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.domain.onboarding.ManualStepHint
import ru.stohastika.sklyanka.domain.onboarding.ManualStepState
import ru.stohastika.sklyanka.domain.onboarding.NotificationAccessState
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionGrant
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepAction
import ru.stohastika.sklyanka.domain.onboarding.StepState
import ru.stohastika.sklyanka.domain.onboarding.actionFor
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.OnboardingSteps
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.SklyankaSwitchRow
import ru.stohastika.sklyanka.ui.component.StackingRow
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Экран «Настроить надёжность» (НАП-19 … НАП-21, ОВ-106, ОВ-107).
 *
 * Развитие экрана разрешений EMUI, а не второй экран: на Huawei показываются пять шагов
 * (два разрешения Android и три настройки EMUI), на остальных телефонах — только два общих.
 * Состав и тексты — из `design/screens/Onboarding.dc.html` и `docs/spec/03-screens.md`;
 * единственное объявленное отступление — подпись «Уведомлений» без слов «и на часах»
 * (`ADR-0009`, интеграция с часами отложена).
 *
 * Верхний блок неизменен на всех входах: заголовок «Настроить надёжность»; индикатор шагов
 * онбординга — только при первом запуске (критерий приёмки 13). Плашка над списком появляется,
 * когда цепочка закрыта: нейтральная «Всё настроено» или предупреждение о перерыве (ОВ-107).
 * Футер выбирается по источнику: на первом запуске «Дальше» и «Пропустить», на остальных —
 * тихое «Готово» (`TDD.md §9.5`).
 *
 * Список — `LazyColumn` с фокусом: вход из баннера прокручивает экран к первому незакрытому
 * шагу цепочки (ОВ-107, `TDD.md §5.10`).
 */
@Composable
fun PermissionsScreen(
    state: OnboardingUiState,
    actions: PermissionsActions,
    toLocal: (Instant) -> LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val listState = rememberLazyListState()
    // Один раз за вход: фокус приходит из модели после чтения системы и не повторяется
    // на каждом возвращении из системных настроек (TDD §5.10).
    LaunchedEffect(state.focusStep) {
        val focus = state.focusStep ?: return@LaunchedEffect
        val index = state.steps.indexOfFirst { (step, _) -> step == focus }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .testTag(TAG_PERMISSIONS_SCREEN),
    ) {
        Heading(state)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = SklyankaSpacing.Screen)
                .testTag(TAG_STEPS_LIST),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            if (state.allConfigured) {
                item(key = "plaque") { ConfiguredPlaque(state, actions, toLocal) }
            }
            items(state.steps, key = { (step, _) -> step.name }) { (step, stepState) ->
                StepCard(
                    step = step,
                    state = stepState,
                    configured = state.allConfigured,
                    instructions = state.instructions,
                    actions = actions,
                )
            }
            if (state.revoked.isNotEmpty()) {
                item(key = "revoked") { Revocations(state) }
            }
        }
        Footer(state.source, actions)
    }
}

/** Индикатор шагов, заголовок и пояснение — верхний блок экрана. */
@Composable
private fun Heading(state: OnboardingUiState) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier.padding(
            start = SklyankaSpacing.Screen,
            end = SklyankaSpacing.Screen,
            top = SklyankaSizes.HeaderTopPadding,
            bottom = SklyankaSpacing.Default,
        ),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        // Индикатор шагов принадлежит онбордингу, а не экрану: при входе из настроек
        // экран тот же, а индикатора нет (критерий приёмки 13).
        if (state.showStepIndicator) {
            // Индикатор — графика без видимого текста, и читалке он называется словами (НФТ-30).
            val steps = stringResource(R.string.cd_onboarding_steps, state.currentStep, state.totalSteps)
            OnboardingSteps(
                total = state.totalSteps,
                current = state.currentStep,
                modifier = Modifier.semantics { contentDescription = steps },
            )
        }
        Text(
            text = stringResource(R.string.setup_title),
            style = SklyankaType.Title,
            color = colors.text,
            modifier = Modifier.testTag(TAG_TITLE),
        )
        Text(
            text = stringResource(R.string.setup_intro),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
        )
    }
}

/**
 * Плашка над списком в закрытой цепочке (ОВ-107).
 *
 * Перерыв и самопроверка говорят одним действием «Проверить доставку» (SKL-86); до неё
 * действия нет — плашка остаётся текстом.
 */
@Composable
private fun ConfiguredPlaque(
    state: OnboardingUiState,
    actions: PermissionsActions,
    toLocal: (Instant) -> LocalDateTime,
) {
    val outage = state.outageSince
    if (outage == null) {
        Banner(
            text = stringResource(R.string.setup_all_configured),
            kind = BannerKind.Neutral,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_ALL_CONFIGURED),
        )
    } else {
        Banner(
            text = stringResource(R.string.setup_outage_warning, toLocal(outage).journalMoment()),
            kind = BannerKind.Warning,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_OUTAGE_WARNING),
        )
    }
    // Действие появится вместе с самопроверкой доставки (SKL-86); сейчас лямбда `null`.
    actions.onCheckDelivery?.let { onCheckDelivery ->
        QuietButton(
            text = stringResource(R.string.setup_check_delivery),
            onClick = onCheckDelivery,
            modifier = Modifier.testTag(TAG_CHECK_DELIVERY),
        )
    }
}

/**
 * Карточка одного шага.
 *
 * Прочитанное системой разрешение — обычная рамка, отметка «Есть» и никакой кнопки;
 * невыданное — акцентная рамка, строка-причина и кнопка действия (критерии 2 и 3).
 * Вендорский шаг не зеленеет сам: пока человек не подтвердил настройку, рамка акцентная,
 * а отметка «Подтверждено вами» появляется только от переключателя (НАП-20, критерий 4).
 */
@Composable
private fun StepCard(
    step: PermissionStep,
    state: StepState,
    configured: Boolean,
    instructions: Set<PermissionStep>,
    actions: PermissionsActions,
) {
    val colors = SklyankaTheme.colors
    val resolved = state.isResolved
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(
                width = if (resolved) SklyankaSizes.BorderThin else SklyankaSizes.BorderAccent,
                color = if (resolved) colors.lineOnSurface else colors.accentBorder,
                shape = SklyankaShapes.Card,
            )
            .padding(SklyankaSpacing.Block)
            .testTag(tagOf(step)),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        StepHeader(step, state)
        ExactAlarmsExplanation(step, state)
        NotificationIssue(state)
        if (state is ManualStepState) {
            VendorExtras(
                step = step,
                state = state,
                configured = configured,
                instructions = instructions,
                actions = actions,
            )
        }
        // В состоянии «Всё настроено» кнопок нет ни на одной карточке (критерий приёмки 7).
        if (!configured) StepActionButton(step, state, actions)
    }
}

/** Шапка карточки: значок, название, подписи и отметка состояния. */
@Composable
private fun StepHeader(step: PermissionStep, state: StepState) {
    val colors = SklyankaTheme.colors
    val resolved = state.isResolved
    StackingRow(
        leading = { IconPlate(step, resolved) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
                Text(text = stringResource(titleOf(step)), style = SklyankaType.BodyDefaultStrong, color = colors.text)
                captionOf(step)?.let { caption ->
                    Text(text = stringResource(caption), style = SklyankaType.Caption, color = colors.textMuted)
                }
                stepExtraAction(step)?.let { extra ->
                    Text(text = stringResource(extra), style = SklyankaType.Caption, color = colors.textMuted)
                }
            }
        },
        action = {
            // «Есть» — только про то, что прочитала система; вендорские шаги так не отмечаются.
            if (state.readBySystemOrResolvedNotification()) GrantedMark()
            if (state is ManualStepState && resolved) ConfirmedMark()
        },
        gap = SklyankaSpacing.Medium,
        minTextWidth = SklyankaSizes.MinTextColumn,
    )
}

/** Читается ли состояние шага системой — у «Уведомлений» это ещё и канал с полноэкранным правом. */
private fun StepState.readBySystemOrResolvedNotification(): Boolean =
    this is PermissionGrant.Granted || this is NotificationAccessState && isResolved

/** Системный индикатор будильника объясняется там, где выдаётся право (НАП-42, ОВ-110). */
@Composable
private fun ExactAlarmsExplanation(step: PermissionStep, state: StepState) {
    if (step != PermissionStep.EXACT_ALARMS || state !is PermissionGrant.Granted) return
    Text(
        text = stringResource(R.string.setup_exact_alarms_indicator),
        style = SklyankaType.Caption,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_EXACT_ALARMS_INDICATOR),
    )
}

/** Строка-причина карточки «Уведомления» — теми же словами, что баннер и диагностика (ОВ-107). */
@Composable
private fun NotificationIssue(state: StepState) {
    val issue = (state as? NotificationAccessState)?.issue ?: return
    val colors = SklyankaTheme.colors
    Text(
        text = stringResource(issueOf(issue)),
        style = SklyankaType.Caption,
        color = colors.onAccentSoft,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accentSoft, SklyankaShapes.Medium)
            .padding(
                horizontal = SklyankaSizes.BannerPaddingHorizontal,
                vertical = SklyankaSizes.BannerPaddingVertical,
            )
            .testTag(TAG_ISSUE),
    )
}

/** Кнопка действия шага; показывается только вне состояния «Всё настроено». */
@Composable
private fun StepActionButton(step: PermissionStep, state: StepState, actions: PermissionsActions) {
    val action = actionFor(step, state) ?: return
    PrimaryButton(
        text = stringResource(actionLabelOf(step, action)),
        onClick = { actions.onStepAction(step) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(actionTagOf(step)),
    )
}

/** Всё, чего нет у шагов-разрешений: непроверяемость, косвенные признаки, инструкция и подтверждение. */
@Composable
private fun VendorExtras(
    step: PermissionStep,
    state: ManualStepState,
    configured: Boolean,
    instructions: Set<PermissionStep>,
    actions: PermissionsActions,
) {
    val colors = SklyankaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        Text(
            text = stringResource(unverifiableOf(step)),
            style = SklyankaType.Caption,
            color = colors.onAccentSoft,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentSoft, SklyankaShapes.Medium)
                .padding(
                    horizontal = SklyankaSizes.BannerPaddingHorizontal,
                    vertical = SklyankaSizes.BannerPaddingVertical,
                )
                .testTag(TAG_UNVERIFIABLE),
        )
        state.hints.sortedBy(ManualStepHint::ordinal).forEach { hint -> HintRow(hint) }
        // Инструкция остаётся видимой после возврата, пока шаг не подтверждён (критерий 6).
        if (step in instructions) {
            Text(
                text = stringResource(fallbackOf(step)),
                style = SklyankaType.Caption,
                color = colors.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.neutralSoft, SklyankaShapes.Medium)
                    .padding(
                        horizontal = SklyankaSizes.BannerPaddingHorizontal,
                        vertical = SklyankaSizes.BannerPaddingVertical,
                    )
                    .testTag(TAG_INSTRUCTIONS),
            )
        }
        if (!configured) {
            SklyankaSwitchRow(
                label = stringResource(confirmLabelOf(step)),
                caption = stringResource(R.string.setup_manual_confirm_caption),
                checked = state.confirmedByUser,
                onCheckedChange = { confirmed -> actions.onManualConfirmed(step, confirmed) },
                modifier = Modifier.testTag(confirmTagOf(step)),
            )
        }
    }
}

/** Косвенный признак: слово «косвенный» стоит в самой строке, а не в оформлении (НАП-20). */
@Composable
private fun HintRow(hint: ManualStepHint) {
    val colors = SklyankaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        modifier = Modifier.testTag(TAG_HINT),
    ) {
        val label = stringResource(R.string.cd_onboarding_hint)
        InfoGlyph(color = colors.textMuted, modifier = Modifier.semantics { contentDescription = label })
        Text(
            text = stringResource(
                when (hint) {
                    ManualStepHint.BATTERY_OPTIMIZATION_IGNORED -> R.string.setup_hint_battery
                },
            ),
            style = SklyankaType.Caption,
            color = colors.textMuted,
        )
    }
}

/** Плашка значка: подкраска говорит о состоянии вторым каналом рядом с отметкой (НФТ-33). */
@Composable
private fun IconPlate(step: PermissionStep, resolved: Boolean) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = Modifier
            .size(SklyankaSizes.IconPlate)
            .background(if (resolved) colors.takenSoft else colors.accentSoft, SklyankaShapes.Button),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (resolved) colors.taken else colors.accent
        val label = stringResource(glyphLabelOf(step))
        val glyph = Modifier.semantics { contentDescription = label }
        when (step) {
            PermissionStep.NOTIFICATIONS -> BellGlyph(color = tint, modifier = glyph)
            PermissionStep.EXACT_ALARMS -> AlarmClockGlyph(color = tint, modifier = glyph)
            PermissionStep.APP_STARTUP -> BoltGlyph(color = tint, modifier = glyph)
            PermissionStep.BATTERY_SAVING -> BatteryGlyph(color = tint, modifier = glyph)
            PermissionStep.RECENTS_PIN -> PinGlyph(color = tint, modifier = glyph)
        }
    }
}

/** Отметка «Есть»: галочка плюс слово — цвет не единственный носитель смысла (НФТ-33). */
@Composable
private fun GrantedMark() {
    val colors = SklyankaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag(TAG_GRANTED_MARK),
    ) {
        val label = stringResource(R.string.cd_onboarding_granted)
        CheckGlyph(
            color = colors.taken,
            size = SklyankaSizes.BannerIcon,
            modifier = Modifier.semantics { contentDescription = label },
        )
        Text(
            text = stringResource(R.string.onboarding_state_granted),
            style = SklyankaType.CaptionStrong,
            color = colors.taken,
        )
    }
}

/** Отметка «Подтверждено вами»: слово человека, а не измерение системы (НАП-20). */
@Composable
private fun ConfirmedMark() {
    val colors = SklyankaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag(TAG_CONFIRMED_MARK),
    ) {
        val label = stringResource(R.string.cd_setup_confirmed)
        CheckGlyph(
            color = colors.taken,
            size = SklyankaSizes.BannerIcon,
            modifier = Modifier.semantics { contentDescription = label },
        )
        Text(
            text = stringResource(R.string.setup_confirmed_by_user),
            style = SklyankaType.CaptionStrong,
            color = colors.taken,
        )
    }
}

/** Обнаруженный отзыв разрешения или снятие подтверждения. */
@Composable
private fun Revocations(state: OnboardingUiState) {
    state.revoked.forEach { step ->
        Banner(
            text = stringResource(revokedTextOf(step)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_REVOKED),
        )
    }
}

/** Футер выбирается по источнику: первый запуск — онбординг, остальные входы — тихое «Готово». */
@Composable
private fun Footer(source: OnboardingSource, actions: PermissionsActions) {
    Column(
        modifier = Modifier.padding(
            start = SklyankaSpacing.Screen,
            end = SklyankaSpacing.Screen,
            top = SklyankaSpacing.Block,
            bottom = SklyankaSpacing.Screen,
        ),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (source == OnboardingSource.FIRST_LAUNCH) {
            PrimaryButton(
                text = stringResource(R.string.setup_action_next),
                onClick = actions.onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_NEXT),
            )
            // Пропуск — легальный путь, а не ошибка (НАП-21), поэтому тихая кнопка, но она есть
            // всегда и не прячется за «ещё» ни в одном состоянии.
            QuietButton(
                text = stringResource(R.string.setup_action_skip),
                onClick = actions.onSkip,
                kind = QuietButtonKind.Muted,
                modifier = Modifier.testTag(TAG_SKIP),
            )
        } else {
            QuietButton(
                text = stringResource(R.string.setup_done),
                onClick = actions.onDone,
                kind = QuietButtonKind.Muted,
                modifier = Modifier.testTag(TAG_DONE),
            )
        }
    }
}

/**
 * Действия экрана.
 *
 * Собраны в один объект, а не переданы шестью лямбдами: экран, принимающий шесть функций,
 * теряет одну при первой правке, и её отсутствие не заметит ни компилятор, ни ревью.
 */
data class PermissionsActions(
    val onStepAction: (PermissionStep) -> Unit,
    val onManualConfirmed: (PermissionStep, Boolean) -> Unit,
    val onNext: () -> Unit,
    val onSkip: () -> Unit,
    val onDone: () -> Unit,
    /** «Проверить доставку» в плашке «Всё настроено»; `null` — самопроверки ещё нет (SKL-86). */
    val onCheckDelivery: (() -> Unit)? = null,
)

private fun titleOf(step: PermissionStep): Int = when (step) {
    PermissionStep.NOTIFICATIONS -> R.string.setup_step_notifications
    PermissionStep.EXACT_ALARMS -> R.string.setup_step_exact_alarms
    PermissionStep.APP_STARTUP -> R.string.setup_step_app_startup
    PermissionStep.BATTERY_SAVING -> R.string.setup_step_battery_saving
    PermissionStep.RECENTS_PIN -> R.string.setup_step_recents_pin
}

/** Подпись шага; у «Энергосбережения» её заменяет плашка непроверяемости из спецификации. */
private fun captionOf(step: PermissionStep): Int? = when (step) {
    PermissionStep.NOTIFICATIONS -> R.string.setup_step_notifications_caption
    PermissionStep.EXACT_ALARMS -> R.string.setup_step_exact_alarms_caption
    PermissionStep.APP_STARTUP -> R.string.setup_step_app_startup_caption
    PermissionStep.BATTERY_SAVING -> null
    PermissionStep.RECENTS_PIN -> R.string.setup_step_recents_pin_caption
}

/** Вторая строка шага «Запуск приложений»: что именно включить (ОВ-106). */
private fun stepExtraAction(step: PermissionStep): Int? = when (step) {
    PermissionStep.APP_STARTUP -> R.string.setup_step_app_startup_action
    else -> null
}

private fun unverifiableOf(step: PermissionStep): Int = when (step) {
    PermissionStep.APP_STARTUP -> R.string.setup_unverifiable_app_startup
    PermissionStep.BATTERY_SAVING -> R.string.setup_unverifiable_battery_saving
    PermissionStep.RECENTS_PIN -> R.string.setup_unverifiable_recents_pin
    PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> R.string.setup_unverifiable_app_startup
}

private fun fallbackOf(step: PermissionStep): Int = when (step) {
    PermissionStep.BATTERY_SAVING -> R.string.setup_fallback_battery_saving
    else -> R.string.setup_fallback_app_startup
}

private fun confirmLabelOf(step: PermissionStep): Int = when (step) {
    PermissionStep.APP_STARTUP -> R.string.setup_manual_confirm_app_startup
    PermissionStep.BATTERY_SAVING -> R.string.setup_manual_confirm_battery_saving
    PermissionStep.RECENTS_PIN -> R.string.setup_manual_confirm_recents_pin
    PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> R.string.setup_manual_confirm_app_startup
}

private fun issueOf(issue: ReminderWarning): Int = when (issue) {
    ReminderWarning.NOTIFICATIONS_OFF -> R.string.setup_issue_notifications
    ReminderWarning.NOTIFICATION_CHANNEL_OFF -> R.string.setup_issue_channel
    ReminderWarning.FULL_SCREEN_INTENT_OFF -> R.string.setup_issue_full_screen
    // Точные будильники и пара — причины баннера, у карточки «Уведомления» их быть не может.
    ReminderWarning.EXACT_ALARMS_OFF, ReminderWarning.BOTH_OFF -> R.string.setup_issue_notifications
}

private fun revokedTextOf(step: PermissionStep): Int = when (step) {
    PermissionStep.NOTIFICATIONS -> R.string.setup_revoked_notifications
    PermissionStep.EXACT_ALARMS -> R.string.setup_revoked_exact_alarms
    PermissionStep.APP_STARTUP -> R.string.setup_revoked_app_startup
    PermissionStep.BATTERY_SAVING -> R.string.setup_revoked_battery_saving
    PermissionStep.RECENTS_PIN -> R.string.setup_revoked_recents_pin
}

/**
 * Подпись кнопки — из спецификации (`03-screens.md`).
 *
 * У точных будильников кнопка всегда «Разрешить»: системного диалога у права нет, но человек
 * идёт именно выдавать разрешение, и макет называет кнопку так. У канала и полноэкранного
 * права диалога тоже нет, и там кнопка ведёт в настройки — «Открыть настройки».
 */
private fun actionLabelOf(step: PermissionStep, action: StepAction): Int = when {
    action == StepAction.OPEN_VENDOR_STEP -> R.string.setup_action_open_settings
    step == PermissionStep.EXACT_ALARMS && action == StepAction.OPEN_SYSTEM_SETTINGS -> R.string.setup_action_allow
    action == StepAction.REQUEST_PERMISSION -> R.string.setup_action_allow
    else -> R.string.setup_action_open_settings
}

private fun glyphLabelOf(step: PermissionStep): Int = when (step) {
    PermissionStep.NOTIFICATIONS -> R.string.cd_onboarding_notifications
    PermissionStep.EXACT_ALARMS -> R.string.cd_onboarding_exact_alarms
    PermissionStep.APP_STARTUP -> R.string.cd_setup_app_startup
    PermissionStep.BATTERY_SAVING -> R.string.cd_setup_battery_saving
    PermissionStep.RECENTS_PIN -> R.string.cd_setup_recents_pin
}

private fun tagOf(step: PermissionStep): String = when (step) {
    PermissionStep.NOTIFICATIONS -> TAG_CARD_NOTIFICATIONS
    PermissionStep.EXACT_ALARMS -> TAG_CARD_EXACT_ALARMS
    PermissionStep.APP_STARTUP -> TAG_CARD_APP_STARTUP
    PermissionStep.BATTERY_SAVING -> TAG_CARD_BATTERY_SAVING
    PermissionStep.RECENTS_PIN -> TAG_CARD_RECENTS_PIN
}

private fun actionTagOf(step: PermissionStep): String = when (step) {
    PermissionStep.NOTIFICATIONS -> TAG_ACTION_NOTIFICATIONS
    PermissionStep.EXACT_ALARMS -> TAG_ACTION_EXACT_ALARMS
    PermissionStep.APP_STARTUP -> TAG_ACTION_APP_STARTUP
    PermissionStep.BATTERY_SAVING -> TAG_ACTION_BATTERY_SAVING
    PermissionStep.RECENTS_PIN -> TAG_ACTION_RECENTS_PIN
}

private fun confirmTagOf(step: PermissionStep): String = when (step) {
    PermissionStep.APP_STARTUP -> TAG_CONFIRM_APP_STARTUP
    PermissionStep.BATTERY_SAVING -> TAG_CONFIRM_BATTERY_SAVING
    PermissionStep.RECENTS_PIN -> TAG_CONFIRM_RECENTS_PIN
    PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> TAG_CONFIRM_APP_STARTUP
}

/** Момент перерыва на плашке: «1 октября, 07:30» — тем же форматом, что у сообщения о перерыве. */
@Composable
private fun LocalDateTime.journalMoment(): String = format(
    // Локаль указана явно: `ofPattern` без неё берёт локаль процесса, а она остаётся
    // локалью телефона и после закрепления конфигурации (`ADR-0008`).
    DateTimeFormatter.ofPattern(stringResource(R.string.setup_outage_moment_pattern), interfaceLocale()),
)

const val TAG_PERMISSIONS_SCREEN = "permissions_screen"
const val TAG_STEPS_LIST = "permissions_steps_list"
const val TAG_TITLE = "permissions_title"
const val TAG_CARD_NOTIFICATIONS = "permissions_card_notifications"
const val TAG_CARD_EXACT_ALARMS = "permissions_card_exact_alarms"
const val TAG_CARD_APP_STARTUP = "permissions_card_app_startup"
const val TAG_CARD_BATTERY_SAVING = "permissions_card_battery_saving"
const val TAG_CARD_RECENTS_PIN = "permissions_card_recents_pin"
const val TAG_ACTION_NOTIFICATIONS = "permissions_action_notifications"
const val TAG_ACTION_EXACT_ALARMS = "permissions_action_exact_alarms"
const val TAG_ACTION_APP_STARTUP = "permissions_action_app_startup"
const val TAG_ACTION_BATTERY_SAVING = "permissions_action_battery_saving"
const val TAG_ACTION_RECENTS_PIN = "permissions_action_recents_pin"
const val TAG_GRANTED_MARK = "permissions_granted_mark"
const val TAG_CONFIRMED_MARK = "permissions_confirmed_mark"
const val TAG_UNVERIFIABLE = "permissions_unverifiable"
const val TAG_INSTRUCTIONS = "permissions_instructions"
const val TAG_CONFIRM_APP_STARTUP = "permissions_confirm_app_startup"
const val TAG_CONFIRM_BATTERY_SAVING = "permissions_confirm_battery_saving"
const val TAG_CONFIRM_RECENTS_PIN = "permissions_confirm_recents_pin"
const val TAG_HINT = "permissions_hint"
const val TAG_ISSUE = "permissions_issue"
const val TAG_ALL_CONFIGURED = "permissions_all_configured"
const val TAG_OUTAGE_WARNING = "permissions_outage_warning"
const val TAG_CHECK_DELIVERY = "permissions_check_delivery"
const val TAG_REVOKED = "permissions_revoked"
const val TAG_EXACT_ALARMS_INDICATOR = "permissions_exact_alarms_indicator"
const val TAG_NEXT = "permissions_next"
const val TAG_SKIP = "permissions_skip"
const val TAG_DONE = "permissions_done"

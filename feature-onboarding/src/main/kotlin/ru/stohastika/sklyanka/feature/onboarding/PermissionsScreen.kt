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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.domain.onboarding.AutostartHint
import ru.stohastika.sklyanka.domain.onboarding.AutostartState
import ru.stohastika.sklyanka.domain.onboarding.PermissionGrant
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepState
import ru.stohastika.sklyanka.domain.onboarding.actionFor
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.OnboardingSteps
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.SklyankaSwitchRow
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Экран разрешений EMUI (НАП-19 … НАП-21, ПЛ-18).
 *
 * Состав и тексты — из `design/screens/Onboarding.dc.html` (критерий приёмки 21). Два
 * отступления от макета, каждое объявлено вслух:
 *
 * 1. **Подтверждение автозапуска переключателем** (ОВ-53). НАП-20 требует «предложить
 *    подтвердить выполнение вручную»; переключатель, а не кнопка, потому что подтверждение
 *    обязано сниматься.
 * 2. **Кнопка «Дальше» цвета акцента, а не тёмная.** В макете она `#241F1B`; роли «тёмная
 *    кнопка» в дизайн-системе нет (`docs/spec/08-design-system.md` §4.3), а завести её здесь
 *    значило бы придумать токен по месту (НФТ-47).
 */
@Composable
fun PermissionsScreen(state: OnboardingUiState, actions: PermissionsActions, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .testTag(TAG_PERMISSIONS_SCREEN),
    ) {
        Heading(state)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SklyankaSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            state.steps.forEach { (step, stepState) ->
                StepCard(
                    step = step,
                    state = stepState,
                    actions = actions,
                    showInstructions = state.showAutostartInstructions,
                )
            }
            Revocations(state)
            if (state.checklist?.remindersUnreliable == true) {
                Banner(
                    text = stringResource(R.string.onboarding_unreliable),
                    kind = BannerKind.Neutral,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_UNRELIABLE),
                )
            }
        }
        Footer(actions)
    }
}

/** Индикатор шагов, заголовок и пояснение — верхний блок макета. */
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
        // экран тот же, а индикатора нет (критерий приёмки 15).
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
            text = stringResource(R.string.onboarding_title),
            style = SklyankaType.Title,
            color = colors.text,
            modifier = Modifier.testTag(TAG_TITLE),
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
        )
    }
}

/**
 * Карточка одного шага.
 *
 * Выданное разрешение — обычная рамка, отметка «Есть» и никакой кнопки; невыданное —
 * акцентная рамка и кнопка действия (критерии приёмки 2 и 3). У шага автозапуска рамка
 * акцентная всегда, пока человек не подтвердил настройку: система об этом шаге не отвечает,
 * и «обычная рамка» была бы утверждением, которого приложение не делало (НАП-20).
 */
@Composable
private fun StepCard(step: PermissionStep, state: StepState, actions: PermissionsActions, showInstructions: Boolean) {
    val colors = SklyankaTheme.colors
    val resolved = state.isResolved
    val granted = state is PermissionGrant.Granted
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
        Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium)) {
            IconPlate(step, granted)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
            ) {
                Text(text = stringResource(titleOf(step)), style = SklyankaType.BodyDefaultStrong, color = colors.text)
                Text(
                    text = stringResource(captionOf(step)),
                    style = SklyankaType.Caption,
                    color = colors.textMuted,
                )
            }
            // «Есть» — только про разрешение Android: про автозапуск система не отвечает.
            if (granted) GrantedMark()
        }
        if (step == PermissionStep.AUTOSTART && state is AutostartState) {
            AutostartExtras(state, actions, showInstructions)
        }
        if (actionFor(step, state) != null) {
            PrimaryButton(
                text = stringResource(actionLabelOf(step)),
                onClick = { actions.onStepAction(step) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(actionTagOf(step)),
            )
        }
    }
}

/** Плашка значка: подкраска говорит о состоянии вторым каналом рядом с отметкой (НФТ-33). */
@Composable
private fun IconPlate(step: PermissionStep, granted: Boolean) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = Modifier
            .size(SklyankaSizes.IconPlate)
            .background(if (granted) colors.takenSoft else colors.accentSoft, SklyankaShapes.Button),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (granted) colors.taken else colors.accent
        val label = stringResource(glyphLabelOf(step))
        val glyph = Modifier.semantics { contentDescription = label }
        when (step) {
            PermissionStep.NOTIFICATIONS -> BellGlyph(color = tint, modifier = glyph)
            PermissionStep.EXACT_ALARMS -> AlarmClockGlyph(color = tint, modifier = glyph)
            PermissionStep.AUTOSTART -> BoltGlyph(color = tint, modifier = glyph)
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

/**
 * Всё, чего нет у шагов-разрешений: непроверяемость, косвенные признаки, ручное
 * подтверждение и запасной путь (НАП-20, ПЛ-18).
 */
@Composable
private fun AutostartExtras(state: AutostartState, actions: PermissionsActions, showInstructions: Boolean) {
    val colors = SklyankaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        Text(
            text = stringResource(R.string.onboarding_autostart_unverifiable),
            style = SklyankaType.Caption,
            color = colors.onAccentSoft,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentSoft, SklyankaShapes.Medium)
                .padding(
                    horizontal = SklyankaSizes.BannerPaddingHorizontal,
                    vertical = SklyankaSizes.BannerPaddingVertical,
                )
                .testTag(TAG_AUTOSTART_UNVERIFIABLE),
        )
        state.hints.sortedBy(AutostartHint::ordinal).forEach { hint -> HintRow(hint) }
        if (showInstructions) {
            Text(
                text = stringResource(R.string.onboarding_autostart_instructions),
                style = SklyankaType.Caption,
                color = colors.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.neutralSoft, SklyankaShapes.Medium)
                    .padding(
                        horizontal = SklyankaSizes.BannerPaddingHorizontal,
                        vertical = SklyankaSizes.BannerPaddingVertical,
                    )
                    .testTag(TAG_AUTOSTART_INSTRUCTIONS),
            )
        }
        SklyankaSwitchRow(
            label = stringResource(R.string.onboarding_autostart_confirm),
            caption = stringResource(R.string.onboarding_autostart_confirm_caption),
            checked = state.confirmedByUser,
            onCheckedChange = actions.onAutostartConfirmed,
            modifier = Modifier.testTag(TAG_AUTOSTART_CONFIRM),
        )
    }
}

/** Косвенный признак: слово «косвенный» стоит в самой строке, а не в оформлении (НАП-20). */
@Composable
private fun HintRow(hint: AutostartHint) {
    val colors = SklyankaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        modifier = Modifier.testTag(TAG_AUTOSTART_HINT),
    ) {
        val label = stringResource(R.string.cd_onboarding_hint)
        InfoGlyph(color = colors.textMuted, modifier = Modifier.semantics { contentDescription = label })
        Text(
            text = stringResource(
                when (hint) {
                    AutostartHint.BATTERY_OPTIMIZATION_IGNORED -> R.string.onboarding_hint_battery
                },
            ),
            style = SklyankaType.Caption,
            color = colors.textMuted,
        )
    }
}

/** Обнаруженный отзыв ранее выданного разрешения (критерий приёмки 17). */
@Composable
private fun Revocations(state: OnboardingUiState) {
    state.revoked.forEach { step ->
        Banner(
            text = stringResource(
                when (step) {
                    PermissionStep.NOTIFICATIONS -> R.string.onboarding_revoked_notifications
                    PermissionStep.EXACT_ALARMS -> R.string.onboarding_revoked_exact_alarms
                    PermissionStep.AUTOSTART -> R.string.onboarding_revoked_autostart
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_REVOKED),
        )
    }
}

/** «Дальше» и «Пропустить, настрою позже» — нижний блок макета. */
@Composable
private fun Footer(actions: PermissionsActions) {
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
        PrimaryButton(
            text = stringResource(R.string.onboarding_action_next),
            onClick = actions.onNext,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_NEXT),
        )
        // Пропуск — легальный путь, а не ошибка (НАП-21), поэтому тихая кнопка, но она есть
        // всегда и не прячется за «ещё» ни в одном состоянии.
        QuietButton(
            text = stringResource(R.string.onboarding_action_skip),
            onClick = actions.onSkip,
            muted = true,
            modifier = Modifier.testTag(TAG_SKIP),
        )
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
    val onAutostartConfirmed: (Boolean) -> Unit,
    val onNext: () -> Unit,
    val onSkip: () -> Unit,
)

private fun titleOf(step: PermissionStep): Int = when (step) {
    PermissionStep.NOTIFICATIONS -> R.string.onboarding_step_notifications
    PermissionStep.EXACT_ALARMS -> R.string.onboarding_step_exact_alarms
    PermissionStep.AUTOSTART -> R.string.onboarding_step_autostart
}

private fun captionOf(step: PermissionStep): Int = when (step) {
    PermissionStep.NOTIFICATIONS -> R.string.onboarding_step_notifications_caption
    PermissionStep.EXACT_ALARMS -> R.string.onboarding_step_exact_alarms_caption
    PermissionStep.AUTOSTART -> R.string.onboarding_step_autostart_caption
}

/**
 * Подпись кнопки действия — из макета и только из него.
 *
 * Она не зависит от того, чем именно добывается разрешение: у точных будильников кнопка
 * «Разрешить» ведёт на экран особого доступа, а не в диалог (ПЛ-14), и макет называет её
 * именно так. Подпись для невыданного разрешения на уведомления макетом не задана — там
 * оно нарисовано выданным; взята та же «Разрешить», потому что это и есть системный запрос.
 */
private fun actionLabelOf(step: PermissionStep): Int = when (step) {
    PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> R.string.onboarding_action_allow
    PermissionStep.AUTOSTART -> R.string.onboarding_action_open_settings
}

private fun glyphLabelOf(step: PermissionStep): Int = when (step) {
    PermissionStep.NOTIFICATIONS -> R.string.cd_onboarding_notifications
    PermissionStep.EXACT_ALARMS -> R.string.cd_onboarding_exact_alarms
    PermissionStep.AUTOSTART -> R.string.cd_onboarding_autostart
}

private fun tagOf(step: PermissionStep): String = when (step) {
    PermissionStep.NOTIFICATIONS -> TAG_CARD_NOTIFICATIONS
    PermissionStep.EXACT_ALARMS -> TAG_CARD_EXACT_ALARMS
    PermissionStep.AUTOSTART -> TAG_CARD_AUTOSTART
}

private fun actionTagOf(step: PermissionStep): String = when (step) {
    PermissionStep.NOTIFICATIONS -> TAG_ACTION_NOTIFICATIONS
    PermissionStep.EXACT_ALARMS -> TAG_ACTION_EXACT_ALARMS
    PermissionStep.AUTOSTART -> TAG_ACTION_AUTOSTART
}

const val TAG_PERMISSIONS_SCREEN = "permissions_screen"
const val TAG_TITLE = "permissions_title"
const val TAG_CARD_NOTIFICATIONS = "permissions_card_notifications"
const val TAG_CARD_EXACT_ALARMS = "permissions_card_exact_alarms"
const val TAG_CARD_AUTOSTART = "permissions_card_autostart"
const val TAG_ACTION_NOTIFICATIONS = "permissions_action_notifications"
const val TAG_ACTION_EXACT_ALARMS = "permissions_action_exact_alarms"
const val TAG_ACTION_AUTOSTART = "permissions_action_autostart"
const val TAG_GRANTED_MARK = "permissions_granted_mark"
const val TAG_AUTOSTART_UNVERIFIABLE = "permissions_autostart_unverifiable"
const val TAG_AUTOSTART_INSTRUCTIONS = "permissions_autostart_instructions"
const val TAG_AUTOSTART_CONFIRM = "permissions_autostart_confirm"
const val TAG_AUTOSTART_HINT = "permissions_autostart_hint"
const val TAG_UNRELIABLE = "permissions_unreliable"
const val TAG_REVOKED = "permissions_revoked"
const val TAG_NEXT = "permissions_next"
const val TAG_SKIP = "permissions_skip"

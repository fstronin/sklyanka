package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.ui.component.ButtonForm
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.SklyankaMark
import ru.stohastika.sklyanka.ui.component.StackingEqualButtons
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Полноэкранное напоминание о приёме (НАП-29, макет `design/screens/Reminder.dc.html`).
 *
 * Единственный тёмный экран первой версии: тема включает тёмную среду параметром, а не
 * системной настройкой — тёмными бывают напоминание и знак, а не приложение целиком
 * (`docs/spec/08-design-system.md` §5). Фон — роль `background` тёмного набора, то есть
 * токен `#191512`; макет рисует `#241F1B` (ОВ-9), и до ответа дизайна держится значение
 * спецификации, потому что правится оно в одной строке токенов, а не здесь.
 *
 * Состав и порядок — по макету: надзаголовок, время, знак лекарства, название, доза
 * и дозировка одной строкой, затем ровно три действия (НАП-30). Чип «Продублировано
 * на часы» из макета не выводится: интеграция отложена до пост-MVP (`ADR-0009`).
 * Подпись кнопки откладывания собирается из настройки, а не хранится строкой:
 * человек, поставивший 30 минут, обязан увидеть «Отложить 30 мин» — константа в этом месте
 * врёт о том, что кнопка сделает.
 */
@Composable
fun ReminderScreen(state: ReminderUiState.Card, actions: ReminderActions, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(horizontal = SklyankaSpacing.Screen)
            .testTag(TAG_REMINDER),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(state)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Mark()
            if (state.items.size == 1) {
                SingleIntake(state.items.first())
            } else {
                // Несколько приёмов на одно время — одна карточка со списком (НАП-36):
                // три полноэкранных уведомления подряд человек закрывает не глядя.
                MultipleIntakes(state.items, actions)
            }
        }
        Actions(state, actions)
    }
}

@Composable
private fun Header(state: ReminderUiState.Card) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier.padding(top = SklyankaSpacing.Screen * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.reminder_overline),
            style = SklyankaType.Overline,
            color = colors.textTertiary,
            modifier = Modifier.testTag(TAG_OVERLINE),
        )
        Text(
            text = state.scheduledAt.timeLabel(),
            style = SklyankaType.Display,
            color = colors.text,
            modifier = Modifier.testTag(TAG_TIME),
        )
        // Индикация чужого профиля (ПРФ-8): полная реализация — SKL-15, здесь подпись,
        // без которой карточка не отвечает на вопрос «чей это приём».
        state.foreignProfileName?.let { name ->
            Text(
                text = stringResource(ru.stohastika.sklyanka.ui.R.string.profile_label, name),
                style = SklyankaType.Caption,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_PROFILE),
            )
        }
    }
}

/** Знак лекарства в круге: то же место, что в макете, но знаком дизайн-системы. */
@Composable
private fun Mark() {
    val colors = SklyankaTheme.colors
    Box(
        modifier = Modifier
            .padding(bottom = SklyankaSpacing.Section)
            .size(SklyankaSizes.LockupMark)
            .background(colors.surface, SklyankaShapes.Pill),
        contentAlignment = Alignment.Center,
    ) {
        SklyankaMark(
            contentDescription = stringResource(ru.stohastika.sklyanka.ui.R.string.cd_app_mark),
            size = SklyankaSizes.EmptyStateMark,
        )
    }
}

@Composable
private fun SingleIntake(row: ReminderRow) {
    val colors = SklyankaTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(
            text = row.name,
            style = SklyankaType.Headline,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(TAG_NAME),
        )
        Text(
            text = row.doseLine(),
            style = SklyankaType.BodyLarge,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(TAG_DOSE),
        )
    }
}

@Composable
private fun MultipleIntakes(rows: List<ReminderRow>, actions: ReminderActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        rows.forEach { row -> IntakeRow(row, actions) }
    }
}

/**
 * Строка общей карточки со своими действиями.
 *
 * Отметка каждой строки независима (НАП-36): нижние кнопки относятся ко всем неотмеченным
 * приёмам сразу, а эти — ровно к одному, иначе человек с двумя лекарствами на 14:00 не может
 * принять одно и пропустить другое.
 */
@Composable
private fun IntakeRow(row: ReminderRow, actions: ReminderActions) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_ROW),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(text = row.name, style = SklyankaType.Subtitle, color = colors.text)
        Text(text = row.doseLine(), style = SklyankaType.Body, color = colors.textMuted)
        // Пара строки — карточная форма: кнопки внутри карточки делят её пополам
        // и одного роста 44 dp (ОВ-90, §4.3).
        StackingEqualButtons(
            first = {
                PrimaryButton(
                    text = stringResource(R.string.reminder_row_taken),
                    onClick = { actions.onTaken(row.intakeId) },
                    form = ButtonForm.Card,
                )
            },
            second = {
                SecondaryButton(
                    text = stringResource(R.string.reminder_row_skip),
                    onClick = { actions.onSkipped(row.intakeId) },
                    form = ButtonForm.Card,
                )
            },
            gap = SklyankaSpacing.Medium,
        )
    }
}

/** Ровно три действия в порядке макета: «Принято», кнопка откладывания, «Пропустить». */
@Composable
private fun Actions(state: ReminderUiState.Card, actions: ReminderActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SklyankaSpacing.EmptyStateSide),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        PrimaryButton(
            text = stringResource(ru.stohastika.sklyanka.ui.R.string.action_taken),
            onClick = actions.onTakenAll,
            form = ButtonForm.Screen,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_ACTION_TAKEN),
        )
        // При крупном шрифте кнопки встают друг под друга во всю ширину: сжатые
        // до половин, они переносили «Отложить 15 мин» на вторую строку (§6.3, SKL-61).
        // Форма — экранная: нижний ряд вне карточки (52 dp / 16, ОВ-90).
        StackingEqualButtons(
            first = {
                SecondaryButton(
                    text = snoozeLabel(state.snoozeMinutes),
                    onClick = actions.onSnooze,
                    form = ButtonForm.Screen,
                    modifier = Modifier.testTag(TAG_ACTION_SNOOZE),
                )
            },
            second = {
                SecondaryButton(
                    text = stringResource(ru.stohastika.sklyanka.ui.R.string.action_skip),
                    onClick = actions.onSkippedAll,
                    form = ButtonForm.Screen,
                    modifier = Modifier.testTag(TAG_ACTION_SKIP),
                )
            },
            gap = SklyankaSpacing.Medium,
        )
    }
}

const val TAG_REMINDER = "reminder_card"
const val TAG_OVERLINE = "reminder_overline"
const val TAG_TIME = "reminder_time"
const val TAG_NAME = "reminder_name"
const val TAG_DOSE = "reminder_dose"
const val TAG_PROFILE = "reminder_profile"
const val TAG_ROW = "reminder_row"
const val TAG_ACTION_TAKEN = "reminder_action_taken"
const val TAG_ACTION_SNOOZE = "reminder_action_snooze"
const val TAG_ACTION_SKIP = "reminder_action_skip"

package ru.stohastika.sklyanka.feature.settings.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.matchesConfirmationWord
import ru.stohastika.sklyanka.feature.settings.transfer.dateAndTime
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.PrimaryButtonKind
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.SklyankaTextField
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Экран удаления профиля (ОВ-7, SKL-36).
 *
 * Отдельный экран, а не диалог: диалогов дизайн-система не содержит, и образец тот же,
 * что у «Заменить всё». Порядок — как в решении по ОВ-7: шапка с возвратом (отмена),
 * что именно теряется — числами и словами, предупреждение о необратимости, предложение
 * выгрузить файл, и только потом слово-подтверждение и опасная кнопка.
 *
 * Числа — утверждение о данных, а не оформление: они пришли посчитанными теми же
 * запросами, что список лекарств и история (`ProfileLossesSource`). Экран их не считает.
 *
 * Кнопка подтверждения — видом `Danger` (SKL-39), и она стоит внизу: до ввода слова
 * действие недоступно, а неверный ввод ошибкой поля не объявляется (ОВ-38 к этому полю
 * не применяется).
 *
 * Экран stateless: слово и факт выгрузки живут в состоянии, наружу уходят намерения.
 */
@Composable
fun DeleteProfileScreen(
    state: DeleteProfileUiState,
    onIntent: (DeleteProfileIntent) -> Unit,
    onSaveFile: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(SklyankaTheme.colors.background).testTag(TAG_DELETE_SCREEN)) {
        when (state.step) {
            DeleteProfileStep.CONFIRM -> ConfirmationStep(state, onIntent, onSaveFile, onDone)
            DeleteProfileStep.RESULT -> ResultStep(state, onDone)
        }
    }
}

@Composable
private fun ConfirmationStep(
    state: DeleteProfileUiState,
    onIntent: (DeleteProfileIntent) -> Unit,
    onSaveFile: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = SklyankaTheme.colors
    val word = stringResource(R.string.profile_delete_word)
    val wordMatches = matchesConfirmationWord(state.word, word)
    ScreenHeader(
        labels = ScreenHeaderLabels(title = stringResource(R.string.profile_delete_title)),
        kind = ScreenHeaderKind.Nested,
        onBack = onDone,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SklyankaSpacing.Section)
            .padding(bottom = SklyankaSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Text(
            text = stringResource(R.string.profile_delete_intro, state.profileName),
            style = SklyankaType.BodyDefault,
            color = colors.text,
            modifier = Modifier.testTag(TAG_DELETE_INTRO),
        )
        CountsBlock(state)
        Text(
            text = stringResource(R.string.profile_delete_reminders),
            style = SklyankaType.BodyDefault,
            color = colors.text,
            modifier = Modifier.testTag(TAG_DELETE_REMINDERS),
        )
        Banner(
            text = stringResource(R.string.profile_delete_warning),
            modifier = Modifier.testTag(TAG_DELETE_WARNING),
        )
        ExportBlock(state = state, onSaveFile = onSaveFile, onDismissFailure = {
            onIntent(DeleteProfileIntent.DismissFailure)
        })
        ConfirmationWordBlock(
            word = word,
            value = state.word,
            onValueChange = { onIntent(DeleteProfileIntent.WordChanged(it)) },
        )
        // Кнопка не должна оказаться под пальцем там, где на прошлом экране была
        // строка «Удалить профиль «Имя»».
        Spacer(modifier = Modifier.padding(top = SklyankaSpacing.Section))
        PrimaryButton(
            text = stringResource(R.string.profile_delete_confirm),
            onClick = { onIntent(DeleteProfileIntent.Confirm) },
            state = when {
                state.busy -> ButtonState.Busy
                wordMatches -> ButtonState.Normal
                else -> ButtonState.Disabled
            },
            kind = PrimaryButtonKind.Danger,
            modifier = Modifier.fillMaxWidth().testTag(TAG_DELETE_CONFIRM),
        )
    }
}

/** Числа потерь: лекарства целиком (активные и архивные) и записи истории. */
@Composable
private fun CountsBlock(state: DeleteProfileUiState) {
    val colors = SklyankaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        Text(
            text = stringResource(
                R.string.profile_delete_medications,
                state.losses.medications,
                state.losses.archived,
            ),
            style = SklyankaType.BodyDefault,
            color = colors.text,
            modifier = Modifier.testTag(TAG_DELETE_MEDICATIONS),
        )
        Text(
            text = stringResource(R.string.profile_delete_history, state.losses.intakes),
            style = SklyankaType.BodyDefault,
            color = colors.text,
            modifier = Modifier.testTag(TAG_DELETE_HISTORY),
        )
    }
}

/**
 * Предложение выгрузки: та же выгрузка, что на экране «Перенос данных» (ОВ-7).
 *
 * После успешного сохранения показывается факт с моментом; кнопка остаётся доступной:
 * копию можно сделать сколько угодно раз, и ни одна выгрузка ничего не удаляет.
 * Отказ выгрузки называется словами — тихого отказа здесь быть не может (НФТ-11).
 */
@Composable
private fun ExportBlock(state: DeleteProfileUiState, onSaveFile: () -> Unit, onDismissFailure: () -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        SecondaryButton(
            text = stringResource(R.string.profile_delete_export),
            onClick = onSaveFile,
            state = if (state.busy) ButtonState.Busy else ButtonState.Normal,
            modifier = Modifier.fillMaxWidth().testTag(TAG_DELETE_EXPORT),
        )
        Text(
            text = stringResource(R.string.profile_delete_export_note, state.profileName),
            style = SklyankaType.CaptionSmall,
            color = colors.textMuted,
            modifier = Modifier.testTag(TAG_DELETE_EXPORT_NOTE),
        )
        state.exportedAt?.let { moment ->
            Text(
                text = stringResource(R.string.profile_delete_export_saved, moment.dateAndTime(state.zone)),
                style = SklyankaType.BodyDefault,
                color = colors.text,
                modifier = Modifier.testTag(TAG_DELETE_EXPORT_FACT),
            )
        }
        state.failure?.let { failure ->
            Banner(
                text = failure.label(),
                actionLabel = stringResource(R.string.transfer_summary_cancel),
                onAction = onDismissFailure,
                modifier = Modifier.testTag(TAG_DELETE_FAILURE),
            )
        }
    }
}

/**
 * Подпись-образец и поле слова-подтверждения (ОВ-7).
 *
 * Подпись и правило сравнения — те же, что у «Заменить всё» (`ConfirmationWord`);
 * слово своё. Поле не переводится в состояние ошибки никогда: неверный ввод виден
 * недоступной кнопкой, а не красной рамкой (ОВ-38 к этому полю не применяется).
 */
@Composable
private fun ConfirmationWordBlock(word: String, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro)) {
        Text(
            text = stringResource(R.string.transfer_replace_word_label, word),
            style = SklyankaType.BodyDefault,
            color = SklyankaTheme.colors.text,
            modifier = Modifier.testTag(TAG_DELETE_WORD_LABEL),
        )
        SklyankaTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(TAG_DELETE_WORD_FIELD),
        )
    }
}

/** Итог: профиль удалён, активным стал владелец; «Готово» возвращает в настройки. */
@Composable
private fun ResultStep(state: DeleteProfileUiState, onDone: () -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Section)
            .padding(top = SklyankaSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Text(
            text = stringResource(R.string.profile_delete_result_title, state.profileName),
            style = SklyankaType.BodyStrong,
            color = colors.text,
            modifier = Modifier.testTag(TAG_DELETE_RESULT_TITLE),
        )
        Text(
            text = stringResource(R.string.profile_delete_result_active),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
            modifier = Modifier.testTag(TAG_DELETE_RESULT_ACTIVE),
        )
        PrimaryButton(
            text = stringResource(R.string.profile_delete_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().testTag(TAG_DELETE_DONE),
        )
    }
}

/** Отказ выгрузки человеческим языком; удаление в этой точке ещё не начато. */
@Composable
private fun DeleteProfileFailure.label(): String = when (this) {
    DeleteProfileFailure.NoSpace -> stringResource(R.string.profile_delete_no_space)
    DeleteProfileFailure.ExportFailed -> stringResource(R.string.transfer_error_export_failed)
    DeleteProfileFailure.NoFilePicker -> stringResource(R.string.profile_delete_no_picker)
}

const val TAG_DELETE_SCREEN = "profile_delete_screen"
const val TAG_DELETE_INTRO = "profile_delete_intro"
const val TAG_DELETE_MEDICATIONS = "profile_delete_medications"
const val TAG_DELETE_HISTORY = "profile_delete_history"
const val TAG_DELETE_REMINDERS = "profile_delete_reminders"
const val TAG_DELETE_WARNING = "profile_delete_warning"
const val TAG_DELETE_EXPORT = "profile_delete_export"
const val TAG_DELETE_EXPORT_NOTE = "profile_delete_export_note"
const val TAG_DELETE_EXPORT_FACT = "profile_delete_export_fact"
const val TAG_DELETE_FAILURE = "profile_delete_failure"
const val TAG_DELETE_WORD_LABEL = "profile_delete_word_label"
const val TAG_DELETE_WORD_FIELD = "profile_delete_word_field"
const val TAG_DELETE_CONFIRM = "profile_delete_confirm"
const val TAG_DELETE_RESULT_TITLE = "profile_delete_result_title"
const val TAG_DELETE_RESULT_ACTIVE = "profile_delete_result_active"
const val TAG_DELETE_DONE = "profile_delete_done"

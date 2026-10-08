package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.screenPadding
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import java.time.LocalTime

/**
 * Форма заведения и правки лекарства (`AddMed.dc.html`).
 *
 * Два осознанных отступления от макета, оба зафиксированы в `TDD.md §5.6`:
 *
 * **Д-2.** Переключатель «Режим приёма» стоит выше блоков расписания, а не после
 * «Времени приёма». В макете человек заполнял счётчик и список времён, доходил до
 * седьмого блока, переключал режим — и два заполненных блока исчезали.
 *
 * **Д-3.** Сохранение одно — нижняя кнопка «Сохранить лекарство». Кнопки «Готово»
 * в шапке нет: на форме высотой в полтора экрана обе кнопки одновременно не видны,
 * и дублирование даёт только риск двойного сохранения и второе место, где нужно
 * повторять состояние «недоступно, пока не заполнено название».
 *
 * Экран без состояния: черновик приходит в [state], наружу уходят намерения. Выбор
 * даты и времени поднимается наружу через [onPickDate] и [onPickTime], а не рисуется
 * здесь: диалог дизайн-системы (`DateTimePickerDialog`, SKL-69) знает о платформенной
 * настройке 12/24 и открывается из маршрута, поэтому контракт колбэков не меняется.
 */
@Suppress("LongParameterList")
@Composable
fun MedicationFormScreen(
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    onCancel: () -> Unit,
    onPickDate: (DateTarget) -> Unit,
    onPickTime: (index: Int, current: LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val scroll = rememberScrollState()
    val anchors = remember { FormAnchors() }

    // Отказ без прокрутки к первому нарушению равносилен молчаливому отказу: сообщение
    // остаётся за пределами видимой области формы (критерий 27).
    LaunchedEffect(state.violations) {
        val target = state.firstViolationField?.let { anchors.offsetOf(it, scroll.value) } ?: return@LaunchedEffect
        scroll.animateScrollTo(target)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
            .testTag(TAG_MEDICATION_FORM),
    ) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = titleOf(state)),
            kind = ScreenHeaderKind.Modal,
            onBack = onCancel,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .onGloballyPositioned { anchors.putContainerTop(it.positionInWindow().y.toInt()) }
                .imePadding()
                .screenPadding(),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Screen),
        ) {
            // Порядок блоков — контракт формы, а не случайность разметки (Д-2).
            // Превью стоит сразу под названием и обновляется вместе с черновиком (ОВ-94).
            NameBlock(state, onIntent, anchors)
            PreviewBlock(state)
            ReleaseFormBlock(state, onIntent, anchors)
            TabletShapeBlock(state, onIntent, anchors)
            DoseBlock(state, onIntent, anchors)
            ModeBlock(state, onIntent, anchors)
            ScheduleBlock(state, onIntent, anchors, onPickTime)
            // «Связь с едой» существует только при «по расписанию» (ОВ-83): приём
            // «по потребности» ею не управляет, и решение дизайна предписывает блок
            // скрыть — как скрыто время. Выбранное значение при этом не сбрасывается.
            if (state.applicability.foodRelationVisible) {
                FoodRelationBlock(state, onIntent, anchors)
            }
            StockBlock(state, onIntent, anchors)
            PeriodBlock(state, onIntent, anchors, onPickDate)
        }
        SaveBar(state, onIntent)
    }
}

/**
 * Единственное действие сохранения формы (Д-3).
 *
 * Недоступная кнопка сопровождается причиной: молча недоступная кнопка не объясняет,
 * чего от человека хотят, а правило здесь ровно одно — нужно название.
 */
@Composable
private fun SaveBar(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.background)
            .screenPadding()
            .padding(top = SklyankaSpacing.Large, bottom = SklyankaSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        if (!state.saveEnabled) {
            BlockHint(stringResource(R.string.form_save_blocked))
        }
        PrimaryButton(
            text = when (state.mode) {
                FormMode.Create -> stringResource(R.string.form_save_new)
                is FormMode.Edit -> stringResource(R.string.form_save_existing)
            },
            onClick = { onIntent(MedFormIntent.Save) },
            state = if (state.saveEnabled) ButtonState.Normal else ButtonState.Disabled,
            modifier = Modifier.fillMaxWidth().testTag(TAG_SAVE_BUTTON),
        )
    }
}

/**
 * Заголовок шапки: «Новое лекарство» либо название правящегося лекарства.
 *
 * Макета состояния правки нет; формулировка взята из `docs/v2/10-behavior.md`.
 */
@Composable
private fun titleOf(state: MedFormUiState): String = when (val mode = state.mode) {
    FormMode.Create -> stringResource(R.string.form_title_new)
    is FormMode.Edit -> mode.title
}

const val TAG_MEDICATION_FORM = "medication_form"
const val TAG_SAVE_BUTTON = "form_save"

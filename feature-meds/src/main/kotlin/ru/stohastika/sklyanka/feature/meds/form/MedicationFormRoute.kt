package ru.stohastika.sklyanka.feature.meds.form

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.ui.component.ConfirmDialog
import ru.stohastika.sklyanka.ui.component.ConfirmDialogTexts
import ru.stohastika.sklyanka.ui.component.DateTimePickerDialog

/**
 * Форма лекарства вместе с её состоянием: заведение и правка одной формой (SKL-09).
 *
 * Выделена из маршрута раздела, потому что вход в неё теперь не один: круглая кнопка
 * добавления на списке и действие «Изменить» в карточке лекарства (SKL-10). Собранная
 * дважды, форма разошлась бы по обработке выбора даты — а это ровно то место, где
 * ошибка приводит к курсу, начавшемуся не в тот день.
 *
 * Маршрут — единственное место, знающее об Android-настройке формата времени
 * (`DateFormat.is24HourFormat`, SKL-28): дизайн-система платформенного чтения не
 * содержит и принимает флаг параметром (SKL-69, TDD §10.3). Выбор времени и даты —
 * диалог приложения `DateTimePickerDialog`, а не системный `TimePickerDialog`/EMUI
 * (`ОВ-89`). Контракт [MedicationFormScreen] при этом не меняется: замена диалога
 * не переписывает форму, её снимки и стенды приёмки.
 *
 * @param medicationId `null` — новое лекарство; иначе правка существующего.
 * @param onDone форма закрылась **после сохранения**. Возврат без сохранения закрывает
 *   маршрут снаружи: системный «Назад» снимает его навигацией раздела (`MedsSection`),
 *   и своего `BackHandler` форма не заводит (Р-54).
 */
@Composable
fun MedicationFormRoute(
    viewModel: MedicationFormViewModel,
    medicationId: String?,
    onDone: () -> Unit,
    /**
     * Уход с формы без сохранения (RT-1, Р-87 п. 1): системная «Назад» на грязном черновике
     * и выбранное в диалоге «сбросить». Закрывает маршрут той же навигацией, что и [onDone]
     * (снятие уровня стека раздела), но записи не делает. По умолчанию — тот же выход, что
     * [onDone]: и сохранение, и сброс возвращают человека туда, откуда форма была открыта.
     */
    onAbandon: () -> Unit = onDone,
    modifier: Modifier = Modifier,
) {
    // Открытие — один раз на заход, а не на сборку композиции. Поворот пересобирает
    // композицию целиком, и безусловный `open()` затирал бы черновик человека: он живёт
    // в ViewModel, который пересоздание переживает, а признак «форма уже открыта» —
    // в сохранённом состоянии композиции (SKL-142). Свежий ViewModel (первый вход или
    // смерть процесса) состояния не имеет — тогда форма читается заново.
    var opened by rememberSaveable(medicationId) { mutableStateOf(false) }
    LaunchedEffect(medicationId) {
        if (!opened || viewModel.state.value == null) {
            opened = true
            viewModel.open(medicationId)
        }
    }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Какой диалог открыт: цель даты либо индекс строки времени. Диалог — состояние
    // маршрута, а не черновика: до нажатия «Готово» пользовательское значение не меняется.
    var dateDialog by remember { mutableStateOf<DateTarget?>(null) }
    var timeDialogIndex by remember { mutableStateOf<Int?>(null) }
    // Открыт ли диалог подтверждения сброса (RT-1): состояние маршрута, а не черновика —
    // до выбора человека ни уход, ни запись не случаются.
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    // Успешное сохранение закрывает форму: список и карточка пересобираются потоком,
    // и перечитывать их отдельно не нужно. Событие не повторяется при новой подписке,
    // поэтому открытие формы после сохранения закрыться от него не может (SKL-57).
    LaunchedEffect(Unit) { viewModel.closed.collect { onDone() } }

    val current = state ?: return

    // Системная «Назад» на грязной форме спрашивает подтверждение, а не сбрасывает ввод молча
    // (RT-1, Р-87 п. 1). На чистой форме и на правке без изменений перехват выключен, и «Назад»
    // закрывает форму навигацией раздела — своего обработчика форма по-прежнему не заводит (Р-54).
    // Диалог — своё окно: пока он открыт, системная «Назад» уходит в его `onDismissRequest`
    // («остаться»), а не в этот перехват, поэтому двойного срабатывания нет.
    BackHandler(enabled = current.isDirty) { showDiscardDialog = true }
    MedicationFormScreen(
        state = current,
        onIntent = viewModel::onIntent,
        onPickDate = { target -> dateDialog = target },
        onPickTime = { index, _ -> timeDialogIndex = index },
        modifier = modifier,
    )

    dateDialog?.let { target ->
        FormDateDialog(
            target = target,
            state = current,
            onIntent = viewModel::onIntent,
            onDismiss = { dateDialog = null },
        )
    }

    timeDialogIndex?.let { index ->
        FormTimeDialog(
            index = index,
            state = current,
            onIntent = viewModel::onIntent,
            onDismiss = { timeDialogIndex = null },
        )
    }

    if (showDiscardDialog) {
        FormDiscardDialog(
            onConfirm = {
                showDiscardDialog = false
                onAbandon()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
}

/** Диалог даты курса: цель определяет и заголовок, и начальное значение, и намерение подтверждения. */
@Composable
private fun FormDateDialog(
    target: DateTarget,
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    DateTimePickerDialog(
        title = when (target) {
            DateTarget.START -> stringResource(R.string.form_dialog_start_date)
            DateTarget.END -> stringResource(R.string.form_dialog_end_date)
        },
        initial = when (target) {
            DateTarget.START -> state.draft.startDate
            DateTarget.END -> state.draft.endDate ?: state.draft.startDate
        },
        onDismiss = onDismiss,
        onConfirm = { picked ->
            onIntent(
                when (target) {
                    DateTarget.START -> MedFormIntent.StartDateChanged(picked)
                    DateTarget.END -> MedFormIntent.EndDateChanged(picked)
                },
            )
            onDismiss()
        },
    )
}

/** Диалог времени приёма: строка берётся по индексу на момент открытия. */
@Composable
private fun FormTimeDialog(
    index: Int,
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = state.draft.times.getOrNull(index) ?: return
    val context = LocalContext.current
    DateTimePickerDialog(
        title = stringResource(R.string.form_dialog_time),
        initial = initial,
        // Системную настройку читаем в момент открытия, а не кэшируем: при смене
        // режима 12/24 следующий диалог обязан открыться уже по-новому (TDD §10.3).
        is24Hour = DateFormat.is24HourFormat(context),
        onDismiss = onDismiss,
        onConfirm = { picked ->
            onIntent(MedFormIntent.TimeChanged(index, picked))
            onDismiss()
        },
    )
}

/**
 * Подтверждение сброса грязной формы (RT-1, Р-87 п. 1): «Сбросить» закрывает без записи,
 * «Остаться» (и системная «Назад», и тап вне окна) возвращает к правке с сохранённым вводом.
 */
@Composable
private fun FormDiscardDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        texts = ConfirmDialogTexts(
            title = stringResource(R.string.form_discard_title),
            message = stringResource(R.string.form_discard_message),
            confirmLabel = stringResource(R.string.form_discard_confirm),
            dismissLabel = stringResource(R.string.form_discard_stay),
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

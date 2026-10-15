package ru.stohastika.sklyanka.feature.meds.form

import android.text.format.DateFormat
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
    modifier: Modifier = Modifier,
    /**
     * Открыть витрину «Про»: сложное расписание закрыто без подписки (ПР-20, ПР-21).
     *
     * Поверхность покупки одна на все точки входа (Р-67): форма не рисует шторку сама,
     * а просит `app` открыть её с [ru.stohastika.sklyanka.domain.pro.ProPoint.MEDICATION_FORM].
     */
    onOpenPro: () -> Unit = {},
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

    // Успешное сохранение закрывает форму: список и карточка пересобираются потоком,
    // и перечитывать их отдельно не нужно. Событие не повторяется при новой подписке,
    // поэтому открытие формы после сохранения закрыться от него не может (SKL-57).
    LaunchedEffect(Unit) { viewModel.closed.collect { onDone() } }
    // Нажатие «Сохранить» на закрытом сложном виде открывает витрину и не сохраняет
    // лекарство (§5.10): сигнал поднимает форма, а поверхность покупки показывает `app`.
    LaunchedEffect(Unit) { viewModel.showcase.collect { onOpenPro() } }

    val current = state ?: return
    MedicationFormScreen(
        state = current,
        onIntent = viewModel::onIntent,
        onPickDate = { target -> dateDialog = target },
        onPickTime = { index, _ -> timeDialogIndex = index },
        modifier = modifier,
    )

    dateDialog?.let { target ->
        DateTimePickerDialog(
            title = when (target) {
                DateTarget.START -> stringResource(R.string.form_dialog_start_date)
                DateTarget.END -> stringResource(R.string.form_dialog_end_date)
            },
            initial = when (target) {
                DateTarget.START -> current.draft.startDate
                DateTarget.END -> current.draft.endDate ?: current.draft.startDate
            },
            onDismiss = { dateDialog = null },
            onConfirm = { picked ->
                viewModel.onIntent(
                    when (target) {
                        DateTarget.START -> MedFormIntent.StartDateChanged(picked)
                        DateTarget.END -> MedFormIntent.EndDateChanged(picked)
                    },
                )
                dateDialog = null
            },
        )
    }

    timeDialogIndex?.let { index ->
        val initial = current.draft.times.getOrNull(index) ?: return@let
        DateTimePickerDialog(
            title = stringResource(R.string.form_dialog_time),
            initial = initial,
            // Системную настройку читаем в момент открытия, а не кэшируем: при смене
            // режима 12/24 следующий диалог обязан открыться уже по-новому (TDD §10.3).
            is24Hour = DateFormat.is24HourFormat(context),
            onDismiss = { timeDialogIndex = null },
            onConfirm = { picked ->
                viewModel.onIntent(MedFormIntent.TimeChanged(index, picked))
                timeDialogIndex = null
            },
        )
    }
}

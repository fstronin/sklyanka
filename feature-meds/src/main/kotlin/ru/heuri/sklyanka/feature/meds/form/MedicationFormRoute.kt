package ru.heuri.sklyanka.feature.meds.form

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * Форма лекарства вместе с её состоянием: заведение и правка одной формой (SKL-09).
 *
 * Выделена из маршрута раздела, потому что вход в неё теперь не один: круглая кнопка
 * добавления на списке и действие «Изменить» в карточке лекарства (SKL-10). Собранная
 * дважды, форма разошлась бы по обработке выбора даты — а это ровно то место, где
 * ошибка приводит к курсу, начавшемуся не в тот день.
 *
 * @param medicationId `null` — новое лекарство; иначе правка существующего.
 * @param onDone форма закрылась: человек отменил либо сохранение прошло.
 */
@Composable
fun MedicationFormRoute(
    viewModel: MedicationFormViewModel,
    medicationId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(medicationId) { viewModel.open(medicationId) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Успешное сохранение закрывает форму: список и карточка пересобираются потоком,
    // и перечитывать их отдельно не нужно.
    LaunchedEffect(state?.saved) { if (state?.saved == true) onDone() }

    val current = state ?: return
    MedicationFormScreen(
        state = current,
        onIntent = viewModel::onIntent,
        onCancel = onDone,
        onPickDate = { target ->
            val initial = when (target) {
                DateTarget.START -> current.draft.startDate
                DateTarget.END -> current.draft.endDate ?: current.draft.startDate
            }
            context.pickDate(initial) { picked ->
                viewModel.onIntent(
                    when (target) {
                        DateTarget.START -> MedFormIntent.StartDateChanged(picked)
                        DateTarget.END -> MedFormIntent.EndDateChanged(picked)
                    },
                )
            }
        },
        onPickTime = { index, time ->
            context.pickTime(time) { picked -> viewModel.onIntent(MedFormIntent.TimeChanged(index, picked)) }
        },
        modifier = modifier,
    )
}

/**
 * Системный выбор даты.
 *
 * Собственного экрана выбора даты и времени продукт не рисует
 * (`docs/tasks/SKL-09/task.md`, «Вне рамок»): системный человек уже умеет, и он
 * подчиняется настройкам доступности устройства.
 */
private fun Context.pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        this,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year,
        // java.time считает месяцы с единицы, android.widget.DatePicker — с нуля.
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

/** Системный выбор времени; формат 12 или 24 часа берётся из настроек устройства. */
private fun Context.pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        this,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        DateFormat.is24HourFormat(this),
    ).show()
}

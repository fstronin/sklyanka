package ru.heuri.sklyanka.feature.meds

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.heuri.sklyanka.feature.meds.form.DateTarget
import ru.heuri.sklyanka.feature.meds.form.MedFormIntent
import ru.heuri.sklyanka.feature.meds.form.MedicationFormScreen
import ru.heuri.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.heuri.sklyanka.feature.meds.list.MedicationListScreen
import ru.heuri.sklyanka.feature.meds.list.MedicationListViewModel
import ru.heuri.sklyanka.feature.meds.list.MedsIntent
import ru.heuri.sklyanka.feature.meds.list.MedsTab
import java.time.LocalDate
import java.time.LocalTime

/**
 * Раздел «Лекарства»: список и форма.
 *
 * Внутренний переход у раздела один — список ↔ форма, и он живёт здесь: форма
 * открывается круглой кнопкой добавления, закрывается «Отменой» и закрывается сама
 * после успешного сохранения.
 *
 * Переходы наружу — карточка лекарства (SKL-10) и архив (SKL-11) — подняты в
 * параметры: `feature-meds` не зависит от других экранных модулей, и решать, куда
 * ведут эти переходы, обязан тот, кто собирает маршруты, то есть `app`.
 */
@Composable
fun MedsRoute(
    listViewModel: MedicationListViewModel,
    formViewModel: MedicationFormViewModel,
    onOpenMedicationCard: (String) -> Unit,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState by listViewModel.state.collectAsState()
    val formState by formViewModel.state.collectAsState()
    var formOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Успешное сохранение закрывает форму и возвращает в список — сам список
    // пересобирается потоком и перечитывать его отдельно не нужно.
    LaunchedEffect(formState?.saved) {
        if (formState?.saved == true) formOpen = false
    }

    val current = formState
    if (formOpen && current != null) {
        MedicationFormScreen(
            state = current,
            onIntent = formViewModel::onIntent,
            onCancel = { formOpen = false },
            onPickDate = { target ->
                val initial = when (target) {
                    DateTarget.START -> current.draft.startDate
                    DateTarget.END -> current.draft.endDate ?: current.draft.startDate
                }
                context.pickDate(initial) { picked ->
                    formViewModel.onIntent(
                        when (target) {
                            DateTarget.START -> MedFormIntent.StartDateChanged(picked)
                            DateTarget.END -> MedFormIntent.EndDateChanged(picked)
                        },
                    )
                }
            },
            onPickTime = { index, time ->
                context.pickTime(time) { picked ->
                    formViewModel.onIntent(MedFormIntent.TimeChanged(index, picked))
                }
            },
            modifier = modifier,
        )
        return
    }

    MedicationListScreen(
        state = listState,
        onIntent = { intent ->
            when (intent) {
                MedsIntent.AddMedication -> {
                    formViewModel.open()
                    formOpen = true
                }

                is MedsIntent.OpenMedication -> onOpenMedicationCard(intent.medicationId)
                is MedsIntent.SelectTab -> {
                    if (intent.tab == MedsTab.ARCHIVE) onOpenArchive() else listViewModel.onIntent(intent)
                }

                is MedsIntent.TakeNow -> listViewModel.onIntent(intent)
            }
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

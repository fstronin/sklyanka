package ru.stohastika.sklyanka.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.di.ReminderActionEntryPoint
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeSource

/**
 * Действие уведомления напоминания из шторки (RT-4, Р-87 п. 3).
 *
 * Три действия — «Принято», «Пропустить», «Отложить» — идут теми же сценариями отметки и
 * откладывания, что и карточка напоминания: `MarkIntakeUseCase` и `SnoozeReminderUseCase`.
 * Отметка из шторки **не открывает активность** и снимает уведомление — снятие делает сам
 * сценарий отметки (`DismissReminderUseCase` в последствиях отметки) и откладывания
 * (`notifications.cancel`), поэтому отдельного вызова снятия здесь нет: второй путь записи
 * означал бы второй способ обойти проверку статуса (ЧАС-24).
 *
 * Приёмник продлевает себе жизнь через [goAsync]: работа — отметить приёмы слота — идёт
 * в общей области напоминаний, а не на главном потоке (НАП-13). Граф может отсутствовать
 * в процессе без приложения продукта, и тогда приёмник обязан пережить вызов, а не уронить
 * процесс (тот же приём, что у [AlarmReceiver]).
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Разбор намерения — отдельный шаг: guards не плодят ранние return в приёмнике
        // (ReturnCount), а неполное намерение становится одним «команды нет».
        val command = parseCommand(context, intent) ?: return

        val pending = goAsync()
        val graph = command.graph
        // Сценарии и подпись журнала читаются один раз на команду: внутри области
        // напоминаний граф уже собран, и второй его сборки на действие не нужно.
        val actions = ReminderActions(
            mark = { request -> graph.markIntake()(request) },
            snooze = { intakeId, minutes -> graph.snooze()(intakeId, minutes) },
            snoozeMinutes = { graph.reminderSettings().read().snoozeMinutes },
        )
        graph.reminderScope().launch {
            try {
                when (command.action) {
                    ACTION_TAKEN -> actions.markAll(command.intakeIds, MarkResult.TAKEN)
                    ACTION_SKIP -> actions.markAll(command.intakeIds, MarkResult.SKIPPED)
                    ACTION_SNOOZE -> actions.snoozeAll(command.intakeIds)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Команда из намерения шторки: граф, действие и непустой список приёмов слота.
     *
     * `null` — намерение неполное или графа нет в процессе без приложения продукта;
     * приёмник в этом случае молча переживает вызов (тот же приём, что у [AlarmReceiver]).
     */
    private fun parseCommand(context: Context, intent: Intent): ReminderActionCommand? {
        val graph = runCatching { ReminderActionEntryPoint.of(context) }.getOrNull()
        val action = intent.action
        val intakeIds = intent.getStringArrayExtra(EXTRA_INTAKE_IDS)?.filter { it.isNotBlank() }.orEmpty()
        return if (graph != null && action != null && intakeIds.isNotEmpty()) {
            ReminderActionCommand(graph, action, intakeIds)
        } else {
            null
        }
    }

    companion object {
        const val ACTION_TAKEN = "ru.stohastika.sklyanka.reminder.action.TAKEN"
        const val ACTION_SKIP = "ru.stohastika.sklyanka.reminder.action.SKIP"
        const val ACTION_SNOOZE = "ru.stohastika.sklyanka.reminder.action.SNOOZE"

        /** Идентификаторы приёмов слота — те же, что кладутся в дополнения уведомления. */
        const val EXTRA_INTAKE_IDS = "ru.stohastika.sklyanka.reminder.action.intake_ids"
    }
}

/**
 * Действия шторки над слотами напоминания (RT-4).
 *
 * «Принято» отмечает все приёмы слота, «Отложить» переносит их на интервал настройки:
 * сценарии приходят функциями, поэтому проверяются без графа Hilt и без устройства.
 */
internal class ReminderActions(
    private val mark: suspend (MarkRequest) -> MarkOutcome,
    private val snooze: suspend (intakeId: String, minutes: Int) -> Unit,
    private val snoozeMinutes: suspend () -> Int,
) {

    /** «Принято»/«Пропустить» относится ко всем приёмам слота, как на карточке (НАП-36). */
    suspend fun markAll(intakeIds: List<String>, result: MarkResult) {
        intakeIds.forEach { intakeId ->
            mark(MarkRequest(intakeId = intakeId, result = result, source = IntakeSource.PHONE))
        }
    }

    /** Откладывание переносит каждый приём слота на интервал настройки (НАП-30, НАП-31). */
    suspend fun snoozeAll(intakeIds: List<String>) {
        val minutes = snoozeMinutes()
        intakeIds.forEach { intakeId -> snooze(intakeId, minutes) }
    }
}

/** Разобранное намерение действия шторки: что отметить, каким действием и через какой граф. */
private data class ReminderActionCommand(
    val graph: ReminderActionEntryPoint,
    val action: String,
    val intakeIds: List<String>,
)

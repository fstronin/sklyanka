package ru.stohastika.sklyanka.feature.widgets.receiver

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint

/**
 * Отметка приёма из виджета (ПР-22).
 *
 * Тот же сценарий, что у кнопки в приложении, — `MarkIntakeUseCase` с источником `PHONE`:
 * второго пути записи в продукте нет, иначе у него был бы второй способ обойти проверку
 * статуса (ПР-2, ЧАС-24). Идемпотентность и окно приёма — тоже общие: повторное нажатие
 * не создаёт второй отметки, а прошедший приём отметить нечем.
 *
 * Нажатие переводит строку в «принято»: она уходит из списка, счётчик растёт, и когда
 * непринятых не остаётся, плита показывает «Всё отмечено». Снэкбара у виджета нет:
 * снэкбар — экранная форма подтверждения с обратным ходом, а виджет не экран (§5.11).
 */
class MarkIntakeAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intakeId = parameters[KEY_INTAKE_ID] ?: return
        // Граф может отсутствовать: нажатие в процессе без приложения продукта обязано
        // стать ничем, а не падением (SKL-97).
        val graph = runCatching { WidgetEntryPoint.of(context) }.getOrNull() ?: return
        // Перерисовку плит делает сам сценарий отметки своими последствиями
        // (продакшн `MarkSideEffects`, который приходит с графом): второй вызов
        // `updateAll` здесь был бы лишним запуском сессии на то же событие.
        WidgetMarking.mark(intakeId = intakeId, mark = { request -> graph.markIntake()(request) })
    }

    companion object {

        private val KEY_INTAKE_ID = ActionParameters.Key<String>("sklyanka.widget.intakeId")

        /** Действие кнопки отметки для строки приёма. */
        fun action(intakeId: String): Action = actionRunCallback<MarkIntakeAction>(
            actionParametersOf(KEY_INTAKE_ID to intakeId),
        )
    }
}

package ru.heuri.sklyanka.platform.transfer

import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.transfer.TransferReplanner
import ru.heuri.sklyanka.scheduler.IntakePlanningTrigger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Перепланирование приёмов после загрузки (ПЕР-18, НАП-11).
 *
 * Собственного планирования перенос не делает: он вызывает уже существующий полный обход
 * (SKL-06) с причиной «загрузка данных». Причина не косметическая — по ней в журнале
 * разбирается, почему состав приёмов изменился целиком именно в этот момент.
 *
 * **Второй проход по архивированным курсам — это лечение находки SKL-06**
 * (`docs/tasks/SKL-06/TODO.md`, пункт 21). Полный обход посещает только активные курсы:
 * будущие ожидающиеся приёмы архивированного курса снимаются один раз, в момент архивации,
 * узкой областью. Режим «заменить всё» загружает архивированный курс **вместе с его
 * ожидающимися приёмами**, минуя событие архивации, — и полный обход их не тронет.
 * Человек получит напоминания по курсу, который он архивировал.
 *
 * Из двух возможных лечений выбрано это: узкий обход по каждому загруженному архивированному
 * курсу. Второе — заставить полный обход посещать архивированные курсы — противоречит
 * `docs/tasks/SKL-06/TDD.md §7` и стоит лишнего прохода по базе при каждом старте приложения,
 * ради случая, который бывает раз в жизни устройства. Здесь же цена ровно в числе
 * архивированных курсов файла, и платится она один раз — на загрузке.
 */
@Singleton
class PlanningTransferReplanner @Inject constructor(private val planning: IntakePlanningTrigger) : TransferReplanner {

    override suspend fun replanAfterTransfer(archivedMedicationIds: List<String>) {
        planning.planAll(PlanReason.DATA_IMPORTED)
        // Порядок важен: полный обход сначала приводит в порядок активные курсы, и только
        // потом снимаются приёмы архивированных — иначе снятое было бы создано заново.
        archivedMedicationIds.forEach { medicationId ->
            planning.planMedication(medicationId, PlanReason.DATA_IMPORTED)
        }
    }
}

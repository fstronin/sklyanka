package ru.heuri.sklyanka.domain.schedule

/** Итог генерации по одному лекарству. */
data class MedicationPlan(
    val profileId: String,
    val medicationId: String,
    val created: Int,
    val deleted: Int,
    val unchanged: Int,
)

/**
 * Итог одного запуска генерации.
 *
 * Нужен не только вызывающему: по нему собирается строка диагностики «сколько приёмов
 * в окне и когда окно пересобиралось» (НАП-37), а устойчиво ненулевое [deleted] при
 * нетронутом расписании — признак того, что генерация «качает» записи туда-обратно.
 */
data class PlanResult(val window: GenerationWindow, val reason: PlanReason, val medications: List<MedicationPlan>) {
    val created: Int get() = medications.sumOf { it.created }
    val deleted: Int get() = medications.sumOf { it.deleted }
    val unchanged: Int get() = medications.sumOf { it.unchanged }

    /** Повторный запуск на неизменившихся данных не меняет ничего (НАП-9). */
    val changedNothing: Boolean get() = created == 0 && deleted == 0
}

/** Число реально применённых изменений: то, что сказало хранилище, а не то, что просил домен. */
data class PlanApplyResult(val created: Int, val deleted: Int) {
    companion object {
        val NOTHING = PlanApplyResult(created = 0, deleted = 0)
    }
}

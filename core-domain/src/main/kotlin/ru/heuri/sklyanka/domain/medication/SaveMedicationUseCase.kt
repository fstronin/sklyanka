package ru.heuri.sklyanka.domain.medication

import ru.heuri.sklyanka.domain.Transactor
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.schedule.MedicationPlanner
import ru.heuri.sklyanka.domain.schedule.PlanReason
import java.util.UUID

/** Чем кончилось сохранение формы. */
sealed interface SaveResult {

    /** Курс записан; приёмы на ближайшее окно порождены. */
    data class Saved(val medicationId: String, val createdIntakes: Int, val deletedIntakes: Int) : SaveResult

    /**
     * Отказ: черновик остаётся у вызывающего нетронутым.
     *
     * Введённое не теряется именно потому, что отказ ничего не записывает и ничего
     * не переписывает — он только называет нарушенные правила.
     */
    data class Rejected(val violations: List<DraftViolation>) : SaveResult
}

/**
 * Сохранение лекарства (`TDD.md §5.4`).
 *
 * Порядок шагов жёсткий: проверка → запись курса → порождение приёмов на ближайшее окно.
 * Генерация идёт через порт [MedicationPlanner], а не напрямую: точка запуска в `app`
 * держит порядок «сначала перевод просроченных, потом генерация» (НАП-34) и пересобирает
 * будильники вслед за приёмами (НФТ-6, НАП-11). Перепланирование выполняется **после**
 * успешной записи, а не внутри её транзакции: планировщик — платформенный адаптер, и его
 * вызов внутри транзакции базы удерживал бы транзакцию на времени работы `AlarmManager`.
 *
 * Причина генерации различает появление и правку: будильники по НАП-11 пересобираются
 * по обоим событиям, но с разными причинами в журнале (`MEDICATION_ADDED` и `SCHEDULE_EDITED`).
 */
class SaveMedicationUseCase(
    private val validate: ValidateMedicationDraft,
    private val medications: MedicationRepository,
    private val planner: MedicationPlanner,
    private val transactor: Transactor,
    private val log: OperationLog,
    /** Идентификатор нового курса. Параметром — чтобы тест видел детерминированный ключ. */
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend operator fun invoke(draft: MedicationDraft): SaveResult {
        // Сначала сброс неприменимых значений, потом проверка: вид таблетки, оставшийся
        // от «таблетки» после переключения на «спрей», не должен ни сохраниться,
        // ни выглядеть нарушением, о котором человеку нечего сказать.
        val normalized = draft.normalized()
        val violations = validate(normalized)
        if (violations.isNotEmpty()) {
            log.record(
                event = OpEvent.MEDICATION_SAVE_REJECTED,
                profileId = normalized.profileId,
                medicationId = normalized.id,
                // Коды нарушений, а не введённые значения: в журнале нет ни названия
                // лекарства, ни доз (НФТ-19).
                detail = violations.joinToString(separator = ",") { it.field.name },
            )
            return SaveResult.Rejected(violations)
        }

        val previous = normalized.id?.let { medications.get(it) }
        val created = previous == null
        val medication = normalized.toMedication(id = normalized.id ?: newId())
        transactor.inTransaction { medications.save(medication) }
        val changed = previous?.let { changedFields(it, medication) } ?: emptyList()
        record(medication, previous, changed)

        // Перепланирование нужно не любому сохранению, а изменению расписания (НАП-11):
        // правка названия или остатка состав приёмов и очередь будильников не меняет,
        // и запись «пересобрано по причине изменённого расписания» в журнале была бы
        // неправдой. Причины различаются для нового курса и правки — НАП-11 разводит
        // «добавлено» и «изменено» и в журнале напоминаний (НАП-14).
        val plan = if (created || SCHEDULE_FIELDS.any { it in changed }) {
            planner.plan(
                medicationId = medication.id,
                reason = if (created) PlanReason.MEDICATION_ADDED else PlanReason.MEDICATION_SAVED,
            )
        } else {
            null
        }
        return SaveResult.Saved(
            medicationId = medication.id,
            createdIntakes = plan?.created ?: 0,
            deletedIntakes = plan?.deleted ?: 0,
        )
    }

    /**
     * След в журнале.
     *
     * У правки записываются **имена** изменившихся полей, а не значения: «изменились
     * times и dose_amount» диагностически достаточно и не содержит медицинских данных (НФТ-19).
     */
    private suspend fun record(medication: Medication, previous: Medication?, changed: List<String>) {
        if (previous == null) {
            log.record(
                event = OpEvent.MEDICATION_CREATED,
                profileId = medication.profileId,
                medicationId = medication.id,
                detail = medication.scheduleMode.name,
            )
            return
        }
        log.record(
            event = OpEvent.MEDICATION_UPDATED,
            profileId = medication.profileId,
            medicationId = medication.id,
            after = changed.joinToString(separator = ","),
            detail = if (SCHEDULE_FIELDS.any { it in changed }) SCHEDULE_CHANGED else null,
        )
    }

    /** Имена изменившихся полей курса — без их значений. */
    private fun changedFields(before: Medication, after: Medication): List<String> = FIELDS
        .filter { (_, read) -> read(before) != read(after) }
        .map { (name, _) -> name }

    private companion object {
        const val SCHEDULE_CHANGED = "schedule_changed"

        /**
         * Поля, изменение которых меняет расписание (НАП-11).
         *
         * Доза входит по таблице НАП-11 наравне с временами и границами курса.
         */
        val SCHEDULE_FIELDS = setOf("times", "start_date", "end_date", "schedule_mode", "max_per_day", "dose_amount")

        /**
         * Поля курса именами колонок модели данных.
         *
         * Имена, а не свойства Kotlin: журнал читают вместе с `docs/spec/02-data-model.md`,
         * и расхождение имён превращает разбор инцидента в перевод с одного словаря на другой.
         */
        val FIELDS: List<Pair<String, (Medication) -> Any?>> = listOf(
            "name" to { it.name },
            "form" to { it.form },
            "shape" to { it.shape },
            "dose_amount" to { it.doseAmount },
            "dose_unit" to { it.doseUnit },
            "strength" to { it.strength },
            "strength_unit" to { it.strengthUnit },
            "food_relation" to { it.foodRelation },
            "schedule_mode" to { it.scheduleMode },
            "max_per_day" to { it.maxPerDay },
            "times" to { it.times },
            "start_date" to { it.startDate },
            "end_date" to { it.endDate },
            "stock_left" to { it.stockLeft },
            "stock_threshold_days" to { it.stockThresholdDays },
        )
    }
}

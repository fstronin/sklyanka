package ru.stohastika.sklyanka.domain.widget

import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource

/**
 * Чтение снимка виджета из домена (SKL-118, `TDD.md §3`, `§7`).
 *
 * Разовое чтение, а не поток: виджет перерисовывается по событиям (отметка, полночь, смена
 * профиля, правка лекарства), а не подпиской на данные — фоновая подписка в поверхности
 * домашнего экрана означала бы работу процесса без человека перед ним (П-6, `TDD.md §7`).
 * Прецедент разового чтения активного профиля — `DeleteProfileViewModel`.
 *
 * Профиль берётся активный ([ProfileContextProvider.snapshot]) и один раз на вызов: два
 * независимых чтения дали бы снимок с приёмами одного профиля и индикацией другого (ПР-24).
 *
 * `null` означает «показывать нечего» — активного профиля нет либо виджет привязан к
 * лекарству чужого профиля. Это тихое состояние поверхности, а не ошибка: виджет ничего
 * не рисует, а не рисует чужие данные (ПР-10).
 */
class ReadWidgetSnapshotsUseCase(
    private val intakes: IntakeRepository,
    private val medications: MedicationRepository,
    /**
     * История — источник недели, а не «Сегодня»: она единственная читает лекарства
     * без фильтра архива (ПР-11), и день, в который курс отправили в архив, обязан
     * сохранить уже принятые приёмы. Тот же источник даёт дату начала ведения истории,
     * которой живёт состояние «до начала истории» в полосе (§5.2).
     */
    private val history: HistoryRepository,
    private val profiles: ProfileContextProvider,
    private val time: TimeSource,
) {

    /** Приёмы сегодняшнего дня активного профиля. */
    suspend fun day(): WidgetDaySnapshot? {
        val context = profiles.snapshot() ?: return null
        val today = time.today()
        val dayIntakes = intakes.observeDay(context.id, today).first()
        val active = medications.observeActive(context.id).first()
        return BuildWidgetSnapshots.day(
            date = today,
            intakes = dayIntakes,
            activeMedications = active,
            now = time.nowLocal(),
            zone = time.zone(),
            context = context,
        )
    }

    /** Семь последних дней активного профиля. */
    suspend fun week(): WidgetWeekSnapshot? {
        val context = profiles.snapshot() ?: return null
        val today = time.today()
        val week = BuildWidgetSnapshots.weekOf(today)
        val slice = history.observe(context.id, HistoryWindow(week.first(), week.last())).first()
        return BuildWidgetSnapshots.week(
            today = today,
            intakes = slice.intakes,
            medications = slice.medications,
            historyStartsAt = slice.historyStartsAt,
            context = context,
        )
    }

    /**
     * Есть ли лекарство у этого виджета вообще: `false` — оно существует и видимо.
     *
     * Нужно, чтобы отличить «лекарство удалено» от «лекарство чужого профиля»: удалённое
     * не показывается нигде (ПР-14), и привязку виджета к нему держать нечем, а привязку
     * к лекарству другого профиля терять нельзя — человек вернётся в свой профиль.
     */
    suspend fun medicationGone(medicationId: String): Boolean =
        medications.get(medicationId)?.let { it.isDeleted } ?: true

    /** Приёмы выбранного лекарства на сегодня — лекарство активного профиля. */
    suspend fun medication(medicationId: String): WidgetMedicationSnapshot? {
        val context = profiles.snapshot() ?: return null
        // Привязка виджета хранит идентификатор лекарства, а профиль мог переключиться:
        // снимок по чужому профилю был бы показом не тех данных (ПР-10, ПР-24).
        // Удалённая запись не показывается нигде (ПР-14): у плиты нет и имени лекарства.
        val medication = medications.get(medicationId)
            ?.takeIf { it.profileId == context.id && !it.isDeleted }
        return medication?.let { found ->
            val today = time.today()
            BuildWidgetSnapshots.medication(
                date = today,
                medication = found,
                intakes = intakes.observeByMedication(context.id, medicationId, today, today).first(),
                now = time.nowLocal(),
                zone = time.zone(),
                context = context,
            )
        }
    }
}

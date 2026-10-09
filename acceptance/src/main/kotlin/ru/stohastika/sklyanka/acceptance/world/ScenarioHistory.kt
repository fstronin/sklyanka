package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.HistorySlice
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import java.time.YearMonth

/**
 * История приёмов сценария: срез периода и отчёт врачу настоящими сценариями домена.
 *
 * Заменяет собой `HistoryRepository` продукта — и заменяет честно: срез собирается по тем
 * же правилам, что `HistoryRepositoryImpl` (приёмы периода, лекарства профиля **без фильтра
 * архива**, дата первой записи за всё время), а не по вкусу шага. Правила поверх среза
 * (расчёт приверженности, состав отчёта, журнал) не повторяются вовсе: их исполняют
 * настоящие `ObserveAdherenceUseCase` и `BuildDoctorReportUseCase`.
 *
 * Профиль в выборке обязателен (ПР-10): запроса истории нескольких профилей сразу
 * в приёмке, как и в продукте, не существует.
 */
class ScenarioHistory(
    private val medications: ScenarioMedications,
    private val intakes: ScenarioIntakes,
    private val profiles: ProfileRepository,
    private val activeProfile: ActiveProfileRepository,
    private val clock: ScenarioClock,
    private val log: ScenarioLog,
) : HistoryRepository {

    /** Последний собранный отчёт: им пользуются проверки состава отчёта и журнала. */
    var report: DoctorReport? = null
        private set

    override fun observe(profileId: String, window: HistoryWindow): Flow<HistorySlice> =
        flowOf(slice(profileId, window))

    /**
     * Срез периода — та же выборка, что вернула бы настоящая база.
     *
     * Приёмы — только профиля (ПР-10), лекарства — все его, включая архивированные
     * (ПР-11), начало истории — первая запись за всё время, а не за окно: дни до неё
     * обязаны остаться нейтральными (`DayState.BEFORE_HISTORY`).
     */
    fun slice(profileId: String, window: HistoryWindow): HistorySlice {
        // Мягко удалённые исключены из истории и отчёта (ПР-14): их дни и приёмы
        // не участвуют ни в календаре, ни в разбивке, ни в приверженности.
        val owned = medications.all().filter { it.profileId == profileId && !it.isDeleted }
        val ownedIds = owned.mapTo(mutableSetOf()) { it.id }
        val history = intakes.all().filter { it.medicationId in ownedIds }
        return HistorySlice(
            profileId = profileId,
            intakes = history.filter { it.scheduledAt.toLocalDate() in window.from..window.to },
            medications = owned,
            historyStartsAt = history.minOfOrNull { it.scheduledAt.toLocalDate() },
        )
    }

    /**
     * Отчёт врачу за месяц — настоящим `BuildDoctorReportUseCase`.
     *
     * Журнал ведёт сам сценарий домена: «след без медицинских данных» проверяется
     * на том же следе, который оставил бы продукт, а не на его пересказе в шаге.
     */
    fun buildReport(month: YearMonth): DoctorReport? = buildReport(HistoryWindow.of(month))

    fun buildReport(window: HistoryWindow): DoctorReport? {
        val useCase = BuildDoctorReportUseCase(
            activeProfile = activeProfile,
            profiles = profiles,
            history = this,
            calculator = AdherenceCalculator(OnTimeRule.TwoHours),
            time = clock.time,
            log = log,
        )
        val built = runBlocking { useCase(window) }
        report = built
        return built
    }
}

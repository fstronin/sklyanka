package ru.heuri.sklyanka.domain.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import ru.heuri.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Наблюдаемые сутки экрана «Сегодня» (`TDD.md §9`).
 *
 * Поток производный от активного профиля: смена профиля пересобирает запрос приёмов
 * целиком, и запроса «приёмы всех профилей» в слое данных не существует вовсе (ПР-10).
 *
 * «Сегодня» — не значение, вычисленное при открытии экрана, а величина, зависящая от
 * времени (НФТ-37). Поэтому здесь два собственных такта:
 *
 * - **граница суток** — поток ждёт ровно до местной полуночи, после чего выполняет
 *   проход по просроченным приёмам (НАП-34) и перезапрашивает новые сутки. Перезапуск
 *   приложения для смены дня не требуется;
 * - **минутный такт** — метка «Сейчас» и переход приёма из «время впереди» в «время
 *   наступило» обязаны случаться сами, без действий человека.
 *
 * Такт берётся от [TimeSource], а не от системных часов (НФТ-48): иначе полночь и
 * смена часового пояса проверяются только ожиданием реальной полуночи.
 */
class ObserveTodayAgendaUseCase(
    private val activeProfile: ActiveProfileRepository,
    private val intakes: IntakeRepository,
    private val medications: MedicationRepository,
    private val sweepMissed: SweepMissedIntakesUseCase,
    /** Предупреждение о покупке считает SKL-12; экран его только показывает. */
    private val stockWarnings: StockWarningForProfileUseCase,
    private val time: TimeSource,
    private val build: BuildTodayAgenda = BuildTodayAgenda(),
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<TodayAgenda> = activeProfile.activeProfileId
        .flatMapLatest { profileId -> days(profileId) }
        // Минутный такт, не изменивший ни одной карточки, не имеет права
        // перерисовывать экран: иначе список мигает раз в минуту без причины.
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun days(profileId: String): Flow<TodayAgenda> = dayBoundaries().flatMapLatest { day ->
        flow {
            // Проход по просроченным выполняется и при открытии экрана, и на границе
            // суток: устройство могло быть выключено ровно в момент истечения окна
            // (НАП-34). Запись в базу сама породит новое значение потока приёмов.
            sweepMissed(if (day.first) SweepReason.APP_START else SweepReason.TIMER)
            emitAll(agenda(profileId, day.date))
        }
    }

    private fun agenda(profileId: String, date: LocalDate): Flow<TodayAgenda> = combine(
        intakes.observeDay(profileId, date),
        medications.observeActive(profileId),
        stockWarnings(profileId),
        moments(),
    ) { dayIntakes, active, warning, now ->
        build(
            date = date,
            intakes = dayIntakes,
            activeMedications = active,
            now = now,
            zone = time.zone(),
            stockWarning = warning,
        )
    }

    /** Текущая дата и следующие за ней — каждая в свою местную полночь (НФТ-37). */
    private fun dayBoundaries(): Flow<Day> = flow {
        var first = true
        while (true) {
            val today = time.today()
            emit(Day(date = today, first = first))
            first = false
            delay(millisUntilMidnight(today))
        }
    }

    private fun moments(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(time.nowLocal())
            delay(TICK.toMillis())
        }
    }

    /**
     * Сколько ждать до следующей местной полуночи.
     *
     * Считается по календарю, а не прибавлением 24 часов: в сутках с переводом времени
     * их 23 или 25, и арифметика на константе промахнулась бы мимо границы ровно
     * дважды в год.
     */
    private fun millisUntilMidnight(today: LocalDate): Long {
        val midnight = today.plusDays(1).atStartOfDay(time.zone()).toInstant()
        return Duration.between(time.now(), midnight).toMillis().coerceAtLeast(1)
    }

    /** Сутки потока: дата и признак «это первое открытие, а не переход через полночь». */
    private data class Day(val date: LocalDate, val first: Boolean)

    private companion object {

        /**
         * Шаг минутного такта.
         *
         * Минута, а не секунда: точнее минуты у экрана ничего не меняется — приёмы
         * назначаются с точностью до минуты, и более частый такт только будил бы
         * пересборку состояния впустую.
         */
        val TICK: Duration = Duration.ofMinutes(1)
    }
}

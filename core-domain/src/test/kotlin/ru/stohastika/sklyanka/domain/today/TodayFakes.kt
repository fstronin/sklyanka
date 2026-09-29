package ru.stohastika.sklyanka.domain.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestCoroutineScheduler
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.schedule.FakeIntakeRepository
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Транзакция в памяти: снимок до, восстановление при отказе.
 *
 * Нужна ровно для одного вопроса — «остаётся ли половина группы отмеченной, если один
 * приём записать не удалось». Настоящая неразрывность проверяется инструментальным
 * тестом `core-data` на Room, эмулятора для которого в сборочном контейнере нет.
 */
internal class SnapshotTransactor(
    private val intakes: FakeIntakeRepository,
    private val medications: FakeMedicationRepository,
) : Transactor {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        val intakesBefore = intakes.stored.toMap()
        val medicationsBefore = medications.stored.toMap()
        return try {
            block()
        } catch (failure: Throwable) {
            intakes.stored.clear()
            intakes.stored.putAll(intakesBefore)
            medications.stored.clear()
            medications.stored.putAll(medicationsBefore)
            throw failure
        }
    }
}

/** Хранилище, отказывающее на записи одного приёма: отказ посреди групповой отметки. */
internal class FailingOnMark(private val delegate: FakeIntakeRepository, private val failingIntakeId: String) :
    IntakeRepository by delegate {

    override suspend fun markAtomically(command: MarkCommand): MarkOutcome = if (
        command.intakeId == failingIntakeId
    ) {
        error("the write for ${command.intakeId} cannot be performed")
    } else {
        delegate.markAtomically(command)
    }
}

/**
 * Приёмы суток отдельным потоком.
 *
 * Штатный `FakeIntakeRepository` отдаёт на `observeDay` пустой поток: генерации он
 * не нужен. Экрану нужен именно он, поэтому наблюдаемая часть добавляется здесь,
 * а не переписывается в чужом наборе.
 */
internal class DayStreamIntakeRepository(private val delegate: FakeIntakeRepository) :
    IntakeRepository by delegate {

    private val revision = MutableStateFlow(0)

    val stored: MutableMap<String, Intake> get() = delegate.stored

    override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> = revision.map {
        delegate.stored.values.filter { it.scheduledAt.toLocalDate() == date }.sortedBy { it.scheduledAt }
    }

    fun put(intake: Intake) {
        delegate.put(intake)
        revision.value++
    }
}

/** Активный профиль, который можно переключить прямо в тесте. */
internal class FakeActiveProfileRepository(initial: String) : ActiveProfileRepository {

    private val state = MutableStateFlow(initial)

    override val activeProfileId: Flow<String> = state

    override suspend fun current(): String = state.value

    override suspend fun switchTo(profileId: String) {
        state.value = profileId
    }
}

/**
 * Часы, идущие вместе с виртуальным временем теста.
 *
 * Только так проверяется переход через полночь: поток ждёт до местной полуночи
 * `delay`-ом, тест сдвигает виртуальное время, и часы обязаны сдвинуться вместе с ним.
 * Часы, стоящие на месте, дали бы вечное «сегодня» и зелёный тест при сломанном
 * переходе (НФТ-48).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VirtualTimeSource(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant,
    private val zone: ZoneId,
) : TimeSource {

    override fun now(): Instant = start.plusMillis(scheduler.currentTime)

    override fun zone(): ZoneId = zone
}

/** Лекарства профиля одной строкой: в сценариях экрана их состав меняется редко. */
internal fun FakeMedicationRepository.putAll(medications: List<Medication>) = medications.forEach { put(it) }

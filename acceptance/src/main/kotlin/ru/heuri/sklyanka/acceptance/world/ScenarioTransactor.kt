package ru.heuri.sklyanka.acceptance.world

import ru.heuri.sklyanka.domain.Transactor

/**
 * Транзакция в памяти: снимок до, восстановление при отказе.
 *
 * Нужна ровно для одного вопроса ОВ-32 — «остаётся ли половина группы отмеченной,
 * если один приём записать не удалось». Настоящая неразрывность проверяется
 * инструментальным тестом `core-data` на Room, эмулятора для которого в сборочном
 * контейнере нет; здесь повторяется её наблюдаемое следствие.
 */
class ScenarioTransactor(private val intakes: ScenarioIntakes, private val medications: ScenarioMedications) :
    Transactor {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        val intakesBefore = intakes.snapshot()
        val medicationsBefore = medications.snapshot()
        return try {
            block()
        } catch (failure: Throwable) {
            intakes.restore(intakesBefore)
            medications.restore(medicationsBefore)
            throw failure
        }
    }
}

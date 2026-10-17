package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.InMemoryDatabaseLife
import ru.stohastika.sklyanka.domain.startup.ExplainDatabaseResetUseCase

/**
 * Шаги приёмки SKL-154: объяснение сброса данных после повреждения базы (RT-2, МГ-8, Р-87 п. 2).
 *
 * Правило «объясняется один раз» живёт в домене ([ExplainDatabaseResetUseCase]) и не требует
 * экрана: сценарий исполняет его на отметках жизни [InMemoryDatabaseLife] тем же порядком,
 * каким продукт зовёт его на старте «Сегодня». Приёмка не повреждает базу и платформой её
 * не стирает — признак пересоздания ставит сценарий, как в продукте его ставит колбэк Room.
 * Баннер на экране доказывает экранный сценарий той же задачи (стенд `feature-today`).
 */
class Sk154DatabaseResetSteps(private val world: AcceptanceWorld) {

    /** Отметки жизни базы сценария: переживают повторные «старты» внутри одного сценария. */
    private val life = InMemoryDatabaseLife()

    /** Что вернуло правило на последнем открытии «Сегодня»: показывать ли объяснение. */
    private var explained: Boolean? = null

    @Допустим("^отметка базы знает о прежней жизни$")
    fun theDatabaseMarkKnowsAboutThePreviousLife() {
        runBlocking { life.markLife() }
    }

    @Допустим("^платформа пересоздала базу при старте$")
    fun thePlatformRecreatedTheDatabaseOnStartup() {
        life.recreate()
    }

    @Когда("^приложение открывает «Сегодня» после старта$")
    fun theAppOpensTodayAfterStartup() {
        // Один вызов на «процесс»: так же правило зовёт `TodayViewModel` на старте экрана.
        explained = runBlocking { useCase().explanationOnStartup() }
    }

    @Когда("^человек закрывает объяснение$")
    fun thePersonDismissesTheExplanation() {
        runBlocking { useCase().dismissExplanation() }
    }

    @Тогда("^потеря данных объясняется человеку$")
    fun theDataLossIsExplained() {
        assertTrue(
            explained == true,
            "recreation over a lived database must be explained once: $explained",
        )
    }

    @Тогда("^потеря данных не объясняется человеку$")
    fun theDataLossIsNotExplainedOnACleanInstall() {
        assertFalse(
            explained == true,
            "a database that never lived has nothing to explain: $explained",
        )
    }

    @Тогда("^потеря данных не объясняется$")
    fun theDataLossIsNotExplainedAgain() {
        assertFalse(
            explained == true,
            "an acknowledged reset must not be explained again: $explained",
        )
    }

    private fun useCase() = ExplainDatabaseResetUseCase(life, world.clock.time)
}

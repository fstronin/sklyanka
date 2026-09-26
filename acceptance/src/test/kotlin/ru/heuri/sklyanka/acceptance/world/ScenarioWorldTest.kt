package ru.heuri.sklyanka.acceptance.world

import io.cucumber.picocontainer.PicoFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Свойства общего состояния сценария, на которых стоит весь перенос.
 *
 * Проверяются они модульным тестом, а не «на глаз по зелёному прогону»: зелёный прогон
 * и был тем, что скрывало четыре независимых мира — сценарий проходил, а шаг работал
 * не с тем состоянием, которое сценарий проверяет.
 */
class ScenarioWorldTest {

    /** Заглушки вместо классов шагов: аннотаций у них нет, в словарь шагов они не попадают. */
    class FirstProbe(val world: AcceptanceWorld)

    class SecondProbe(val world: AcceptanceWorld)

    @Test
    fun `one scenario gets one world for every glue class`() {
        val factory = PicoFactory()
        factory.addClass(FirstProbe::class.java)
        factory.addClass(SecondProbe::class.java)

        factory.start()
        val first = factory.getInstance(FirstProbe::class.java)
        val second = factory.getInstance(SecondProbe::class.java)
        factory.stop()

        assertSame(first.world, second.world, "both glue classes must share one scenario state")
    }

    @Test
    fun `the next scenario gets a world of its own`() {
        val factory = PicoFactory()
        factory.addClass(FirstProbe::class.java)

        factory.start()
        val ofFirstScenario = factory.getInstance(FirstProbe::class.java).world
        ofFirstScenario.profiles.use("Я")
        ofFirstScenario.medications.add(ofFirstScenario.profiles, "Я", "Л", listOf(LocalTime.of(8, 0)))
        ofFirstScenario.intakes.markTaken("Л", LocalDateTime.of(2026, 9, 25, 8, 0))
        factory.stop()

        factory.start()
        val ofSecondScenario = factory.getInstance(FirstProbe::class.java).world
        factory.stop()

        assertNotSame(ofFirstScenario, ofSecondScenario)
        assertTrue(ofSecondScenario.medications.names().isEmpty(), "medications leaked into the next scenario")
        assertTrue(ofSecondScenario.intakes.all().isEmpty(), "intakes leaked into the next scenario")
        assertTrue(ofSecondScenario.profiles.all().isEmpty(), "profiles leaked into the next scenario")
        assertFalse(ofSecondScenario.acted, "the snapshot flag leaked into the next scenario")
    }

    @Test
    fun `the phone owner is the first profile the scenario names`() {
        val world = AcceptanceWorld()

        world.profiles.use("Я")
        world.profiles.use("Мама")

        assertEquals("Я", world.profiles.self()?.name)
        assertEquals("Мама", world.profiles.active, "the scenario switched the active profile")
        assertTrue(world.profiles.get("Мама").mirrorReminders, "a foreign profile mirrors reminders")
    }

    @Test
    fun `an unknown medication is a step failure and not a silent creation`() {
        val world = AcceptanceWorld()
        world.profiles.use("Я")

        assertThrows<IllegalArgumentException> { world.medications.get("Л") }
    }

    @Test
    fun `the snapshot is taken once even when the scenario acts twice`() {
        val world = AcceptanceWorld()
        world.profiles.use("Я")
        world.medications.add(world.profiles, "Я", "Л", listOf(LocalTime.of(8, 0)))
        world.intakes.markTaken("Л", LocalDateTime.of(2026, 9, 25, 8, 0))

        world.rememberState()
        world.intakes.markTaken("Л", LocalDateTime.of(2026, 9, 25, 20, 0))
        world.rememberState()

        assertTrue(world.acted)
        assertEquals(1, world.before.size, "the snapshot must describe the state before the first action")
    }

    @Test
    fun `a taken intake of the background does not touch the stock`() {
        val world = AcceptanceWorld()
        world.profiles.use("Я")
        world.medications.add(world.profiles, "Я", "Л", listOf(LocalTime.of(8, 0)))

        world.intakes.markTaken("Л", LocalDateTime.of(2026, 9, 25, 8, 0))

        assertNull(world.medications.stockOf("Л"), "the background describes a state, it does not spend tablets")
    }

    @Test
    fun `the course of a medication starts on the day of the scenario`() {
        val world = AcceptanceWorld()
        world.clock.moveTo(LocalDateTime.of(2026, 9, 25, 7, 55))
        world.profiles.use("Я")

        world.medications.add(world.profiles, "Я", "Л", listOf(LocalTime.of(8, 0)))

        assertEquals(LocalDate.of(2026, 9, 25), world.medications.get("Л").startDate)
    }
}

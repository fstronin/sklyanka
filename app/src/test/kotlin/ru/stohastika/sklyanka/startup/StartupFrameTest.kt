package ru.stohastika.sklyanka.startup

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.data.room.MigrationCopyBudget
import ru.stohastika.sklyanka.feature.today.TodayUiState
import java.time.LocalDate

/**
 * Правило кадров запуска (SKL-42, ОВ-87).
 *
 * Четыре состояния правила и граница между ними: маршрут, онбординг и готовность
 * «Сегодня». Собственной логики у заставки нет — она держится ровно там, где первый
 * экран не готов, и ни кадром дольше (НФТ-21).
 */
class StartupFrameTest {

    private val ready: TodayUiState = TodayUiState.Empty(DATE)

    @Test
    fun `the route is not decided yet`() {
        assertEquals(
            StartupFrame.AppSplash,
            startupFrame(
                destination = null,
                onboardingUnseen = false,
                onboardingDone = false,
                today = ready,
            ),
        )
    }

    @Test
    fun `today content waits for the first value of the flow`() {
        assertEquals(
            StartupFrame.AppSplash,
            startupFrame(
                destination = StartDestination.Today,
                onboardingUnseen = false,
                onboardingDone = false,
                today = TodayUiState.Opening,
            ),
        )
    }

    @Test
    fun `today ready replaces the splash`() {
        assertEquals(
            StartupFrame.FirstScreen,
            startupFrame(
                destination = StartDestination.Today,
                onboardingUnseen = false,
                onboardingDone = false,
                today = ready,
            ),
        )
    }

    @Test
    fun `the permissions screen is ready as soon as the route is decided`() {
        assertEquals(
            StartupFrame.FirstScreen,
            startupFrame(
                destination = StartDestination.Onboarding,
                onboardingUnseen = true,
                onboardingDone = false,
                today = TodayUiState.Opening,
            ),
        )
    }

    @Test
    fun `a seen onboarding falls through to today and waits for it`() {
        assertEquals(
            StartupFrame.AppSplash,
            startupFrame(
                destination = StartDestination.Onboarding,
                onboardingUnseen = false,
                onboardingDone = false,
                today = TodayUiState.Opening,
            ),
        )
        assertEquals(
            StartupFrame.FirstScreen,
            startupFrame(
                destination = StartDestination.Onboarding,
                onboardingUnseen = false,
                onboardingDone = false,
                today = ready,
            ),
        )
    }

    @Test
    fun `a finished onboarding falls through to today`() {
        assertEquals(
            StartupFrame.FirstScreen,
            startupFrame(
                destination = StartDestination.Onboarding,
                onboardingUnseen = true,
                onboardingDone = true,
                today = ready,
            ),
        )
    }

    @Test
    fun `a newer database blocks the app no matter how ready the rest is`() {
        // База новее установленной сборки: ни первый экран, ни заставка (Р-44,
        // ADR-0012 п. 7). Готовность «Сегодня» здесь не спасает и не спасает вообще
        // ничего: любой экран продукта открыл бы схему, которой эта сборка не знает.
        assertEquals(
            StartupFrame.DatabaseBlocked(DatabaseBlock.TooNew),
            startupFrame(
                destination = StartDestination.Today,
                onboardingUnseen = false,
                onboardingDone = true,
                today = ready,
                block = DatabaseBlock.TooNew,
            ),
        )
    }

    @Test
    fun `a database without room for the migration copy blocks the app the same way`() {
        // Места на копию перед миграцией нет — миграция не выполняется (ADR-0012 п. 3),
        // и открывать базу этой сборке нельзя ровно так же, как на базе новее. Кадр тот же,
        // а причина — своя: «освободите место» вместо «обновите приложение».
        val budget = MigrationCopyBudget(requiredBytes = 50, freeBytes = 10)

        assertEquals(
            StartupFrame.DatabaseBlocked(DatabaseBlock.NotEnoughSpace(budget)),
            startupFrame(
                destination = StartDestination.Today,
                onboardingUnseen = false,
                onboardingDone = true,
                today = ready,
                block = DatabaseBlock.NotEnoughSpace(budget),
            ),
        )
    }

    @Test
    fun `an ordinary database keeps the usual frames`() {
        assertEquals(
            StartupFrame.FirstScreen,
            startupFrame(
                destination = StartDestination.Today,
                onboardingUnseen = false,
                onboardingDone = true,
                today = ready,
                block = null,
            ),
        )
    }

    @Test
    fun `the analytics consent screen is shown instead of the splash`() {
        // Экран согласия — первый экран первого запуска: ему нечего ждать, и пустой кадр
        // «Сегодня» под ним заставку не держит (SKL-165, АН-1).
        assertEquals(
            StartupFrame.FirstScreen,
            startupFrame(
                destination = StartDestination.Onboarding,
                onboardingUnseen = true,
                onboardingDone = false,
                today = TodayUiState.Opening,
                analyticsConsentUnseen = true,
            ),
        )
    }

    @Test
    fun `a decided analytics question leaves the ordinary frames`() {
        assertEquals(
            StartupFrame.AppSplash,
            startupFrame(
                destination = StartDestination.Today,
                onboardingUnseen = false,
                onboardingDone = true,
                today = TodayUiState.Opening,
                analyticsConsentUnseen = false,
            ),
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 12)
    }
}

package ru.stohastika.sklyanka.screen.journey

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceJourneyStorage
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R

/**
 * Поход «Выживание: смерть процесса», фаза 2 (SKL-140, поход реестра № 12).
 *
 * Идёт свежим процессом и утверждает, что оставленное первой фазой пережило смерть:
 * признак настройки надёжности, лекарство с приёмом в файловой базе, снимок будильников
 * в DE-хранилище. Поэтому фаза **не делает уборку**: файлы и хранилища — предмет
 * утверждения, а не подготовка.
 *
 * Отметку о запуске фаза тоже не снимает: настоящая смерть процесса `SharedPreferences`
 * не теряет, и подменять её здесь значило бы выдать желаемое за проверенное. Признак
 * онбординга читается напрямую из хранилища (`onboardingIsSeen`) — независимо от того,
 * что маршрут первого экрана мог опереться на отметку о запуске.
 */
@HiltAndroidTest
class SurvivalPhaseTwoJourneyTest : JourneyTest() {

    /** Состояние первой фазы остаётся: только часы ставятся на тот же управляемый момент. */
    override fun resetBeforeScenario() {
        DeviceJourneyStorage.requireFile()
        DeviceTestClock.reset()
    }

    /** Ничего не сеется: вторая фаза смотрит только на оставленное первой. */
    override fun seedDefaultState() = Unit

    @Test
    fun theNewProcessSeesTheSurvivingState() {
        val name = testContext.getString(R.string.device_test_medication_name, SurvivalJourney.NAME_SUFFIX)

        // До запуска приложения: файлы на месте глазами домена. Снимок будильников здесь
        // не утверждается — на стенде право точных будильников есть у продукта, а не у
        // тестового пакета, поэтому зеркало пусто по окружению (TDD §8).
        assertTrue("признак настройки надёжности потерян вместе с процессом", onboardingIsSeen())
        assertEquals(
            "лекарство обязано пережить смерть процесса",
            SurvivalJourney.MEDICATION_ID,
            dayIntakes().singleOrNull { it.scheduledAt.toLocalTime() == DeviceTestClock.scheduledTime }?.medicationId,
        )

        launchApp()
        waitUntil(LAUNCH_TIMEOUT_MS, "«Сегодня» без онбординга") { exists(TAG_TODAY_SCREEN) }
        assertFalse("настройка надёжности не должна открываться сама", exists(TAG_PERMISSIONS_SCREEN))
        waitUntil(SCREEN_TIMEOUT_MS, "приёма в дне после смерти процесса") { textExists(name) }
        assertTrue(
            "приём обязан остаться ожидающим",
            dayIntakes().any { it.isPending && it.scheduledAt.toLocalTime() == DeviceTestClock.scheduledTime },
        )
        capture("survival-read")
    }
}

package ru.stohastika.sklyanka.screen.journey

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R

/**
 * Общее для обеих фаз эстафеты выживания (SKL-140, поход реестра № 12).
 *
 * Одно имя и один ключ лекарства: вторая фаза идёт другим процессом и ищет ровно то,
 * что оставила первая. Момент времени тоже общий — час берётся у управляемых часов.
 */
internal object SurvivalJourney {

    /** Ключ посеянного лекарства: вторая фаза сверяет приём по нему. */
    const val MEDICATION_ID = "jny-survival"

    /** Суффикс имени лекарства в ресурсах стенда. */
    const val NAME_SUFFIX = "survival"
}

/**
 * Поход «Выживание: смерть процесса», фаза 1 (SKL-140, поход реестра № 12).
 *
 * Фаза доводит состояние человека до рабочего и **завершается, не стирая его**: признак
 * пройденной настройки надёжности (сид базы), лекарство с расписанием и приёмом в файловой
 * базе, снимок будильников ближайшего окна в DE-хранилище. Файлы и есть предмет
 * утверждения второй фазы — поэтому её запуск скрипт делает отдельным вызовом
 * инструментовки, а не следующим классом того же запуска.
 */
@HiltAndroidTest
class SurvivalPhaseOneJourneyTest : JourneyTest() {

    @Test
    fun thePersonLeavesTheAppWithAWorkingDay() {
        val name = testContext.getString(R.string.device_test_medication_name, SurvivalJourney.NAME_SUFFIX)
        seedMedication(
            id = SurvivalJourney.MEDICATION_ID,
            name = name,
            times = listOf(DeviceTestClock.scheduledTime),
        )
        planIntakes()

        launchApp()
        awaitRoot()
        waitUntil(SCREEN_TIMEOUT_MS, "приёма в посеянном дне") { textExists(name) }
        capture("survival-write")

        assertTrue("посеянный приём обязан быть в дне", dayIntakes().isNotEmpty())
        assertTrue("настройка надёжности обязана быть отмеченной", onboardingIsSeen())
        // Снимок будильников здесь не утверждается: право точных будильников объявлено
        // у продукта, а не у тестового пакета, и на стенде `AlarmManager` отказывает
        // («lost permission to set exact alarms»), поэтому зеркало пусто по окружению,
        // а не по поведению продукта (TDD §8). Настоящее срабатывание и зеркало держат
        // испытания И-1…И-16 и ручной прогон.
    }
}

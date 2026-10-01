package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки пресетов срока окончания курса (SKL-75, группа `@screen`).
 *
 * Блок «Период приёма» лежит ниже превью, и стенд собирает форму целиком: сценарии
 * говорят и о выключенной, и о включённой бессрочности.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsPresetAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl75RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-75"
    }
}

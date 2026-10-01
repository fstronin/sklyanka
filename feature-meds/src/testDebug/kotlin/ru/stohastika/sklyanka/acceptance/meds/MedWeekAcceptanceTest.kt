package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки сводки «Последние 7 дней» (SKL-77, группа `@screen`).
 *
 * Карточка длиннее окна телефона, а сценарии говорят о её нижнем блоке: стенд собирает
 * её целиком. Язык — часть условий: сценарии записаны по-русски, и даты со склонениями
 * на английской локали читались бы не тем.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedWeekAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl77RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-77"
    }
}

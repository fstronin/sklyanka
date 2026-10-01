package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки формы лекарства (SKL-66, группа `@screen`).
 *
 * Форма длиннее окна телефона, а сценарии говорят о блоках ниже превью: стенд собирает
 * её целиком (тот же приём, что в `MedsAcceptanceTest`). Язык — часть условий: сценарии
 * записаны по-русски, и даты со склонениями на английской локали читались бы не тем.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsPreviewAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl66RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-66"
    }
}

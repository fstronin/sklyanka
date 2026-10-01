package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки порядка списка лекарств (SKL-68, группа `@screen`).
 *
 * Язык — часть условий: сценарии записаны по-русски, и даты со склонениями на английской
 * локали читались бы не тем. «Сегодня» проверяется тем же прогоном: шаги читают данные,
 * которые показал бы экран, а чужая задача по-соседски не переписывается.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsOrderAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl68RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-68"
    }
}

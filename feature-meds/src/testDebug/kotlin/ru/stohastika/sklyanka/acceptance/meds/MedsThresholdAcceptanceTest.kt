package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки порога напоминания о покупке (SKL-74, группа `@screen`).
 *
 * Форма длиннее окна телефона, а сценарий говорит о блоке «Остаток в упаковке» ниже превью:
 * стенд собирает её целиком (тот же приём, что в `MedsPreviewAcceptanceTest`).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsThresholdAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl74RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-74"
    }
}

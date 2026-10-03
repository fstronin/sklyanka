package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки вёрстки карточек при нехватке ширины (SKL-59, `@screen`).
 *
 * Модуль свой: список и карточка живут здесь, и их шаги обязаны видеть собранный экран
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Ширина узкая, а высота с запасом: карточка
 * длиннее окна телефона, и нижний блок иначе не собрался бы.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsWidthAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl59RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-59"
    }
}

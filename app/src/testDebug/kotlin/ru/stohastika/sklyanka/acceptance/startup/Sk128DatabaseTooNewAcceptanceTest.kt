package ru.stohastika.sklyanka.acceptance.startup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки отказа от работы на базе новее установленной сборки (SKL-128).
 *
 * Сценарии говорят о корне приложения (кадр запуска и блокирующий экран) и о сработавшем
 * будильнике, который приходит в приёмник без экрана вовсе. И то и другое живёт только
 * в `app`: экраны продукта в этом состоянии не участвуют, а приёмник — точка входа
 * приложения, а не модуля.
 *
 * Высота окна — телефонная: блокирующий экран собирается на настоящей высоте, и на
 * удлинённом стенде «поместилось бы» то, что на телефоне не помещается.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk128DatabaseTooNewAcceptanceTest {

    @Test
    fun theRefusalScenariosOfSkl128RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-128"
        const val SCREEN_TAG = "@screen"
    }
}

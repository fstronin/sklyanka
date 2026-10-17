package ru.stohastika.sklyanka.acceptance.steps.sk154

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Экранный прогон приёмки SKL-154 на стенде `core-ui`: нижняя панель при шрифте 200 % (RT-3).
 *
 * Тег выбирает только сценарий панели: остальные `@screen` сценарии файла SKL-154 исполняют
 * стенды `feature-today` и `feature-meds`, а доменные сценарии — сценарный прогон `acceptance`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h1200dp-mdpi")
class Sk154BottomBarAcceptanceTest {

    @Test
    fun theBottomBarScenariosOfSk154RunOnTheStand() {
        assertNull(Sk154AcceptanceRun.run(tags = "@screen and @bottom-bar"))
    }
}

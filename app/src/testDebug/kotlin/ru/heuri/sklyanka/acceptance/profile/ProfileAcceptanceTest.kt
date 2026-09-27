package ru.heuri.sklyanka.acceptance.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки профилей (SKL-26, группа `@screen` файла SKL-15).
 *
 * Модуль выбран не по удобству: `Структура сценария` «Признаки чужого профиля есть на всех
 * экранах профиля» обходит шесть экранов трёх разных модулей, а один сценарий не может
 * исполняться в трёх модулях. `app` — единственное место, где все эти экраны существуют
 * одновременно (`TDD.md §4.3`).
 *
 * Цена ошибки здесь наибольшая в продукте: отметка чужого приёма как своего делает ложными
 * обе истории, и обнаруживается это поздно или никогда (`ПРФ-10`). Поэтому проверяется не
 * «признак есть», а «признаки есть все сразу» и «в своём профиле нет ни одного».
 */
@RunWith(AndroidJUnit4::class)
class ProfileAcceptanceTest {

    @Test
    fun theScreenScenariosOfProfilesRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-15"
    }
}

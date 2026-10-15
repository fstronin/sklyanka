package ru.stohastika.sklyanka.acceptance.pro

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-122 — строка блока «Склянка Про» в настройках.
 *
 * Модуль свой, а не общий: строка живёт в `feature-settings`, и поход на витрину из неё
 * проверяется словами строки, её тегом и её ресурсами (`SKL-26/TDD.md §4.3`). Витрина —
 * соседняя поверхность другого модуля, и группа `@pro` сюда не попадает: строка настроек
 * и шторка проверяются разными прогонами (тег `@settings` против `@pro`), потому что
 * ни один из двух модулей не видит экран соседа (`verifyModuleDependencies`).
 *
 * Высота окна — удлинённая: экран настроек длиннее телефона, и строка блока стоит в нём
 * ниже видимой части; стенд собирает список целиком (тот же порог, что у прогона SKL-134).
 * Язык — часть условий: сценарии записаны по-русски (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk122ProRowAcceptanceTest {

    @Test
    fun theSettingsRowScenariosOfSk122RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-122"

        /** Группа строки настроек: сценарии витрины помечены `@pro` и идут своим прогоном. */
        const val TAGS = "@screen and @settings"
    }
}

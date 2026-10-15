package ru.stohastika.sklyanka.acceptance.pro

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-122 — витрина «Склянка Про» как собранная поверхность.
 *
 * Модуль свой, а не общий: шторка живёт в `feature-pro`, и только здесь рядом с её шагами
 * оказываются её теги (`SKL-26/TDD.md §4.3`). Строка настроек — соседняя поверхность
 * другого модуля, и она сюда не попадает: `feature-pro` не видит `feature-settings`
 * (`verifyModuleDependencies`), поэтому группа `@settings` отсечена тегом — иначе
 * сценарий строки настроек упал бы здесь нереализованным шагом.
 *
 * Высота окна — телефонная: шторка поднимается снизу по высоте содержимого и прокручивает
 * середину, и порог обязан собираться на настоящей высоте экрана, а не на удлинённом стенде.
 * Язык — часть условий: сценарии записаны по-русски (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk122ProSheetAcceptanceTest {

    @Test
    fun theShowcaseScenariosOfSk122RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-122"

        /** Группа витрины: сценарии строки настроек помечены `@settings` и идут своим прогоном. */
        const val TAGS = "@screen and @pro and not @settings and not @mark"
    }
}

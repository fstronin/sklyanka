package ru.stohastika.sklyanka.acceptance.widgets

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-118 — экран настройки виджета как собранная поверхность.
 *
 * Модуль свой, а не общий: экран живёт в `feature-widgets`, и только здесь рядом с его
 * шагами оказывается его тег (SKL-26/TDD.md §4.3). Плиты виджетов на этом стенде нет —
 * Glance рисуется в RemoteViews, и кадры типов приходят с устройства (фаза 6.2).
 *
 * Высота окна — телефонная: экран настройки открывается системой поверх домашнего экрана
 * прошивки, и пороги полей обязаны собираться на настоящей высоте, а не на удлинённом стенде.
 * Язык — часть условий: сценарии записаны по-русски (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk118WidgetConfigAcceptanceTest {

    @Test
    fun theSetupScreenScenariosOfSk118RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-118"

        /** Группа экрана настройки: поведенческие сценарии идут сценарным прогоном. */
        const val TAGS = "@screen and @gate"
    }
}

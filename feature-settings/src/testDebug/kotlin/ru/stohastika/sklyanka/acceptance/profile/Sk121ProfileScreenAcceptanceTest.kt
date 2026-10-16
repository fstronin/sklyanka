package ru.stohastika.sklyanka.acceptance.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-121 — мастер добавления профиля и строка «Добавить профиль».
 *
 * Модуль свой, а не общий: форма профиля и строка профилей живут в `feature-settings`, и поход
 * в мастер из строки проверяется словами строки, её тегом и ресурсами (`SKL-26/TDD.md §4.3`).
 * Группа `@screen and @settings` отсекает сценарные сценарии задачи: они идут своим прогоном
 * в модуле `acceptance` и о композиции не говорят.
 *
 * Высота окна — удлинённая: экран настроек длиннее телефона, и строка «Добавить профиль» стоит
 * в нём последней в блоке профилей; стенд собирает список целиком. Язык — часть условий:
 * сценарии записаны по-русски (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk121ProfileScreenAcceptanceTest {

    @Test
    fun theProfileScreenScenariosOfSk121RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-121"

        /** Группа экрана: сценарии мастера и строки помечены `@screen` и `@settings`. */
        const val TAGS = "@screen and @settings"
    }
}

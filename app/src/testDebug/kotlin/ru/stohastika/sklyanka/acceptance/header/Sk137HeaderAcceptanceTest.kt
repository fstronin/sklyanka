package ru.stohastika.sklyanka.acceptance.header

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-137 — форма лекарства без действия в шапке, крупный заголовок,
 * системный возврат и потолок верхнего поля.
 *
 * Модуль свой: сценарии говорят сразу о форме, списке лекарств и двух шторках разных модулей,
 * а одновременно они существуют только в `app` (`SKL-26/TDD.md §4.3`). Файл сценариев читает
 * общая обвязка [ScreenAcceptanceRun]; группа `@screen` — та, что исполняется на стенде,
 * а не сценарным прогоном (`ОВ-78`).
 *
 * Высота окна — телефонная: сценарии говорят о прокрутке формы до конца и о закреплённом
 * ряде, и порог обязан собираться на настоящей высоте экрана, а не на удлинённом стенде.
 * Язык — часть условий: сценарии записаны по-русски (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk137HeaderAcceptanceTest {

    @Test
    fun theFormHeaderScenariosOfSk137RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-137"
        const val TAGS = "@screen"
    }
}

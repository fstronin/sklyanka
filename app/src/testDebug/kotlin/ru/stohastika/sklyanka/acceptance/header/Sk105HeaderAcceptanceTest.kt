package ru.stohastika.sklyanka.acceptance.header

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-105 о шапке, модальных слоях, переходах корня и карточке приёма.
 *
 * Модуль свой: сценарии говорят сразу о нескольких экранах (история, настройки, лекарства,
 * «Сегодня») и о корне приложения, а одновременно они существуют только в `app`
 * (`SKL-26/TDD.md §4.3`). Теги выбраны так, чтобы прогон был один и точный: из файла
 * SKL-105 берутся сценарии групп `@ui`, `@header`, `@nav`, `@today`, `@profile` —
 * остальные (`@history`, `@settings`, `@meds`) исполняют модули своих экранов.
 *
 * Высота окна — телефонная: сценарии говорят о закреплённой шапке и о прокрутке до конца,
 * и сжатый ряд обязан собираться на настоящей высоте экрана, а не на удлинённом стенде.
 * Язык — часть условий: сценарии записаны по-русски (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk105HeaderAcceptanceTest {

    @Test
    fun theHeaderAndRootScenariosOfSk105RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-105"
        const val TAGS = "@screen and not @history and not @settings and not @meds"
    }
}

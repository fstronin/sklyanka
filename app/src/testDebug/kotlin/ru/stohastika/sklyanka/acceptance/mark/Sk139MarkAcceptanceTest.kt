package ru.stohastika.sklyanka.acceptance.mark

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-139 — холодная палитра знака (Р-56, Р-57).
 *
 * Модуль свой: сценариям нужны и токены `core-ui`, и композиция заставки и фирменного
 * блока, и ресурсы иконки, и разметка названия при 200 % — всё это видит только `app`
 * (`SKL-26/TDD.md §4.3`).
 *
 * Окно телефонное: сценарий о названии собирает заставку и фирменный блок в настоящей
 * ширине экрана, и подгонка кегля обязана считаться на ней, а не на удлинённом стенде.
 * Язык — часть условий: сценарии записаны по-русски (НФТ-43), и название приходит
 * из русских ресурсов.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk139MarkAcceptanceTest {

    @Test
    fun theMarkPaletteScenariosOfSk139RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-139"

        /** Все экранные сценарии задачи: палитра, контрасты, дуга, заставка, имя при 200 %. */
        const val TAGS = "@screen and not @device"
    }
}

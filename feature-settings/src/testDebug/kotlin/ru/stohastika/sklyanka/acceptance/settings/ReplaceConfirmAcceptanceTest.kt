package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки подтверждения замены всего (SKL-39, группа `@screen`).
 *
 * Шаги те же, что у переноса данных (SKL-19): экран подтверждения — часть того же
 * маршрута, и отдельный стенд здесь был бы вторым способом собрать одно состояние.
 * Модуль свой по той же причине, что и у SKL-19: шаги видят ресурсы и композицию
 * `feature-settings` (`SKL-26/TDD.md §4.3`).
 */
@RunWith(AndroidJUnit4::class)
// Экран подтверждения длиннее окна телефона: числа, слово и кнопки иначе не собираются,
// и проверка видела бы не экран, а его верх. Язык — часть условий: сценарии записаны
// по-русски, и подпись слова-образца читается с ресурсов русской локали.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class ReplaceConfirmAcceptanceTest {

    @Test
    fun theReplaceConfirmationScenariosRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK))
        } finally {
            TransferScreenProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-39"
    }
}

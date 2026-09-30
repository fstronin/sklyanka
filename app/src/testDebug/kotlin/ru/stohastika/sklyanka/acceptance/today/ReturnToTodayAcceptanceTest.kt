package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки возврата на «Сегодня» под живым окном отмены (SKL-43,
 * 3 сценария).
 *
 * Модуль выбран не по удобству: сценарию нужны сразу корень приложения (`RootNavigation`),
 * «Сегодня» и карточка напоминания — единственное место, где они существуют одновременно,
 * `app` (`SKL-26/TDD.md §4.3`). Два ручных сценария SKL-43 (`@manual @device`) исполняются
 * на устройстве и в этот прогон не входят.
 */
@RunWith(AndroidJUnit4::class)
// Высота с запасом: «Сегодня» обязана показать ленты и нижнюю панель со снэкбаром;
// язык — часть условий: сценарии записаны по-русски, и склонения счётных форм читаются
// не на английской локали стенда.
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class ReturnToTodayAcceptanceTest {

    @Test
    fun theReturnScenariosOfTheReminderCardRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $RETURN_TAG"))
        } finally {
            UndoAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-43"
        const val SCREEN_TAG = "@screen"
        const val RETURN_TAG = "@return"
    }
}

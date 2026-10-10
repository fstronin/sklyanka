package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-134 — пара подписей строки «Системные настройки уведомлений».
 *
 * Стенд и высота окна те же, что у прогона SKL-78: сценарии говорят о той же строке того же
 * экрана, а экран длиннее окна телефона, поэтому стенд собирает его целиком. Прогон свой,
 * а не общий: `ScreenAcceptanceRun` читает файл сценариев одной задачи.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk134SettingsRowAcceptanceTest {

    @Test
    fun theSettingsRowCopyScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-134"
        const val TAGS = "@screen and @sound"
    }
}

package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки диагностики напоминаний (SKL-26, группа `@screen` файла SKL-20).
 *
 * Модуль свой: экран диагностики живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Из группы `@diagnostics` сюда приходят и оба
 * сценария `@privacy`: они говорят о том же экране — журнале, его выгрузке и отсутствии
 * каналов наружу.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: журнал на пятьдесят записей иначе не собирается,
// и проверка видела бы не экран, а его верх. Язык — часть условий: сценарии приёмки
// записаны по-русски, и даты со склонениями на английской локали стенда читаются
// не тем, что в документе.
@Config(qualifiers = "ru-rRU-w390dp-h8000dp-mdpi")
class DiagnosticsAcceptanceTest {

    @Test
    fun theScreenScenariosOfDiagnosticsRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $DIAGNOSTICS_TAG"))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-20"
        const val SCREEN_TAG = "@screen"
        const val DIAGNOSTICS_TAG = "@diagnostics"
    }
}

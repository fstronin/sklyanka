package ru.stohastika.sklyanka.acceptance.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки подписи действий карточки напоминания (SKL-61, `@screen`).
 *
 * Модуль свой: карточка напоминания живёт здесь, и её шаги обязаны видеть собранный
 * экран и ресурсы модуля (`SKL-26/TDD.md §4.3`). Сценарий «Подпись читается
 * на заблокированном экране» несёт `@manual @device` и остаётся чек-листом устройства.
 */
@RunWith(AndroidJUnit4::class)
// Ширина — как у телефона, на котором обе подписи помещаются в равные половины
// при обычном шрифте: сценарий проверяет масштаб шрифта, а не конкретную ширину,
// и на узком стенде кнопки встают в столбец уже при 100 % (SKL-61).
@Config(qualifiers = "ru-rRU-w430dp-h3000dp-mdpi")
class ReminderActionsAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl61RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = ScreenAcceptanceRun.SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-61"
    }
}

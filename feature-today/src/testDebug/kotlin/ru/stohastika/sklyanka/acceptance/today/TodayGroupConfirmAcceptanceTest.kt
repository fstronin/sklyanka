package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-31: строки по одной, подтверждение батча, ссылка на историю
 * (группа `@screen` файла SKL-31; прогон заведён этой задачей).
 *
 * Модуль свой: сценарии говорят о собранном экране «Сегодня» и его ресурсах
 * (`SKL-26/TDD.md §4.3`). Таймер подтверждения в стенде не крутится: дедлайн зеркалится
 * `TodayScreenToggles.confirmingUntil`, а «наступает 14:05:09/10» двигает `ScenarioClock`
 * (`TDD.md §14.2`).
 *
 * Структура «Возврат из истории ведёт туда, откуда её открыли» исполняется прогоном `app`
 * (`@composition`): ей нужны два экрана сразу, и её тег исключён здесь — иначе она
 * осталась бы в этом прогоне без шагов.
 */
@RunWith(AndroidJUnit4::class)
// Экран с запасом на развёрнутый плотный день: в нём тридцать приёмов, и ленивый список
// не собрал бы дальние карточки, а сценарии говорят и о них (TDD.md §14.2).
// Язык — часть условий: сценарии записаны по-русски, и даты со склонениями на английской
// локали стенда читаются не тем, что записано в документе.
@Config(qualifiers = "ru-rRU-w390dp-h4500dp-mdpi")
class TodayGroupConfirmAcceptanceTest {

    @Test
    fun theGroupConfirmScenariosOfTodayRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and not $COMPOSITION_TAG"))
    }

    private companion object {
        const val TASK = "SKL-31"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
    }
}

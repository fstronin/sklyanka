package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-126 (группа `@screen`, модуль лекарств).
 *
 * Сценарии говорят о единицах измерения: подпись единицы в поле дозы, список единиц
 * дозировки, слова дозы в превью и в карточке. Все они собираются на стенде формы
 * и карточки, поэтому прогон один на весь файл; доменные сценарии единиц
 * (`@units`, `@strength`, `@validation`) исполняет `acceptanceRun` без `@screen`.
 *
 * Язык — часть условий: сценарии записаны по-русски, а единицы печатаются словом языка,
 * и на английской локали «капля» читалась бы не тем словом.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk126UnitsAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl126RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-126"
        const val SCREEN_TAG = "@screen"
    }
}

package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-138 (группа `@screen`, модуль лекарств).
 *
 * Сценарии говорят о слове единицы дозы в поле: подпись поля дозы, строки «Сейчас есть»
 * и «Размер упаковки» формы, поле пополнения остатка в карточке. Собираются они на стендах
 * формы и карточки, поэтому прогон один на весь файл; слова дозы в готовых подписях
 * («Сегодня», напоминание, документ отчёта) проверяются переборами единиц этих модулей.
 *
 * Язык — часть условий: слово единицы печатается словом языка, и на английской локали
 * «таблетки» читались бы чужим словом.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk138UnitWordsAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl138RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-138"
        const val SCREEN_TAG = "@screen"
    }
}

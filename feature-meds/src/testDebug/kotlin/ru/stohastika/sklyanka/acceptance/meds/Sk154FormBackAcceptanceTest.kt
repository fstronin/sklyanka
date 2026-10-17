package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-154 на стенде формы лекарства: системная «Назад»
 * на грязном черновике и правка без изменений (RT-1).
 *
 * Модуль свой: маршрут и модель формы живут здесь, и шаги обязаны видеть собранный экран
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Тег выбирает только сценарии формы: остальные
 * `@screen` сценарии файла SKL-154 исполняют стенды `feature-today` и `core-ui`.
 */
@RunWith(AndroidJUnit4::class)
// Высота — телефонная: сценарии говорят о диалоге поверх формы и о поле названия на первом
// экране, а не о хвосте формы. Язык — часть условий: сценарии записаны по-русски (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk154FormBackAcceptanceTest {

    @Test
    fun theFormBackScenariosOfSk154RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-154"
        const val TAGS = "@screen and @form-back"
    }
}

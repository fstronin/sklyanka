package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки связи с едой в PRN-форме (SKL-49, группа `@screen`).
 *
 * Модуль свой: сценарии открывают собранную форму лекарства, и шаги обязаны видеть
 * её ресурсы (`SKL-26/TDD.md §4.3`). Те же сценарии дополнены в приёмке SKL-09 —
 * там они исполняются прогоном `MedsAcceptanceTest` как часть контракта формы.
 */
@RunWith(AndroidJUnit4::class)
// Форма длиннее окна телефона: блоки остатка и периода иначе не собираются.
// Язык — часть условий: строки формы и связи с едой читаются по-русски (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedsPrnAcceptanceTest {

    @Test
    fun thePrnFoodRelationScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-49"
        const val TAGS = "@screen and not @composition and not @manual"
    }
}

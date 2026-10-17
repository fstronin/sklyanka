package ru.stohastika.sklyanka.acceptance.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-154 на стенде «Сегодня»: баннер объяснения сброса базы (RT-2).
 *
 * Модуль свой: экран живёт здесь, и шаг обязан видеть собранный экран и его ресурсы
 * (`SKL-26/TDD.md §4.3`). Тег выбирает только сценарий баннера: остальные `@screen`
 * сценарии файла SKL-154 исполняют стенды `feature-meds` и `core-ui`.
 */
@RunWith(AndroidJUnit4::class)
// Язык — часть условий: сценарий сверяет текст баннера с продуктовым ресурсом (НФТ-43).
@Config(qualifiers = "ru-rRU-w390dp-h1200dp-mdpi")
class Sk154BannerAcceptanceTest {

    @Test
    fun theResetBannerScenariosOfSk154RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-154"
        const val TAGS = "@screen and @db-reset-banner"
    }
}

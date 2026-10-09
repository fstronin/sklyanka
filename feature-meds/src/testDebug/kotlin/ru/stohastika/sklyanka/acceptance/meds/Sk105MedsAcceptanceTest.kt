package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-105 в модуле лекарств (тег `@meds`, группа `@screen`).
 *
 * Сценарии задачи говорят о карточке и форме целиком — от сжатой недели до нижних полей
 * прокрутки, — поэтому стенд собирает их в окне высотой 3000 dp: иначе нижние блоки
 * остались бы за пределами видимой области, а унаследованные шаги ждут их видимыми.
 * Язык — часть условий: сценарии записаны по-русски, и слова недели, доз и шторки
 * на английской локали читались бы не тем.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk105MedsAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl105RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_AND_MEDS))
    }

    private companion object {
        const val TASK = "SKL-105"

        /** Группа лекарств: остальные сценарии задачи живут в своих модулях. */
        const val SCREEN_AND_MEDS = "@screen and @meds"
    }
}

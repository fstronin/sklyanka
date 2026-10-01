package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки экрана «Настроить надёжность» (SKL-84, группа `@screen`).
 *
 * Модуль свой: экран живёт здесь, и шаги обязаны видеть собранную композицию и ресурсы
 * модуля (`SKL-26/TDD.md §4.3`). Кросс-экранные сценарии (баннеры, «Исправить»)
 * исполняет композиция `app`, сценарии баннера на «Сегодня» — `feature-today`.
 */
@RunWith(AndroidJUnit4::class)
// Экран с пятью карточками, плашкой и футером: без запаса по высоте нижние блоки
// не собираются, и проверка видела бы верх экрана. Язык — часть условий: тексты сценариев
// записаны по-русски.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class SetupSk84AcceptanceTest {

    @Test
    fun theSetupScenariosOfSk84RunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            PermissionsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-84"
        const val TAGS = "@screen and not @manual and not @composition and not @banner"
    }
}

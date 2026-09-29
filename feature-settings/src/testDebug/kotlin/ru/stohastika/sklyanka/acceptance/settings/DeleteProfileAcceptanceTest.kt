package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки удаления профиля (SKL-36, группа `@screen`).
 *
 * Шаги видят ресурсы и композицию `feature-settings` (`SKL-26/TDD.md §4.3`): экран
 * подтверждения и строка в настройках живут в этом модуле, и отдельный стенд в `app`
 * был бы вторым способом собрать те же состояния.
 */
@RunWith(AndroidJUnit4::class)
// Экран подтверждения длиннее окна телефона: числа, слово, выгрузка и кнопки иначе
// не собираются, и проверка видела бы не экран, а его верх. Язык — часть условий:
// подписи и факт выгрузки читаются с ресурсов русской локали.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class DeleteProfileAcceptanceTest {

    @Test
    fun theDeletionScenariosRunOnTheScreenStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK))
        } finally {
            DeleteProfileScreenProbe.dropMainDispatcher()
            SettingsScreenProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-36"
    }
}

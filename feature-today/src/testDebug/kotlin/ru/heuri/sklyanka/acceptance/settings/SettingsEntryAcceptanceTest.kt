package ru.heuri.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки единственного входа в настройки (SKL-26, `@defect` файла SKL-20).
 *
 * Модуль свой: сценарий говорит о шапке экрана «Сегодня» и его нижней панели, а живут они
 * здесь (`SKL-26/TDD.md §4.3`). Композиция не нужна: вход в настройки обещает вкладка
 * нижней панели этого же экрана, и проверяется она на его стенде.
 */
@RunWith(AndroidJUnit4::class)
// Язык — часть условий: подписи вкладок и описания для чтения с экрана берутся
// из русских ресурсов.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class SettingsEntryAcceptanceTest {

    @Test
    fun theDefectScenarioOfSettingsRunsOnTheTodayStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and $DEFECT_TAG"))
    }

    private companion object {
        const val TASK = "SKL-20"
        const val SCREEN_TAG = "@screen"
        const val DEFECT_TAG = "@defect"
    }
}

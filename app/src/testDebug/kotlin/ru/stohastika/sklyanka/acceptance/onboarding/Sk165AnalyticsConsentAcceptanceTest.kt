package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-165 (SKL-165 п. 33, TDD §11.5).
 *
 * Модуль — `app`: сценариям нужны и маршрут первого запуска (политика → аналитика →
 * «Сегодня»), и строка «Аналитика» в настройках одновременно; оба видны только здесь,
 * в composition root.
 */
@RunWith(AndroidJUnit4::class)
// Экраны длиннее окна телефона: строка раздела данных и карточки приёмов иначе
// не собираются. Язык — часть условий: подписи и склонения берутся из русских ресурсов.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class Sk165AnalyticsConsentAcceptanceTest {

    @Test
    fun theAnalyticsConsentScenariosRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK))
        } finally {
            LaunchRouteStand.forgetMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-165"
    }
}

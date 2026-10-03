package ru.stohastika.sklyanka.acceptance.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки согласия на аналитику (SKL-82, группа `@screen`).
 *
 * Модуль выбран по правилу `TDD.md §4.3`: сценариям нужны одновременно экран согласия
 * (`feature-onboarding`) и строка настроек (`feature-settings`) с маршрутом запуска
 * (`SplashRoute`, `RootNavigation`) — всё это видно сразу только в `app`.
 *
 * Сценарии про трафик и отсутствие HMS помечены `@manual @device` и здесь не исполняются.
 */
@RunWith(AndroidJUnit4::class)
// Экран согласия длиннее окна телефона: без высокой области нижние блоки и кнопки
// не собираются, а язык — часть условий: подписи берутся из русских ресурсов.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class AnalyticsConsentAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl82RunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK))
        } finally {
            AnalyticsConsentStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-82"
    }
}

package ru.stohastika.sklyanka.acceptance.rollback

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки сворачивания вендора (SKL-100, группа `@screen`).
 *
 * Сценариям нужны одновременно маршрут запуска и настройки — единственный модуль,
 * где видно и то и другое, `app` (SKL-24/SKL-26, как у SKL-82).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class AnalyticsRollbackAcceptanceTest {

    @Test
    fun theScreenScenariosOfSkl100RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK))
    }

    private companion object {
        const val TASK = "SKL-100"
    }
}

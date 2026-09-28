package ru.heuri.sklyanka.acceptance.transfer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки переноса данных (SKL-19, `@composition`).
 *
 * Модуль выбран не по удобству: сценарии «После замены на устройстве ровно содержимое
 * файла» и «После загрузки человека ведут настраивать разрешения» заканчиваются экраном
 * настройки разрешений, а он живёт в `feature-onboarding` — единственное место, где
 * оба экрана существуют одновременно, `app` (`SKL-26/TDD.md §4.3`).
 *
 * Остальные `@screen`-сценарии SKL-19 исполняет прогон `feature-settings`
 * (`@screen and not @composition`): один сценарий исполняется ровно там, где видны
 * его экраны, и где доступен настоящий кодек формата файла.
 */
@RunWith(AndroidJUnit4::class)
// Экраны переноса и разрешений длиннее окна телефона: высоты с запасом хватает,
// чтобы собирались и отчёт, и пробное напоминание под ним.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TransferCompositionAcceptanceTest {

    @Test
    fun theCompositionScenariosOfTheTransferRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = COMPOSITION_TAG))
        } finally {
            TransferAppProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-19"
        const val COMPOSITION_TAG = "@composition"
    }
}

package ru.stohastika.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-99: карточка, шторка и снэкбар (ОВ-124).
 *
 * Сценарный прогон эти сценарии не исполняет — им нужен собранный Compose и ресурсы
 * модуля (SKL-24, SKL-26). Прогон идёт в `feature-meds`, потому что карточка и список
 * живут здесь; композицию «карточка → Сегодня» исполняет прогон в `app`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h1200dp-mdpi")
class DeletionAcceptanceTest {

    @Test
    fun theCardScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = "SKL-99", tags = "@screen and @deletion-card"))
    }

    @Test
    fun theListAndArchiveScenariosRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = "SKL-99", tags = "@screen and @deletion-meds"))
    }
}

package ru.stohastika.sklyanka.acceptance.startup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки копии перед миграцией и отказа из-за места (SKL-94, `ADR-0012` п. 3).
 *
 * Сценарии говорят о корне приложения (кадр запуска и блокирующий экран) и о файлах базы
 * рядом с ним. И то и другое живёт только в `app`: подготовка базы — часть сборки графа,
 * а `noBackupFilesDir` и путь базы известны лишь контексту приложения.
 *
 * Высота окна — телефонная: блокирующий экран собирается на настоящей высоте, и на
 * удлинённом стенде «поместилось бы» то, что на телефоне не помещается.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk94AcceptanceTest {

    @Test
    fun theDatabaseUpgradeScenariosOfSkl94RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = SCREEN_TAG))
    }

    private companion object {
        const val TASK = "SKL-94"
        const val SCREEN_TAG = "@screen"
    }
}

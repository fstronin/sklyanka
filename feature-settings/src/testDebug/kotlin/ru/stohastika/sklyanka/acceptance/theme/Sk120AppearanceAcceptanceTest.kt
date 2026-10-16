package ru.stohastika.sklyanka.acceptance.theme

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-120 — блок «Оформление», его диалоги и тёмная среда экрана.
 *
 * Модуль свой, а не общий: блок и оба диалога живут в `feature-settings`, и проверяются
 * словами строк, тегами и ресурсами этого модуля (`SKL-26/TDD.md §4.3`). Группа
 * `@screen and @settings` отсекает сценарии других стендов — иконки, плиты, документа
 * и токенов: дверь у них своя, и ни один модуль не видит экран соседа
 * (`verifyModuleDependencies`).
 *
 * Высота окна — удлинённая: экран настроек длиннее телефона, и блок «Оформление» стоит
 * в нём ниже видимой части (тот же порог, что у прогонов SKL-121/122). Язык — часть
 * условий: сценарии записаны по-русски (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class Sk120AppearanceAcceptanceTest {

    @Test
    fun theAppearanceBlockScenariosOfSk120RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-120"

        /** Группа блока «Оформление»: сценарии помечены `@screen` и `@settings`. */
        const val TAGS = "@screen and @settings"
    }
}

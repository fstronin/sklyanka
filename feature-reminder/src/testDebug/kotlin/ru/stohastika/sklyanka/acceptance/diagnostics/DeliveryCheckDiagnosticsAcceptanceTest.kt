package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки самопроверки доставки (SKL-86, группа `@screen`).
 *
 * Модуль свой: шторка, блок исхода и карточка живут в `feature-reminder`, и шаги обязаны
 * видеть собранную композицию и ресурсы модуля (SKL-26/TDD.md §4.3). Сценарий второй
 * точки входа (`@reliability`) исполняется там, где сходятся оба экрана вместе, —
 * в композиции `app`: `feature-onboarding` не видит «Проверку напоминаний» (`ADR-0007`).
 */
@RunWith(AndroidJUnit4::class)
// Экран проверки длиннее окна телефона: блок, шторка и журнал иначе не собираются,
// и проверка видела бы верх экрана. Язык — часть условий: тексты сценариев русские.
@Config(qualifiers = "ru-rRU-w390dp-h8000dp-mdpi")
class DeliveryCheckDiagnosticsAcceptanceTest {

    @Test
    fun theScreenScenariosOfSk86RunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            DiagnosticsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-86"
        const val TAGS = "@screen and @SKL-86 and not @reliability"
    }
}

package ru.heuri.sklyanka.acceptance.meds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки карточки лекарства (SKL-26, группа `@screen` файла SKL-10).
 *
 * Модуль свой: карточка живёт здесь, и её шаги обязаны видеть собранный экран и ресурсы
 * модуля (`SKL-26/TDD.md §4.3`).
 *
 * Сценарий «Архивированное лекарство не планируется и не напоминает» несёт
 * `@composition`: его последняя проверка — про экран «Сегодня» из другого модуля.
 * Строки часов (`@watch`) в прогоне нет намеренно: интеграция с Wear Engine отложена
 * (`ADR-0009`), и сценарии, которым нужен переключатель, остаются `UNDEFINED`.
 */
@RunWith(AndroidJUnit4::class)
// Карточка длиннее окна телефона: блок «Последние 7 дней» и нижнее действие иначе
// не собираются, и проверка видела бы не экран, а его верх (тот же приём, что в модульных тестах).
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class MedCardAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheCardRunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and not $COMPOSITION_TAG"))
    }

    private companion object {
        const val TASK = "SKL-10"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
    }
}

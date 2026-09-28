package ru.heuri.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки переноса данных (SKL-26, группа `@screen` файла SKL-19).
 *
 * Модуль свой: экран переноса живёт здесь, и шаги обязаны видеть собранную композицию
 * и ресурсы модуля (`SKL-26/TDD.md §4.3`). Он же — единственное место, где доменные
 * сценарии замены получают настоящий формат файла: кодек `JsonTransferCodec` живёт
 * в `core-data`, а модуль приёмки остаётся чистым JVM (`ADR-0007`).
 *
 * Сценарии «После замены на устройстве ровно содержимое файла» и «После загрузки человека
 * ведут настраивать разрешения» несут `@composition`: первый заканчивается экраном
 * настройки разрешений, второй целиком о нём, а живёт он в `feature-onboarding` —
 * единственное место, где оба экрана существуют одновременно, `app`.
 */
@RunWith(AndroidJUnit4::class)
// Экран переноса длиннее окна телефона: карточки, чипы и предупреждения иначе
// не собираются, и проверка видела бы не экран, а его верх. Язык — часть условий:
// сценарии приёмки записаны по-русски, и даты со склонениями на английской локали
// стенда читаются не тем, что в документе.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TransferAcceptanceTest {

    @Test
    fun theScreenScenariosOfTheTransferRunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = "$SCREEN_TAG and not $COMPOSITION_TAG"))
        } finally {
            TransferScreenProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-19"
        const val SCREEN_TAG = "@screen"
        const val COMPOSITION_TAG = "@composition"
    }
}

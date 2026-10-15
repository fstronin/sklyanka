package ru.stohastika.sklyanka.acceptance.pro

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки SKL-122 для метки «Про» (критерий 8).
 *
 * Отдельный прогон от шторки и строки настроек: теги не пересекаются, и это не дробление
 * ради дробления — метка живёт в дизайн-системе и не зависит ни от магазина, ни от состояния
 * подписки, а шторка зависит от обоих. Общий прогон тянул бы за собой состояние витрины
 * туда, где оно не нужно.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk122ProMarkAcceptanceTest {

    @Test
    fun theMarkScenarioOfSk122RunsOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-122"

        const val TAGS = "@screen and @mark"
    }
}

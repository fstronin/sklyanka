package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_DIALOG
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_ROW
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * Враждебный поход «Настройки» (SKL-140, реестр походов № 18).
 *
 * Человек пробует запутать возврат: уходит системной «Назад» с корня вкладки, закрывает
 * выбор настройки выбором, ныряет на второй уровень и выходит оттуда двумя возвратами,
 * затем ищет «О программе» и политику. Враждебность здесь в том, что каждый возврат
 * проверяется на месте приземления, а не только на факте «экран сменился»: ошибка
 * в глубине стека оставила бы человека в разделе настроек или в истории.
 *
 * Выбор срока отложения держится не кадром, а состоянием строки: диалог закрыт,
 * а выбранный срок стоит в строке. Переход в политику конфиденциальности идёт
 * системным [androidx.compose.ui.platform.LocalUriHandler] — браузер вне приложения,
 * и автоматизация его не проходит; проверяется доступность строки, а сам переход
 * остаётся человеку.
 */
@HiltAndroidTest
class HostileSettingsTest : JourneyTest() {

    @Test
    fun thePersonReturnsFromEverySettingsLevelToWhereHeStarted() {
        launchApp()
        awaitRoot()
        capture("today")

        // 1. Корень вкладки: системная «Назад» ведёт на «Сегодня», а не вглубь настроек.
        openSettings()
        capture("settings-root")
        pressBack()
        waitUntil(SCREEN_TIMEOUT_MS, "возврата на «Сегодня» с корня настроек") {
            exists(TAG_TODAY_SCREEN) && !exists(TAG_SETTINGS_SCREEN)
        }

        // 2. Срок отложения: выбор закрывает окно и остаётся показанным в строке.
        openSettings()
        scrollTo(TAG_SETTINGS_SCREEN, TAG_SNOOZE_ROW)
        click(TAG_SNOOZE_ROW)
        waitForTag(TAG_SNOOZE_DIALOG)
        // Диалог выбора срока — своё окно, и корней на экране два; кадр берётся его
        // собственным корнем, иначе onRoot() неоднозначен.
        capture("snooze-open", compose.onNode(isDialog()))
        val chosen = snoozeLabel(THIRTY_MINUTES)
        compose.onNodeWithText(chosen, useUnmergedTree = true).performClick()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия окна выбора и срока «$chosen» в строке") {
            !exists(TAG_SNOOZE_DIALOG) && textExists(chosen)
        }
        capture("snooze-chosen")

        // 3. Второй уровень: первый возврат ведёт на настройки, второй — на «Сегодня».
        openSecondLevel()
        capture("history-from-settings")
        pressBack()
        waitUntil(SCREEN_TIMEOUT_MS, "возврата на настройки из истории") {
            exists(TAG_SETTINGS_SCREEN) && !exists(TAG_HISTORY_SCREEN)
        }
        pressBack()
        waitUntil(SCREEN_TIMEOUT_MS, "возврата на «Сегодня» из настроек") {
            exists(TAG_TODAY_SCREEN) && !exists(TAG_SETTINGS_SCREEN)
        }

        // 4. «О программе» и политика: строки доступны; за строкой — системный браузер.
        openSettings()
        scrollSettingsTo(ui(SettingsR.string.settings_about_vendor))
        assertTrue("the about plate must name the vendor", textExists(ui(SettingsR.string.settings_about_vendor)))
        assertTrue("the about plate must offer the site", textExists(ui(SettingsR.string.settings_about_site)))
        scrollSettingsTo(ui(SettingsR.string.settings_policy))
        assertTrue("the privacy policy row must be available", textExists(ui(SettingsR.string.settings_policy)))
        capture("about-and-policy")
    }

    /**
     * Открыть второй уровень настроек — «Историю приёмов».
     *
     * Строка второго уровня помечена `internal` в чужом модуле, а `internal` app-проверке
     * не виден; строка адресуется своим названием — тем же, что видит человек.
     */
    private fun openSecondLevel() {
        val history = ui(SettingsR.string.settings_history)
        scrollSettingsTo(history)
        compose.onNodeWithText(history, useUnmergedTree = true).performClick()
        waitForTag(TAG_HISTORY_SCREEN)
    }

    /**
     * Прокрутить настройки до строки без доступного тега.
     *
     * Строки раздела «Данные» и плашка «О программе» помечены `internal`, поэтому
     * прокрутка идёт по тексту, а не по тегу.
     */
    private fun scrollSettingsTo(text: String) {
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_SETTINGS_SCREEN)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    /** Подпись срока отложения: та же строка, что стоит в выбранной строке настроек. */
    private fun snoozeLabel(minutes: Int): String =
        targetContext.resources.getQuantityString(SettingsR.plurals.settings_snooze_minutes, minutes, minutes)

    private companion object {
        /** Срок, отличный от значения по умолчанию: выбор виден по строке. */
        const val THIRTY_MINUTES = 30
    }
}

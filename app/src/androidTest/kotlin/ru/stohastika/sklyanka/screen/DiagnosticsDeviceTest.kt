package ru.stohastika.sklyanka.screen

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_BLOCK
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_DELIVERY
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_EXPORT
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSIONS
import ru.stohastika.sklyanka.feature.reminder.TAG_VERDICT
import ru.stohastika.sklyanka.feature.reminder.TAG_WARNING
import ru.stohastika.sklyanka.feature.reminder.TAG_WEEK_SUMMARY

/**
 * I — диагностика напоминаний (SKL-98, TDD §6; первый уровень SKL-102, ОВ-126).
 *
 * Экран открывается прямым намерением продукта и показывает первый уровень: вердикт,
 * «Разрешения», «Проверка доставки», «Напоминания» и служебную строку «Выгрузить журнал».
 * Технические сведения (журнал, счётчики пересборки, список будильников) на первый
 * уровень не выходят: они уезжают файлом журнала по требованию (НФТ-51, SKL-106).
 *
 * Экспорт журнала проверкой не запускается: нажатие поднимает системный выбор места
 * (SAF), а он принадлежит платформе, не приложению. Предупреждения о разрешениях
 * не утверждаются — они зависят от образа, а не от сценария.
 */
@HiltAndroidTest
class DiagnosticsDeviceTest : DeviceScreenTest() {

    @Test
    fun theFirstLevelShowsVerdictPermissionsDeliveryRemindersAndExport() {
        launchScreen(DiagnosticsActivity.intentFor(targetContext))
        awaitActivity(DiagnosticsActivity::class.java)

        waitForTag(TAG_DIAGNOSTICS)
        waitUntil(SCREEN_TIMEOUT_MS, "вердикта проверки") {
            exists(TAG_VERDICT) || exists(TAG_WARNING)
        }
        waitForTag(TAG_PERMISSIONS)
        // Блоки ниже разрешений могут стоять за экраном, и ленивый список их не компонует:
        // у образа, где доступ к политике «Не беспокоить» не читается, группа разрешений
        // длиннее на строку, и `diagnostics_check_block` в дерево не попадает вовсе
        // (проверено на образе `default` API 35: узел появляется после одного проматывания).
        // Поэтому к блоку сначала прокручивают — тем же приёмом, что ниже к сводке недели.
        scrollTo(TAG_DIAGNOSTICS, TAG_CHECK_BLOCK)
        waitForTag(TAG_CHECK_DELIVERY)
        capture("diagnostics")

        scrollTo(TAG_DIAGNOSTICS, TAG_WEEK_SUMMARY)
        scrollTo(TAG_DIAGNOSTICS, TAG_JOURNAL_EXPORT)
        capture("diagnostics-journal-export")
    }
}

package ru.stohastika.sklyanka.screen

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_BLOCK
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_DELIVERY
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSIONS
import ru.stohastika.sklyanka.feature.reminder.TAG_REPORT_FOOTER
import ru.stohastika.sklyanka.feature.reminder.TAG_VERDICT
import ru.stohastika.sklyanka.feature.reminder.TAG_WARNING
import ru.stohastika.sklyanka.feature.reminder.TAG_WEEK_SUMMARY

/**
 * I — диагностика напоминаний (SKL-98, TDD §6; первый уровень SKL-102, ОВ-126).
 *
 * Экран открывается прямым намерением продукта и показывает первый уровень: вердикт,
 * «Разрешения», «Проверка доставки», «Напоминания» и низ «Сообщить о проблеме». Технические
 * сведения (журнал, счётчики пересборки, список будильников) на первый уровень не выходят:
 * они уезжают в отчёт разработчику.
 *
 * Экспорт журнала проверкой не запускается: системного выбора места (SAF) в этой волне нет
 * (задача later, `tech-options.md` §3). Предупреждения о разрешениях не утверждаются —
 * они зависят от образа, а не от сценария.
 */
@HiltAndroidTest
class DiagnosticsDeviceTest : DeviceScreenTest() {

    @Test
    fun theFirstLevelShowsVerdictPermissionsDeliveryRemindersAndReport() {
        launchScreen(DiagnosticsActivity.intentFor(targetContext))
        awaitActivity(DiagnosticsActivity::class.java)

        waitForTag(TAG_DIAGNOSTICS)
        waitUntil(SCREEN_TIMEOUT_MS, "вердикта проверки") {
            exists(TAG_VERDICT) || exists(TAG_WARNING)
        }
        waitForTag(TAG_PERMISSIONS)
        waitForTag(TAG_CHECK_BLOCK)
        waitForTag(TAG_CHECK_DELIVERY)
        capture("diagnostics")

        scrollTo(TAG_DIAGNOSTICS, TAG_WEEK_SUMMARY)
        scrollTo(TAG_DIAGNOSTICS, TAG_REPORT_FOOTER)
        capture("diagnostics-report")
    }
}

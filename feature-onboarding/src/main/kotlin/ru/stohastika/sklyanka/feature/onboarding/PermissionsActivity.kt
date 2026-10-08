package ru.stohastika.sklyanka.feature.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant
import javax.inject.Inject
import android.graphics.Color as AndroidColor

/**
 * Повторный вход на экран «Настроить надёжность» (НАП-21, ОВ-106, ОВ-107).
 *
 * Своя Activity, а не экран в графе навигации, по той же причине, что у диагностики (SKL-13):
 * постоянная точка входа — пункт настроек, а решение, один это экран с диагностикой или два
 * разных, не принято (ОВ-48). До ответа экран не встраивается в чужой граф навигации,
 * чтобы потом не выковыривать его оттуда.
 *
 * Экран тот же, что при первом запуске, — отличается источником: индикатор шагов
 * принадлежит онбордингу и при повторном входе не показывается. Баннер перерыва приводит
 * сюда с моментом перерыва (источник `OUTAGE_BANNER`): экран открывается на первом
 * незакрытом шаге и называет перерыв в плашке (ОВ-107).
 */
@AndroidEntryPoint
class PermissionsActivity : ComponentActivity() {

    /**
     * Язык интерфейса закрепляется до первого обращения к ресурсам (НФТ-43а, `ADR-0008`).
     *
     * Без этого Android применил бы к русским строкам правила множественного числа языка
     * телефона, а даты вывел бы его словами.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withInterfaceLocale())
    }

    private val viewModel: OnboardingViewModel by viewModels()

    /**
     * Куда ведёт «Проверить доставку» из плашки «Всё настроено» (SKL-86, ОВ-108).
     *
     * Подставляется `app`: экран проверки напоминаний живёт в `feature-reminder`,
     * и `feature-onboarding` не имеет права его видеть.
     */
    @Inject
    lateinit var deliveryCheckEntry: DeliveryCheckEntry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Светлый экран — тёмные значки системных панелей (SKL-72 §4).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        val source = intent.source()
        val outageSince = intent.outageSince()
        setContent {
            SklyankaTheme {
                PermissionsRoute(
                    viewModel = viewModel,
                    source = source,
                    outageSince = outageSince,
                    // Отдельный экран: и «Дальше», и «Пропустить», и «Готово» возвращают
                    // туда, откуда пришли. Обещать подключение часов из настроек нечем — это
                    // шаг онбординга, а не пункт настроек (SKL-17).
                    onExit = { finish() },
                    onCheckDelivery = { startActivity(deliveryCheckEntry.intentFor(this)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Состояние читается из системы при каждом показе (НФТ-52): человек мог уйти
        // выдавать разрешение в системные настройки и вернуться, и экран обязан это заметить
        // без перезахода (критерий приёмки 12).
        viewModel.refresh()
    }

    private fun Intent.source(): OnboardingSource {
        val name = getStringExtra(EXTRA_SOURCE) ?: return OnboardingSource.SETTINGS
        return runCatching { OnboardingSource.valueOf(name) }.getOrDefault(OnboardingSource.SETTINGS)
    }

    private fun Intent.outageSince(): Instant? = getLongExtra(EXTRA_OUTAGE_SINCE, NO_MOMENT)
        .takeIf { it != NO_MOMENT }
        ?.let(Instant::ofEpochMilli)

    companion object {
        private const val EXTRA_SOURCE = "ru.stohastika.sklyanka.extra.SETUP_SOURCE"
        private const val EXTRA_OUTAGE_SINCE = "ru.stohastika.sklyanka.extra.OUTAGE_SINCE"
        private const val NO_MOMENT = -1L

        /**
         * Намерение входа на экран.
         *
         * @param source откуда пришёл человек: решает фокус, индикатор шагов и журнал.
         * @param outageSince момент перерыва для баннера «Исправить» (ОВ-107).
         */
        fun intentFor(
            context: Context,
            source: OnboardingSource = OnboardingSource.SETTINGS,
            outageSince: Instant? = null,
        ): Intent = Intent(context, PermissionsActivity::class.java)
            .putExtra(EXTRA_SOURCE, source.name)
            .putExtra(EXTRA_OUTAGE_SINCE, outageSince?.toEpochMilli() ?: NO_MOMENT)
    }
}

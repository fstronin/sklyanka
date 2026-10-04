package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.ScreenName
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import javax.inject.Inject
import android.graphics.Color as AndroidColor

/**
 * Экран диагностики напоминаний (НАП-37).
 *
 * Своя Activity, а не экран в графе навигации: постоянная точка входа — пункт настроек
 * «Разрешения и автозапуск» (SKL-20), и она же вход в онбординг разрешений (SKL-14).
 * Один это экран с двумя частями или два разных — открытый вопрос (ОВ-48), и до ответа
 * диагностика не встраивается в чужой граф навигации, чтобы потом не выковыривать её оттуда.
 */
@AndroidEntryPoint
class DiagnosticsActivity : ComponentActivity() {

    /**
     * Язык интерфейса закрепляется до первого обращения к ресурсам (НФТ-43а, `ADR-0008`).
     *
     * Без этого Android применил бы к русским строкам правила множественного числа языка
     * телефона, а даты вывел бы его словами.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withInterfaceLocale())
    }

    private val viewModel: DiagnosticsViewModel by viewModels()

    /**
     * Куда ведёт «Пройти настройку заново» (НАП-21).
     *
     * Подставляется `app`: экран разрешений принадлежит SKL-14, и экранные модули друг
     * от друга не зависят (`ADR-0007`).
     */
    @Inject
    lateinit var setupEntry: ReminderSetupEntry

    /**
     * Куда ведёт «Исправить» у строки разрешения (ОВ-49).
     *
     * Системный `Intent` собирает `app`: `feature-reminder` не зависит от онбординга
     * и не знает экранов системных настроек (`ADR-0007`).
     */
    @Inject
    lateinit var settingsFixer: ReminderSettingsFixer

    /** Канал событий: экран — отдельная Activity, и корень приложения его не видит (SKL-82). */
    @Inject
    lateinit var analytics: Analytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.REMINDERS_CHECK))
        // Светлый экран — тёмные значки системных панелей (SKL-72 §4).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            SklyankaTheme {
                val state by viewModel.state.collectAsState()
                DiagnosticsScreen(
                    state = state,
                    onRestartSetup = {
                        startActivity(setupEntry.intentFor(this@DiagnosticsActivity))
                    },
                    // Исход фиксера не глотается: экран покажет запасную инструкцию,
                    // если системный раздел не открылся (НФТ-8, ОВ-106).
                    onFixPermission = { permission ->
                        viewModel.onFixResult(permission, settingsFixer.fix(permission))
                    },
                    onCheckDelivery = viewModel::onCheckDelivery,
                    onDismissCheckSheet = viewModel::onDismissCheckSheet,
                    onStartDeliveryCheck = viewModel::onStartDeliveryCheck,
                    onCancelDeliveryCheck = viewModel::onCancelDeliveryCheck,
                    // Исход прочитан — он подтверждается до действия, иначе вернулся бы
                    // баннером при следующем входе (ОВ-108).
                    onAcknowledgeDeliveryCheck = viewModel::onAcknowledgeDeliveryCheck,
                    onFixDeliveryCheck = {
                        viewModel.onAcknowledgeDeliveryCheck()
                        startActivity(setupEntry.intentFor(this@DiagnosticsActivity))
                    },
                    onReportOpen = viewModel::onReportOpen,
                    onReportCommentChange = viewModel::onReportCommentChange,
                    onReportSend = viewModel::onReportSend,
                    onReportDismiss = viewModel::onReportDismiss,
                    onBack = ::finish,
                    toLocal = viewModel::local,
                )
            }
        }
        // Вход из плашки «Всё настроено» (SKL-84, ОВ-108): экран открывается с уже
        // поднятой шторкой, чтобы запуск проверки оставался одним нажатием.
        if (intent.getBooleanExtra(EXTRA_OPEN_DELIVERY_CHECK, false)) {
            viewModel.onCheckDelivery()
        }
    }

    override fun onResume() {
        super.onResume()
        // Состояние разрешений читается из системы при каждом показе (НФТ-52): человек
        // мог уйти в системные настройки и вернуться, и экран обязан это заметить.
        viewModel.refresh()
    }

    companion object {
        private const val EXTRA_OPEN_DELIVERY_CHECK = "ru.stohastika.sklyanka.diagnostics.open_delivery_check"

        /**
         * Намерение экрана «Проверка напоминаний».
         *
         * [openDeliveryCheck] — вход из плашки «Всё настроено» (SKL-86): шторка проверки
         * доставки уже открыта, и запуск остаётся одним нажатием. Второй копии шторки
         * и второго дома результата не заводится (ОВ-108).
         */
        fun intentFor(context: Context, openDeliveryCheck: Boolean = false): Intent =
            Intent(context, DiagnosticsActivity::class.java)
                .putExtra(EXTRA_OPEN_DELIVERY_CHECK, openDeliveryCheck)
    }
}

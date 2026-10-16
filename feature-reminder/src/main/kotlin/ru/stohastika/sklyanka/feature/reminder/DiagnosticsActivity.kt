package ru.stohastika.sklyanka.feature.reminder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalDocument
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
 *
 * **Системный выбор места для файла журнала живёт здесь** (НФТ-17, SKL-106): результат
 * чужой активности умеет получить только composable, а домен про `Uri` не знает. Отмена
 * диалога приходит в модель как `null` — и исходом не является. Запуск — только при
 * наличии обработчика (тот же приём, что у отчёта врачу, SKL-46): отсутствие `DocumentsUI`
 * обязано стать честной плашкой, а не падением.
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

    /**
     * Акцентная тема «Про» (Р-31, §2.5): экран обязан нести выбор человека, а не базовую
     * «оливу» — тем же швом, каким его читает корневая активность (SKL-120).
     */
    @Inject
    lateinit var observeAccentTheme: ObserveAccentThemeUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Значки панелей читают среду у **системы**, а не у композиции (SKL-72 §4, Р-23).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { resources ->
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { resources ->
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            },
        )
        setContent {
            // Среда — из системной настройки (Р-23), тема — из выбора человека:
            // экран состояния базы не исключение, у него те же роли, что у всех (§9).
            val accentTheme by observeAccentTheme().collectAsState(initial = AccentTheme.DEFAULT)
            SklyankaTheme(theme = accentTheme, dark = isSystemInDarkTheme()) {
                val state by viewModel.state.collectAsState()
                val journalExportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument(ReminderJournalDocument.MEDIA_TYPE),
                ) { uri ->
                    viewModel.onJournalExportResult(uri?.toString())
                }
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
                    onJournalExport = {
                        val name = viewModel.journalExportFileName()
                        if (canCreateJournalDocument(name)) {
                            try {
                                journalExportLauncher.launch(name)
                            } catch (_: ActivityNotFoundException) {
                                // Обработчик исчез между проверкой и запуском: исход тот
                                // же — честная плашка, а не падение.
                                viewModel.onJournalExportUnavailable()
                            }
                        } else {
                            viewModel.onJournalExportUnavailable()
                        }
                    },
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

    /**
     * Есть ли обработчик системного выбора места для файла журнала.
     *
     * Намерение-проба собирается тем же контрактом и с тем же именем файла, что и запуск:
     * проверяемое и запускаемое намерения обязаны совпадать, иначе проверка отвечала бы
     * про другое намерение (тот же приём, что `DocumentSaveSupport`, SKL-46). С Android 11
     * запросы видимости фильтруются, поэтому действие с mime объявлено в `<queries>`
     * манифеста.
     */
    private fun canCreateJournalDocument(fileName: String): Boolean {
        val probe = ActivityResultContracts.CreateDocument(ReminderJournalDocument.MEDIA_TYPE)
            .createIntent(this, fileName)
        return probe.resolveActivity(packageManager) != null
    }

    companion object {
        private const val EXTRA_OPEN_DELIVERY_CHECK = "ru.stohastika.sklyanka.diagnostics.open_delivery_check"

        /**
         * [openDeliveryCheck] — вход из плашки «Всё настроено» (SKL-86): шторка проверки
         * поднимается сразу, запуск остаётся одним нажатием.
         */
        fun intentFor(context: Context, openDeliveryCheck: Boolean = false): Intent =
            Intent(context, DiagnosticsActivity::class.java)
                .putExtra(EXTRA_OPEN_DELIVERY_CHECK, openDeliveryCheck)
    }
}

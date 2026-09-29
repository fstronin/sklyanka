package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalDocument
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import javax.inject.Inject

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SklyankaTheme {
                val state by viewModel.state.collectAsState()
                // Системный выбор места (SAF) живёт здесь, а не в `ViewModel` и не в домене:
                // это результат чужой активности, и получить его может только composable.
                // Наружу уходит непрозрачная ссылка строкой, разрешений на внешнее
                // хранилище приложение не запрашивает (НФТ-17).
                val saveLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument(ReminderJournalDocument.MEDIA_TYPE),
                ) { uri -> uri?.let { viewModel.onExportTo(it.toString()) } }
                DiagnosticsScreen(
                    state = state,
                    onCheckNow = viewModel::onCheckNow,
                    onTestReminder = viewModel::onTestReminder,
                    onExportJournal = { saveLauncher.launch(state.exportFileName) },
                    onRestartSetup = {
                        startActivity(setupEntry.intentFor(this@DiagnosticsActivity))
                    },
                    onBack = ::finish,
                    toLocal = viewModel::local,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Состояние разрешений читается из системы при каждом показе (НФТ-52): человек
        // мог уйти в системные настройки и вернуться, и экран обязан это заметить.
        viewModel.refresh()
    }

    companion object {
        fun intentFor(context: Context): Intent = Intent(context, DiagnosticsActivity::class.java)
    }
}

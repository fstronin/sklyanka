package ru.heuri.sklyanka.feature.reminder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

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

    private val viewModel: DiagnosticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SklyankaTheme {
                val state by viewModel.state.collectAsState()
                DiagnosticsScreen(
                    state = state,
                    onCheckNow = viewModel::onCheckNow,
                    onTestReminder = viewModel::onTestReminder,
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

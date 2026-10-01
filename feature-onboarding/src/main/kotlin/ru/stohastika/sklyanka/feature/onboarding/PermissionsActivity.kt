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
import android.graphics.Color as AndroidColor

/**
 * Повторный вход на экран разрешений (НАП-21, критерий приёмки 15).
 *
 * Своя Activity, а не экран в графе навигации, по той же причине, что у диагностики (SKL-13):
 * постоянная точка входа — пункт настроек «Разрешения и автозапуск», и решение, один это
 * экран с диагностикой или два разных, не принято (ОВ-48). До ответа экран не встраивается
 * в чужой граф навигации, чтобы потом не выковыривать его оттуда.
 *
 * Экран тот же, что при первом запуске, — отличается только источником: индикатор шагов
 * принадлежит онбордингу и при входе из настроек не показывается.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Светлый экран — тёмные значки системных панелей (SKL-72 §4).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            SklyankaTheme {
                PermissionsRoute(
                    viewModel = viewModel,
                    source = OnboardingSource.SETTINGS,
                    // Отдельный экран: и «Дальше», и «Пропустить» возвращают туда, откуда
                    // пришли. Обещать подключение часов из настроек нечем — это шаг
                    // онбординга, а не пункт настроек (SKL-17).
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Состояние читается из системы при каждом показе (НФТ-52): человек мог уйти
        // выдавать разрешение в системные настройки и вернуться, и экран обязан это заметить
        // без перезахода (критерии приёмки 4 и 16).
        viewModel.refresh()
    }

    companion object {
        fun intentFor(context: Context): Intent = Intent(context, PermissionsActivity::class.java)
    }
}

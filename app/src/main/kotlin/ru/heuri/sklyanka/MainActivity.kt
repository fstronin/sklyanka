package ru.heuri.sklyanka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import ru.heuri.sklyanka.feature.today.TodayScreen
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

/**
 * Единственная Activity приложения.
 *
 * Навигация — заглушка: экранов, между которыми можно переходить, пока нет.
 * Настоящая навигация приходит вместе с экранами этапа 4.
 *
 * Тема ставится здесь и ровно один раз — в корне (TDD §5.1 задачи SKL-05). Профиль
 * ей пока не передаётся: по умолчанию это профиль владельца, и до появления
 * переключателя профиля (SKL-15) индикации чужого профиля нет, потому что ветка
 * тонирования не выполняется (ПРФ-16).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SklyankaTheme {
                TodayScreen()
            }
        }
    }
}

package ru.heuri.sklyanka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import ru.heuri.sklyanka.feature.today.TodayScreen
import ru.heuri.sklyanka.ui.token.SklyankaTheme

/**
 * Единственная Activity приложения.
 *
 * Навигация — заглушка: экранов, между которыми можно переходить, пока нет.
 * Настоящая навигация приходит вместе с экранами этапа 4.
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

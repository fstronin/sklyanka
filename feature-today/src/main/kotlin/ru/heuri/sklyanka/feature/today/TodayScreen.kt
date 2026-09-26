package ru.heuri.sklyanka.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Пустой экран.
 *
 * Это не заготовка «Сегодня» (SKL-08), а проверка того, что приложение
 * запускается и Compose отрисовывает содержимое. Продуктового поведения здесь нет
 * намеренно: карточки приёмов, статусы и отметки приходят со схемой данных.
 */
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().testTag(TODAY_SCREEN_TEST_TAG))
    }
}

/** Опора экранного теста: текста на пустом экране нет, зацепиться больше не за что. */
const val TODAY_SCREEN_TEST_TAG = "today_screen"

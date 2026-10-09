package ru.stohastika.sklyanka.startup

import androidx.compose.runtime.Composable

/**
 * Экран закрытой базы: рисует причину и ничего больше (`ADR-0012` пп. 3, 7).
 *
 * Разбор причины вынесен из правила кадров отдельной композицией: правило отвечает «что
 * показывать», а не «какими словами», и держать в нём две ветки текста значило бы смешать
 * решение с оформлением. Ни «Сегодня», ни настройки здесь не рисуются ни в одной ветке:
 * любой экран продукта открыл бы базу.
 */
@Composable
fun DatabaseBlockedScreen(block: DatabaseBlock) {
    when (block) {
        DatabaseBlock.TooNew -> DatabaseTooNewScreen()
        is DatabaseBlock.NotEnoughSpace -> DatabaseNotEnoughSpaceScreen(
            missingMegabytes = block.budget.missingMegabytes,
        )
    }
}

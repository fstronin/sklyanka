package ru.stohastika.sklyanka.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экран истории приёмов (`design/screens/History.dc.html`).
 *
 * Состояния не держит вовсе: всё приходит в [state], наружу уходят только намерения.
 * Порядок блоков — за макетом: шапка со стрелкой «Назад» и кнопкой «Отчёт врачу»,
 * заголовок «История», три плитки сводки, календарь месяца, блок «По лекарствам»
 * и строка-вывод. Нижней панели нет: это экран второго уровня.
 *
 * **Индикация чужого профиля действует и здесь, хотя её нет в макете.**
 * `History.dc.html` не несёт её вовсе, и `docs/spec/03-screens.md` называет это прямо:
 * «Индикации профиля на этом экране в макете нет, хотя спека требует её на экранах
 * чужого профиля». ПРФ-11 и ПРФ-12 главнее макета — экран, на котором индикация
 * исчезает, это дыра в защите от чужой отметки.
 *
 * Ни одного признака этот экран при этом не рисует сам, и это не упущение, а условие:
 * полосу и подкраску даёт обёртка `ProfileChrome` в корне приложения, надпись
 * «Профиль · Имя» и несворачиваемый баннер — общая шапка `ScreenHeader`, акцент —
 * тема. Собранные здесь заново, они исчезли бы с этого экрана при первой же правке
 * оформления профиля, и исчезли бы молча (ПРФ-12). Шестой признак — подпись
 * «отметили вы» — относится к отметке, а не к экрану, и живёт в блоке «По лекарствам».
 *
 * Экран прокручивается целиком: при крупном системном шрифте блоки растут по высоте,
 * и кнопка «Отчёт врачу» обязана остаться достижимой (НФТ-31).
 *
 * **Шторка отчёта лежит поверх содержимого, а не рядом с ним.** Корень экрана — `Box`:
 * соседом в столбце шторка получала нулевой остаток высоты (`Column` меряет детей без
 * веса после `fillMaxSize`-сына), и на устройстве «Отчёт врачу» строил документ молча —
 * шторка не предъявлялась вовсе (находка прогона SKL-41, фикс SKL-44). Тот же способ
 * наложения, что у снэкбара «Сегодня» (`TodayScreen.kt`).
 */
@Composable
fun HistoryScreen(state: HistoryUiState, onIntent: (HistoryIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_HISTORY_SCREEN),
    ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            ScreenHeader(
                labels = ScreenHeaderLabels(title = stringResource(R.string.history_title)),
                kind = ScreenHeaderKind.Nested,
                leading = {
                    GlyphButton(
                        contentDescription = stringResource(UiR.string.cd_back),
                        onClick = { onIntent(HistoryIntent.Back) },
                    ) {
                        ChevronGlyph(color = colors.text, pointsLeft = true, size = SklyankaSizes.IconPlateGlyph)
                    }
                },
                trailing = {
                    SecondaryButton(
                        text = stringResource(R.string.history_report_action),
                        onClick = { onIntent(HistoryIntent.ReportRequested) },
                        modifier = Modifier.testTag(TAG_REPORT_ACTION),
                    )
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SklyankaSpacing.Section)
                    .padding(bottom = SklyankaSpacing.Screen),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
            ) {
                SummaryTiles(state)
                CalendarMonth(month = state.month, days = state.period.days, onIntent = onIntent)
                // Блок «По лекарствам» скрыт, когда за месяц нет ни одной записи: пустая
                // карточка с заголовком сообщает человеку, что экран сломан.
                if (state.period.byMedication.isNotEmpty()) {
                    MedicationBreakdown(rows = state.period.byMedication, insightTime = state.insightTime)
                }
            }
        }
        ReportSheetHost(report = state.report, outcome = state.reportOutcome, onIntent = onIntent)
    }
}

/**
 * Три плитки сводки: «принято вовремя», «дней без пропусков», «пропусков за месяц».
 *
 * Плитка «принято вовремя» показывает прочерк, и это проектное решение, а не заглушка:
 * порог «вовремя» не согласован (ОВ-4), а выдуманное число человек покажет врачу.
 */
@Composable
private fun SummaryTiles(state: HistoryUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(TAG_SUMMARY),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
    ) {
        SummaryTile(
            value = state.period.onTimeShare.label(),
            caption = stringResource(R.string.history_tile_on_time),
            description = null,
            modifier = Modifier.weight(1f).testTag(TAG_TILE_ON_TIME),
        )
        SummaryTile(
            value = tileValue(state.period.daysWithoutMisses, state.isEmptyMonth),
            caption = stringResource(R.string.history_tile_days_without_misses),
            description = daysWithoutMissesDescription(state.period.daysWithoutMisses)
                .takeIf { !state.isEmptyMonth },
            modifier = Modifier.weight(1f).testTag(TAG_TILE_DAYS_WITHOUT_MISSES),
        )
        SummaryTile(
            value = tileValue(state.period.missesInPeriod, state.isEmptyMonth),
            caption = stringResource(R.string.history_tile_misses),
            description = missesDescription(state.period.missesInPeriod).takeIf { !state.isEmptyMonth },
            modifier = Modifier.weight(1f).testTag(TAG_TILE_MISSES),
        )
    }
}

@Composable
private fun SummaryTile(value: String, caption: String, description: String?, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .background(colors.surface, SklyankaShapes.Large)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Large)
            .padding(horizontal = SklyankaSpacing.Medium, vertical = SklyankaSpacing.Large)
            .then(
                // Читалке нужен один узел «9 дней без пропусков», а не число и подпись
                // по отдельности (НФТ-30). Там, где числа нет, остаётся видимый текст.
                if (description == null) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics {
                        contentDescription = description
                    }
                },
            ),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        Text(text = value, style = SklyankaType.Stat, color = colors.text)
        Text(text = caption, style = SklyankaType.Micro, color = colors.textMuted)
    }
}

const val TAG_HISTORY_SCREEN = "history_screen"
const val TAG_REPORT_ACTION = "history_report_action"
const val TAG_SUMMARY = "history_summary"
const val TAG_TILE_ON_TIME = "history_tile_on_time"
const val TAG_TILE_DAYS_WITHOUT_MISSES = "history_tile_days_without_misses"
const val TAG_TILE_MISSES = "history_tile_misses"

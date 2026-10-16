package ru.stohastika.sklyanka.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.history.ReportScope
import ru.stohastika.sklyanka.ui.component.SklyankaBottomSheet
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.io.File

/**
 * Эталонные снимки экрана истории (SKL-24, критерий 5).
 *
 * По снимку на состояние: обычный месяц, пустой месяц, чужой профиль, открытый отчёт
 * (обычный, крупный шрифт, отмена, ошибка, успех) и увеличенный системный шрифт.
 * Состав шторки собирает настоящий композер — снимок ловит и разметку, и формулировки.
 *
 * Снимок ловит **изменение**, а не правильность: как экран выглядит на настоящем EMUI,
 * по-прежнему смотрят глазами на устройстве (`docs/archive/tasks/SKL-24/TDD.md §2.2`).
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenSnapshotTest {

    @Test
    fun theHistoryOfTheMonth() = capture("history") {
        HistoryScreen(state = HistoryStates.september(), onIntent = {})
    }

    @Test
    fun theMonthWithoutRecords() = capture("history-empty") {
        HistoryScreen(state = HistoryStates.emptyMonth(), onIntent = {})
    }

    /**
     * Чужой профиль: все признаки сразу (ПРФ-11).
     *
     * Снимок валится, если хотя бы один из них снимут, — именно за этим он здесь и стоит:
     * на макете `History.dc.html` индикации нет вовсе, и без снимка её исчезновение
     * заметил бы только тот, кто помнит, что она должна быть.
     */
    @Test
    fun theHistoryOfAForeignProfile() = capture("history-foreign-profile", wrapped = false) {
        ProfileChrome(ProfileChromeState.Foreign(name = PROFILE_NAME, colour = ProfileColor.Terracotta)) {
            HistoryScreen(state = HistoryStates.foreignProfile(), onIntent = {})
        }
    }

    @Test
    fun theDoctorReportOnTheScreen() = capture("history-report") {
        HistoryScreen(state = HistoryStates.withReport(), onIntent = {})
    }

    /** Шторка в состоянии расширенного выбора: вторая половина кадра «двух состояний выбора» (§5.15). */
    @Test
    fun theExtendedCompositionOnTheScreen() = capture("history-report-extended") {
        HistoryScreen(state = HistoryStates.withReport(scope = ReportScope.EXTENDED), onIntent = {})
    }

    /**
     * Шторка выгрузки истории: чипы, карточка состава, тихое пояснение, действия (§5.16).
     *
     * Кадр собирается настоящей шторкой в её слое: поверхность выгрузки хостит `app` (тем же
     * способом, что витрину), и снимок показывает её так, как её увидит человек.
     */
    @Test
    fun theExportSheetOnTheScreen() = capture("history-export", heightDp = TALLER, wrapped = false) {
        ExportSurface(ExportStates.withRecords())
    }

    /** Пустой период: фраза вместо числа записей, файл остаётся с шапкой (Р-33). */
    @Test
    fun theExportOfAnEmptyPeriodOnTheScreen() = capture("history-export-empty", heightDp = TALLER, wrapped = false) {
        ExportSurface(ExportStates.withoutRecords())
    }

    /** Места не хватило: сообщение первым элементом, содержимое остаётся (§5.16). */
    @Test
    fun theExportWithoutSpaceOnTheScreen() = capture("history-export-no-space", heightDp = TALLER, wrapped = false) {
        ExportSurface(ExportStates.withoutSpace())
    }

    /** Успех выгрузки: содержимое заменяется целиком — «Файл сформирован» и «Сохранён». */
    @Test
    fun theExportSavedOnTheScreen() = capture("history-export-saved", heightDp = TALLER, wrapped = false) {
        ExportSurface(ExportStates.saved())
    }

    /** Шторка выгрузки при 200 %: полотно растёт, средняя часть прокручивается (§6.3). */
    @Test
    fun theExportAtDoubleFontScale() = capture(
        "history-export-200",
        heightDp = TALLER,
        fontScale = SCALE_200,
        wrapped = false,
    ) {
        ExportSurface(ExportStates.withRecords())
    }

    /**
     * Шторка выгрузки в её слое — над экраном, как в продукте (§5.16, Р-53).
     *
     * За затемнением стоит «История»: строку выгрузки нажимают из настроек, но `feature-history`
     * не видит `feature-settings` (правило направлений `verifyModuleDependencies`), а кадр без
     * экрана за затемнением показывал бы прозрачную подложку вместо затемнения. Верхний слой
     * при этом тот же, что в продукте: шторка поверх корня.
     */
    @Composable
    private fun ExportSurface(state: ExportHistoryUiState) {
        SklyankaTheme {
            Box(modifier = Modifier.fillMaxWidth()) {
                HistoryScreen(state = HistoryStates.september(), onIntent = {})
                SklyankaBottomSheet(visible = true, onDismiss = {}) {
                    state.sheet?.let { sheet -> ExportHistorySheet(sheet, state.outcome, onIntent = {}) }
                }
            }
        }
    }

    /** Отмена системного диалога: сообщение первым элементом, состав и действия на месте. */
    @Test
    fun theCancelledReportOnTheScreen() = capture("history-report-cancelled") {
        HistoryScreen(state = HistoryStates.withReport(outcome = ReportOutcome.CANCELLED), onIntent = {})
    }

    @Test
    fun theFailedReportOnTheScreen() = capture("history-report-failed") {
        HistoryScreen(state = HistoryStates.withReport(outcome = ReportOutcome.FAILED_SAVE), onIntent = {})
    }

    /** Успех — отдельное содержимое: чипы, карточка, пояснение и действия убраны. */
    @Test
    fun theSuccessfulReportOnTheScreen() = capture("history-report-success") {
        HistoryScreen(state = HistoryStates.withReport(outcome = ReportOutcome.SHARED), onIntent = {})
    }

    /**
     * Системный шрифт 200 % (НФТ-31).
     *
     * Ровно та проверка, которую спецификация предписывает делать руками и которую
     * сегодня не делает никто. Снимок замечает изменение вёрстки; обрезан ли текст
     * на настоящем экране — смотрят глазами.
     */
    @Test
    fun theHistoryAtDoubleFontScale() = capture("history-font-scale-200", heightDp = TALLER, fontScale = SCALE_200) {
        HistoryScreen(state = HistoryStates.september(), onIntent = {})
    }

    /** Шторка отчёта при 200 %: полотно растёт, средняя часть прокручивается (§6.3). */
    @Test
    fun theReportAtDoubleFontScale() = capture("history-report-200", heightDp = TALLER, fontScale = SCALE_200) {
        HistoryScreen(state = HistoryStates.withReport(), onIntent = {})
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        heightDp: Int = HEIGHT,
        fontScale: Float = 1f,
        wrapped: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = heightDp)
                fontScale(fontScale)
            },
        ) {
            if (wrapped) {
                SklyankaTheme {
                    Box(modifier = Modifier.fillMaxWidth().background(SklyankaTheme.colors.background)) {
                        content()
                    }
                }
            } else {
                // Оформление профиля само ставит тему и фон: вторая обёртка закрасила бы
                // подкраску, ради которой снимок и снимается.
                content()
            }
        }
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390

        /** Экран длиннее телефона: снимок берёт его целиком. */
        const val HEIGHT = 1100

        /** На двойном шрифте блоки растут по высоте. */
        const val TALLER = 2000
        const val SCALE_200 = 2.0f

        const val PROFILE_NAME = "profile-b"
    }
}

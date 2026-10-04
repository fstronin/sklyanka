package ru.stohastika.sklyanka.acceptance.locale.matrix

import androidx.compose.runtime.Composable
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveScreen
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardScreen
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.feature.meds.list.MedicationListScreen
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckScreen
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsScreen
import ru.stohastika.sklyanka.feature.reminder.ReminderActions
import ru.stohastika.sklyanka.feature.reminder.ReminderScreen
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileScreen
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferScreen
import ru.stohastika.sklyanka.feature.settings.transfer.ReplaceConfirmScreen
import ru.stohastika.sklyanka.feature.settings.transfer.TransferReportScreen
import ru.stohastika.sklyanka.feature.settings.transfer.TransferSummaryScreen
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.startup.SplashScreen
import java.time.LocalDateTime

/**
 * Один экран локализационной матрицы (SKL-91).
 *
 * [heightDp] — высота окна, в котором экран компонуется: Lazy-списки дорендериваются
 * целиком только тогда, когда вьюпорт не меньше содержимого. Высоты взяты те же, что
 * у существующих прогонов SKL-09/10/13/18 (2400–4500 dp).
 */
internal data class MatrixScreen(val id: String, val heightDp: Int, val content: @Composable () -> Unit)

/**
 * Текстоёмкие экраны и состояния, которые прогоняются на всех шести языках.
 *
 * Список собран по приоритету: карточки лекарств, список, архив, форма, карточка
 * лекарства, напоминание, настройки, перенос, история и отчёт, онбординг, «Сегодня»
 * и пустые состояния. Экраны всех модулей существуют одновременно только в `app`
 * (`SKL-26/TDD.md §4.3`) — здесь и живёт каталог.
 */
internal object LocalizationScreenCatalog {

    private const val DEFAULT_HEIGHT_DP = 3000
    private const val TALL_HEIGHT_DP = 4500

    val screens: List<MatrixScreen> by lazy {
        val fixtures = LocalizationScreenFixtures
        val todayDay = fixtures.todayDay(fixtures.todayDayWorld())
        val todayDense = fixtures.todayDay(fixtures.todayDenseWorld())
        val diagnostics = fixtures.diagnosticsState(fixtures.diagnosticsWorld())
        listOf(
            MatrixScreen("today-empty", TALL_HEIGHT_DP) {
                TodayScreen(state = TodayUiState.Empty(fixtures.TODAY), onIntent = {})
            },
            MatrixScreen("today-day", TALL_HEIGHT_DP) {
                TodayScreen(state = todayDay, onIntent = {})
            },
            MatrixScreen("today-dense", TALL_HEIGHT_DP) {
                TodayScreen(state = todayDense, onIntent = {})
            },
            MatrixScreen("meds-list", DEFAULT_HEIGHT_DP) {
                MedicationListScreen(state = fixtures.medsList(), onIntent = {})
            },
            MatrixScreen("meds-long-name", DEFAULT_HEIGHT_DP) {
                MedicationListScreen(state = fixtures.medsList(longTitle = true), onIntent = {})
            },
            MatrixScreen("meds-empty", DEFAULT_HEIGHT_DP) {
                MedicationListScreen(state = fixtures.medsEmpty(), onIntent = {})
            },
            MatrixScreen("meds-archive", DEFAULT_HEIGHT_DP) {
                ArchiveScreen(state = fixtures.archive(), onIntent = {})
            },
            MatrixScreen("med-card", TALL_HEIGHT_DP) {
                MedicationCardScreen(state = fixtures.medCard(), onIntent = {})
            },
            MatrixScreen("med-form", TALL_HEIGHT_DP) {
                MedicationFormScreen(
                    state = fixtures.medForm(),
                    onIntent = {},
                    onCancel = {},
                    onPickDate = {},
                    onPickTime = { _, _ -> },
                )
            },
            MatrixScreen("history", TALL_HEIGHT_DP) {
                HistoryScreen(state = fixtures.history(), onIntent = {})
            },
            MatrixScreen("history-empty", DEFAULT_HEIGHT_DP) {
                HistoryScreen(state = fixtures.historyEmpty(), onIntent = {})
            },
            MatrixScreen("history-report", TALL_HEIGHT_DP) {
                HistoryScreen(state = fixtures.historyReport(), onIntent = {})
            },
            MatrixScreen("reminder", TALL_HEIGHT_DP) {
                ReminderScreen(state = fixtures.reminderCard(), actions = noReminderActions())
            },
            MatrixScreen("diagnostics", TALL_HEIGHT_DP) {
                DiagnosticsScreen(
                    state = diagnostics,
                    onRestartSetup = {},
                    onFixPermission = {},
                    onCheckDelivery = {},
                    onDismissCheckSheet = {},
                    onStartDeliveryCheck = {},
                    onCancelDeliveryCheck = {},
                    onAcknowledgeDeliveryCheck = {},
                    onFixDeliveryCheck = {},
                    onReportOpen = {},
                    onReportCommentChange = {},
                    onReportSend = {},
                    onReportDismiss = {},
                    onBack = {},
                    toLocal = { instant -> instant.atZone(fixtures.ZONE).toLocalDateTime() },
                )
            },
            MatrixScreen("delivery-check", DEFAULT_HEIGHT_DP) {
                DeliveryCheckScreen(at = LocalDateTime.of(2026, 9, 25, 8, 0), onAcknowledge = {})
            },
            MatrixScreen("permissions", TALL_HEIGHT_DP) {
                PermissionsScreen(
                    state = fixtures.permissions(),
                    actions = noPermissionActions(),
                    toLocal = { instant -> instant.atZone(fixtures.ZONE).toLocalDateTime() },
                )
            },
            MatrixScreen("settings", TALL_HEIGHT_DP) {
                SettingsScreen(state = fixtures.settings(), onIntent = {})
            },
            MatrixScreen("delete-profile", DEFAULT_HEIGHT_DP) {
                DeleteProfileScreen(
                    state = fixtures.deleteProfile(),
                    onIntent = {},
                    onSaveFile = {},
                    onDone = {},
                )
            },
            MatrixScreen("transfer", DEFAULT_HEIGHT_DP) {
                DataTransferScreen(
                    state = fixtures.transferOverview(),
                    onIntent = {},
                    onSave = {},
                    onPickFile = {},
                    onBack = {},
                )
            },
            MatrixScreen("transfer-summary", DEFAULT_HEIGHT_DP) {
                TransferSummaryScreen(
                    counts = LocalizationScreenFixtures.FILE_COUNTS,
                    state = fixtures.transferSummary(),
                    zone = fixtures.ZONE,
                    onContinue = {},
                    onCancel = {},
                )
            },
            MatrixScreen("transfer-replace", DEFAULT_HEIGHT_DP) {
                ReplaceConfirmScreen(
                    device = LocalizationScreenFixtures.DEVICE_COUNTS,
                    file = LocalizationScreenFixtures.FILE_COUNTS,
                    safetyCopyName = "sklyanka-before-replace.json",
                    confirmationWord = "",
                    onConfirmationWordChange = {},
                    onConfirm = {},
                    onCancel = {},
                )
            },
            MatrixScreen("transfer-report", DEFAULT_HEIGHT_DP) {
                TransferReportScreen(
                    report = fixtures.transferReport(),
                    medicationNames = mapOf("file-med" to "Magnesium B6"),
                    safetyCopyName = "sklyanka-before-replace.json",
                    onContinue = {},
                )
            },
            MatrixScreen("splash", DEFAULT_HEIGHT_DP) {
                SplashScreen()
            },
        )
    }

    private fun noReminderActions(): ReminderActions = ReminderActions(
        onTaken = {},
        onSkipped = {},
        onTakenAll = {},
        onSkippedAll = {},
        onSnooze = {},
    )

    private fun noPermissionActions(): PermissionsActions = PermissionsActions(
        onStepAction = {},
        onManualConfirmed = { _, _ -> },
        onNext = {},
        onSkip = {},
        onDone = {},
    )
}

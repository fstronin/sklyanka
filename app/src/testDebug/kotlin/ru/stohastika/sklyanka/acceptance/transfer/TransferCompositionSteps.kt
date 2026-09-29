package ru.stohastika.sklyanka.acceptance.transfer

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransfer
import ru.stohastika.sklyanka.acceptance.world.TransferFileBuilder
import ru.stohastika.sklyanka.data.transfer.JsonTransferCodec
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.transfer.ReminderSound
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferReport
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActivity
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_NOT_TRANSFERRED
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_REPORT
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_REPORT_CONTINUE
import ru.stohastika.sklyanka.feature.settings.transfer.TransferIntent
import ru.stohastika.sklyanka.feature.settings.transfer.TransferStep
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаги композиции переноса данных (SKL-19, группа `@composition`).
 *
 * Собирается настоящая связка продукта: маршрут переноса с настоящим `DataTransferViewModel`
 * на мире сценария и настоящий экран разрешений. «Тот же экран, что при первой установке» —
 * это `PermissionsActivity`, в которую ведут обе точки входа; проверка сверяет и её
 * намерение, и содержимое, а не пересказывает разметку.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TransferCompositionSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Лекарства устройства до замены: «которых нет в файле, не осталось». */
    private var deviceMedicationsBefore: List<Medication> = emptyList()

    /** Загруженный файл: из него берётся свой профиль, ставший активным. */
    private var loadedFile: TransferSnapshot? = null

    /** Отчёт, снятый с ViewModel до композиции: разборка маршрута начинает перенос заново. */
    private var reportAfterImport: TransferReport? = null
    private var namesAfterImport: Map<String, String> = emptyMap()
    private var safetyCopyAfterImport: String? = null

    // --- После замены: содержимое файла и экран разрешений (S16) -------------------------------

    @Допустим("^человек выбрал режим «Заменить всё»$")
    fun thePersonChoseTheReplaceMode() {
        TransferAppProbe.selectMode(world, TransferMode.REPLACE_ALL)
    }

    /**
     * Слово-подтверждение уходит в состояние тем же намерением, что шлёт поле (ОВ-67).
     *
     * Через собранный маршрут шаг не делается намеренно: маршрут на разборке зовёт
     * `Leave` (ПЕР-17), и перенос начался бы заново — файла бы уже не было. Само поле
     * и его связь с состоянием проверяет экранный прогон `feature-settings`.
     */
    @Допустим("^человек вводит в поле подтверждения «(.+)»$")
    fun thePersonTypesTheConfirmationWord(word: String) {
        TransferAppProbe.onIntent(world, TransferIntent.ConfirmationWordChanged(word))
    }

    @Допустим("^файл от (.+?) содержит три профиля, семь лекарств и историю с (.+)$")
    fun theFileCarriesThreeProfiles(date: String, historyFrom: String) {
        deviceMedicationsBefore = world.medications.all()
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.profile("Файл второй")
        builder.profile("Файл третий")
        val plan = listOf(FILE_SELF to THREE, "Файл второй" to TWO, "Файл третий" to TWO)
        plan.forEachIndexed { profileIndex, (profileKey, count) ->
            repeat(count) { index ->
                builder.medication(
                    key = "файловое лекарство $profileIndex-$index",
                    profileKey = profileKey,
                    name = "file-medication-$profileIndex-$index",
                    times = listOf(MORNING, EVENING),
                    startDate = world.dateOf(historyFrom),
                )
            }
        }
        val takenAt = world.dateOf(historyFrom).atTime(MORNING)
        builder.intake(
            medicationKey = "файловое лекарство 0-0",
            at = takenAt,
            status = IntakeStatus.TAKEN,
            actedAt = world.clock.momentOf(takenAt),
            source = IntakeSource.PHONE,
        )
        val snapshot = builder.build(exportedAt = exportedAt(date))
        loadedFile = snapshot
        world.transfer.prepare(snapshot)
        TransferAppProbe.chooseFileAndContinue(world)
    }

    @Когда("^загрузка завершается$")
    fun theImportFinishes() {
        TransferAppProbe.apply(world)
    }

    @Тогда("^на устройстве три профиля, семь лекарств и история с 1 января 2026$")
    fun theDeviceHoldsTheFile() {
        assertEquals(3, world.transfer.counts().profiles)
        assertEquals(7, world.medications.all().size)
        assertEquals(world.dateOf("1 января 2026"), world.transfer.counts().historyFrom)
    }

    @Тогда("^лекарств «Магний B6» и «Нурофен», которых нет в файле, на устройстве не осталось$")
    fun theOldMedicationsAreGone() {
        val oldNames = deviceMedicationsBefore.map { it.name }
        assertTrue(oldNames.isNotEmpty(), "the device must have had medications before the replacement")
        assertTrue(world.medications.all().none { it.name in oldNames })
    }

    @Тогда("^активным становится свой профиль из файла$")
    fun theSelfProfileOfTheFileBecomesActive() {
        val self = requireNotNull(loadedFile?.selfProfile) { "the loaded file must carry its own profile" }
        assertEquals(self.id, kotlinx.coroutines.runBlocking { world.profileSession.activeStore.current() })
    }

    @Тогда("^открывается экран настройки разрешений$")
    fun thePermissionsScreenOpens() {
        pressContinueOnTheReport()
        TransferAppProbe.askPermissions { onNodeWithTag(TAG_PERMISSIONS_SCREEN).assertIsDisplayed() }
        assertSameEntryAsFirstInstall()
    }

    // --- Отчёт о загрузке (S27) -----------------------------------------------------------------

    @Допустим("^загрузка в режиме «Заменить всё» завершилась на новом устройстве$")
    fun theImportFinishedOnANewDevice() {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medication(
            key = REPORT_FILE_MEDICATION,
            profileKey = FILE_SELF,
            name = REPORT_FILE_MEDICATION,
            times = listOf(MORNING),
            startDate = FILE_DATE,
        )
        val codec = JsonTransferCodec(world.clock.time)
        world.transfer.codec = codec
        // Звук из файла на этом устройстве не разрешается: отчёт обязан сказать,
        // что перенеслось не полностью (ПЕР-12), — иначе проверка «сказано» пуста.
        val exportedAt = world.clock.momentOf(FILE_DATE.atTime(EXPORT_HOUR, EXPORT_MINUTE))
        val raw = codec.encode(builder.build(exportedAt = exportedAt))
            .replace("\"sound\": \"${ReminderSound.DEFAULT.code}\"", "\"sound\": \"alien-signal\"")
        world.transfer.files.put(ScenarioTransfer.IMPORT_HANDLE, raw)
        TransferAppProbe.selectMode(world, TransferMode.REPLACE_ALL)
        TransferAppProbe.chooseFile(world, handle = ScenarioTransfer.IMPORT_HANDLE)
        TransferAppProbe.apply(world)
        val state = TransferAppProbe.state(world)
        assertEquals(TransferStep.REPORT, state.step, "the import must finish with a report")
        reportAfterImport = requireNotNull(state.report) { "the report must be shown" }
        namesAfterImport = state.medicationNames
        safetyCopyAfterImport = state.safetyCopyName
    }

    @Когда("^человек читает отчёт о загрузке$")
    fun thePersonReadsTheReport() {
        TransferAppProbe.askReport(
            report = requireNotNull(reportAfterImport),
            medicationNames = namesAfterImport,
            safetyCopyName = safetyCopyAfterImport,
        ) { onNodeWithTag(TAG_REPORT).assertIsDisplayed() }
    }

    @Тогда("^в отчёте названы числа: сколько профилей, лекарств и записей истории перенесено$")
    fun theReportNamesTheNumbers() {
        val report = requireNotNull(reportAfterImport) { "the report must be shown" }
        val lines = listOf(
            context.getString(R.string.transfer_report_profiles, report.profilesAdded),
            context.getString(R.string.transfer_report_medications, report.medicationsAdded),
            context.getString(R.string.transfer_report_intakes, report.intakesAdded),
        )
        TransferAppProbe.askReport(report, namesAfterImport, safetyCopyAfterImport) {
            lines.forEach { onNodeWithText(it).assertIsDisplayed() }
        }
    }

    @Тогда("^сказано, что не удалось перенести$")
    fun theReportNamesWhatFailed() {
        TransferAppProbe.askReport(requireNotNull(reportAfterImport), namesAfterImport, safetyCopyAfterImport) {
            onNodeWithTag(TAG_NOT_TRANSFERRED).assertIsDisplayed()
            onNodeWithText(context.getString(R.string.transfer_report_notice_sound)).assertIsDisplayed()
        }
    }

    @Тогда("^следом открывается тот же экран настройки разрешений, что и при первой установке$")
    fun theSamePermissionsScreenOpens() {
        pressContinueOnTheReport()
        TransferAppProbe.askPermissions { onNodeWithTag(TAG_PERMISSIONS_SCREEN).assertIsDisplayed() }
        assertSameEntryAsFirstInstall()
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun pressContinueOnTheReport() {
        TransferAppProbe.askRoute(world) { onNodeWithTag(TAG_REPORT_CONTINUE).performClick() }
        assertEquals(
            1,
            TransferAppProbe.permissionsOpened,
            "the report must lead to the permissions screen (ПЕР-25)",
        )
    }

    /**
     * Тот же экран разрешений, что и при первой установке.
     *
     * Обе точки входа — онбординг и перенос — собирают одно намерение
     * `PermissionsActivity.intentFor`, а содержимое экрана — `PermissionsScreen`.
     * Это и есть «тот же экран», а не похожий.
     */
    private fun assertSameEntryAsFirstInstall() {
        assertEquals(
            PermissionsActivity::class.java.name,
            PermissionsActivity.intentFor(context).component?.className,
        )
        val main = File(RepositoryLayout.root, "app/src/main/kotlin/ru/stohastika/sklyanka/MainActivity.kt").readText()
        assertTrue(
            main.contains("onOpenPermissions = { startActivity(PermissionsActivity.intentFor(this)) }"),
            "the transfer route must lead to the same activity as the first launch",
        )
        val module = File(
            RepositoryLayout.root,
            "app/src/main/kotlin/ru/stohastika/sklyanka/di/ReminderModule.kt",
        ).readText()
        assertTrue(
            module.contains("ReminderSetupEntry { context -> PermissionsActivity.intentFor(context) }"),
            "the onboarding entry must be the same activity",
        )
    }

    private fun exportedAt(date: String): java.time.Instant =
        world.clock.momentOf(world.dateOf(date).atTime(EXPORT_HOUR, EXPORT_MINUTE))

    private companion object {
        const val FILE_SELF = "файловый свой"
        const val REPORT_FILE_MEDICATION = "file-medication-report"

        const val TWO = 2
        const val THREE = 3
        const val EXPORT_HOUR = 18
        const val EXPORT_MINUTE = 14

        val MORNING: LocalTime = LocalTime.of(9, 0)
        val EVENING: LocalTime = LocalTime.of(21, 0)

        val FILE_DATE: LocalDate = LocalDate.of(2026, 10, 5)
    }
}

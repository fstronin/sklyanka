package ru.stohastika.sklyanka.feature.settings.transfer

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.domain.transfer.ApplyTransferUseCase
import ru.stohastika.sklyanka.domain.transfer.BuildTransferPlan
import ru.stohastika.sklyanka.domain.transfer.DeviceState
import ru.stohastika.sklyanka.domain.transfer.ExportDataUseCase
import ru.stohastika.sklyanka.domain.transfer.ReadTransferFileUseCase
import ru.stohastika.sklyanka.domain.transfer.TransferDecoded
import ru.stohastika.sklyanka.domain.transfer.ValidateSnapshot
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Экран «Перенос данных» вместе с его состоянием (SKL-60).
 *
 * Проверяет то, что увидел человек: состав устройства перечитывается при каждом
 * открытии экрана. Между заходами данные меняются — лекарства добавляются, курсы
 * уходят в архив, — и вчерашние числа в чипах — ложь в том месте, где человек
 * решает, что попадёт в его резервную копию.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class DataTransferRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val store = FakeTransferStore(
        DeviceState(
            profiles = listOf(TransferData.self()),
            medications = listOf(TransferData.medication(id = "first")),
            intakes = emptyList(),
        ),
    )
    private val files = FakeTransferFiles()
    private val settings = FakeSettingsStore()
    private val log = RecordingLog()
    private val replanner = FakeReplanner()
    private val ids = FixedIds()
    private val codec = FakeCodec(TransferDecoded.Loaded(TransferData.snapshot(), emptyList()))
    private val time = FixedTimeSource(TransferData.EXPORTED_AT, java.time.ZoneId.of("Europe/Moscow"))

    @Test
    fun theCountsAreReadAgainWhenTheScreenOpens() {
        val model = viewModel()
        // Экран был закрыт: первое чтение состава состоялось, дальше данные меняются.
        shadowOf(Looper.getMainLooper()).idle()
        store.state = DeviceState(
            profiles = listOf(TransferData.self()),
            medications = listOf(
                TransferData.medication(id = "first"),
                TransferData.medication(id = "second"),
                TransferData.medication(id = "third"),
            ),
            intakes = emptyList(),
        )

        composeRule.setContent {
            SklyankaTheme {
                DataTransferRoute(viewModel = model, onOpenPermissions = {}, onBack = {})
            }
        }

        val expected = context.resources.getQuantityString(R.plurals.transfer_chip_medications, 3, 3)
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText(expected, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun viewModel(): DataTransferViewModel {
        val export = ExportDataUseCase(
            store = store,
            settings = settings,
            codec = codec,
            files = files,
            time = time,
            log = log,
            app = AppStamp(versionName = "1.0", versionCode = 10),
        )
        val read = ReadTransferFileUseCase(
            files = files,
            codec = codec,
            validate = ValidateSnapshot(),
            log = log,
        )
        val apply = ApplyTransferUseCase(
            store = store,
            settings = settings,
            codec = codec,
            files = files,
            activeProfile = FakeActiveProfile(),
            replanner = replanner,
            log = log,
            buildPlan = BuildTransferPlan(ids),
        )
        return DataTransferViewModel(
            exportData = export,
            readFile = read,
            applyTransfer = apply,
            store = store,
            settings = settings,
            files = files,
            ids = ids,
            time = time,
        )
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}

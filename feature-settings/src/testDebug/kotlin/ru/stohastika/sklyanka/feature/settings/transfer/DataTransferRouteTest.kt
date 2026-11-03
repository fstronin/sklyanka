package ru.stohastika.sklyanka.feature.settings.transfer

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
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
import ru.stohastika.sklyanka.domain.transfer.TransferMode
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

    /**
     * Правило «идёт замена — кнопка занята, второе нажатие ничего не применяет» (ПЕР-16, SKL-144).
     *
     * Применение придержано защёлкой: без неё операция успевает завершиться внутри самого
     * нажатия, и занятого состояния у кнопки не существует. Человек нажимает подтверждение
     * дважды подряд по той же точке — как нажимает человек, — и замена обязана примениться
     * ровно один раз, а кнопка на время записи — быть занятой, а не выглядеть готовой.
     */
    @Test
    fun twoConfirmationPressesApplyTheReplacementOnce() {
        val gate = CompletableDeferred<Unit>()
        store.applyGate = { gate.await() }
        val model = viewModel()
        shadowOf(Looper.getMainLooper()).idle()

        composeRule.setContent {
            SklyankaTheme {
                DataTransferRoute(viewModel = model, onOpenPermissions = {}, onBack = {})
            }
        }
        // Шаги до подтверждения: режим «Заменить всё», файл и верное слово (ОВ-67).
        composeRule.runOnUiThread {
            model.onIntent(TransferIntent.SelectMode(TransferMode.REPLACE_ALL))
            model.onIntent(TransferIntent.FileChosen("content://file"))
        }
        idleMain()
        composeRule.runOnUiThread { model.onIntent(TransferIntent.ContinueFromSummary) }
        idleMain()
        composeRule.runOnUiThread {
            model.onIntent(TransferIntent.ConfirmationWordChanged(context.getString(R.string.transfer_replace_word)))
        }
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithTag(TAG_REPLACE_CONFIRM_ACTION).fetchSemanticsNodes().isNotEmpty()
        }
        val confirm = composeRule.onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION, useUnmergedTree = true)

        confirm.performClick()
        composeRule.waitForIdle()
        confirm.assertIsNotEnabled()
        confirm.performClick()
        composeRule.waitForIdle()

        gate.complete(Unit)
        composeRule.waitUntil(timeoutMillis = WAIT_MS) { store.applied.size == 1 }
        composeRule.waitForIdle()

        assertEquals("one confirmed replacement must apply exactly once", 1, store.applied.size)
        composeRule.onNodeWithTag(TAG_REPORT).assertIsDisplayed()
    }

    /** Дать очередям главного потока выполниться: состояние шага двигается корутинами (Robolectric). */
    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
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

package ru.stohastika.sklyanka.acceptance.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransfer
import ru.stohastika.sklyanka.data.transfer.JsonTransferCodec
import ru.stohastika.sklyanka.domain.transfer.ExportDataUseCase
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileIntent
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileScreen
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileUiState
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileViewModel
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_DONE
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_EXPORT
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_SCREEN
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_INPUT
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд экрана удаления профиля: настоящий `DeleteProfileViewModel` на мире сценария.
 *
 * Состояние собирает продукт, а не пересказ: удаляемый профиль читается тем же
 * `ProfileContextProvider`, числа потерь — тем же портом, выгрузка идёт настоящим
 * `ExportDataUseCase` с настоящим кодеком, удаление — настоящим `DeleteProfileUseCase`.
 * Шаги только нажимают и читают.
 *
 * Выбор места сохранения — чужая активность, и открыть её compose-стенд не может; стенд
 * зовёт `onSaveFile` (как это сделал бы маршрут) и запоминает факт, а «выбранное место»
 * подаёт намерением. Отмена диалога намерения не шлёт вовсе — так же, как у продукта.
 *
 * Ввод слова зеркалится локальным состоянием: поле экрана без собственного состояния,
 * и замена текста меняет значение, только когда композиция перерисовывается с новым —
 * тот же приём, что у стенда SKL-39.
 */
@OptIn(ExperimentalTestApi::class)
internal object DeleteProfileScreenProbe {

    /** Куда «система» сохраняет файл в сценариях выгрузки. */
    const val EXPORT_HANDLE = "delete-export-handle"

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: DeleteProfileViewModel? = null

    /** Сколько раз экран попросил место сохранения. */
    var saveRequests: Int = 0
        private set

    /** Сколько раз нажали «Готово»: возврат в настройки. */
    var doneCalls: Int = 0
        private set

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            saveRequests = 0
            doneCalls = 0
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun model(world: AcceptanceWorld): DeleteProfileViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): DeleteProfileUiState = model(world).state.value

    fun onIntent(world: AcceptanceWorld, intent: DeleteProfileIntent) {
        model(world).onIntent(intent)
    }

    /** Открытие экрана: состояние строится над активным профилем мира. */
    fun open(world: AcceptanceWorld) {
        model(world)
    }

    /** Человек нажал «Сохранить файл переноса»: маршрут пошёл бы в системный выбор. */
    fun pressSave(world: AcceptanceWorld) {
        act(world) { onNodeWithTag(TAG_DELETE_EXPORT).performClick() }
    }

    /** Система вернула выбранное место: намерение уходит ViewModel — как у маршрута. */
    fun chooseExportTarget(world: AcceptanceWorld, handle: String = EXPORT_HANDLE) {
        onIntent(world, DeleteProfileIntent.ExportTargetChosen(handle))
    }

    /** Содержимое файла, записанного экраном удаления: им проверяется «та же выгрузка». */
    fun exportedContent(world: AcceptanceWorld): String? = world.transfer.files.documents[EXPORT_HANDLE]

    /** Человек нажал «Готово»: маршрут позвал бы возврат в настройки. */
    fun pressDone(world: AcceptanceWorld) {
        act(world) { onNodeWithTag(TAG_DELETE_DONE).performClick() }
    }

    /**
     * Ввод слова-подтверждения на собранном экране.
     *
     * Поле экрана без собственного состояния: локальное зеркало перерисовывает поле,
     * а намерение уходит в настоящий ViewModel — тот же путь, что у продукта.
     */
    fun typeWord(world: AcceptanceWorld, word: String) {
        reset(world)
        runComposeUiTest {
            val current = state(world)
            setContent {
                var typed by remember { mutableStateOf(current.word) }
                SklyankaTheme {
                    DeleteProfileScreen(
                        state = current.copy(word = typed),
                        onIntent = { intent ->
                            if (intent is DeleteProfileIntent.WordChanged) {
                                typed = intent.text
                                onIntent(world, intent)
                            }
                        },
                        onSaveFile = {},
                        onDone = {},
                    )
                }
            }
            onNodeWithTag(TAG_TEXT_FIELD_INPUT).performTextReplacement(word)
            waitForIdle()
        }
    }

    /** Доступность кнопки подтверждения на собранном экране: слово уже в состоянии. */
    fun assertConfirmAvailability(world: AcceptanceWorld, enabled: Boolean) {
        ask(world) {
            if (enabled) {
                onNodeWithTag(TAG_DELETE_CONFIRM).assertIsEnabled()
            } else {
                onNodeWithTag(TAG_DELETE_CONFIRM).assertIsNotEnabled()
            }
        }
    }

    // --- Композиция ----------------------------------------------------------------------------

    fun ask(world: AcceptanceWorld, state: DeleteProfileUiState = state(world), question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(state = state, onIntent = {}, onSaveFile = {}, onDone = {}) }
            question()
            waitForIdle()
        }
    }

    /** Действие человека: нажатия копятся и применяются после композиции настоящим ViewModel. */
    fun act(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        reset(world)
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            setContent {
                Content(
                    state = state(world),
                    onIntent = { intent -> performed += { onIntent(world, intent) } },
                    onSaveFile = { performed += { saveRequests++ } },
                    onDone = { performed += { doneCalls++ } },
                )
            }
            action(this)
            waitForIdle()
        }
        performed.forEach { it() }
    }

    /** Есть ли на собранном экране текст: сверка идёт с ресурсами модуля, а не с литералом. */
    fun hasText(state: DeleteProfileUiState, text: String): Boolean {
        var found = false
        runComposeUiTest {
            setContent { Content(state = state, onIntent = {}, onSaveFile = {}, onDone = {}) }
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    fun hasTag(state: DeleteProfileUiState, tag: String): Boolean {
        var found = false
        runComposeUiTest {
            setContent { Content(state = state, onIntent = {}, onSaveFile = {}, onDone = {}) }
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    @Composable
    private fun Content(
        state: DeleteProfileUiState,
        onIntent: (DeleteProfileIntent) -> Unit,
        onSaveFile: () -> Unit,
        onDone: () -> Unit,
    ) {
        SklyankaTheme {
            DeleteProfileScreen(
                state = state,
                onIntent = onIntent,
                onSaveFile = onSaveFile,
                onDone = onDone,
            )
        }
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun build(world: AcceptanceWorld): DeleteProfileViewModel {
        world.transfer.codec = JsonTransferCodec(world.clock.time)
        val deletion = world.profileDeletion
        return DeleteProfileViewModel(
            context = world.profileSession.contextProvider,
            deleteProfile = deletion.useCase,
            losses = deletion.losses,
            exportData = ExportDataUseCase(
                store = world.transfer.store,
                settings = world.transfer.settings,
                codec = world.transfer.codec,
                files = world.transfer.files,
                time = world.clock.time,
                log = world.log,
                app = ScenarioTransfer.APP_STAMP,
            ),
            time = world.clock.time,
        )
    }

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}

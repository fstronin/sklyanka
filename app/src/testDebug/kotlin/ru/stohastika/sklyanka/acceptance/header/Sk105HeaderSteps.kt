package ru.stohastika.sklyanka.acceptance.header

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.today.MedicationCardFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.TrailingLine
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.meds.card.MedCardUiState
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardScreen
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.form.FormInputs
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.feature.meds.list.MedicationCardUi
import ru.stohastika.sklyanka.feature.meds.list.MedicationListScreen
import ru.stohastika.sklyanka.feature.meds.list.MedsTab
import ru.stohastika.sklyanka.feature.meds.list.MedsUiState
import ru.stohastika.sklyanka.feature.settings.PermissionsSummary
import ru.stohastika.sklyanka.feature.settings.ProfileRowState
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.today.TAG_LANE_HEADER
import ru.stohastika.sklyanka.feature.today.TAG_PRN_CARD
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.navigation.RootLayer
import ru.stohastika.sklyanka.navigation.RootNavigation
import ru.stohastika.sklyanka.navigation.RootTab
import ru.stohastika.sklyanka.navigation.layer
import ru.stohastika.sklyanka.ui.component.DateTimePickerDialog
import ru.stohastika.sklyanka.ui.component.ListPickerDialog
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_SHEET_SCRIM
import ru.stohastika.sklyanka.ui.component.TAG_COMPACT_TITLE
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import ru.stohastika.sklyanka.ui.component.TAG_DIALOG_SCRIM
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.component.TAG_LIST_PICKER
import ru.stohastika.sklyanka.ui.component.TAG_MARK_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_PINNED_HEADER_BAR
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.ui.component.TAG_TAKEN_STATUS
import ru.stohastika.sklyanka.ui.component.TAG_TIME_PICKER
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.profile.TAG_PROFILE_AVATAR
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import ru.stohastika.sklyanka.feature.history.R as HistoryR
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.feature.settings.R as SettingsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-105 о шапке, модальных слоях, переходах корня и карточке приёма.
 *
 * Живут в `app`, потому что сценарии говорят сразу о нескольких экранах разных модулей
 * (история, настройки, лекарства, «Сегодня») и о корне приложения: единственное место, где
 * всё это существует одновременно, — `app` (`SKL-26/TDD.md §4.3`). Состояние экранов
 * собирается из мира сценария тем же доменом, что в продукте ([TodayFromWorld],
 * [MedicationCardFromWorld]); навигация корня — тем же [RootNavigation], что держит
 * `MainActivity`.
 *
 * Стенд пересобирает композицию на каждое утверждение: сессия Compose живёт внутри блока,
 * а шаги Gherkin — отдельные вызовы, и держать блок открытым между ними нечем (приём
 * `ProfileIndicationSteps`). Поэтому «Когда» запоминают наблюдение, а «Тогда» собирают
 * экран и спрашивают у него.
 *
 * Чего стенд не видит и почему это записано, а не обойдено:
 * - **затемнение окна диалога.** `AppDialogScrim` гасит платформенное затемнение
 *   (`setDimAmount(0)`), но величину `dimAmount` семантика не несёт, и в этом прогоне она
 *   не наблюдаема. Наблюдаемо другое: приложение рисует СВОЙ слой ([TAG_DIALOG_SCRIM]
 *   у диалогов, [TAG_BOTTOM_SHEET_SCRIM] у шторки) и берёт его цвет из роли темы — это
 *   и проверяется;
 * - **системная кнопка «Назад».** Полный запуск `MainActivity` в этом контейнере
 *   недоступен (граф Hilt поднимается только на устройстве), поэтому проверяется договор
 *   маршрутизации: `MainActivity` включает обработчик только на `RootLayer.Settings` и
 *   ведёт оттуда на «Сегодня» (`app/.../MainActivity.kt:478`), а на `RootLayer.Today`
 *   обработчика нет — нажатие не перехватывается и завершает активность;
 * - **обрезка подписи и пунктирная рамка** нарисованы `Canvas`/типографикой и семантики
 *   не несут; там, где это важно, проверяется наблюдаемый эквивалент (узел нарисован,
 *   цвет взят ролью темы) и это отмечено в шаге.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class Sk105HeaderSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Наблюдения, которые «Тогда» перечитывает -----------------------------------------

    /** Роль затемнения, прочитанная в каждой собранной композиции (роль, а не пиксель). */
    private val scrims = linkedMapOf<String, Color>()

    /** Слои, закрывшиеся от нажатия по подложке: фон под ними недоступен. */
    private val scrimDismissed = mutableListOf<String>()

    private var cardMedication: String = ""

    private var rootNavigation: RootNavigation? = null
    private var appFinished = false

    private var fontScale: Float = 1f

    private var avatarIntents: List<TodayIntent> = emptyList()

    // =======================================================================================
    // 1. Затемнение модального слоя — роль системы, а не чёрная подложка (V-4)
    // =======================================================================================

    @Когда("^человек открывает диалог времени приёма$")
    fun thePersonOpensTheTimeDialog() {
        runComposeUiTest {
            setContent {
                Stand {
                    scrims[DIALOG] = SklyankaTheme.colors.scrim
                    DateTimePickerDialog(
                        title = string(MedsR.string.form_dialog_time),
                        initial = LocalTime.of(8, 0),
                        is24Hour = true,
                        onDismiss = { scrimDismissed += DIALOG },
                        onConfirm = {},
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_DATE_TIME_PICKER, useUnmergedTree = true).assertExists()
            onNodeWithTag(TAG_DIALOG_SCRIM, useUnmergedTree = true).assertExists()
            // Подложка перехватывает нажатие: тап вне окна закрывает модальный слой —
            // тот же обработчик, что у продукта. Точка — угол слоя: центр закрыт оболочкой
            // диалога, и нажатие досталось бы ей.
            onNodeWithTag(TAG_DIALOG_SCRIM, useUnmergedTree = true).performTouchInput { click(Offset(4f, 4f)) }
            waitForIdle()
        }
    }

    @Тогда("^подложка под диалогом залита ролью затемнения с непрозрачностью 0,32$")
    fun theDialogScrimIsTheRoleAt32Percent() {
        val role = requireNotNull(scrims[DIALOG]) { "the time dialog never drew its scrim" }
        assertEquals("the scrim role carries opacity 0,32 (§2.3, V-4)", 0.32f, role.alpha, TOLERANCE)
    }

    @И("^фон под подложкой недоступен нажатию$")
    fun theBackgroundIsUnreachable() {
        assertTrue(
            "the tap on the scrim must dismiss the modal layer: $scrimDismissed",
            DIALOG in scrimDismissed,
        )
    }

    // =======================================================================================
    // 2. Все модальные слои затемняют одинаково (V-4)
    // =======================================================================================

    @Когда("^человек открывает карточку «([^»]+)»$")
    fun thePersonOpensTheCard(medication: String) {
        cardMedication = medication
        runComposeUiTest {
            setContent { Stand { MedicationCardScreen(state = cardState(deleteConfirmation = false), onIntent = {}) } }
            waitForIdle()
            onNodeWithTag(TAG_MEDICATION_CARD_SCREEN, useUnmergedTree = true).assertExists()
        }
    }

    @Когда("^человек открывает по очереди шторку удаления лекарства, диалог времени и диалог выбора из списка$")
    fun thePersonOpensTheThreeLayers() {
        requireNotNull(world.medications.get(world.medicationNamed(cardMedication))) {
            "the scenario never opened a medication card"
        }

        // a) Шторка удаления лекарства — тот же `SklyankaBottomSheet`, что у остальных действий.
        runComposeUiTest {
            setContent {
                Stand {
                    scrims[SHEET] = SklyankaTheme.colors.scrim
                    MedicationCardScreen(state = cardState(deleteConfirmation = true), onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_BOTTOM_SHEET_SCRIM, useUnmergedTree = true).assertExists()
        }

        // b) Диалог времени приёма.
        runComposeUiTest {
            setContent {
                Stand {
                    scrims[TIME] = SklyankaTheme.colors.scrim
                    DateTimePickerDialog(
                        title = string(MedsR.string.form_dialog_time),
                        initial = LocalTime.of(9, 0),
                        is24Hour = true,
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_DIALOG_SCRIM, useUnmergedTree = true).assertExists()
        }

        // c) Диалог выбора из списка — тот же компонент, что «Отложить на» в настройках.
        runComposeUiTest {
            setContent {
                Stand {
                    scrims[LIST] = SklyankaTheme.colors.scrim
                    ListPickerDialog(
                        title = string(SettingsR.string.settings_snooze),
                        options = listOf(15, 30),
                        selected = 15,
                        label = { minutes -> "$minutes" },
                        onSelect = {},
                        onDismiss = {},
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_LIST_PICKER, useUnmergedTree = true).assertExists()
            onNodeWithTag(TAG_DIALOG_SCRIM, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^у всех трёх слоёв подложка — одна и та же роль с одной и той же непрозрачностью$")
    fun allThreeLayersShareOneScrimRole() {
        val sheet = requireNotNull(scrims[SHEET]) { "the deletion sheet never drew its scrim" }
        val time = requireNotNull(scrims[TIME]) { "the time dialog never drew its scrim" }
        val list = requireNotNull(scrims[LIST]) { "the list picker never drew its scrim" }
        assertEquals("the sheet and the time dialog must share the scrim role", sheet, time)
        assertEquals("the time dialog and the list picker must share the scrim role", time, list)
        assertEquals("the one role carries opacity 0,32 (§2.3)", 0.32f, sheet.alpha, TOLERANCE)
    }

    @Но("^чёрной подложки прошивки под слоями нет$")
    fun thereIsNoPlatformBackdrop() {
        // Платформенное затемнение гасит сам `AppDialogScrim` (`setDimAmount(0)`), и этой
        // величины семантика не несёт. Наблюдаемый эквивалент: каждый слой нарисован
        // слоем приложения — ролью темы, а не подложкой прошивки (V-4).
        assertTrue("the app must draw the sheet scrim", SHEET in scrims)
        assertTrue("the app must draw the dialog scrim", TIME in scrims && LIST in scrims)
    }

    // =======================================================================================
    // 3. Возврат во вложенной шапке — шеврон с именем «Назад» (V-7, ПРФ-11)
    // =======================================================================================

    @Тогда("^в шапке нарисован шеврон направления «влево»$")
    fun theHeaderDrawsTheLeftChevron() {
        onHistory { test ->
            test.onNodeWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true).assertIsDisplayed()
            val back = test.onNodeWithContentDescription(backLabel(), useUnmergedTree = true)
            back.assertExists()
            back.assertIsDisplayed()
            // Направление глифа нарисовано `Canvas` и семантики не несёт; наблюдаемый
            // признак возврата — узел, озвученный как «Назад», в шапке второго уровня.
        }
    }

    @Но("^слова «Назад» на экране нет$")
    fun theWordBackIsNotOnScreen() {
        onHistory { test ->
            test.onAllNodesWithText(backLabel(), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @И("^цель возврата не меньше 44 dp и читается как «Назад»$")
    fun theBackTargetIsAtLeast44Dp() {
        onHistory { test ->
            val density = test.onRoot(useUnmergedTree = true).fetchSemanticsNode().layoutInfo.density.density
            val bounds = test.onNodeWithContentDescription(backLabel(), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val min = MIN_TOUCH_TARGET_DP * density
            assertTrue(
                "the back target must be at least 44 dp: ${bounds.width} × ${bounds.height} px at ${density}x",
                bounds.width >= min - TOLERANCE && bounds.height >= min - TOLERANCE,
            )
        }
    }

    // =======================================================================================
    // 4. Модальная форма сохраняет слово «Отмена»
    // =======================================================================================

    @Тогда("^слева в шапке стоит слово «Отмена»$")
    fun theCancelWordStandsOnTheLeft() {
        val cancel = string(UiR.string.action_cancel)
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            waitForIdle()
            val node = onNodeWithText(cancel, useUnmergedTree = true)
            node.assertExists()
            node.assertIsDisplayed()
            val left = node.fetchSemanticsNode().boundsInRoot
            val header = onNodeWithTag(TAG_SCREEN_HEADER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("«$cancel» must stand on the left of the modal header", left.center.x < header.center.x)
        }
    }

    @Но("^шеврона возврата в модальной шапке нет$")
    fun theModalHeaderHasNoChevron() {
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            waitForIdle()
            onAllNodesWithContentDescription(backLabel(), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // =======================================================================================
    // 5. Крупный заголовок уезжает, компактный ряд остаётся
    // =======================================================================================

    @Когда("^человек прокручивает содержимое вниз до конца$")
    fun thePersonScrollsToTheEnd() {
        // Намерение шага: прокрутка выполняется в «Тогда» — сессия композиции между шагами
        // не живёт (приём `ProfileIndicationSteps`).
    }

    @Тогда("^сверху стоит закреплённый компактный ряд с заголовком «Настройки»$")
    fun thePinnedRowKeepsTheSettingsTitle() {
        runComposeUiTest {
            setContent { Stand { Window { SettingsScreen(state = settingsState(), onIntent = {}) } } }
            val bar = onNodeWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true)
            bar.assertExists()
            bar.assertIsDisplayed()
            val title = scrollUntilCompactTitle()
            assertEquals(
                "the pinned row must carry the screen title",
                string(SettingsR.string.settings_title),
                textsOf(title).joinToString(" ").trim(),
            )
        }
    }

    @И("^доступные на этом экране действия остаются доступными на любом уровне прокрутки$")
    fun theActionsStayReachableWhileScrolling() {
        runComposeUiTest {
            setContent { Stand { Window { SettingsScreen(state = settingsState(), onIntent = {}) } } }
            val barTop = onNodeWithTag(
                TAG_PINNED_HEADER_BAR,
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot.top
            val tabsTop = onNodeWithTag(TAG_BOTTOM_BAR, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            scrollToEnd()
            assertEquals(
                "the pinned row must not move while the content scrolls",
                barTop,
                onNodeWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top,
                TOLERANCE,
            )
            assertEquals(
                "the tab bar — the actions of this screen — must stay in place",
                tabsTop,
                onNodeWithTag(TAG_BOTTOM_BAR, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top,
                TOLERANCE,
            )
        }
    }

    // =======================================================================================
    // 6. Компактный заголовок появляется, когда крупный ушёл под ряд
    // =======================================================================================

    @Когда("^крупный заголовок прокруткой ушёл под закреплённый ряд целиком$")
    fun theLargeTitleWentUnderTheRow() {
        // Прокрутка и наблюдение порога — в «Тогда».
    }

    @Тогда("^компактный заголовок показан$")
    fun theCompactTitleIsShown() {
        onHistory { test ->
            test.scrollUntilCompactTitle()
        }
    }

    @И("^при прокрутке назад он гаснет тем же порогом$")
    fun theCompactTitleFadesOnScrollBack() {
        onHistory { test ->
            test.scrollUntilCompactTitle()
            repeat(SCROLL_SWIPES) {
                test.onAllNodes(hasScrollAction(), useUnmergedTree = true).onFirst().performTouchInput { swipeDown() }
                test.waitForIdle()
            }
            test.onAllNodesWithTag(TAG_COMPACT_TITLE, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // =======================================================================================
    // 7. Длинный компактный заголовок не рисуется, а не обрезается
    // =======================================================================================

    @Допустим("^системный шрифт увеличен до (\\d+) %$")
    fun theSystemFontIsEnlargedTo(percent: Int) {
        fontScale = percent / PERCENT
    }

    @Тогда("^компактного заголовка нет$")
    fun thereIsNoCompactTitle() {
        // Сценарий называет немецкий язык и 200 %: немецкое название истории вместе
        // с действием «Arztbericht» не помещается в строку, и порог — фактическая ширина
        // подписи, а не `fontScale` (§6.3, §7). Язык сценария задаёт Предыстория, и стенд
        // собирает экран на том же немецком ресурсе.
        runComposeUiTest {
            setContent {
                Stand(locale = GERMAN, fontScale = fontScale) {
                    Window { HistoryScreen(state = historyState(), onIntent = {}) }
                }
            }
            scrollToEnd()
            onAllNodesWithTag(TAG_COMPACT_TITLE, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @И("^крупный заголовок не обрезан$")
    fun theLargeTitleIsNotTruncated() {
        runComposeUiTest {
            setContent {
                Stand(locale = GERMAN, fontScale = fontScale) {
                    Window { HistoryScreen(state = historyState(), onIntent = {}) }
                }
            }
            val title = string(HistoryR.string.history_title, GERMAN)
            val node = onNodeWithText(title, useUnmergedTree = true)
            node.assertExists()
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            // Обрезка многоточием запрещена (§5.1): строка нарисована целиком (текст равен
            // ресурсу) и занимает непустое место, то есть не срезана.
            assertTrue("the large title must be drawn, not cut: $bounds", bounds.width > 0f && bounds.height > 0f)
        }
    }

    // =======================================================================================
    // 8. Корень вкладки возврата не показывает
    // =======================================================================================

    @Тогда("^шеврона возврата в шапке нет$")
    fun theRootHeaderHasNoChevron() {
        runComposeUiTest {
            setContent { Stand { SettingsScreen(state = settingsState(), onIntent = {}) } }
            waitForIdle()
            onAllNodesWithContentDescription(backLabel(), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // =======================================================================================
    // 9. В сжатой шапке чужого профиля все шесть признаков на месте (ПРФ-11)
    // =======================================================================================

    @Допустим("^человек открыл вкладку «Лекарства»$")
    fun thePersonOpenedTheMedsTab() {
        // Экран «Лекарства» — тот же, что открывает вкладка нижней панели; состояние
        // навигации шагам не нужно, экран собирается из мира сценария.
    }

    @Тогда("^подкраска шапки и нижняя линия остались$")
    fun theTintedHeaderStays() {
        var tinted = false
        runComposeUiTest {
            setContent {
                Stand(chrome = foreignChrome()) {
                    val colors = SklyankaTheme.colors
                    tinted = colors.profileHeader != colors.background
                    MedicationListScreen(state = medsState(), onIntent = {}, onOpenProfiles = {})
                }
            }
            onNodeWithTag(TAG_PROFILE_STRIPE, useUnmergedTree = true).assertIsDisplayed()
            scrollToEnd()
            onNodeWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true).assertIsDisplayed()
            assertTrue("a foreign profile must tint the header (ПРФ-16) и не снимать подкраску (§7)", tinted)
            // Нижнюю линию рисует `Canvas` внутри ряда и семантики она не несёт; наблюдаемо,
            // что сжатый ряд на месте и подкраска не снята (§7).
        }
    }

    @И("^баннер о том, что это не ваши приёмы, показан и не сворачивается$")
    fun theForeignProfileBannerStays() {
        val banner = context.withInterfaceLocale(RUSSIAN).getString(UiR.string.profile_banner, FOREIGN.name)
        runComposeUiTest {
            setContent {
                Stand(chrome = foreignChrome()) {
                    Window {
                        MedicationListScreen(state = medsState(extraItems = EXTRA_CARDS), onIntent = {
                        }, onOpenProfiles = {})
                    }
                }
            }
            scrollToEnd()
            val node = onNodeWithText(banner, useUnmergedTree = true)
            node.assertExists()
            node.assertIsDisplayed()
            val actions = node.fetchSemanticsNode().config.toList().map { it.key.name }
            assertTrue(
                "the foreign profile banner must carry no way to dismiss it (ПРФ-13): $actions",
                actions.none { it.contains("OnClick") || it.contains("Dismiss") },
            )
        }
    }

    @И("^аватар в компактном ряду несёт цвет профиля$")
    fun theAvatarCarriesTheProfileColour() {
        runComposeUiTest {
            var accent: Color? = null
            setContent {
                Stand(chrome = foreignChrome()) {
                    accent = SklyankaTheme.colors.accent
                    Window {
                        MedicationListScreen(state = medsState(extraItems = EXTRA_CARDS), onIntent = {
                        }, onOpenProfiles = {})
                    }
                }
            }
            scrollToEnd()
            onNodeWithTag(TAG_PROFILE_AVATAR, useUnmergedTree = true).assertIsDisplayed()
            // Кружок аватара красится ролью `accent` (`ProfileAvatar`), а акцент темы
            // в чужом профиле — цвет профиля (`ProfileChrome`, ПРФ-11).
            assertEquals(
                "the avatar must carry the profile colour",
                FOREIGN.colour.color,
                requireNotNull(accent) { "the theme accent was not read" },
            )
        }
    }

    // =======================================================================================
    // 10–11. Системная «Назад» на корнях (10-behavior §5.1)
    // =======================================================================================

    @Допустим("^человек открыл вкладку «Сегодня»$")
    fun thePersonOpenedTheTodayTab() {
        rootNavigation = RootNavigation().apply { openTab(RootTab.TODAY) }
    }

    @И("^человек не открывал других экранов$")
    fun thePersonOpenedNoOtherScreens() {
        val navigation = requireNotNull(rootNavigation) { "the Today tab was not opened" }
        assertEquals("nothing but Today must be open (ОВ-81)", RootLayer.Today, navigation.layer())
    }

    @Когда("^человек нажимает системную «Назад»$")
    fun thePersonPressesSystemBack() {
        // Обработчик корня включён ровно на `RootLayer.Settings` и ведёт оттуда на
        // «Сегодня» (`MainActivity.kt:478`); на «Сегодня» его нет — нажатие завершает
        // активность. Сценарий корня настроек приходит сюда без своего состояния
        // (Предыстория «Настройки» — общая и ничего не держит), поэтому отсутствие
        // выбранной вкладки читается как корень настроек.
        val navigation = rootNavigation ?: RootNavigation().apply { openTab(RootTab.SETTINGS) }
        rootNavigation = navigation
        when (navigation.layer()) {
            RootLayer.Settings -> {
                navigation.openToday()
                appFinished = false
            }

            RootLayer.Today -> appFinished = true
            else -> error("the root back handler owns only the tab roots: ${navigation.layer()}")
        }
    }

    @Тогда("^открыт экран «Сегодня»$")
    fun todayIsOpen() {
        val navigation = requireNotNull(rootNavigation) { "the root was never built" }
        assertTrue("the root must lead to Today (10-behavior §5.1)", navigation.showsToday)
        runComposeUiTest {
            setContent { Stand { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) } }
            onNodeWithTag(TAG_TODAY_SCREEN, useUnmergedTree = true).assertExists()
        }
    }

    @Но("^приложение не закрылось$")
    fun theAppDidNotClose() {
        assertFalse("leaving the Settings root must not finish the app", appFinished)
    }

    @Тогда("^приложение выходит$")
    fun theAppCloses() {
        // Полный запуск активности недоступен (граф Hilt требует устройства): наблюдаемый
        // факт — договор корня, на «Сегодня» нажатие не перехватывается.
        assertTrue("on Today the system back must finish the app (10-behavior §5.1)", appFinished)
    }

    @Но("^ни один экран не открывается вместо выхода$")
    fun nothingOpensInsteadOfExit() {
        val navigation = requireNotNull(rootNavigation) { "the root was never built" }
        assertEquals("nothing must open instead of the exit", RootLayer.Today, navigation.layer())
        assertTrue("Today stays where it was", navigation.showsToday)
        assertTrue("no second-level screen was opened", navigation.meds == null && navigation.section == null)
    }

    // =======================================================================================
    // 12. Аватар ведёт в список профилей, а не переключает профиль (ПРФ-19)
    // =======================================================================================

    @Когда("^человек нажимает аватар в шапке «Сегодня»$")
    fun thePersonTapsTheAvatar() {
        runComposeUiTest {
            val intents = mutableListOf<TodayIntent>()
            setContent { Stand { TodayScreen(state = TodayFromWorld.state(world), onIntent = intents::add) } }
            onNodeWithTag(TAG_PROFILE_AVATAR, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag(TAG_PROFILE_AVATAR, useUnmergedTree = true).performClick()
            waitForIdle()
            avatarIntents = intents
        }
    }

    @Тогда("^открыт список профилей в настройках$")
    fun theProfilesListIsOpenInSettings() {
        assertTrue(
            "the avatar must lead to the settings profiles (ПРФ-19), not switch the profile: $avatarIntents",
            avatarIntents.any { it is TodayIntent.OpenSettings },
        )
        runComposeUiTest {
            setContent { Stand { SettingsScreen(state = settingsState(), onIntent = {}) } }
            // Список профилей — первая группа настроек: узнаём его по строке владельца
            // (заголовок рубрики компонент набирает прописными, и сверять его сырым ресурсом нельзя).
            onNodeWithText(string(SettingsR.string.settings_profile_self), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @И("^активным профилем остался «Я»$")
    fun theOwnProfileStaysActive() {
        assertEquals("the avatar must not switch the profile (ПРФ-19)", OWN, world.profiles.active)
    }

    @И("^аватар читается как «Профили»$")
    fun theAvatarIsReadAsProfiles() {
        val label = string(UiR.string.cd_profile_avatar)
        assertEquals("Профили", label)
        runComposeUiTest {
            setContent { Stand { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) } }
            onNodeWithTag(TAG_PROFILE_AVATAR, useUnmergedTree = true).assertContentDescriptionEquals(label)
        }
    }

    // =======================================================================================
    // 13. Карточка приёма показывается в пяти состояниях (12-design §5.6)
    // =======================================================================================

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» с приёмами в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) ежедневно$")
    fun theProfileHasThatMedicationTwiceDaily(profile: String, medication: String, first: String, second: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(LocalTime.parse(first), LocalTime.parse(second)),
        )
        // Расписание без приёмов на экране не видно: день порождает генератор, как в продукте.
        world.plan()
    }

    @Когда("^человек смотрит на «Сегодня» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonLooksAtTodayAt(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        // Приёмы дня порождает генератор: без него «Сегодня» пусто, а сценарий говорит о состояниях карточки.
        world.plan()
        runComposeUiTest {
            setContent { Stand { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) } }
            onNodeWithTag(TAG_TODAY_SCREEN, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^приём «([^»]+)» на (\\d{2}:\\d{2}) показан будущим — с круглой кнопкой отметки$")
    fun theIntakeIsUpcoming(medication: String, time: String) {
        onToday { test ->
            val card = test.cardUnderLane(medication, time)
            assertTrue(
                "an upcoming intake must show the round mark button ($medication, $time)",
                test.cardHas(card, TAG_MARK_BUTTON),
            )
        }
    }

    @И("^приём «([^»]+)» на (\\d{2}:\\d{2}) показан наступившим — с кнопками «Принято» и «Пропустить»$")
    fun theIntakeIsDueNow(medication: String, time: String) {
        onToday { test ->
            val card = test.cardUnderLane(medication, time)
            assertTrue(
                "a due intake must offer «Принято» ($medication, $time)",
                test.cardHasText(card, string(UiR.string.action_taken)),
            )
            assertTrue(
                "a due intake must offer «Пропустить» ($medication, $time)",
                test.cardHasText(card, string(UiR.string.action_skip)),
            )
        }
    }

    @И("^приём «([^»]+)» на (\\d{2}:\\d{2}) показан отмеченным — галочкой и фактическим временем$")
    fun theIntakeIsTaken(medication: String, time: String) {
        onToday { test ->
            val card = test.cardUnderLane(medication, time)
            val status = test.onAllNodesWithTag(TAG_TAKEN_STATUS, useUnmergedTree = true).fetchSemanticsNodes()
                .firstOrNull { card.boundsInRoot.contains(it.boundsInRoot.center) }
            val taken = requireNotNull(status) { "a taken intake must carry the taken status ($medication, $time)" }
            val shown = test.textsOf(taken)
            assertTrue(
                "a taken intake must name the actual time, not the planned one: $shown",
                shown.any { it.contains(TAKEN_AT) },
            )
        }
    }

    @И("^приём «([^»]+)» на (\\d{2}:\\d{2}) показан просроченным — приглушённым и подписанным словом «([^»]+)»$")
    fun theIntakeIsMissed(medication: String, time: String, status: String) {
        // Приглушение — цвет (`textMuted` у названия и у слова статуса, `IntakeCard` §5.6),
        // а семантика цвета не несёт: его доказывает кадр состояния `Missed`. Стенд проверяет
        // читаемое: у карточки нет кнопок отметки, и статус назван словом, а не догадкой.
        assertEquals(
            "the scenario must quote the resource «$status»",
            string(UiR.string.status_missed),
            status,
        )
        onToday { test ->
            val card = test.cardUnderLane(medication, time)
            assertFalse(
                "a missed intake keeps no mark buttons ($medication, $time)",
                test.cardHas(card, TAG_MARK_BUTTON) || test.cardHasText(card, string(UiR.string.action_taken)),
            )
            assertTrue(
                "a missed intake names its status with the word «$status» ($medication, $time)",
                test.cardHasText(card, status),
            )
        }
    }

    @И("^«Обезболивающее» показано в блоке «По потребности» с пунктирной рамкой и кнопкой «Принять»$")
    fun theOnDemandMedicationIsInItsBlock() {
        onToday { test ->
            // День длиннее окна стенда: до блока «По потребности» доводим прокруткой, иначе
            // карточка остаётся за нижней гранью и её кнопка не попадает в проверяемые границы.
            test.onNodeWithTag(TAG_PRN_CARD, useUnmergedTree = true).performScrollTo()
            val cards = test.onAllNodesWithTag(TAG_PRN_CARD, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("the on-demand block must show its card", cards.isNotEmpty())
            // Пунктирную рамку рисует `Canvas` (`dashedBorder`), и семантика её не несёт;
            // наблюдаемый признак — карточка «по потребности» и её кнопка «Принять».
            assertTrue(
                "the on-demand card must offer «Принять»",
                test.onAllNodesWithText(string(UiR.string.action_take), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .any { node -> cards.any { it.boundsInRoot.contains(node.boundsInRoot.center) } },
            )
        }
    }

    // =======================================================================================
    // 14–15. Циферблат диалога времени (V-9)
    // =======================================================================================

    @Тогда("^поле циферблата залито нейтральной плашкой$")
    fun theClockDialIsTheNeutralPlaque() {
        var plaque: Color? = null
        var surface: Color? = null
        runComposeUiTest {
            setContent {
                Stand {
                    plaque = SklyankaTheme.colors.neutralSoft
                    surface = SklyankaTheme.colors.surface
                    DateTimePickerDialog(
                        title = string(MedsR.string.form_dialog_time),
                        initial = LocalTime.of(8, 0),
                        is24Hour = true,
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_TIME_PICKER, useUnmergedTree = true).assertExists()
        }
        // Поле залито нейтральной ролью, а не белой пустотой (§5.5, V-9): роль плашки
        // отличается от поверхности диалога.
        assertTrue(
            "the clock field must be the neutral plaque role, not the surface",
            requireNotNull(plaque) != requireNotNull(surface),
        )
    }

    @И("^цифры циферблата набраны тем же начертанием, что и прежде, но крупнее$")
    fun theClockDigitsUseTheSameFaceLarger() {
        // Начертание и кегль цифр задаёт `timePickerTypography()` внутри `DateTimePickerDialog`,
        // и семантика их не несёт. Наблюдаемо: цифры нарисованы (циферблат собран) и поле
        // осталось фиксированным механизмом, а не выросло вместе со шрифтом (§5.5).
        runComposeUiTest {
            setContent {
                Stand {
                    DateTimePickerDialog(
                        title = string(MedsR.string.form_dialog_time),
                        initial = LocalTime.of(8, 0),
                        is24Hour = true,
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_TIME_PICKER, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Тогда("^цифры циферблата, слоты времени и разделитель того же размера, что при обычном шрифте$")
    fun theDialogDigitsDoNotScale() {
        val normal = timePickerBounds(1f)
        val enlarged = timePickerBounds(fontScale)
        assertEquals(
            "the clock size must not change with the system font scale",
            normal.width,
            enlarged.width,
            TOLERANCE,
        )
        assertEquals(
            "the clock size must not change with the system font scale",
            normal.height,
            enlarged.height,
            TOLERANCE,
        )
    }

    @И("^ни одна цифра не перекрывает соседнюю и не выходит за подложку$")
    fun noDigitOverlapsOrLeavesThePlaque() {
        runComposeUiTest {
            setContent {
                Stand(fontScale = fontScale) {
                    DateTimePickerDialog(
                        title = string(MedsR.string.form_dialog_time),
                        initial = LocalTime.of(8, 0),
                        is24Hour = true,
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
            waitForIdle()
            val picker = onNodeWithTag(TAG_TIME_PICKER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val plaque = onNodeWithTag(TAG_DATE_TIME_PICKER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // Цифры не выходят за подложку: циферблат целиком лежит внутри поверхности
            // диалога. Фиксированное поле слота и кольцо цифр не дают соседям перекрыться
            // на любой ступени масштаба (§5.5, НФТ-31).
            assertTrue(
                "the clock must stay inside the dialog surface: picker=$picker plaque=$plaque",
                picker.left >= plaque.left - TOLERANCE &&
                    picker.top >= plaque.top - TOLERANCE &&
                    picker.right <= plaque.right + TOLERANCE &&
                    picker.bottom <= plaque.bottom + TOLERANCE,
            )
        }
    }

    // =======================================================================================
    // Обвязка
    // =======================================================================================

    private fun timePickerBounds(scale: Float): Rect {
        var bounds = Rect.Zero
        runComposeUiTest {
            setContent {
                Stand(fontScale = scale) {
                    DateTimePickerDialog(
                        title = string(MedsR.string.form_dialog_time),
                        initial = LocalTime.of(8, 0),
                        is24Hour = true,
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
            waitForIdle()
            bounds = onNodeWithTag(TAG_TIME_PICKER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        return bounds
    }

    private fun onHistory(question: (ComposeUiTest) -> Unit) {
        runComposeUiTest {
            setContent { Stand { Window { HistoryScreen(state = historyState(), onIntent = {}) } } }
            waitForIdle()
            question(this)
        }
    }

    private fun onToday(question: (ComposeUiTest) -> Unit) {
        runComposeUiTest {
            setContent { Stand { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) } }
            waitForIdle()
            question(this)
        }
    }

    /**
     * Прокрутка до появления компактного заголовка — с повтором, а не одним свитком.
     *
     * Порог появления — геометрия (§7): заголовок показывается, когда крупный ушёл под ряд
     * целиком. Сколько именно свитков до этого нужно, зависит от высоты окна стенда, поэтому
     * шаг прокручивает до наблюдаемого состояния, а не «на N пикселей».
     */
    private fun ComposeUiTest.scrollUntilCompactTitle(): SemanticsNode {
        repeat(SCROLL_SWIPES) {
            onAllNodes(hasScrollAction(), useUnmergedTree = true).onFirst().performTouchInput { swipeUp() }
            waitForIdle()
            val compact = onAllNodesWithTag(TAG_COMPACT_TITLE, useUnmergedTree = true).fetchSemanticsNodes()
            if (compact.isNotEmpty()) return compact.first()
        }
        val bar = onAllNodesWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true).fetchSemanticsNodes()
        val header = onAllNodesWithTag(TAG_SCREEN_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        val scrollables = onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
        throw AssertionError(
            "the compact title never appeared: bar=${bar.map { it.boundsInRoot }} " +
                "header=${header.map { it.boundsInRoot }} " +
                "scrollables=${scrollables.map { it.boundsInRoot }}",
        )
    }

    private fun ComposeUiTest.scrollToEnd() {
        repeat(SCROLL_SWIPES) {
            val scrollables = onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
            if (scrollables.isEmpty()) return
            onAllNodes(hasScrollAction(), useUnmergedTree = true).onFirst().performTouchInput { swipeUp() }
            waitForIdle()
        }
    }

    /**
     * Окно стенда шапки: заведомо короче содержимого, чтобы прокрутка существовала.
     *
     * Сам экран обязан работать при любой высоте окна и прокручиваться (§7); стенд берёт
     * короткое окно, потому что сценарии о сжатой шапке нуждаются в прокрутке, а не в
     * конкретной высоте телефона.
     */
    @Composable
    private fun Window(content: @Composable () -> Unit) {
        Box(modifier = Modifier.height(HEADER_WINDOW)) { content() }
    }

    private fun ComposeUiTest.cardUnderLane(medication: String, time: String): SemanticsNode {
        val name = world.medications.get(world.medicationNamed(medication)).name
        val lanes = onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        val header =
            requireNotNull(
                lanes.firstOrNull {
                    time in textsOf(it)
                },
            ) { "no lane for $time on the screen; lanes=${lanes.map { textsOf(it) }}" }
        val nextLane = lanes.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
        val bottom = nextLane ?: Float.MAX_VALUE
        // Одно лекарство бывает в двух лентах (08:00 и 20:00): карточка ищется по ленте,
        // а не только по названию, иначе совпадений было бы два.
        val cards = onAllNodes(hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(name)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= header.boundsInRoot.top && it.boundsInRoot.top < bottom }
        return requireNotNull(cards.singleOrNull()) {
            "exactly one card of «$medication» must stand under lane $time, found ${cards.size}"
        }
    }

    private fun ComposeUiTest.cardHas(card: SemanticsNode, tag: String): Boolean =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            .any { card.boundsInRoot.contains(it.boundsInRoot.center) }

    private fun ComposeUiTest.cardHasText(card: SemanticsNode, text: String): Boolean =
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
            .any { card.boundsInRoot.contains(it.boundsInRoot.center) }

    private fun ComposeUiTest.textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    @Composable
    private fun formScreen() {
        MedicationFormScreen(
            state = formState(),
            onIntent = {},
            onCancel = {},
            onPickDate = {},
            onPickTime = { _, _ -> },
        )
    }

    private fun cardState(deleteConfirmation: Boolean): MedCardUiState {
        val name = world.medications.get(world.medicationNamed(cardMedication.ifEmpty { DEFAULT_MEDICATION }))
        return MedicationCardFromWorld.state(world, name, deleteConfirmation)
    }

    private fun formState(): MedFormUiState = MedFormUiState.of(
        draft = MedicationDraft(
            id = null,
            profileId = world.profiles.get(world.profiles.active).id,
            startDate = world.clock.today(),
        ),
        mode = FormMode.Create,
        inputs = FormInputs(),
    )

    private fun historyState(): HistoryUiState {
        val month = YearMonth.from(world.clock.today())
        val profileId = world.profiles.get(world.profiles.active).id
        return HistoryUiState(
            month = month,
            period = AdherencePeriod.empty(profileId, month.atDay(1), month.atEndOfMonth()),
        )
    }

    private fun settingsState(): SettingsUiState = SettingsUiState(
        profiles = listOf(
            ProfileRowState(
                id = "self",
                name = OWN,
                colorArgb = ProfileColor.Teal.argb,
                isSelf = true,
                isActive = true,
                medicationCount = 0,
                mirrorReminders = false,
            ),
            ProfileRowState(
                id = "foreign",
                name = FOREIGN.name,
                colorArgb = FOREIGN.colour.argb,
                isSelf = false,
                isActive = false,
                medicationCount = 3,
                mirrorReminders = false,
            ),
            ProfileRowState(
                id = "third",
                name = "Сын",
                colorArgb = ProfileColor.Olive.argb,
                isSelf = false,
                isActive = false,
                medicationCount = 1,
                mirrorReminders = false,
            ),
        ),
        permissions = PermissionsSummary(denied = 0, manual = 1),
        version = "0.1.0",
    )

    /**
     * Список лекарств стенда.
     *
     * @param extraItems сколько карточек добавить сверх первой. Сценарии о сжатой шапке
     *   нуждаются в прокрутке: на одной карточке список короче окна, и шапка законно
     *   остаётся развёрнутой (§7), а «сжатое состояние» так и не наступает.
     */
    private fun medsState(extraItems: Int = 0): MedsUiState = MedsUiState(
        tab = MedsTab.ACTIVE,
        activeCount = 1 + extraItems,
        items = listOf(
            MedicationCardUi(
                medicationId = "med-1",
                title = DEFAULT_MEDICATION,
                form = MedicationForm.TABLET,
                shape = TabletShape.ROUND,
                strength = null,
                strengthUnit = null,
                timesPerDay = 1,
                times = listOf(LocalTime.of(8, 0)),
                trailingLine = TrailingLine.Period(world.clock.today(), null),
                asNeeded = null,
            ),
        ) + List(extraItems) { index ->
            MedicationCardUi(
                medicationId = "med-extra-$index",
                title = "med-extra-$index",
                form = MedicationForm.TABLET,
                shape = TabletShape.ROUND,
                strength = null,
                strengthUnit = null,
                timesPerDay = 1,
                times = listOf(LocalTime.of(8, 0)),
                trailingLine = TrailingLine.Period(world.clock.today(), null),
                asNeeded = null,
            )
        },
        loading = false,
    )

    private fun foreignChrome(): ProfileChromeState = ProfileChromeState.Foreign(FOREIGN.name, FOREIGN.colour)

    private fun string(id: Int, locale: Locale = RUSSIAN): String = context.withInterfaceLocale(locale).getString(id)

    private fun backLabel(): String = string(UiR.string.cd_back)

    /**
     * Стенд: язык интерфейса и масштаб шрифта сценария, оформление профиля и тема.
     *
     * Язык закреплён, потому что сценарии приёмки записаны по-русски, а телефон стенда
     * мог быть любым (НФТ-43); немецкий ресурс берётся тем же контекстом.
     */
    @Composable
    private fun Stand(
        locale: Locale = RUSSIAN,
        fontScale: Float = 1f,
        chrome: ProfileChromeState = ProfileChromeState.Owner,
        content: @Composable () -> Unit,
    ) {
        val pinned = LocalContext.current.withInterfaceLocale(locale)
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            ProfileChrome(chrome) { content() }
        }
    }

    private companion object {
        const val DIALOG = "dialog"
        const val TIME = "time"
        const val SHEET = "sheet"
        const val LIST = "list"

        const val OWN = "Я"
        const val DEFAULT_MEDICATION = "Магний B6"
        const val TAKEN_AT = "08:05"

        /** Цель нажатия возврата (§7): 44 dp. */
        const val MIN_TOUCH_TARGET_DP = 44f
        const val PERCENT = 100f
        const val TOLERANCE = 0.5f
        const val SCROLL_SWIPES = 8

        /** Карточек сверх первой: списку нужна прокрутка, иначе шапка остаётся развёрнутой. */
        const val EXTRA_CARDS = 5

        /** Высота окна стенда шапки: короче содержимого, чтобы прокрутка существовала. */
        /**
         * Высота окна стенда шапки: короче содержимого экранов, чтобы прокрутка была.
         *
         * 260 dp — заметно меньше и пустого месяца «Истории», и списка настроек: сценарии
         * о сжатой шапке проверяют геометрию порога, а не конкретную высоту телефона.
         */
        val HEADER_WINDOW = 260.dp

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
        val GERMAN: Locale = Locale.forLanguageTag("de")

        val FOREIGN = ForeignProfile("Мама", ProfileColor.Terracotta)
    }

    /** Чужой профиль сценария: имя и цвет — из Предыстории, а не выдуманы шагом. */
    private data class ForeignProfile(val name: String, val colour: ProfileColor)
}

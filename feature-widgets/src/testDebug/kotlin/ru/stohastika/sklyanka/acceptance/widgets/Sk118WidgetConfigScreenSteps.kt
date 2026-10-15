package ru.stohastika.sklyanka.acceptance.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioWidgets
import ru.stohastika.sklyanka.domain.pro.ProStatus
import ru.stohastika.sklyanka.feature.widgets.R
import ru.stohastika.sklyanka.feature.widgets.config.TAG_WIDGET_ADD
import ru.stohastika.sklyanka.feature.widgets.config.TAG_WIDGET_MEDICATION_ROW
import ru.stohastika.sklyanka.feature.widgets.config.TAG_WIDGET_PRO_REQUIRED
import ru.stohastika.sklyanka.feature.widgets.config.WidgetConfigScreen
import ru.stohastika.sklyanka.feature.widgets.config.WidgetConfigState
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import ru.stohastika.sklyanka.ui.component.TAG_PRO_MARK
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.util.Locale

/**
 * Экранные шаги приёмки SKL-118 — экран настройки виджета на стенде.
 *
 * Шаги живут в `feature-widgets`, потому что говорят об одной поверхности этого модуля
 * (`SKL-26/TDD.md §4.3`): экран собирается из компонентов `core-ui`, а состояние приходит
 * в него целиком, а не пересчитывается здесь. Состояние набирают глобальные шаги сценария
 * (подписка, лекарства профилей), а этот класс проверяет то, что видит человек: слова,
 * метки «Про», доступность завершения и состав выбора лекарства.
 *
 * **Почему экран собирается на каждую проверку.** Сессия Compose живёт внутри блока
 * `runComposeUiTest`, а шаги Gherkin — отдельные вызовы: держать блок открытым между ними
 * нечем. Поэтому состояние копится в полях, а экран собирается заново в том шаге,
 * который его спрашивает.
 *
 * **Почему нажатие на кнопку без подписки не завершает установку.** По §5.12 кнопка
 * не приглушается: она остаётся «Добавить виджет» и открывает витрину. Поэтому
 * «завершение недоступно» проверяется не состоянием кнопки, а исходом нажатия.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса; тексты
 * интерфейса берутся из ресурсов модуля с закреплённым языком (НФТ-43).
 */
@OptIn(ExperimentalTestApi::class)
class Sk118WidgetConfigScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Тип, названный общим шагом сценария: у «по лекарству» появляется строка лекарства. */
    private val type: WidgetType get() = widgetType(world.widgets.type)

    /** Завершение установки состоялось: экран позвал запись привязки и `RESULT_OK`. */
    private var completed: Boolean = false

    /** Витрина попросила показаться: без доступа кнопка ведёт в неё, а не к завершению. */
    private var showcaseRequested: Boolean = false

    @Тогда("на нём названы три типа виджетов")
    fun threeTypesAreNamed() = onScreen {
        WidgetType.entries.forEach { onNodeWithText(string(it.label)).assertExists() }
    }

    @И("у типов стоит метка «Про»")
    fun theTypesCarryTheProMark() = onScreen {
        onAllNodesWithTag(TAG_PRO_MARK).assertCountEquals(WidgetType.entries.size)
    }

    @И("сказано, что виджет показывает приёмы активного профиля")
    fun theHintIsShown() = onScreen {
        onNodeWithText(string(R.string.widget_setup_hint)).assertExists()
    }

    @И("завершение названо словами «Добавить виджет»")
    fun theCompletionIsNamed() = onScreen {
        onNodeWithText(string(R.string.widget_action_add)).assertExists()
    }

    @И("сказано, что виджеты — в подписке «Склянка Про»")
    fun theReasonIsShown() = onScreen {
        onNodeWithTag(TAG_WIDGET_PRO_REQUIRED).assertExists()
        onNodeWithText(string(R.string.widget_pro_required)).assertExists()
    }

    @Тогда("^в выборе лекарства есть «([^»]+)»$")
    fun thePickerOffers(name: String) = onPicker { onNodeWithText(name).assertExists() }

    @Но("^в нём нет «([^»]+)»$")
    fun thePickerDoesNotOffer(name: String) = onPicker { onNodeWithText(name).assertDoesNotExist() }

    @Тогда("завершение настройки недоступно")
    fun theCompletionIsUnavailable() {
        onScreen { onNodeWithTag(TAG_WIDGET_ADD).performClick() }
        // Нажатие без доступа ведёт в витрину и не завершает установку (Р-32, §5.12):
        // плита не появляется, пока подписки нет.
        assertFalse("установка не завершается без подписки", completed)
        assertTrue("нажатие открывает витрину", showcaseRequested)
    }

    @И("причина названа словами над кнопкой")
    fun theReasonStandsAboveTheButton() = onScreen {
        onNodeWithTag(TAG_WIDGET_PRO_REQUIRED).assertExists()
        onNodeWithTag(TAG_WIDGET_ADD).assertExists()
    }

    /** Открыть выбор лекарства: строка ведёт в диалог дизайн-системы (§5.12). */
    private fun onPicker(block: ComposeUiTest.() -> Unit) {
        onScreen {
            onNodeWithTag(TAG_WIDGET_MEDICATION_ROW).performClick()
            block()
        }
    }

    private fun onScreen(block: ComposeUiTest.() -> Unit) {
        runComposeUiTest {
            setContent { Screen() }
            block()
        }
    }

    @Composable
    private fun Screen() {
        // Язык стенда закреплён условиями прогона (`@Config(qualifiers = "ru-rRU-…")`),
        // и тексты экрана приходят из тех же ресурсов, что на устройстве (НФТ-43).
        val state = state()
        ProfileChrome(state = state.profile) {
            SklyankaTheme {
                WidgetConfigScreen(
                    state = state,
                    onSelectType = { world.widgets.type = scenarioType(it) },
                    onSelectMedication = { },
                    onComplete = { completed = true },
                    onOpenPro = { showcaseRequested = true },
                )
            }
        }
    }

    /** Состояние экрана из мира сценария: тот же состав, что собрал бы `WidgetConfigViewModel`. */
    private fun state(): WidgetConfigState {
        val active = world.profiles.get(world.profiles.active)
        return WidgetConfigState(
            type = type,
            medications = world.medications.all().filter { it.profileId == active.id },
            selectedMedicationId = null,
            proIncluded = ProStatus(world.pro.state.subscription).included,
            profile = ProfileChromeState.of(active.name, active.color, active.isSelf),
        )
    }

    /** Обратный переход: экран выбрал тип — мир сценария это запоминает. */
    private fun scenarioType(type: WidgetType): ScenarioWidgets.Type = when (type) {
        WidgetType.TODAY -> ScenarioWidgets.Type.TODAY
        WidgetType.WEEK -> ScenarioWidgets.Type.WEEK
        WidgetType.MEDICATION -> ScenarioWidgets.Type.MEDICATION
    }

    /** Тип сценария в тип модуля: у мира приёмки своё перечисление (SKL-26/TDD.md §4.1). */
    private fun widgetType(type: ScenarioWidgets.Type): WidgetType = when (type) {
        ScenarioWidgets.Type.TODAY -> WidgetType.TODAY
        ScenarioWidgets.Type.WEEK -> WidgetType.WEEK
        ScenarioWidgets.Type.MEDICATION -> WidgetType.MEDICATION
    }

    private fun string(id: Int): String = context.withInterfaceLocale(RUSSIAN).getString(id)

    private companion object {
        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}

package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.ui.component.DateTimePickerDialog
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import ru.stohastika.sklyanka.ui.component.TAG_TIME_PICKER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import java.time.LocalDate
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки диалогов времени и даты (SKL-69, группа `@screen`).
 *
 * Диалог собирается напрямую: сценарии группы — о самом диалоге (масштаб цифр, цель
 * нажатия, язык подписей), а не о маршруте формы, который его показывает. Правила не
 * копируются: масштаб цифр задаёт `SklyankaType.Display.withCappedFontScale()` через
 * `MaterialTheme.typography`, и шаг сравнивает отрисованные размеры, а не константу.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsDialogScreenSteps {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Системный масштаб шрифта, названный Предысторией. */
    private var fontScale: Float = 1f

    // --- Предыстория ---------------------------------------------------------------------------

    @Допустим("^системный шрифт увеличен до (\\d+) %$")
    fun theSystemFontScaleIs(percent: Int) {
        fontScale = percent / PERCENT
    }

    @Допустим("^системный язык телефона — английский$")
    fun theSystemLanguageIsEnglish() {
        assertTrue(
            "the stand must run under an English system locale: ${context.resources.configuration.locales}",
            context.resources.configuration.locales[0].language == "en",
        )
    }

    @Когда("^человек открывает диалог «Время приёма»$")
    fun thePersonOpensTheTimeDialog() {
        assertTrue("the dialog must show the time title", timeTitle().isNotEmpty())
    }

    @Когда("^человек открывает диалог «Дата начала»$")
    fun thePersonOpensTheStartDateDialog() {
        assertTrue(
            "the dialog must show the start date title",
            context.getString(R.string.form_dialog_start_date).isNotEmpty(),
        )
    }

    // --- Оформление и доступность --------------------------------------------------------------

    @Тогда("^цифры времени не крупнее, чем при системном шрифте 130 %$")
    fun theTimeDigitsAreCapped() {
        val atMax = maxDigitHeight(fontScale)
        val atCap = maxDigitHeight(CAP_SCALE)
        assertTrue("the time digits must be measured: $atMax, $atCap", atMax > 0 && atCap > 0)
        assertTrue(
            "time digits at ${fontScale}x ($atMax px) must not exceed 130 % ($atCap px)",
            atMax <= atCap + TOLERANCE_PX,
        )
    }

    @Но("^остальные подписи диалога масштабируются свободно$")
    fun theOtherLabelsScaleFreely() {
        val atMax = titleHeight(fontScale)
        val atCap = titleHeight(CAP_SCALE)
        assertTrue("the title must grow with the system font: $atMax <= $atCap", atMax > atCap)
    }

    @Тогда("^у действий «Отмена» и «Готово» цель нажатия не меньше 44 dp$")
    fun theDialogActionsKeepTheirTouchTarget() {
        onTimeDialog(1f) {
            val minimum = SklyankaSizes.MinTouchTarget - TOLERANCE
            listOf(UiR.string.action_cancel, UiR.string.action_done).forEach { id ->
                val label = context.getString(id)
                val node = onNode(hasText(label) and hasClickAction())
                    .fetchSemanticsNode()
                val height = with(node.layoutInfo.density) { node.size.height.toDp() }
                assertTrue("the action «$label» is $height, below $minimum", height >= minimum)
            }
        }
    }

    @Тогда("^все подписи диалога — на русском$")
    fun allDialogLabelsAreRussian() {
        onDateDialog(1f, russian = true) {
            val labels: List<String> = onAllNodes(
                hasAnyAncestor(hasTestTag(TAG_DATE_TIME_PICKER)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().flatMap { node: SemanticsNode ->
                val texts: List<String> = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                val descriptions: List<String> =
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                texts + descriptions
            }
            assertTrue("the dialog must carry labels: $labels", labels.isNotEmpty())
            val latin = labels.filter { LATIN.containsMatchIn(it) }
            assertEquals("the dialog must not carry English Material labels", emptyList<String>(), latin)
        }
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun timeTitle(): String = context.getString(R.string.form_dialog_time)

    private fun maxDigitHeight(scale: Float): Int = onTimeDialog(scale) {
        onAllNodes(hasAnyAncestor(hasTestTag(TAG_TIME_PICKER)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { node: SemanticsNode ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.any(Char::isDigit) } == true
            }
            .maxOfOrNull { node: SemanticsNode -> node.size.height } ?: 0
    }

    private fun titleHeight(scale: Float): Int = onTimeDialog(scale) {
        onNodeWithText(timeTitle(), useUnmergedTree = true).fetchSemanticsNode().size.height
    }

    private fun <T> onTimeDialog(scale: Float, russian: Boolean = false, question: ComposeUiTest.() -> T): T =
        composeDialog(scale, russian, question) {
            DateTimePickerDialog(
                title = timeTitle(),
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = {},
                onConfirm = {},
            )
        }

    private fun <T> onDateDialog(scale: Float, russian: Boolean = false, question: ComposeUiTest.() -> T): T =
        composeDialog(scale, russian, question) {
            DateTimePickerDialog(
                title = context.getString(R.string.form_dialog_start_date),
                initial = LocalDate.of(2026, 9, 25),
                onDismiss = {},
                onConfirm = {},
            )
        }

    /**
     * Диалог собирается в отдельном окне, и подмена `LocalDensity`/`LocalContext` до него
     * не доходит: окно берёт плотность и язык у конфигурации устройства. Поэтому масштаб
     * и локаль задаются среде прогона, а по окончании возвращаются — как у `@Config`,
     * только обратимо.
     */
    private fun <T> composeDialog(
        scale: Float,
        russian: Boolean,
        question: ComposeUiTest.() -> T,
        content: @Composable () -> Unit,
    ): T {
        val previousScale = RuntimeEnvironment.getFontScale()
        val previousQualifiers = RuntimeEnvironment.getQualifiers()
        RuntimeEnvironment.setFontScale(scale)
        if (russian) RuntimeEnvironment.setQualifiers(RUSSIAN_QUALIFIER)
        try {
            var answer: T? = null
            runComposeUiTest {
                setContent { SklyankaTheme { content() } }
                answer = question(this)
            }
            @Suppress("UNCHECKED_CAST")
            return answer as T
        } finally {
            RuntimeEnvironment.setFontScale(previousScale)
            RuntimeEnvironment.setQualifiers(previousQualifiers)
        }
    }

    private companion object {
        const val PERCENT = 100f

        /** Потолок масштаба цифр времени (`SklyankaSizes.DISPLAY_MAX_FONT_SCALE`, §6.3). */
        const val CAP_SCALE = SklyankaSizes.DISPLAY_MAX_FONT_SCALE

        /** Округление плотности может отнять доли точки. */
        const val TOLERANCE_PX = 2

        /** Русская локаль, прибавленная к квалификаторам устройства (SKL-28). */
        const val RUSSIAN_QUALIFIER = "+ru"

        val TOLERANCE = SklyankaSizes.BorderThin

        /** Английские подписи Material: две и более латинских буквы подряд. */
        val LATIN = Regex("[A-Za-z]{2,}")
    }
}

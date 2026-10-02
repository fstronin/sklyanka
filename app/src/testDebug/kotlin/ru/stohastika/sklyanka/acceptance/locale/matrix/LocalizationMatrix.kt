package ru.stohastika.sklyanka.acceptance.locale.matrix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import ru.stohastika.sklyanka.acceptance.brand.BrandTextFact
import ru.stohastika.sklyanka.acceptance.brand.brandTextFact
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.util.Locale

/** Что именно не так с надписью на конкретном языке. */
internal enum class MatrixDefect {
    /** Разметка не поместилась в высоту. */
    CUT,

    /** Надпись усечена многоточием, и это не разрешённое место. */
    ELLIPSIS,

    /** Перенос разрезал слово. */
    WORD_BREAK,
}

/** Нарушение разметки: экран, язык и что случилось. */
internal data class MatrixFinding(
    val screen: String,
    val locale: String,
    val defect: MatrixDefect,
    val detail: String,
) {
    override fun toString(): String = "$screen [${locale.lowercase()}] ${defect.name}: $detail"
}

/** Разметка одного экрана на одном языке — для отчётов и сценария разрешённого усечения. */
internal data class MatrixLocaleFacts(val screen: String, val locale: String, val facts: List<BrandTextFact>)

/** Что дал прогон матрицы: нарушения и полный прочитанный материал. */
internal data class MatrixScan(val findings: List<MatrixFinding>, val groups: List<MatrixLocaleFacts>) {
    /** Сколько надписей на языках без пробелов реально прочитано: доказывает, что проверка шла. */
    val syllableFacts: Int
        get() = groups.sumOf { group ->
            group.facts.count { fact -> fact.text.any { char -> char.isSyllabic() } }
        }
}

/**
 * Локализационная матрица (SKL-91): экраны и состояния × объявленные языки.
 *
 * Каждый экран компонуется один раз со всеми языками сразу — языки стоят друг под
 * другом, и поддерево каждого помечено тегом языка. Это не оптимизация ради
 * оптимизации: прогонов в обычной сборке сотни, и шесть прогонов на экран оплачивались
 * бы временем всего конвейера. Разметка читается из семантического дерева —
 * `GetTextLayoutResult`, — и привязывается к языку по тегу поддерева.
 */
internal object LocalizationMatrix {

    /** Сколько символов обязано остаться от надписи под разрешённым многоточием. */
    const val MIN_VISIBLE_ELLIPSIS_CHARACTERS = 3

    private const val LOCALE_TAG_PREFIX = "locale:"

    fun scan(
        screens: List<MatrixScreen> = LocalizationScreenCatalog.screens,
        locales: List<Locale> = InterfaceLocale.SUPPORTED,
    ): List<MatrixFinding> = scanDetailed(screens, locales).findings

    @OptIn(ExperimentalTestApi::class)
    fun scanDetailed(screens: List<MatrixScreen>, locales: List<Locale> = InterfaceLocale.SUPPORTED): MatrixScan {
        require(locales.isNotEmpty()) { "the matrix must check at least one locale" }
        val groups = mutableListOf<MatrixLocaleFacts>()
        screens.forEach { screen ->
            runComposeUiTest {
                setContent { MatrixComposition(screen, locales) }
                groups += readFacts(screen.id, locales)
            }
        }
        val findings = groups.flatMap { group ->
            group.facts.flatMap { fact ->
                fact.defects().map { (defect, detail) ->
                    MatrixFinding(screen = group.screen, locale = group.locale, defect = defect, detail = detail)
                }
            }
        }
        return MatrixScan(findings = findings, groups = groups)
    }

    /** Разрешённые усечения, которые матрица увидела: доказательство, что место опознано. */
    fun allowedEllipsis(scan: MatrixScan): List<MatrixLocaleFacts> = scan.groups
        .mapNotNull { group ->
            val allowed = group.facts.filter { fact -> fact.allowedEllipsis }
            if (allowed.isEmpty()) null else group.copy(facts = allowed)
        }

    private fun BrandTextFact.defects(): List<Pair<MatrixDefect, String>> = buildList {
        // Усечённая строка поднимает и `didOverflowHeight`: диагноз «обрезана по высоте»
        // ставится только там, где усечения нет, иначе дефект назывался бы неверно.
        if (cutHeight && !ellipsized) add(MatrixDefect.CUT to complaint().orEmpty())
        if (ellipsized && !allowedEllipsis) add(MatrixDefect.ELLIPSIS to complaint().orEmpty())
        // Разрешённое усечение — название лекарства, но исчезнуть за «…» оно не имеет
        // права: пустой заголовок делает карточки «по потребности» неразличимыми.
        if (ellipsized && allowedEllipsis && visibleCharacters < MIN_VISIBLE_ELLIPSIS_CHARACTERS) {
            add(
                MatrixDefect.ELLIPSIS to
                    "the allowed ellipsis hid the text: only $visibleCharacters characters visible",
            )
        }
        if (wordBroken) add(MatrixDefect.WORD_BREAK to complaint().orEmpty())
    }

    @Composable
    private fun MatrixComposition(screen: MatrixScreen, locales: List<Locale>) {
        Column {
            locales.forEach { locale ->
                Column(
                    modifier = Modifier
                        .height(screen.heightDp.dp)
                        .testTag(LOCALE_TAG_PREFIX + matrixLocaleTag(locale)),
                ) {
                    PinnedInterfaceLocale(locale) { screen.content() }
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.readFacts(screen: String, locales: List<Locale>): List<MatrixLocaleFacts> {
        val byLocale = linkedMapOf<String, MutableList<BrandTextFact>>()
        locales.forEach { locale -> byLocale[matrixLocaleTag(locale)] = mutableListOf() }
        val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
        collect(root, locale = null, byLocale = byLocale)
        return byLocale.map { (locale, facts) -> MatrixLocaleFacts(screen, locale, facts) }
    }

    private fun collect(
        node: SemanticsNode,
        locale: String?,
        byLocale: MutableMap<String, MutableList<BrandTextFact>>,
    ) {
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        val current = if (tag != null && tag.startsWith(LOCALE_TAG_PREFIX)) {
            tag.removePrefix(LOCALE_TAG_PREFIX)
        } else {
            locale
        }
        if (current != null) {
            node.brandTextFact()?.let { fact -> byLocale[current]?.add(fact) }
        }
        node.children.forEach { child -> collect(child, current, byLocale) }
    }
}

/** Тег поддерева языка и имя каталога контактных листов. */
internal fun matrixLocaleTag(locale: Locale): String = locale.toLanguageTag()

/**
 * Контекст, говорящий на названном языке: тот же путь, что в продукте и в приёмке SKL-62
 * (`LocalizationSteps`), — язык устройства, затем выбор приложения.
 */
@Composable
internal fun PinnedInterfaceLocale(locale: Locale, content: @Composable () -> Unit) {
    val pinned = LocalContext.current.withInterfaceLocale(locale)
    CompositionLocalProvider(
        LocalContext provides pinned,
        LocalConfiguration provides pinned.resources.configuration,
    ) {
        SklyankaTheme { content() }
    }
}

/** Письменности, переносящиеся по знакам: граница строки в них — не граница слова. */
internal fun Char.isSyllabic(): Boolean = Character.UnicodeScript.of(code) in SYLLABIC_SCRIPTS

private val SYLLABIC_SCRIPTS = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
)
